# Java Vector Nexus Engine

<div align="left">
    <img src="docs/images/jvn_logo.png" width="512">
    <br><br>
</div>

Modern, modular 2D game engine with a lightweight scene graph, simple physics, a custom DSL (JES) for content, and a JavaFX Editor for iteration.

- Modules: `core`, `swing`, `fx`, `scripting`, `runtime`, `editor`
- Rendering backends: Swing (`swing`) and JavaFX (`fx`)
- Scripting: JES (JVN Engine Script) for scenes, entities, components, input, and timeline
- Editor: open a JES file, preview, select entities, tweak properties live

## Quick Start

Prereqs: JDK 17+, Gradle (wrapper included).

- Build scripting and editor:
```bash
./gradlew :scripting:build :editor:build -x test
```

- Run the Editor:
```bash
./gradlew :editor:run
```
Use File → Open to load `samples/sample.jes`. Click to select entities; edit properties in the inspector.

- Run the Runtime with a JES script:
```bash
./gradlew :runtime:run --args="--jes samples/sample.jes --ui swing"
```
Keys: D = toggle physics debug, C = spawn circle, B = spawn box.

## JES at a glance

```jes
scene "Demo" {
  entity "board" {
    component Panel2D { x: 40 y: 40 w: 400 h: 260 fill: rgb(0.12,0.14,0.18,0.9) }
  }
  entity "title" {
    component Label2D { text: "JES Demo" x: 60 y: 80 size: 24 bold: true color: rgb(1,1,1,1) align: left }
  }
  entity "ball" {
    component PhysicsBody2D { shape: circle x: 200 y: 180 r: 20 mass: 1 restitution: 0.8 color: rgb(0.2,0.7,0.9,1) }
  }

  on key "D" do toggleDebug
  on key "C" do spawnCircle { r: 20 restitution: 0.9 }
  on key "B" do spawnBox { w: 40 h: 40 restitution: 0.4 }

  timeline {
    move "title" { x: 60 y: 60 dur: 500 }
    wait 300
    move "title" { x: 60 y: 80 dur: 500 }
  }
}
```

- Values: numbers, strings, booleans, colors via `rgb(r,g,b[,a])` with 0..1 floats
- Blocks: `entity`, `component`, `on key`, `timeline`
- Actions: `toggleDebug`, `spawnCircle`, `spawnBox`; timeline supports `wait`, `move`, `rotate`, `scale`, `call`

## Modules

- `core`: scene graph (`Scene2DBase`, `Entity2D`), components (`Panel2D`, `Label2D`, `Sprite2D`), physics (`RigidBody2D`, `PhysicsWorld2D`)
- `swing`: Swing implementation of `Blitter2D`
- `fx`: JavaFX implementation `FxBlitter2D` and app launcher helpers
- `scripting`: JES tokenizer, parser, AST, and loader → builds `JesScene2D`
- `runtime`: command-line runner (loads JES with `--jes`, selects UI via `--ui`)
- `editor`: JavaFX editor with canvas viewport, selection overlay, and property inspector

## Documentation

- docs/Overview.md – Project overview
- docs/Architecture.md – Modules and how they fit together
- docs/JES.md – Language syntax, examples, and grammar sketch
- docs/Components.md – Supported components and their properties
- docs/Runtime.md – CLI flags, running scripts
- docs/Editor.md – Editor usage, selection, inspector, roadmap

## Roadmap

- Editor: richer inspector (Label2D color, align, font), scene graph panel, input bridge
- JES: functions/variables, loops, math, reusable prefabs
- Engine: shaders, particle system, tilemaps, audio

## License
TBD.
