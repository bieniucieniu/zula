package com.zula.features.feed

class FeedConsumer(private val feedService: com.zula.features.feed.FeedService) {
    fun onMediaEmbeddingCompleted(itemId: String) {
        // TODO: media.embedding.completed → update item vector
    }
}
