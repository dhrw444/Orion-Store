package com.orion.store.data

import android.content.Context
import androidx.core.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpError(val code: Int, val body: String) : IOException("请求失败，状态为 $code")

object Net {
    val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(Defaults.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(Defaults.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS).callTimeout(Defaults.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS).build()

    suspend fun text(url: String, headers: Map<String, String> = emptyMap(), noStore: Boolean = false, c: OkHttpClient = client): String = withContext(Dispatchers.IO) {
        val rb = Request.Builder().url(url); headers.forEach(rb::header); if (noStore) rb.header("Cache-Control", "no-store")
        c.newCall(rb.build()).execute().use { r -> val b = r.body.string(); if (!r.isSuccessful) throw HttpError(r.code, b); b }
    }

    suspend fun retry(url: String, retries: Int = 3, backoff: Long = 1000, noStore: Boolean = false): String {
        repeat(retries - 1) { i -> runCatching { return text(url, noStore = noStore) }; delay(backoff * (i + 1)) }
        return text(url, noStore = noStore)
    }

    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): Pair<Int, String> = withContext(Dispatchers.IO) {
        val rb = Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType())); headers.forEach(rb::header)
        client.newCall(rb.build()).execute().use { it.code to it.body.string() }
    }

    suspend fun alive(url: String, timeoutMs: Long = 5000): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build().newCall(Request.Builder().url(url).head().build()).execute().use { it.code in 200..399 }
        }.getOrDefault(false)
    }
}

data class CatalogLoad(val rawApps: JsonElement?, val mirror: JsonElement?, val config: StoreConfig?, val source: String, val devError: String? = null, val failed: Boolean = false)

/** Last good catalog, already processed, so boot paints from disk while the network refresh runs. */
@Serializable data class CatalogCache(val apps: List<AppItem>, val config: StoreConfig? = null, val source: String = "")

class Repository(private val ctx: Context) {
    fun embeddedApps(): JsonElement = json.parseToJsonElement(ctx.assets.open("local_apps.json").bufferedReader().use { it.readText() })

    private val cache = AtomicFile(File(ctx.filesDir, "catalog.json"))
    fun cached(): CatalogCache? = runCatching { json.decodeFromString(CatalogCache.serializer(), cache.readFully().decodeToString()) }.getOrNull()
    fun saveCache(c: CatalogCache) {
        val out = cache.startWrite()
        runCatching { out.write(json.encodeToString(CatalogCache.serializer(), c).toByteArray()); cache.finishWrite(out) }.onFailure { cache.failWrite(out) }
    }

    private fun bust(url: String, ts: String) = "$url${if ('?' in url) '&' else '?'}t=$ts"

    private suspend fun fetchSet(config: StoreConfig, appsFallback: String?, appsTs: String): Pair<JsonElement, JsonElement?> = coroutineScope {
        val appsUrl = config.appsJsonUrl.ifEmpty { appsFallback ?: throw IOException("开发配置缺少 appsJsonUrl。") }
        val mirrorUrl = config.mirrorJsonUrl?.ifEmpty { null } ?: Defaults.MIRROR_JSON
        val apps = async { Net.retry(bust(appsUrl, appsTs), 1, noStore = true) }
        val mirror = async { runCatching { json.parseToJsonElement(Net.retry(bust(mirrorUrl, appsTs), 1)) }.getOrNull() }
        json.parseToJsonElement(apps.await()) to mirror.await()
    }

    /** Port of App.tsx loadApps network section: dev config → GitHub → GitLab → Codeberg → JSDelivr → embedded. */
    suspend fun loadCatalog(s: Settings, manual: Boolean): CatalogLoad {
        val useDev = s.devConfigEnabled; val remote = useDev || s.useRemoteJson
        val ts = System.currentTimeMillis().toString(); val appsTs = ts
        if (!remote) return if (s.loadLocalData) CatalogLoad(embeddedApps(), null, null, "Disabled") else CatalogLoad(null, null, null, "禁用和阻止本地", failed = true)
        var devError: String? = null
        if (useDev) {
            try {
                val url = s.devConfigUrl.trim().ifEmpty { throw IOException("重新加载之前输入开发人员配置 URL。") }
                val cfgText = try { Net.retry(bust(url, ts), 1, noStore = true) } catch (e: HttpError) { throw IOException("开发配置请求失败，显示 ${e.code}") }
                val cfg = CatalogProcessor.parseConfig(cfgText)
                if (cfg.appsJsonUrl.isEmpty()) throw IOException("开发配置缺少 appsJsonUrl。")
                val (apps, mirror) = try { fetchSet(cfg, null, appsTs) } catch (e: HttpError) { throw IOException("开发应用请求失败，显示 ${e.code}") }
                return CatalogLoad(apps, mirror, cfg, "开发者配置")
            } catch (e: Exception) { devError = e.message ?: "无法加载开发者配置。" }
        } else {
            for (i in Defaults.CONFIG_URLS.indices) {
                runCatching {
                    val cfg = CatalogProcessor.parseConfig(Net.retry("${Defaults.CONFIG_URLS[i]}?t=$ts", 1, noStore = true))
                    val (apps, mirror) = fetchSet(cfg, Defaults.APPS_URLS[i], appsTs)
                    return CatalogLoad(apps, mirror, cfg, Defaults.MIRROR_LABELS[i])
                }
            }
        }
        return if (s.loadLocalData) CatalogLoad(embeddedApps(), null, null, if (useDev) "开发人员配置失败（本地回退）" else "离线（本地）", devError)
        else CatalogLoad(null, null, null, if (useDev) "开发者配置失败" else "失败（离线）", devError, failed = true)
    }

}
