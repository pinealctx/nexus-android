package com.pinealctx.nexus.client

import com.api.v1.CompleteUploadRequest
import com.api.v1.GetDownloadURLRequest
import com.api.v1.InitUploadRequest
import com.api.v1.UploadChunkRequest
import com.api.v1.UploadFileRequest
import com.google.protobuf.ByteString
import com.pinealctx.nexus.core.MediaFileData
import com.pinealctx.nexus.core.UploadSessionData
import com.shared.v1.MediaFileInfo
import com.shared.v1.MediaPurpose
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaApi @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val headers: RpcHeaders
) {
    suspend fun getDownloadUrl(fileId: String): String {
        return apiClientFactory.createClients()
            .media
            .getDownloadURL(
                request = GetDownloadURLRequest.newBuilder()
                    .setFileId(fileId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
            .url
    }

    suspend fun uploadFile(data: ByteArray, fileName: String, contentType: String, purpose: Int): MediaFileData {
        return apiClientFactory.createClients()
            .media
            .uploadFile(
                request = UploadFileRequest.newBuilder()
                    .setFileName(fileName)
                    .setContentType(contentType)
                    .setPurpose(purpose.toMediaPurpose())
                    .setData(ByteString.copyFrom(data))
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
            .file
            .toData()
    }

    suspend fun initUpload(fileName: String, contentType: String, size: Long): UploadSessionData {
        val response = apiClientFactory.createClients()
            .media
            .initUpload(
                request = InitUploadRequest.newBuilder()
                    .setFileName(fileName)
                    .setContentType(contentType)
                    .setSize(size)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()

        return UploadSessionData(response.sessionId, response.uploaded, response.createdAt)
    }

    suspend fun uploadChunk(sessionId: String, chunk: ByteArray, offset: Long) {
        apiClientFactory.createClients()
            .media
            .uploadChunk(
                request = UploadChunkRequest.newBuilder()
                    .setSessionId(sessionId)
                    .setChunk(ByteString.copyFrom(chunk))
                    .setOffset(offset)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun completeUpload(sessionId: String): MediaFileData {
        return apiClientFactory.createClients()
            .media
            .completeUpload(
                request = CompleteUploadRequest.newBuilder()
                    .setSessionId(sessionId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
            .file
            .toData()
    }
}

private fun Int.toMediaPurpose(): MediaPurpose =
    when (this) {
        MediaPurpose.MEDIA_PURPOSE_AVATAR.number -> MediaPurpose.MEDIA_PURPOSE_AVATAR
        MediaPurpose.MEDIA_PURPOSE_GROUP_AVATAR.number -> MediaPurpose.MEDIA_PURPOSE_GROUP_AVATAR
        MediaPurpose.MEDIA_PURPOSE_MESSAGE.number -> MediaPurpose.MEDIA_PURPOSE_MESSAGE
        else -> MediaPurpose.MEDIA_PURPOSE_UNSPECIFIED
    }

private fun MediaFileInfo.toData(): MediaFileData =
    MediaFileData(
        fileId = fileId,
        fileName = fileName,
        contentType = contentType,
        size = size,
        width = width,
        height = height,
        durationMs = durationMs,
        thumbnailFileId = thumbnailFileId,
        publicUrl = publicUrl
    )
