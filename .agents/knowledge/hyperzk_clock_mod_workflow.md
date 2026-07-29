# HyperZK Clock Mod — Complete Xposed Workflow Reference

> **Purpose**: This document captures the architecture, codebase hooks, layout constraints, and build configurations for dynamically injecting custom ZK Status Bar Clock Styles into HyperOS / MIUI SystemUI via the HyperCeiler LSPosed module, replacing the legacy decompilation & systemless overlay method.

---

## 1. Project Context

| Item | Value |
|------|-------|
| **Device** | POCO F6 (codename: `peridot`) |
| **OS** | HyperOS (MIUI-based, Android 15/16, SDK 36) |
| **Xposed Hook Target** | `com.android.systemui.statusbar.views.MiuiClock` |
| **Module Project** | [HyperCeiler-2.10.166](file:///d:/DEV/UnlockBootloader/root/n/HyperCeiler-2.10.166) |
| **Git Repository** | [GitHub Repo](https://github.com/cavoixanh1806/hyperceilemod) |
| **ADB Serial** | `3a2422d5` |

---

## 2. Architecture & Code Structure

Instead of decompiling and rebuilding the closed-source `MiuiSystemUI.apk`, the ZK Clock style customization is hooked at runtime inside the **HyperCeiler** Xposed library:

```
n/HyperCeiler-2.10.166/library/libhook/src/main/java/.../statusbar/clock/
├── ZkClockStyleHook.kt    # Main Xposed Hook entrypoint, view builder, and layouts
└── ZkClockDrawables.kt    # Programmatic factory for capsules, gradients, and ripple drawables
```

### 2.1 Settings Integration
The preferences toggle for the ZK Clock is integrated into the HyperCeiler Settings framework:
* **Settings XML**: `library/core/src/main/res/xml/system_ui_statusbar_zk_clock_style.xml`
* **Entry Keys**: `zk_clock_style` (Int database configuration: `0` for Disable, `1` - `15` for corresponding Styles).

### 2.2 Hook Mechanisms
`ZkClockStyleHook.kt` hooks into `com.android.systemui` package load, specifically targeted at the `MiuiClock` class constructor or inflation callback:
* The original `MiuiClock` (a custom TextView) is hidden (`visibility = View.GONE`, `textSize = 0f`, layout width/height zeroed out).
* The custom ZK layout view (`zkContainer`) is dynamically created in Kotlin code, then inserted into the parent container at the index position of the original clock view.

---

## 3. Visual Layout Guidelines

To ensure the clock layouts render beautifully across different device DPIs and status bar heights:

### 3.1 Vertical Centering Wrapper (Anti-Stretch)
If the parent container forces `MATCH_PARENT` height on the custom clock, any capsule backgrounds or borders will stretch from the top of the screen to the bottom of the status bar, causing layout distortion.
* **Solution**: Create a vertical centering `LinearLayout` wrapper:
  ```kotlin
  val wrapper = LinearLayout(context).apply {
      tag = "zk_clock_container"
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
  }
  ```
* Add the actual clock style view (`zkContainer`) to this wrapper with layout parameters height set to `WRAP_CONTENT` (or a fixed `22dp` height for Style 1 and 2):
  ```kotlin
  zkContainer.layoutParams = LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT,
      ViewGroup.LayoutParams.WRAP_CONTENT // or dpToPx(context, 22f)
  ).apply {
      gravity = Gravity.CENTER_VERTICAL
  }
  ```
* Apply the original clock layout parameters to the `wrapper` instead.

### 3.2 Anti-Clipping Rule (No Negative Padding)
* **Problem**: Applying negative top padding values (e.g. `setPadding(..., -1dp, ...)`) to view bounds causes the content to draw outside the view boundaries, which is clipped/cut off by the status bar's default `clipChildren=true` hierarchy.
* **Rule**: Always keep the top padding of the view positive (e.g. `0dp` or `0.5dp`). To nudge the clock layout upwards slightly (standard offset for Style 4, 5, 6), apply a negative top margin to the LayoutParams:
  ```kotlin
  if (style in setOf(4, 5, 6)) {
      topMargin = dpToPx(context, -1f)
  }
  ```

---

## 4. Programmatic Drawables (ZkClockDrawables.kt)

Background pills and gradients are generated dynamically in code using Android SDK's `GradientDrawable` and `RippleDrawable` to eliminate static XML asset dependencies:

* **Capsule Pills**:
  ```kotlin
  GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      setColor(backgroundColor)
      setStroke(dpToPxInt(ctx, 1f), Color.WHITE)
      cornerRadius = dpToPx(ctx, 15f)
  }
  ```
* **Asymmetric Pills** (Style 8, 9): Constructed with custom corner radii:
  ```kotlin
  cornerRadii = floatArrayOf(r1, r1, r2, r2, r1, r1, r2, r2)
  ```
* **System Color Adaptation**: Colors adapt dynamically using dynamic resources (e.g. `system_accent1_300`, `system_accent1_500`) parsed at runtime.

---

## 5. Visual Clock Designer (zk_clock_designer.html)

A custom HTML/CSS tool hosted in the workspace `n/zk_clock_designer.html` acts as a local WYSIWYG layout builder:
1. Simulates status bar height and light/dark theme backgrounds.
2. Supports dragging sliders to adjust text sizes, margin spacing, and EMS limits for Vietnamese and English locales.
3. Automatically exports copy-pasteable Kotlin code for the modified style rules.
