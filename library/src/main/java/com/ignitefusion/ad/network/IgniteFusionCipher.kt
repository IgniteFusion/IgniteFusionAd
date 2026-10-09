package com.ignitefusion.ad.network

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.ignitefusion.ad.util.IgniteFusionLog
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

@PublishedApi internal const val CIPHER_TAG = "IgniteFusionCipher"
private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
private const val AES_KEY_BYTES = 32
private const val AES_NONCE_BYTES = 12
private const val GCM_TAG_BITS = 128

data class PublicKeyBundle(
    val kid: String,
    val publicPem: String,
    val key: RSAPublicKey
)

data class EncEnvelope(
    val kid: String,
    val encryptedKey: String,
    val iv: String,
    val payload: String
)

data class EncryptedResult(
    val envelope: EncEnvelope,
    val aesKey: ByteArray,
    val nonce: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedResult) return false
        return envelope == other.envelope &&
            aesKey.contentEquals(other.aesKey) &&
            nonce.contentEquals(other.nonce)
    }

    override fun hashCode(): Int {
        var result = envelope.hashCode()
        result = 31 * result + aesKey.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        return result
    }
}

@PublishedApi internal val gson: Gson by lazy {
    GsonBuilder().serializeNulls().create()
}

private fun stripPem(pem: String): String {
    return pem
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("\r", "")
        .replace("\n", "")
        .trim()
}

private fun base64DecodeStd(s: String): ByteArray {
    return Base64.decode(s, Base64.DEFAULT)
}

private fun base64EncodeStd(bytes: ByteArray): String {
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

fun loadRSAPublicFromPem(publicPem: String): RSAPublicKey {
    val der = base64DecodeStd(stripPem(publicPem))
    val kf = KeyFactory.getInstance("RSA")
    val spec = X509EncodedKeySpec(der)
    return kf.generatePublic(spec) as RSAPublicKey
}

private fun rsaEncrypt(pub: RSAPublicKey, aesKey: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
    cipher.init(
        Cipher.ENCRYPT_MODE,
        pub,
        OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
        )
    )
    // RSA 2048 / OAEP-SHA256 能加密 <= 190 字节；32 字节完全够用；无需分段
    return cipher.doFinal(aesKey)
}

private fun aesGcmEncrypt(aesKey: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
    if (aesKey.size != AES_KEY_BYTES) error("AES key 长度不正确: ${aesKey.size}")
    if (nonce.size != AES_NONCE_BYTES) error("AES nonce 长度不正确: ${nonce.size}")
    val cipher = Cipher.getInstance(AES_TRANSFORMATION)
    cipher.init(
        Cipher.ENCRYPT_MODE,
        SecretKeySpec(aesKey, "AES"),
        GCMParameterSpec(GCM_TAG_BITS, nonce)
    )
    return cipher.doFinal(plaintext)
}

private fun aesGcmDecrypt(aesKey: ByteArray, nonce: ByteArray, ciphertextWithTag: ByteArray): ByteArray {
    if (aesKey.size != AES_KEY_BYTES) error("AES key 长度不正确: ${aesKey.size}")
    if (nonce.size != AES_NONCE_BYTES) error("AES nonce 长度不正确: ${nonce.size}")
    val cipher = Cipher.getInstance(AES_TRANSFORMATION)
    cipher.init(
        Cipher.DECRYPT_MODE,
        SecretKeySpec(aesKey, "AES"),
        GCMParameterSpec(GCM_TAG_BITS, nonce)
    )
    return cipher.doFinal(ciphertextWithTag)
}

private fun xorNonceOne(nonce: ByteArray): ByteArray {
    val out = ByteArray(nonce.size)
    for (i in nonce.indices) out[i] = (nonce[i].toInt() xor 0x01).toByte()
    return out
}

fun encryptJsonToEnvelope(pk: PublicKeyBundle, plainObject: Any): EncryptedResult {
    val plainBytes = gson.toJson(plainObject).toByteArray(StandardCharsets.UTF_8)
    val aesKey = ByteArray(AES_KEY_BYTES).also { SecureRandom().nextBytes(it) }
    val nonce = ByteArray(AES_NONCE_BYTES).also { SecureRandom().nextBytes(it) }
    val ctWithTag = aesGcmEncrypt(aesKey, nonce, plainBytes)
    val encKey = rsaEncrypt(pk.key, aesKey)
    val env = EncEnvelope(
        kid = pk.kid,
        encryptedKey = base64EncodeStd(encKey),
        iv = base64EncodeStd(nonce),
        payload = base64EncodeStd(ctWithTag)
    )
    return EncryptedResult(env, aesKey, nonce)
}

fun decryptEnvelopeToBytes(aesKey: ByteArray, requestNonce: ByteArray, envelope: EncEnvelope): ByteArray {
    val requestNonce1 = xorNonceOne(requestNonce)
    val useNonce = runCatching { base64DecodeStd(envelope.iv) }.getOrElse { requestNonce1 }
    val ct = base64DecodeStd(envelope.payload)
    return aesGcmDecrypt(aesKey, useNonce, ct)
}

internal inline fun <reified T> decryptEnvelopeTo(
    aesKey: ByteArray,
    requestNonce: ByteArray,
    envelope: EncEnvelope
): T {
    val bytes = decryptEnvelopeToBytes(aesKey, requestNonce, envelope)
    val str = String(bytes, StandardCharsets.UTF_8)
    IgniteFusionLog.d(CIPHER_TAG, "decrypt plain (length=${str.length}): ${str.take(240)}${if (str.length > 240) "…" else ""}")
    return gson.fromJson(str, T::class.java)
}

fun publicKeyBundleFromJson(json: String): PublicKeyBundle {
    val map = gson.fromJson(json, Map::class.java) as Map<*, *>
    val kid = map["kid"] as? String ?: error("public-key payload 缺少 kid")
    val pem = map["publicPem"] as? String ?: error("public-key payload 缺少 publicPem")
    val rsa = loadRSAPublicFromPem(pem)
    return PublicKeyBundle(kid, pem, rsa)
}
