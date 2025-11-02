# Editor

JavaFX-based editor to preview JES scenes.

## Features
- Open/Reload a JES file
- Viewport render with FxBlitter2D
- Click to select `Panel2D` or physics body entities
- Property Inspector: position, panel width/height, body mass/restitution
- Selection overlay
- Keyboard/mouse bridged to JesScene2D input (test `on key` bindings)

## Usage
- Run: `./gradlew :editor:run`
- File → Open… to select a `.jes` file
- Click in canvas to select entities
- Edit numeric fields in the inspector to see live updates

## Roadmap
- Inspector fields for Label2D (text, size, bold, align, color)
- Scene Graph panel (select by entity name)
- Undo/redo, multi-select, gizmos
