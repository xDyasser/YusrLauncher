# Minimalist

An Android launcher that cuts phone usage by force rather than by suggestion.

The home screen is the time, where you are in the day's prayers, an ayah, and a handful of names.
Everything else has to be earned: type the app's full name, say why you want it, wait out a
countdown that grows every time you have already been there today, and then use it inside a
session with an end. Daily budgets are hard — when they are spent, the app does not open again
until tomorrow.

Behind the home screen is a hub for the things that are not apps: the qibla, the mushaf, the
tasbīḥ, the adhkār, the fasting calendar. They are the reason to pick the phone up that the rest
of the app is trying to leave room for.

Requires Android 13 or newer. Sideload only; it is not on Play and would not be allowed there,
because it deliberately makes itself hard to remove.

The network is used for exactly three things, all optional and all one-shot: refreshing the
prayer timetable, downloading the Qur'an once, and fetching a sūrah's recitation when you ask for
it. Every feature works with it permanently unavailable — the prayer times are computed on the phone, and the gate falls back to the ayat
bundled in the APK. Location is read only when you ask it to fill in your coordinates, which you
can equally type. Nothing is ever uploaded; there is no account and no analytics.

## What it does

- **The phone stops for salah.** Prayer times are computed on the device — Umm al-Qura, MWL,
  Egyptian, Karachi, ISNA, Tehran and Jafari, standard or Hanafi asr, with the Shia rules and an
  option to combine dhuhr with asr and maghrib with isha into two windows a day — combined, they
  are shown as one entry at one time throughout, because that is how they are prayed. Sharʿī
  midnight and the last third of the night are printed alongside them: the first is where ʿishāʾ
  runs out, the second is where qiyām al-layl begins, and neither is anywhere on a wall clock.
  During a prayer
  window nothing opens but the dialer and whatever you have marked as opening during salah:
  favourites included, which no other limit in this app does. An app already in the foreground
  when the adhan arrives is sent home.
- **An ayah at the gate.** Reaching a gated app shows an ayah, drawn at random from the whole
  Qur'an, in Arabic, English or both. The typing test is a dhikr rather than the app's own name —
  either script, with diacritics optional. The text is the Uthmani Ḥafṣ mushaf and the translation
  is Ali Quli Qarai's, a Shīʿī one; both come from the same corpus, verse for verse, and the
  bundled handful that stands in before the download is set from the same two.
- **Text-only home screen.** The clock centred, the Hijri date under it, then the five prayers
  across the width with the next one in gold and a line saying how long the current prayer's
  preferred time has left. Under that the ayah the mushaf is bookmarked at — tap it when you have
  read it and both it and the reader move on one ayah — then the favourites —
  names, nothing else. No icons, no wallpaper, no swipe into a grid. The list scrolls if you name
  more favourites than fit, and the bar at the foot — **Hub**, **All apps**, **Dhikr** — stays put
  whatever the list does. A faint mark beside the clock gives every name a handle to drag it into
  the order you want; held against either edge the list walks past under your finger, the rest of
  the row still scrolls, and *done* puts it back. The drawer is that word or a long press, and
  shows nothing until you have typed two letters.
- **A hub for what is not an app.** The qibla as a dial that turns with the phone, computed from
  the coordinates already set for the prayer times and needing no network. Six things behind it:
  the day's timetable with its preferred windows, a Qur'an reader — read it straight through, the
  chevrons running on from the end of one sūrah into the next and a sideways drag turning a whole
  one, with the basmala set above each sūrah as a heading rather than swallowed into its first
  ayah — al-Tawba, which opens without one, gets none, and al-Fātiḥa keeps it as the ayah it is.
  Nothing is cut to make that true: the text is taken from an edition that numbers the book the
  way the mushaf does, so al-Baqara 2:1 arrives as *alif lām mīm* — a tasbīḥ, the adhkār, the duʿāʾ,
  and a fasting calendar that knows the white days, Mondays and Thursdays, ʿĀshūrāʾ and ʿArafah
  from the Hijri date and never suggests a fast on an Eid.
- **Mafātīḥ al-Jinān, whole and in Arabic.** All three bābs, both appendices and 194 texts —
  Kumayl, Nudba, ʿAhd, Ṣabāḥ, Iftitāḥ, Jawshan Kabīr, Abū Ḥamza, the Munājāt, Ziyārat ʿĀshūrāʾ —
  bundled rather than fetched, and read as a book: the parts, then what is in a part, then the
  text. It is taken from an Arabic edition, so the headings are the ones the book prints and
  al-Qummī's guidance stands beside the supplications instead of being dropped for being in the
  wrong language — when to read a text, who narrated it, how many times. The editor's footnotes
  are left out; nothing else is. The import is a script in `tools/`, so all of that is something
  you can re-run rather than a claim. Ḥiṣn al-Muslim is the other book, a hand-made selection with
  English beside each entry, and either is one tap from the other whatever your madhab is.
- **Recitation that belongs to the phone.** Pick a reciter — Sunnī and Shīʿī both, all Ḥafṣ —
  and download a sūrah once. After that it plays with the network permanently gone, which is the
  same bargain everything else here makes. Nothing is streamed.
- **One question, asked once.** Which school you follow. It sets the ʿasr rule, the calculation
  method, whether the prayers are combined, and which book of supplications the hub reads from —
  Mafātīḥ al-Jinān or Ḥiṣn al-Muslim — and every one of those stays editable afterwards. Nothing
  is pre-selected, and an install from before the question existed is read rather than
  interrogated.
- **English or Arabic, and the screen turns round with it.** The app follows the phone unless
  you tell it otherwise, under **Settings → Appearance**. Choosing Arabic sets the whole interface
  in Arabic and mirrors the layout, the way the language is read; the Hijri date, the sūra names
  and the titles of the two books of supplications come over into Arabic script with it. The
  choice is handed to Android's own per-app language, so it survives a restart and appears in the
  system's language settings beside every other app.
- **One typeface for the interface and one for revelation.** IBM Plex Sans Arabic throughout,
  Amiri for anything Qur'anic, both bundled so they work offline. Old gold is the only accent in
  the app and is spent on two things: the prayer that is next, and the thing you are in the middle
  of. It follows the system's light and dark setting, or can be pinned to either.
- **Decide once, up front.** Until you press *lock these rules in*, every change applies
  immediately — you sort out the whole app list in one sitting rather than thirty minutes at a
  time. Preinstalled apps start allowed; what you installed yourself starts gated.
- **One widget, if you want one.** Your salah app almost certainly knows things a calculated
  timetable does not — your masjid's iqama, the adhan you like. Pick its widget under
  **Settings → Home widget** and it sits under the clock in place of the next-prayer line. The
  list is every widget published on the phone; the system is asked only for permission to hold
  one. Off by default.
- **Your phone's own navigation.** The launcher does not replace it: the swipe from the edge for
  back, the swipe up for home, whatever your phone is set to. There is no bar of ours at the
  bottom. For phones where the system navigation has been hidden or cannot be reached, an
  optional strip is available under **Settings → Appearance and navigation** — it needs an
  accessibility service, since that is the only API that can press back or open recents.
- **Web-backed apps still work.** A gated browser used to stand in front of every link, sign-in
  page and web app on the phone. Browsers are now marked *opens when another app sends you to
  it*: the handoff is free, and going to the browser yourself still costs the full gate. The
  setting is per app, under **Apps and limits**.
- **The gate.** Reaching a gated app costs a typed name, a written reason, and an escalating
  countdown (15s, then 30s, then 45s… capped at five minutes). Pasting is disabled; leaving the
  screen restarts the countdown.
- **Hard budgets.** Per-app daily minutes and daily opens. Spent is spent.
- **Blackout windows.** Hours when only favourites open at all — sleep, work, evenings.
- **Notification suppression.** Notifications from gated apps are dismissed as they arrive and
  delivered as a summary twice a day. Calls, alarms, media, and anything the system pins always
  get through — cancelling a player's notification would stop playback, not just hide it.
- **A cooldown on going soft.** Once the rules are locked, tightening happens the moment you ask;
  loosening waits out a cooldown (30 minutes by default) and can be cancelled in the meantime.
- **Emergency bypass.** Three a week, each logged with a written reason.
- **Usage dashboard.** Plain numbers: minutes today, opens, and where they went.

## How the enforcement works

Three layers, strongest available first:

| Tier | How it is granted | What it gives |
| --- | --- | --- |
| Device owner | one ADB command, opt-in | the OS refuses to open blocked apps; uninstall blocked; launcher pinned |
| Device admin | a tap in the setup checklist | uninstalling requires deactivating admin first |
| Guard service | always on | watches the foreground app and sends you home when it was not earned |

The guard service is what catches the routes the launcher UI cannot: recents, notifications, and
links from other apps. It needs Usage Access to see the foreground app and "display over other
apps" to put the block screen on top of one.

See [docs/SETUP.md](docs/SETUP.md) for installing, granting the permissions, the HyperOS/Xiaomi
settings that keep it alive, and how to remove it.

## Building

CI builds a debug APK on every push: **Actions → Build APK → `minimalist-debug-apk`**. Download,
unzip, sideload.

Every build — local or CI — is signed with the key checked in at `app/minimalist.keystore`, so a
new version installs over the old one instead of asking you to uninstall first. It guards
nothing: the app is never published, and the password is in `app/build.gradle.kts` beside it.
Settings and rules are also included in Android's backup, so a genuine reinstall or a move to a
new phone can bring them along.

Locally:

```
./gradlew testDebugUnitTest   # pure JVM tests over the gate, budget, blackout, and notification logic
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

## Layout

```
domain/     pure logic, no Android imports — GateEvaluator, BudgetCalculator, BlackoutSchedule,
            NotificationPolicy, PrayerTimes, PrayerWindows, Fadila, Madhab, Qibla, Hijri,
            FastingCalendar, Tasbih, Dhikr, FavoriteOrder
data/       Room rules and history, DataStore settings, RuleMutator (the tighten/loosen asymmetry),
            the prayer timetable, the Qur'an and its recitation, the bundled supplications,
            and the network fetches
service/    GuardService (foreground enforcement), notification filter, optional nav strip
admin/      device admin and device owner policy
ui/         home (and its widget host), hub (qibla, prayer, mushaf, tasbih, adhkar, fasting),
            search, gate, block, settings, usage, setup checklist
work/       the deferred-change worker and the notification digest
```

The decision "may this app open right now?" lives in exactly one place, `GateEvaluator`, and is
reached through one repository method, so the launcher can never permit something the guard
service would immediately undo.
