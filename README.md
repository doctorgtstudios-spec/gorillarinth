# GorillaRinth

An early Android UI prototype for a Quest mod-management companion.

## Current prototype
- Modrinth-inspired navigation and card layout with GorillaRinth yellow branding.
- Home, Mods, Patcher, Themes, and Settings tabs.
- Theme selector: yellow, green, blue, purple, dark, and light.
- Single-ZIP archive picker (one archive at a time).
- GitHub Actions workflow to build a debug APK automatically.

## Important status
This first build is a **UI prototype**, not a working Gorilla Tag mod loader. Wireless ADB pairing, Quest file access, OBB backup/patching, compatible mod archive extraction/installation, and direct game launching are not implemented. The patch button is deliberately disabled until the actual patch procedure and a compatible, authorized loader are verified. Selecting a ZIP archive only stores its selection; it does not extract or install its contents.

## Build
Push to `main` or run the **Build GorillaRinth APK** workflow manually from the Actions tab. Download the `GorillaRinth-debug-apk` artifact from the successful run.
