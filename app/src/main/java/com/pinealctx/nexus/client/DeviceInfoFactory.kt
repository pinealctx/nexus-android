package com.pinealctx.nexus.client

import android.os.Build
import com.api.v1.DeviceInput
import com.api.v1.deviceInput
import com.pinealctx.nexus.BuildConfig
import com.pinealctx.nexus.core.SecureStorage
import com.shared.v1.DeviceType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceInfoFactory @Inject constructor(
    private val secureStorage: SecureStorage
) {
    fun create(): DeviceInput {
        return deviceInput {
            deviceId = secureStorage.getDeviceId()
            deviceType = DeviceType.DEVICE_TYPE_ANDROID
            deviceName = Build.DEVICE
            deviceModel = Build.MODEL
            osVersion = "Android ${Build.VERSION.RELEASE}"
            appVersion = BuildConfig.VERSION_NAME
        }
    }
}
