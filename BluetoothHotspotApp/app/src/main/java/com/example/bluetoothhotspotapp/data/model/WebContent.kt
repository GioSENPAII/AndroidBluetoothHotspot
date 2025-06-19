package com.example.bluetoothhotspotapp.data.model

import java.util.Date

data class WebContent(
    val url: String,
    val title: String,
    val htmlContent: String,
    val cssContent: String = "",
    val images: List<ImageResource> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val size: Long = 0
)

data class ImageResource(
    val originalUrl: String,
    val localPath: String,
    val mimeType: String,
    val size: Long
)

data class WebPageRequest(
    val url: String,
    val includeImages: Boolean = true,
    val includeCss: Boolean = true
)

data class WebPageResponse(
    val success: Boolean,
    val content: WebContent? = null,
    val error: String? = null,
    val fromCache: Boolean = false
)