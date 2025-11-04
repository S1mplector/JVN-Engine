# Architecture

## Modules

- core: scene graph, components, physics
- swing: Swing Blitter2D implementation
- fx: JavaFX Blitter2D implementation
- scripting: JES lexer/parser/AST/loader, runtime scene (JesScene2D)
- runtime: CLI runner for engine + JES
- editor: JavaFX editor and viewport

## Data Flow

JES (.jes) → JesTokenizer → JesParser (AST) → JesLoader → JesScene2D

JesScene2D contains:
- Named entities created from components
- Input bindings
- Timeline actions
- Physics world

The Editor uses FxBlitter2D to render, provides picking/inspector and bridges input to JesScene2D.
