# Signing and recovery

Release signing material must stay outside this repository. The build accepts the path to an external properties file through `-PclickDownloaderKeystoreProperties=...` or `CLICK_DOWNLOADER_KEYSTORE_PROPERTIES`.

The file format is:

```properties
storeFile=C:/absolute/private/path/click-downloader-release.jks
storePassword=private-value
keyAlias=click-downloader
keyPassword=private-value
```

Build with:

```powershell
.\gradlew.bat :app:assembleRelease --no-configuration-cache -PclickDownloaderKeystoreProperties='C:/private/keystore.properties'
```

Disabling the configuration cache for a signing build prevents signing credentials from being serialized into the local Gradle cache and ensures a newly selected external key/version is applied. Normal unsigned CI builds can continue using the cache.

## Recovery procedure

1. Keep at least two encrypted copies of the JKS and its password record, with one copy off the development PC.
2. Record SHA-256 hashes for each copy and periodically verify them.
3. Restore both files to a private directory and run `keytool -list -v` to confirm alias and certificate fingerprint.
4. Build a higher `versionCode` with the same key, verify it using `apksigner verify --verbose --print-certs`, then test `adb install -r` over the previous signed APK.
5. Never regenerate a key for an existing package: Android will reject it as an update.

The automated Phase 10 run creates and hash-verifies two local copies outside the repository. The owner must still make and confirm an encrypted off-device backup; the application cannot honestly verify that external action.
