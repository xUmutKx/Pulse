# Signing key

Releases are signed with a private key that is not in this repository. It lives in the GitHub Actions secrets
`PULSE_KEYSTORE_B64` (the keystore, base64), `PULSE_STORE_PASSWORD` and `PULSE_KEY_PASSWORD` (alias `pulse`).
The workflow decodes it into a temporary file and passes it to Gradle through `PULSE_KEYSTORE`, `PULSE_STORE_PASSWORD` and `PULSE_KEY_PASSWORD`.
Without them (forks, local builds) the release is signed with the debug key.
