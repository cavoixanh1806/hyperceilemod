# Agent Rules — UnlockBootloader/root

## Project Context
This workspace contains tools and resources for modifying HyperOS ROM APKs on POCO F6 (Peridot).
Initially, the workflow attempted to recompile and overlay `MiuiSystemUI.apk` and `Settings.apk` systemlessly. However, because both run under UID 1000 (system) and are signed with Xiaomi's official platform key, custom-signed overlays trigger Zygote SELinux context aborts on boot.
**New Direction**: Transition to integrating the ZK Clock style customization hooks and settings logic directly inside the **HyperCeiler** LSPosed module codebase.

## Key References
- **Full workflow documentation**: `.agents/knowledge/hyperzk_clock_mod_workflow.md`
- **HyperCeiler LSPosed Codebase**: [HyperCeiler-2.10.166](file:///d:/DEV/UnlockBootloader/root/HyperCeiler-2.10.166)
- **Build scripts**: Located in the Gemini brain scratch directory (see conversation artifacts)
- **Modded APK sources**: `decoded_systemui/` and `decoded_settings/` (READ ONLY)
- **Working copies**: `active_decoded_systemui_full/` and `active_decoded_settings_full/`

## Rules
1. **Never modify** files in `decoded_systemui/` or `decoded_settings/` — these are reference sources.
2. **Always use `smali_classes13`** (or next available) for custom ZK/XMiui smali classes to avoid DEX overflow.
3. **Always skip `public.xml`** when running private attribute fixes.
4. **ZK smali uses `getIdentifier()`** for runtime resource lookup — no hardcoded hex resource IDs.
5. **APK signing**: Use `uber-apk-signer` with debug key for KernelSU module overlays.
6. **apktool version**: Must use 3.0.2+ for Android 15/16 SDK 36 support.
7. **Magisk/KernelSU ZIP Packaging**: Do NOT use PowerShell's `Compress-Archive` to pack modules on Windows (causes backslash `\` directory separator bugs on Linux/Android). Use Python's `zipfile` module (e.g., [zip_module.py](file:///C:/Users/Administrator/.gemini/antigravity-ide/brain/35c2908e-d8ed-4024-82bf-d2a87d61124c/scratch/zip_module.py)) or `7z` to maintain standard forward slashes (`/`).
8. **Settings Menu Integration (Cook ROMs)**: Cook ROMs (like NexiunOS) may dynamically filter custom header IDs. Always integrate custom ZK preference fragments (`zk.lab.zkMods`) as an entry inside the existing cook ROM lab XML (e.g., [fold_screen_settings.xml](file:///d:/DEV/UnlockBootloader/root/active_decoded_settings_full/res/xml/fold_screen_settings.xml) representing "NexiunOS-LAB") to ensure visibility.
9. **No Manual ADB Module Copies for Meta-OverlayFS**: If the target device uses `meta-overlayfs` (common on KernelSU Next/SUSFS configurations), copying files directly to `/data/adb/modules` will bypass registration and fail to mount. Always install the module via the KernelSU Next manager app.
10. **LSPosed Transition (HyperCeiler)**: Do not perform new SystemUI/Settings APK modifications. All status bar clock customization hooks, settings layout switches, and preference handlers must be integrated as Xposed hooks within the HyperCeiler module repository.



