package com.arm.aichat

import android.content.Context
import com.arm.aichat.InferenceEngine.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** The "lite" build: there is no chat engine inside, so the engine says "unsupported" and never answers. The full build has the real one (llama.cpp). */
object AiChat {
    fun getInferenceEngine(context: Context): InferenceEngine = NoEngine
}

private object NoEngine : InferenceEngine {
    override val state: StateFlow<State> = MutableStateFlow(State.Error(UnsupportedArchitectureException()))
    override suspend fun loadModel(pathToModel: String) { throw UnsupportedArchitectureException() }
    override suspend fun setSystemPrompt(systemPrompt: String) {}
    override suspend fun restartConversation(systemPrompt: String) {}
    override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> = emptyFlow()
    override suspend fun loadProjector(projectorPath: String): Boolean = false
    override fun sendUserPromptWithImage(message: String, image: ByteArray, predictLength: Int): Flow<String> = emptyFlow()
    override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String = ""
    override fun cleanUp() {}
    override fun destroy() {}
}
