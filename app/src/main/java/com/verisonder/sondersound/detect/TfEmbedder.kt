package com.verisonder.sondersound.detect

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Two TFLite models from assets: audio to a 32-band mel spectrogram, and 76 mel frames to a
 * 96-number speech embedding. Interpreters are not thread-safe, so every call is serialised.
 */
class TfEmbedder private constructor(context: Context) : Embedder {

    private val mel = Interpreter(load(context, "melspectrogram.tflite"), Interpreter.Options().setNumThreads(2))
    private val embedding = Interpreter(load(context, "embedding_model.tflite"), Interpreter.Options().setNumThreads(2))
    private val melFrames: Int
    private val melBands: Int

    init {
        // The shipped mel model has its input fixed at 1.2 s (see docs/MODELS.md). The
        // original declared a 1-sample input, which the Interpreter constructor tries to
        // allocate and fails on before a resize can happen.
        mel.allocateTensors()
        val shape = mel.getOutputTensor(0).shape() // [1, 1, frames, bands]
        melFrames = shape[2]
        melBands = shape[3]
    }

    @Synchronized
    override fun embed(window: FloatArray, starts: (Int) -> List<Int>): List<FloatArray> {
        require(window.size == Features.WINDOW)
        val melOut = Array(1) { Array(1) { Array(melFrames) { FloatArray(melBands) } } }
        mel.run(arrayOf(window), melOut)
        val spectrogram = melOut[0][0]
        // The same scaling openWakeWord applies before the embedding model.
        for (row in spectrogram) for (b in row.indices) row[b] = row[b] / 10f + 2f

        val out = ArrayList<FloatArray>()
        for (s in starts(melFrames)) {
            val input = Array(1) { Array(Features.MEL_WINDOW) { f -> Array(melBands) { b -> floatArrayOf(spectrogram[s + f][b]) } } }
            val result = Array(1) { Array(1) { Array(1) { FloatArray(EMBEDDING_SIZE) } } }
            embedding.run(input, result)
            out += result[0][0][0]
        }
        return out
    }

    companion object {
        const val EMBEDDING_SIZE = 96

        @Volatile
        private var instance: TfEmbedder? = null

        fun get(context: Context): TfEmbedder =
            instance ?: synchronized(this) {
                instance ?: TfEmbedder(context.applicationContext).also { instance = it }
            }

        /** The mean embedding of ordinary speech, which every vector is centred on. */
        fun background(context: Context): FloatArray {
            val bytes = context.assets.open("background.f32").use { it.readBytes() }
            val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            return FloatArray(floats.remaining()).also { floats.get(it) }
        }

        private fun load(context: Context, name: String): ByteBuffer {
            val bytes = context.assets.open(name).use { it.readBytes() }
            return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
                put(bytes)
                rewind()
            }
        }
    }
}
