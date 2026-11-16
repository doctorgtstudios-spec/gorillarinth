# GorillaRinth

An Android companion and mod-management prototype for Gorilla Tag on Quest.

## Current features
- Original GorillaRinth interface with yellow default branding.
- Theme selector and local ZIP mod import.
- Wireless ADB pairing using the Android 11+ Wireless Debugging pairing code.
- Automatic discovery of ADB pairing and connection ports over mDNS.
- Reconnection to a previously paired Quest when Wireless debugging advertises its ADB endpoint.
- Pulls and saves the original Gorilla Tag OBB locally and creates a second backup on the Quest before changing it.
- Applies ZIP overlay patches to the OBB and can restore the saved original.
- Launches and force-stops Gorilla Tag over ADB.
- GitHub Actions builds a debug APK automatically.

## Meta sign-in for Gorilla Tag APK downloads

## Automatic QMOD game-version matching

When a QMOD is imported, GorillaRinth reads `packageId` and `packageVersion` from its `mod.json`. The QMOD `version` field is the mod's own release version; `packageVersion` is the target Android game's version. GorillaRinth checks the installed Gorilla Tag `versionName`. If it differs, GorillaRinth looks for an exact matching downloadable version in OculusDB and opens that build's Meta Developer Dashboard page automatically—there is no manual version picker. This follows the QMOD manifest schema's definition of `packageVersion`.

After downloading the matched APK through Meta, use **Import downloaded Gorilla Tag APK**. GorillaRinth validates the package name and version metadata before storing a private copy. Meta authentication remains in the browser; GorillaRinth does not collect passwords or browser cookies. Meta may restrict APK access based on account ownership and developer-dashboard permissions. OculusDB supplies version metadata, not download authorization. **This flow auto-selects the required build but does not silently install or patch it; Android's package installer still requires user confirmation, and QuestLoader APK patching is a separate unfinished feature.**

## Wireless ADB setup
1. Put GorillaRinth and the Quest on the same Wi-Fi network.
2. Open Android Developer options, enable Wireless debugging, and choose **Pair device with pairing code**.
3. GorillaRinth discovers the pairing port automatically. Enter the six-digit code shown by Android.
4. Keep Wireless debugging enabled. The app discovers the separate connection port and saves the pairing endpoint for reconnecting.

## OBB mod ZIP layout
A mod archive must contain a `mod.json` manifest and an `obb/` directory. Files under `obb/` are treated as paths inside the game's OBB archive. For example:

```
MyMod.zip
├── mod.json
└── obb/
    └── assets/
        └── example.dat
```

GorillaRinth copies the original OBB to private app storage and to a `.gorillarinth.bak` file on the Quest before replacing it. Applying a mod merges the files under `obb/` into the OBB ZIP, replacing entries with matching paths. Use **Restore original** to put the backup back. This overlay format only works for mods that supply compatible replacement/addition files; it does not translate arbitrary mod formats or bypass game integrity checks.

## Build
Push to `main` or run the **Build GorillaRinth APK** workflow manually from the Actions tab. Download the `GorillaRinth-debug-apk` artifact from the successful run.
