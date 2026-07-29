# HyperZK Clock Mod — Complete Workflow Reference

> **Purpose**: This document captures the full context, architecture, and workflow for merging custom
> status bar clock styles from a modded HyperOS ROM into the user's active cook ROM on POCO F6 (Peridot),
> packaged as a KernelSU/Magisk flashable module. No Iconify dependency.

---

## 1. Device & ROM Context

| Item | Value |
|------|-------|
| **Device** | POCO F6 (codename: `peridot`) |
| **SoC** | Snapdragon 8s Gen 3 (SM8635) |
| **OS** | HyperOS (MIUI-based, Android 15/16, SDK 36) |
| **Kernel** | KernelSU Next + SUSFS |
| **Active ROM** | Cook ROM (ZKOS / NexiunOS variant) |
| **Recovery** | OrangeFox R12.0 |
| **ADB serial** | `3a2422d5` |

### System APK Paths (on device)
```
/system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk
/system_ext/priv-app/Settings/Settings.apk
```

---

## 2. Source Files Layout (on host PC)

```
d:\DEV\UnlockBootloader\root\
├── active_systemui.apk          # Pulled from active ROM via `adb pull`
├── active_settings.apk          # Pulled from active ROM via `adb pull`
├── active_decoded_systemui_full/ # apktool d active_systemui.apk (WORKING COPY)
├── active_decoded_settings_full/ # apktool d active_settings.apk (WORKING COPY)
├── decoded_systemui/             # Modded SystemUI (source of clock mods) — READ ONLY
├── decoded_settings/             # Modded Settings (source of ZK panel)  — READ ONLY
├── modded_systemui_smali/        # Smali extracted from modded SystemUI  — READ ONLY
├── modded_settings_smali/        # Smali extracted from modded Settings  — READ ONLY
├── tools/
│   ├── apktool_3.0.2.jar
│   └── uber-apk-signer-1.3.0.jar
├── merged_systemui.apk                       # Build output (unsigned)
├── merged_settings.apk                       # Build output (unsigned)
├── merged_systemui-aligned-debugSigned.apk   # Final signed APK
├── merged_settings-aligned-debugSigned.apk   # Final signed APK
└── hyperzk_module/                           # KernelSU module structure
    ├── module.prop
    ├── customize.sh
    └── system/system_ext/priv-app/
        ├── MiuiSystemUI/MiuiSystemUI.apk
        └── Settings/Settings.apk
```

---

## 3. What Gets Merged

### 3.1 SystemUI Modifications

| Resource | Source | Target | Notes |
|----------|--------|--------|-------|
| `res/layout/status_bar_clock.xml` | `decoded_systemui` | `active_decoded_systemui_full` | Multi-style custom clock layout |
| `res/drawable*/clock_bg*`, `Background*` | `decoded_systemui` | `active_decoded_systemui_full` | Clock background drawables |
| `smali/**/xLinearLayout*.smali` | `modded_systemui_smali` | `active_decoded_systemui_full` | Custom clock container view |
| `res/values/colors.xml` additions | `decoded_systemui` | `active_decoded_systemui_full` | `statusbar_back_clock`, `statusbar_back_clock_inv` |
| `res/layout/status_bar.xml` | PATCHED IN-PLACE | — | `<include layout="@layout/status_bar_clock" />` added before original clock; original MiuiClock hidden with 0dp |

### 3.2 Settings Modifications

| Resource | Source | Target | Notes |
|----------|--------|--------|-------|
| `res/xml/zk_*.xml` | `decoded_settings` | `active_decoded_settings_full` | Preference screens (zk_misc, zk_mods) |
| `res/layout/zk_*.xml` | `decoded_settings` | `active_decoded_settings_full` | Dashboard layout (zk_lab) |
| `res/font/*` | `decoded_settings` | `active_decoded_settings_full` | Custom font `zk` |
| `res/drawable*/zk_*` | `decoded_settings` | `active_decoded_settings_full` | Icons and backgrounds |
| `res/values/strings.xml` additions | `decoded_settings` | `active_decoded_settings_full` | 61 strings (zk_* + hyperzk) |
| `res/values/arrays.xml` additions | `decoded_settings` | `active_decoded_settings_full` | 5 arrays: `status_bar_entries/icons/values`, `zk_interval_entries/values` |
| `res/xml/settings_headers.xml` | PATCHED IN-PLACE | — | Added `<header android:id="@+id/zk_lab" android:fragment="zk.lab.zkMods" />` |
| `smali_classes13/zk/lab/*.smali` | `modded_settings_smali` | `active_decoded_settings_full` | ZK fragment classes (zkMods, zkMisc, etc.) |
| `smali_classes13/androidx/preference/XMiui*.smali`, `MyX*.smali` | `modded_settings_smali` | `active_decoded_settings_full` | Custom preference widgets |

### 3.3 Clock Style Control
- Setting key: `zk_clock_style` in system settings database
- Controlled by preference UI in Settings → HyperZK panel

---

## 4. AAPT2 Private Resource Fix Rules

When decompiling and recompiling MIUI/HyperOS APKs, AAPT2 rejects private framework attributes.
The `apply_private_fixes.py` script applies these transformations to ALL XML files (except `public.xml`):

### 4.1 Namespace Fixes
```
@android:     →  @*android:        (allow private resource access)
name="android:  →  name="*android:   (style items with android namespace)
?android:^attr-private/  →  ?android:attr/   (private attr lookups)
```

### 4.2 Private Attribute Mappings (both `android:attr/X` and `android:X` forms)

| Private Attribute | Mapped To |
|-------------------|-----------|
| `colorSurfaceVariant` | `colorBackground` |
| `colorSurfaceDim` | `colorBackground` |
| `colorSurfaceBright` | `colorBackground` |
| `colorSurfaceContainer` | `colorBackground` |
| `colorSurface` | `colorBackground` |
| `colorAccentPrimaryVariant` | `colorAccent` |
| `colorAccentPrimary` | `colorAccent` |
| `colorAccentSecondary` | `colorAccent` |
| `colorAccentTertiary` | `colorAccent` |
| `colorBackgroundHeader` | `colorBackground` |
| `textColorPrimaryActivated` | `textColorPrimary` |

### 4.3 Layout-Specific Fixes

| File | Fix |
|------|-----|
| `media_projection_app_selector.xml` | Remove `androidprv:maxFileSize="56.0dp"`, change `layout_childType="true"` → `"none"` |
| `zk_lab.xml` | Change `@id/zk2` → `@+id/zk2`, `@id/zk3` → `@+id/zk3`, remove `layout_constraintTop_toBottomOf="@id/zk1"` |

### 4.4 Manifest Fix
Remove `android:` prefix from `compileSdkVersion` and `compileSdkVersionCodename` attributes.

### 4.5 Important: Skip `public.xml`
Never modify `public.xml` — it contains resource ID declarations. Modifying it causes duplicate ID errors.

---

## 5. DEX Multi-Class Safety

The original Settings APK has 12 smali folders (`smali` through `smali_classes12`).
The first `smali` folder is near the DEX method reference limit (65536).

**Rule**: Always place custom ZK/XMiui smali classes in `smali_classes13` (or next available number)
to avoid "Unsigned short value out of range: 65578" DEX compilation errors.

---

## 6. ZK Mod Architecture — Why It's Safe

The ZK mod was designed for cross-ROM compatibility:

1. **Zero hardcoded resource IDs** — All 69 smali files use `Resources.getIdentifier(name, type, package)` 
   for runtime resource lookup instead of `0x7fXXXXXX` hex constants
2. **Parent class**: `Lcom/android/settings/MiuiSettingsPreferenceFragment;` — exists in all MIUI/HyperOS Settings APKs
3. **XMiui widgets** extend standard `androidx.preference.*` classes — always available
4. **Framework classes** (BroadcastReceiver, AsyncTask, ImageView) — provided by Android runtime

---

## 7. Build Pipeline (Automated Scripts)

All scripts are in the scratch directory. The pipeline is orchestrated by `reset_and_merge.py`:

### 7.1 Pipeline Steps
```
1. Delete old working directories (active_decoded_*_full)
2. Decompile active APKs:
   - java -jar tools/apktool_3.0.2.jar d active_systemui.apk -o active_decoded_systemui_full
   - java -jar tools/apktool_3.0.2.jar d active_settings.apk -o active_decoded_settings_full
3. Run merge_all.py → copies resources, smali, patches XMLs
4. Run apply_private_fixes.py → fixes AAPT2 private resource errors
5. Build SystemUI:
   - java -jar tools/apktool_3.0.2.jar b active_decoded_systemui_full -o merged_systemui.apk
6. Build Settings:
   - java -jar tools/apktool_3.0.2.jar b active_decoded_settings_full -o merged_settings.apk
7. Sign both APKs:
   - java -jar tools/uber-apk-signer-1.3.0.jar -a merged_systemui.apk merged_settings.apk
```

### 7.2 Script Files
| Script | Purpose |
|--------|---------|
| `reset_and_merge.py` | **Main orchestrator** — runs full pipeline |
| `merge_all.py` | Copies resources, smali, patches XML files |
| `apply_private_fixes.py` | Fixes AAPT2 private framework attribute errors |

### 7.3 Key Build Notes
- **apktool version**: 3.0.2 (must be this version for Android 15/16 SDK 36 support)
- **Framework**: Install framework first: `java -jar apktool.jar if active_systemui.apk`
- **Build time**: ~3-4 min for SystemUI, ~5-6 min for Settings (12+ DEX files)
- **Signing**: Debug key from `~/.android/debug.keystore`, v3 signature scheme

---

## 8. Module Packaging

### Structure
```
hyperzk_module/
├── module.prop          # id, name, version, author, description
├── customize.sh         # Installation UI messages
└── system/
    └── system_ext/
        └── priv-app/
            ├── MiuiSystemUI/MiuiSystemUI.apk
            └── Settings/Settings.apk
```

> [!WARNING]
> Do NOT use Windows PowerShell `Compress-Archive` to package the module. It creates ZIP archives with backslash (`\`) path separators, which Android/Linux treats as raw file names rather than subdirectories, breaking the KernelSU mount.
> Always use `zip_module.py` (which forces POSIX `/` separators) or 7-Zip/WinRAR to zip the `hyperzk_module` folder.

> [!IMPORTANT]
> If the device uses `meta-overlayfs` (common on KernelSU Next + SUSFS configurations to group module mounts), do NOT copy the module folders manually to `/data/adb/modules` using ADB. Since `meta-overlayfs` builds a merged file tree only for registered/active modules, manual copy directories will bypass registration and fail to mount. You must install the ZIP directly through the KernelSU Next manager app.


### Flashing
1. Run `python zip_module.py` to create the ZIP.
2. Copy `HyperZK_Clock_Mod_v1.0.zip` to device.
3. KernelSU Next → Modules → Install from storage.
4. Reboot.

### Recovery (if bootloop)
Boot recovery → delete `/data/adb/modules/hyperzk_clock_mod/`

---

## 9. Pulling Fresh APKs from Device

If you need to rebuild from a new ROM version:

```powershell
# Connect device
adb devices

# Pull SystemUI
adb pull /system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk active_systemui.apk

# Pull Settings
adb pull /system_ext/priv-app/Settings/Settings.apk active_settings.apk
```

---

## 10. Troubleshooting Common Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `resource android:attr/colorXxx is private` | AAPT2 rejects vendor private attrs | Add mapping in `apply_private_fixes.py` |
| `no definition for declared symbol` in `public.xml` | `apply_private_fixes.py` modified `public.xml` | Skip `public.xml` in the fix script |
| `Unsigned short value out of range: 65578` | DEX method limit exceeded | Move classes to `smali_classes13+` |
| `resource X not found` in layout | Missing resource (color, id, font, drawable) | Merge missing resource from modded APK |
| `@id/xxx not found` in custom layouts | ID not declared | Change `@id/` to `@+id/` for dynamic declaration |
| Bootloop after flash | Signature mismatch or resource crash | Remove module from recovery |
| Module files exist but files do not overlay (Settings/SystemUI unaltered) | Windows backslash `\` ZIP issue | Re-archive using `zip_module.py` or 7-Zip |
| HyperZK settings header not showing in main Settings page | MIUI whitelists headers dynamically | Inject ZKMods preference class `zk.lab.zkMods` inside `fold_screen_settings.xml` (NexiunOS-LAB) |


---

## 11. Key Decisions & Rationale

1. **Why not Iconify?** — Iconify uses Xposed hooks to modify SystemUI at runtime. The clock styles 
   from the modded ROM aren't present in the user's active ROM, so Iconify can't access them. Direct 
   APK modification is the only reliable approach.

2. **Why smali_classes13?** — The original Settings APK has 12 DEX files. The first `smali` folder 
   was at the 65k method reference limit. Adding classes there caused DEX overflow.

3. **Why debug signing?** — KernelSU module overlays replace the system APK at filesystem level 
   before Android boots, bypassing normal signature verification. Platform key not required.

4. **Why map private attrs to public ones?** — HyperOS/MIUI marks some standard Material Design 
   attributes as private. Since we can't include the vendor framework JAR, we map them to the 
   closest public equivalents. Visual differences are minimal.

---

## 12. Transition to LSPosed Integration (HyperCeiler)

### Rationale
Direct APK decompilation and re-signing for system UID 1000 processes (like Settings and SystemUI) is blocked on Enforcing SELinux environments by Zygote's platform signature verification checks. Because we do not possess Xiaomi's private platform key, the overlay APKs trigger JNI FatalErrors on startup.

To bypass this signature restriction while maintaining `Enforcing` SELinux for system security and app compatibility, the customization is transitioning from systemless APK overlays to runtime Xposed hooks.

### Implementation Path
1. **Target Repository**: [HyperCeiler-2.10.166](file:///d:/DEV/UnlockBootloader/root/HyperCeiler-2.10.166). HyperCeiler is an open-source LSPosed module targeting HyperOS/MIUI UI tweaks.
2. **Clock Hooking**: Implement hooks in HyperCeiler's SystemUI package targeting status bar clock layout creation (replacing layouts dynamically, inserting custom container views, and applying ZK font files).
3. **Database & UI Hooks**: Hook Settings package to insert the ZK Preference fragment UI directly, or use a custom companion app interface in HyperCeiler to toggle settings values that write directly to the settings provider database.

