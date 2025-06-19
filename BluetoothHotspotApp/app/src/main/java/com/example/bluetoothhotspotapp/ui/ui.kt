package com.example.bluetoothhotspotapp.ui

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetoothhotspotapp.data.model.SearchResult
import com.example.bluetoothhotspotapp.databinding.ItemSearchResultBinding

class SearchResultAdapter : ListAdapter<SearchResult, SearchResultAdapter.SearchResultViewHolder>(
    DiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    class SearchResultViewHolder(private val binding: ItemSearchResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(searchResult: SearchResult) {
            binding.apply {
                textViewTitle.text = searchResult.title
                textViewSnippet.text = searchResult.snippet
                textViewUrl.text = searchResult.url

                // ACTUALIZADO: Usar MiniBrowserActivity en lugar de WebViewActivity
                root.setOnClickListener {
                    val context = root.context
                    val intent = Intent(context, MiniBrowserActivity::class.java).apply {
                        putExtra(MiniBrowserActivity.EXTRA_URL, searchResult.url)
                        putExtra(MiniBrowserActivity.EXTRA_TITLE, searchResult.title)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SearchResult>() {
        override fun areItemsTheSame(oldItem: SearchResult, newItem: SearchResult) =
            oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: SearchResult, newItem: SearchResult) =
            oldItem == newItem
    }
}