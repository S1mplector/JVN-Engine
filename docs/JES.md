# JES Language

JES (JVN Engine Script) is a small DSL for scenes, entities, components, input, and timelines.

## Example

```jes
scene "Demo" {
  entity "board" { component Panel2D { x: 40 y: 40 w: 400 h: 260 fill: rgb(0.12,0.14,0.18,0.9) } }
  entity "title" { component Label2D { text: "JES Demo" x: 60 y: 80 size: 24 bold: true color: rgb(1,1,1,1) align: left } }
  entity "ball"  { component PhysicsBody2D { shape: circle x: 200 y: 180 r: 20 mass: 1 restitution: 0.8 color: rgb(0.2,0.7,0.9,1) } }

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

## Values
- number: 12, 0.5
- string: "text"
- boolean: true/false
- color: rgb(r,g,b[,a]) with 0..1 floats
- identifier-as-string in value position: left, right, circle, box

## Blocks
- scene "Name" { ... }
- entity "Name" { component ... }
- component Type { key: value ... }
- on key "K" do Action { props? }
- timeline { actions... }

## Timeline actions
- wait ms
- move "entity" { x: , y: , dur: }
- rotate "entity" { deg: , dur: }
- scale "entity" { sx: , sy: , dur: }
- call "functionName"  (placeholder in runtime)
