package com.flintlauncher.android.audio

/**
 * Android audio backend contract. Minecraft's OpenAL calls are routed through a
 * backend that writes PCM samples to an Android audio path. Two implementations:
 *  - [AudioTrackBackend]: built-in, uses android.media.AudioTrack (always available).
 *  - OpenAL Soft dynamic plugin: LGPL, loaded at runtime via dlopen from a plugin
 *    path (see [OpenAlDynamicPlugin]). Never bundled statically.
 *
 * Implementations must not throw on construction; [available] reports readiness.
 */
interface AudioBackend {
    val name: String
    val available: Boolean

    /** Initialize the output stream. Returns false if the device/format is unsupported. */
    fun init(sampleRate: Int = 44100, channels: Int = 2): Boolean

    /** Write interleaved PCM samples. Returns the number of frames accepted. */
    fun write(samples: ShortArray): Int

    /** Master volume 0.0..1.0 (clamped). Applied immediately. */
    fun setVolume(volume: Float)

    fun pause()
    fun resume()
    fun release()
}

/** Volume 0..1 clamped to the valid range. */
object Volume {
    const val MIN = 0f
    const val MAX = 1f
    const val DEFAULT = 0.8f
    fun clamp(value: Float): Float = value.coerceIn(MIN, MAX)
}

/**
 * Holds the master volume and applies it to the active backend. Volume is kept in
 * memory; persistence (SharedPreferences/DataStore) is a UI-layer concern.
 */
class VolumeController(initial: Float = Volume.DEFAULT) {
    @Volatile
    var volume: Float = Volume.clamp(initial)
        private set

    @Volatile
    private var backend: AudioBackend? = null

    fun attach(backend: AudioBackend) {
        this.backend = backend
        backend.setVolume(volume)
    }

    fun detach() {
        backend = null
    }

    fun set(value: Float) {
        volume = Volume.clamp(value)
        backend?.setVolume(volume)
    }
}
