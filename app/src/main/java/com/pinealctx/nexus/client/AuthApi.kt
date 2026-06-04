package com.pinealctx.nexus.client

import com.api.v1.GetClientConfigRequest
import com.api.v1.IdentityType
import com.api.v1.LoginPasswordResponse
import com.api.v1.LogoutAllRequest
import com.api.v1.LogoutRequest
import com.api.v1.VerifyCodeResponse
import com.api.v1.changePasswordRequest
import com.api.v1.loginPasswordRequest
import com.api.v1.refreshTokenRequest
import com.api.v1.requestVerifyCodeRequest
import com.api.v1.setupPasswordRequest
import com.api.v1.verifyCodeRequest
import com.pinealctx.nexus.core.ClientConfigData
import com.pinealctx.nexus.core.LoginResult
import com.pinealctx.nexus.core.SecureStorage
import com.pinealctx.nexus.core.VerifyCodeData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthApi @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val deviceInfoFactory: DeviceInfoFactory,
    private val headers: RpcHeaders,
    private val secureStorage: SecureStorage
) {
    suspend fun getClientConfig(): ClientConfigData {
        val response = apiClientFactory.createClients()
            .auth
            .getClientConfig(
                request = GetClientConfigRequest.getDefaultInstance(),
                headers = headers.current(includeAuth = false)
            )
            .requireMessage()

        return ClientConfigData(
            phoneEnabled = response.login.phoneEnabled,
            emailEnabled = response.login.emailEnabled,
            wsUrl = response.gateway.wsUrl.takeIf { it.isNotBlank() }
        )
    }

    suspend fun requestVerifyCode(identityType: Int, identityValue: String): VerifyCodeData {
        val response = apiClientFactory.createClients()
            .auth
            .requestVerifyCode(
                request = requestVerifyCodeRequest {
                    this.identityType = requireIdentityType(identityType)
                    this.identityValue = identityValue
                },
                headers = headers.current(includeAuth = false)
            )
            .requireMessage()

        return VerifyCodeData(
            verifyToken = response.verifyToken,
            expiresIn = response.expiresIn
        )
    }

    suspend fun verifyCode(verifyToken: String, code: String): LoginResult {
        val response = apiClientFactory.createClients()
            .auth
            .verifyCode(
                request = verifyCodeRequest {
                    this.verifyToken = verifyToken
                    this.code = code
                    deviceInfo = deviceInfoFactory.create()
                },
                headers = headers.current(includeAuth = false)
            )
            .requireMessage()

        return response.toLoginResult()
    }

    suspend fun loginPassword(identityType: Int, identityValue: String, password: String): LoginResult {
        val response = apiClientFactory.createClients()
            .auth
            .loginPassword(
                request = loginPasswordRequest {
                    this.identityType = requireIdentityType(identityType)
                    this.identityValue = identityValue
                    this.password = password
                    deviceInfo = deviceInfoFactory.create()
                },
                headers = headers.current(includeAuth = false)
            )
            .requireMessage()

        return response.toLoginResult()
    }

    suspend fun refreshAccessToken(): Boolean {
        val refreshToken = secureStorage.getRefreshToken()?.takeIf { it.isNotBlank() } ?: return false
        val userId = secureStorage.getUserId().takeIf { it > 0 } ?: return false
        val response = apiClientFactory.createClients()
            .auth
            .refreshToken(
                request = refreshTokenRequest {
                    this.refreshToken = refreshToken
                },
                headers = headers.current(includeAuth = false)
            )
            .requireMessage()

        secureStorage.saveTokens(response.accessToken, response.refreshToken, response.expiresIn, userId)
        return true
    }

    suspend fun logout() {
        apiClientFactory.createClients()
            .auth
            .logout(
                request = LogoutRequest.getDefaultInstance(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun logoutAll() {
        apiClientFactory.createClients()
            .auth
            .logoutAll(
                request = LogoutAllRequest.getDefaultInstance(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun setupPassword(password: String) {
        apiClientFactory.createClients()
            .auth
            .setupPassword(
                request = setupPasswordRequest {
                    newPassword = password
                },
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        apiClientFactory.createClients()
            .auth
            .changePassword(
                request = changePasswordRequest {
                    this.oldPassword = oldPassword
                    this.newPassword = newPassword
                },
                headers = headers.current()
            )
            .requireMessage()
    }

    private fun requireIdentityType(value: Int): IdentityType {
        val identityType = IdentityType.forNumber(value)
        require(identityType == IdentityType.IDENTITY_TYPE_EMAIL || identityType == IdentityType.IDENTITY_TYPE_PHONE) {
            "Unsupported identity type: $value"
        }
        return identityType
    }

    private fun VerifyCodeResponse.toLoginResult(): LoginResult {
        return LoginResult(
            userId = userId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = expiresIn,
            isNewUser = isNewUser
        )
    }

    private fun LoginPasswordResponse.toLoginResult(): LoginResult {
        return LoginResult(
            userId = userId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = expiresIn,
            isNewUser = false
        )
    }

}
