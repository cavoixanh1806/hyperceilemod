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

ZK Clock style customization is hooked at runtime inside the **HyperCeiler** Xposed library:

```
n/HyperCeiler-2.10.166/library/libhook/src/main/java/.../statusbar/clock/
├── ZkClockStyleHook.kt    # Main Xposed Hook entrypoint, view builder, and layouts
└── ZkClockDrawables.kt    # Programmatic factory for capsules, gradients, and ripple drawables
```

### 2.1 Settings Integration
The preferences are integrated into the HyperCeiler Settings framework under a dedicated section:
* **Dedicated Section**: **`cvxLAB`** (fragment `ZkClockStyleSettings`), positioned at the very top of the Status Bar settings screen (`system_ui_status_bar.xml`), directly above the **Icons** (`IconManageNewSettings`) menu.
* **Settings XML**: `library/core/src/main/res/xml/system_ui_statusbar_zk_clock_style.xml` (screen title `cvxLAB`).
* **Style Selector Dropdown**: `prefs_key_system_ui_statusbar_zk_clock_style_value` (mapped to dropdown previews).
* **Capsule Toggle**: `prefs_key_system_ui_statusbar_zk_clock_style_iconify_bg` (enables capsule background for Iconify styles).
* **Scale Slider (SeekBar)**: `prefs_key_system_ui_statusbar_zk_clock_style_scale` (customizes scale factor from 50% to 150%).

---

## 3. Visual Layout & Scaling Guidelines

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
* Add the actual clock style view (`zkContainer`) to this wrapper with layout parameters height set to `WRAP_CONTENT` (or a scaled fixed height like `22dp * scale` for capsule backgrounds):
  ```kotlin
  val containerHeight = if (style in setOf(1, 2, 24) || (style in 16..23 && zkClockStyleIconifyBg)) {
      dpToPx(context, 22f * zkClockStyleScale)
  } else {
      ViewGroup.LayoutParams.WRAP_CONTENT
  }
  ```

### 3.2 Dynamic Scale Slider
A seekbar preference provides scaling factors (50% to 150%). The hook scales all layout characteristics proportionally:
1. **Clock text size**: `scaledClockSizePx = systemClockSizePx * zkClockStyleScale`.
2. **Layout margins/paddings**: Intercepted in `createParams` and multiplied by `zkClockStyleScale`.
3. **Fixed components**: Separator lines, AnalogClock views, and avatar circle dimensions are multiplied by `zkClockStyleScale`.

### 3.3 Anti-Clipping Rule (No Negative Padding)
* **Problem**: Negative padding on view bounds causes clipping.
* **Rule**: Keep padding positive. To offset vertical height, apply negative top margins on layout parameters:
  ```kotlin
  if (style in setOf(4, 5, 6)) {
      topMargin = dpToPx(context, -1f * zkClockStyleScale)
  }
  ```

---

## 4. Dynamic Color Adaptation (Light/Dark Status Bar)

To keep custom text readable when the status bar background switches (e.g. from dark home screen wallpaper to a white app background):

### 4.1 Hooks
We hook `onDarkChanged(ArrayList areas, float darkIntensity, int tint)` and `setTextColor(int)` on `MiuiClock` to capture status bar color updates:
* The original clock continues to receive these callbacks while hidden, letting us capture the target color `tint`.

### 4.2 Tag-Based Recursive Colorization
We walk the hierarchy of the custom container and colorize views based on their tags:
* `"primary_text"`: Set to `tint` (light in dark mode, dark in light mode).
* `"accent_text"`: Set to `accent1_300` in dark mode, and high-contrast `accent1_600`/`0xFF1976D2` in light mode.
* `"separator_line"`: Dynamically updates background color matching the accent.
* `"avatar_circle"`: Dynamically updates the GradientDrawable color matching the accent.

---

## 5. Iconify Styles Merged (Styles 16 - 24)
Nine clock styles were extracted from Iconify QS Header Clock module and recreated programmatically:
* **Style 16 (Iconify 1)**: Time and Date separated by a vertical accent line.
* **Style 17 (Iconify 2)**: Vertical stacked Date/Time.
* **Style 18 (Iconify 3)**: Bold accent hour time.
* **Style 19 (Iconify 4)**: Small Analog Clock + Date column.
* **Style 20 (Iconify 5)**: Small Seconds aligned right-bottom.
* **Style 21 (Iconify 6)**: Avatar circle and italicized greeting.
* **Style 22 (Iconify 7)**: Overlapping accent minutes in bold italic.
* **Style 23 (Iconify 8)**: BebasStacked month, day name, and dotted time.
* **Style 24 (Iconify 9)**: Outer capsule with inner semi-transparent time badge.
