package top.imsyy.splayer.nativeapp.data.repository

import javax.inject.Provider
import javax.inject.Singleton
import javax.inject.Inject
import top.imsyy.splayer.nativeapp.data.local.AppSettingsStore
import top.imsyy.splayer.nativeapp.model.UserAccountUi

@Singleton
class NeteaseLoginRepository(
    private val remoteRepository: SPlayerRemoteRepository,
    private val apiRootProvider: () -> String,
    private val nativeLoginClientProvider: Provider<NeteaseLoginClient>,
) : NeteaseLoginClient {
    @Inject constructor(
        remoteRepository: SPlayerRemoteRepository,
        appSettingsStore: AppSettingsStore,
        nativeLoginClientProvider: Provider<NeteaseLoginClient>,
    ) : this(
        remoteRepository = remoteRepository,
        apiRootProvider = { appSettingsStore.apiRoot },
        nativeLoginClientProvider = nativeLoginClientProvider,
    )

    private fun resolveBackend(): LoginBackend {
        val apiRoot = apiRootProvider().trim()
        return if (apiRoot.isBlank()) {
            LoginBackend.LocalNative
        } else {
            LoginBackend.RemoteApiRoot(apiRoot)
        }
    }

    override suspend fun fetchLoginState(): UserAccountUi? {
        return when (resolveBackend()) {
            LoginBackend.LocalNative -> nativeLoginClientProvider.get().fetchLoginState()
            is LoginBackend.RemoteApiRoot -> remoteRepository.fetchLoginState()
        }
    }

    override suspend fun fetchQrKey(): String {
        return when (resolveBackend()) {
            LoginBackend.LocalNative -> nativeLoginClientProvider.get().fetchQrKey()
            is LoginBackend.RemoteApiRoot -> remoteRepository.fetchQrKey()
        }
    }

    override suspend fun fetchQrImage(key: String): String {
        return when (resolveBackend()) {
            LoginBackend.LocalNative -> nativeLoginClientProvider.get().fetchQrImage(key)
            is LoginBackend.RemoteApiRoot -> remoteRepository.fetchQrImage(key)
        }
    }

    override suspend fun checkQrState(key: String): QrCheckState {
        return when (resolveBackend()) {
            LoginBackend.LocalNative -> nativeLoginClientProvider.get().checkQrState(key)
            is LoginBackend.RemoteApiRoot -> remoteRepository.checkQrState(key)
        }
    }

    override suspend fun refreshLogin(): LoginMutationResult {
        return when (resolveBackend()) {
            LoginBackend.LocalNative -> nativeLoginClientProvider.get().refreshLogin()
            is LoginBackend.RemoteApiRoot -> remoteRepository.refreshLogin()
        }
    }

    override suspend fun logout(): LoginMutationResult {
        return when (resolveBackend()) {
            LoginBackend.LocalNative -> nativeLoginClientProvider.get().logout()
            is LoginBackend.RemoteApiRoot -> remoteRepository.logout()
        }
    }

    override suspend fun sendCaptcha(phone: String, countryCode: String): LoginMutationResult {
        return when (resolveBackend()) {
            LoginBackend.LocalNative -> nativeLoginClientProvider.get().sendCaptcha(phone, countryCode)
            is LoginBackend.RemoteApiRoot -> remoteRepository.sendCaptcha(phone, countryCode)
        }
    }

    override suspend fun verifyCaptcha(phone: String, captcha: String, countryCode: String): LoginMutationResult {
        return when (resolveBackend()) {
            LoginBackend.LocalNative -> nativeLoginClientProvider.get().verifyCaptcha(phone, captcha, countryCode)
            is LoginBackend.RemoteApiRoot -> remoteRepository.verifyCaptcha(phone, captcha, countryCode)
        }
    }

    override suspend fun loginCellphone(
        phone: String,
        captcha: String?,
        password: String?,
        countryCode: String,
    ): LoginMutationResult {
        return when (resolveBackend()) {
            LoginBackend.LocalNative -> nativeLoginClientProvider.get().loginCellphone(phone, captcha, password, countryCode)
            is LoginBackend.RemoteApiRoot -> remoteRepository.loginCellphone(phone, captcha, password, countryCode)
        }
    }

    override suspend fun fetchCountryCodeList(): String {
        return when (resolveBackend()) {
            LoginBackend.LocalNative -> nativeLoginClientProvider.get().fetchCountryCodeList()
            is LoginBackend.RemoteApiRoot -> remoteRepository.fetchCountryCodeList()
        }
    }
}
