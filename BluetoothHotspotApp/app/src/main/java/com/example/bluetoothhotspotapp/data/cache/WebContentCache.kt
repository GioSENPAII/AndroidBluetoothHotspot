package com.example.bluetoothhotspotapp.data.cache

import android.content.Context
import android.util.Log
import com.example.bluetoothhotspotapp.data.model.WebContent
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.security.MessageDigest

class WebContentCache(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "web_content")
    private val gson = Gson()

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun saveContent(content: WebContent) {
        try {
            val key = generateKey(content.url)
            val file = File(cacheDir, "$key.json")

            // Guardar metadata
            file.writeText(gson.toJson(content))

            // Guardar imágenes si las hay
            content.images.forEach { image ->
                // Las imágenes ya están guardadas en localPath
                Log.d("WebCache", "Imagen guardada: ${image.localPath}")
            }

            Log.d("WebCache", "Contenido guardado en cache: ${content.url}")
        } catch (e: Exception) {
            Log.e("WebCache", "Error al guardar en cache", e)
        }
    }

    fun getContent(url: String): WebContent? {
        return try {
            val key = generateKey(url)
            val file = File(cacheDir, "$key.json")

            if (file.exists()) {
                val json = file.readText()
                val content = gson.fromJson(json, WebContent::class.java)

                // Verificar que las imágenes aún existen
                val validImages = content.images.filter {
                    File(it.localPath).exists()
                }

                content.copy(images = validImages)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("WebCache", "Error al leer cache", e)
            null
        }
    }

    fun hasContent(url: String): Boolean {
        val key = generateKey(url)
        return File(cacheDir, "$key.json").exists()
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    fun getCacheSize(): Long {
        return cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    private fun generateKey(url: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(url.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
    }

    fun saveImage(imageUrl: String, imageData: ByteArray): String? {
        return try {
            val key = generateKey(imageUrl)
            val extension = getImageExtension(imageUrl)
            val file = File(cacheDir, "img_$key.$extension")

            FileOutputStream(file).use { fos ->
                fos.write(imageData)
            }

            file.absolutePath
        } catch (e: Exception) {
            Log.e("WebCache", "Error al guardar imagen", e)
            null
        }
    }

    private fun getImageExtension(url: String): String {
        return when {
            url.contains(".jpg", true) || url.contains(".jpeg", true) -> "jpg"
            url.contains(".png", true) -> "png"
            url.contains(".gif", true) -> "gif"
            url.contains(".webp", true) -> "webp"
            else -> "jpg"
        }
    }
}