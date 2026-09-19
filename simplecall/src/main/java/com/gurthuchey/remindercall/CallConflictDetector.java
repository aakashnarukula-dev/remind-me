package com.gurthuchey.remindercall;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;

/** Detects cellular and VoIP calls without requiring phone-state permission. */
final class CallConflictDetector {
    private static final String OWNS_AUDIO_ROUTE = "ownsAudioRoute";
    private static final String AUDIO_ROUTE_SINCE = "audioRouteSince";
    private CallConflictDetector() {}

    static boolean isAnotherCallActive(Context context) {
        AudioManager audio = context.getSystemService(AudioManager.class);
        recoverAbandonedRoute(context, audio);
        return audio != null && isCallMode(audio.getMode(), Build.VERSION.SDK_INT);
    }

    static void markAppRouteActive(Context context) {
        CallService.session(context).edit()
                .putBoolean(OWNS_AUDIO_ROUTE, true)
                .putLong(AUDIO_ROUTE_SINCE, System.currentTimeMillis())
                .commit();
    }

    static void markAppRouteReleased(Context context, boolean released) {
        if (!released) return;
        CallService.session(context).edit()
                .putBoolean(OWNS_AUDIO_ROUTE, false)
                .remove(AUDIO_ROUTE_SINCE)
                .commit();
    }

    static void recoverAbandonedRoute(Context context, AudioManager audio) {
        if (audio == null) return;
        android.content.SharedPreferences session = CallService.session(context);
        if (!session.getBoolean(OWNS_AUDIO_ROUTE, false)) return;
        boolean sessionActive = session.getBoolean("active", false);
        // Static process state cannot survive a killed service. A persisted active=true with no
        // live CallService is therefore stale and safe to repair immediately.
        if (CallService.isProcessCallActive()) return;
        try {
            if (Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice();
        } catch (SecurityException | IllegalArgumentException ignored) {}
        try {
            if (Build.VERSION.SDK_INT < 31) audio.setSpeakerphoneOn(false);
        } catch (SecurityException | IllegalArgumentException ignored) {}
        try {
            audio.setMode(AudioManager.MODE_NORMAL);
        } catch (SecurityException | IllegalArgumentException ignored) {}
        boolean released = audio.getMode() != AudioManager.MODE_IN_COMMUNICATION;
        markAppRouteReleased(context, released);
        if (released && sessionActive) {
            session.edit().putBoolean("active", false).putBoolean("answered", false).commit();
        }
    }

    @SuppressLint("InlinedApi")
    static boolean isCallMode(int mode, int sdk) {
        if (mode == AudioManager.MODE_RINGTONE
                || mode == AudioManager.MODE_IN_CALL
                || mode == AudioManager.MODE_IN_COMMUNICATION) return true;
        return sdk >= 30 && (mode == AudioManager.MODE_CALL_SCREENING
                || mode == AudioManager.MODE_CALL_REDIRECT);
    }
}
