package dev.arialite

import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class Connection(val url: String = "", val secret: String = "", val name: String = "")

data class Download(
    val gid: String,
    val name: String,
    val status: String,
    val total: Long,
    val done: Long,
    val speed: Long,
    val uploaded: Long,
    val error: String,
    val addedAt: Long = 0,
    val observedAt: Long = 0,
    val fileCount: Int = 0,
) {
    val progress: Float
        get() = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f

    companion object {
        fun parse(o: JSONObject): Download {
            val files = o.optJSONArray("files")
            val file = files?.optJSONObject(0)
            val name =
                o.optJSONObject("bittorrent")
                    ?.optJSONObject("info")
                    ?.optString("name")
                    .orEmpty()
                    .ifBlank { file?.optString("path")?.substringAfterLast('/').orEmpty() }
                    .ifBlank {
                        file?.optJSONArray("uris")?.optJSONObject(0)?.optString("uri").orEmpty()
                    }
                    .ifBlank { "正在获取磁力链信息…" }
            return Download(
                o.getString("gid"),
                name,
                o.getString("status"),
                o.optLong("totalLength"),
                o.optLong("completedLength"),
                o.optLong("downloadSpeed"),
                o.optLong("uploadSpeed"),
                o.optString("errorMessage"),
                fileCount = files?.length() ?: 0,
            )
        }
    }
}

data class Snapshot(val tasks: List<Download>, val down: Long?, val up: Long?)

class AriaRpc(private val connection: Connection, private val client: OkHttpClient = sharedClient) {
    companion object {
        private val sharedClient =
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()

        fun validateUrl(raw: String): String {
            val url =
                raw.trim().toHttpUrlOrNull()
                    ?: throw IllegalArgumentException("请输入完整的 HTTP / HTTPS RPC 地址")
            require(url.username.isEmpty() && url.password.isEmpty()) {
                "请通过 RPC secret 验证身份，地址中不要包含密码"
            }
            require(url.fragment == null) { "RPC 地址不能包含 # 片段" }
            return url.toString()
        }
    }

    suspend fun call(method: String, vararg args: Any): Any =
        withContext(Dispatchers.IO) {
            val params = JSONArray()
            if (connection.secret.isNotEmpty()) params.put("token:${connection.secret}")
            args.forEach { params.put(it) }
            val id = UUID.randomUUID().toString()
            val body =
                JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", id)
                    .put("method", "aria2.$method")
                    .put("params", params)
            val request =
                Request.Builder()
                    .url(validateUrl(connection.url))
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful)
                    throw IOException("HTTP ${response.code}：请检查 RPC 地址和服务状态")
                val result = JSONObject(response.body?.string() ?: throw IOException("服务器返回空响应"))
                val error = result.optJSONObject("error")
                if (error != null)
                    throw IOException("RPC ${error.optInt("code")}：${error.optString("message")}")
                require(result.optString("id") == id) { "RPC 响应编号不匹配" }
                result.get("result")
            }
        }

    private suspend fun page(method: String): List<Download> {
        val out = mutableListOf<Download>()
        var offset = 0
        while (true) {
            val rows = call(method, offset, 500) as JSONArray
            for (i in 0 until rows.length()) out += Download.parse(rows.getJSONObject(i))
            if (rows.length() < 500) break
            offset += rows.length()
        }
        return out
    }

    suspend fun snapshot(): Snapshot {
        val active = call("tellActive") as JSONArray
        val tasks =
            (0 until active.length()).map { Download.parse(active.getJSONObject(it)) } +
                page("tellWaiting") +
                page("tellStopped")
        val stat = call("getGlobalStat") as JSONObject
        return Snapshot(
            tasks.distinctBy { it.gid },
            stat.optString("downloadSpeed").toLongOrNull()?.takeIf { it >= 0 },
            stat.optString("uploadSpeed").toLongOrNull()?.takeIf { it >= 0 },
        )
    }

    suspend fun options(): Pair<String, String> {
        val o = call("getGlobalOption") as JSONObject
        return o.optString("max-overall-download-limit", "0") to
            o.optString("max-concurrent-downloads", "5")
    }

    suspend fun addLink(link: String): String {
        val value = link.trim()
        require(value.startsWith("magnet:?", true) || value.toHttpUrlOrNull() != null) {
            "请输入 HTTP、HTTPS 或磁力链接"
        }
        return call("addUri", JSONArray().put(value)) as String
    }

    suspend fun addTorrent(base64: String): String = call("addTorrent", base64) as String

    suspend fun pauseAll() {
        call("pauseAll")
    }

    suspend fun resumeAll() {
        call("unpauseAll")
    }

    suspend fun remove(task: Download) {
        if (task.status in listOf("complete", "error", "removed"))
            call("removeDownloadResult", task.gid)
        else {
            call("remove", task.gid)
            call("removeDownloadResult", task.gid)
        }
    }

    suspend fun limits(speed: String, concurrent: String) {
        require(Regex("\\d+[KMGkmg]?").matches(speed)) { "限速请输入 0、500K 或 10M" }
        require((concurrent.toIntOrNull() ?: 0) > 0) { "同时下载数必须为正整数" }
        call(
            "changeGlobalOption",
            JSONObject()
                .put("max-overall-download-limit", speed)
                .put("max-concurrent-downloads", concurrent),
        )
    }
}
