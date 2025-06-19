package com.example.bluetoothhotspotapp.data.repository

import android.content.Context
import android.util.Log
import com.example.bluetoothhotspotapp.data.cache.WebContentCache
import com.example.bluetoothhotspotapp.data.model.SearchResult
import com.example.bluetoothhotspotapp.data.model.WebContent
import com.example.bluetoothhotspotapp.data.model.WebPageRequest
import com.example.bluetoothhotspotapp.data.model.WebPageResponse
import com.example.bluetoothhotspotapp.data.network.BluetoothProtocol
import com.example.bluetoothhotspotapp.data.network.HtmlParser
import com.example.bluetoothhotspotapp.data.network.SearchApiService
import com.example.bluetoothhotspotapp.data.network.WebScraper
import com.example.bluetoothhotspotapp.util.JsonSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchProcessor(
    private val context: Context,
    private val searchService: SearchApiService,
    private val htmlParser: HtmlParser,
    private val jsonSerializer: JsonSerializer
) {
    private val webScraper = WebScraper()
    private val webCache = WebContentCache(context)

    suspend fun processCommand(command: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val (cmd, params) = BluetoothProtocol.parseCommand(command)

                when (cmd) {
                    BluetoothProtocol.COMMAND_SEARCH -> {
                        processSearchQuery(params.firstOrNull() ?: "")
                    }
                    BluetoothProtocol.COMMAND_GET_PAGE -> {
                        processGetPageRequest(params)
                    }
                    BluetoothProtocol.COMMAND_GET_CACHED_PAGE -> {
                        processGetCachedPageRequest(params.firstOrNull() ?: "")
                    }
                    BluetoothProtocol.COMMAND_CLEAR_CACHE -> {
                        processClearCacheRequest()
                    }
                    BluetoothProtocol.COMMAND_CACHE_STATUS -> {
                        processCacheStatusRequest()
                    }
                    else -> {
                        // Backward compatibility - si no es un comando, tratar como búsqueda
                        processSearchQuery(command)
                    }
                }
            } catch (e: Exception) {
                Log.e("SearchProcessor", "Error al procesar comando", e)
                jsonSerializer.toJson(WebPageResponse(false, error = e.message))
            }
        }
    }

    // Método legacy para compatibilidad
    suspend fun processSearchQuery(query: String): String {
        return try {
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36"
            val html = searchService.search(query, userAgent)
            val searchResults = htmlParser.parseResults(html)
            jsonSerializer.toJson(searchResults)
        } catch (e: Exception) {
            Log.e("SearchProcessor", "Error al procesar búsqueda", e)
            jsonSerializer.toJson(emptyList<SearchResult>())
        }
    }

    private suspend fun processGetPageRequest(params: List<String>): String {
        val url = params.getOrNull(0) ?: return jsonSerializer.toJson(
            WebPageResponse(false, error = "URL requerida")
        )
        val includeImages = params.getOrNull(1)?.toBoolean() ?: true

        Log.d("SearchProcessor", "Solicitando página: $url")

        // Verificar cache primero
        val cachedContent = webCache.getContent(url)
        if (cachedContent != null) {
            Log.d("SearchProcessor", "Página encontrada en cache")
            return jsonSerializer.toJson(
                WebPageResponse(true, cachedContent, fromCache = true)
            )
        }

        // Descargar nueva página
        val content = webScraper.scrapeWebPage(url, includeImages, true)

        return if (content != null) {
            // Guardar en cache
            webCache.saveContent(content)
            jsonSerializer.toJson(WebPageResponse(true, content))
        } else {
            jsonSerializer.toJson(
                WebPageResponse(false, error = "No se pudo descargar la página")
            )
        }
    }

    private fun processGetCachedPageRequest(url: String): String {
        val cachedContent = webCache.getContent(url)
        return if (cachedContent != null) {
            jsonSerializer.toJson(WebPageResponse(true, cachedContent, fromCache = true))
        } else {
            jsonSerializer.toJson(
                WebPageResponse(false, error = "Página no encontrada en cache")
            )
        }
    }

    private fun processClearCacheRequest(): String {
        webCache.clearCache()
        return jsonSerializer.toJson(mapOf("success" to true, "message" to "Cache limpiado"))
    }

    private fun processCacheStatusRequest(): String {
        val size = webCache.getCacheSize()
        return jsonSerializer.toJson(mapOf(
            "cacheSize" to size,
            "cacheSizeMB" to (size / 1024.0 / 1024.0)
        ))
    }
}