package rhynia.nyx.common.mte.prod

import kotlin.test.Test
import kotlin.test.assertEquals

class CopierAmountTest {
    @Test
    fun `large whole numbers remain exact`() {
        assertEquals(9_007_199_254_740_993L, parseCopyAmount("9007199254740993"))
        assertEquals(Long.MAX_VALUE - 1, parseCopyAmount("9223372036854775806"))
    }

    @Test
    fun `amount expressions preserve whole number precision`() {
        assertEquals(129L, parseCopyAmount("2s + 1"))
        assertEquals(9_007_199_254_740_993L, parseCopyAmount("9007199254740992 + 1"))
    }

    @Test
    fun `copy amount stays within its nonnegative long range`() {
        assertEquals(0L, parseCopyAmount("-5"))
        assertEquals(0L, parseCopyAmount("not a number"))
        assertEquals(Long.MAX_VALUE, parseCopyAmount("9223372036854775808"))
    }
}
