package dev.arialite

import android.app.Application
import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ConnectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("aria-rpc", null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            "aria-rpc",
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    private fun records() = org.json.JSONObject(prefs.getString("profiles", "{}") ?: "{}")

    fun names() = urls().associateWith { prefs.getString("name:$it", "").orEmpty() }

    fun urls(): List<String> =
        (records().keys().asSequence().toList() + prefs.getString("url", "").orEmpty())
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    fun load(url: String = prefs.getString("url", "").orEmpty()): Connection {
        val encrypted =
            if (records().has(url)) records().getString(url) else prefs.getString("secret", null)
        val secret =
            if (encrypted == null) ""
            else {
                val parts = encrypted.split(':')
                Cipher.getInstance("AES/GCM/NoPadding").run {
                    init(
                        Cipher.DECRYPT_MODE,
                        key(),
                        GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
                    )
                    String(doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
                }
            }
        return Connection(url, secret, prefs.getString("name:$url", "").orEmpty())
    }

    fun save(c: Connection) {
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted =
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
                ":" +
                Base64.encodeToString(
                    cipher.doFinal(c.secret.toByteArray(Charsets.UTF_8)),
                    Base64.NO_WRAP,
                )
        val profiles = records()
        val oldUrl = prefs.getString("url", "").orEmpty()
        if (oldUrl.isNotBlank() && !profiles.has(oldUrl))
            profiles.put(oldUrl, prefs.getString("secret", ""))
        profiles.put(c.url, encrypted)
        check(
            prefs
                .edit()
                .putString("profiles", profiles.toString())
                .putString("url", c.url)
                .putString("name:${c.url}", c.name)
                .remove("secret")
                .commit()
        ) {
            "无法保存连接"
        }
    }

    fun theme(): String = prefs.getString("theme", "system") ?: "system"

    fun theme(value: String) {
        prefs.edit { putString("theme", value) }
    }
}

sealed interface PlaybackTarget {
    data object All : PlaybackTarget

    data class Task(val gid: String) : PlaybackTarget
}

data class AppState(
    val connection: Connection = Connection(),
    val names: Map<String, String> = emptyMap(),
    val urls: List<String> = emptyList(),
    val tasks: List<Download> = emptyList(),
    val down: Long = 0,
    val up: Long = 0,
    val connected: Boolean = false,
    val pendingPlayback: PlaybackTarget? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val limit: String = "0",
    val concurrent: String = "5",
    val theme: String = "system",
    val history: List<SpeedSample> = emptyList(),
)

class AppModel(application: Application) : AndroidViewModel(application) {
    private val store = ConnectionStore(application)
    private val times = application.getSharedPreferences("task-times", Context.MODE_PRIVATE)

    private fun timeKey(gid: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(mutable.value.connection.url.toByteArray())
            .joinToString("") { "%02x".format(it) } + ":" + gid

    private fun recordCreated(gid: String) {
        times.edit { putLong(timeKey(gid) + ":created", System.currentTimeMillis()) }
    }

    private fun dated(tasks: List<Download>): List<Download> {
        val editor = times.edit()
        val now = System.currentTimeMillis()
        val result =
            tasks.map { task ->
                val key = timeKey(task.gid)
                val seen = times.getLong(key, now)
                if (!times.contains(key)) editor.putLong(key, seen)
                task.copy(addedAt = times.getLong(key + ":created", 0), observedAt = seen)
            }
        editor.apply()
        return result
    }

    private val mutable =
        MutableStateFlow(
            runCatching {
                    AppState(
                        connection = store.load(),
                        urls = store.urls(),
                        names = store.names(),
                        theme = store.theme(),
                    )
                }
                .getOrElse { AppState(error = "无法读取保存的凭证，请重新填写连接") }
        )
    val state = mutable.asStateFlow()
    private val mutex = Mutex()

    private fun update(block: (AppState) -> AppState) {
        mutable.value = block(mutable.value)
    }

    private fun rpc() = AriaRpc(mutable.value.connection)

    private fun friendly(e: Exception): String =
        when (e) {
            is java.net.ConnectException -> "无法连接 NAS，请检查同一局域网、端口映射和防火墙"
            is java.net.SocketTimeoutException -> "连接超时，请检查 NAS 是否在线"
            is java.net.UnknownHostException -> "无法解析服务器地址"
            else ->
                (e.message ?: "操作失败").replace(
                    mutable.value.connection.secret.takeIf { it.isNotBlank() } ?: "\u0000",
                    "••••",
                )
        }

    suspend fun refresh() =
        mutex.withLock {
            if (mutable.value.connection.url.isBlank()) return@withLock
            try {
                val api = rpc()
                val options = if (!mutable.value.connected) api.options() else null
                val result = api.snapshot()
                update {
                    it.copy(
                        tasks = dated(result.tasks),
                        history =
                            appendSample(
                                it.history,
                                SpeedSample(System.currentTimeMillis(), result.down, result.up),
                            ),
                        down = result.down ?: 0,
                        up = result.up ?: 0,
                        connected = true,
                        error = null,
                        limit = options?.first ?: it.limit,
                        concurrent = options?.second ?: it.concurrent,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update {
                    it.copy(
                        connected = false,
                        down = 0,
                        up = 0,
                        error = friendly(e),
                        history =
                            appendSample(
                                it.history,
                                SpeedSample(System.currentTimeMillis(), null, null),
                            ),
                    )
                }
            }
        }

    private val playbackFailures = Channel<String>(Channel.BUFFERED)
    val playbackErrors = playbackFailures.receiveAsFlow()

    private fun action(success: String, target: PlaybackTarget? = null, block: suspend () -> Unit) {
        if (mutable.value.busy) return
        update { it.copy(busy = true, pendingPlayback = target, message = null) }
        viewModelScope.launch {
            // Keep a successful playback control locked until the existing refresh supplies its new
            // status.
            var awaitingStatus = false
            try {
                mutex.withLock {
                    try {
                        block()
                        awaitingStatus = target != null
                        update { it.copy(message = success) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val message = friendly(e)
                        if (target == null) update { it.copy(message = message) }
                        else playbackFailures.trySend(message)
                    } finally {
                        if (!awaitingStatus)
                            update { it.copy(busy = false, pendingPlayback = null) }
                    }
                }
                refresh()
            } finally {
                if (awaitingStatus) update { it.copy(busy = false, pendingPlayback = null) }
            }
        }
    }

    fun testConnection(url: String, secret: String) =
        action("测试成功，可以连接到 aria2") {
            AriaRpc(Connection(AriaRpc.validateUrl(url), secret)).call("getVersion")
        }

    fun saveConnection(name: String, url: String, secret: String) =
        action("服务器配置已保存") { activate(Connection(AriaRpc.validateUrl(url), secret, name.trim())) }

    fun select(url: String) = action("已切换下载器") { activate(store.load(url)) }

    private suspend fun activate(candidate: Connection) {
        withContext(Dispatchers.IO) { store.save(candidate) }
        update {
            it.copy(
                connection = candidate,
                urls = store.urls(),
                names = store.names(),
                down = 0,
                up = 0,
                error = null,
                tasks = emptyList(),
                history = emptyList(),
                limit = "0",
                concurrent = "5",
                connected = false,
            )
        }
    }

    fun loadOptions() =
        action("已读取服务器设置") {
            val values = rpc().options()
            update { it.copy(limit = values.first, concurrent = values.second) }
        }

    fun limits(speed: String, count: String) =
        action("全局下载设置已生效") {
            rpc().limits(speed.trim(), count.trim())
            val values = rpc().options()
            update { it.copy(limit = values.first, concurrent = values.second) }
        }

    fun addLink(link: String) = action("下载任务已添加") { recordCreated(rpc().addLink(link)) }

    fun pauseAll() = action("已暂停全部下载", PlaybackTarget.All) { rpc().pauseAll() }

    fun resumeAll() = action("已恢复所有暂停任务", PlaybackTarget.All) { rpc().resumeAll() }

    fun torrent(uri: Uri) =
        action("BT 任务已添加") {
            val bytes =
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input
                        ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            require(buffer.size() + n <= 4 * 1024 * 1024) { "种子文件过大，最多支持 4 MiB" }
                            buffer.write(chunk, 0, n)
                        }
                        buffer.toByteArray()
                    } ?: error("无法读取种子文件")
                }
            require(bytes.isNotEmpty() && bytes.first() == 'd'.code.toByte()) {
                "请选择有效的 .torrent 文件"
            }
            recordCreated(rpc().addTorrent(Base64.encodeToString(bytes, Base64.NO_WRAP)))
        }

    fun toggle(task: Download) =
        action("任务已更新", PlaybackTarget.Task(task.gid)) {
            rpc().call(if (task.status == "paused") "unpause" else "pause", task.gid)
        }

    fun remove(task: Download) = action("任务已移除，NAS 上的文件保留") { rpc().remove(task) }

    fun theme(value: String) {
        store.theme(value)
        update { it.copy(theme = value) }
    }

    fun clearMessage() {
        update { it.copy(message = null) }
    }
}
