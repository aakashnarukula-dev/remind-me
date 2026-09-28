# Remind Me: compact reminder timeline

## Current direction
Home contains a header and reminder list. The header keeps branding, Add reminder, and the account/language menu. The date, completion summary, next-reminder panel, list heading, filter toggle, and bottom Add dock are removed.

## Tokens
Cloud `#F6F5FB`, paper `#FFFFFF`, ink `#27243D`, iris `#6652CC`, lilac `#EAE5FC`, sage `#277566`, rose `#A74760`.

System sans-serif supports all six app languages. Reminder titles use 15sp; times use condensed 14sp numerals with AM/PM on the same line, fitting down when text size increases. Rows start at 66dp and grow only for long names. Weekday schedules appear in the editor, not below reminder rows. Status tags retain 40dp touch height; header actions are 44dp.

## Timeline behavior
A single rail is drawn behind all rows and period headings. Period labels sit in the left time gutter; reminder content stays to the right. Row-center dots define reminder times. Elapsed time fills the rail in iris; future time remains gray. Between reminder times, the fill interpolates between their row centers, including any intervening section headings. The rail updates on minute boundaries, without rebuilding or scrolling the list.

Reminders not scheduled today use muted text and gray status tags. Their names still open the usual reminder editor. Their disabled Not today tags consume taps without opening either the editor or the daily status sheet. Pending, Completed, and Skipped tags retain direct status editing; the skip action remains Skip for today.

## Other screens
The editor has 14sp title/time fields, 12sp field labels, 13sp buttons, and 40dp controls with tight section spacing. Sheets wrap their contents and cap at 72% of screen height, with scrollable forms and a fixed Save action. Short conversation sheets also wrap their contents. Daily status uses compact descriptive action rows. Calls and text reminders retain the iris visual identity and offline behavior.

## Validation
- Release unit tests and Android lint pass; timeline tests cover boundaries, exact reminder times, interpolation across sections, duplicate times, and empty lists.
- Disposable-emulator UI checks cover removed home controls, disabled Not today taps, and editing off-day reminders.
- Reminder-mode regression covers call/text flows, snooze, skip, timeout/retry, status editing, and scroll preservation.
- English and Telugu preview fixtures are emulator-only; 130% text size remains readable. Member data is never replaced during phone testing.
- Build 88 installed on Samsung S22 Ultra. Not today taps open no sheet; the same reminder name opens the editor. The standard editor now occupies about half the phone screen instead of nearly all of it.

Synthetic-data previews: [home](previews/home.png), [status](previews/status.png), [editor](previews/editor.png).
