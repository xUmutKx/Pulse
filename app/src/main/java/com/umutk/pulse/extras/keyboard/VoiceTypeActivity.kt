package com.umutk.pulse.extras.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent

/**
 * Speech typing with the phone's own listening screen: this see-through activity only opens it, and the words it returns go to the
 * keyboard that asked (see [VoiceTypeBus]). The keyboard stays where it is.
 */
class VoiceTypeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tag = intent.getStringExtra("tag") ?: "en-US"
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        if (savedInstanceState != null) return
        runCatching { startActivityForResult(i, 42) }.onFailure { finish() }
    }

    @Deprecated("Deprecated in the platform; the recognizer still returns its words this way")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        if (text.isNotBlank()) VoiceTypeBus.deliver(text.trim() + " ")
        finish()
    }
}

/** The words from [VoiceTypeActivity]: to the keyboard that is listening, or kept until one is back in a field. */
object VoiceTypeBus {
    @Volatile var sink: ((String) -> Unit)? = null
    @Volatile var pending = ""
    fun deliver(text: String) {
        val s = sink
        if (s != null) s(text) else pending += text
    }
}
