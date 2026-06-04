package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.MediaApi
import com.pinealctx.nexus.core.MediaFileData
import com.pinealctx.nexus.core.UploadSessionData
import com.pinealctx.nexus.local.LocalDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class MediaManager @Inject constructor(
    private val mediaApi: MediaApi,
    private val localDataStore: LocalDataStore
) {
    fun getMediaUrl(fileId: String): String = getDownloadUrl(fileId)

    fun getDownloadUrl(fileId: String): String {
        val url = runBlocking { mediaApi.getDownloadUrl(fileId) }
        localDataStore.updateMediaPublicUrl(fileId, url)
        return url
    }

    fun uploadFile(data: ByteArray, fileName: String, contentType: String, purpose: Int): MediaFileData =
        runBlocking { mediaApi.uploadFile(data, fileName, contentType, purpose) }
            .also { localDataStore.upsertMediaFile(it) }

    fun initUpload(fileName: String, contentType: String, size: Long): UploadSessionData =
        runBlocking { mediaApi.initUpload(fileName, contentType, size) }

    fun uploadChunk(sessionId: String, chunk: ByteArray, offset: Long) {
        runBlocking { mediaApi.uploadChunk(sessionId, chunk, offset) }
    }

    fun completeUpload(sessionId: String): MediaFileData =
        runBlocking { mediaApi.completeUpload(sessionId) }
            .also { localDataStore.upsertMediaFile(it) }
}
