package com.gurthuchey.admin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class PhoneLoginTest {
    @Test public void normalizesSupportedIndianPhoneFormats() {
        assertEquals("+919876543210", PhoneLogin.normalizeIndianPhone("98765 43210"));
        assertEquals("+919876543210", PhoneLogin.normalizeIndianPhone("+91-98765-43210"));
    }

    @Test public void rejectsIncompleteOrImpossibleIndianNumbers() {
        assertEquals("", PhoneLogin.normalizeIndianPhone("987654321"));
        assertEquals("", PhoneLogin.normalizeIndianPhone("1234567890"));
        assertEquals("", PhoneLogin.normalizeIndianPhone(""));
        assertEquals("", PhoneLogin.normalizeIndianPhone(null));
    }
}
