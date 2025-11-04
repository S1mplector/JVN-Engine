# JES Parsing

This document explains how JES source files are turned into a running scene.

## Pipeline

- Tokenize: `JesTokenizer` → list of `JesToken`
- Parse: `JesParser` → `JesAst.Program`
- Load/build: `JesLoader` → `JesScene2D`
- Run: `JesScene2D` updates input, physics, timeline; renders entities and invokes registered calls

Key files:
- `scripting/src/main/java/com/jvn/scripting/jes/JesTokenizer.java`
- `scripting/src/main/java/com/jvn/scripting/jes/JesParser.java`
- `scripting/src/main/java/com/jvn/scripting/jes/ast/JesAst.java`
- `scripting/src/main/java/com/jvn/scripting/jes/JesLoader.java`
- `scripting/src/main/java/com/jvn/scripting/jes/runtime/JesScene2D.java`

## Tokenizer

`JesTokenizer` converts text into tokens with line/column info.
- Identifiers: letters/digits/`_`/`.`
- Numbers: int and float, optional leading `-`
- Strings: double-quoted with escapes `\n`, `\t`, `\`x``
- Symbols: `{ } : , ( )`
- Comments: `//` to end of line
- Whitespace and blank lines are skipped

Produced token types: `IDENT`, `STRING`, `NUMBER`, `LBRACE`, `RBRACE`, `COLON`, `COMMA`, `LPAREN`, `RPAREN`, `EOF`

## Grammar (informal)

```
scene "Name" {
  // optional scene-level props as key: value pairs (future use)
  entity "Name" { component Type { key: value ... } }
  on key "K" do actionName { key: value ... }   // input binding
  timeline {
    wait <ms>
    call "functionName"
    move   "entity" { x: , y: , dur: , easing: }
    rotate "entity" { deg: , dur: , easing: }
    scale  "entity" { sx: , sy: , dur: , easing: }
  }
}
```

Values supported by the parser:
- number: `12`, `-3`, `0.5`
- string: `"text"`
- boolean: `true`/`false`
- colors: `rgb(r,g,b[,a])` or `rgba(r,g,b,a)` where components are 0..1 doubles
- bare identifiers in value position are treated as strings (e.g., `left`, `circle`, `box`)

## AST shape

See `JesAst`:
- `Program` → list of `SceneDecl`
- `SceneDecl` → `name`, `props`, `entities`, `bindings`, `timeline`
- `EntityDecl` → `name`, list of `ComponentDecl`
- `ComponentDecl` → `type`, `props`
- `InputBinding` → `key`, `action`, `props`
- `TimelineAction` → `type`, optional `target`, `props`

## Loader: AST → Runtime

`JesLoader.buildScene(SceneDecl)` materializes a `JesScene2D`:
- Registers input bindings into `JesScene2D`
- Sets the timeline actions
- Builds supported components and registers entities by name

Supported components and props:
- Panel2D
  - `x`, `y`, `w`, `h`, `fill: rgb(...)`
- Sprite2D
  - `image`, `x`, `y`, `w`, `h`, `alpha`, `originX`, `originY`
  - Optional region draw: `sx`, `sy`, `sw`, `sh`, `dw`, `dh`
- Label2D
  - `text`, `x`, `y`, `size`, `bold`, `color: rgb(...)`, `align`
- ParticleEmitter2D
  - `x`, `y`, `emissionRate`, `minLife`, `maxLife`, `minSize`, `maxSize`, `endSizeScale`, `minSpeed`, `maxSpeed`, `minAngle`, `maxAngle`, `gravityY`, `texture`, `additive`, `startColor`, `endColor`
- PhysicsBody2D (visualized by `PhysicsBodyEntity2D`)
  - `shape: circle|box`, `x`, `y`, circle: `r`; box: `w`, `h`
  - `mass`, `restitution`, `static`, `sensor`, `vx`, `vy`

Entities are registered with `registerEntity(name, entity)` so timeline actions can reference them by name.

## Runtime behavior (JesScene2D)

- Physics: internal `PhysicsWorld2D` stepped each update
- Input: for each binding, if key was pressed, built-in actions are handled; others are forwarded to a Java handler if provided via `setActionHandler`
- Timeline: runs actions in order, supports easing via `Easing.Type`
- Calls:
  - `registerCall(name, handler)` to expose functions callable from timeline `call` steps or via runtime interop
  - `invokeCall(name, props)` executes a registered call or forwards to the scene-level action handler

## Extending JES

- New component types: add cases to `JesLoader.buildScene`
- New timeline actions: extend `JesParser.parseTimelineAction` and add logic in `JesScene2D.updateTimeline`
- New value types: extend `JesParser.parseValue`
- Additional built-in input actions: extend `JesScene2D.handleAction`

## Minimal example

```jes
scene "Demo" {
  entity "title" { component Label2D { text: "JES Demo" x: 60 y: 80 size: 24 } }
  on key "D" do toggleDebug
  timeline { move "title" { x: 60 y: 60 dur: 500 } wait 300 move "title" { x: 60 y: 80 dur: 500 } }
}
```
