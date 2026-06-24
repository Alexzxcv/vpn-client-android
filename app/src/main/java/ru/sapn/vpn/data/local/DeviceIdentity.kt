package ru.sapn.vpn.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Криптографическая идентичность устройства (контракт совпадает с бэкендом).
 *
 * - При первом запуске генерируется Ed25519 keypair.
 * - PRIVATE seed (32 байта) НИКОГДА не хранится в открытом виде: он шифруется
 *   AES/GCM-ключом, который лежит в Android Keystore (не извлекается из железа).
 *   Зашифрованный seed пишется в приватный файл приложения.
 * - PUBLIC key (32 байта) отдаётся серверу в base64 (std) при регистрации.
 * - [sign] подписывает строку `"<device_id>.<timestamp>"` для заголовков /vpn/config.
 *
 * Почему не «чистый» Keystore-ключ: Android Keystore поддерживает Ed25519 только
 * c API 33, а у нас minSdk 26. Поэтому используем гибрид: сам Ed25519 — софтовый
 * (net.i2p.crypto:eddsa), а его секрет защищаем аппаратным AES-ключом Keystore.
 *
 * Безопасность логирования: ни seed, ни public key, ни подпись не логируются.
 */
class DeviceIdentity(context: Context) {

    private val appContext = context.applicationContext
    private val curve = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
    private val seedFile: File
        get() = File(appContext.filesDir, SEED_FILE)

    /** Кешируем приватный ключ в памяти процесса, чтобы не дешифровать на каждую подпись. */
    @Volatile
    private var cachedPrivate: EdDSAPrivateKey? = null

    /** Гарантирует наличие keypair (генерирует при первом вызове). Идемпотентно. */
    @Synchronized
    fun ensureKeyPair() {
        if (seedFile.exists()) return
        val seed = ByteArray(32).also { SecureRandom().nextBytes(it) }
        writeEncryptedSeed(seed)
    }

    /** Public key в base64 (std, 32 байта) для POST /devices. */
    fun publicKeyBase64(): String {
        ensureKeyPair()
        // abyte — каноническое 32-байтное представление публичной точки.
        return Base64.encodeToString(loadPrivate().abyte, Base64.NO_WRAP)
    }

    /**
     * Подпись сообщения приватным ключом. Возвращает base64 (std) подписи (64 байта).
     * Используется для заголовка X-Device-Signature: base64(Ed25519_sign("<id>.<ts>")).
     */
    fun sign(message: String): String {
        ensureKeyPair()
        val engine = EdDSAEngine(MessageDigest.getInstance(curve.hashAlgorithm))
        engine.initSign(loadPrivate())
        engine.update(message.toByteArray(Charsets.UTF_8))
        val signature = engine.sign()
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    // ---- внутреннее: загрузка/восстановление приватного ключа ----

    private fun loadPrivate(): EdDSAPrivateKey {
        cachedPrivate?.let { return it }
        val seed = readDecryptedSeed()
        val priv = EdDSAPrivateKey(EdDSAPrivateKeySpec(seed, curve))
        cachedPrivate = priv
        return priv
    }

    // ---- внутреннее: шифрование seed через Keystore AES/GCM ----

    private fun writeEncryptedSeed(seed: ByteArray) {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(seed)
        // Формат файла: [iv_len(1)][iv][ciphertext].
        seedFile.outputStream().use { out ->
            out.write(iv.size)
            out.write(iv)
            out.write(ciphertext)
        }
    }

    private fun readDecryptedSeed(): ByteArray {
        val bytes = seedFile.readBytes()
        val ivLen = bytes[0].toInt() and 0xFF
        val iv = bytes.copyOfRange(1, 1 + ivLen)
        val ciphertext = bytes.copyOfRange(1 + ivLen, bytes.size)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, aesKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    /** Достаёт (или создаёт) аппаратный AES-ключ из Android Keystore. */
    private fun aesKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Не требуем аутентификацию пользователя: подпись нужна в фоне (авто-рефреш).
            .setUserAuthenticationRequired(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sapn_device_seed_aes"
        const val SEED_FILE = "device_seed.bin"
        const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
