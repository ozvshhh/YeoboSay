package com.yeobosay.app.voice

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import java.io.File

class AudioPlayer(
    private val context: Context,
) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: File? = null

    fun playBase64Mp3(audioBase64: String, onComplete: () -> Unit = {}) {
        stop()
        val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
        val file = File.createTempFile("yeobosay-response-", ".mp3", context.cacheDir)
        file.writeBytes(bytes)

        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                stop()
                onComplete()
            }
            setOnErrorListener { _, _, _ ->
                stop()
                onComplete()
                true
            }
            prepare()
            start()
        }
        currentFile = file
    }

    fun stop() {
        mediaPlayer?.run {
            runCatching { stop() }
            release()
        }
        mediaPlayer = null
        currentFile?.delete()
        currentFile = null
    }
}
