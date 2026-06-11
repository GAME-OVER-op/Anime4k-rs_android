package com.anime4k.upscaler

object NativeBridge {
    init {
        System.loadLibrary("anime4k_android")
    }

    external fun processClassicImage(
        inputPath: String,
        outputPath: String,
        scale: Double,
        iterations: Int,
        pcs: Double,
        pgs: Double,
    ): String
}
