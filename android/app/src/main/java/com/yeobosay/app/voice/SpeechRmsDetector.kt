package com.yeobosay.app.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

data class SpeechDetectionConfig(
    val sampleRateHz: Int = 16_000,
    val minSpeechMs: Long = 500L,
    val speechEndSilenceMs: Long = 1_300L,
    val maxUtteranceMs: Long = 12_000L,
    val minRmsThreshold: Double = 0.018,
    val noiseMultiplier: Double = 3.2,
)

data class SpeechDetectionSnapshot(
    val rms: Double,
    val threshold: Double,
    val noiseFloor: Double,
    val isSpeaking: Boolean,
    val audioSourceName: String,
)

interface SpeechDetectionListener {
    fun onListening(snapshot: SpeechDetectionSnapshot)
    fun onSpeechStarted(snapshot: SpeechDetectionSnapshot)
    fun onSpeechEnded(durationMs: Long, snapshot: SpeechDetectionSnapshot)
    fun onSpeechEndedWithAudio(
        durationMs: Long,
        snapshot: SpeechDetectionSnapshot,
        audioFile: File?,
    ) {
        onSpeechEnded(durationMs, snapshot)
    }

    fun onError(message: String)
}

object AudioLevelMath {
    fun rmsPcm16(buffer: ShortArray, length: Int): Double {
        if (length <= 0) return 0.0

        var sum = 0.0
        for (index in 0 until length) {
            val normalized = buffer[index] / Short.MAX_VALUE.toDouble()
            sum += normalized * normalized
        }
        return sqrt(sum / length)
    }
}

class SpeechRmsDetector(
    private val context: Context,
    private val config: SpeechDetectionConfig = SpeechDetectionConfig(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var detectJob: Job? = null
    private var audioRecord: AudioRecord? = null

    val isRunning: Boolean
        get() = detectJob?.isActive == true

    fun start(listener: SpeechDetectionListener) {
        if (isRunning) return

        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            listener.onError("마이크 권한이 필요합니다.")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            config.sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            listener.onError("마이크 버퍼를 준비할 수 없습니다.")
            return
        }

        val bufferSize = max(minBufferSize, config.sampleRateHz / 5)
        val recordingSource = createRecorder(bufferSize)
        if (recordingSource == null) {
            listener.onError("마이크를 초기화할 수 없습니다.")
            return
        }

        val record = recordingSource.record

        audioRecord = record
        detectJob = scope.launch {
            runDetectionLoop(recordingSource, bufferSize, listener)
        }
    }

    fun stop() {
        detectJob?.cancel()
        detectJob = null
        releaseRecorder()
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private suspend fun runDetectionLoop(
        recordingSource: RecordingSource,
        bufferSize: Int,
        listener: SpeechDetectionListener,
    ) {
        val record = recordingSource.record
        val buffer = ShortArray(bufferSize)
        var speechPcmBuffer = ByteArrayOutputStream()
        var isSpeaking = false
        var speechStartedAt = 0L
        var lastSpeechAt = 0L
        var noiseFloor = config.minRmsThreshold / config.noiseMultiplier

        try {
            record.startRecording()
            while (currentCoroutineContext().isActive) {
                val readCount = record.read(buffer, 0, buffer.size)
                if (readCount <= 0) continue

                val now = System.currentTimeMillis()
                val rms = AudioLevelMath.rmsPcm16(buffer, readCount)
                val threshold = max(config.minRmsThreshold, noiseFloor * config.noiseMultiplier)
                val hasVoice = rms >= threshold

                if (!isSpeaking && !hasVoice) {
                    noiseFloor = (noiseFloor * 0.94) + (rms * 0.06)
                }

                val snapshot = SpeechDetectionSnapshot(
                    rms = rms,
                    threshold = threshold,
                    noiseFloor = noiseFloor,
                    isSpeaking = isSpeaking,
                    audioSourceName = recordingSource.name,
                )

                if (hasVoice) {
                    lastSpeechAt = now
                    if (!isSpeaking) {
                        isSpeaking = true
                        speechStartedAt = now
                        speechPcmBuffer = ByteArrayOutputStream()
                        listener.onSpeechStarted(snapshot.copy(isSpeaking = true))
                    } else {
                        listener.onListening(snapshot.copy(isSpeaking = true))
                    }
                    speechPcmBuffer.writePcm16(buffer, readCount)
                    if (now - speechStartedAt >= config.maxUtteranceMs) {
                        val durationMs = now - speechStartedAt
                        val audioFile = writeWavFile(speechPcmBuffer.toByteArray())
                        isSpeaking = false
                        listener.onSpeechEndedWithAudio(
                            durationMs,
                            snapshot.copy(isSpeaking = false),
                            audioFile,
                        )
                    }
                    continue
                }

                if (isSpeaking && now - lastSpeechAt >= config.speechEndSilenceMs) {
                    val durationMs = lastSpeechAt - speechStartedAt
                    isSpeaking = false
                    if (durationMs >= config.minSpeechMs) {
                        val audioFile = writeWavFile(speechPcmBuffer.toByteArray())
                        listener.onSpeechEndedWithAudio(
                            durationMs,
                            snapshot.copy(isSpeaking = false),
                            audioFile,
                        )
                    } else {
                        listener.onListening(snapshot.copy(isSpeaking = false))
                    }
                    speechPcmBuffer = ByteArrayOutputStream()
                } else {
                    if (isSpeaking) {
                        speechPcmBuffer.writePcm16(buffer, readCount)
                    }
                    listener.onListening(snapshot.copy(isSpeaking = isSpeaking))
                }
            }
        } catch (error: RuntimeException) {
            if (currentCoroutineContext().isActive) {
                listener.onError(error.message ?: "음성 감지 중 문제가 발생했습니다.")
            }
        } finally {
            releaseRecorder()
        }
    }

    private fun releaseRecorder() {
        val record = audioRecord ?: return
        audioRecord = null
        runCatching { record.stop() }
        record.release()
    }

    @SuppressLint("MissingPermission")
    private fun createRecorder(bufferSize: Int): RecordingSource? {
        val candidates = listOf(
            MediaRecorder.AudioSource.MIC to "MIC",
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
        )

        for ((source, name) in candidates) {
            val record = runCatching {
                AudioRecord(
                    source,
                    config.sampleRateHz,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            }.getOrNull() ?: continue

            if (record.state == AudioRecord.STATE_INITIALIZED) {
                return RecordingSource(record = record, name = name)
            }

            record.release()
        }

        return null
    }

    private data class RecordingSource(
        val record: AudioRecord,
        val name: String,
    )

    private fun ByteArrayOutputStream.writePcm16(buffer: ShortArray, length: Int) {
        val bytes = ByteArray(length * 2)
        val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until length) {
            byteBuffer.putShort(buffer[index])
        }
        write(bytes)
    }

    private fun writeWavFile(pcmBytes: ByteArray): File? {
        if (pcmBytes.isEmpty()) return null

        return runCatching {
            val file = File.createTempFile("yeobosay-auto-turn-", ".wav", context.cacheDir)
            file.outputStream().use { output ->
                output.write(createWavHeader(pcmBytes.size))
                output.write(pcmBytes)
            }
            file
        }.getOrNull()
    }

    private fun createWavHeader(pcmDataSize: Int): ByteArray {
        val channelCount = 1
        val bitsPerSample = 16
        val byteRate = config.sampleRateHz * channelCount * bitsPerSample / 8
        val blockAlign = channelCount * bitsPerSample / 8

        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcmDataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channelCount.toShort())
            putInt(config.sampleRateHz)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmDataSize)
        }.array()
    }
}
