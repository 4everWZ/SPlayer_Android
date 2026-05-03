package top.imsyy.splayer.nativeapp.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.imsyy.splayer.nativeapp.AppSettingsProto
import top.imsyy.splayer.nativeapp.model.ThemeMode
import top.imsyy.splayer.nativeapp.model.UnlockServerMode
import top.imsyy.splayer.nativeapp.ui.toStoredUserOrNull

class AppSettingsContractTest {
    @Test
    fun `mergeCookieValue keeps current when response value missing or blank`() {
        assertEquals("persisted", mergeCookieValue("persisted", null))
        assertEquals("persisted", mergeCookieValue("persisted", ""))
    }

    @Test
    fun `mergeCookieValue accepts non blank response value`() {
        assertEquals("fresh", mergeCookieValue("persisted", "fresh"))
    }

    @Test
    fun `parseCookieHeader extracts music cookies only`() {
        val parsed = parseCookieHeader("MUSIC_U=test_music_u; __csrf=test_csrf; NMTID=test_nmtid; Path=/; HttpOnly")

        assertEquals("test_music_u", parsed["MUSIC_U"])
        assertEquals("test_csrf", parsed["__csrf"])
        assertEquals("test_nmtid", parsed["NMTID"])
    }

    @Test
    fun `toStoredUserOrNull builds user from persisted account`() {
        val settings = AppSettingsProto.newBuilder()
            .setAccountId("12345")
            .setAccountNickname("测试账号")
            .setAccountAvatarUrl("https://example.com/avatar.jpg")
            .build()

        val user = settings.toStoredUserOrNull()

        requireNotNull(user)
        assertEquals(12345L, user.userId)
        assertEquals("测试账号", user.nickname)
        assertEquals("https://example.com/avatar.jpg", user.avatarUrl)
    }

    @Test
    fun `toStoredUserOrNull returns null when account id invalid`() {
        val settings = AppSettingsProto.newBuilder()
            .setAccountId("")
            .setAccountNickname("测试账号")
            .build()

        val user = settings.toStoredUserOrNull()

        assertNull(user)
    }

    @Test
    fun `themeMode falls back to dark when value unknown`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromRaw(-1))
        assertEquals(ThemeMode.DARK, ThemeMode.fromRaw(0))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromRaw(1))
    }

    @Test
    fun `default settings keep unlock server mode local`() {
        assertEquals(
            UnlockServerMode.LOCAL,
            UnlockServerMode.fromRaw(AppSettingsSerializer.defaultValue.unlockServerMode),
        )
    }

    @Test
    fun `default settings do not include private api root`() {
        assertEquals("", AppSettingsSerializer.defaultValue.apiRoot)
    }

    @Test
    fun `default settings do not allow concurrent playback with other apps`() {
        assertEquals(false, AppSettingsSerializer.defaultValue.allowConcurrentPlayback)
    }

    @Test
    fun `configuredApiRoot ignores legacy value without user configured marker`() {
        val settings = AppSettingsProto.newBuilder()
            .setApiRoot("https://legacy.example.com/splayer")
            .setApiRootUserConfigured(false)
            .build()

        assertEquals("", configuredApiRoot(settings))
    }

    @Test
    fun `configuredApiRoot returns explicitly configured api root`() {
        val settings = AppSettingsProto.newBuilder()
            .setApiRoot("https://example.com/splayer")
            .setApiRootUserConfigured(true)
            .build()

        assertEquals("https://example.com/splayer", configuredApiRoot(settings))
    }

    @Test
    fun `normalizeApiRoot trims trailing slash and keeps explicit port`() {
        assertEquals(
            "http://192.168.1.10:25884/splayer",
            normalizeApiRoot("  http://192.168.1.10:25884/splayer/  "),
        )
    }

    @Test
    fun `normalizeApiRoot allows blank value for local default`() {
        assertEquals("", normalizeApiRoot("   "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `normalizeApiRoot rejects url without http scheme`() {
        normalizeApiRoot("192.168.1.10:25884/splayer")
    }

    @Test
    fun `unlockServerMode falls back to local when value unknown`() {
        assertEquals(UnlockServerMode.LOCAL, UnlockServerMode.fromRaw(-1))
        assertEquals(UnlockServerMode.LOCAL, UnlockServerMode.fromRaw(0))
        assertEquals(UnlockServerMode.EXTERNAL, UnlockServerMode.fromRaw(1))
    }

    @Test
    fun `configuredUnlockServerMode keeps legacy external value local without user configuration`() {
        val settings = AppSettingsProto.newBuilder()
            .setApiRoot("https://legacy.example.com/splayer")
            .setApiRootUserConfigured(false)
            .setUnlockServerMode(UnlockServerMode.EXTERNAL.rawValue)
            .build()

        assertEquals(UnlockServerMode.LOCAL, configuredUnlockServerMode(settings))
    }

    @Test
    fun `configuredUnlockServerMode ignores legacy external value even when api root exists`() {
        val settings = AppSettingsProto.newBuilder()
            .setApiRoot("https://legacy.example.com/splayer")
            .setApiRootUserConfigured(true)
            .setUnlockServerMode(UnlockServerMode.EXTERNAL.rawValue)
            .setUnlockServerModeUserConfigured(false)
            .build()

        assertEquals(UnlockServerMode.LOCAL, configuredUnlockServerMode(settings))
    }

    @Test
    fun `configuredUnlockServerMode keeps external when mode was explicitly configured`() {
        val settings = AppSettingsProto.newBuilder()
            .setUnlockServerMode(UnlockServerMode.EXTERNAL.rawValue)
            .setUnlockServerModeUserConfigured(true)
            .build()

        assertEquals(UnlockServerMode.EXTERNAL, configuredUnlockServerMode(settings))
    }

    @Test
    fun `withConfiguredApiRoot clears legacy external mode when api root is blank or legacy`() {
        val legacyExternal = AppSettingsProto.newBuilder()
            .setApiRoot("https://legacy.example.com/splayer")
            .setApiRootUserConfigured(false)
            .setUnlockServerMode(UnlockServerMode.EXTERNAL.rawValue)
            .build()

        val cleared = legacyExternal.withConfiguredApiRoot("")

        assertEquals("", configuredApiRoot(cleared))
        assertEquals(UnlockServerMode.LOCAL, configuredUnlockServerMode(cleared))
        assertEquals(UnlockServerMode.LOCAL.rawValue, cleared.unlockServerMode)
        assertEquals(false, cleared.unlockServerModeUserConfigured)
    }

    @Test
    fun `withConfiguredApiRoot does not force external mode when api root is saved`() {
        val local = AppSettingsProto.newBuilder()
            .setUnlockServerMode(UnlockServerMode.LOCAL.rawValue)
            .setUnlockServerModeUserConfigured(true)
            .build()

        val saved = local.withConfiguredApiRoot("https://example.com/splayer")

        assertEquals("https://example.com/splayer", configuredApiRoot(saved))
        assertEquals(UnlockServerMode.LOCAL, configuredUnlockServerMode(saved))
        assertEquals(true, saved.unlockServerModeUserConfigured)
    }
}
