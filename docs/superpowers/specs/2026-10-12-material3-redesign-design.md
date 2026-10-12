# Remind Me — Material 3 redesign

Date: 2026-10-12 · Status: draft for owner review

## 1. Goal

Rebuild every screen of both apps — `గుర్తు చేయి` (member app, `simplecall`) and Admin (`admin`) — so they
look, feel and move like a current Google Pixel stock app (Clock, Phone, Calendar, Settings):
Material 3 components, Material You wallpaper colours, Material type scale, Material Symbols icons,
rounded shapes and fluid motion. Admin and the member app share one design system and the same UX.

Owner decisions (2026-10-12):

- Colours follow the phone's wallpaper (Material You dynamic colour); a brand palette is the fallback.
- The full-screen reminder call screen is redesigned too, in the style of Google's Phone app.
- Built with Jetpack Compose + Material 3 (Kotlin). The existing Java logic stays.
- Home = "Today" with a week strip (from direction A) plus a "Next up" card (from direction B).
- Admin has no "Next up" card; it shows a status line and the whole day instead.
- Buttons, text alignment and icons must be stock: real M3 components and Material Symbols, no emoji,
  no hand-drawn controls.

Mockups: `.superpowers/brainstorm/6824-1791805938/content/home-combined.html` and
`admin-editor-call-v2.html` (drawn at 1px = 1dp).

### Success criteria

1. Every screen uses stock Compose Material 3 components; no custom-drawn buttons, chips, sheets,
   switches or nav bars remain.
2. On Android 12+ the colours follow the wallpaper; on Android 8–11 the brand palette is used.
3. Admin and member app screens that exist in both are the same composables.
4. All current behaviour keeps working: reminders, calls, retries, remind-later, skip days, offline
   use, sync, sign-in, six languages, lock-screen calls, the ongoing-call notification.
5. All existing unit tests still pass; new UI state logic has unit tests.
6. Owner checks every screen on their phone and approves before it ships to family phones.

## 2. Out of scope

- No change to how reminders are scheduled, rung, retried or synced (`ReminderScheduler`,
  `AlarmReceiver`, `CallService` call logic, `RemoteSync`, `AdminFirebase`, Cloud Functions).
- No change to the voice / question logic or Telugu clips.
- No iOS or web app.
- No Play Store release work; APKs are installed directly as today.

## 3. Architecture

### Modules

```
:designsystem   (new, Android library, Kotlin + Compose)
    theme/      RemindMeTheme: dynamic colour + brand fallback, typography, shapes, motion tokens
    components/ shared composables (see §5)
    icons/      Material Symbols Rounded mapping per reminder category
:simplecall     member app — new Compose screens in Kotlin, existing Java logic unchanged
:admin          Admin app — new Compose screens in Kotlin, existing Java logic unchanged
```

- Kotlin + Compose are added to the build (Compose BOM, `material3`, `navigation-compose`,
  `lifecycle-viewmodel-compose`, `activity-compose`).
- Icons: Google's Material Symbols Rounded variable font, bundled in `:designsystem` and subset to
  the glyphs the apps use (the FILL axis gives outlined vs filled). Rendered through one `Symbol`
  composable so size, weight and fill follow M3 rules everywhere.
- Each screen gets a small Kotlin `ViewModel` (state holder) that calls the existing Java classes
  (`RemoteStore`, `RemoteSync`, `DailyCallStatus`, `ReminderScheduler`, `AdminFirebase`, `Models`) and
  exposes immutable UI state as `StateFlow`. Composables only read state and send events.
- `MainActivity` in each app becomes a single `ComponentActivity` hosting a Compose `NavHost`.
  `CallActivity` stays its own activity (lock screen / show-when-locked flags) but renders Compose.
- The hand-drawn `Ui.java` helpers, `AgendaTimeline`, `CompactSheetLayout`, `TimelineProgress` and
  the View-based UI code in both `MainActivity` files are deleted once their screens are replaced.

### Shared screens between apps

Reminder rows, the week strip, the reminder editor, the questions editor, the skip sheet, settings
rows and sign-in are composables in `:designsystem`, fed by each app's state. That is how both apps
get the same UX by construction. Each app keeps its own data adapters (member: `RemoteStore.Schedule`;
Admin: `Models.Schedule`) mapping into one shared UI model, `ReminderUi`.

## 4. Visual system

- **Colour:** `dynamicLightColorScheme` / `dynamicDarkColorScheme` on Android 12+. Fallback scheme
  generated from the current brand violet `#6652CC` as seed (Material Theme Builder, tonal palettes).
  Light and dark both supported, following the system setting.
- **Type:** Material 3 type scale. Font: Roboto Flex (the closest freely available match to Pixel's
  Google Sans Text); Telugu, Hindi, Tamil, Kannada and Malayalam fall back to the system Noto fonts.
  Respects the system font size; layouts tested at 130% for older family members.
- **Shape:** M3 shape scale — extra-large 28dp for sheets and the Next up card, large 16dp for FAB
  and cards, medium 12dp for list leading tiles, full for buttons and nav indicators, small 8dp for chips.
- **Icons:** Material Symbols Rounded, outlined when inactive and filled when selected.
  Category icons: Medicine `medication`, Supplement `pill`, Meal `restaurant`, Drink `water_full`,
  Exercise `fitness_center`, Appointment `event`, Bill or payment `payments`, Task `task_alt`,
  Wake-up `alarm`, Custom `notifications`.
- **Component sizes (stock M3):** buttons 40dp, label-large 14sp/500, icon 18dp + 8dp gap, content
  centred; call-screen answer buttons 56dp; FAB 56dp; navigation bar 80dp with 64×32dp pill
  indicator; chips 32dp; list items 72dp (two-line); touch targets at least 48dp.
- **Motion:** M3 motion tokens and springs.
  - Tab switches: fade-through. Opening a screen: shared-axis X. Back: predictive back gesture.
  - Bottom sheets: standard M3 modal sheet drag/settle.
  - Next up card and status badges: `animateContentSize` + crossfade when state changes.
  - List: `animateItem` so rows slide when status or order changes.
  - Buttons and chips: stock ripple and state layers; FAB shrinks to icon-only when scrolling.
  - Respects the system "Remove animations" setting.

## 5. Screens

### Navigation

| Member app | Admin |
|---|---|
| Today · Reminders · Settings | Today · Reminders · Family · Settings |

Navigation bar on phones (both apps are phone-only).

### Today (both apps)

- Large title "Today" (or the weekday when another day is picked) with the full date below.
- **Admin only:** a row of filter chips for family members (selected chip shows a check), and a
  48dp status line: "4 of 13 done" + phone connection state (from the `devices` listener).
- **Week strip:** 7 days (Sun–Sat of the current week, swipe for previous/next week). Selected day is
  a filled pill. A small red dot marks skipped days.
- **Member only, today only — Next up card** (extra-large shape, primary container colour):
  "NEXT UP · IN 40 MIN", reminder name, time and mode, a progress ring "4/13", and two buttons:
  **Done** (filled) and **Skip today** (tonal). When nothing is left today it shows "All done for today".
- **Skipped day:** the top card becomes an error-container card "All reminders skipped",
  "16–17 Oct · back on Sunday", with **Resume reminders**.
- **The day's list:** grouped Morning / Afternoon / Evening / Night, M3 two-line list items: leading
  category icon in a tonal circle, title, "7:00 PM · Call", trailing status badge (Upcoming, Calling,
  Retry 6:05, Done, Skipped, Not today). Past items fade to 60%. Tapping the badge opens today's
  status sheet (Pending / Done / Skip today); tapping the row opens the editor.
- **Extended FAB** "Reminder" with `add` icon.
- Past and future days show the schedule; statuses are shown for today (and for past days only
  where the phone has recorded them, see §6).

### Reminders tab (both apps)

All reminders as Clock-style cards: large time, name, repeat days, mode, and an M3 switch to turn a
reminder on or off. Tap opens the editor. In Admin the member chips filter the list.

### Reminder editor (both apps)

Full-height modal bottom sheet: close icon, title ("New reminder" / "Edit reminder"), **Save**
filled button, overflow menu with **Delete** (with confirm dialog).
Fields: outlined text field for the name; category filter chips with icons; time shown large in a
time-input chip with AM/PM toggle, tapping opens the stock M3 `TimePicker` (clock face) in a dialog;
repeat days as 40dp round toggles with "Every day" shortcut; Call / Reminder segmented button;
list rows for **Meal reminder** (off / 15 / 30 / 45 / 60 min) and **Questions & answers**
(opens the questions editor screen). Validation errors show as supporting text under the field.

### Questions editor (both apps)

Full screen with top app bar: reorderable list of questions, each with its answers as chips; add /
edit a question in a sheet with outlined fields. Same rules as today (`[Name]`, `[Reminder]`
placeholders, built-in Remind me later answer shown as a locked chip).

### Skip reminders (both apps)

M3 modal sheet: **Skip today**, **Choose days** (stock M3 `DateRangePicker`, at most 60 days,
past days disabled), and **Resume reminders** when a skip is set. Reached from Settings, from the
Next up card's overflow, and from a skipped day's card. Behaviour exactly as shipped in `b45beec`.

### Settings (both apps)

M3 list in sections: **Reminders** (Skip reminders, permissions status with fix-it rows for
exact alarms / notifications / full-screen calls / battery), **Language** (opens a radio-button
dialog with the six languages), **Account** (phone number, Log out with confirm dialog),
**About** (version). Admin adds **Family** shortcuts.

### Family (Admin only)

List of members as M3 list items (avatar initial, name, phone, connected / not signed in chip).
Tap opens a member detail screen: name, phone number (edit in a dialog), language, connected phone,
remove member (confirm dialog). Extended FAB "Add member" opens a sheet. Pairing code shows in a
dialog with large digits, as today.

### Sign-in (both apps)

Centred layout: app icon, title, one outlined phone field with +91 prefix, **Send OTP** filled
button, then a 6-box OTP field, **Verify**, and **Resend OTP** text button with countdown.
Truecaller button (tonal) when available. Errors as supporting text.

### Call screen (member app)

Kept behaviour, new look modelled on Google's Phone app:

- **Incoming:** soft vertical gradient from primary container to surface, 120dp avatar with
  filled `alarm` symbol, "Chitti" in display-small, "Reminder call · Workout" below. Pixel-style
  answer: swipe-up gesture plus 72dp round **Decline** (error) and **Answer** (green) buttons
  with labels. Ringing pulse animation on the avatar.
- **Ongoing / questions:** smaller avatar, name, call timer; the question in headline-small,
  centred; answers as 56dp full-width buttons — first answer filled, others tonal, Remind me later
  outlined with `snooze` icon; red 96×56dp pill **Hang up** at the bottom.
- **Remind me later:** M3 sheet with 5 / 15 / 30 / 60 minute choices and "Choose time"
  (M3 `TimePicker`).
- **Reminder mode (text):** same layout without timer / hang up; **Okay** filled button for
  information-only messages.
- Ongoing CallStyle notification, lock-screen flags, volume/power silencing: unchanged.

## 6. New data needed

The Admin status line, and statuses on past days, need the phone to share what happened.
Today this lives only in the phone's `DailyCallStatus` preferences.

- The member phone writes a small daily summary to
  `families/{familyId}/members/{memberId}/days/{yyyy-MM-dd}`:
  `{ statuses: { <scheduleId>: "completed" | "skipped" | "missed" | "pending" }, updatedAt }`,
  written when a status changes (debounced) and kept for 30 days.
- Firestore rules: the paired phone may create/update its own member's day documents with only
  those keys; the owner may read. No Cloud Function change.
- Admin reads the selected member's day document for the selected date.
- If no document exists (old app version, offline phone), the UI shows the schedule without
  statuses and the status line says "No status yet".

## 7. Error handling

- Sync failures show an M3 snackbar with the message the apps already use and a **Retry** action.
- Saving uses the existing optimistic update; on failure the change is rolled back and a snackbar
  explains it.
- Missing permissions show a persistent tonal card at the top of Today linking to the fix in Settings.
- Offline: everything still renders from the local cache, with a small "Offline" chip in the top bar.

## 8. Testing

- Existing JUnit tests in both apps must pass unchanged.
- New JVM unit tests for every ViewModel's state mapping (Next up selection, day grouping, status
  badges, skipped-day card, Admin status line, week-strip ranges).
- Compose UI tests (instrumented, on the owner's phone) for: add/edit/delete reminder, change status,
  skip and resume, language switch, sign-in screen states, call screen incoming → answer → answer a
  question → hang up (using the existing test-call path).
- Screenshot check of every screen in light, dark, a non-blue wallpaper colour, Telugu, and 130% font.
- Manual check on the owner's phone (Samsung S22 Ultra) before any family rollout.

## 9. Delivery

Built and reviewed in phases, each installed on the owner's phone for review before the next:

1. Design system + member app Today, Reminders, editor, skip, Settings, sign-in.
2. Call screen.
3. Admin app on the same components (+ Family, member chips).
4. Daily status sharing (§6) + Admin status line + past-day statuses.

Then version bump, install on family phones, update README and design notes. Expected APK growth
about 3–5 MB per app.

## 10. Risks

- **Call screen regressions** — it is the core of the product. Mitigation: Compose replaces only the
  drawing; `CallService` and the call state machine are untouched; phase 2 is tested on the phone
  with real reminder calls before Admin work starts.
- **Kotlin/Compose added to a Java project** — build time and APK size grow. Mitigation: R8 shrinking
  stays on; measure APK size in each phase.
- **Older family phones (Android 8–11)** — no dynamic colour. Mitigation: brand fallback palette,
  tested on an API 26 emulator.
- **Old app versions** on family phones won't write day statuses; Admin shows "No status yet".
