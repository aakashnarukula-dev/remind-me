package com.gurthuchey.remindercall;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.AudioManager;

import org.junit.Test;

public final class CallConflictDetectorTest {
    @Test public void detectsCellularAndVoipCallModes() {
        assertTrue(CallConflictDetector.isCallMode(AudioManager.MODE_RINGTONE, 35));
        assertTrue(CallConflictDetector.isCallMode(AudioManager.MODE_IN_CALL, 35));
        assertTrue(CallConflictDetector.isCallMode(AudioManager.MODE_IN_COMMUNICATION, 35));
        assertFalse(CallConflictDetector.isCallMode(AudioManager.MODE_NORMAL, 35));
    }

    @Test public void gatesNewerTelecomModesByAndroidVersion() {
        assertTrue(CallConflictDetector.isCallMode(AudioManager.MODE_CALL_SCREENING, 35));
        assertTrue(CallConflictDetector.isCallMode(AudioManager.MODE_CALL_REDIRECT, 35));
        assertFalse(CallConflictDetector.isCallMode(AudioManager.MODE_CALL_SCREENING, 29));
    }
}
