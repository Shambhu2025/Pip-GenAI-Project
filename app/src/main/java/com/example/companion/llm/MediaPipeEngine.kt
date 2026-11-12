package com.example.companion.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MediaPipeEngine(
    private val context: Context,
    private val modelPath: String
) : LlmEngine {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var turnCount = 0
    private var currentSystemPrompt: String = ""

    override fun initialize(systemPrompt: String) {
        currentSystemPrompt = systemPrompt
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        val eng = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(threadCount = 4),
                maxNumTokens = ReplyLimits.MAX_CONTEXT_TOKENS,
                cacheDir = context.cacheDir.path
            )
        )
        eng.initialize()
        engine = eng
        resetConversation(systemPrompt)
    }

    override fun resetConversation(systemPrompt: String) {
        currentSystemPrompt = systemPrompt
        try { conversation?.close() } catch (_: Throwable) {}
        val eng = engine ?: return
        conversation = eng.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                samplerConfig = SamplerConfig(topK = 20, topP = 0.90, temperature = 0.35)
            )
        )
        turnCount = 0
    }

    override fun streamReply(userPrompt: String): Flow<String> {
        if (turnCount >= ReplyLimits.MAX_HISTORY_TURNS || conversation == null) {
            resetConversation(currentSystemPrompt)
        }
        turnCount++
        val conv = conversation!!
        return conv.sendMessageAsync(userPrompt).map { it.toString() }
    }

    override fun close() {
        try { conversation?.close() } catch (_: Throwable) {}
        try { engine?.close() } catch (_: Throwable) {}
    }
}
