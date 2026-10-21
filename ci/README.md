# Signing key

`pulse.keystore` signs every release build (GitHub Actions and the Termux script), so a new APK installs over the previous one.
The key is public because this repository is. It only proves that an APK came from this project's builds; do not reuse it for anything else.
To move to a private key later, replace the file and the passwords in `app/build.gradle.kts` (installed copies must be uninstalled once).
