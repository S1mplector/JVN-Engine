# Components

## Panel2D
- x, y: position
- w, h: size
- fill: rgb(r,g,b,a)

## Label2D
- text: string
- x, y: position
- size: number
- bold: boolean
- color: rgb(r,g,b,a)
- align: left|center|right

## Sprite2D
- image: classpath string
- x, y: position
- w, h: size (no region) or dw, dh (with region)
- alpha: 0..1
- originX, originY: 0..1 (normalized pivot)
- optional region: sx, sy, sw, sh, dw, dh

## PhysicsBody2D
- shape: circle|box
- x, y: position
- r (circle)
- w, h (box)
- mass: number
- restitution: bounciness (0..1)
- static: boolean
- sensor: boolean
- color: rgb(r,g,b,a) (visual only)
