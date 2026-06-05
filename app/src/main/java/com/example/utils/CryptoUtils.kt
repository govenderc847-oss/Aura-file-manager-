package com.example.utils

import android.util.Base64
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH_LIMIT = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12

    /**
     * Generates a random secure salt bytes.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * Derives a SecretKeySpec of specified bit length from a password and salt.
     */
    fun deriveKey(password: String, salt: ByteArray, keySizeBits: Int = 256): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val spec: KeySpec = PBEKeySpec(
            password.toCharArray(),
            salt,
            ITERATIONS,
            keySizeBits
        )
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, ALGORITHM)
    }

    /**
     * Encrypts plain text bytes using AES-256 GCM.
     * Prepends the salt and IV to the encrypted bytes for easy storage.
     * Returns a Base64-encoded string representation.
     */
    fun encrypt(plainText: String, secretKey: SecretKeySpec, salt: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val gcmSpec = GCMParameterSpec(128, iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val cipherTextBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        
        // Combine salt, IV, and cipher text bytes
        val combined = ByteArray(salt.size + iv.size + cipherTextBytes.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(cipherTextBytes, 0, combined, salt.size + iv.size, cipherTextBytes.size)
        
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts a Base64 encoded string back to its original plain text.
     * Separates the preprended salt, IV, and cipher text automatically.
     */
    fun decrypt(base64Combined: String, password: String, keySizeBits: Int = 256): String? {
        return try {
            val combined = Base64.decode(base64Combined, Base64.NO_WRAP)
            
            // Extract salt and IV
            val salt = ByteArray(SALT_LENGTH)
            val iv = ByteArray(IV_LENGTH)
            val cipherTextLength = combined.size - SALT_LENGTH - IV_LENGTH
            if (cipherTextLength <= 0) return null
            
            val cipherTextBytes = ByteArray(cipherTextLength)
            
            System.arraycopy(combined, 0, salt, 0, SALT_LENGTH)
            System.arraycopy(combined, SALT_LENGTH, iv, 0, IV_LENGTH)
            System.arraycopy(combined, SALT_LENGTH + IV_LENGTH, cipherTextBytes, 0, cipherTextLength)
            
            val secretKey = deriveKey(password, salt, keySizeBits)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(128, iv)
            
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val decryptedBytes = cipher.doFinal(cipherTextBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
