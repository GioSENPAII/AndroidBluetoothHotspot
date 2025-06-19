package com.example.bluetoothhotspotapp.data.network

object BluetoothProtocol {
    const val COMMAND_SEARCH = "SEARCH"
    const val COMMAND_GET_PAGE = "GET_PAGE"
    const val COMMAND_GET_CACHED_PAGE = "GET_CACHED"
    const val COMMAND_CLEAR_CACHE = "CLEAR_CACHE"
    const val COMMAND_CACHE_STATUS = "CACHE_STATUS"

    fun createSearchCommand(query: String): String {
        return "$COMMAND_SEARCH:$query"
    }

    fun createGetPageCommand(url: String, includeImages: Boolean = true): String {
        return "$COMMAND_GET_PAGE:$url:$includeImages"
    }

    fun parseCommand(message: String): Pair<String, List<String>> {
        val parts = message.split(":", limit = 2)
        val command = parts.getOrNull(0) ?: ""
        val params = if (parts.size > 1) parts[1].split(":") else emptyList()
        return command to params
    }
}