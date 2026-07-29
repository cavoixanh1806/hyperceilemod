---
name: hyperzk-clock-mod
description: |
  Merges custom status bar clock styles from a modded HyperOS ROM into the active
  cook ROM on POCO F6 (Peridot). Covers the full pipeline: APK decompilation,
  resource/smali merging, AAPT2 private attribute fixes, APK building/signing,
  and KernelSU module packaging. Use when the user mentions clock modding,
  status bar clock styles, ZK mod, HyperZK module, or merging SystemUI/Settings APKs.
---

# HyperZK Clock Mod Skill

This skill covers the end-to-end workflow for modifying HyperOS ROM APKs to integrate
custom status bar clock styles.

## When to Use
- User asks to rebuild or update the HyperZK clock module
- User wants to merge new clock styles into ROM APKs
- User encounters AAPT2 build errors during APK modification
- User wants to update the module for a new ROM version

## Full Documentation
Read the complete workflow reference at:
**`.agents/knowledge/hyperzk_clock_mod_workflow.md`**

This document covers:
1. Device & ROM context
2. Source file layout
3. What resources get merged (SystemUI + Settings)
4. AAPT2 private attribute fix rules (critical for build success)
5. DEX multi-class safety rules
6. ZK mod architecture & safety analysis
7. Build pipeline steps & automation scripts
8. Module packaging structure
9. Pulling fresh APKs from device
10. Troubleshooting common errors
11. Key decisions & rationale

## Quick Start (Rebuild from scratch)
```powershell
# 1. Pull fresh APKs (device must be connected)
adb pull /system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk active_systemui.apk
adb pull /system_ext/priv-app/Settings/Settings.apk active_settings.apk

# 2. Run the pipeline
python reset_and_merge.py

# 3. Package as module
# Copy signed APKs to hyperzk_module/system/system_ext/priv-app/*/
# Zip and flash via KernelSU
```
