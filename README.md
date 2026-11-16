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

## Automatic QMOD game-version matching and APK download

When a QMOD is imported, GorillaRinth reads `packageId` and `packageVersion` from its `mod.json`. The QMOD `version` field is the mod's own release version; `packageVersion` is the target Android game's version. GorillaRinth checks the installed Gorilla Tag `versionName` and looks up an exact downloadable OculusDB version when it differs. There is no manual version picker.

For a matched build, tap **Download exact required build**. Use **Sign in to Meta / get download token** to open Meta Developer Dashboard, then create an Oculus platform utility token in Account settings. Paste the token into GorillaRinth when prompted. The token is used only for that download and is not saved. GorillaRinth downloads the exact OculusDB binary ID from Meta's secure download endpoint, validates that the file is a Gorilla Tag APK with the requested `versionName`, and opens Android's package installer. Android still asks you to confirm installation. Meta requires an account authorized to download the game; a browser sign-in by itself does not grant build access.

You can also use **Import downloaded Gorilla Tag APK** to stage an APK you downloaded separately, then tap **Install downloaded APK**. Both paths use Android's package installer and do not bypass its confirmation or package-signature checks.

**Important limitation:** this implements authenticated APK download and user-confirmed installation, not QuestLoader APK patching. Native `.so` mods still cannot load until a compatible Quest modloader is injected into the game APK. GorillaRinth does not claim that the APK patcher is implemented. The existing OBB backup/patch/restore workflow remains separate and unchanged.

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
