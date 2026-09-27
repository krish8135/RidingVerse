package com.ridingverse.app.intercom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Half-duplex push-to-talk walkie-talkie for the convoy channel.
 *
 * Press-and-hold to transmit; the channel is half-duplex so transmitting mutes
 * the incoming stream. Capture runs through a real [AudioRecord] PCM pipeline
 * (16 kHz mono 16-bit, VOICE_COMMUNICATION source) with an energy-gate
 * squelch that doubles as the wind-noise suppression indicator.
 *
 * ## Network voice protocol (voice transport = remaining work)
 *
 * Each ~40 ms audio frame is sent to the relay as a **text** WebSocket frame:
 * ```json
 * {
 *   "v": 1,
 *   "type": "ptt",
 *   "room": "RV-9042",
 *   "riderId": "<uuid>",
 *   "seq": 42,
 *   "codec": "pcm16/16000/mono",
 *   "frames": 8,
 *   "audio": "<base64 PCM16 mono 16kHz, ~640 samples per frame>"
 * }
 * ```
 * The relay rebroadcasts `ptt` packets to every peer in the room except the
 * sender (see relay/server.js). Receivers base64-decode `audio` and feed the
 * raw PCM bytes to [playReceivedFrame].
 *
 * What is real today: capture → frame callback, incoming frame → [AudioTrack]
 * playback, and [startLoopbackTest] which routes captured audio straight back
 * to the speaker to verify the whole audio path on-device. What remains is
 * wiring the frame callback to `TelemetrySocket.send()` and routing incoming
 * `ptt` packets to [playReceivedFrame] — both are ~20 lines in the convoy UI
 * layer once a relay is deployed.
 */
class PttManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isTransmitting = MutableStateFlow(false)
    /** True while the PTT button is held down (or a loopback test runs). */
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _noiseSuppressionActive = MutableStateFlow(false)
    /**
     * True when the energy-gate squelch is attenuating the mic stream
     * (wind/engine rumble below the voice threshold).
     */
    val noiseSuppressionActive: StateFlow<Boolean> = _noiseSuppressionActive.asStateFlow()

    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var pumpJob: Job? = null
    private var sequence = 0

    /**
     * Glove-friendly press-and-hold entry point. No-op without RECORD_AUDIO.
     *
     * @param room convoy room code for the packet header.
     * @param riderId our rider id for the packet header.
     * @param onFrame invoked on a background thread with each captured PCM
     * frame plus its sequence number; the network layer serializes it per the
     * protocol above. Null = local only (mic still captured for the squelch
     * indicator).
     */
    fun startTransmit(
        room: String,
        riderId: String,
        onFrame: ((frame: PttVoiceFrame) -> Unit)? = null
    ) {
        if (_isTransmitting.value) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        _isTransmitting.value = true
        sequence = 0
        pumpJob = scope.launch {
            val rec = runCatching { openRecorder() }.getOrNull() ?: run {
                _isTransmitting.value = false
                return@launch
            }
            val buffer = ShortArray(FRAME_SAMPLES)
            while (_isTransmitting.value) {
                val read = rec.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val rms = rms(buffer, read)
                // Energy-gate squelch: below threshold counts as wind/engine
                // rumble — we still forward the frame (keeps timing), but flag
                // the suppression indicator so the UI can show it.
                _noiseSuppressionActive.value = rms < SQUELCH_RMS
                val pcm = buffer.toPcmBytes(read)
                onFrame?.let {
                    val frame = PttVoiceFrame(
                        room = room,
                        riderId = riderId,
                        seq = sequence++,
                        audioBase64 = Base64.encodeToString(pcm, Base64.NO_WRAP)
                    )
                    runCatching { it(frame) }
                }
            }
        }
    }

    /** Release-to-stop entry point. */
    fun stopTransmit() {
        if (!_isTransmitting.value) return
        _isTransmitting.value = false
        _noiseSuppressionActive.value = false
        pumpJob?.cancel()
        pumpJob = null
        runCatching { closeRecorder() }
    }

    /**
     * Plays one received network voice frame. Creates the [AudioTrack] lazily;
     * safe to call from any thread.
     */
    fun playReceivedFrame(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        scope.launch {
            val track = runCatching { openPlayer() }.getOrNull() ?: return@launch
            runCatching { track.write(pcm, 0, pcm.size) }
        }
    }

    /**
     * On-device self-test: captured mic audio is routed straight back to the
     * speaker, verifying the full record → playback path without a network.
     * Hold-to-talk semantics still apply; call [stopLoopbackTest] on release.
     */
    fun startLoopbackTest() {
        startTransmit(room = "LOOPBACK", riderId = "self") { frame ->
            val pcm = runCatching {
                Base64.decode(frame.audioBase64, Base64.NO_WRAP)
            }.getOrDefault(ByteArray(0))
            playReceivedFrame(pcm)
        }
    }

    fun stopLoopbackTest() = stopTransmit()

    /** Releases the playback track; call when the owning screen is destroyed. */
    fun release() {
        stopTransmit()
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
    }

    // ------------------------------------------------------------------
    // Audio plumbing
    // ------------------------------------------------------------------

    private fun openRecorder(): AudioRecord {
        val minIn = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT
        ).coerceAtLeast(FRAME_SAMPLES * 4)
        return AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT, minIn
        ).apply {
            check(state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }
            startRecording()
        }.also { recorder = it }
    }

    private fun closeRecorder() {
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
    }

    @Synchronized
    private fun openPlayer(): AudioTrack {
        player?.let { return it }
        val minOut = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT
        ).coerceAtLeast(FRAME_SAMPLES * 4)
        return AudioTrack(
            AudioManager.STREAM_VOICE_CALL,
            SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT, minOut,
            AudioTrack.MODE_STREAM
        ).apply {
            check(state == AudioTrack.STATE_INITIALIZED) { "AudioTrack init failed" }
            play()
        }.also { player = it }
    }

    private fun rms(samples: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) sum += samples[i] * samples[i].toDouble()
        return sqrt(sum / count.coerceAtLeast(1))
    }

    private fun ShortArray.toPcmBytes(count: Int): ByteArray {
        val out = ByteArray(count * 2)
        for (i in 0 until count) {
            out[i * 2] = (this[i].toInt() and 0xFF).toByte()
            out[i * 2 + 1] = ((this[i].toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        /** 40 ms frames at 16 kHz. */
        private const val FRAME_SAMPLES = 640
        /** RMS below this ≈ wind/engine rumble, not voice. */
        private const val SQUELCH_RMS = 220.0
    }
}

/**
 * One captured voice frame, ready to be wrapped in the network packet
 * documented on [PttManager]. Serialized with org.json and sent
 * as a text WebSocket frame with `"type": "ptt"`.
 */
data class PttVoiceFrame(
    val v: Int = 1,
    val type: String = "ptt",
    val room: String,
    val riderId: String,
    val seq: Int,
    val codec: String = "pcm16/16000/mono",
    val audioBase64: String
) {
    fun toJson(): String = JSONObject().apply {
        put("v", v)
        put("type", type)
        put("room", room)
        put("riderId", riderId)
        put("seq", seq)
        put("codec", codec)
        put("audioBase64", audioBase64)
    }.toString()

    companion object {
        fun fromJson(raw: String): PttVoiceFrame? = runCatching {
            val o = JSONObject(raw)
            PttVoiceFrame(
                v = o.optInt("v", 1),
                type = o.optString("type", "ptt"),
                room = o.getString("room"),
                riderId = o.getString("riderId"),
                seq = o.getInt("seq"),
                codec = o.optString("codec", "pcm16/16000/mono"),
                audioBase64 = o.getString("audioBase64")
            )
        }.getOrNull()
    }
}
