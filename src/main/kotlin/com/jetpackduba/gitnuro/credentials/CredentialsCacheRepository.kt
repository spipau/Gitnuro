package com.jetpackduba.gitnuro.credentials

import com.jetpackduba.gitnuro.extensions.lockUse
import kotlinx.coroutines.sync.Mutex
import org.eclipse.jgit.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

private const val KEY_LENGTH = 16

@Singleton
class CredentialsCacheRepository @Inject constructor() {
    private val credentialsCached = mutableListOf<CredentialsType>()
    private val credentialsLock = Mutex(false)

    // having a random key to encrypt the password may help in case of a memory dump attack
    private val encryptionKey = getRandomKey()

    fun getCachedHttpCredentials(url: String, isLfs: Boolean): CredentialsType.HttpCredentials? {
        val credentials = credentialsCached.filterIsInstance<CredentialsType.HttpCredentials>().firstOrNull {
            it.url == url && it.isLfs == isLfs
        }

        return credentials?.copy(password = credentials.password.cipherDecrypt())
    }

    suspend fun cacheHttpCredentials(credentials: CredentialsType.HttpCredentials) {
        cacheHttpCredentials(credentials.url, credentials.userName, credentials.password, credentials.isLfs)
    }

    suspend fun cacheHttpCredentials(url: String, userName: String, password: String, isLfs: Boolean) {
        val passwordEncrypted = password.cipherEncrypt()

        credentialsLock.lockUse {
            val previouslyCached = credentialsCached.any {
                it is CredentialsType.HttpCredentials && it.url == url
            }

            if (!previouslyCached) {
                val credentials = CredentialsType.HttpCredentials(url, userName, passwordEncrypted, isLfs)
                credentialsCached.add(credentials)
            }
        }
    }

    suspend fun cacheSshCredentials(sshKey: String, password: String) {
        credentialsLock.lockUse {
            val previouslyCached = credentialsCached.any {
                it is CredentialsType.SshCredentials && it.sshKey == sshKey
            }

            if (!previouslyCached) {
                val credentials = CredentialsType.SshCredentials(sshKey, password)
                credentialsCached.add(credentials)
            }
        }
    }

    private fun String.cipherEncrypt(): String {
        val secretKeySpec = SecretKeySpec(encryptionKey, "AES")
        val ivParameterSpec = IvParameterSpec(encryptionKey)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)

        val encryptedValue = cipher.doFinal(this.toByteArray())
        return Base64.encodeBytes(encryptedValue)
    }

    private fun String.cipherDecrypt(): String {
        val secretKeySpec = SecretKeySpec(encryptionKey, "AES")
        val ivParameterSpec = IvParameterSpec(encryptionKey)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)

        val decodedValue = Base64.decode(this)
        val decryptedValue = cipher.doFinal(decodedValue)
        return String(decryptedValue)
    }

    private fun getRandomKey(): ByteArray {
        val byteArray = ByteArray(KEY_LENGTH)
        Random.Default.nextBytes(byteArray)

        return byteArray
    }
}

sealed interface CredentialsType {
    data class SshCredentials(
        val sshKey: String,
        val password: String,
    ) : CredentialsType

    data class HttpCredentials(
        val url: String,
        val userName: String,
        val password: String,
        val isLfs: Boolean,
    ) : CredentialsType
}
