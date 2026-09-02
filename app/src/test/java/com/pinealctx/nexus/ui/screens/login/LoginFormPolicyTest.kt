package com.pinealctx.nexus.ui.screens.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginFormPolicyTest {
    @Test
    fun phoneIdentityRequiresSixToTwentyDigits() {
        assertTrue(isValidPhoneIdentity("13800138000"))
        assertFalse(isValidPhoneIdentity("12345"))
        assertFalse(isValidPhoneIdentity("138-0013-8000"))
    }

    @Test
    fun emailIdentityRequiresOneAtAndDomainSuffix() {
        assertTrue(isValidEmailIdentity("silkage@example.com"))
        assertFalse(isValidEmailIdentity("silkage@example"))
        assertFalse(isValidEmailIdentity("silkage @example.com"))
    }

    @Test
    fun destinationMaskKeepsEnoughContextForVerification() {
        assertEquals("si***@example.com", maskLoginDestination("silkage@example.com", true))
        assertEquals("+86 •••• 8000", maskLoginDestination("+8613800138000", false))
    }
}
