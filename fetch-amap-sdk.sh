#!/usr/bin/env bash
# fetch-amap-sdk.sh — 下载并解包高德导航 SDK（jar + .so）到 app 模块（macOS/Linux）
#
# 背景: maven.amap.com 已永久不可用（2026-08 起），SDK 改为从高德官网合包离线引入。
# 用法: 仓库根目录运行  ./fetch-amap-sdk.sh
# 幂等: jar 和全部 .so 都在则跳过；下载支持断点续传，中断后重跑即可。
set -euo pipefail

ZIP_URL="https://amappc.cn-hangzhou.oss-pub.aliyun-inc.com/lbs/static/zip/AMap_Android_Navi_SDK_All.zip"
ROOT="$(cd "$(dirname "$0")" && pwd)"
JAR_OUT="$ROOT/android/app/libs/amap-all.jar"
JNI_ROOT="$ROOT/android/app/src/main/jniLibs"
ABIS="arm64-v8a armeabi-v7a"

needs=0
[ -f "$JAR_OUT" ] || needs=1
for abi in $ABIS; do [ -n "$(ls "$JNI_ROOT/$abi"/*.so 2>/dev/null)" ] || needs=1; done

if [ "$needs" = "1" ]; then
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  echo "下载 SDK 合包(224MB, 支持断点续传)..."
  curl -L -C - -o "$TMP/all.zip" "$ZIP_URL"
  cd "$TMP"
  unzip -q all.zip
  unzip -q AMap3DMap*.zip -d layer
  jar="$(find layer -maxdepth 1 -name '*.jar' | head -1)"
  [ -n "$jar" ] || { echo "SDK 层未找到 jar"; exit 1; }
  mkdir -p "$(dirname "$JAR_OUT")" "$JNI_ROOT"
  cp "$jar" "$JAR_OUT"
  echo "jar -> $JAR_OUT"
  for abi in $ABIS; do
    if ls "layer/$abi"/*.so >/dev/null 2>&1; then
      mkdir -p "$JNI_ROOT/$abi"
      cp "layer/$abi"/*.so "$JNI_ROOT/$abi/"
      echo "so($abi) -> $JNI_ROOT/$abi"
    fi
  done
  echo "完成: $JAR_OUT"
else
  echo "SDK 已就绪, 无需下载"
fi
