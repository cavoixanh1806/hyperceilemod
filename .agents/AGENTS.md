# Agent Rules — ZK Clock Style Mod Workspace (HyperCeiler)

## Project Context
This workspace contains tools and resources for injecting custom **ZK Clock Styles** dynamically into the HyperOS/MIUI SystemUI using an Xposed/LSPosed hook inside the **HyperCeiler** module.

* All active development, configuration, and tools are consolidated inside the **`/root/n`** directory.
* **Working Source**: [n/HyperCeiler-2.10.166](file:///d:/DEV/UnlockBootloader/root/n/HyperCeiler-2.10.166)
* **Visual Designer**: [n/zk_clock_designer.html](file:///d:/DEV/UnlockBootloader/root/n/zk_clock_designer.html)
* **Old Reference Files**: [n/old_apk_mod_reference](file:///d:/DEV/UnlockBootloader/root/n/old_apk_mod_reference) (contains original decompiled Smali/XML files)

---

## Workspace Rules

1. **Pure Kotlin Xposed Hooks**: Do NOT decompile/modify system APKs directly. Implement status bar clock custom designs as dynamic Kotlin programmatical views inside `ZkClockStyleHook.kt` and `ZkClockDrawables.kt`.
2. **Layout Centering Wrapper**: Always wrap the clock layout container (`zkContainer`) in a vertical-centering wrapper (`LinearLayout` with height `MATCH_PARENT` and gravity `CENTER_VERTICAL`) before adding it to the status bar to prevent capsule background drawables from stretching to the full height of the status bar.
3. **No Negative Padding**: Avoid setting negative top padding values (e.g. `-1f`) inside clock style views, as Android will clip the rendering boundaries at the top of the text/pill. Instead, apply negative margins (e.g., `topMargin = -1dp`) to the layout parameters of the container.
4. **Clean Codebase Tracking**: Keep `gpr.properties`, `local.properties`, `signing.properties`, and `build/` directories ignored in the main repository's `.gitignore` to prevent leaking private GitHub tokens or credentials.
5. **Gradle Build**: Always compile using `.\gradlew.bat assembleDebug` and install on the target device via ADB.
6. **Detailed Architecture**: Refer to [.agents/knowledge/hyperzk_clock_mod_workflow.md](file:///d:/DEV/UnlockBootloader/root/n/.agents/knowledge/hyperzk_clock_mod_workflow.md) for full layout rules and drawable factory details.
7. **Branching & Stable Release Strategy**: All new features, layouts, and experimental code must be committed and tested on a dedicated `beta` branch (or development branch). Do NOT commit untested experimental code directly to the `main` branch. Only when the `beta` builds are fully verified as stable on the target device via ADB/reboot should they be merged into the `main` branch and published as a GitHub release.
