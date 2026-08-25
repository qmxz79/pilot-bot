# fetch-amap-sdk.ps1 — 下载并解包高德导航 SDK（jar + .so）到 app 模块
#
# 背景: maven.amap.com 已永久不可用（2026-08 起），SDK 改为从高德官网合包离线引入。
# 用法: 仓库根目录运行  powershell -ExecutionPolicy Bypass -File .\fetch-amap-sdk.ps1
# 幂等: jar 和全部 .so 都在则跳过；下载支持断点续传，中断后重跑即可。
$ErrorActionPreference = "Stop"

$ZipUrl = "https://amappc.cn-hangzhou.oss-pub.aliyun-inc.com/lbs/static/zip/AMap_Android_Navi_SDK_All.zip"
$RepoRoot = $PSScriptRoot
$JarOut   = Join-Path $RepoRoot "android\app\libs\amap-all.jar"
$JniRoot  = Join-Path $RepoRoot "android\app\src\main\jniLibs"
$Abis     = @("arm64-v8a", "armeabi-v7a")

$needs = -not (Test-Path $JarOut)
foreach ($abi in $Abis) { if (-not (Test-Path (Join-Path $JniRoot "$abi\*.so"))) { $needs = $true } }

if ($needs) {
    $Zip = Join-Path $env:TEMP "AMap_Android_Navi_SDK_All.zip"
    $layer1 = Join-Path $env:TEMP "amap_sdk_all"
    $layer2 = Join-Path $env:TEMP "amap_sdk_layer"
    Write-Host "下载 SDK 合包(224MB, 支持断点续传)..."
    if (-not (Test-Path $Zip)) { curl.exe -L -C - -o $Zip $ZipUrl }
    Remove-Item -Recurse -Force $layer1, $layer2 -ErrorAction SilentlyContinue
    Expand-Archive -Path $Zip -DestinationPath $layer1 -Force
    $inner = Get-ChildItem "$layer1\*AMap3DMap*.zip" | Select-Object -First 1
    if (-not $inner) { throw "合包内未找到 SDK zip: $Zip" }
    Expand-Archive -Path $inner.FullName -DestinationPath $layer2 -Force
    $jar = Get-ChildItem "$layer2\*.jar" | Select-Object -First 1
    if (-not $jar) { throw "SDK 层未找到 jar" }
    New-Item -ItemType Directory -Force (Split-Path $JarOut) | Out-Null
    Copy-Item $jar.FullName $JarOut -Force
    Write-Host "jar -> $JarOut"
    foreach ($abi in $Abis) {
        if (Test-Path "$layer2\$abi\*.so") {
            $soDir = Join-Path $JniRoot $abi
            New-Item -ItemType Directory -Force $soDir | Out-Null
            Copy-Item "$layer2\$abi\*.so" $soDir -Force
            Write-Host "so($abi) -> $soDir"
        }
    }
    Write-Host "完成: $JarOut"
} else {
    Write-Host "SDK 已就绪, 无需下载"
}
