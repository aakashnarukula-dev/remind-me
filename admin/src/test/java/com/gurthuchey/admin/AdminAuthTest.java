package com.gurthuchey.admin;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AdminAuthTest {
    @Test public void exactAdminPhoneIsAccepted() {
        assertTrue(AdminAuth.isAllowedPhone("+919177216132"));
        assertTrue(AdminAuth.isAllowedPhone("  +919177216132  "));
    }

    @Test public void everyOtherPhoneIsRejected() {
        assertFalse(AdminAuth.isAllowedPhone(null));
        assertFalse(AdminAuth.isAllowedPhone(""));
        assertFalse(AdminAuth.isAllowedPhone("+919177216133"));
        assertFalse(AdminAuth.isAllowedPhone("9177216132"));
    }
}
