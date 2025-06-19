package com.example.bluetoothhotspotapp.data.repository

import android.bluetooth.BluetoothDevice
import com.example.bluetoothhotspotapp.data.model.SearchResult
import com.example.bluetoothhotspotapp.data.model.WebPageResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton para mantener una única conexión Bluetooth activa
 * que puede ser compartida entre múltiples Activities
 */
object BluetoothConnectionManager {

    private var activeManager: BluetoothClientCommunicationManager? = null

    fun initializeConnection(manager: BluetoothClientCommunicationManager) {
        // Si ya hay una conexión activa, cerrarla primero
        activeManager?.let {
            // No cerramos la conexión, solo cambiamos la referencia
        }
        activeManager = manager
    }

    fun getActiveConnection(): BluetoothClientCommunicationManager? {
        return activeManager
    }

    fun isConnected(): Boolean {
        return activeManager?.connectionState?.value is ConnectionState.Connected
    }

    // Métodos proxy para acceder a la funcionalidad sin exponer el manager completo
    val connectionState: StateFlow<ConnectionState>?
        get() = activeManager?.connectionState

    val searchResults: Flow<List<SearchResult>>?
        get() = activeManager?.searchResults

    val webPageResults: Flow<WebPageResponse>?
        get() = activeManager?.webPageResults

    fun connectToDevice(device: BluetoothDevice) {
        activeManager?.connectToDevice(device)
    }

    fun sendQuery(query: String) {
        activeManager?.sendQuery(query)
    }

    fun requestWebPage(url: String, includeImages: Boolean = true) {
        activeManager?.requestWebPage(url, includeImages)
    }

    fun clearConnection() {
        activeManager = null
    }
}