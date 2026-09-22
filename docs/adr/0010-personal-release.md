# ADR 0010: Personal signed release

Status: accepted

Click Downloader uses one long-lived external signing key for all `com.clickdownloader.app` updates. Signing paths and passwords are supplied outside Gradle source control. Release artifacts are minified, resource-shrunk and split by ABI, with a universal APK retained only for recovery/convenience.

Version 1.0.0 uses `versionCode` 10. Build properties can override name/code solely to produce a previous-version fixture for update testing. Every distributed APK must pass `apksigner` certificate verification and SHA-256 recording. APK binaries and signing files are ignored by Git.

The release is personal sideload distribution. No silent installer or signing secret is embedded in the app, repository or GitHub workflow.
