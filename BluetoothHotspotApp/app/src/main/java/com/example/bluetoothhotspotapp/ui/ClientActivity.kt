package com.example.bluetoothhotspotapp.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.bluetoothhotspotapp.data.repository.BluetoothConnectionManager
import com.example.bluetoothhotspotapp.data.repository.ConnectionState
import com.example.bluetoothhotspotapp.databinding.ActivityClientBinding
import com.example.bluetoothhotspotapp.viewmodel.ClientViewModel
import com.example.bluetoothhotspotapp.viewmodel.ViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
class ClientActivity : AppCompatActivity(), PairedDevicesDialogFragment.DeviceSelectionListener {

    private lateinit var binding: ActivityClientBinding
    private val viewModel: ClientViewModel by viewModels { ViewModelFactory(this) }
    private val searchResultAdapter = SearchResultAdapter()
    private var bluetoothAdapter: BluetoothAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClientBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        // NUEVO: Registrar la conexión en el singleton
        viewModel.bluetoothManager?.let { manager ->
            BluetoothConnectionManager.initializeConnection(manager)
        }

        if (!PermissionHelper.hasBluetoothPermissions(this)) {
            PermissionHelper.requestBluetoothPermissions(this)
        }
    }

    override fun onResume() {
        super.onResume()
        // Verificar si ya estamos conectados cuando regresamos de MiniBrowser
        val currentState = BluetoothConnectionManager.connectionState?.value
        if (currentState is ConnectionState.Connected) {
            updateUiForConnectionState(currentState)
        }
    }

    override fun onDeviceSelected(device: BluetoothDevice) {
        viewModel.connectToDevice(device)
    }

    private fun setupRecyclerView() {
        binding.recyclerViewResults.adapter = searchResultAdapter
    }

    private fun setupListeners() {
        // Listener para el nuevo botón de conexión
        binding.buttonConnect.setOnClickListener {
            showPairedDevicesDialog()
        }

        // Listener para el botón de búsqueda que está en la barra superior
        binding.buttonSearch.setOnClickListener {
            val query = binding.editTextSearch.text.toString()
            if (query.isNotBlank()) {
                binding.textViewEmpty.visibility = View.GONE
                binding.recyclerViewResults.visibility = View.INVISIBLE
                binding.progressBar.visibility = View.VISIBLE
                viewModel.onSearchClicked(query)
            } else {
                Toast.makeText(this, "Escribe algo para buscar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPairedDevicesDialog() {
        PairedDevicesDialogFragment().show(supportFragmentManager, "PairedDevicesDialog")
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Escuchar los resultados de la búsqueda
                launch {
                    viewModel.searchResults.collect { results ->
                        // Forzamos la actualización de la UI en el hilo principal
                        withContext(Dispatchers.Main) {
                            Log.d("ClientActivity", "UI Recibió ${results.size} resultados del ViewModel.")
                            binding.progressBar.visibility = View.GONE
                            searchResultAdapter.submitList(results)

                            if (results.isEmpty()) {
                                binding.recyclerViewResults.visibility = View.GONE
                                binding.textViewEmpty.visibility = View.VISIBLE
                            } else {
                                binding.recyclerViewResults.visibility = View.VISIBLE
                                binding.textViewEmpty.visibility = View.GONE
                            }
                        }
                    }
                }
                // Escuchar el estado de la conexión
                launch {
                    viewModel.connectionState.collect { state ->
                        withContext(Dispatchers.Main) {
                            updateUiForConnectionState(state)
                        }
                    }
                }
            }
        }
    }

    private fun updateUiForConnectionState(state: ConnectionState) {
        when (state) {
            is ConnectionState.Connected -> {
                binding.progressBar.visibility = View.GONE
                binding.groupSearchBar.visibility = View.VISIBLE // Muestra la barra de búsqueda
                binding.buttonConnect.visibility = View.GONE    // Oculta el botón de conectar
                supportActionBar?.subtitle = "Conectado a Host"
            }
            is ConnectionState.Connecting -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.groupSearchBar.visibility = View.GONE
                binding.buttonConnect.visibility = View.GONE
                supportActionBar?.subtitle = "Conectando..."
            }
            is ConnectionState.Error, is ConnectionState.Disconnected -> {
                binding.progressBar.visibility = View.GONE
                binding.groupSearchBar.visibility = View.GONE       // Oculta la barra de búsqueda
                binding.buttonConnect.visibility = View.VISIBLE     // Muestra el botón de conectar
                supportActionBar?.subtitle = if (state is ConnectionState.Error) "Error de conexión" else "Desconectado"

                // NUEVO: Limpiar la conexión del singleton si se desconecta
                if (state is ConnectionState.Disconnected) {
                    BluetoothConnectionManager.clearConnection()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Solo limpiar la conexión si la actividad se está destruyendo por completo
        if (isFinishing) {
            BluetoothConnectionManager.clearConnection()
        }
    }
}