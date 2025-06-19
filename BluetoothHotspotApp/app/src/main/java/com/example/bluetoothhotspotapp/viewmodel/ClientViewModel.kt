package com.example.bluetoothhotspotapp.viewmodel

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import com.example.bluetoothhotspotapp.data.model.SearchResult
import com.example.bluetoothhotspotapp.data.model.WebPageResponse
import com.example.bluetoothhotspotapp.data.repository.BluetoothClientCommunicationManager
import com.example.bluetoothhotspotapp.data.repository.ClientCommunicationManager
import com.example.bluetoothhotspotapp.data.repository.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class ClientViewModel(
    private val commManager: ClientCommunicationManager
) : ViewModel() {

    // Exposer el manager Bluetooth para el singleton (con nombre diferente para evitar conflicto)
    val bluetoothManager: BluetoothClientCommunicationManager? =
        commManager as? BluetoothClientCommunicationManager

    // Flows existentes
    val searchResults: Flow<List<SearchResult>> = commManager.searchResults
    val connectionState: StateFlow<ConnectionState> = commManager.connectionState

    // Flow para resultados de páginas web
    val webPageResults: Flow<WebPageResponse> = if (commManager is BluetoothClientCommunicationManager) {
        commManager.webPageResults
    } else {
        kotlinx.coroutines.flow.emptyFlow()
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (commManager is BluetoothClientCommunicationManager) {
            commManager.connectToDevice(device)
        }
    }

    fun onSearchClicked(query: String) {
        commManager.sendQuery(query)
    }

    fun requestWebPage(url: String, includeImages: Boolean = true) {
        if (commManager is BluetoothClientCommunicationManager) {
            commManager.requestWebPage(url, includeImages)
        }
    }
}