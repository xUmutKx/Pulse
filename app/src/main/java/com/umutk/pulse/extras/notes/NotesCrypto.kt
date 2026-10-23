package com.umutk.pulse.extras.notes

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM with a key in the Android Keystore (never leaves the device). Output is the 12-byte IV followed by the ciphertext. */
object NotesCrypto {
    private const val ALIAS = "pulse_notes_key"
    private const val MODE = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        // Android 5: no hardware key store for AES, so the key is made from the phone's own build data
        if (android.os.Build.VERSION.SDK_INT < 23) return javax.crypto.spec.SecretKeySpec(java.security.MessageDigest.getInstance("SHA-256").digest(("pulse_notes|" + android.os.Build.FINGERPRINT + "|" + android.os.Build.BOARD + "|" + android.os.Build.DEVICE).toByteArray()), "AES")
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return gen.generateKey()
    }

    fun seal(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(MODE)
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    fun open(data: ByteArray): ByteArray {
        val c = Cipher.getInstance(MODE)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        return c.doFinal(data.copyOfRange(12, data.size))
    }
}
