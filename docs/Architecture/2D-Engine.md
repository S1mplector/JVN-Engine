# JVN 2D Engine – Technical Notes & DSL Reference

This document fills gaps in the existing docs for the 2D stack (Scene2D, JES, VNS handoff, assets, and runtime wiring).

## Architecture at a Glance
- Core runtime: `com.jvn.core.engine.Engine` owns `SceneManager`, global `Input`, `TweenRunner`; delta time is clamped/smoothed before dispatch.
- Scene graph: `Scene2DBase` + `Entity2D` subclasses (Sprite2D, Label2D, Panel2D, TileMap2D, ParticleEmitter2D, CharacterEntity2D, SpriteAnimation2D). Render order is by `z`, with per-entity parallax and transform stack.
- Rendering backends: `Blitter2D` implemented by Swing (`SwingBlitter2D`) and JavaFX (`FxBlitter2D`). Letterboxing/scaling handled in launchers via `ViewportScaler2D`. Blitters log/draw placeholders for missing assets.
- Camera: `Camera2D` with smoothing, bounds, zoom. Applied in Scene2DBase render path.
- Input: global `Input` + `ActionMap` for action bindings; JES binds keys to actions; Fx/Swing feed OS events to `Input`.
- Physics: `PhysicsWorld2D` + `RigidBody2D` (AABB or circle). Features: gravity, static rects from tilemaps, sensor callbacks, naive pairwise collision, raycast, world bounds, restitution, linear damping, step clamping. Debug overlay: `PhysicsDebugOverlay2D`.
- Assets: `AssetCatalog` with classpath manager by default; optional filesystem overlay via `--assets` flag (`FilesystemAssetManager` + `OverlayAssetManager`). Paths are relative to `game/` (images/audio/scripts/fonts).
- Tweening: `TweenRunner` dispatches arbitrary tween tasks each frame.
- Launchers: `FxLauncher` and `SwingLauncher` inject `Input`, `Camera2D`, handle resize/letterbox, and render the active scene.

## JES Runtime (JesScene2D) – Behaviors & Built-ins
- Components → entities:
  - `Panel2D { x y w h fill: rgb(...) stroke: rgb(...) }`
  - `Label2D { text x y size bold align color: rgb(...) }`
  - `Sprite2D { image x y w h alpha originX originY [sx sy sw sh dw dh] }` (region optional)
  - `TileMap2D { tileset: name map props: width height tileW tileH; layers can mark collision or triggerCall }`
  - `ParticleEmitter2D { x y emissionRate minLife maxLife minSize maxSize endSizeScale minSpeed maxSpeed minAngle maxAngle gravityY additive startColor endColor texture }`
  - `PhysicsBody2D { shape: circle|box x y [r|w h] mass restitution static sensor vx vy color }` (adds RigidBody2D + visible debug entity)
  - `Character2D { spriteSheet frameW frameH cols drawW drawH x y startTileX/Y speed originX/Y animations startAnim dialogueId z }`
  - `Stats { maxHp hp maxMp mp atk def speed onDeathCall removeOnDeath }`
  - `Inventory { slots items: \"id*count,...\" }`
  - `Equipment { slotName: itemId ... }`
  - `Ai2D { type target aggroRange attackRange attackIntervalMs attackAmount moveSpeed }` (chase/attack loop)
- Grid & movement: `gridW/gridH` derived from first valid map; `moveHero` uses grid steps and collision from `collisionTilemaps`. Interaction checks facing tile; calls `interactNpc` with `npc`, `dialogueId`, `facing`, `heroX`, `heroY`.
- Collisions & queries: `isTileBlocked`, `isWorldBlocked`, `raycast` exposed; tilemaps marked `collision` become static colliders.
- Timeline actions (per `timeline { ... }`): `wait`, `call`, `move`, `walkToTile`, `rotate`, `scale`, `fade`, `visible`, `cameraMove`, `cameraZoom`, `cameraShake`, `spawnCircle`, `spawnBox`, `damage`, `heal`.
- Built-in calls: `warpMap`, `useItem`, `giveItem`, `takeItem`, `equipItem`, `unequipItem`, `attack` plus user-registered `callHandlers`/`actionHandler`.
- State persistence: `saveState()/loadState()` serializes entity positions, player name/facing/pos, stats, inventories, equipment (see `JesSceneState`).

## VNS ↔ JES Integration
- Runtime flag `--jes <path>` loads a JES scene directly; otherwise VNS flows through MainMenu.
- VNS scripts can push a JES scene: `[jes push path label return_label with key=value]`.
- JES can return to VNS via `call "return" { label: "after_game" score: 123 }` (handled by VNS action handler).
- Engine exposes `VnInteropFactory` hook so VNS/JES can share data or transition scenes.

## 2D Engine Usage Notes
- Camera smoothing: set `camera.setSmoothingMs(ms)` and bounds before render for smooth pan/zoom.
- Parallax: set per-entity `parallaxX/Y` for layered backgrounds.
- Physics: set `RigidBody2D.setLinearDamping(...)` for drag; clamp steps via `PhysicsWorld2D.setMaxStepMs(...)` to avoid tunneling spikes; mark sensors for triggers.
- Tile collisions: call `TileMap2D.buildStaticColliders(world)` on collision layers; JesLoader does this when `collision: true`.
- Input: use `ActionMap` in custom scenes or JES `on key "K" do actionName` to map keys to actions, then handle in `actionHandler`.

## Asset & Packaging Notes
- Place assets under `game/images`, `game/audio`, `game/scripts`, `game/fonts`. Override with `--assets /path/to/external`.
- Classpath + filesystem overlay: classpath remains fallback; missing assets render magenta placeholders and log warnings.

## Suggested Next Steps for 2D Capabilities
1) Rendering & content
   - Add sprite batching/atlas trimming to reduce draw calls in Fx/Swing backends.
   - Add 9-slice / nine-patch support to Sprite2D (Panel2D already supports NinePatch object; extend loader).
   - Add text wrapping + max width to Label2D; bitmap font support for crisp pixel art.
2) Physics & collisions
   - Add simple broadphase grid to reduce pairwise checks; add swept-AABB for fast movers.
   - Per-body friction/drag; support one-way platforms in tilemaps.
3) Camera & input
   - Add camera shake channels and follow target with dead-zone; expose mouse-to-world helpers.
   - Add gamepad mapping to `Input`/`ActionMap`.
4) JES language
   - Add `sound` and `music` actions, screen fade helpers, and `spawn` for prefab entities.
   - Add named trigger volumes (not just tile triggers) and area enter/exit callbacks.
   - Add loop/branch constructs to `timeline` (repeat, conditional).
5) Tooling/editor
   - JES/VNS linting inside editor; preview tile collisions and AI aggro ranges.
   - Live asset reload (watcher) for images/audio in editor preview.
6) Testing & CI
   - Headless tests for physics edge cases (sensors, static collisions), timeline actions, and JES parsing of all components.
   - Golden image tests for sprite sheet region math and letterboxing transforms.
