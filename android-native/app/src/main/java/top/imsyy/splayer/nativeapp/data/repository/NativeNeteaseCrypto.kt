package top.imsyy.splayer.nativeapp.data.repository

import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object NativeNeteaseCrypto {
    const val WEAPI_RSA_EXPONENT = "010001"
    private const val WEAPI_PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val WEAPI_IV = "0102030405060708"
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val RSA_MODULUS =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725" +
            "152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecb" +
            "da92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cf" +
            "a6b63acec026260d8fcf2c3f0c5c7be8a9cf9a0e4c1c8c5a6f3f6d8f1"

    fun eapi(uri: String, payloadJson: String): Map<String, String> {
        val digest = md5("nobody${uri}use${payloadJson}md5forencrypt")
        val data = "$uri-36cd479b6b5-$payloadJson-36cd479b6b5-$digest"
        return mapOf("params" to aesEcb(data, EAPI_KEY).uppercase())
    }

    fun weapi(payloadJson: String, secretKey: String): Map<String, String> {
        val first = aesCbc(payloadJson, WEAPI_PRESET_KEY)
        val second = aesCbc(first, secretKey)
        return mapOf(
            "params" to second,
            "encSecKey" to rsaEncrypt(secretKey.reversed()),
        )
    }

    fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun aesCbc(value: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(WEAPI_IV.toByteArray(Charsets.UTF_8)),
        )
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }

    private fun aesEcb(value: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun rsaEncrypt(value: String): String {
        val encrypted = BigInteger(value.toByteArray(Charsets.UTF_8)).modPow(
            BigInteger(WEAPI_RSA_EXPONENT, 16),
            BigInteger(RSA_MODULUS, 16),
        )
        return encrypted.toString(16).padStart(256, '0')
    }
}
