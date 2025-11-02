# Billiards Game (Standalone)

This project is a standalone JavaFX + JVN hybrid game using JES overlays.

## Requirements
- JDK 21
- Gradle (if no `gradlew` wrapper)
- JVN engine modules published to `mavenLocal`:
  - Run in the engine repo root: `./gradlew publishToMavenLocal`

## Run
- From this folder:
  - `gradle run`
- Or open in the JVN Editor:
  - File → Open Project… (select this folder)
  - Project → Run Project

## Project manifest
`jvn.project`
```
name=Billiards
type=gradle
path=
task=run
args=
```

## Overlay
A default JES overlay is bundled at:
- `samples/billiards_overlay.jes`
- `src/main/resources/samples/billiards_overlay.jes` (classpath fallback)
