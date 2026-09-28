package com.gurthuchey.remindercall;

/** Maps clock time onto sorted reminder positions, including gaps between sections. */
final class TimelineProgress {
    private TimelineProgress() {}

    static float position(int[] minutes, int currentMinute) {
        if (minutes.length == 0 || currentMinute < minutes[0]) return -1f;
        for (int i = 1; i < minutes.length; i++) {
            if (currentMinute < minutes[i]) {
                return i - 1 + (currentMinute - minutes[i - 1])
                        / (float) (minutes[i] - minutes[i - 1]);
            }
        }
        return minutes.length - 1;
    }
}
