# Gurthu Chey 2.0

Two lightweight native Android apps for private family reminder calls:

- `com.gurthuchey.admin` (`Admin`) manages every family member and reminder.
- `com.gurthuchey.app` (`గుర్తు చేయి`) shows and edits only the signed-in member's reminders and runs the calls.

Both apps use the same lightweight pastel interface. The incoming reminder-call screen keeps its high-contrast light design, large Telugu controls, and normal Android incoming-call behavior.

## Sign-in and ownership

Both apps use mobile-number authentication. SMS OTP is always available. Truecaller is an optional shortcut on supported Android phones and falls back to SMS OTP when unavailable.

The Admin app no longer exposes Google sign-in. An existing Admin installation can verify a mobile number once and link that number to the existing Firebase owner account, preserving all members and schedules. After that, OTP sign-in on a reinstalled or new Admin phone restores the same family. The Admin app has a visible **Log out** action.

Each member has one Admin-assigned Indian mobile number. When that number signs into `గుర్తు చేయి`, Firebase restores only that member and registers the phone automatically. There is no QR code or six-digit connection code in the new flow. The member can add, edit, or delete only their own schedules; Firestore rules prevent access to other members.

## Reminder behavior

- The medicine name is entered without the word `tablet`; Chitti adds `టాబ్లెట్` to the Telugu prompt.
- Reminder categories are Medicine, Appointment, Bill or payment, Exercise, Meal, Task, Wake-up, and Custom.
- Medicine keeps its tested dose flow. Every other category uses the ordered questions, answer buttons, and spoken responses written in Admin.
- Custom scripts can use `[Name]` and `[Reminder]`; both placeholders are resolved before Telugu voice generation.
- Every scripted question includes a built-in, localized **Remind me later** answer. It asks for 5, 15, 30, or 60 minutes and schedules a one-time offline callback without changing the regular schedule.
- A configurable meal call can run 15, 30, 45, or 60 minutes before a medicine dose, or be turned off.
- Morning meal calls say `టిఫిన్`; afternoon and evening calls say `భోజనం`.
- Meal calls are informational: Chitti speaks the full message and ends the call without showing answer buttons.
- Medicine calls ask whether the tablet was taken. The screen provides Telugu buttons for taken, not taken, and asking for more time.
- Extra reminder choices are 5, 15, 30, or 60 minutes. They create a one-time local retry and do not modify the next day's regular schedule.
- Incoming calls ring for one minute. Rejected, missed, ended early, or incompletely answered calls retry five minutes later. Calls deferred because a cellular or VoIP call is already active also retry after five minutes.
- While an answered call is hidden, Android ongoing CallStyle shows Chitti, elapsed call timer, and Hang up action. Supported Android 12+ devices can promote it to a status-bar call timer chip. Notification stays non-clearable and re-posts itself if Android or an OEM permits dismissal. Tapping it reopens the call screen.
- The reminder uses its own bundled ringtone. Power and volume keys silence the reminder ringtone without dismissing the incoming call.
- Calls open full screen, including over the lock screen. If the person deliberately leaves the call screen, an ongoing call notification remains and opens the full call screen when tapped.
- The call uses large swipe-to-answer and swipe-to-reject controls. Active questions use large tap buttons; the app does not need microphone access or speech recognition.

Schedules, active retry state, ringtone behavior, question logic, and cached voice clips work offline after the first successful sync. Firebase generates or reuses all required Telugu clips before pushing a schedule change to the phone. The phone downloads, validates, and activates the complete voice set for offline calls, including custom questions and responses. Android TTS remains only a last-resort audio fallback if a required clip has not yet downloaded.

## Firebase

Project: `gurthu-chey`, region: `asia-south1`.

The production model uses:

- Firebase Phone Authentication for OTP.
- Firestore `phoneAssignments` to map a verified number to exactly one member.
- Per-user device documents and Firestore rules for member isolation.
- FCM data pushes for immediate schedule refresh.
- Firestore-triggered Truecaller authorization-code exchange; no service-account secret is bundled in either APK.
- Cloud Text-to-Speech only when a new natural Telugu clip must be generated.

The hosted privacy policy, terms, and app page are at `https://gurthu-chey.web.app`. Both Truecaller projects have been submitted for production verification; SMS OTP works independently of that approval.

## Installation

1. Install `admin/build/outputs/apk/release/Gurthu-Chey-Admin-2.0.apk` on the administrator phone.
2. On the existing Admin installation, sign in with OTP or Truecaller once so the mobile number is linked to the current family owner.
3. Add or edit each member's mobile number in Admin.
4. Install `simplecall/build/outputs/apk/release/Gurthu-Chey-2.0.apk` on each member's phone and sign in with that assigned number.
5. Allow notifications, exact alarms, full-screen calls, and unrestricted battery use when prompted. Test one scheduled reminder after any phone OS update.

Because the final user package is `com.gurthuchey.app`, an older build using `com.gurthuchey.remindercall` may appear as a separate app. Once 2.0 has signed in and restored the schedule, the old package can be uninstalled.

## Build and checks

Use JDK 17 and Android SDK 35:

```bash
./gradlew test lintRelease assembleRelease
npm run check --prefix functions
```

Release APKs are R8-minified, resource-shrunk, and signed with this workstation's existing Android signing certificate so future direct-install updates remain compatible.

This is a reminder aid, not a medical device. Keep a backup medication plan for critical medicines.
