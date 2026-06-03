param(
    [string]$AvdName = "nexus_test",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

function Get-LocalProperty {
    param([string]$Name)

    if (!(Test-Path "local.properties")) {
        return $null
    }

    $line = Get-Content "local.properties" |
        Where-Object { $_ -match "^\s*$Name\s*=" } |
        Select-Object -First 1
    if (!$line) {
        return $null
    }

    return ($line -replace "^\s*$Name\s*=", "").Trim()
}

function Wait-ForBoot {
    param([string]$Adb)

    $deadline = (Get-Date).AddMinutes(4)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        $deviceLines = & $Adb devices | Where-Object { $_ -match "\tdevice$" }
        if ($deviceLines) {
            $boot = (& $Adb shell getprop sys.boot_completed 2>$null).Trim()
            if ($boot -eq "1") {
                return
            }
        }
        Write-Host "Waiting for emulator boot..."
    }

    throw "Timed out waiting for emulator boot."
}

$sdkDir = Get-LocalProperty "sdk.dir"
if (!$sdkDir) {
    $sdkDir = $env:ANDROID_HOME
}
if (!$sdkDir) {
    $sdkDir = $env:ANDROID_SDK_ROOT
}
if (!$sdkDir -or !(Test-Path $sdkDir)) {
    throw "Android SDK not found. Set local.properties sdk.dir, ANDROID_HOME, or ANDROID_SDK_ROOT."
}

$adb = Join-Path $sdkDir "platform-tools/adb.exe"
$emulator = Join-Path $sdkDir "emulator/emulator.exe"
if (!(Test-Path $adb)) {
    throw "adb not found: $adb"
}
if (!(Test-Path $emulator)) {
    throw "emulator not found: $emulator"
}

if (!$SkipBuild) {
    & ./gradlew.bat assembleDebug --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "assembleDebug failed"
    }
}

$deviceLines = & $adb devices | Where-Object { $_ -match "\tdevice$" }
if (!$deviceLines) {
    $availableAvds = & $emulator -list-avds
    if ($availableAvds -notcontains $AvdName) {
        throw "AVD '$AvdName' not found. Available AVDs: $($availableAvds -join ', ')"
    }

    Start-Process -FilePath $emulator -ArgumentList @("-avd", $AvdName) -WorkingDirectory (Split-Path $emulator)
}

Wait-ForBoot -Adb $adb

$apk = Resolve-Path "app/build/outputs/apk/debug/app-debug.apk"
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) {
    throw "APK install failed"
}

& $adb logcat -c
& $adb shell am force-stop "com.pinealctx.nexus"
& $adb shell am start -n "com.pinealctx.nexus/.MainActivity"
if ($LASTEXITCODE -ne 0) {
    throw "MainActivity launch failed"
}

Start-Sleep -Seconds 8
$appPid = (& $adb shell pidof com.pinealctx.nexus).Trim()
if (!$appPid) {
    & $adb logcat -d -t 300 | Select-String -Pattern "FATAL EXCEPTION|AndroidRuntime|UnsatisfiedLinkError|NexusApp"
    throw "App process is not running after launch."
}

$fatal = & $adb logcat -d -t 300 |
    Select-String -Pattern "FATAL EXCEPTION|AndroidRuntime|UnsatisfiedLinkError|Failed to initialize core"
if ($fatal) {
    $fatal
    throw "Fatal logcat entries found after launch."
}

Write-Host "Nexus Android is running. pid=$appPid"
