# Work log: Sony BRAVIA TL2

Everything was done over ADB from a Mac on the same network, and checked on the TV with
screenshots after each step. Nothing was uninstalled: every change can be undone (see
[README.md](README.md)).

## 1. Finding the TV

- mDNS browse (`dns-sd -B _androidtvremote2._tcp` and `_googlecast._tcp`) found two Android TVs
  on the network; this work covers only the BRAVIA TL2.
- Network ADB (port 5555) was already open; `adb` was installed (`android-platform-tools`) and
  the "Allow USB debugging?" prompt accepted on the TV.

## 2. What the TV turned out to be

- **Sony BRAVIA TL2**, device `OD0M_EA_T32`, Android TV 11 (API 30), build `RTM2.210929.098`,
  about 1.7 GB RAM, MediaTek SoC.
- It does **not** run Sony's usual software. The packages are TPV's (`org.droidtv*`,
  `com.tpv_soem.*`, the company that builds Philips TVs) and MediaTek's (`com.mediatek.wwtv.*`),
  so none of cleanyourtv's Sony or Philips lists applied and a TL2 list was built from scratch.
- Health check at the time: CPU mostly idle (the load average of ~67 is inflated by MediaTek
  kernel threads), memory tight (swap in use, background apps being killed), storage fine, no
  crashes since boot, previous restarts all ordinary software reboots.
- Classic Android TV home (`com.google.android.tvlauncher`), not Google TV.

## 3. Backup

Before any change: full package list, disabled packages, `getprop` and all `settings`
(system/secure/global) plus the HOME resolver were saved locally. The backup is not in this
repository (it contains device identifiers).

## 4. Debloat

Unclear packages were inspected with `dumpsys package` (activities, services, receivers) before
deciding. Disabled with `pm disable-user --user 0`, in two small batches with a screenshot, a
Live TV check and a Chromecast check after each. The full list with notes is
[debloat.txt](debloat.txt):

- Not used by the owner: Prime Video, Disney+, Red Bull TV, a football game, Zeus.
- Junk: retail demo mode (`fusion.android.tv.demo`), partner app auto-installer
  (`android.autoinstalls.config.tpv.device`), Google Feedback, Play Games.

Kept on purpose: the TPV TV stack and Live TV (`org.droidtv*`, `com.mediatek.wwtv.tvcenter`,
`com.mediatek.tvinput`), Google Assistant and voice (`katniss`, `tts`), Chromecast
(`mediashell`), the phone remote service, Netflix, YouTube, YouTube Music, HBO GO, and anything
update-related. ARD, ZDF and Google TV (`videos`) were already disabled and left out of the list,
so `restore.sh` doesn't switch them back on.

Also: Jellyfin was installed from the Play Store. **Gemini** is not available: Google ships it
only for Google TV, and this set runs classic Android TV 11, so nothing unofficial was installed.

## 5. Launcher

cleanyourtv's native launcher (Java, RecyclerView) was built with JDK 17 and SDK 34, installed,
opened by hand, tested, then made the default home; the stock launcher was disabled (only it and
Android's fallback claimed HOME, so no setup-wizard trap). Verified to survive a reboot.

Configuration for this TV: four-app dock; Live TV, Settings and the e-manual hidden from the grid
(Live TV is in the Inputs menu, Settings in the top bar).

### Added on top of cleanyourtv

| Feature | Files |
|---|---|
| **Weather widget**: current conditions, today's high/low, sunrise/sunset, next 3 days. Open-Meteo (no key); location from the public IP once a day (ipapi.co, falling back to ipwho.is) or pinned in config.xml; cached, refreshed at most every 30 min while Home is shown; fades out as the grid scrolls. Needed the `INTERNET` permission. | `WeatherWidget.java`, `WeatherIcon.java` (glyphs drawn in code) |
| **10-day forecast** (OK on the widget), laid out like Apple Weather: sky gradient for the conditions and time of day, big temperature, hourly strip with sunrise/sunset slotted in and rain chances (Left/Right scroll), 10-day list with temperature range bars on a shared scale and a dot for "now", tiles for feels like, UV, wind, humidity and the next sun event. | `WeatherActivity.java`, `RangeBar.java` |
| **All Apps drawer**: Right at the right edge slides in every launchable app A–Z (phone-style apps only if user-installed, so factory tools stay out); Left, Back or Home closes it. | `AppDrawer.java` |
| **Arranging Home**: long-press menu (Move / Remove from Home / App info); Move mode with an iPhone-style jiggle and arrow-key carrying, OK or Back to drop; Add to Home from the drawer, including hidden and phone-style apps; arrangement persisted; new installs appended. | `HomeLayout.java`, `AppAdapter.java`, `MainActivity.java` |
| **tvOS-inspired redesign**: Inter typeface (OFL); liquid-glass panels (translucent fill, rim lit along the top edge, drawn in code because XML shapes can't stroke a gradient); white focused buttons; focused tiles lift with a real shadow, tilt in from the direction of travel and catch a sweeping sheen instead of an outline highlight; glass dock band, drawer and menus; deeper sky-tinted glass cards on the forecast. No blur: too heavy for a 1.7 GB Android 11 TV. | `Glass.java`, `Shine.java`, `DockDecoration.java`, drawables |

Apple's tvOS UI kit was **not** used: its license allows it only for mock-ups of software for
Apple platforms, so the design is an original interpretation with an open-license font and
self-drawn icons.

### Bugs found and fixed along the way

- The TV's `screencap` prints log lines before the PNG; `shot.sh` strips them.
- A factory "Disclaimer Customization" entry showed in All Apps; system apps with only a phone
  launcher entry are now skipped.
- Glass panels looked milky because the rim was a filled layer under a translucent fill; the rim
  is now a real gradient stroke.
- A shadow under the focused (translucent) weather panel showed through it; the panel only
  scales now.
- 10-day rain chances were clipped by divider spacing; rows were tightened.

## 6. Still to do on the TV

- Switch off USB/network debugging when no more changes are planned.
