# GorillaRinth

An early Android UI prototype for a Quest mod-management companion.

## Current prototype
- Modrinth-inspired navigation and card layout with GorillaRinth yellow branding.
- Home, Mods, Patcher, Themes, and Settings tabs.
- Theme selector: yellow, green, blue, purple, dark, and light.
- Single-ZIP archive picker with safe extraction into private app storage (500-entry and 256 MB limits; rejects path traversal).
- Launch button opens the installed Gorilla Tag app (`com.AnotherAxiom.GorillaTag`) when available.
- GitHub Actions workflow to build a debug APK automatically.

## Important status
This first build is a **UI prototype**, not a working Gorilla Tag mod loader. Wireless ADB pairing, Quest file access, OBB backup/patching, compatible mod installation, and game-side loading are not implemented. The patch button is deliberately disabled until a Gorilla Tag-specific patch procedure and compatible, authorized loader are verified. ZIP import extracts one selected archive into GorillaRinth private storage with basic path-traversal and size protections. The Launch button can open Gorilla Tag if it is installed, but does not load mods.

## Build
Push to `main` or run the **Build GorillaRinth APK** workflow manually from the Actions tab. Download the `GorillaRinth-debug-apk` artifact from the successful run.

## Driftrinth APK inspection notes
The supplied Driftrinth APK identifies itself in its embedded UI and code strings as an **Orion Drift** mod manager. Its native code references the Orion Drift package `com.AnotherAxiom.A2`, the `/sdcard/Android/obb/com.AnotherAxiom.A2/` OBB location, and Orion Drift-specific `.utoc`, `.ucas`, and `.pak` patch containers. Those details are game-specific and are not safe to transplant as a Gorilla Tag patcher. GorillaRinth therefore keeps the game target `com.AnotherAxiom.GorillaTag`; its launch action is implemented separately, while OBB patching remains disabled pending a verified Gorilla Tag-specific format and authorized workflow.
