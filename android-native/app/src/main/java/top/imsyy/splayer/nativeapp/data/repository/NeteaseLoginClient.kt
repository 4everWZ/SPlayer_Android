package top.imsyy.splayer.nativeapp.data.repository

import top.imsyy.splayer.nativeapp.model.UserAccountUi

interface NeteaseLoginClient {
    suspend fun fetchLoginState(): UserAccountUi?
    suspend fun fetchQrKey(): String
    suspend fun fetchQrImage(key: String): String
    suspend fun checkQrState(key: String): QrCheckState
    suspend fun refreshLogin(): LoginMutationResult
    suspend fun logout(): LoginMutationResult
    suspend fun sendCaptcha(phone: String, countryCode: String = "86"): LoginMutationResult
    suspend fun verifyCaptcha(phone: String, captcha: String, countryCode: String = "86"): LoginMutationResult
    suspend fun loginCellphone(
        phone: String,
        captcha: String? = null,
        password: String? = null,
        countryCode: String = "86",
    ): LoginMutationResult
    suspend fun fetchCountryCodeList(): String
}

data class NativeNeteaseCookies(
    val musicU: String = "",
    val csrf: String = "",
    val nmtid: String = "",
)

data class NativeNeteaseHttpResponse(
    val body: String,
    val cookies: List<String> = emptyList(),
)

data class LoginMutationResult(
    val code: Int,
    val body: String,
    val cookieHeader: String = "",
)

sealed interface LoginBackend {
    data object LocalNative : LoginBackend
    data class RemoteApiRoot(val apiRoot: String) : LoginBackend
}

interface NativeNeteaseHttpTransport {
    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String>,
    ): NativeNeteaseHttpResponse
}
