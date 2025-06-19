package com.example.bluetoothhotspotapp.data.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.example.bluetoothhotspotapp.Constants
import com.example.bluetoothhotspotapp.data.model.SearchResult
import com.example.bluetoothhotspotapp.data.model.WebPageResponse
import com.example.bluetoothhotspotapp.data.network.BluetoothProtocol
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

@SuppressLint("MissingPermission")
class BluetoothClientCommunicationManager(private val bluetoothAdapter: BluetoothAdapter?) : ClientCommunicationManager {

    private val commsJob = Job()
    private val commsScope = CoroutineScope(Dispatchers.IO + commsJob)

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState = _connectionState.asStateFlow()

    private val _searchResults = MutableSharedFlow<List<SearchResult>>()
    override val searchResults = _searchResults.asSharedFlow()

    // NUEVO: Flow para resultados de páginas web
    private val _webPageResults = MutableSharedFlow<WebPageResponse>()
    val webPageResults = _webPageResults.asSharedFlow()

    override fun connectToDevice(device: BluetoothDevice) {
        if (_connectionState.value == ConnectionState.Connected) return
        _connectionState.value = ConnectionState.Connecting

        commsScope.launch {
            try {
                // Usa el mismo UUID que el servidor
                val socket: BluetoothSocket? = device.createRfcommSocketToServiceRecord(Constants.BLUETOOTH_UUID)
                socket?.connect() // Llamada bloqueante

                bluetoothSocket = socket
                outputStream = socket?.outputStream
                inputStream = socket?.inputStream

                _connectionState.value = ConnectionState.Connected
                listenForIncomingMessages()
            } catch (e: IOException) {
                _connectionState.value = ConnectionState.Error("Conexión fallida: ${e.message}")
                disconnect()
            }
        }
    }

    override fun sendQuery(query: String) {
        commsScope.launch {
            try {
                outputStream?.write(query.toByteArray())
                outputStream?.flush()
            } catch (e: IOException) {
                _connectionState.value = ConnectionState.Error("Error al enviar: ${e.message}")
                disconnect()
            }
        }
    }

    // NUEVO: Método para solicitar páginas web
    fun requestWebPage(url: String, includeImages: Boolean = true) {
        commsScope.launch {
            try {
                val command = BluetoothProtocol.createGetPageCommand(url, includeImages)
                outputStream?.write(command.toByteArray())
                outputStream?.flush()
            } catch (e: IOException) {
                _connectionState.value = ConnectionState.Error("Error al enviar: ${e.message}")
                disconnect()
            }
        }
    }

    private fun listenForIncomingMessages() {
        startKeepAlive()

        commsScope.launch {
            val gson = Gson()
            val stream = inputStream ?: return@launch

            while (isActive) { // Usamos isActive para respetar el ciclo de vida de la corrutina
                try {
                    // --- LÓGICA DE RECEPCIÓN COMPLETAMENTE NUEVA Y ROBUSTA ---

                    // 1. Leer byte por byte hasta encontrar el delimitador de nueva línea '\n'
                    val sizeBuffer = ByteArrayOutputStream()
                    var nextByte: Int
                    while (stream.read().also { nextByte = it } != '\n'.code) {
                        if (nextByte == -1) throw IOException("La conexión se cerró antes de recibir el tamaño.")
                        sizeBuffer.write(nextByte)
                    }

                    // Convertir los bytes del tamaño a un String y luego a un Int
                    val sizeString = sizeBuffer.toString(Charset.defaultCharset().name())
                    val messageSize = sizeString.toInt()

                    if (messageSize == 0) continue // Ignorar si el mensaje está vacío

                    Log.d("ClientBT", "Tamaño de mensaje esperado: $messageSize")

                    // 2. Leer exactamente esa cantidad de bytes para obtener el JSON completo
                    val jsonBuffer = ByteArray(messageSize)
                    var totalBytesRead = 0
                    while (totalBytesRead < messageSize) {
                        val bytesRead = stream.read(jsonBuffer, totalBytesRead, messageSize - totalBytesRead)
                        if (bytesRead == -1) throw IOException("La conexión se cerró antes de recibir el mensaje completo.")
                        totalBytesRead += bytesRead
                    }

                    val receivedJson = String(jsonBuffer, Charset.defaultCharset())
                    Log.d("ClientBT", "JSON Recibido: $receivedJson")

                    // 3. Determinar qué tipo de respuesta es y procesarla
                    processJsonResponse(gson, receivedJson)

                } catch (e: Exception) {
                    // Ahora los errores serán mucho más visibles
                    Log.e("ClientBT", "Error al escuchar mensajes", e)
                    _connectionState.value = ConnectionState.Error("Conexión perdida: ${e.message}")
                    disconnect()
                    break // Salir del bucle si hay un error
                }
            }
        }
    }

    private suspend fun processJsonResponse(gson: Gson, receivedJson: String) {
        try {
            // Determinar qué tipo de respuesta es
            when {
                receivedJson.contains("\"success\"") -> {
                    // Es una WebPageResponse
                    val webPageResponse: WebPageResponse = gson.fromJson(receivedJson, WebPageResponse::class.java)
                    _webPageResults.emit(webPageResponse)
                    Log.d("ClientBT", "WebPage response emitida: ${webPageResponse.success}")
                }
                receivedJson.startsWith("[") -> {
                    // Es una lista de SearchResult (legacy)
                    val listType = object : TypeToken<List<SearchResult>>() {}.type
                    val results: List<SearchResult> = gson.fromJson(receivedJson, listType)
                    _searchResults.emit(results)
                    Log.d("ClientBT", "Resultados de búsqueda emitidos: ${results.size} items.")
                }
                else -> {
                    // Intentar como lista de SearchResult por compatibilidad
                    try {
                        val listType = object : TypeToken<List<SearchResult>>() {}.type
                        val results: List<SearchResult> = gson.fromJson(receivedJson, listType)
                        _searchResults.emit(results)
                        Log.d("ClientBT", "Resultados de búsqueda emitidos (fallback): ${results.size} items.")
                    } catch (e: Exception) {
                        Log.w("ClientBT", "No se pudo procesar la respuesta como SearchResult o WebPageResponse")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ClientBT", "Error al procesar JSON response", e)
        }
    }

    private fun disconnect() {
        try {
            _connectionState.value = ConnectionState.Disconnected
            outputStream?.close()
            inputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            // Ignorar errores al cerrar
        }
    }

    private fun startKeepAlive() {
        commsScope.launch {
            while (commsScope.isActive && _connectionState.value is ConnectionState.Connected) {
                delay(20000) // Esperar 20 segundos
                sendQuery("ping_keep_alive")
            }
        }
    }
}