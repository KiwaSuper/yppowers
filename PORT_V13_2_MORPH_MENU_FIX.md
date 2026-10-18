# Port v13.2 — Shadow Archer morph menu reliability

- Added a dedicated server-to-client sync flag for Shadow Archer ownership.
- The flag is sent on join, power assignment, and power clearing.
- Pressing V now opens the morph menu locally and immediately for Shadow Archer holders instead of depending on a server round-trip before the screen appears.
- Server-side morph requests still verify that the player owns Shadow Archer and that the requested mob was defeated; the new UI path does not bypass authorization.
- Kept the old server-response path as a fallback for clients that still send the tertiary keybind packet.

## Validation

- Static source checks and ZIP integrity check performed. A Gradle build is not claimed because this environment previously could not resolve/download the configured Gradle distribution.
