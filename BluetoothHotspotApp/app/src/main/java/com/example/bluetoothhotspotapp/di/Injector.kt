package com.example.bluetoothhotspotapp.di

import android.content.Context
import com.example.bluetoothhotspotapp.data.network.HtmlParser
import com.example.bluetoothhotspotapp.data.network.SearchApiService
import com.example.bluetoothhotspotapp.data.repository.SearchProcessor
import com.example.bluetoothhotspotapp.util.JsonSerializer
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

object Injector {

    private fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://html.duckduckgo.com/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }

    private fun provideSearchApiService(): SearchApiService {
        return provideRetrofit().create(SearchApiService::class.java)
    }

    // ACTUALIZADO: Ahora requiere Context
    fun provideSearchProcessor(context: Context): SearchProcessor {
        return SearchProcessor(
            context = context,
            searchService = provideSearchApiService(),
            htmlParser = HtmlParser(),
            jsonSerializer = JsonSerializer()
        )
    }
}