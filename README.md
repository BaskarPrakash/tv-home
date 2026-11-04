# TV Home

A custom home screen for Android TV and Google TV, built for a TCL Smart TV Pro.
It shows your own photo as the background, with a clock, live weather, a 5-day
forecast, and your apps as round bubbles.

The stock Google TV home screen does not allow a custom wallpaper, so TV Home
replaces that screen with its own.

## Features

- Your own photo as the background, uploaded from a phone or PC
- Zoom and reposition the photo before setting it
- Clock, current weather and 5-day forecast (from [Open-Meteo](https://open-meteo.com), no account needed)
- App bubbles, one row at a time, with up and down to change rows
- Sort apps by most used, A to Z, or your own order
- Sidebar with app categories, an input source list (HDMI 1-4 and TV) and Customize

## Install the prebuilt APK

The ready-to-install file is [`dist/tv-home.apk`](dist/tv-home.apk).

1. On the TV, open **Settings > System > About** and select **Android TV OS build**
   seven times to unlock Developer options.
2. In **Developer options**, turn on **USB debugging**. Menu names vary between models.
3. Find the IP address of the TV in its network settings. The examples below use
   `192.168.1.2`.
4. On your PC, connect and install. Accept the **Allow debugging?** prompt on the TV.

   ```bash
   adb connect 192.168.1.2:5555
   adb install -r dist/tv-home.apk
   ```

5. Turn on usage access, which the **Most used** sort needs:

   ```bash
   adb shell appops set com.baskar.tvhome GET_USAGE_STATS allow
   ```

6. Open **TV Home** from the Apps list on the TV.

## Change the wallpaper

### Use your own photo

1. Open TV Home on the TV. The upload page only works while TV Home is on screen.
2. On a phone or PC on the same network, open `http://<TV-IP>:8765`,
   for example `http://192.168.1.2:8765`.
3. Choose a photo. A preview appears in the shape of the TV.
4. Use the **Zoom** slider, from fitting the whole photo up to 400%, and drag the
   photo to reposition it.
5. Select **Set as background**.

Photos can be up to 20 MB. The TV keeps only the latest upload, so keep your
original. If a browser cannot preview a photo, such as an iPhone HEIC file outside
Safari, the page says so and sends the photo without zooming.

### Use a built-in background

Select the **Customize** button at the bottom of the sidebar, then **Change
background**, and choose Midnight, Forest, Sunset, Slate or Ocean. The same menu
shows the upload address.

## Using the remote

| Action | How |
| --- | --- |
| Open an app | Highlight it and press **OK** |
| See more apps | Press **down** and **up** to change rows |
| Move an app | In the All category, hold **OK**, use the arrows, then press **OK** |
| Sort apps | Round button above the app row, or **Customize > Sort apps** |
| Filter by category | Sidebar buttons: All, Video, Music, Games, Other |
| Switch input | Sidebar input button, just above Customize |
| Set the weather city | Highlight the weather card and press **OK** |

## Build from source

Requirements:

- JDK 11 or later (`javac` and `jar` on your `PATH`)
- Android SDK with build-tools `35.0.0` and platform `android-34`
- A debug keystore at `~/.android/debug.keystore`, which Android Studio creates
- A POSIX shell. On Windows, use Git Bash

The script calls the SDK tools directly, so Gradle is not needed. It uses the
Windows tool names (such as `aapt2.exe`) and looks for the SDK in `ANDROID_HOME`,
then in `%LOCALAPPDATA%\Android\Sdk`. On macOS or Linux, set `ANDROID_HOME` and
remove the `.exe` and `.bat` endings in `build.sh`.

```bash
sh build.sh
adb install -r out/tv-home.apk
```

## Set as the default home screen

This is optional, and TV Home works from the Apps list without it.

```bash
adb shell cmd role add-role-holder android.app.role.HOME com.baskar.tvhome
```

On the TCL Smart TV Pro this command succeeds, but the Home button still opens
Google's launcher, which has a higher priority. Making the Home button open TV Home
would mean disabling that launcher. This has not been tested, loses Google's
recommendations, and could leave the TV with no working home screen, so keep
debugging turned on if you try it.

## Project layout

| Path | Purpose |
| --- | --- |
| `src/com/baskar/tvhome/MainActivity.java` | Home screen, apps, sorting, dialogs |
| `src/com/baskar/tvhome/BackgroundServer.java` | Upload web page on port 8765 |
| `src/com/baskar/tvhome/WeatherClient.java` | Open-Meteo city search and forecast |
| `src/com/baskar/tvhome/ClockView.java` | Analog clock hands |
| `res/drawable/` | Icons |
| `build.sh` | Build script |
| `dist/tv-home.apk` | Prebuilt APK |

## Known limitations

- The APK is signed with a debug key, so it is for personal sideloading only.
- The upload page has no password. While TV Home is on screen, anyone on your
  network can change the background.
- Input switching and the HDMI list use TCL package names, so other TV brands
  would need changes.
- Some apps do not declare a category, so they may appear under **Other**.
- These have not been fully tested on the TV: moving apps in the grid, the
  category buttons, the Settings and Network buttons, choosing an input, and
  dragging the photo on the upload page.
