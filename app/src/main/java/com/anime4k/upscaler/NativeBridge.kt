package com.anime4k.upscaler

object NativeBridge {
    init {
        System.loadLibrary("anime4k_android")
    }

    external fun processImage(
        inputPath: String,
        outputPath: String,
        mode: String,
        scale: Double,
        quality: String,
        iterations: Int,
        pcs: Double,
        pgs: Double,
        denoise: String,
        deblur: String,
        lineDarken: Double,
        lineThin: Double,
        clampHighlights: Boolean,
    ): String
}
