package com.yeobosay.app.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class GreetingSpeaker(
    context: Context,
) {
    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var isReady = false
    private var pendingGreeting: PendingGreeting? = null
    private var onComplete: (() -> Unit)? = null

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                textToSpeech?.language = Locale.KOREAN
                textToSpeech?.setSpeechRate(0.88f)
                textToSpeech?.setPitch(0.95f)
                textToSpeech?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit

                        override fun onDone(utteranceId: String?) {
                            finishPlayback()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            finishPlayback()
                        }
                    },
                )
                pendingGreeting?.let {
                    pendingGreeting = null
                    speak(it.text, it.onComplete)
                }
            } else {
                pendingGreeting?.onComplete?.invoke()
                pendingGreeting = null
            }
        }
    }

    fun speak(text: String, onComplete: () -> Unit = {}) {
        stop(invokeCallback = false)
        if (!isReady) {
            pendingGreeting = PendingGreeting(text, onComplete)
            return
        }

        this.onComplete = onComplete
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, GREETING_UTTERANCE_ID)
        }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, GREETING_UTTERANCE_ID)
    }

    fun stop(invokeCallback: Boolean = true) {
        pendingGreeting = null
        textToSpeech?.stop()
        if (invokeCallback) finishPlayback()
    }

    fun shutdown() {
        pendingGreeting = null
        onComplete = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isReady = false
    }

    private fun finishPlayback() {
        val callback = onComplete
        onComplete = null
        callback?.invoke()
    }

    private data class PendingGreeting(
        val text: String,
        val onComplete: () -> Unit,
    )

    private companion object {
        const val GREETING_UTTERANCE_ID = "yeobosay-greeting"
    }
}
