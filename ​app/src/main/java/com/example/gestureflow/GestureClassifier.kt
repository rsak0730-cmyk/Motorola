package com.example.gestureflow

import android.content.res.AssetManager
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class GestureClassifier(assetManager: AssetManager) {
    private val interpreter: Interpreter

    init {
        interpreter = Interpreter(loadModelFile(assetManager, "gesture_model.tflite"))
    }

    private fun loadModelFile(assetManager: AssetManager, modelPath: String): ByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun predict(inputFeatures: FloatArray): FloatArray {
        val inputBuffer = ByteBuffer.allocateDirect(inputFeatures.size * 4).apply {
            order(ByteOrder.nativeOrder())
            for (value in inputFeatures) {
                putFloat(value)
            }
        }

        // Classes order: [chop, idle, twist]
        val outputBuffer = Array(1) { FloatArray(3) }
        interpreter.run(inputBuffer, outputBuffer)
        return outputBuffer[0]
    }
}

