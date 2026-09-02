package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.MediaApi
import com.pinealctx.nexus.core.MediaFileData
import com.pinealctx.nexus.core.UploadSessionData
import com.pinealctx.nexus.local.LocalDataStore
import com.shared.v1.MediaPurpose
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaManager @Inject constructor(
    private val mediaApi: MediaApi,
    private val localDataStore: LocalDataStore
) {
    fun getCachedMediaUrl(fileId: String): String? =
        localDataStore.getMediaFile(fileId)?.publicUrl?.takeIf { it.isNotBlank() }

    suspend fun getMediaUrl(fileId: String): String = getDownloadUrl(fileId)

    suspend fun getDownloadUrl(fileId: String): String {
        val url = mediaApi.getDownloadUrl(fileId)
        localDataStore.updateMediaPublicUrl(fileId, url)
        return url
    }

    suspend fun uploadFile(
        data: ByteArray,
        fileName: String,
        contentType: String,
        purpose: MediaPurpose
    ): MediaFileData =
        mediaApi.uploadFile(data, fileName, contentType, purpose)
            .also { localDataStore.upsertMediaFile(it) }

    suspend fun initUpload(fileName: String, contentType: String, size: Long): UploadSessionData =
        mediaApi.initUpload(fileName, contentType, size)

    suspend fun uploadChunk(sessionId: String, chunk: ByteArray, offset: Long) {
        mediaApi.uploadChunk(sessionId, chunk, offset)
    }

    suspend fun completeUpload(sessionId: String): MediaFileData =
        mediaApi.completeUpload(sessionId)
            .also { localDataStore.upsertMediaFile(it) }

    suspend fun uploadStream(
        input: InputStream,
        fileName: String,
        contentType: String,
        size: Long,
        purpose: MediaPurpose
    ): MediaFileData {
        if (size in 0..SINGLE_UPLOAD_LIMIT_BYTES) {
            return uploadFile(input.readBytes(), fileName, contentType, purpose)
        }

        val session = initUpload(fileName, contentType, size)
        var offset = session.uploaded
        if (offset > 0) {
            var remaining = offset
            while (remaining > 0) {
                val skipped = input.skip(remaining)
                if (skipped <= 0) break
                remaining -= skipped
            }
        }

        val buffer = ByteArray(CHUNK_SIZE_BYTES)
        while (true) {
            val bytesRead = input.read(buffer)
            if (bytesRead < 0) break
            if (bytesRead == 0) continue
            val chunk = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead)
            uploadChunk(session.sessionId, chunk, offset)
            offset += bytesRead
        }
        return completeUpload(session.sessionId)
    }

    private companion object {
        const val SINGLE_UPLOAD_LIMIT_BYTES = 5L * 1024L * 1024L
        const val CHUNK_SIZE_BYTES = 5 * 1024 * 1024
    }
}
