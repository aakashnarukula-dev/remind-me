# Material 3 Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild every screen of the member app (`:simplecall`) and Admin (`:admin`) in Jetpack Compose Material 3 so both look and move like a Pixel stock app, with one shared design system.

**Architecture:** A new Android library `:designsystem` (Kotlin + Compose) holds the theme, fonts, icons, shared UI models, pure screen logic and every composable both apps use. Each app gets Kotlin view models in its existing Java package that call the unchanged Java logic (`RemoteStore`, `RemoteSync`, `DailyCallStatus`, `ReminderScheduler`, `CallService`, `AdminFirebase`, `Models`) and map it into the shared UI models. View-based UI code is deleted as each screen is replaced.

**Tech Stack:** Kotlin 2.2.20, Compose BOM 2025.09.00 (Material 3 1.4), navigation-compose 2.9.x, lifecycle-viewmodel-compose 2.9.x, activity-compose 1.10.1, AGP 8.13.2, Firebase BOM 34.18.0 (unchanged), JUnit 4, compose-ui-test on the `pixel_api36` and `api28` emulators.

**Spec:** `docs/superpowers/specs/2026-10-12-material3-redesign-design.md`

## Global Constraints

- minSdk 26, targetSdk/compileSdk 35, Java/Kotlin target 17 — unchanged.
- Colours: `dynamicLightColorScheme`/`dynamicDarkColorScheme` on Android 12+; brand fallback scheme generated from seed `#6652CC`; light and dark follow the system.
- Font: Roboto Flex (bundled). Indic scripts fall back to system Noto fonts. Layouts must hold at 130% font scale.
- Icons: Material Symbols Rounded variable font, bundled and subset; outlined when inactive, filled (FILL=1) when selected. No emoji and no hand-drawn controls anywhere.
- Stock sizes: buttons 40dp, label-large 14sp/500, icon 18dp + 8dp gap, content centred; call answer buttons 56dp; FAB 56dp; navigation bar 80dp with 64×32dp indicator; chips 32dp; two-line list items 72dp; touch targets ≥ 48dp.
- Shapes: sheets and Next up card 28dp; FAB and cards 16dp; list leading tiles 12dp; chips 8dp; buttons and nav indicators full.
- Motion: fade-through between tabs, shared-axis X for pushed screens, predictive back, `animateContentSize`/crossfade on state changes, `animateItem` on lists; honour the system "Remove animations" setting.
- Do not change behaviour or code of `ReminderScheduler`, `AlarmReceiver`, `CallService` call logic, `RemoteSync`, `AdminFirebase` publishing, Cloud Functions or voice logic, except the additions named in Tasks 6 (`RemoteSync.saveOwnSchedules` writes each reminder's real `enabled` value) and 11 (day statuses).
- All six languages (`en te hi ta kn ml`) keep working; new user-visible strings go through each app's `AppLanguage.ui`.
- Instrumented tests run on emulators only — never `connectedAndroidTest` on the owner's phone (it uninstalls the app and wipes its data). Install on the phone with `adb install -r` only.
- Commits are authored `Aakash Narukula <aakashnarukula.dev@gmail.com>` with no AI attribution.

## Review Focus

1. **130% font scale + Telugu text** — badges, chips, buttons and the Next up card must neither clip nor overlap; long labels ellipsize on one line. (Task 3 test `rowAndCardsSurviveLargeTeluguText`.)
2. **Midnight while the app is open** — Today must roll to the new date and Next up recompute without a restart. (Task 2 test `rollsToNextDayAtMidnight`, Task 6 ticker.)
3. **Nothing to show** — no reminders, all done, or none scheduled today must give a clear empty state, never a blank screen or a crash. (Task 2 tests `allDoneShowsAllDoneCard`, `noRemindersGivesEmptyState`.)
4. **Retry carried past midnight** — a 23:55 reminder retrying at 00:05 still shows as yesterday's retry, and today's Next up does not claim it. (Task 2 test `carriedRetryIsNotNextUpForNewDay`.)
5. **Unknown or legacy data** — an unknown category string, missing questions, or a member doc without day statuses renders with the Custom icon / "No status yet" instead of failing. (Task 2 test `unknownCategoryFallsBackToCustom`, Task 11 test `missingDayDocShowsNoStatusYet`.)

---

## File Structure

```
settings.gradle                         include ':designsystem'
build.gradle                            + kotlin android / compose plugins
designsystem/build.gradle               new library module
designsystem/src/main/res/font/         roboto_flex.ttf, material_symbols_rounded.ttf (subset)
designsystem/src/main/kotlin/com/gurthuchey/design/
  theme/Theme.kt                        RemindMeTheme, BrandColors
  theme/Type.kt  theme/Shape.kt  theme/Motion.kt
  icons/Symbol.kt                       Symbol composable + SymbolGlyph enum
  model/ReminderUi.kt                   ReminderUi, Category, Mode, ReminderStatus, SkipDays
  model/TodayModel.kt                   pure: sections, next up, progress, day card
  model/ReminderDraft.kt                editor draft + validate()
  components/*.kt                       ReminderRow, StatusBadge, WeekStrip, NextUpCard, DayCard,
                                        SectionHeader, AppScaffold, SkipSheet, StatusSheet,
                                        SettingsItems, ConfirmDialog
  screens/TodayScreen.kt  RemindersScreen.kt  ReminderEditor.kt  QuestionsEditor.kt  SignInScreen.kt
  call/CallUi.kt                        CallUiState + Incoming/Ongoing composables
designsystem/src/test/...               JVM tests for model/*
designsystem/src/androidTest/...        compose UI tests (emulator)
scripts/subset-symbols.py               builds the icon font subset from SymbolGlyph
simplecall/src/main/java/com/gurthuchey/remindercall/
  MemberActivity.kt (replaces MainActivity.java)  MemberViewModel.kt  ScheduleMapper.kt
  CallActivity.java -> CallActivity.kt (Compose host)  CallSession.kt
  DaySummary.java  DayStatusJob.java  (Task 11)
admin/src/main/java/com/gurthuchey/admin/
  AdminActivity.kt (replaces MainActivity.java)  AdminViewModel.kt  AdminMapper.kt  FamilyScreens.kt
firestore.rules                         + days subcollection (Task 11)
```

Kotlin files in the apps live in the existing Java package so they can use the package-private Java classes.

---

## Phase 1 — Design system and member app

### Task 1: Build setup, theme, fonts and icons

**Files:**
- Modify: `settings.gradle`, `build.gradle`, `simplecall/build.gradle`, `admin/build.gradle`
- Create: `designsystem/build.gradle`, `designsystem/src/main/AndroidManifest.xml`, `theme/Theme.kt`, `theme/Type.kt`, `theme/Shape.kt`, `theme/Motion.kt`, `icons/Symbol.kt`, `res/font/roboto_flex.ttf`, `res/font/material_symbols_rounded.ttf`, `scripts/subset-symbols.py`
- Test: `designsystem/src/test/kotlin/com/gurthuchey/design/icons/SymbolGlyphTest.kt`

**Interfaces:**
- Produces: `@Composable fun RemindMeTheme(dynamic: Boolean = true, content: @Composable () -> Unit)`; `object BrandColors { val light: ColorScheme; val dark: ColorScheme }`; `val RemindMeTypography: Typography` (Roboto Flex); `val RemindMeShapes: Shapes` (extraSmall 4, small 8, medium 12, large 16, extraLarge 28); `object Motion { fun <T> spatial(): FiniteAnimationSpec<T>; fun <T> effects(): FiniteAnimationSpec<T> }` (M3 expressive default springs); `enum class SymbolGlyph(val codepoint: Int)`; `@Composable fun Symbol(glyph: SymbolGlyph, modifier: Modifier = Modifier, size: Dp = 24.dp, filled: Boolean = false, tint: Color = LocalContentColor.current, contentDescription: String? = null)`.

- [ ] **Step 1:** Add `id 'org.jetbrains.kotlin.android' version '2.2.20' apply false` and `id 'org.jetbrains.kotlin.plugin.compose' version '2.2.20' apply false` to root `build.gradle`; `include ':designsystem'` in `settings.gradle`. Create `designsystem/build.gradle` (namespace `com.gurthuchey.design`, minSdk 26, compileSdk 35, Java 17, `buildFeatures { compose true }`, deps: Compose BOM `2025.09.00`, `material3`, `ui`, `ui-tooling-preview`, `animation`, `foundation`; test: junit 4.13.2; androidTest: `ui-test-junit4`, `ui-test-manifest`). Apply both Kotlin plugins + compose to both apps and add `implementation project(':designsystem')`, `activity-compose 1.10.1`, `navigation-compose 2.9.0`, `lifecycle-viewmodel-compose 2.9.0`.
- [ ] **Step 2:** Download Roboto Flex variable TTF (Google Fonts) to `res/font/roboto_flex.ttf`; download `MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf` and its `.codepoints` file from `google/material-design-icons/variablefont`.
- [ ] **Step 3: Write the failing test** `SymbolGlyphTest.everyGlyphMatchesOfficialCodepoint` — parses `designsystem/src/test/resources/MaterialSymbolsRounded.codepoints` (copied in Step 2) and asserts each `SymbolGlyph` name (lower-case, e.g. `CALL_END` → `call_end`) maps to the same codepoint; and `everyCategoryHasAGlyph` asserting `Category.values().all { it.glyph != null }` (Category comes in Task 2 — write this second test in Task 2).
- [ ] **Step 4:** Run `./gradlew :designsystem:testDebugUnitTest` — Expected: FAIL (SymbolGlyph missing).
- [ ] **Step 5:** Implement `SymbolGlyph` with every glyph the spec names: `MEDICATION, PILL, RESTAURANT, WATER_FULL, FITNESS_CENTER, EVENT, PAYMENTS, TASK_ALT, ALARM, NOTIFICATIONS, ADD, CHECK, CLOSE, CANCEL, MORE_VERT, MORE_HORIZ, CHEVRON_RIGHT, TODAY, LIST_ALT, GROUP, SETTINGS, SEARCH, CALL, CALL_END, SNOOZE, KEYBOARD_ARROW_UP, PHONE_ANDROID, PAUSE_CIRCLE, PLAY_CIRCLE, LANGUAGE, LOGOUT, INFO, DELETE, EDIT, DRAG_HANDLE, LOCK, WIFI_OFF, VOLUME_UP, PERSON_ADD, SCHEDULE, CALENDAR_MONTH, WARNING`. `Symbol` renders `String(Character.toChars(codepoint))` in the bundled font with `FontVariation.Settings(FontVariation.Setting("FILL", if (filled) 1f else 0f), FontVariation.weight(400), FontVariation.Setting("opsz", size.value))`.
- [ ] **Step 6:** Write `scripts/subset-symbols.py`: reads `SymbolGlyph` names from `Symbol.kt`, looks codepoints up in the `.codepoints` file, runs `fontTools.subset` with `--unicodes=<list> --layout-features='*' --flavor= --output-file=designsystem/src/main/res/font/material_symbols_rounded.ttf`, keeping variation axes; exits non-zero if a name is missing. Run it: `python3 scripts/subset-symbols.py` — Expected: subset font under 300 KB.
- [ ] **Step 7:** Implement `Theme.kt` (dynamic on API 31+ else `BrandColors`; generate `BrandColors` light/dark from seed `#6652CC` with Material Theme Builder → export Compose → paste values), `Type.kt` (M3 type scale, all styles on Roboto Flex), `Shape.kt`, `Motion.kt`.
- [ ] **Step 8:** Run `./gradlew :designsystem:testDebugUnitTest :simplecall:assembleRelease :admin:assembleRelease` — Expected: tests PASS, both APKs build (no UI changed yet).
- [ ] **Step 9: Commit** `feat: add designsystem module with M3 theme, fonts and symbols`

### Task 2: Shared UI models and Today logic

**Files:**
- Create: `designsystem/src/main/kotlin/com/gurthuchey/design/model/ReminderUi.kt`, `model/TodayModel.kt`
- Test: `designsystem/src/test/kotlin/com/gurthuchey/design/model/TodayModelTest.kt`, add `everyCategoryHasAGlyph` to `SymbolGlyphTest`

**Interfaces:**
- Produces:
  - `enum class Category(val key: String, val glyph: SymbolGlyph)` — keys `medicine supplement meal drink exercise appointment bill task wakeup custom` with glyphs MEDICATION, PILL, RESTAURANT, WATER_FULL, FITNESS_CENTER, EVENT, PAYMENTS, TASK_ALT, ALARM, NOTIFICATIONS; `companion fun fromKey(key: String?): Category` (unknown → CUSTOM). Match keys to the strings stored today in `RemoteStore.Schedule.category` / `Models.Schedule.category` (check both files; map any alias there).
  - `enum class Mode { CALL, TEXT }`
  - `enum class ReminderStatus { UPCOMING, PENDING, CALLING, RETRY, COMPLETED, MISSED, SKIPPED, NOT_TODAY, UNKNOWN }`
  - `data class ReminderUi(val id: String, val title: String, val category: Category, val hour: Int, val minute: Int, val days: Int /* Mon=bit0 */, val mode: Mode, val enabled: Boolean, val preMinutes: Int, val questionCount: Int, val status: ReminderStatus = ReminderStatus.UNKNOWN, val retryAt: Long = 0L)`
  - `data class SkipDays(val from: LocalDate, val until: LocalDate) { fun covers(d: LocalDate): Boolean; val resumeOn: LocalDate }`
  - `enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }` with `fun dayPartOf(hour: Int): DayPart` using the same boundaries as `AppLanguage.timePeriod` in both apps (read it; copy the boundaries).
  - `sealed interface DayCard { data class NextUp(val reminder: ReminderUi, val minutesAway: Long) ; object AllDone ; data class Skipped(val skip: SkipDays) ; object None }`
  - `data class DayView(val date: LocalDate, val sections: List<Pair<DayPart, List<ReminderUi>>>, val card: DayCard, val done: Int, val total: Int, val empty: Boolean)`
  - `object TodayModel { fun build(reminders: List<ReminderUi>, date: LocalDate, now: LocalDateTime, skip: SkipDays?, showNextUp: Boolean): DayView; fun weekOf(date: LocalDate): List<LocalDate> /* Sun..Sat */ }`

Rules `build` must follow (pin each in a test):
- Only `enabled` reminders whose `days` bit includes `date`'s weekday appear, sorted by time; others are left out except in `total` logic below.
- `card`: if `skip?.covers(date)` → `Skipped`; else if `!showNextUp` or `date != now.toLocalDate()` → `None`; else the first reminder with status in {UPCOMING, PENDING, CALLING, RETRY} whose time ≥ `now` or whose status is CALLING/RETRY → `NextUp`; else if the day has reminders → `AllDone`; else `None`.
- `total` = reminders shown for the date minus SKIPPED; `done` = COMPLETED count.
- `empty` = no reminders shown for the date.

- [ ] **Step 1: Write the failing tests** in `TodayModelTest`:
  - `nextUpIsFirstUpcomingAfterNow` (reminders 13:00 COMPLETED, 18:00 UPCOMING, 19:00 UPCOMING, now 17:20 → NextUp 18:00, minutesAway 40)
  - `retryBeatsLaterUpcoming` (17:00 RETRY, 18:00 UPCOMING, now 17:20 → NextUp 17:00)
  - `allDoneShowsAllDoneCard`, `noRemindersGivesEmptyState` (card None, empty true)
  - `skippedDayShowsSkippedCard` (skip 16–17 Oct, date 16 Oct → Skipped; resumeOn 18 Oct)
  - `otherDayHasNoNextUp`, `adminNeverShowsNextUp` (showNextUp=false → None)
  - `progressCountsDoneOverNonSkipped` (13 shown, 2 SKIPPED, 4 COMPLETED → done 4, total 11)
  - `sectionsGroupByDayPartInOrder`, `disabledAndOffDayRemindersAreHidden`
  - `rollsToNextDayAtMidnight` (same reminders, now 12 Oct 23:59 vs 13 Oct 00:01 → different date, NextUp recomputed)
  - `carriedRetryIsNotNextUpForNewDay` (reminder 23:55 with status RETRY but `date` = next day and its status for the new day UPCOMING at 23:55 → NextUp is the earliest reminder of the new day after now, not a retry)
  - `unknownCategoryFallsBackToCustom` (`Category.fromKey("garden")` == CUSTOM)
  - `weekStartsOnSunday` (`weekOf(2026-10-14)` = 11..17 Oct)
- [ ] **Step 2:** Run `./gradlew :designsystem:testDebugUnitTest` — Expected: FAIL (types missing).
- [ ] **Step 3:** Implement the models and `TodayModel` in the files above (java.time; minSdk 26 has it).
- [ ] **Step 4:** Run `./gradlew :designsystem:testDebugUnitTest` — Expected: all PASS.
- [ ] **Step 5: Commit** `feat: add shared reminder UI models and Today logic`

### Task 3: Shared components

**Files:**
- Create: `designsystem/src/main/kotlin/com/gurthuchey/design/components/{StatusBadge,ReminderRow,WeekStrip,DayCards,SectionHeader,AppScaffold,SkipSheet,StatusSheet,SettingsItems,ConfirmDialog}.kt`, `designsystem/src/main/kotlin/com/gurthuchey/design/Strings.kt`
- Test: `designsystem/src/androidTest/kotlin/com/gurthuchey/design/components/ComponentsTest.kt`

**Interfaces:**
- Consumes: Task 1 theme/Symbol, Task 2 models.
- Produces:
  - `fun interface Strings { fun get(english: String): String }` + `val LocalStrings = staticCompositionLocalOf<Strings> { Strings { it } }` — each app provides its `AppLanguage.ui` here; every composable reads text through it.
  - `@Composable fun StatusBadge(status: ReminderStatus, retryAt: Long, onClick: (() -> Unit)?)` — 24dp tonal badge; COMPLETED uses a green tertiary-style container with `CHECK` icon; SKIPPED uses errorContainer; NOT_TODAY disabled (no click).
  - `@Composable fun ReminderRow(reminder: ReminderUi, faded: Boolean, onClick: () -> Unit, onStatusClick: (() -> Unit)?, modifier: Modifier = Modifier)` — `ListItem`, 72dp, leading 40dp `primaryContainer` circle with category Symbol, headline title (1 line, ellipsis), supporting "7:00 PM · Call", trailing `StatusBadge`.
  - `@Composable fun WeekStrip(week: List<LocalDate>, selected: LocalDate, today: LocalDate, skip: SkipDays?, onSelect: (LocalDate) -> Unit, onSwipeWeek: (Int) -> Unit)` — 56dp day pills; selected = primary; skipped days get a 4dp error dot.
  - `@Composable fun NextUpCard(card: DayCard.NextUp, done: Int, total: Int, onDone: () -> Unit, onSkipToday: () -> Unit)`, `@Composable fun AllDoneCard()`, `@Composable fun SkippedDayCard(skip: SkipDays, onResume: () -> Unit)` — extra-large shape; NextUp uses `primaryContainer`, filled **Done** + tonal **Skip today** buttons, `CircularProgressIndicator`-based ring with "done/total"; SkippedDay uses `errorContainer` with **Resume reminders**. All wrapped in `AnimatedContent` keyed by card type.
  - `@Composable fun SectionHeader(part: DayPart)`.
  - `data class NavTab(val route: String, val label: String, val glyph: SymbolGlyph)` + `@Composable fun AppScaffold(tabs: List<NavTab>, current: String, onTab: (String) -> Unit, fab: (@Composable () -> Unit)?, topBar: @Composable () -> Unit, snackbarHost: SnackbarHostState, content: @Composable (PaddingValues) -> Unit)` — M3 `NavigationBar` (80dp, filled icon when selected).
  - `@Composable fun SkipSheet(active: SkipDays?, today: LocalDate, onSkipToday: () -> Unit, onChooseDays: (LocalDate, LocalDate) -> Unit, onResume: () -> Unit, onDismiss: () -> Unit)` — `ModalBottomSheet`; Choose days opens M3 `DateRangePicker` in a `DatePickerDialog`, `selectableDates` limits to today .. today+59 and range ≤ 60 days.
  - `@Composable fun StatusSheet(reminder: ReminderUi, onPending: () -> Unit, onDone: () -> Unit, onSkipToday: () -> Unit, onDismiss: () -> Unit)` — three selectable list rows with radio buttons.
  - `@Composable fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit)`, `@Composable fun SettingsRow(glyph: SymbolGlyph, title: String, summary: String?, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null)`.
  - `@Composable fun ConfirmDialog(title: String, text: String, confirm: String, destructive: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit)`.

- [ ] **Step 1: Write the failing UI tests** in `ComponentsTest` (createComposeRule, `RemindMeTheme`):
  - `weekStripMarksSkippedDays` (skip 16–17 Oct → nodes tagged `skipDot-2026-10-16` and `-17` exist, none for 15)
  - `nextUpButtonsFireCallbacks` (click "Done" and "Skip today" → counters 1)
  - `notTodayBadgeIsNotClickable`
  - `skipSheetLimitsRangeToSixtyDays` (DateRangePicker `selectableDates.isSelectableDate` false for today+60)
  - `rowAndCardsSurviveLargeTeluguText` (fontScale 1.3 via `LocalDensity`, title "విటమిన్ డి టాబ్లెట్ రాత్రి భోజనం తర్వాత", `Strings` returning Telugu; assert badge and both NextUp buttons are fully displayed: `assertIsDisplayed()` and bounds inside the row/card)
- [ ] **Step 2:** Start the emulator (`~/Library/Android/sdk/emulator/emulator -avd pixel_api36 -no-snapshot-save &`) and run `./gradlew :designsystem:connectedDebugAndroidTest` — Expected: FAIL (components missing).
- [ ] **Step 3:** Implement the components with stock M3 composables only (`ListItem`, `Button`, `FilledTonalButton`, `OutlinedButton`, `TextButton`, `FilterChip`, `AssistChip`, `NavigationBar`, `ExtendedFloatingActionButton`, `ModalBottomSheet`, `DateRangePicker`, `AlertDialog`, `RadioButton`, `Switch`). Buttons with icons use `ButtonDefaults.ButtonWithIconContentPadding` and `Modifier.size(ButtonDefaults.IconSize)` + `Spacer(ButtonDefaults.IconSpacing)`.
- [ ] **Step 4:** Run `./gradlew :designsystem:connectedDebugAndroidTest` — Expected: PASS.
- [ ] **Step 5: Commit** `feat: add shared Material 3 components`

### Task 4: Reminder editor and questions editor

**Files:**
- Create: `designsystem/.../model/ReminderDraft.kt`, `screens/ReminderEditor.kt`, `screens/QuestionsEditor.kt`
- Test: `designsystem/src/test/.../model/ReminderDraftTest.kt`, `designsystem/src/androidTest/.../screens/EditorTest.kt`

**Interfaces:**
- Produces:
  - `data class AnswerDraft(val label: String, val response: String)`; `data class QuestionDraft(val prompt: String, val answers: List<AnswerDraft>)`
  - `data class ReminderDraft(val id: String, val title: String, val category: Category, val hour: Int, val minute: Int, val days: Int, val mode: Mode, val preMinutes: Int, val questions: List<QuestionDraft>, val isNew: Boolean)`
  - `enum class DraftError { TITLE_EMPTY, TITLE_TOO_LONG, NO_DAYS, NO_QUESTIONS, QUESTION_EMPTY, ANSWER_EMPTY }` and `fun ReminderDraft.validate(): Set<DraftError>` — same limits as the current editors (read `showScheduleDialog` in both `MainActivity.java` files for max lengths and the "custom needs a question" rule; copy them as constants).
  - `@Composable fun ReminderEditor(draft: ReminderDraft, onChange: (ReminderDraft) -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?, onOpenQuestions: () -> Unit, onDismiss: () -> Unit)` — full-height `ModalBottomSheet` per spec §5 (close icon, title, Save; overflow Delete → `ConfirmDialog`; `OutlinedTextField`; category `FilterChip`s with leading Symbol; time chip with AM/PM opening `TimePicker` in a dialog; 40dp day toggles + "Every day"; `SingleChoiceSegmentedButtonRow` Call/Reminder; list rows Meal reminder (off/15/30/45/60 menu) and Questions & answers). Errors as `supportingText`.
  - `@Composable fun QuestionsEditor(questions: List<QuestionDraft>, onChange: (List<QuestionDraft>) -> Unit, onBack: () -> Unit)` — full-screen with `TopAppBar`, list of questions with answers as chips, built-in "Remind me later" shown as a locked `AssistChip` with `LOCK` icon, add/edit in a `ModalBottomSheet`, reorder with up/down icon buttons.

- [ ] **Step 1: Write failing tests** `ReminderDraftTest`: `emptyTitleIsInvalid`, `noDaysIsInvalid`, `customWithoutQuestionIsInvalid`, `validMedicineDraftHasNoErrors`, `titleLimitMatchesCurrentEditor`. `EditorTest`: `saveDisabledWhileTitleEmpty`, `pickingEveryDaySetsAllSevenBits` (days == 0b1111111), `switchingToReminderModeUpdatesDraft`.
- [ ] **Step 2:** Run `./gradlew :designsystem:testDebugUnitTest :designsystem:connectedDebugAndroidTest` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Re-run the same command — Expected: PASS.
- [ ] **Step 5: Commit** `feat: add shared reminder and questions editors`

### Task 5: Sign-in screen

**Files:**
- Create: `designsystem/.../screens/SignInScreen.kt`
- Test: `designsystem/src/androidTest/.../screens/SignInTest.kt`

**Interfaces:**
- Produces: `data class SignInState(val phone: String = "", val otpSent: Boolean = false, val code: String = "", val busy: Boolean = false, val error: String? = null, val resendInSeconds: Int = 0, val truecallerAvailable: Boolean = false)`; `@Composable fun SignInScreen(title: String, state: SignInState, onPhone: (String) -> Unit, onSendOtp: () -> Unit, onCode: (String) -> Unit, onVerify: () -> Unit, onResend: () -> Unit, onTruecaller: () -> Unit)` — app icon, title, `OutlinedTextField` with "+91" prefix and phone keyboard, filled **Send OTP**; after sending a 6-box code field, filled **Verify**, `TextButton` **Resend OTP** with countdown; tonal Truecaller button when available; `error` as supporting text; `LinearProgressIndicator` while busy.

- [ ] **Step 1: Write failing tests**: `sendOtpDisabledUntilTenDigits`, `codeFieldAppearsAfterOtpSent`, `resendShowsCountdown`, `errorShownAsSupportingText`.
- [ ] **Step 2:** Run `./gradlew :designsystem:connectedDebugAndroidTest` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Re-run — Expected: PASS.
- [ ] **Step 5: Commit** `feat: add shared sign-in screen`

### Task 6: Member app shell, Today and Reminders tabs

**Files:**
- Create: `simplecall/src/main/java/com/gurthuchey/remindercall/MemberActivity.kt`, `MemberViewModel.kt`, `ScheduleMapper.kt`, `designsystem/.../screens/TodayScreen.kt`, `designsystem/.../screens/RemindersScreen.kt`
- Modify: `simplecall/src/main/AndroidManifest.xml` (launcher aliases point to `.MemberActivity`; keep alias names), `RemoteSync.java` (`saveOwnSchedules` puts `item.enabled` instead of the hard-coded `true`, so the Reminders-tab switch works; the member list still hides disabled reminders on Today but shows them, switched off, on the Reminders tab)
- Delete (end of Task 7): `MainActivity.java`, `Ui.java`, `AgendaTimeline.java`, `CompactSheetLayout.java`, `TimelineProgress.java`, their now-dead tests (`TimelineProgressTest`)
- Test: `simplecall/src/test/java/com/gurthuchey/remindercall/ScheduleMapperTest.kt`

**Interfaces:**
- Consumes: Tasks 2–5.
- Produces:
  - `object ScheduleMapper { fun toUi(schedule: RemoteStore.Schedule, display: DailyCallStatus.Display?): ReminderUi; fun toDraft(schedule: RemoteStore.Schedule?, language: String): ReminderDraft; fun applyDraft(config: RemoteStore.Config, draft: ReminderDraft): RemoteStore.Config; fun status(kind: String): ReminderStatus; fun skip(range: SkipRange?): SkipDays? }` — `days` bit order: RemoteStore uses Monday = bit 0 (see `ReminderScheduler.nextTrigger`), same as `ReminderUi`.
  - `class MemberViewModel(app: Application) : AndroidViewModel(app)` exposing `val ui: StateFlow<MemberUi>` where `data class MemberUi(val signedIn: Boolean, val signIn: SignInState, val language: String, val reminders: List<ReminderUi>, val skip: SkipDays?, val selectedDate: LocalDate, val day: DayView, val offline: Boolean, val permissionsMissing: Boolean, val message: String?)` and events `selectDate(LocalDate)`, `markDone(id)`, `markPending(id)`, `skipToday(id)`, `skipDays(from, to)`, `resumeSkip()`, `save(draft)`, `delete(id)`, `setEnabled(id, Boolean)`, `setLanguage(code)`, `signOut()`, sign-in events. Each event calls exactly the Java the old `MainActivity` called for the same action (e.g. markDone = `cancelRetry` ×3 + `DailyCallStatus.markCompleted` + `CallService.ACTION_COMPLETE_TODAY` when that call is active; skipToday = `markSkippedToday` + `ReminderScheduler.skipRemainingToday` + `ACTION_SKIP_TODAY`; skipDays/resume = `RemoteSync.updateSkip`; save/delete = optimistic config + `RemoteSync.saveOwnSchedules`).
  - The view model listens to `RemoteStore` and `DailyCallStatus` preference changes and to a minute ticker that also fires at local midnight, recomputing `day`.
  - `@Composable fun TodayScreen(state: DayViewState, ...)` and `@Composable fun RemindersScreen(reminders: List<ReminderUi>, onOpen: (String) -> Unit, onToggle: (String, Boolean) -> Unit)` in `:designsystem` so Admin reuses them; `TodayScreen` takes optional `header: @Composable () -> Unit` (Admin's chips and status line go there) and `showNextUp`.

- [ ] **Step 1: Write failing tests** `ScheduleMapperTest`: `mapsEveryDailyStatusKind` (each `DailyCallStatus` constant → enum), `mondayIsBitZero`, `textModeMapsToText`, `draftRoundTripKeepsQuestions` (toDraft → applyDraft → toDraft equal), `skipRangeMapsToSkipDays`.
- [ ] **Step 2:** Run `./gradlew :simplecall:testReleaseUnitTest` — Expected: FAIL.
- [ ] **Step 3:** Implement mapper, view model, `MemberActivity` (`ComponentActivity`, `enableEdgeToEdge()`, `RemindMeTheme`, `CompositionLocalProvider(LocalStrings provides Strings { AppLanguage.ui(this, it) })`, `NavHost` with routes `today`, `reminders`, `settings`, `questions/{id}`; tab changes use `fadeIn/fadeOut` with `Motion.effects()`, pushes use `slideInHorizontally` shared-axis), Today (week strip, card, sections, FAB "Reminder", editor sheet, status sheet, skip sheet, offline chip, permissions card), Reminders tab.
- [ ] **Step 4:** Run `./gradlew :simplecall:testReleaseUnitTest :simplecall:assembleRelease` — Expected: all tests PASS, APK builds.
- [ ] **Step 5: Commit** `feat: member app Today and Reminders in Compose`

### Task 7: Member Settings, sign-in wiring and owner review 1

**Files:**
- Create: `simplecall/.../SettingsScreen.kt`
- Modify: `MemberActivity.kt`, `MemberViewModel.kt`, `simplecall/build.gradle` (versionCode 90)
- Delete: files listed under Task 6 "Delete"

**Interfaces:**
- Consumes: Task 6 `MemberViewModel`; existing `PhoneLogin` (OTP + Truecaller) and the permission checks from old `MainActivity.addPermissionButton` (exact alarms, notifications, full-screen intent, battery optimisation) — move those checks into `PermissionStatus.kt` with `fun missing(context): List<Permission>` and the same intents.
- Produces: Settings per spec §5 (Reminders: Skip reminders, permission rows; Language: radio dialog of `AppLanguage.NAMES`; Account: phone, Log out → `ConfirmDialog`; About: version).

- [ ] **Step 1:** Implement Settings, sign-in screen wiring to `PhoneLogin`, permission rows; delete the dead View files; fix any compile errors from the deletion.
- [ ] **Step 2:** Run `./gradlew :simplecall:testReleaseUnitTest :simplecall:assembleRelease` — Expected: PASS; `grep -rn "Ui\.\|AgendaTimeline" simplecall/src/main` returns only `CallActivity.java` uses (replaced in Task 8).
- [ ] **Step 3:** Install on the owner's phone: `adb install -r simplecall/build/outputs/apk/release/Gurthu-Chey-2.0.apk`; open each screen; screenshot Today, Reminders, editor, questions, skip sheet, Settings in light and dark (`adb shell cmd uimode night yes|no`) and at 130% font (`adb shell settings put system font_scale 1.3`, restore to the previous value afterwards). Check the phone stays signed in and reminders, statuses and alarms (`dumpsys alarm`) match before/after.
- [ ] **Step 4: Commit** `feat: member settings and sign-in in Compose; remove old views`
- [ ] **Step 5:** Show the owner the screenshots and wait for approval before Phase 2.

## Phase 2 — Call screen

### Task 8: Compose call screen

**Files:**
- Create: `designsystem/.../call/CallUi.kt`, `simplecall/.../CallSession.kt`
- Modify: `simplecall/.../CallActivity.java` → replace with `CallActivity.kt` (same class name and manifest entry)
- Test: `simplecall/src/test/java/com/gurthuchey/remindercall/CallSessionTest.kt`; existing `simplecall/src/androidTest/.../ReminderModeRegression.java`, `SampleLunchCallInstrumentation.java`

**Interfaces:**
- Produces:
  - `data class CallUiState(val active: Boolean, val answered: Boolean, val textReminder: Boolean, val title: String, val caller: String, val question: String, val answers: List<String>, val customCall: Boolean, val showRemindLater: Boolean, val remindLaterLabel: String, val showDelayOptions: Boolean, val finalizing: Boolean, val showTextContinue: Boolean, val speakerOn: Boolean, val step: Int, val branch: Int, val language: String, val answeredAt: Long)` in `:designsystem`.
  - `object CallSession { fun read(prefs: SharedPreferences, fallbackLanguage: String): CallUiState }` in `:simplecall`, reading exactly the keys `CallActivity.java` reads today: `active answered customCall finalizing showDelayOptions showRemindLater showTextContinue speakerOn textReminder branch step answerA answerB answerC answerD question remindLaterLabel reminderTitle language` (blank answers dropped). Add `answeredAt` only if `CallService` already stores it; otherwise the timer starts when the screen first shows answered.
  - `@Composable fun IncomingCall(state: CallUiState, onAnswer: () -> Unit, onDecline: () -> Unit)`, `@Composable fun OngoingCall(state: CallUiState, onOption: (Int) -> Unit, onRemindLater: () -> Unit, onDelay: (Int) -> Unit, onDelayAt: (Long) -> Unit, onSkipToday: () -> Unit, onContinue: () -> Unit, onSpeaker: () -> Unit, onHangUp: () -> Unit)` per spec §5 (gradient, 120dp avatar with filled ALARM and pulse, 72dp Decline/Answer with labels + swipe-up to answer, 56dp answer buttons: first filled, rest tonal, Remind me later outlined with SNOOZE; remind-later `ModalBottomSheet` with `CallService.DELAY_MINUTES` choices + Choose time `TimePicker` + Skip today; 96×56dp error pill Hang up; text-reminder mode without timer/hang-up and **Okay**).
  - `CallActivity.kt` keeps every non-drawing behaviour of the Java version: `handleLaunchAction`, `configureCallWindow` flags, the `ACTION_STATE_CHANGED` receiver registered in onStart/onStop, `ACTION_CALL_SCREEN_VISIBLE/HIDDEN` in onResume/onPause, back → `moveTaskToBack(true)`, volume keys → `ACTION_SILENCE`, one-shot submit guard, and the exact intents (`ACTION_OPTION` with `EXTRA_OPTION` + `EXTRA_OPTION_STEP`, `ACTION_DELAY`, `ACTION_DELAY_AT` with `EXTRA_DELAY_AT`, `ACTION_CONTINUE`, `ACTION_SKIP_TODAY`, `ACTION_TOGGLE_SPEAKER`, `ACTION_ANSWER`, `ACTION_REJECT`). Hang up sends whatever the current Java hang-up control sends (read `connectedHeader`/`callControl` and reuse it).

- [ ] **Step 1: Write failing tests** `CallSessionTest` (fake `SharedPreferences` map): `incomingWhenNotAnswered`, `blankAnswersAreDropped`, `delayOptionsHiddenWhileFinalizing`, `textReminderKeepsOkay`, `missingLanguageUsesFallback`.
- [ ] **Step 2:** Run `./gradlew :simplecall:testReleaseUnitTest` — Expected: FAIL.
- [ ] **Step 3:** Implement `CallSession`, `CallUi.kt`, `CallActivity.kt`; delete `CallActivity.java` and anything only it used.
- [ ] **Step 4:** Run `./gradlew :simplecall:testReleaseUnitTest` — Expected: PASS. Then on the `pixel_api36` emulator: `./gradlew :simplecall:connectedDebugAndroidTest -PtestBuildType=debug` — Expected: `ReminderModeRegression` and `SampleLunchCallInstrumentation` PASS (update their view lookups to Compose test tags if they fail only on lookup, never on behaviour).
- [ ] **Step 5:** Bump `versionCode` to 91, install on the owner's phone, trigger the existing test call, and check: lock-screen ring, answer by button and by swipe, each answer, remind later 5 min (alarm appears in `dumpsys alarm`), hang up, decline → retry in 5 min, text-reminder mode, ongoing notification reopens the screen. Restore anything the test changed.
- [ ] **Step 6: Commit** `feat: Pixel-style Compose call screen`
- [ ] **Step 7:** Show the owner screenshots of incoming, question and remind-later; wait for approval before Phase 3.

## Phase 3 — Admin

### Task 9: Admin shell, Today, Reminders, editor and skip

**Files:**
- Create: `admin/src/main/java/com/gurthuchey/admin/AdminActivity.kt`, `AdminViewModel.kt`, `AdminMapper.kt`
- Modify: `admin/src/main/AndroidManifest.xml` (aliases → `.AdminActivity`)
- Test: `admin/src/test/java/com/gurthuchey/admin/AdminMapperTest.kt`

**Interfaces:**
- Consumes: Tasks 2–6 shared screens; `AdminFirebase` (`start`, `listenToMembers`, `listenToDevices`, `publishAll`, `publishMember`, `updateSkip`, `deleteMember`, `createPairingCode`), `AdminStore`, `AdminAuth`, `PhoneLogin`, `Models`.
- Produces:
  - `object AdminMapper { fun toUi(schedule: Models.Schedule, status: ReminderStatus = ReminderStatus.UNKNOWN): ReminderUi; fun toDraft(schedule: Models.Schedule?, language: String): ReminderDraft; fun applyDraft(member: Models.Member, draft: ReminderDraft): Models.Member; fun skip(range: SkipRange?): SkipDays? }` (Admin's `days` bit order — confirm in `Models.Schedule` and convert if it differs).
  - `class AdminViewModel(app: Application)` with `StateFlow<AdminUi>`: `data class AdminUi(val signedIn: Boolean, val signIn: SignInState, val members: List<MemberSummary>, val selectedMemberId: String?, val selectedDate: LocalDate, val day: DayView, val statusLine: StatusLine, val reminders: List<ReminderUi>, val syncStatus: String, val message: String?)`, `data class MemberSummary(val id: String, val name: String, val phone: String, val connected: Boolean, val skip: SkipDays?)`, `data class StatusLine(val done: Int?, val total: Int?, val connected: Boolean)` (done/total null until Task 11 → shows "No status yet"). Events mirror the member app plus `selectMember(id)`. Save/delete go through the same path old Admin `MainActivity` used (`store.save(members)` + `firebase.publishAll(members)`); skip uses `firebase.updateSkip`.
  - Today uses `TodayScreen(showNextUp = false, header = { MemberChips(...); StatusLineCard(...) })`; tabs Today · Reminders · Family · Settings.

- [ ] **Step 1: Write failing tests** `AdminMapperTest`: `mapsScheduleFields`, `draftRoundTripKeepsQuestionsAndEnabled`, `unknownCategoryIsCustom`, `skipMaps`.
- [ ] **Step 2:** Run `./gradlew :admin:testReleaseUnitTest` — Expected: FAIL.
- [ ] **Step 3:** Implement mapper, view model, `AdminActivity` with Today, Reminders, editor, questions, skip.
- [ ] **Step 4:** Run `./gradlew :admin:testReleaseUnitTest :admin:assembleRelease` — Expected: PASS.
- [ ] **Step 5: Commit** `feat: Admin Today, Reminders and editor in Compose`

### Task 10: Admin Family, Settings, sign-in and owner review 3

**Files:**
- Create: `admin/.../FamilyScreens.kt` (Family list, member detail, add-member sheet, phone dialog, pairing-code dialog), `admin/.../AdminSettingsScreen.kt`
- Modify: `AdminActivity.kt`, `AdminViewModel.kt`, `admin/build.gradle` (versionCode 63)
- Delete: `admin/.../MainActivity.java`, `admin/.../Ui.java` and dead helpers

**Interfaces:**
- Consumes: Task 9; the add/phone/remove/pairing logic of old Admin `MainActivity` (`showMemberDialog`, `showPhoneDialog`, `confirmMemberDelete` + `flushPendingMemberDeletes`, `showPairingCode`, `cleanupSupersededPhones`) moved into `AdminViewModel` unchanged in behaviour.

- [ ] **Step 1:** Implement Family per spec §5 (members as `ListItem` with avatar initial and a connected/not-signed-in `AssistChip`; detail screen with name, phone dialog, language, connected phone, Remove → `ConfirmDialog`; extended FAB "Add member" → sheet; pairing code dialog with display-large digits), Settings (Language, Account/Log out, About), sign-in via shared screen; delete dead files.
- [ ] **Step 2:** Run `./gradlew :admin:testReleaseUnitTest :admin:assembleRelease` — Expected: PASS.
- [ ] **Step 3:** Install on the owner's phone; check every Admin screen in light/dark/130%; confirm members, reminders and phones match before/after and that publishing still reaches the member app (edit a reminder's name, see it on the member app, change it back).
- [ ] **Step 4: Commit** `feat: Admin family, settings and sign-in in Compose; remove old views`
- [ ] **Step 5:** Show the owner screenshots; wait for approval before Phase 4.

## Phase 4 — Shared day statuses

### Task 11: Phone publishes day statuses; Admin and past days read them

**Files:**
- Create: `simplecall/.../DaySummary.java`, `simplecall/.../DayStatusJob.java`
- Modify: `simplecall/.../DailyCallStatus.java` (schedule the job after each `write`/`clearSchedule`), `simplecall/src/main/AndroidManifest.xml` (job service, like `ScheduleSyncJob`), `MemberViewModel.kt` (read past days), `admin/.../AdminFirebase.java` (`listenToDay`), `AdminViewModel.kt`, `firestore.rules`
- Test: `simplecall/src/test/.../DaySummaryTest.java`, `admin/src/test/.../AdminMapperTest.kt` (add cases)

**Interfaces:**
- Produces:
  - `final class DaySummary { static Map<String, String> build(List<RemoteStore.Schedule> schedules, DailyCallStatus statuses, long now); static String docId(long time) /* yyyy-MM-dd local */ }` — value per schedule id ∈ {`completed`, `skipped`, `missed`, `pending`} from the day's display kind (UPCOMING/CALLING/RETRY/PENDING → `pending`; NOT_TODAY omitted).
  - `DayStatusJob` (JobService, id 9201, 30 s minimum latency, network required, replaces pending job) writes `families/{f}/members/{m}/days/{docId}` = `{ statuses, updatedAt: serverTimestamp }` with `SetOptions.merge()`, and deletes day docs older than 30 days it finds for this member.
  - `void AdminFirebase.listenToDay(String memberId, String isoDay, DayListener listener)` with `interface DayListener { void onDay(Map<String, String> statuses /* null if no doc */); }`, replacing the previous listener on each call.
  - Member app reads its own past-day doc for a selected past date the same way (one-shot `get()`).
- Rules (add under `members/{memberId}`):
  ```
  match /days/{day} {
    allow read: if isOwner(familyId) || pairedFor(familyId, memberId);
    allow create, update: if pairedFor(familyId, memberId)
      && day.matches('^[0-9]{4}-[0-9]{2}-[0-9]{2}$')
      && request.resource.data.keys().hasOnly(["statuses", "updatedAt"])
      && request.resource.data.statuses is map
      && request.resource.data.statuses.size() <= 50
      && request.resource.data.updatedAt == request.time;
    allow delete: if isOwner(familyId) || pairedFor(familyId, memberId);
  }
  ```

- [ ] **Step 1: Write failing tests** `DaySummaryTest`: `mapsKindsToStatuses`, `notTodayIsOmitted`, `docIdIsLocalDate`. `AdminMapperTest`: `statusLineCountsFromDayDoc` (statuses 4 completed, 2 skipped, 7 pending → done 4, total 11), `missingDayDocShowsNoStatusYet` (null → StatusLine(done = null, total = null)).
- [ ] **Step 2:** Run `./gradlew :simplecall:testReleaseUnitTest :admin:testReleaseUnitTest` — Expected: FAIL.
- [ ] **Step 3:** Implement; `firebase deploy --only firestore:rules --project gurthu-chey --dry-run` must compile.
- [ ] **Step 4:** Run the tests — Expected: PASS. Deploy rules: `firebase deploy --only firestore:rules --project gurthu-chey`.
- [ ] **Step 5:** Bump versionCodes (member 92, Admin 64), install both on the owner's phone; mark one reminder Done on the member app and confirm Admin's status line and badge update within a minute; mark it back.
- [ ] **Step 6: Commit** `feat: share daily reminder statuses with Admin`

### Task 12: Release wrap-up

**Files:**
- Modify: `README.md` (UI section rewritten for the new design), `design/remind-me-redesign.md` (replace with the M3 design notes: theme, components, screens, motion), `.gitignore` (keep `.superpowers/`)

- [ ] **Step 1:** Run the full suite: `./gradlew :designsystem:testDebugUnitTest :simplecall:testReleaseUnitTest :admin:testReleaseUnitTest :simplecall:assembleRelease :admin:assembleRelease` and, on `pixel_api36` and `api28`, `./gradlew :designsystem:connectedDebugAndroidTest` — Expected: all PASS. On `api28` also screenshot Today to confirm the brand fallback palette.
- [ ] **Step 2:** Record APK sizes before (v89/v62 builds in git history) and after; note them in the README.
- [ ] **Step 3:** Update the docs listed above.
- [ ] **Step 4: Commit** `docs: describe the Material 3 design` and push `main`.
- [ ] **Step 5:** Tell the owner the APK paths to install on the family phones (member app on each member's phone, Admin where used).
