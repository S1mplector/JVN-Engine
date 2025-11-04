# Runtime

CLI runner to load JES scenes and select rendering backend.

## Usage
```bash
./gradlew :runtime:run --args="--jes <path> --ui swing"
./gradlew :runtime:run --args="--jes <path> --ui fx"
```

- --jes: path to .jes file
- --ui: swing|fx

Default input actions supported in sample runtime:
- D: toggle physics debug overlay
- C: spawn circle at cursor
- B: spawn box at cursor
