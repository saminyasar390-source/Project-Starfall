# Project Starfall – Main Menu Enhancement

## What changed
- Animated blue/cyan castle beacon and a softly pulsing magical glow.
- Drifting luminous motes/petals across the scene.
- Subtle breathing aura around the character.
- Animated button rims and a brighter gold glow while pressed/hovered.
- Uses the existing `sfx_portal.ogg` as a quiet one-shot magical arrival sound.
- Uses the existing `sfx_hover.ogg` and `sfx_click.ogg` for menu interaction.

## Files changed
- `app/src/main/java/com/starfall/game/MenuView.java`
- `app/src/main/java/com/starfall/game/SoundManager.java`

## Note about music
The project and `Audio Assets.zip` contain sound effects but no dedicated background-music track. The portal effect is used for the menu entrance, not looped as fake music. To have continuous menu music, add a licensed/royalty-free music file (for example `app/src/main/res/raw/menu_music.mp3`) and I can wire it to a lifecycle-safe looping `MediaPlayer`.

Open the project in Android Studio and run the `app` configuration. This package excludes generated `build/` output and local IDE caches so Android Studio can regenerate them.
