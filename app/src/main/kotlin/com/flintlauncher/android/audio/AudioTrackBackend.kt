package com.flintlauncher.android.audio

import android.annotation.TargetApi
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build

/**
 * Built-in AudioTrack backend skeleton. Writes 16-bit interleaved PCM to a streaming
 * AudioTrack. This is the fallback when the OpenAL Soft dynamic plugin is unavailable.
 *
 * Spike-0 status: the lifecycle (init/write/volume/pause/release) is wired but no
 * real audio data is produced yet — Minecraft's sound engine must route samples here.
 * Marked honestly: [available] reflects whether an AudioTrack could be created.
 */
class AudioTrackBackend : AudioBackend {

    override val name: String = "AudioTrack"

    @Volatile
    private var track: AudioTrack? = null

    override val available: Boolean
        get() = track?.state == AudioTrack.STATE_INITIALIZED

    @Volatile
    private var volume: Float = Volume.DEFAULT

    override fun init(sampleRate: Int, channels: Int): Boolean {
        release()
        val channelConfig = if (channels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        val bufferSize = minBuffer * 4
        return try {
            val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                createPostM(sampleRate, channelConfig, bufferSize)
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                )
            }
            if (created.state != AudioTrack.STATE_INITIALIZED) {
                created.release()
                return false
            }
            track = created
            setVolume(volume)
            created.play()
            true
        } catch (e: Exception) {
            false
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun createPostM(sampleRate: Int, channelConfig: Int, bufferSize: Int): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(channelConfig)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

    override fun write(samples: ShortArray): Int {
        val t = track ?: return 0
        return t.write(samples, 0, samples.size)
    }

    override fun setVolume(volume: Float) {
        this.volume = Volume.clamp(volume)
        val t = track ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            t.setVolume(this.volume)
        } else {
            @Suppress("DEPRECATION")
            t.setStereoVolume(this.volume, this.volume)
        }
    }

    override fun pause() {
        runCatching { track?.pause() }
    }

    override fun resume() {
        runCatching { track?.play() }
    }

    override fun release() {
        runCatching {
            track?.stop()
            track?.release()
        }
        track = null
    }
}
