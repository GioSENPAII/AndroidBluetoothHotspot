package com.example.bluetoothhotspotapp.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.bluetoothhotspotapp.data.cache.WebContentCache
import com.example.bluetoothhotspotapp.data.model.WebPageResponse
import com.example.bluetoothhotspotapp.data.repository.BluetoothConnectionManager
import com.example.bluetoothhotspotapp.data.repository.ConnectionState
import com.example.bluetoothhotspotapp.databinding.ActivityMiniBrowserBinding
import kotlinx.coroutines.launch

class MiniBrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMiniBrowserBinding
    private lateinit var webCache: WebContentCache
    private var currentUrl: String? = null

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMiniBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webCache = WebContentCache(this)

        val url = intent.getStringExtra(EXTRA_URL)
        val title = intent.getStringExtra(EXTRA_TITLE)

        if (url == null) {
            Toast.makeText(this, "URL no válida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentUrl = url
        setupWebView()
        setupToolbar(title)
        setupBackPressedHandler()
        checkConnectionAndLoadPage(url)
    }

    private fun setupWebView() {
        binding.webView.apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    binding.progressBar.visibility = View.GONE
                }
            }

            settings.apply {
                javaScriptEnabled = false // Deshabilitado para seguridad
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
            }
        }
    }

    private fun setupToolbar(title: String?) {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            this.title = title ?: "Mini Browser"
        }
    }

    private fun setupBackPressedHandler() {
        // Manejo moderno del botón atrás
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    // Si no puede ir atrás en el WebView, cerrar la actividad
                    finish()
                }
            }
        })
    }

    private fun checkConnectionAndLoadPage(url: String) {
        // Verificar si hay conexión Bluetooth activa
        if (!BluetoothConnectionManager.isConnected()) {
            binding.textStatus.text = "Error: No hay conexión con el Host"
            Toast.makeText(this, "No hay conexión activa con el Host", Toast.LENGTH_LONG).show()

            // Intentar cargar desde cache local
            loadFromCacheOnly(url)
            return
        }

        // Si hay conexión, proceder normalmente
        setupObservers()
        loadPage(url)
    }

    private fun loadFromCacheOnly(url: String) {
        val cachedContent = webCache.getContent(url)
        if (cachedContent != null) {
            Log.d("MiniBrowser", "Cargando desde cache local (sin conexión)")
            binding.textStatus.text = "Cargado desde cache (sin conexión)"
            displayContent(cachedContent.htmlContent, cachedContent.cssContent)
        } else {
            binding.textStatus.text = "Página no disponible offline"
            showErrorPage("Esta página no está disponible sin conexión al Host.")
        }
    }

    private fun setupObservers() {
        // Observar resultados de páginas web
        BluetoothConnectionManager.webPageResults?.let { webPageFlow ->
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    webPageFlow.collect { response ->
                        handleWebPageResponse(response)
                    }
                }
            }
        }

        // Observar estado de conexión
        BluetoothConnectionManager.connectionState?.let { connectionFlow ->
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    connectionFlow.collect { state ->
                        if (state !is ConnectionState.Connected) {
                            binding.textStatus.text = "Conexión perdida"
                            // Si se pierde la conexión, intentar cargar desde cache
                            currentUrl?.let { loadFromCacheOnly(it) }
                        }
                    }
                }
            }
        }
    }

    private fun loadPage(url: String) {
        // Primero verificar cache local
        val cachedContent = webCache.getContent(url)
        if (cachedContent != null) {
            Log.d("MiniBrowser", "Cargando desde cache local")
            binding.textStatus.text = "Cargado desde cache"
            displayContent(cachedContent.htmlContent, cachedContent.cssContent)
            return
        }

        // Solicitar al host usando el singleton de conexión
        binding.progressBar.visibility = View.VISIBLE
        binding.textStatus.text = "Descargando página..."

        BluetoothConnectionManager.requestWebPage(url, includeImages = true)
    }

    private fun handleWebPageResponse(response: WebPageResponse) {
        binding.progressBar.visibility = View.GONE

        if (response.success && response.content != null) {
            val content = response.content

            // Guardar en cache local si no viene del cache
            if (!response.fromCache) {
                webCache.saveContent(content)
            }

            binding.textStatus.text = if (response.fromCache) {
                "Cargado desde cache del Host"
            } else {
                "Descargado del Host"
            }

            displayContent(content.htmlContent, content.cssContent)

        } else {
            binding.textStatus.text = "Error: ${response.error}"
            showErrorPage("Error al cargar página: ${response.error}")
        }
    }

    private fun displayContent(htmlContent: String, cssContent: String) {
        val fullHtml = if (cssContent.isNotEmpty()) {
            """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    $cssContent
                    
                    /* Estilos adicionales para móvil */
                    body { 
                        margin: 0; 
                        padding: 10px; 
                        font-family: Arial, sans-serif;
                        line-height: 1.4;
                    }
                    img { 
                        max-width: 100%; 
                        height: auto; 
                    }
                    table {
                        width: 100%;
                        overflow-x: auto;
                        display: block;
                        white-space: nowrap;
                    }
                </style>
            </head>
            <body>
                $htmlContent
            </body>
            </html>
            """.trimIndent()
        } else {
            htmlContent
        }

        binding.webView.loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)
    }

    private fun showErrorPage(message: String) {
        val errorHtml = """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { 
                        font-family: Arial, sans-serif; 
                        text-align: center; 
                        padding: 20px;
                        color: #666;
                    }
                    .error-icon { 
                        font-size: 48px; 
                        color: #ff6b6b; 
                        margin-bottom: 20px; 
                    }
                    .error-message { 
                        font-size: 18px; 
                        margin-bottom: 10px; 
                    }
                    .error-details { 
                        font-size: 14px; 
                        color: #999; 
                    }
                </style>
            </head>
            <body>
                <div class="error-icon">⚠️</div>
                <div class="error-message">No se pudo cargar la página</div>
                <div class="error-details">$message</div>
            </body>
            </html>
        """.trimIndent()

        binding.webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}