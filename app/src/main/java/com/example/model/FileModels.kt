package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "cloud_accounts")
data class CloudAccount(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val service: String, // Google Drive, Dropbox, OneDrive
    val email: String,
    val accessToken: String,
    val isConnected: Boolean,
    val storageUsedBytes: Long,
    val storageTotalBytes: Long,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "vault_settings")
data class VaultSetting(
    @PrimaryKey val id: Int = 1,
    val vaultPasswordHash: String? = null,
    val vaultSalt: String? = null,
    val isPremiumUser: Boolean = false,
    val selectedTheme: String = "Sophisticated Dark", // Sophisticated Dark, Cobalt Blue, Luxury Dark, Crimson Velvet, Olive Grove
    val aesKeySizeBits: Int = 256,
    val biometricEnabled: Boolean = false,
    val premiumUnlockedDate: Long = 0L
) : Serializable

@Entity(tableName = "secure_files")
data class SecureFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val path: String, // Directory hierarchy path e.g. "/Documents"
    val isFolder: Boolean,
    val isEncrypted: Boolean,
    val encryptedBase64Payload: String, // The AES-256 encrypted string payload
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
) : Serializable

data class LocalFile(
    val id: Int,
    val name: String,
    val path: String,
    val isFolder: Boolean,
    val sizeBytes: Long,
    val mimeType: String,
    val lastModified: Long,
    val isEncrypted: Boolean,
    val isFavorite: Boolean = false,
    val payloadText: String = "" // Decrypted or raw plain text context for simple editing
) : Serializable

data class CloudFile(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val sizeBytes: Long,
    val mimeType: String,
    val cloudService: String, // "Google Drive", "Dropbox", "OneDrive"
    val parentId: String = "",
    val isEncryptedOnCloud: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
) : Serializable
