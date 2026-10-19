# Sony BRAVIA TL2: cleanup and custom home screen

A debloated Sony BRAVIA TL2 (Android TV 11, built by TPV on a MediaTek chip) running a custom
tvOS-inspired launcher with a weather widget, a 10-day forecast screen, an All Apps drawer and
on-TV arranging. Built on [cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT). What was
done and why is in [WORKLOG.md](WORKLOG.md).

Every script talks to the TV over ADB, so **network/USB debugging must be on** while you use
them (Settings → Device Preferences → Developer options), and you pass the TV's address:

    export TV_HOST=192.168.1.50      # your TV's IP

Switch debugging off again when you're done: while it's on, anything on your network can try to
connect to the TV.

## Files

| File | What it does |
|---|---|
| `debloat.txt` | The apps that were disabled (not uninstalled), with notes |
| `disable.sh` / `restore.sh` | Apply / undo `debloat.txt` |
| `build-and-install.sh` | Build the launcher in `../launcher` and install it as the default home |
| `shot.sh out.png` | Screenshot the TV (Live TV video shows black: it's a protected layer) |

Building needs JDK 17 and the Android SDK (`brew install openjdk@17 android-platform-tools` and
`brew install --cask android-commandlinetools`, then `sdkmanager "platforms;android-34" "build-tools;34.0.0"`).

## Re-enable apps

One app:

    adb connect $TV_HOST:5555
    adb -s $TV_HOST:5555 shell pm enable com.disney.disneyplus

Everything in `debloat.txt`, plus the stock Google home screen as default:

    ./restore.sh debloat.txt

To remove the custom home screen completely after that:

    adb -s $TV_HOST:5555 uninstall com.gathin.tvhome

## Using the home screen

- **Up/Down** move one row; **Down** from the dock scrolls into the rest of your apps.
- **All Apps**: press **Right** at the right edge (last tile of a row, the Settings button or the
  weather panel). Every app A–Z; **Left** or **Back** closes it.
- **Arranging Home**: hold **OK** on a tile for *Move*, *Remove from Home* or *App info*. In Move
  the tile jiggles; the arrows carry it (the dock is the first row) and OK or Back drops it. In All
  Apps, hold OK on an app for *Add to Home Screen* / *Remove from Home*. The arrangement is saved
  on the TV; newly installed apps appear at the end of Home and in All Apps automatically.
- **Weather**: Up from the dock (or Down from the top buttons) selects the weather panel; OK
  opens the full forecast (hourly, 10 days, feels like, UV, wind, humidity, sunrise/sunset).
  Left/Right scroll the hourly strip, Back returns Home.
- **Inputs** lists the HDMI ports and Live TV. At power-on the TV opens Live TV itself (a TV
  setting); Home goes to this screen.

## Changing the launcher

Settings live in `../launcher/app/src/main/res/values/config.xml`; edit, then run
`./build-and-install.sh`.

- **Dock order**: easiest on the TV (*Arranging Home* above). `dock_apps` is only the starting
  order, used until you first move, add or remove something.
- **Hidden tiles**: `hidden_apps` (Live TV, Settings, the e-manual). They stay in All Apps, and
  *Add to Home Screen* there overrides this.
- **Weather**: `weather_widget` true/false. By default the location comes from the TV's public
  IP once a day ([Open-Meteo](https://open-meteo.com), no account). To pin it, set
  `weather_latitude`, `weather_longitude` and `weather_place`.

### Wallpaper (no rebuild)

The launcher reads `config.json` and `wallpaper.jpg` from its folder on the TV, overriding
`config.xml` (keys in `../themes/README.md`):

    D=/sdcard/Android/data/com.gathin.tvhome/files
    adb -s $TV_HOST:5555 push wallpaper.jpg $D/wallpaper.jpg
    adb -s $TV_HOST:5555 push config.json  $D/config.json     # optional
    adb -s $TV_HOST:5555 shell "am force-stop com.gathin.tvhome; am start -n com.gathin.tvhome/.MainActivity"

Remove both files (`shell rm -f $D/wallpaper.jpg $D/config.json`) and restart to go back.
