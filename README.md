# GorillaRinth

An Android companion and mod-management prototype for Gorilla Tag on Quest.

## Current prototype
- Original GorillaRinth interface with yellow default branding.
- Home, Mods, Setup, Game, and Settings views.
- Theme selector with yellow, blue, cyan, purple, pink, red, orange, green, and RGB options.
- Local ZIP archive import with safe extraction into private app storage.
- Launch button opens Gorilla Tag when installed.
- GitHub Actions builds a debug APK automatically.

## Important status
This is an early prototype, not a working Gorilla Tag mod loader. Wireless ADB pairing, Quest file access, OBB patching, compatible mod installation, and game-side loading are not implemented. ZIP import only inspects files locally; it does not activate mods.

## Build
Push to `main` or run the **Build GorillaRinth APK** workflow manually from the Actions tab. Download the `GorillaRinth-debug-apk` artifact from the successful run.
