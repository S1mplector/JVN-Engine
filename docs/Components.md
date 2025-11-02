# Components

Available components for JES scenes.

## Panel2D
- `x`, `y`: Position
- `w`, `h`: Dimensions
- `fill`: Color as `rgb(r,g,b,a)` with [0..1] values

## Label2D  
- `text`: Display text
- `x`, `y`: Position
- `size`: Font size (pixels)
- `bold`: Boolean for bold text
- `color`: Text color as `rgb(r,g,b,a)`
- `align`: left, center, right

## Sprite2D
- `image`: Resource path (classpath)
- `x`, `y`: Position
- `w`, `h`: Draw dimensions
- `alpha`: Opacity [0..1]
- `originX`, `originY`: Origin point [0..1] for rotation/scale center
- Region support: `sx`, `sy`, `sw`, `sh`, `dw`, `dh` for sprite sheets

## PhysicsBody2D
- `shape`: circle or box
- `x`, `y`: Initial position
- Circle: `r` (radius)
- Box: `w`, `h` (dimensions)
- `mass`: Mass for dynamics (default 1.0)
- `restitution`: Bounciness [0..1] (default 0.2)
- `static`: Boolean, immovable if true
- `sensor`: Boolean, trigger-only if true
- `color`: Visual color as `rgb(r,g,b,a)`
- `vx`, `vy`: Initial velocity (pixels/second)

## ParticleEmitter2D
- `x`, `y`: Emitter position
- **Emission**:
  - `emissionRate`: Particles per second
  - `maxParticles`: Maximum simultaneous particles
- **Life & Size**:
  - `minLife`, `maxLife`: Particle lifetime range (seconds)
  - `minSize`, `maxSize`: Initial size range (pixels)
  - `endSizeScale`: Size multiplier at end of life
- **Motion**:
  - `minSpeed`, `maxSpeed`: Initial speed range (pixels/second)
  - `minAngle`, `maxAngle`: Emission angle range (degrees, 0=right)
  - `gravityY`: Vertical gravity acceleration
- **Appearance**:
  - `startColor`: Initial particle color `rgb(r,g,b,a)`
  - `endColor`: Final particle color `rgb(r,g,b,a)`
  - `texture`: Optional texture path for particles
  - `additive`: Boolean for additive blending
