# Experimental themes

Eleven test looks modelled on classic interfaces, chosen in **Settings → Appearance → Theme**.
openplayer's own look is unchanged when *openplayer* is chosen; glass theming and the
background colour only apply to it.

| Theme | Modelled on | What changes |
|---|---|---|
| Aqua | Mac OS X 10.0–10.2 with iTunes 2/3 | Global menu bar with working menus, brushed-metal window, gel traffic lights, pinstripes, an iTunes status display, Play as a blue gel pill, source list and striped song table. |
| Luna | Windows XP | Blue title bar and caption buttons, property-sheet tabs, taskbar with a working Start menu, Explorer task panes and Tiles view, green progress blocks, Play as a green round "go" button. |
| Classic | Windows 95/98 | Everything on a whole-pixel grid with an original 8 px bitmap font; bevelled window, menus, tabs and taskbar with Start menu; a CD Player with a seven-segment display; notched trackbars; sunken white lists; classic scrollbars; message boxes. |
| Platinum | Mac OS 9 | Menu bar with clock and application menu, ridged title bars with square boxes, Appearance Manager tabs, bevel buttons, lavender scroll thumbs and progress, Finder icon and list views. |
| iPod | Early (monochrome) iPod | The phone is the iPod: a greyscale LCD and a working click wheel instead of tabs and the Play arc. MENU goes back, the centre selects (and toggles the scrubber on Now Playing; hold it for the sleep timer), turning the wheel moves through menus, scrolls lists, and sets the volume. |
| Metro | Zune HD / Windows Phone 7 | Panorama title running off the edge with parallax, pivot headers and sliding pages, round app-bar icons, toggle switches, accent tiles, Zune-style Now Playing with the artist's name drifting behind. |
| XMB | PSP / PS3 | Deep blue backdrop with a flowing ribbon of light, categories as a row of glowing icons with the chosen one's contents listed below, options panels, face-button hints. |
| Blades | Xbox 360 (2005) | One coloured blade per tab; the others stacked at the edges as spines and sliding across on a tab change; highlight-bar menus; controller legend (B goes back, Y plays/pauses). |
| Channels | Wii menu | Playlists as rounded channels (with empty ones), the grey dock with the clock and round buttons for the tabs, a channel-preview Now Playing with *Wii Menu* and *Start*. |
| S60 | Nokia Symbian (S60 3rd edition) | Status pane with signal/battery bars, navi-pane tabs with arrows, chunky icons and the highlight bar, softkeys: Options menu, selection key for play/pause, Back/Exit. |

## How it's built

- `ui/skin/Skin.kt` — the skin contract. Every shared control (text styles, icon buttons, panels,
  switchers, sliders, toggles, menus, dialogs, header, mini player, the frame and tabs, Now
  Playing) asks `LocalSkin` first and falls back to openplayer's own implementation when it's null.
- `ui/skin/<theme>/` — one package per look.
- `ui/skin/SkinFonts.kt` — openly licensed stand-ins for each era's system font (credited on the
  licences screen). *Pixel 95*, the Classic theme's bitmap font, was drawn for openplayer.
- The theme is stored as `ui_theme` in settings (`UiTheme`).

## Seeing them

`ThemeRenderTest` draws the real app (MainActivity, its database and settings) under
Robolectric's native graphics in every theme, and writes stills — and optionally a 30 fps
walkthrough per theme — for review. It's skipped unless an output directory is given:

```
./gradlew testDebugUnitTest --tests '*ThemeRenderTest*' \
    -PthemeRenderOut=/tmp/frames \
    -PthemeRenderOnly=AQUA,LUNA \
    -PthemeRenderVideo=true
```

Encode a walkthrough with
`ffmpeg -framerate 30 -i /tmp/frames/video_aqua/f%05d.png -vf scale=540:-2 -pix_fmt yuv420p aqua.mp4`.

If Maven Central rate-limits Robolectric's runtime download, pass
`-ProbolectricRepo=<a Maven Central mirror>`.

## Notes before anything ships

- These are tests. Some looks draw marks that echo their source (a four-colour flag on the Start
  buttons, controller face-button letters, product names such as *Wii Menu*); review those before
  any release.
- No original fonts, images or icons from the source systems are included.
