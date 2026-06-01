package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.core.MediaFileData
import com.pinealctx.nexus.core.NexusClientProvider
import com.pinealctx.nexus.core.UploadSessionData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaManager @Inject constructor(
    private val clientProvider: NexusClientProvider
) {
    fun getMediaUrl(fileId: String): String = clientProvider.getOrNull()?.getMediaUrl(fileId) ?: ""

    fun getDownloadUrl(fileId: String): String = clientProvider.getOrNull()?.getDownloadUrl(fileId) ?: ""

    fun uploadFile(data: ByteArray, fileName: String, contentType: String, purpose: Int): MediaFileData {
        val result = clientProvider.get().uploadFile(data, fileName, contentType, purpose)
        return result.toMediaFileData()
    }

    fun initUpload(fileName: String, contentType: String, size: Long): UploadSessionData {
        val result = clientProvider.get().initUpload(fileName, contentType, size)
        return UploadSessionData(result.sessionId, result.uploaded, result.createdAt)
    }

    fun uploadChunk(sessionId: String, chunk: ByteArray, offset: Long) {
        clientProvider.get().uploadChunk(sessionId, chunk, offset)
    }

    fun completeUpload(sessionId: String): MediaFileData {
        val result = clientProvider.get().completeUpload(sessionId)
        return result.toMediaFileData()
    }
}

private fun uniffi.nexus_ffi.MediaFileInfoFfi.toMediaFileData() = MediaFileData(
    fileId, fileName, contentType, size, width, height, durationMs, thumbnailFileId, publicUrl
)
