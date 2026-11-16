# GorillaRinth

An early Android UI prototype for a Quest mod-management companion.

## Current prototype
- Modrinth-inspired navigation and card layout with GorillaRinth yellow branding.
- Home, Mods, Patcher, Themes, and Settings tabs.
- Theme selector: yellow, green, blue, purple, dark, and light.
- Single-ZIP archive picker with safe extraction into private app storage (500-entry and 256 MB limits; rejects path traversal).
- GitHub Actions workflow to build a debug APK automatically.

## Important status
This first build is a **UI prototype**, not a working Gorilla Tag mod loader. Wireless ADB pairing, Quest file access, OBB backup/patching, compatible mod archive extraction/installation, and direct game launching are not implemented. The patch button is deliberately disabled until the actual patch procedure and a compatible, authorized loader are verified. ZIP import extracts one selected archive into GorillaRinth private storage with basic path-traversal and size protections. It does not install or load the contents into Gorilla Tag.

## Build
Push to `main` or run the **Build GorillaRinth APK** workflow manually from the Actions tab. Download the `GorillaRinth-debug-apk` artifact from the successful run.
