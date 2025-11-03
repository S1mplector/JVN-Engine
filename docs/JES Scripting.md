# JES Language

JES (JVN Engine Script) is a small DSL for scenes, entities, components, input, and timelines.

## Example

```jes
scene "Demo" {
  entity "board" { component Panel2D { x: 40 y: 40 w: 400 h: 260 fill: rgb(0.12,0.14,0.18,0.9) } }
  entity "title" { component Label2D { text: "JES Demo" x: 60 y: 80 size: 24 bold: true color: rgb(1,1,1,1) align: left } }
<  entity "ball"  { component PhysicsBody2D { shape: circle x: 200 y: 180 r: 20 mass: 1 restitution: 0.8 color: rgb(0.2,0.7,0.9,1) vx: 50 vy: -30 } }>
  entity "fire" { 
    component ParticleEmitter2D {
      x: 400 y: 200
      emissionRate: 20
      minLife: 0.5 maxLife: 1.5
      minSize: 4 maxSize: 12 endSizeScale: 0.1
      minSpeed: 20 maxSpeed: 60
      minAngle: 250 maxAngle: 290
      gravityY: -30
      startColor: rgb(1,0.8,0.2,1)
      endColor: rgb(1,0.2,0,0)
    }
  }

  on key "D" do toggleDebug
  on key "C" do spawnCircle { r: 20 restitution: 0.9 }
  on key "B" do spawnBox { w: 40 h: 40 restitution: 0.4 }

  timeline {
    move "title" { x: 60 y: 60 dur: 500 easing: ease_out_back }
    wait 300
    move "title" { x: 60 y: 80 dur: 500 easing: ease_in_out_sine }
    rotate "ball" { deg: 360 dur: 2000 easing: ease_in_out_cubic }
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
- move "entity" { x: , y: , dur: , easing: }
- rotate "entity" { deg: , dur: , easing: }
- scale "entity" { sx: , sy: , dur: , easing: }
- call "functionName"  (placeholder in runtime)

## Easing Types
Optional easing property for timeline actions:
- linear (default)
- ease_in_quad, ease_out_quad, ease_in_out_quad
- ease_in_cubic, ease_out_cubic, ease_in_out_cubic
- ease_in_quart, ease_out_quart, ease_in_out_quart
- ease_in_expo, ease_out_expo, ease_in_out_expo
- ease_in_sine, ease_out_sine, ease_in_out_sine
- ease_in_elastic, ease_out_elastic, ease_in_out_elastic
- ease_in_back, ease_out_back, ease_in_out_back
- ease_in_bounce, ease_out_bounce, ease_in_out_bounce
