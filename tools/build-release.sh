#!/usr/bin/env bash
# 打一个可以发出去的 release 包。
# 用法：bash tools/build-release.sh
set -euo pipefail
cd "$(dirname "$0")/.."

GRADLE="../buildenv/gradle-8.7/bin/gradle"
[ -x "$GRADLE" ] || GRADLE="gradle"

VERSION=$(grep -o 'versionName = "[^"]*"' app/build.gradle.kts | head -1 | cut -d'"' -f2)
echo "==> 版本 $VERSION"

"$GRADLE" clean assembleRelease testDebugUnitTest --console=plain

OUT="DayTrace-v${VERSION}-release.apk"
cp app/build/outputs/apk/release/app-release.apk "$OUT"

APKSIGNER="../buildenv/sdk/build-tools/34.0.0/apksigner.bat"
if [ -f "$APKSIGNER" ]; then
  echo "==> 验签"
  "$APKSIGNER" verify --print-certs "$OUT" | head -4
fi

ls -lh "$OUT"
echo "==> 完成：$OUT"