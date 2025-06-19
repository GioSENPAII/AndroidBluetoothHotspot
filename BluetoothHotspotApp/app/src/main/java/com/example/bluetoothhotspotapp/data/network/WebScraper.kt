package com.example.bluetoothhotspotapp.data.network

import android.util.Log
import com.example.bluetoothhotspotapp.data.model.ImageResource
import com.example.bluetoothhotspotapp.data.model.WebContent
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.util.concurrent.TimeUnit

class WebScraper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun scrapeWebPage(
        url: String,
        includeImages: Boolean = true,
        includeCss: Boolean = true
    ): WebContent? {
        return try {
            Log.d("WebScraper", "Descargando: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e("WebScraper", "Error HTTP: ${response.code}")
                return null
            }

            val html = response.body?.string() ?: return null
            val document = Jsoup.parse(html, url)

            // Procesar el HTML
            val processedContent = processHtmlContent(document, url, includeImages, includeCss)

            Log.d("WebScraper", "Página descargada exitosamente: ${processedContent.title}")
            processedContent

        } catch (e: Exception) {
            Log.e("WebScraper", "Error al descargar página", e)
            null
        }
    }

    private suspend fun processHtmlContent(
        document: Document,
        baseUrl: String,
        includeImages: Boolean,
        includeCss: Boolean
    ): WebContent {

        val title = document.title().ifEmpty { "Página sin título" }
        val images = mutableListOf<ImageResource>()

        // Procesar imágenes
        if (includeImages) {
            document.select("img[src]").forEach { img ->
                val imgSrc = img.attr("abs:src")
                if (imgSrc.isNotEmpty()) {
                    downloadImage(imgSrc)?.let { imageData ->
                        val imageResource = ImageResource(
                            originalUrl = imgSrc,
                            localPath = "data:image/jpeg;base64,${android.util.Base64.encodeToString(imageData, android.util.Base64.DEFAULT)}",
                            mimeType = "image/jpeg",
                            size = imageData.size.toLong()
                        )
                        images.add(imageResource)

                        // Reemplazar src con data URL
                        img.attr("src", imageResource.localPath)
                    }
                }
            }
        }

        // Procesar CSS inline
        var cssContent = ""
        if (includeCss) {
            document.select("link[rel=stylesheet]").forEach { link ->
                val href = link.attr("abs:href")
                if (href.isNotEmpty()) {
                    downloadCss(href)?.let { css ->
                        cssContent += css + "\n"
                    }
                }
            }
        }

        // Limpiar el HTML
        cleanHtml(document)

        return WebContent(
            url = baseUrl,
            title = title,
            htmlContent = document.html(),
            cssContent = cssContent,
            images = images,
            size = document.html().length.toLong()
        )
    }

    private fun downloadImage(imageUrl: String): ByteArray? {
        return try {
            val request = Request.Builder()
                .url(imageUrl)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("WebScraper", "No se pudo descargar imagen: $imageUrl")
            null
        }
    }

    private fun downloadCss(cssUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url(cssUrl)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w("WebScraper", "No se pudo descargar CSS: $cssUrl")
            null
        }
    }

    private fun cleanHtml(document: Document) {
        // Remover elementos problemáticos
        document.select("script").remove()
        document.select("noscript").remove()
        document.select("iframe").remove()
        document.select("embed").remove()
        document.select("object").remove()

        // Remover atributos de eventos
        document.select("*").forEach { element ->
            element.attributes().forEach { attr ->
                if (attr.key.startsWith("on")) {
                    element.removeAttr(attr.key)
                }
            }
        }
    }
}