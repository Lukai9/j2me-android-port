# J2ME Touch Port

Play old Java (J2ME) phone games on Android with proper touch controls.

Import a game's `.jar` and the app converts it on your phone. The game's original code then runs
natively, on top of an Android implementation of the phone APIs it expects. You get an 8-way
D-pad, an OK button and the two softkeys, with crisp pixel scaling.

**No games are included.** You need the `.jar` file of a game you own.

## Install

Download `j2me-touch-port.apk` from [Releases](../../releases), open it on your phone, and allow
installing from unknown sources when asked. Requires Android 5.0 or later.

## Use

1. Tap **Import .jar game** and pick the game's `.jar` file. You can also open or share a `.jar`
   from a file manager straight into the app.
2. The game converts in a few seconds and starts.
3. Long-press a game in the list to change its **screen size** or **delete** it.

### Controls

| On screen | Phone key |
|---|---|
| D-pad (8-way, multi-touch) | Navigation keys |
| **OK** | Centre / fire key |
| Left and right bars | Left and right softkeys (the game shows their labels in its bottom corners) |
| ✕ between the softkeys | Quit the game |

Portrait puts the keypad under the screen; landscape puts it on both sides.
Hardware keyboards and gamepads also work: arrows or WASD, Enter/Space = OK,
Menu/Q = left softkey, Back/E = right softkey, number keys.

### Screen size

Phones of that era had different screens, and a game only draws correctly at the size it was
made for. The app guesses from the jar (its manifest, or a size like `240x320` in the filename)
and defaults to 240 × 320. If the picture looks cut off or squashed, long-press the game and pick
another size. Common sizes are 128 × 160 (older Nokia S40), 176 × 208 (S60) and 240 × 320 (later
S40 and S60).

## Compatibility

Tested with **WALL-E** (THQ, Nokia S40 240 × 320): menus, gameplay, music, saves and
pause/resume all work.

Implemented: MIDP `lcdui` Canvas graphics and fonts, `GameCanvas`, Nokia UI
`DirectGraphics`/`DirectUtils`, MMAPI audio (MIDI/WAV via MediaPlayer), RMS record stores (saves),
and app properties.

**Not implemented yet:** high-level UI screens (`Form`, `List`, `Alert`, `TextBox`, `Command`),
MIDP 2 `Sprite`/`TiledLayer`/`LayerManager`, networking, Bluetooth, M3G 3D, and Samsung/Sony
Ericsson vendor APIs. If a game needs one of these, the app tells you which API was missing
instead of crashing silently. Use **Copy details** and open an issue.

For a mature emulator that runs far more games, see
[J2ME-Loader](https://github.com/nikita36078/J2ME-Loader). This project is smaller, with controls
tuned for full-screen action games.

## Build from source

Requires JDK 17+, `curl`, and the Android SDK (build-tools 34.0.0, platforms/android-34).
No Gradle needed.

```
bash build.sh               # writes j2me-touch-port.apk
adb install -r j2me-touch-port.apk
```

The first build downloads `dalvik-dx` (checksum-verified) and creates `release.keystore`.
Keep that key. Updates must be signed with the same key, and it should never be committed
(it's in `.gitignore`). Set `KEYSTORE` and `KEYSTORE_PASS` to sign with your own.

## How it works

- `src/javax/microedition/**`, `src/com/nokia/**`: the phone APIs, implemented with
  `android.graphics`, `MediaPlayer` and app-private files.
- `src/j2me/touchport/Games.java`: import. Reads the jar manifest, converts the bytecode to
  Android's dex format on the phone with `dx`, and stores `classes.dex` together with the jar's
  resources.
- `GameActivity`: loads the game with a `DexClassLoader` in its own process (MIDlets keep their
  state in statics, so each run gets a fresh process).
- `GameView`: draws the virtual screen and the touch controller.

## License

MIT, see [LICENSE](LICENSE). Includes AOSP `dx` (Apache 2.0), see [NOTICE](NOTICE).
Game names mentioned here are trademarks of their owners. This project isn't affiliated with them.
