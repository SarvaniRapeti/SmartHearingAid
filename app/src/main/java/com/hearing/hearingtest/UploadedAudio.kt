package com.hearing.hearingtest

data class UploadedAudio(
    val id: Long,
    val name: String,
    val filePath: String,
    val sizeBytes: Long,
    val timestamp: Long
)
