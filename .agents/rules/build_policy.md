# Build Policy

- **NEVER build or compile the Android app locally** (do NOT run `./gradlew assemble...`, `./gradlew compile...`, etc.).
- All app builds must ONLY be performed via **GitHub Actions**.
- Always verify code by static analysis / code inspection, never run local build commands.
