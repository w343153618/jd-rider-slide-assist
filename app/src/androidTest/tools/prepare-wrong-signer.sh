#!/bin/sh
# Creates only a disposable, differently signed test APK for the fixture build.
set -eu
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK}"
workdir=$(mktemp -d "${TMPDIR:-/tmp}/slide-wrong-signer.XXXXXX")
trap 'rm -rf "$workdir"' EXIT HUP INT TERM
cat > "$workdir/AndroidManifest.xml" <<'MANIFEST'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.slideassist.testfixture" android:versionCode="9999" android:versionName="9.9.9"><uses-sdk android:minSdkVersion="24"/><application android:label="wrong signer test"/></manifest>
MANIFEST
"$ANDROID_HOME/build-tools/35.0.0/aapt" package -f -M "$workdir/AndroidManifest.xml" -I "$ANDROID_HOME/platforms/android-35/android.jar" -F "$workdir/unsigned.apk"
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -f 4 "$workdir/unsigned.apk" "$workdir/aligned.apk"
"$JAVA_HOME/bin/keytool" -genkeypair -alias test -keystore "$workdir/wrong.jks" -storepass testpass -keypass testpass -keyalg RSA -keysize 2048 -validity 1 -dname 'CN=Isolated Test Only' -noprompt
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign --ks "$workdir/wrong.jks" --ks-key-alias test --ks-pass pass:testpass --key-pass pass:testpass --out "$workdir/wrong.apk" "$workdir/aligned.apk"
adb push "$workdir/wrong.apk" /data/local/tmp/slide-wrong-signer.apk
