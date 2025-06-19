package com.example.bluetoothhotspotapp.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.bluetoothhotspotapp.data.cache.WebContentCache
import com.example.bluetoothhotspotapp.data.model.WebPageResponse
import com.example.bluetoothhotspotapp.databinding.ActivityMiniBrowserBinding
import com.example.bluetoothhotspotapp.viewmodel.ClientViewModel
import com.example.bluetoothhotspotapp.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

class MiniBrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMiniBrowserBinding
    private val viewModel: ClientViewModel by viewModels { ViewModelFactory(this) }
    private lateinit var webCache: WebContentCache

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

        setupWebView()
        setupToolbar(title)
        setupObservers()
        loadPage(url)
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

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.webPageResults.collect { response ->
                    handleWebPageResponse(response)
                }
            }
        }
    }

    private fun loadPage(url: String) {
        // Primero verificar cache local
        val cachedContent = webCache.getContent(url)
        if (cachedContent != null) {
            Log.d("MiniBrowser", "Cargando desde cache local")
            displayContent(cachedContent.htmlContent, cachedContent.cssContent)
            return
        }

        // Solicitar al host
        binding.progressBar.visibility = View.VISIBLE
        binding.textStatus.text = "Descargando página..."
        viewModel.requestWebPage(url, includeImages = true)
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
                "Cargado desde cache"
            } else {
                "Descargado del host"
            }

            displayContent(content.htmlContent, content.cssContent)

        } else {
            binding.textStatus.text = "Error: ${response.error}"
            Toast.makeText(this, "Error al cargar página: ${response.error}", Toast.LENGTH_LONG).show()
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

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}