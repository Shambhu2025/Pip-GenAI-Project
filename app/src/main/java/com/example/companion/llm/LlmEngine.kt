package com.example.companion.llm

import kotlinx.coroutines.flow.Flow

interface LlmEngine {
    fun initialize(systemPrompt: String)
    fun resetConversation(systemPrompt: String)
    fun streamReply(userPrompt: String): Flow<String>
    fun close()
}
