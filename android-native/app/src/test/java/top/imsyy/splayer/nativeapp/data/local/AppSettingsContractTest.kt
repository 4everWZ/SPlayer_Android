package top.imsyy.splayer.nativeapp.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.imsyy.splayer.nativeapp.AppSettingsProto
import top.imsyy.splayer.nativeapp.model.ThemeMode
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
}
