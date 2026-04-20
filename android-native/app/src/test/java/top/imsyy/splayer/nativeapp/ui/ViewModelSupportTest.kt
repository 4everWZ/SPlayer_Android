package top.imsyy.splayer.nativeapp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewModelSupportTest {
    @Test
    fun `sanitizeLoadErrorMessage hides raw duplicated json parse details`() {
        val message = sanitizeLoadErrorMessage(
            rawMessage = "Unexpected JSON token at offset 26: Expected EOF after parsing, but had { instead at path: \$ JSON input: {\"msg\":\"参数错误\",\"code\":400}{\"msg\":\"参数错误\",\"code\":400}",
            fallback = "加载歌单失败",
        )

        assertEquals("加载歌单失败，请稍后重试", message)
    }

    @Test
    fun `sanitizeLoadErrorMessage keeps ordinary human readable messages`() {
        val message = sanitizeLoadErrorMessage(
            rawMessage = "歌单参数无效",
            fallback = "加载歌单失败",
        )

        assertEquals("歌单参数无效", message)
    }
}
