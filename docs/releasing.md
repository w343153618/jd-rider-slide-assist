# 版本发布与升级

每次发布递增 `app/build.gradle` 中的 versionCode 和 versionName，更新 CHANGELOG、验证记录，完成主机测试、Android 构建/lint及受影响的隔离设备测试。已有版本标签和发布文件保持不可变；有修复时发布新版本。

## 签名

正式版使用本地受保护的 `.private/release.jks` 与 `.private/signing.properties`，两者均不进入 Git。后续签名必须保持一致，发布者应另行安全备份；丢失签名私钥会无法覆盖升级，不能从 APK 恢复私钥。

签名属性模板如下，真实密码不得放入公共仓库：

```properties
storeFile=release.jks
storePassword=YOUR_LOCAL_SECRET
keyAlias=release
keyPassword=YOUR_LOCAL_SECRET
```

无签名配置时仍可构建 debug 测试包；正式发布必须使用签名 release 包。早期 debug 包不能直接覆盖升级到新的正式签名，首次需要重新安装；正式版之间使用同一签名覆盖升级。

## 产物与发布

```sh
./gradlew :app:assembleRelease :app:lintRelease
mkdir -p artifacts/release
cp app/build/outputs/apk/release/app-release.apk artifacts/release/jd-rider-slide-assist.apk
(cd artifacts/release && shasum -a 256 jd-rider-slide-assist.apk > jd-rider-slide-assist.apk.sha256)
```

在已验证提交上创建 `vX.Y.Z` 标签，在固定仓库 `w343153618/jd-rider-slide-assist` 创建正式 GitHub Release，附带更新说明，并上传且保留以下固定文件名：

- `jd-rider-slide-assist.apk`
- `jd-rider-slide-assist.apk.sha256`
- `update.json`：包含 tag_name、body、draft=false、prerelease=false 和上述两个资源的 assets（name/browser_download_url），资源 URL 指向本仓库的相同版本标签。

发布后回读 API 的最新版本、资源名及摘要，下载一次对照本地签名产物；验证应用内检查更新与升级安装。APK 的 versionName 必须和发布标签一致。不要将测试夹具、debug 签名包或有不同包名的 APK 作为更新资产。

## 用户流程和边界

只有主动点按检查/下载才联网。更新检查下载 GitHub 最新正式发布的固定 update.json，不使用有匿名配额限制的 API，不需要用户账号或 Token。网络失败可稍后重试，旧版功能仍可继续使用。

下载后校验摘要和包信息，拒绝不同包、非递增版本或签名证书不匹配的文件。缓存按摘要命名，不替换已授予系统安装器读取权限的文件。用户在系统设置允许安装来源后，仍需点击确认安装。操作取消、关闭应用或文件异常不会触发静默安装。
