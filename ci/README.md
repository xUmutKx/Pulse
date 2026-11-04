# Signing key

Release builds are signed with a private key that is not in this repository. It is kept in the GitHub Actions secrets:
`PULSE_KEYSTORE_B64` (the keystore, base64), `PULSE_STORE_PASSWORD` and `PULSE_KEY_PASSWORD` (alias `pulse`).
The workflow writes the keystore to a temporary file and passes its path to Gradle as `PULSE_KEYSTORE`.
If a secret is missing, the build stops with an error; nothing is signed with a public key.
