# Themes

openplayer's own look, or one of ten modelled on classic interfaces, chosen in
**Settings → Appearance → Theme**. A theme with options of its own shows them right under the
picker. Glass theming, icon backgrounds and the background colour belong to openplayer's own look
and are only offered with it.

| Theme | Modelled on | What changes |
|---|---|---|
| Aqua | Mac OS X 10.0–10.2 with iTunes 2/3 | Global menu bar with working menus, brushed-metal window, gel traffic lights, pinstripes, an iTunes status display, Play as a blue gel pill, source list and striped song table. |
| Luna | Windows XP | Blue title bar and caption buttons, property-sheet tabs, taskbar with a working Start menu, Explorer task panes and Tiles view, green progress blocks, Play as a green round "go" button. |
| Classic | Windows 95/98 | Everything on a whole-pixel grid with an original 8 px bitmap font; bevelled window, menus, tabs and taskbar with Start menu; a CD Player with a seven-segment display; notched trackbars; sunken white lists; classic scrollbars; message boxes. |
| Platinum | Mac OS 9 | Menu bar with clock and application menu, ridged title bars with square boxes, Appearance Manager tabs, bevel buttons, lavender scroll thumbs and progress, Finder icon and list views. |
| iPod | Early (monochrome) iPod | The phone is the iPod: a greyscale LCD and a working click wheel instead of tabs and the Play arc. MENU goes back, the centre selects (and toggles the scrubber on Now Playing; hold it for the sleep timer), and turning the wheel moves through menus, scrolls lists and Settings, and sets the volume. |
| Metro | Zune HD / Windows Phone 7 | Panorama title running off the edge with parallax, pivot headers and sliding pages, round app-bar icons, toggle switches, accent tiles, Zune-style Now Playing with the artist's name drifting behind. |
| XMB | PSP / PS3 | A coloured backdrop with a flowing ribbon of light, categories as a row of glowing icons with the chosen one's contents listed below, options panels, face-button hints. |
| Blades | Xbox 360 (2005) | One coloured blade per tab; the others stacked at the edges as spines and sliding across on a tab change; highlight-bar menus; controller legend (B goes back, Y plays/pauses). |
| Channels | Wii menu | Playlists as rounded channels (with empty ones), the grey dock with the clock and round buttons for the tabs, a channel-preview Now Playing with *Wii Menu* and *Start*. |
| S60 | Nokia Symbian (S60 3rd edition) | Status pane with signal/battery bars, navi-pane tabs with arrows, chunky icons and the highlight bar, softkeys: Options menu, selection key for play/pause, Back/Exit. |

## Options

Each is a setting the original system itself offered.

| Theme | Option | Choices |
|---|---|---|
| Metro | Background | Dark or Light, with Windows Phone's own greys for each. |
| Metro | Accent colour | Windows Phone 7's ten: magenta, purple, teal, lime, brown, pink, mango, blue, red, green. |
| XMB | Colour | *Original*, which changes with the month as the PS3 and PSP did, or any one month's colour. Sony never published the values (the PS3's own theme files call theirs approximate), so these are matched to the consoles by eye. |
| Classic | Scheme | Windows Standard and the schemes Windows 98 shipped beside it (Brick, Eggplant, Lilac, Maple, Marine, Plum, Pumpkin, Rainy Day, Red White and Blue, Rose, Slate, Storm, Teal, Wheat). Values as recorded in ReactOS's copy of the same registry schemes; the high-contrast schemes are left out. |
| Aqua | Appearance | Blue, or Graphite: grey gels, traffic lights and selection. |

Choices are stored per theme and option (`skin.THEME.option` in settings), so each theme keeps
its own, and travel in backups like every other setting. An id a skin doesn't know reads back as
that option's default.

## On a phone

The app hides the system bars, and a skin's frame is built like a desktop or a device, with menu
bars, title bars, taskbars and softkeys hard against the edges. So the frame is set between bands
at the top and bottom that keep it clear of a camera cutout and of the screen's rounded corners,
measured from the phone itself and filled to carry on the frame's edge colour.

Text in fixed-size chrome (menu bars, taskbars, tabs, tiles, switchers, the mini player) grows
with the phone's font size up to 115%, which is as far as that chrome has room for. Lists,
titles and settings grow in full.

## How it's built

- `ui/skin/Skin.kt` holds the skin contract. Every shared control (text styles, icon buttons, panels,
  switchers, sliders, toggles, single-choice rows, menus, dialogs, header, mini player, the frame
  and tabs, Now Playing) asks `LocalSkin` first and falls back to openplayer's own implementation
  when it's null.
- `ui/skin/<theme>/` has one package per look. A theme with options builds one skin per set of
  choices (`ui/skin/Skins.kt` keeps them), declared in its companion's `Options`.
- `ui/skin/SkinOptions.kt` defines the option model; `ui/skin/FrameEdges.kt` measures the bands.
- `ui/skin/SkinFonts.kt` lists openly licensed stand-ins for each era's system font (credited on the
  licences screen). *Pixel 95*, the Classic theme's bitmap font, was drawn for openplayer.
- The theme is stored as `ui_theme` in settings (`UiTheme`); a stored theme that no longer exists
  reads back as openplayer's own look.

## Trying it on a phone

`./gradlew assemblePreview` builds `app/build/outputs/apk/preview/app-preview.apk`: optimised
like a release, signed with the debug key, and installed beside openplayer as *openplayer preview*
(`com.hyperperformant.openplayer.preview`) with its own data. Its folder backups go in
`.openplayer-preview-backup.json`, so it never overwrites the real app's.

## Seeing them

`ThemeRenderTest` draws the real app (MainActivity, its database and settings) under
Robolectric's native graphics in every theme, and writes stills, and optionally a 30 fps
walkthrough per theme, for review. It's skipped unless an output directory is given:

```
./gradlew testDebugUnitTest --tests '*ThemeRenderTest*' \
    -PthemeRenderOut=/tmp/frames \
    -PthemeRenderOnly=AQUA,LUNA \
    -PthemeRenderVideo=true
```

More switches:

- `-PthemeRenderVariants="METRO:background=light,accent=lime|CLASSIC:scheme=rainy"` adds looks
  with a theme's options set.
- `-PthemeRenderEdges=36,20` renders as on a phone keeping that many dp clear at the top and
  bottom.
- `-PthemeRenderQualifiers=w360dp-h640dp-port-xhdpi` and `-PthemeRenderFontScale=1.3` render on
  another screen, or with larger text.

Encode a walkthrough with
`ffmpeg -framerate 30 -i /tmp/frames/video_aqua/f%05d.png -vf scale=540:-2 -pix_fmt yuv420p aqua.mp4`.

If Maven Central rate-limits Robolectric's runtime download, pass
`-ProbolectricRepo=<a Maven Central mirror>`.

## Notes before shipping

- Some looks draw marks that echo their source: a four-colour flag on the Start buttons,
  controller face-button letters, and product names such as *Wii Menu*. Review those before any
  release.
- No original fonts, images or icons from the source systems are included.
