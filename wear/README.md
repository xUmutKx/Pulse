# Pulse Watch

The Wear OS app of Pulse: a project of its own (`wear/`), built as its own APK (`PulseWatch.apk` in each release), signed with Pulse's key. It works on its own (no phone app needed), Wear OS 3 and newer.

Made for a round screen: black background, the time curved along the top edge, lists that bend to the circle (ScalingLazyColumn), rings round the edge for progress.
The crown (or the rotating bezel) scrolls every list and turns every value (timer minutes, beats a minute, the counter, the number of dice); a swipe to the right goes back.

Home: three pages. The middle one (where it opens) is the time, the date and the battery as a ring; swipe right for quick buttons, left for every app.

Apps: Stopwatch (laps), Timer (crown sets the minutes; swipe left for presets), World clock (swipe left to pick cities), Pomodoro, Heart rate, Steps (today, goal ring),
Breathe, Compass, Battery & device, Calculator (round keys), Notes (the watch's own voice / keyboard input), Counter (crown), Dice & coin, Metronome (beats as ticks on the wrist),
Flashlight (the whole screen, a tap changes the colour), Water (glasses today, crown), Tip (bill, tip and split), Reaction (tap on green, best time kept), Convert (°C °F, km mi, kg lb, cm in), Settings (accent, crown ticks).

Timers and the metronome run while their page is open (the screen stays on).
