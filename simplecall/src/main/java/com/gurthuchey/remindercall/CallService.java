package com.gurthuchey.remindercall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;
import java.util.Calendar;
import java.io.File;

public final class CallService extends Service {
    static final String CALLER_NAME = "చిట్టి";
    static final String ACTION_RING = "com.gurthuchey.remindercall.RING";
    static final String ACTION_ANSWER = "com.gurthuchey.remindercall.ANSWER";
    static final String ACTION_REJECT = "com.gurthuchey.remindercall.REJECT";
    static final String ACTION_OPTION = "com.gurthuchey.remindercall.OPTION";
    static final String ACTION_END = "com.gurthuchey.remindercall.END";
    static final String ACTION_SILENCE = "com.gurthuchey.remindercall.SILENCE";
    static final String ACTION_RESTORE_NOTIFICATION =
            "com.gurthuchey.remindercall.RESTORE_NOTIFICATION";
    static final String ACTION_CALL_SCREEN_VISIBLE =
            "com.gurthuchey.remindercall.CALL_SCREEN_VISIBLE";
    static final String ACTION_CALL_SCREEN_HIDDEN =
            "com.gurthuchey.remindercall.CALL_SCREEN_HIDDEN";
    static final String ACTION_TOGGLE_SPEAKER =
            "com.gurthuchey.remindercall.TOGGLE_SPEAKER";
    static final String ACTION_STATE_CHANGED = "com.gurthuchey.remindercall.STATE_CHANGED";
    static final String EXTRA_OPTION = "option";
    static final String EXTRA_OPTION_STEP = "optionStep";
    static final int[] DELAY_MINUTES = {5, 15, 30, 60};

    static final String CHANNEL_ID = "reminder_calls";
    private static final String SESSION = "call_session";
    private static final int NOTIFICATION_ID = 7101;
    private static final long RING_TIMEOUT_MS = 60_000L;
    private static final long ANSWER_TIMEOUT_MS = 60_000L;
    private static final long SPEECH_COMPLETION_TIMEOUT_MS = 60_000L;
    private static volatile boolean processCallActive;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable missedCall = () -> reject(true);
    private final Runnable unansweredQuestion = this::handleUnansweredQuestion;
    private NotificationManager notifications;
    private AudioManager audioManager;
    private AudioFocusRequest voiceFocus;
    private int previousAudioMode = AudioManager.MODE_NORMAL;
    private boolean previousSpeakerphone;
    private AudioDeviceInfo previousCommunicationDevice;
    private boolean voiceRouteConfigured;
    private boolean speakerOn;
    private MediaPlayer ringtonePlayer;
    private MediaPlayer voicePlayer;
    private Vibrator vibrator;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;
    private String pendingSpeechId;
    private String activeTtsToken;
    private String activeTtsSpeechId;
    private long speechGeneration;
    private boolean active;
    private boolean answered;
    private boolean finalizing;
    private boolean transitioning;
    private boolean callScreenVisible;
    private long connectedAtMillis;
    private int step;
    private int branch;
    private String scheduleId = "test-call";
    private String phase = ReminderScheduler.PHASE_MEDICINE;
    private String label = "Medicine";
    private String member = "Family member";
    private String language = "te";
    private int preMinutes = 30;
    private int doseHour = -1;
    private RemoteStore.Schedule activeSchedule;
    private BroadcastReceiver ringControlReceiver;

    private void handleUnansweredQuestion() {
        if (active && answered && !finalizing) reject(true);
    }

    @Override public void onCreate() {
        super.onCreate();
        notifications = getSystemService(NotificationManager.class);
        audioManager = getSystemService(AudioManager.class);
        createChannel();
        vibrator = getSystemService(Vibrator.class);
        ringControlReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)
                        || "android.media.VOLUME_CHANGED_ACTION".equals(action)) {
                    silenceRinging();
                }
            }
        };
        IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction("android.media.VOLUME_CHANGED_ACTION");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(ringControlReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(ringControlReceiver, screenFilter);
        }
        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) {
                int result = tts.setLanguage(AppLanguage.locale(language));
                if (result == TextToSpeech.LANG_MISSING_DATA
                        || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.getDefault());
                }
                tts.setAudioAttributes(voiceAudioAttributes());
                if (pendingSpeech != null) {
                    String text = pendingSpeech;
                    String id = pendingSpeechId;
                    pendingSpeech = null;
                    pendingSpeechId = null;
                    speak(text, id);
                }
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onError(String utteranceId) {
                finishTtsUtterance(utteranceId);
            }
            @Override public void onDone(String utteranceId) {
                finishTtsUtterance(utteranceId);
            }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_RING.equals(action)) startRinging(intent);
        else if (ACTION_ANSWER.equals(action)) answer();
        else if (ACTION_REJECT.equals(action)) reject(false);
        else if (ACTION_OPTION.equals(action)) choose(intent.getIntExtra(EXTRA_OPTION, -1),
                intent.getIntExtra(EXTRA_OPTION_STEP, -1));
        else if (ACTION_END.equals(action)) reject(false);
        else if (ACTION_SILENCE.equals(action)) silenceRinging();
        else if (ACTION_RESTORE_NOTIFICATION.equals(action)) restoreCallNotification();
        else if (ACTION_CALL_SCREEN_VISIBLE.equals(action)) setCallScreenVisible(true);
        else if (ACTION_CALL_SCREEN_HIDDEN.equals(action)) setCallScreenVisible(false);
        else if (ACTION_TOGGLE_SPEAKER.equals(action)) toggleSpeaker();
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void startRinging(Intent intent) {
        String requestedId = safe(intent.getStringExtra(ReminderScheduler.EXTRA_ID), "test-call");
        String requestedPhase = safe(intent.getStringExtra(ReminderScheduler.EXTRA_PHASE),
                ReminderScheduler.PHASE_MEDICINE);
        String requestedLabel = safe(intent.getStringExtra("label"), "Medicine");
        String requestedMember = safe(intent.getStringExtra("member"), "Family member");
        int requestedPreMinutes = Math.max(1, intent.getIntExtra("preMinutes", 30));
        if (active) {
            if (!"test-call".equals(requestedId)) {
                ReminderScheduler.scheduleRetry(this, requestedId, requestedPhase,
                        requestedLabel, requestedMember, requestedPreMinutes);
            }
            return;
        }
        scheduleId = requestedId;
        phase = requestedPhase;
        label = requestedLabel;
        member = requestedMember;
        preMinutes = requestedPreMinutes;
        doseHour = intent.getIntExtra("doseHour", -1);
        RemoteStore.Config config = new RemoteStore(this).load();
        activeSchedule = ReminderScheduler.active(config, scheduleId, phase);
        language = activeSchedule == null
                ? (config == null ? AppLanguage.current(this) : AppLanguage.normalize(config.language))
                : AppLanguage.normalize(activeSchedule.language);
        if (ttsReady && tts != null) tts.setLanguage(AppLanguage.locale(language));
        if (CallConflictDetector.isAnotherCallActive(this)) {
            if (!"test-call".equals(scheduleId)) {
                ReminderScheduler.scheduleRetry(this, scheduleId, phase,
                        label, member, preMinutes);
            }
            stopSelf();
            return;
        }
        active = true;
        processCallActive = true;
        answered = false;
        speakerOn = false;
        finalizing = false;
        transitioning = false;
        connectedAtMillis = 0L;
        step = 0;
        branch = 0;
        writeSession();
        startForeground(NOTIFICATION_ID, incomingNotification());
        startRingAudio();
        handler.removeCallbacks(missedCall);
        handler.postDelayed(missedCall, RING_TIMEOUT_MS);
        broadcastState();
        launchCallScreen();
    }

    private void answer() {
        if (!active || answered) return;
        answered = true;
        speakerOn = false;
        connectedAtMillis = System.currentTimeMillis();
        step = 0;
        handler.removeCallbacks(missedCall);
        stopRingAudio();
        configureVoiceRoute();
        ReminderScheduler.cancelRetry(this, scheduleId, phase);
        writeSession();
        showOngoingNotification("Connecting…");
        broadcastState();
        if (!callScreenVisible) launchCallScreen();
        announceQuestion(0);
    }

    private void announceQuestion(int nextStep) {
        if (!active || !answered) return;
        if (customCall() && (nextStep < 0 || nextStep >= activeSchedule.questions.size())) {
            finishCall();
            return;
        }
        step = nextStep;
        transitioning = false;
        writeSession();
        showOngoingNotification(question());
        broadcastState();
        if (ReminderScheduler.PHASE_MEAL.equals(phase) && step == 0) {
            finalizing = true;
            handler.removeCallbacks(unansweredQuestion);
            speak(question(), "finish");
            handler.postDelayed(this::finishCall, SPEECH_COMPLETION_TIMEOUT_MS);
            return;
        }
        handler.removeCallbacks(unansweredQuestion);
        // Safety watchdog for a broken TTS engine or corrupt cached clip. Normally this is
        // replaced by a fresh full-minute timeout as soon as the spoken question completes.
        handler.postDelayed(unansweredQuestion, ANSWER_TIMEOUT_MS + 30_000L);
        speak(question(), "question_" + step);
    }

    private void choose(int option, int expectedStep) {
        int maximumOption = customCall() ? customAnswerCount() - 1
                : step == 2 ? DELAY_MINUTES.length - 1 : confirmationCall() ? 2 : 1;
        if (!active || !answered || expectedStep != step
                || transitioning || option < 0 || option > maximumOption) return;
        handler.removeCallbacks(unansweredQuestion);
        if (confirmationCall()) {
            if (step == 0 && option == 0) {
                finishWithResponse(SpeechText.medicineTaken(member, language));
            } else if (step == 0 && option == 1) {
                if (!"test-call".equals(scheduleId)) {
                    ReminderScheduler.scheduleRetryAfter(this, scheduleId, phase,
                            label, member, preMinutes, 5);
                }
                finishWithResponse(SpeechText.confirmationNotTaken(language));
            } else if (step == 0) {
                announceQuestion(2);
            } else {
                int delay = DELAY_MINUTES[option];
                if (!"test-call".equals(scheduleId)) {
                    ReminderScheduler.scheduleRetryAfter(this, scheduleId, phase,
                            label, member, preMinutes, delay);
                }
                finishWithResponse(SpeechText.reminderDelayed(delay, language));
            }
            return;
        }
        if (customCall()) {
            RemoteStore.ScriptAnswer selected = activeSchedule.questions.get(step).answers.get(option);
            String response = SpeechText.custom(selected.response, member, label,
                    activeSchedule.category, language);
            int nextStep = step + 1;
            if (nextStep >= activeSchedule.questions.size()) {
                scheduleConfirmationAfterPrimary();
                if (response.isEmpty()) finishCall(); else finishWithResponse(response);
            } else if (response.isEmpty()) {
                announceQuestion(nextStep);
            } else {
                continueWithResponse(response, nextStep);
            }
            return;
        }
        if (step == 0) {
            if (option == 0) {
                if (!ReminderScheduler.PHASE_MEAL.equals(phase)) {
                    scheduleConfirmationAfterPrimary();
                }
                finishWithResponse(ReminderScheduler.PHASE_MEAL.equals(phase)
                        ? SpeechText.mealAcknowledged(language)
                        : SpeechText.medicineTaken(member, language));
            } else {
                branch = 1;
                announceQuestion(1);
            }
            return;
        }
        if (step == 1) {
            if (option == 0) {
                scheduleConfirmationAfterPrimary();
                finishWithResponse(SpeechText.medicineTaken(member, language));
            }
            else announceQuestion(2);
            return;
        }
        if (step == 2) {
            int delay = DELAY_MINUTES[option];
            if (!"test-call".equals(scheduleId)) {
                ReminderScheduler.scheduleRetryAfter(this, scheduleId, phase,
                        label, member, preMinutes, delay);
            }
            finishWithResponse(SpeechText.reminderDelayed(delay, language));
        }
    }

    private void reject(boolean missed) {
        if (!active) return;
        if (!"test-call".equals(scheduleId)) {
            ReminderScheduler.scheduleRetry(this, scheduleId, phase,
                    label, member, preMinutes);
        }
        finishCall();
    }

    private void toggleSpeaker() {
        if (!active || !answered) return;
        speakerOn = !speakerOn;
        configureVoiceRoute();
        getSharedPreferences(SESSION, MODE_PRIVATE).edit()
                .putBoolean("speakerOn", speakerOn).apply();
        broadcastState();
    }

    private void finishCall() {
        if (!active) return;
        active = false;
        processCallActive = false;
        answered = false;
        connectedAtMillis = 0L;
        activeSchedule = null;
        transitioning = false;
        handler.removeCallbacksAndMessages(null);
        stopRingAudio();
        cancelSpeechPlayback();
        releaseVoiceRoute();
        getSharedPreferences(SESSION, MODE_PRIVATE).edit()
                .putBoolean("active", false).putBoolean("answered", false)
                .putBoolean("speakerOn", false)
                .putLong("connectedAtMillis", 0L).apply();
        broadcastState();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void speak(String text, String id) {
        cancelSpeechPlayback();
        long generation = speechGeneration;
        File clip = VoiceClipCache.fileFor(this, text, language);
        if (clip != null && playClip(clip, text, id, generation)) return;
        if (!ttsReady) {
            pendingSpeech = text;
            pendingSpeechId = id;
            return;
        }
        playTts(text, id, generation);
    }

    private void playTts(String text, String id, long generation) {
        if (!ttsReady || tts == null || generation != speechGeneration) return;
        requestVoiceFocus();
        String token = "speech_" + generation;
        activeTtsToken = token;
        activeTtsSpeechId = id;
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, token) == TextToSpeech.ERROR) {
            activeTtsToken = null;
            activeTtsSpeechId = null;
            abandonVoiceFocus();
            onSpeechFinished(id);
        }
    }

    private boolean playClip(File clip, String text, String id, long generation) {
        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(voiceAudioAttributes());
            player.setVolume(1f, 1f);
            player.setDataSource(clip.getAbsolutePath());
            player.setOnCompletionListener(done -> {
                boolean current = voicePlayer == done && generation == speechGeneration;
                done.release();
                if (voicePlayer == done) voicePlayer = null;
                if (!current) return;
                abandonVoiceFocus();
                onSpeechFinished(id);
            });
            player.setOnErrorListener((failed, what, extra) -> {
                boolean current = voicePlayer == failed && generation == speechGeneration;
                if (voicePlayer == failed) voicePlayer = null;
                try { failed.release(); } catch (RuntimeException ignored) {}
                if (current) {
                    clip.delete();
                    abandonVoiceFocus();
                    playTts(text, id, generation);
                }
                return true;
            });
            player.prepare();
            voicePlayer = player;
            requestVoiceFocus();
            player.start();
            return true;
        } catch (Exception ignored) {
            stopVoicePlayer();
            return false;
        }
    }

    private void finishTtsUtterance(String utteranceId) {
        handler.post(() -> {
            if (utteranceId == null || !utteranceId.equals(activeTtsToken)) return;
            String speechId = activeTtsSpeechId;
            activeTtsToken = null;
            activeTtsSpeechId = null;
            abandonVoiceFocus();
            onSpeechFinished(speechId);
        });
    }

    private void cancelSpeechPlayback() {
        speechGeneration++;
        pendingSpeech = null;
        pendingSpeechId = null;
        activeTtsToken = null;
        activeTtsSpeechId = null;
        stopVoicePlayer();
        if (tts != null) tts.stop();
    }

    private void stopVoicePlayer() {
        if (voicePlayer == null) return;
        try { voicePlayer.stop(); } catch (RuntimeException ignored) {}
        voicePlayer.release();
        voicePlayer = null;
        abandonVoiceFocus();
    }

    private void finishWithResponse(String response) {
        finalizing = true;
        getSharedPreferences(SESSION, MODE_PRIVATE).edit()
                .putString("question", response).putString("answerA", "")
                .putString("answerB", "").putString("answerC", "")
                .putString("answerD", "").putBoolean("showDelayOptions", false)
                .putBoolean("finalizing", true).putBoolean("transitioning", false).apply();
        showOngoingNotification(response);
        broadcastState();
        speak(response, "finish");
        handler.postDelayed(this::finishCall, SPEECH_COMPLETION_TIMEOUT_MS);
    }

    private void continueWithResponse(String response, int nextStep) {
        transitioning = true;
        getSharedPreferences(SESSION, MODE_PRIVATE).edit()
                .putString("question", response).putString("answerA", "")
                .putString("answerB", "").putString("answerC", "")
                .putString("answerD", "").putBoolean("showDelayOptions", false)
                .putBoolean("transitioning", true).apply();
        showOngoingNotification(response);
        broadcastState();
        speak(response, "continue_" + nextStep);
        handler.postDelayed(unansweredQuestion, SPEECH_COMPLETION_TIMEOUT_MS);
    }

    private void onSpeechFinished(String id) {
        if ("finish".equals(id)) {
            finishCall();
        } else if (id != null && id.startsWith("continue_")
                && active && answered && !finalizing) {
            handler.removeCallbacks(unansweredQuestion);
            try {
                announceQuestion(Integer.parseInt(id.substring("continue_".length())));
            } catch (NumberFormatException ignored) {
                reject(true);
            }
        } else if (id != null && id.startsWith("question_")
                && active && answered && !finalizing) {
            handler.removeCallbacks(unansweredQuestion);
            if (customCall() && customAnswerCount() == 0) {
                int nextStep = step + 1;
                if (nextStep >= activeSchedule.questions.size()) {
                    scheduleConfirmationAfterPrimary();
                    finishCall();
                } else {
                    announceQuestion(nextStep);
                }
            } else {
                // Give the person a full minute after Chitti finishes asking the question.
                // A partial conversation therefore cannot time out while the prompt is playing.
                handler.postDelayed(unansweredQuestion, ANSWER_TIMEOUT_MS);
            }
        }
    }

    private void startRingAudio() {
        try {
            MediaPlayer player = MediaPlayer.create(this, R.raw.incoming_ring,
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(), AudioManager.AUDIO_SESSION_ID_GENERATE);
            if (player != null) {
                ringtonePlayer = player;
                player.setLooping(true);
                player.setVolume(1f, 1f);
                player.start();
            }
        } catch (RuntimeException ignored) {}
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 900, 700};
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        }
    }

    private void stopRingAudio() {
        if (ringtonePlayer != null) {
            try { ringtonePlayer.stop(); } catch (RuntimeException ignored) {}
            ringtonePlayer.release();
            ringtonePlayer = null;
        }
        if (vibrator != null) vibrator.cancel();
    }

    private void silenceRinging() {
        if (active && !answered) stopRingAudio();
    }

    private Notification incomingNotification() {
        String incoming = AppLanguage.ui(language, "Reminder call");
        Notification.Builder builder = baseBuilder(callerName(), incoming)
                .setContentIntent(activityPending())
                .setFullScreenIntent(activityPending(), true)
                .setDeleteIntent(actionPending(ACTION_RESTORE_NOTIFICATION, 32, -1))
                .setOngoing(true);
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setStyle(Notification.CallStyle.forIncomingCall(callerPerson(),
                    actionPending(ACTION_REJECT, 31, -1), answerAndOpenPending()));
        } else {
            builder.setStyle(new Notification.BigTextStyle()
                            .bigText(incoming))
                    .addAction(new Notification.Action.Builder(null,
                            AppLanguage.ui(language, "Reject"),
                            actionPending(ACTION_REJECT, 31, -1)).build())
                    .addAction(new Notification.Action.Builder(null,
                            AppLanguage.ui(language, "Answer"),
                            answerAndOpenPending()).build());
        }
        return nonClearable(builder.build());
    }

    private Notification ongoingNotification(String question) {
        long startedAt = connectedAtMillis > 0L
                ? connectedAtMillis
                : getSharedPreferences(SESSION, MODE_PRIVATE)
                        .getLong("connectedAtMillis", System.currentTimeMillis());
        Notification.Builder builder = baseBuilder(callerName(), question)
                .setContentIntent(activityPending())
                .setWhen(startedAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(false)
                .setColor(Ui.ACCEPT)
                .setOngoing(true);
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setStyle(Notification.CallStyle.forOngoingCall(callerPerson(),
                    actionPending(ACTION_END, 33, -1)));
        } else {
            builder.setStyle(new Notification.BigTextStyle().bigText(question))
                    .addAction(new Notification.Action.Builder(null,
                            AppLanguage.ui(language, "Hang up"),
                            actionPending(ACTION_END, 33, -1)).build());
        }
        return nonClearable(builder.build());
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.P)
    private android.app.Person callerPerson() {
        return new android.app.Person.Builder()
                .setName(callerName())
                .setImportant(true)
                .build();
    }

    private Notification nonClearable(Notification notification) {
        notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
        return notification;
    }

    private void showOngoingNotification(String question) {
        if (!callScreenVisible) {
            startForeground(NOTIFICATION_ID, ongoingNotification(question));
        }
    }

    private void restoreCallNotification() {
        if (!active || callScreenVisible) return;
        String text = getSharedPreferences(SESSION, MODE_PRIVATE)
                .getString("question", answered ? question()
                        : AppLanguage.ui(language, "Reminder call"));
        startForeground(NOTIFICATION_ID,
                answered ? ongoingNotification(text) : incomingNotification());
    }

    private Notification.Builder baseBuilder(String title, String text) {
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_CALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setColor(Ui.NAVY)
                .setOnlyAlertOnce(true);
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder;
    }

    private PendingIntent activityPending() {
        Intent intent = new Intent(this, CallActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return activityPendingIntent(20, intent);
    }

    private PendingIntent answerAndOpenPending() {
        Intent intent = new Intent(this, CallActivity.class)
                .setAction(ACTION_ANSWER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return activityPendingIntent(21, intent);
    }

    private PendingIntent activityPendingIntent(int requestCode, Intent intent) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            return PendingIntent.getActivity(this, requestCode, intent, flags,
                    options.toBundle());
        }
        return PendingIntent.getActivity(this, requestCode, intent, flags);
    }

    private PendingIntent actionPending(String action, int requestCode, int option) {
        Intent intent = new Intent(this, CallActionReceiver.class).setAction(action);
        if (option >= 0) intent.putExtra(EXTRA_OPTION, option);
        return PendingIntent.getBroadcast(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void setCallScreenVisible(boolean visible) {
        if (!active) return;
        callScreenVisible = visible;
        if (visible) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            notifications.cancel(NOTIFICATION_ID);
        } else {
            String text = getSharedPreferences(SESSION, MODE_PRIVATE)
                    .getString("question", answered ? question()
                            : AppLanguage.ui(language, "Reminder call"));
            startForeground(NOTIFICATION_ID,
                    answered ? ongoingNotification(text) : incomingNotification());
        }
    }

    private void launchCallScreen() {
        try {
            startActivity(new Intent(this, CallActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (RuntimeException ignored) {
            // The full-screen notification remains the platform fallback when an OEM blocks
            // a direct background activity launch.
        }
    }

    private AudioAttributes voiceAudioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    private void configureVoiceRoute() {
        if (audioManager == null) return;
        if (!voiceRouteConfigured) {
            previousAudioMode = audioManager.getMode();
            previousSpeakerphone = audioManager.isSpeakerphoneOn();
            if (Build.VERSION.SDK_INT >= 31) {
                previousCommunicationDevice = audioManager.getCommunicationDevice();
            }
            voiceRouteConfigured = true;
            CallConflictDetector.markAppRouteActive(this);
        }
        try {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            if (Build.VERSION.SDK_INT >= 31) {
                AudioDeviceInfo preferred = preferredCommunicationDevice(speakerOn);
                if (preferred != null) audioManager.setCommunicationDevice(preferred);
            } else {
                audioManager.setSpeakerphoneOn(speakerOn);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {}
    }

    private AudioDeviceInfo preferredCommunicationDevice(boolean speaker) {
        if (audioManager == null || Build.VERSION.SDK_INT < 31) return null;
        int[] preferredTypes = speaker
                ? new int[]{AudioDeviceInfo.TYPE_BUILTIN_SPEAKER}
                : new int[]{AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE};
        for (int type : preferredTypes) {
            for (AudioDeviceInfo device : audioManager.getAvailableCommunicationDevices()) {
                if (device.getType() == type) return device;
            }
        }
        return null;
    }

    private void releaseVoiceRoute() {
        if (audioManager == null || !voiceRouteConfigured) return;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                audioManager.clearCommunicationDevice();
            }
        } catch (SecurityException | IllegalArgumentException ignored) {}
        try {
            if (Build.VERSION.SDK_INT >= 31 && previousCommunicationDevice != null) {
                audioManager.setCommunicationDevice(previousCommunicationDevice);
            } else if (Build.VERSION.SDK_INT < 31) {
                audioManager.setSpeakerphoneOn(previousSpeakerphone);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {}
        try {
            // One OEM communication-device failure must not skip mode restoration.
            audioManager.setMode(previousAudioMode);
        } catch (SecurityException | IllegalArgumentException ignored) {}
        CallConflictDetector.markAppRouteReleased(this,
                previousAudioMode == AudioManager.MODE_IN_COMMUNICATION
                        || audioManager.getMode() != AudioManager.MODE_IN_COMMUNICATION);
        previousCommunicationDevice = null;
        previousAudioMode = AudioManager.MODE_NORMAL;
        previousSpeakerphone = false;
        voiceRouteConfigured = false;
        speakerOn = false;
    }

    private void requestVoiceFocus() {
        if (audioManager == null) return;
        if (voiceFocus != null) return;
        voiceFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(voiceAudioAttributes())
                .setOnAudioFocusChangeListener(change -> {})
                .build();
        audioManager.requestAudioFocus(voiceFocus);
    }

    private void abandonVoiceFocus() {
        if (audioManager == null) return;
        if (voiceFocus != null) {
            audioManager.abandonAudioFocusRequest(voiceFocus);
            voiceFocus = null;
        }
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Reminder calls",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Incoming and ongoing reminder calls");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.enableVibration(true);
        // Ringing is managed by the service so the channel must stay silent;
        // otherwise some phones play the first ring twice.
        channel.setSound(null, null);
        notifications.createNotificationChannel(channel);
    }

    private void writeSession() {
        getSharedPreferences(SESSION, MODE_PRIVATE).edit()
                .putBoolean("active", active)
                .putBoolean("answered", answered)
                .putBoolean("finalizing", finalizing)
                .putLong("connectedAtMillis", connectedAtMillis)
                .putInt("step", step)
                .putInt("branch", branch)
                .putBoolean("transitioning", transitioning)
                .putBoolean("speakerOn", speakerOn)
                .putString("language", language)
                .putString("callerName", callerName())
                .putString("member", member)
                .putString("question", question())
                .putString("answerA", answerA())
                .putString("answerB", answerB())
                .putString("answerC", answerC())
                .putString("answerD", answerD())
                .putBoolean("customCall", customCall())
                .putBoolean("showDelayOptions", !customCall() && step == 2)
                .apply();
    }

    private String question() {
        if (confirmationCall()) {
            if (step == 2) return SpeechText.reminderDelayQuestion(language);
            return SpeechText.confirmationQuestion(member, label,
                    activeSchedule == null ? "custom" : activeSchedule.category, language);
        }
        if (customCall()) {
            return SpeechText.custom(activeSchedule.questions.get(step).prompt,
                    member, label, activeSchedule.category, language);
        }
        boolean meal = ReminderScheduler.PHASE_MEAL.equals(phase);
        if (step == 0) return meal ? SpeechText.mealQuestion(member, label,
                morningMeal(), preMinutes, language)
                : SpeechText.medicineQuestion(member, label, language);
        if (step == 2) return SpeechText.reminderDelayQuestion(language);
        return meal ? SpeechText.mealNotCompleted(label, language)
                : SpeechText.medicineNotTaken(language);
    }

    private String answerA() {
        if (customCall()) return answerAt(0);
        if (step == 2) return "";
        if (confirmationCall()) return SpeechText.confirmationDone(language);
        if (step == 0) return ReminderScheduler.PHASE_MEAL.equals(phase)
                ? "" : SpeechText.answerTaken(language);
        return ReminderScheduler.PHASE_MEAL.equals(phase)
                ? AppLanguage.ui(language, "Okay") : SpeechText.answerTaken(language);
    }

    private String answerB() {
        if (customCall()) return answerAt(1);
        if (confirmationCall() && step == 0) return SpeechText.confirmationNotDone(language);
        if (step == 0) return ReminderScheduler.PHASE_MEAL.equals(phase)
                ? "" : SpeechText.answerNotTaken(language);
        if (step == 1 && !ReminderScheduler.PHASE_MEAL.equals(phase)) {
            return SpeechText.answerLater(language);
        }
        return "";
    }

    private String answerC() {
        if (customCall()) return answerAt(2);
        if (confirmationCall() && step == 0) return SpeechText.answerLater(language);
        return "";
    }

    private String answerD() {
        return customCall() ? answerAt(3) : "";
    }

    private String answerAt(int index) {
        if (!customCall() || step < 0 || step >= activeSchedule.questions.size()) return "";
        java.util.List<RemoteStore.ScriptAnswer> answers = activeSchedule.questions.get(step).answers;
        return index >= 0 && index < answers.size() ? answers.get(index).label : "";
    }

    private int customAnswerCount() {
        if (!customCall() || step < 0 || step >= activeSchedule.questions.size()) return 0;
        return activeSchedule.questions.get(step).answers.size();
    }

    private boolean customCall() {
        return activeSchedule != null && activeSchedule.scripted()
                && ReminderScheduler.PHASE_MEDICINE.equals(phase);
    }

    private boolean confirmationCall() {
        return ReminderScheduler.PHASE_CONFIRMATION.equals(phase);
    }

    private void scheduleConfirmationAfterPrimary() {
        if (!"test-call".equals(scheduleId)
                && ReminderScheduler.PHASE_MEDICINE.equals(phase)
                && activeSchedule != null && activeSchedule.confirmationMinutes > 0) {
            ReminderScheduler.scheduleConfirmation(this, scheduleId);
        }
    }

    private boolean morningMeal() {
        if (doseHour >= 0) return doseHour < 12;
        Calendar expectedDose = Calendar.getInstance();
        expectedDose.add(Calendar.MINUTE, preMinutes);
        return expectedDose.get(Calendar.HOUR_OF_DAY) < 12;
    }

    private String callerName() { return AppLanguage.caller(language); }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private void broadcastState() {
        sendBroadcast(new Intent(ACTION_STATE_CHANGED).setPackage(getPackageName()));
    }

    static SharedPreferences session(Context context) {
        return context.getSharedPreferences(SESSION, Context.MODE_PRIVATE);
    }

    static boolean isProcessCallActive() { return processCallActive; }

    @Override public void onTaskRemoved(Intent rootIntent) {
        if (active && !finalizing) reject(true);
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        processCallActive = false;
        if (active && !finalizing && !"test-call".equals(scheduleId)) {
            ReminderScheduler.scheduleRetry(this, scheduleId, phase,
                    label, member, preMinutes);
        }
        handler.removeCallbacksAndMessages(null);
        stopRingAudio();
        cancelSpeechPlayback();
        abandonVoiceFocus();
        releaseVoiceRoute();
        if (tts != null) {
            tts.shutdown();
        }
        if (ringControlReceiver != null) {
            try { unregisterReceiver(ringControlReceiver); }
            catch (IllegalArgumentException ignored) {}
            ringControlReceiver = null;
        }
        super.onDestroy();
    }
}
