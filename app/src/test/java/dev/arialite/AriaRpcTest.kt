package dev.arialite

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AriaRpcTest {
    private lateinit var server: MockWebServer
    private lateinit var api: AriaRpc
    private var result = "\"OK\""
    private var rpcError = false

    @Before
    fun setup() {
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = JSONObject(request.body.clone().readUtf8())
                    val response =
                        JSONObject().put("jsonrpc", "2.0").put("id", body.getString("id"))
                    if (rpcError)
                        response.put(
                            "error",
                            JSONObject().put("code", 1).put("message", "Unauthorized"),
                        )
                    else response.put("result", org.json.JSONTokener(result).nextValue())
                    return MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(response.toString())
                }
            }
        server.start()
        api = AriaRpc(Connection(server.url("/jsonrpc").toString(), "test-only-secret"))
    }

    @After
    fun close() {
        server.shutdown()
    }

    private fun request(): JSONObject = JSONObject(server.takeRequest().body.clone().readUtf8())

    @Test
    fun fileCountReflectsAvailableFiles() {
        val task = JSONObject().put("gid", "g").put("status", "waiting")
        assertEquals(0, Download.parse(task).fileCount)
        val files = org.json.JSONArray()
        task.put("files", files)
        for (count in 1..3) {
            files.put(JSONObject().put("path", "/downloads/file$count"))
            assertEquals(count, Download.parse(task).fileCount)
        }
    }

    @Test
    fun magnetUsesAddUriWithTokenFirst() = runBlocking {
        api.addLink("magnet:?xt=urn:btih:abc")
        val body = request()
        assertEquals("aria2.addUri", body.getString("method"))
        assertEquals("token:test-only-secret", body.getJSONArray("params").getString(0))
        assertEquals(
            "magnet:?xt=urn:btih:abc",
            body.getJSONArray("params").getJSONArray(1).getString(0),
        )
    }

    @Test
    fun torrentUsesBase64String() = runBlocking {
        api.addTorrent("ZGU=")
        val body = request()
        assertEquals("aria2.addTorrent", body.getString("method"))
        assertEquals("ZGU=", body.getJSONArray("params").getString(1))
    }

    @Test
    fun globalLimitsUseCorrectOptionNames() = runBlocking {
        api.limits("10M", "3")
        val body = request()
        assertEquals("aria2.changeGlobalOption", body.getString("method"))
        val options = body.getJSONArray("params").getJSONObject(1)
        assertEquals("10M", options.getString("max-overall-download-limit"))
        assertEquals("3", options.getString("max-concurrent-downloads"))
    }

    @Test
    fun completedTaskRemovesOnlyHistory() = runBlocking {
        api.remove(Download("gid", "test", "complete", 1, 1, 0, 0, ""))
        assertEquals("aria2.removeDownloadResult", request().getString("method"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun activeRemovalStopsThenClearsRecord() = runBlocking {
        api.remove(Download("gid", "test", "active", 1, 0, 0, 0, ""))
        assertEquals("aria2.remove", request().getString("method"))
        assertEquals("aria2.removeDownloadResult", request().getString("method"))
    }

    @Test
    fun unauthorizedResponseIsNotSuccess() = runBlocking {
        rpcError = true
        val failure = runCatching { api.call("getVersion") }.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
        assertTrue(failure!!.message!!.contains("Unauthorized"))
    }

    @Test
    fun invalidLimitsNeverReachNetwork() = runBlocking {
        assertTrue(runCatching { api.limits("-1", "0") }.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun emptySecretDoesNotAddToken() = runBlocking {
        AriaRpc(Connection(server.url("/jsonrpc").toString())).call("getVersion")
        assertEquals(0, request().getJSONArray("params").length())
    }

    @Test
    fun parsesMetadataAndClampsProgress() {
        val task =
            Download.parse(
                JSONObject(
                    """{"gid":"abc","status":"active","totalLength":"10","completedLength":"12","bittorrent":{"info":{"name":"Movie"}},"files":[]}"""
                )
            )
        assertEquals("Movie", task.name)
        assertEquals(1f, task.progress)
    }

    @Test
    fun rejectsEmbeddedCredentials() {
        assertTrue(
            runCatching { AriaRpc.validateUrl("http://user:pass@example.com/jsonrpc") }.isFailure
        )
    }

    @Test
    fun bulkPauseAndResumeUseAria2GlobalMethods() = runBlocking {
        api.pauseAll()
        assertEquals("aria2.pauseAll", request().getString("method"))
        api.resumeAll()
        assertEquals("aria2.unpauseAll", request().getString("method"))
    }

    @Test
    fun addedLinkReturnsGidForCreationTimestamp() = runBlocking {
        result = "\"0123456789abcdef\""
        assertEquals("0123456789abcdef", api.addLink("https://example.com/file.zip"))
    }

    @Test
    fun snapshotDistinguishesZeroFromMissingSpeed() = runBlocking {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val rpc = JSONObject(request.body.clone().readUtf8())
                    val result =
                        if (rpc.getString("method") == "aria2.getGlobalStat")
                            JSONObject().put("uploadSpeed", "0")
                        else org.json.JSONArray()
                    return MockResponse()
                        .setBody(
                            JSONObject()
                                .put("id", rpc.getString("id"))
                                .put("result", result)
                                .toString()
                        )
                }
            }
        val snapshot = api.snapshot()
        assertNull(snapshot.down)
        assertEquals(0L, snapshot.up)
    }
}
