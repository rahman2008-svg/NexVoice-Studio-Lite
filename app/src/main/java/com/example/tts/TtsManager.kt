package com.example.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.util.Locale

class TtsManager(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit
) {
    private var tts: TextToSpeech? = null
    var isInitialized = false
        private set

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                try {
                    val engine = tts
                    if (engine != null) {
                        engine.language = Locale.getDefault()
                    }
                } catch (e: Exception) {
                    Log.e("TtsManager", "Error setting language: ${e.message}")
                }
                onInitComplete(true)
            } else {
                Log.e("TtsManager", "Failed to initialize TextToSpeech")
                onInitComplete(false)
            }
        }
    }

    fun speak(text: String, voiceStyle: String, pitch: Float, speed: Float) {
        if (!isInitialized) return
        val engine = tts ?: return

        configureVoice(engine, voiceStyle, pitch, speed)
        
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "PREVIEW")
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, "PREVIEW")
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun synthesizeToFile(
        text: String,
        voiceStyle: String,
        pitch: Float,
        speed: Float,
        outputFile: File,
        utteranceId: String,
        onStart: () -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isInitialized) {
            onError("TTS engine not initialized")
            return
        }
        val engine = tts ?: run {
            onError("TTS engine is null")
            return
        }

        configureVoice(engine, voiceStyle, pitch, speed)

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {
                if (id == utteranceId) {
                    onStart()
                }
            }

            override fun onDone(id: String) {
                if (id == utteranceId) {
                    onComplete(outputFile)
                }
            }

            @Deprecated("Deprecated in Java", ReplaceWith("onError(id, errorCode)"))
            override fun onError(id: String) {
                if (id == utteranceId) {
                    onError("Unknown synthesis error")
                }
            }

            override fun onError(id: String, errorCode: Int) {
                if (id == utteranceId) {
                    onError("Synthesis failed with error code $errorCode")
                }
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        val result = engine.synthesizeToFile(text, params, outputFile, utteranceId)
        if (result == TextToSpeech.ERROR) {
            onError("Failed to initiate text synthesis to file")
        }
    }

    private fun configureVoice(engine: TextToSpeech, voiceStyle: String, rawPitch: Float, rawSpeed: Float) {
        var finalPitch = rawPitch
        var finalSpeed = rawSpeed

        when (voiceStyle.lowercase()) {
            "child" -> {
                finalPitch *= 1.7f
                finalSpeed *= 1.1f
            }
            "robot" -> {
                finalPitch *= 0.55f
                finalSpeed *= 0.85f
            }
            "male" -> {
                finalPitch *= 0.82f
            }
            "female" -> {
                finalPitch *= 1.25f
            }
        }

        engine.setPitch(finalPitch.coerceIn(0.5f, 2.0f))
        engine.setSpeechRate(finalSpeed.coerceIn(0.5f, 2.0f))

        try {
            val voices = engine.voices
            if (!voices.isNullOrEmpty()) {
                val locale = Locale.getDefault()
                val localeVoices = voices.filter { it.locale?.language == locale.language }
                if (localeVoices.isNotEmpty()) {
                    val matchingVoice = when (voiceStyle.lowercase()) {
                        "male" -> localeVoices.firstOrNull { 
                            val name = it.name?.lowercase() ?: ""
                            name.contains("male") && !name.contains("female") 
                        } ?: localeVoices.firstOrNull { (it.name?.lowercase() ?: "").contains("m-") }
                        "female" -> localeVoices.firstOrNull { 
                            (it.name?.lowercase() ?: "").contains("female") 
                        } ?: localeVoices.firstOrNull { (it.name?.lowercase() ?: "").contains("f-") }
                        else -> null
                    }
                    if (matchingVoice != null) {
                        engine.voice = matchingVoice
                    } else {
                        engine.language = locale
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TtsManager", "Error configuring voice style selection: ${e.message}")
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
