#!/bin/bash
# 轻量构建：aapt2 + javac + d8 + apksigner（不依赖 Gradle/Kotlin，内存占用小）
set -e
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH
BT=/opt/android/sdk/build-tools/34.0.0
AJ=/opt/android/sdk/platforms/android-34/android.jar
SRC=/root/apk/panel
OUT=/root/apk/panel-build
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$AJ" \
  --manifest "$SRC/AndroidManifest.xml" \
  "$OUT/res.zip" --java "$OUT/gen" \
  --min-sdk-version 26 --target-sdk-version 34 --version-code 15 --version-name 3.5

javac -nowarn -source 11 -target 11 -encoding UTF-8 \
  -classpath "$AJ" -d "$OUT/classes" \
  $(find "$SRC/java" "$OUT/gen" -name '*.java')

"$BT/d8" --lib "$AJ" --min-api 26 --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')

cp "$OUT/base.apk" "$OUT/app.apk"
(cd "$OUT/dex" && zip -q -j "$OUT/app.apk" classes.dex)
"$BT/zipalign" -f 4 "$OUT/app.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks /root/apk/release.keystore --ks-pass pass:***已打码*** --key-pass pass:***已打码*** \
  --out /root/apk/saisqi-console-v2.apk "$OUT/aligned.apk"
"$BT/apksigner" verify /root/apk/saisqi-console-v2.apk >/dev/null && echo "签名校验通过"
ls -lh /root/apk/saisqi-console-v2.apk