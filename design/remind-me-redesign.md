# Remind Me: a day planner, not a list of alarm cards

## Direction
A quiet, personal agenda with a recognizable iris accent. Time is the organizing element: a narrow time rail, open reminder rows, and a single next-reminder panel. Status remains directly editable without rebuilding the page.

## Tokens
- Cloud `#F6F5FB`: page canvas.
- Paper `#FFFFFF`: forms and surfaces.
- Ink `#27243D`: primary reading text.
- Iris `#6652CC`: primary actions and next reminder.
- Lilac `#EAE5FC`: selected controls.
- Sage `#277566`: completed state; rose `#A74760`: skipped state.

Android system sans-serif supplies multilingual body text; sans-serif-medium defines titles. Condensed numerals give times a clear, compact column. Main title 26sp, reminder titles 15sp, body 14sp, supporting labels 10–12sp. Status controls keep a 40dp tap height; Add stays 46dp.

## Layout
Left-aligned, time-led home screen:

    Remind me                         Profile
    Monday, 28 September
    Today   3 of 12 completed      day progress
    [ Next up                                ]
    [ 5:30 pm           Evening walk          ]
    Your reminders   [All reminders | Today]
    Morning
    8:00 | Medicine name     [Today's status]
      am | Medicine · Call
    ...
    [                + Add reminder          ]

The editor uses a large title input, compact category choices, a distinct time control, delivery mode, weekday toggles, and a sticky Save action. Status choices become three descriptive rows. Calls and text reminders share the iris identity and a focused question surface.

## Review against the brief
A palette change alone would leave the old app intact. This design replaces icon-first elevated cards with a time rail, adds a useful next-reminder focus, relocates the primary add action, and rebuilds the editing hierarchy. Avoid decorative gradients, emoji-led chrome, fake calendar controls, and a row of statistic cards. Preserve offline scheduling, all six languages, current call/reminder modes, status wording, and scroll stability.

## Compactness pass
The first phone preview required too much scrolling. The final home screen uses a single-line daily summary, a shorter next-reminder panel, and side-by-side reminder title/status. Rows start at 66dp instead of roughly 114dp, with extra height only for wrapping names or specific weekday schedules. The Add dock and section spacing are smaller. Status changes still update in place.

## Validation
- Release unit tests and Android lint pass (zero lint errors; existing warnings remain).
- Disposable-emulator regression passes: call and text flows, completion, snooze, skip, timeout/retry, all reminder tags, status choices, and scroll preservation.
- Design fixture verifies All reminders / Today only filtering and captures home, status, and editor screens.
- English and Telugu checked visually, including 130% font size; weekday labels fit without wrapping and the editor remains scrollable.
- Release build 86 installed and inspected on Samsung S22 Ultra; six full reminder rows plus part of a seventh fit on the initial screen, with member reminders and existing statuses retained.

Synthetic-data previews: [home](previews/home.png), [status](previews/status.png), [editor](previews/editor.png).
