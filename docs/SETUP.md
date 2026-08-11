# Setup

## 1. Install

Download the APK from the latest CI run — **Actions → Build APK → artifacts →
`yusr-debug-apk`** — unzip it, and copy `app-debug.apk` to the phone. Open it and allow
installing from that source when prompted.

Every build is signed with the same key (`app/yusr.keystore`, checked in), so a newer APK
installs straight over the one already on the phone and your rules, timetable and settings stay
where they are. If you are coming from a build made before that key existed, that one upgrade
still needs an uninstall first — after it, upgrades are ordinary. The app is also included in
Android's backup, so a reinstall or a new phone can restore the rules with it.

Or build and install over USB:

```
./gradlew installDebug
```

## 2. Grant the permissions

Open the app, long-press the clock to reach Settings, then **Setup and permissions**. Each item
links straight to the right system screen.

The first thing the checklist asks is **which school you follow** — Ḥanafī, Shāfiʿī, Mālikī,
Ḥanbalī, Jaʿfarī or Zaydī. Nothing is chosen for you. Answering it sets the ʿasr rule, the
calculation method, whether dhuhr and ʿaṣr (and maghrib and ʿishāʾ) are combined, and which book
the hub's adhkār and duʿāʾ come from; all of those stay editable afterwards under **Hub →
Madhab & calculation**. The question disappears once answered, and an install made before it
existed keeps its current settings rather than being asked again.

1. **Be the home screen.** Without this the old launcher is one press away.
2. **Usage access.** How the app sees which app is in the foreground. Nothing is enforced
   without it.
3. **Display over other apps.** Not for drawing — this is the exemption that lets the guard
   service put the block screen on top of an app you just opened. Without it, blocks fail
   silently on the routes that matter most.
4. **Notification access.** Only used to hold back notifications from gated apps. Calls, alarms,
   reminders, media, and anything the system pins always pass through untouched.
5. **Location** (optional). Asked for only when you press *use my location* on the prayer screen,
   and read once, on the spot, to fill in two numbers. Refusing it costs nothing but typing the
   coordinates in yourself.

Then **device admin** (optional but recommended): makes uninstalling take deliberate steps rather
than a long press on an icon.

Finally press **start enforcing**.

Navigation is not on that list. The launcher uses your phone's own — the swipe from the edge for
back, the swipe up for home, or the three buttons if that is how the phone is set. Which one you
get is decided in **Settings → System → Navigation mode** on the phone, not here.

For a phone where that navigation has been hidden or cannot be reached, there is an optional
strip of our own under **Settings → Appearance and navigation**. It is off by default and needs
an accessibility service, because that is the only API that can press back or open recents on
your behalf; it subscribes to no events and cannot read the screen.

| gesture on the strip | what happens |
| --- | --- |
| swipe up | home |
| tap, or swipe right | back |
| hold, or swipe left | recent apps |

## 2b. English or Arabic

**Settings → Appearance and navigation → Language.** Three choices: follow the phone, English, or
Arabic. Following the phone is the default and is right for almost everybody.

Choosing Arabic does two things. Every string in the interface is set in Arabic — including the
paragraphs that explain what each setting costs, which is the part that matters, since a screen
that argues in one language and labels its buttons in another argues to nobody. And the layout is
mirrored, because that is the direction Arabic is read in: the names move to the right margin,
the chevrons turn round, the back mark points the other way. The Hijri date comes over into
Arabic month names and Arabic numerals, sūras take their own names, and Mafātīḥ al-Jinān appears
under its own title.

Qur'anic text does not move. It is set flush right whatever the interface language is, because
that is where it belongs in either.

The choice is handed to Android's own per-app language, not kept privately: it survives a
restart, it appears in **Settings → System → Languages → App languages** on the phone beside
every other app, and changing it there works as well as changing it here. It is also stored in
this app's own settings, so a restore onto a new phone — which brings settings across but not the
system's per-app locale — comes up in the language you chose rather than in English.

Anything the app has no Arabic for falls back to the English rather than showing a blank.

## 2c. A widget under the clock

**Settings → Home widget.** One widget sitting under the clock in place of the app's one-line note
about the next prayer. This is meant for a salah app's widget: it knows your masjid's iqama and
your adhan, and this launcher does not. Press *choose a widget* and the list is everything the
phone publishes, by app. The first one you pick raises a system dialog asking whether this
launcher may hold widgets — say yes once and later picks are immediate. Choose the height, or
remove it and the next-prayer line comes back.

## 2d. The hub

**Hub**, at the bottom left of the home screen. Everything the phone does that is not an app.

The **qibla** is at the centre of it, drawn from the coordinates you already set for the prayer
times. On a phone with a compass the dial turns as you turn and the gold needle points at the
Kaʿba in the room you are standing in; on one without, the dial is drawn north-up and the needle
shows the bearing, which the screen says out loud rather than pretending otherwise. The bearing is
the great-circle one — the direction every published qibla agrees on, and not the direction a
straight line on a flat map would give.

Six things sit under it:

| tile | what it is |
| --- | --- |
| Prayer times | today's five, when each was or will be, and when each preferred window closes |
| Qur'an | the reader, the sūrah index, and the reciter |
| Tasbīḥ | a counter; the whole screen is the button, and it buzzes at the end of each set |
| Adhkār | morning and evening, out of your school's book |
| Duʿāʾ | the same book, read for what you need rather than for the hour |
| Fasting | the sunna days for the next three weeks, and a tick for the ones you kept |

**Faḍīla windows.** Each prayer has a window it stays valid in and a shorter opening portion that
is better. The app computes the end of that portion from named boundaries — *isfār* for fajr, the
one-shadow point for dhuhr, the red *shafaq* leaving the sky for maghrib, *shar'ī* midnight for
ʿishāʾ — and shows it under the home screen's prayer strip. Two of those are descriptions of the
sky rather than angles, so they are approximations and are worth checking against your masjid.
Nothing in the enforcement layer reads any of it: it is a nudge on a screen, not a rule. Turn it
off under **Hub → Madhab & calculation → Show faḍīla windows**.

**The mushaf.** The reader needs the full Qur'an, which is the one-shot download under
**Settings → Prayer times and salah**; the two dozen āyāt bundled in the APK are enough for the
gate and nowhere near enough to read a sūrah from, so the reader says so rather than showing three
of al-Kahf's hundred and ten. Tap an ayah to move the bookmark there.

The bookmark is also what the home screen shows. Tapping the ayah there moves it on by one — into
the next sūrah at the end of one, and back to al-Fātiḥa after al-Nās — so the two screens are never
in different places in the book, and reading the home screen a line at a time reads the Qur'an in
order. Until the download is done the home screen falls back to the next of the bundled āyāt rather
than showing nothing.

**Recitation** is downloaded, never streamed. Pick a reciter under the reader's footer — Sunnī and
Shīʿī reciters both, every one of them Ḥafṣ ʿan ʿĀṣim so the words track the text on screen — then
press *download* on a sūrah. It arrives an ayah at a time and resumes where it stopped if you
cancel or lose signal. Once it is complete the transport controls appear and it plays with the
network permanently gone. A sūrah at 128 kbps is roughly a megabyte a minute, so al-Baqara is a
couple of hundred megabytes; short sūrahs are a few. The app checks each reciter against the audio
host before letting you choose them, and says *not reachable* rather than starting a download that
will die.

**Adhkār and duʿāʾ** ship in the APK and need no network ever. Which book you get follows the
school: **Mafātīḥ al-Jinān** for Jaʿfarī and Zaydī, **Ḥiṣn al-Muslim** for the four Sunni schools.
The other one is always one tap away at the foot of the screen — the setting decides which is
offered, never which is permitted.

Mafātīḥ al-Jinān is here whole: all three bābs, both appendices and 194 texts, Kumayl and Ṣabāḥ
and Simāt and Mashlūl, Jawshan Kabīr and Ṣaghīr, Iftitāḥ and Abū Ḥamza, Nudba and ʿAhd, the
fifteen Munājāt, Ziyārat ʿĀshūrāʾ and the Jāmiʿa. Because it is a book rather than a list it is
read as one — the parts, then what is in a part, then the text — and each entry says roughly how
long it is before you open it. Every part and every text is named the way the book names it,
whichever language the interface is in; where a text has a settled English name — Duʿāʾ Kumayl,
Ziyārat ʿĀshūrāʾ — that name is printed underneath as a gloss, never in place of the heading.

**It is the book's own words.** The source is an Arabic edition of it — the muḥaqqaq printing,
904 pages — rather than the Persian one this used to be built from. That matters twice over. The
headings are the book's: every bāb, faṣl, maqṣad and text is titled the way it is titled in print,
instead of being invented here because the source's own headings were in a language the app does
not show. And al-Qummī's guidance sits beside the supplications instead of being thrown away for
the same reason: when to read a text, who narrated it, how many times. It is set small and grey,
because a page where the commentary is as loud as the supplication is a page you read instead of
pray. The editor's footnotes and his apparatus are not here; nothing else of the book is missing.

The import is a script, `tools/import_mafatih.py`, so all of that is something you can re-run
rather than something you have to take on trust. It reads the Word file through `wvHtml` because
the styles are the structure — `Heading 1` is a bāb, `Heading 2` a faṣl — so the outline is the
book's rather than something guessed from the writing. Inside a part it does have to guess once:
every supplication in this edition is fully vocalised and the prose around it is not, so a
diacritic decides which is which. Then it undoes the typesetting — the zero-width joiners that
hold *ʿalayhi al-salām* together, the marks that only steer the line direction — and splits the
book into an index and one file per text, so reading four lines of a taʿqīb does not parse a
megabyte. It needs `wv` installed (`apt-get install wv`).

Ḥiṣn al-Muslim is a hand-made *selection*, with an English rendering behind a toggle at the foot
of the page, and it says at its front that it is a selection. Every entry in
either book carries its source. To add to it, edit `app/src/main/assets/supplications.json` and
rebuild: it is data, not code, so a new supplication is a new object in that file from an edition
you trust and nothing else.

**Fasting** needs no setting up. The recurring sunna fasts are calendar facts, so the app works
them out from the Hijri date: the white days (the 13th to the 15th of any month), Mondays and
Thursdays, ʿĀshūrāʾ and ʿArafah, and Ramaḍān in full. Fasting is never suggested on the two Eids
or the days of tashrīq — those are shown as forbidden rather than left blank. Tick a day to record
that you kept it; days still to come cannot be ticked, because that would be a promise rather than
a record.

**Tasbīḥ** counts to 33, 34 or 100 and resets with the day. The count on the home screen's bottom
bar is today's, and tapping it opens the counter.

## 2e. Prayer times and salah

Under **Settings → Prayer times and salah**.

1. **Set a location**, by permission or by typing latitude and longitude. Nothing about salah is
   enforced until there is one, because a timetable computed for latitude zero would be worse
   than none.
2. **Pick the calculation method and the asr school.** Today's times appear at the top of the
   screen — check them against your masjid before trusting them to close the phone. Jafari is
   the Shia calculation, with maghrib after sunset rather than at it; Tehran is the other one.
3. **Combine, or don't.** Combined means one window covering dhuhr through asr and another
   covering maghrib through isha: two pauses a day rather than four. It also changes what the
   home screen and the prayer page print — three entries rather than five, each timed by the
   first prayer of its pair, with the second one's own start named underneath it.
4. **Press *pause for salah*.** From then on, during a prayer window, nothing opens but the
   dialer and whatever you have marked as opening during salah. Favourites included — this is
   the one limit that outranks them. Mark a mushaf or an adhkar app under **Apps and limits →
   the app → during salah**.
5. **Download the Qur'an** if you want the gate to draw from all 6,236 ayat rather than the two
   dozen bundled in the APK. It is fetched once and then never again.
6. **Choose the script for the ayah.** Arabic sets the whole block in Arabic, reference included
   — the sūrah named in Arabic and numbered in Arabic-Indic digits.

If you follow the Jafari calculation and the times are refreshed over the network, note that the
source reports asr at dhuhr and isha at maghrib, because the pairs are prayed together. Those are
not start times, so the app fills those two in from its own solver and keeps the rest of the
fetched day. Nothing to switch on; it just holds.

If the times are a minute or two out from your masjid, use the per-prayer corrections at the
bottom of the screen rather than changing method — those apply immediately, because they make
the times right rather than weaker. Turning salah enforcement *off*, or shortening a window,
waits out the cooldown like every other loosening.

The Hijri date under the clock comes from the Umm al-Qura calendar, which is calculated rather
than sighted and can sit a day either side of the local announcement; the ±1 offset on the same
screen is there to correct it.

### The network

Three endpoints, all optional, all one-shot: `api.aladhan.com` for refreshing the timetable,
`api.alquran.cloud` for the Qur'an text once, and `everyayah.com` for a sūrah's recitation when
you ask for one. Nothing is streamed and nothing polls. Everything works with the network
permanently unavailable — the prayer times are computed on the phone and the gate falls back to the bundled
ayat. Set **corrections → network → compute only** to switch syncing off entirely. Nothing is
ever uploaded, and there is no account, no analytics and no telemetry of any kind.

## 2f. Decide every app, once, before the cooldown starts

Straight after installing, the rules are **unlocked**: every change you make applies immediately.
Go to **Settings → Decide app by app** and sort the whole list — favourites for the home screen,
allowed for things that should just open, gated for the rest, blocked for the ones you want gone.

Favourites arrive on the home screen in the order you named them. To change that, press the faint
**≡** in the home screen's top right corner, beside the clock — it only appears once you have two
favourites to put in an order. Every name then grows a **≡** handle at its right edge: drag a
handle to move that name up or down, and hold it against the top or bottom edge to walk the list
past under it. The rest of the row still scrolls the list as usual, so you can move around a long
list without carrying an app with you. Press **done** (or the back gesture) when you are finished.
Nothing opens with a tap while the list is being arranged, and the new order is kept as soon as
you let go of a handle.

Rearranging is not a rule change, so it takes effect at once even after the rules are locked.

Preinstalled apps (camera, clock, files, calculator) start **allowed** — making you type "Files"
to open a file manager is friction with nothing on the other side of it. Anything you installed
yourself starts **gated**, and anything Android files under social/video/game/news/audio also
gets a 20 minutes-a-day, 3-opens-a-day budget.

When you are happy, press **lock these rules in**. From that moment the asymmetry applies:
tightening a rule is instant, loosening one waits out the cooldown (30 minutes by default). It is
a one-way door — there is no unlock button, by design.

## 3. Keep it alive on Xiaomi / Redmi / HyperOS

Aggressive battery management is the single biggest threat to enforcement — a killed guard
service means no limits. On a Redmi (HyperOS or MIUI), do all of these:

- **Settings → Apps → Yusr Launcher → Battery saver → No restrictions.**
- **Settings → Apps → Yusr Launcher → Autostart → on.**
- **Recents → hold the Yusr Launcher card → the padlock**, so clearing recents does not kill it.
- **Settings → Battery → turn off "Optimise battery use"** for this app if the toggle exists
  separately.
- If MIUI Optimisation is available in Developer options, leaving it on is fine; the app uses no
  OEM-specific APIs.

Check it worked: leave the phone idle for an hour, then open a gated app. If the gate appears,
the service survived.

Other manufacturers have their own version of this — Samsung's "Deep sleeping apps", OnePlus's
"Battery optimisation", Huawei's "App launch". Exempt Yusr Launcher wherever you find it.

## 4. Device owner (optional, strongest, hardest to undo)

Device owner is the only tier where the operating system itself refuses to open a blocked app.
It also blocks uninstall outright and pins the launcher so it cannot be swapped in Settings.

**Read this whole section before running the command.**

Requirements:

- The device has **no other accounts** — no Google account, no Mi account, nothing. In practice
  this means doing it on a freshly reset phone, before signing in anywhere.
- USB debugging enabled (Settings → About phone → tap Build number seven times → Developer
  options → USB debugging).

```
adb shell dpm set-device-owner dev.yusr/.admin.YusrAdminReceiver
```

Expected output: `Success: Device owner set to package dev.yusr`.

If it fails with "Not allowed to set the device owner because there are already several users on
the device", an account exists — remove every account, or factory reset, and try again.

### Removing device owner

There are exactly two ways out:

```
adb shell dpm remove-active-admin dev.yusr/.admin.YusrAdminReceiver
```

or a factory reset. There is no in-app button for this, on purpose. Do not set device owner on a
phone you cannot afford to reset.

## 5. Removing the app

- **Device owner set:** run the `remove-active-admin` command above first, then uninstall.
- **Device admin only:** Settings → Security → Device admin apps → Yusr Launcher → deactivate, then
  uninstall normally. You will be shown a warning first; that is the point.
- **Neither:** uninstall normally.

Set another launcher as your home screen before uninstalling, or the phone will have no home
screen until you do.

## Troubleshooting

**Blocked apps open anyway.** Usage access or "display over other apps" is missing, or the guard
service was killed — check the battery settings in step 3. The guard's ongoing notification is
the quickest way to tell: if it is gone, the service is gone.

**The block screen flashes and disappears.** Another app is fighting for the foreground. Set
device owner if you can; suspension at the OS level does not depend on the race.

**Notifications still arrive from a gated app.** Notification access was revoked (HyperOS
sometimes drops it after an update), or the notification is one that always passes: a call, an
alarm, a media/playback notification, or anything the system pins so you could not swipe it away
by hand either.

**A video will not start playing.** This was a real bug, now fixed: cancelling a player's media
notification tears down the foreground service behind it, so playback stops or never starts.
Media and pinned notifications are never cancelled. If you see it again on an older build,
update.

**The navigation strip does nothing.** It is off by default — the phone's own navigation is what
this launcher uses. If you have turned the strip on and it is not there, the accessibility
service is off; Settings → Appearance and navigation will say so and link to the right screen.
HyperOS also turns accessibility services off after some updates.

**A gated browser blocks links and web apps.** It should not any more: browsers are marked *opens
when another app sends you to it*, so a link, a sign-in page or a web app goes straight through
while opening the browser yourself still costs the gate. Check it under **Apps and limits → the
browser → when another app opens it**, and set it the same way for any other app that is really
a web view in a coat.

**The home widget is blank or missing.** The app behind it may have been uninstalled, or lost its
binding after an update. Settings → Home widget will say so; pick it again or remove it.

**The launcher crashes on every launch after choosing a widget.** Fixed, and it can no longer
lock you out: the widget is built inside a guard, and a note is left on disk while it is being
drawn. If the app starts and finds that note still there, the last attempt did not survive, so the
widget is dropped and the next-prayer line comes back instead of the crash. Nothing to do but
update — and if you are stuck on the broken build, see below.

**Stuck with a home screen that will not open.** Nothing on the phone is lost. Pull down the
notification shade and open Settings from the gear, set any other launcher as the home app
(**Settings → Home screen**, or **Apps → Default apps → Launcher**), install the newer APK over
the top — same signing key, so your rules and settings survive — then set Yusr Launcher back as home.

**Choosing a widget throws you back to the home screen.** Fixed. The screen used to open the
system's widget picker, which HyperOS and some other ROMs do not ship — the launch failed and took
the settings screen down with it, which lands you on the launcher. The list is now the app's own.

**A settings change did not take effect.** If it loosened a rule, it is queued — Settings →
Pending changes shows the countdown. The background check runs about every fifteen minutes, so a
change can apply a little after its stated time.

**Locked out of something you need.** Emergency bypass on the block screen: three per week. The
dialer, the messaging app, the keyboard, and the system Settings app are never gated.
