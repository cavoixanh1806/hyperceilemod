/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */
package com.sevtinge.hyperceiler.libhook.rules.systemui.statusbar.clock

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AnalogClock
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextClock
import android.widget.TextView
import com.sevtinge.hyperceiler.common.utils.PrefsBridge
import com.sevtinge.hyperceiler.common.utils.prefs.PrefType
import com.sevtinge.hyperceiler.common.utils.prefs.PrefsChangeObserver
import com.sevtinge.hyperceiler.libhook.base.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.ConstructorFinder.`-Static`.constructorFinder
import io.github.kyuubiran.ezxhelper.core.finder.MethodFinder.`-Static`.methodFinder
import io.github.kyuubiran.ezxhelper.core.util.ClassUtil.loadClass
import io.github.kyuubiran.ezxhelper.xposed.dsl.HookFactory.`-Static`.createAfterHook
import com.sevtinge.hyperceiler.common.log.AndroidLog
import java.util.Collections
import java.util.WeakHashMap

/**
 * ZK Clock Style Hook
 *
 * Replaces the default MiuiClock in the status bar with custom clock layouts
 * featuring various backgrounds, date displays, and styling options.
 *
 * This hook reads the style preference from PrefsBridge and programmatically
 * constructs the appropriate clock layout, hiding the original MiuiClock.
 *
 * 16 styles (0 = default/off, 1-15 = custom styles)
 *
 * Original layout reference: decoded_systemui/res/layout/status_bar_clock.xml
 */
object ZkClockStyleHook : BaseHook() {

    private val statusBarClockClass by lazy {
        loadClass("com.android.systemui.statusbar.views.MiuiClock")
    }

    private val zkClockStyle: Int
        get() = PrefsBridge.getStringAsInt("system_ui_statusbar_zk_clock_style_value", 0)

    private val zkClockStyleIconifyBg: Boolean
        get() = PrefsBridge.getBoolean("system_ui_statusbar_zk_clock_style_iconify_bg", false)

    private val zkClockStyleScale: Float
        get() = PrefsBridge.getInt("system_ui_statusbar_zk_clock_style_scale", 100) / 100f

    /** Track which MiuiClock views we've already processed to avoid duplicate inserts */
    private val processedClocks: MutableSet<View> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    /** Store the original properties to restore them cleanly when switching/disabling styles */
    private val originalSizes = Collections.synchronizedMap(WeakHashMap<TextView, Float>())
    private val originalParams = Collections.synchronizedMap(WeakHashMap<TextView, ViewGroup.LayoutParams>())

    private var styleValueObserver: PrefsChangeObserver? = null
    private var styleEnableObserver: PrefsChangeObserver? = null
    private var styleIconifyBgObserver: PrefsChangeObserver? = null
    private var styleScaleObserver: PrefsChangeObserver? = null
    private var observersRegistered = false

    override fun init() {
        // Hook MiuiClock constructor (3 params: Context, AttributeSet?, int)
        statusBarClockClass.constructorFinder()
            .filterByParamCount(3)
            .filterByParamTypes {
                it[0] == Context::class.java
            }.first().createAfterHook { param ->
                runCatching {
                    val miuiClock = param.thisObject as TextView
                    val clockName = miuiClock.resources.getResourceEntryName(miuiClock.id)
                        ?: return@createAfterHook

                    // Only hook the status bar clock (portrait), not big_time/date_time/pad_clock
                    if (clockName != "clock") return@createAfterHook

                    // Avoid processing the same view twice
                    if (!processedClocks.add(miuiClock)) return@createAfterHook

                    // Register ContentObservers to update layouts instantly when preferences change
                    registerObservers(miuiClock.context)

                    // If already attached, inject immediately
                    if (miuiClock.isAttachedToWindow) {
                        injectZkClock(miuiClock, zkClockStyle)
                    }

                    // Post layout injection after the view is attached to window
                    miuiClock.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            injectZkClock(v as TextView, zkClockStyle)
                        }

                        override fun onViewDetachedFromWindow(v: View) {
                            processedClocks.remove(v)
                        }
                    })
                }
            }

        // Hook onDarkChanged to sync dynamic tinting
        runCatching {
            statusBarClockClass.methodFinder()
                .filterByName("onDarkChanged")
                .first().createAfterHook { param ->
                    val miuiClock = param.thisObject as TextView
                    val tint = param.args[2] as Int
                    updateCustomClockTint(miuiClock, tint)
                }
        }.onFailure { t ->
            AndroidLog.e("Failed to hook onDarkChanged: ${t.message}")
        }

        // Hook setTextColor to sync dynamic tinting
        runCatching {
            statusBarClockClass.methodFinder()
                .filterByName("setTextColor")
                .filterByParamTypes {
                    it.size == 1 && it[0] == Int::class.javaPrimitiveType
                }
                .first().createAfterHook { param ->
                    val miuiClock = param.thisObject as TextView
                    val color = param.args[0] as Int
                    updateCustomClockTint(miuiClock, color)
                }
        }.onFailure { t ->
            AndroidLog.e("Failed to hook setTextColor: ${t.message}")
        }
    }

    private fun registerObservers(context: Context) {
        if (observersRegistered) return
        observersRegistered = true

        val mainHandler = Handler(Looper.getMainLooper())

        styleValueObserver = object : PrefsChangeObserver(
            context, mainHandler, true, PrefType.String, "system_ui_statusbar_zk_clock_style_value", "0"
        ) {
            override fun onChange(type: PrefType?, uri: android.net.Uri?, name: String?, def: Any?) {
                updateAllClocks()
            }
        }

        styleEnableObserver = object : PrefsChangeObserver(
            context, mainHandler, true, PrefType.Boolean, "system_ui_statusbar_zk_clock_style_enable", false
        ) {
            override fun onChange(type: PrefType?, uri: android.net.Uri?, name: String?, def: Any?) {
                updateAllClocks()
            }
        }

        styleIconifyBgObserver = object : PrefsChangeObserver(
            context, mainHandler, true, PrefType.Boolean, "system_ui_statusbar_zk_clock_style_iconify_bg", false
        ) {
            override fun onChange(type: PrefType?, uri: android.net.Uri?, name: String?, def: Any?) {
                updateAllClocks()
            }
        }

        styleScaleObserver = object : PrefsChangeObserver(
            context, mainHandler, true, PrefType.Integer, "system_ui_statusbar_zk_clock_style_scale", 100
        ) {
            override fun onChange(type: PrefType?, uri: android.net.Uri?, name: String?, def: Any?) {
                updateAllClocks()
            }
        }
    }

    private fun updateAllClocks() {
        Handler(Looper.getMainLooper()).post {
            synchronized(processedClocks) {
                processedClocks.forEach { view ->
                    if (view is TextView) {
                        updateClockLayout(view)
                    }
                }
            }
        }
    }

    private fun updateClockLayout(originalClock: TextView) {
        val parent = originalClock.parent as? ViewGroup ?: return
        val context = originalClock.context

        // Find and remove existing ZK container wrapper
        val oldWrapper = parent.findViewWithTag<View>("zk_clock_container")
        if (oldWrapper != null) {
            parent.removeView(oldWrapper)
        }

        // Restore original clock properties
        val originalTextSize = originalSizes[originalClock] ?: originalClock.textSize
        val savedParams = originalParams[originalClock]

        originalClock.visibility = View.VISIBLE
        originalClock.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalTextSize)
        if (savedParams != null) {
            originalClock.layoutParams = savedParams
        } else {
            originalClock.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
            originalClock.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        originalClock.requestLayout()

        val enabled = PrefsBridge.getBoolean("system_ui_statusbar_zk_clock_style_enable", false)
        val style = if (enabled) zkClockStyle else 0

        if (style == 0) {
            return
        }

        // Inject the ZK layout again with the new preference values
        injectZkClock(originalClock, style)
    }

    /**
     * Inject a ZK clock layout into the status bar, hiding the original MiuiClock.
     */
    private fun injectZkClock(originalClock: TextView, style: Int) {
        if (style == 0) return

        val parent = originalClock.parent as? ViewGroup ?: return
        val context = originalClock.context

        // Check if we already injected (look for our tag)
        if (parent.findViewWithTag<View>("zk_clock_container") != null) return

        // Save original properties if not saved yet
        if (!originalSizes.containsKey(originalClock)) {
            originalSizes[originalClock] = originalClock.textSize
        }
        if (!originalParams.containsKey(originalClock)) {
            val lp = originalClock.layoutParams
            val copy = try {
                val constructor = lp.javaClass.getConstructor(lp.javaClass)
                constructor.newInstance(lp)
            } catch (_: Throwable) {
                lp
            }
            originalParams[originalClock] = copy
        }

        // 1. Get correct text size. Use 12dp fallback if invalid.
        val originalTextSize = originalSizes[originalClock] ?: originalClock.textSize
        val systemClockSizePx = if (originalTextSize > 1f) originalTextSize else dpToPxF(context, 12f)
        val scaledClockSizePx = systemClockSizePx * zkClockStyleScale

        // 2. Hide original clock
        originalClock.visibility = View.GONE
        originalClock.textSize = 0f
        originalClock.layoutParams.width = 0
        originalClock.layoutParams.height = 0

        // 3. Build the ZK clock layout
        val zkContainer = buildZkClockLayout(context, style, scaledClockSizePx) ?: return

        // Create a wrapper to center the container vertically and prevent stretching
        val wrapper = LinearLayout(context).apply {
            tag = "zk_clock_container"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Set zkContainer layout params to WRAP_CONTENT (or 22dp for style 1, 2, 24, or Iconify with bg)
        val containerHeight = if (style in setOf(1, 2, 24) || (style in 16..23 && zkClockStyleIconifyBg)) dpToPx(context, 22f * zkClockStyleScale) else ViewGroup.LayoutParams.WRAP_CONTENT
        zkContainer.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            containerHeight
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
            if (style in setOf(4, 5, 6)) {
                topMargin = dpToPx(context, -1f * zkClockStyleScale)
            }
        }

        wrapper.addView(zkContainer)

        // Use parent's layout parameters type to avoid ClassCastException
        val originalParams = originalClock.layoutParams
        
        val layoutParams = try {
            val constructor = originalParams.javaClass.getConstructor(originalParams.javaClass)
            constructor.newInstance(originalParams)
        } catch (_: Throwable) {
            try {
                val constructor = originalParams.javaClass.getConstructor(ViewGroup.LayoutParams::class.java)
                constructor.newInstance(originalParams)
            } catch (_: Throwable) {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }.apply {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
            
            // Preserve original margins and gravity where applicable
            if (this is ViewGroup.MarginLayoutParams && originalParams is ViewGroup.MarginLayoutParams) {
                this.leftMargin = originalParams.leftMargin
                this.topMargin = originalParams.topMargin
                this.rightMargin = originalParams.rightMargin
                this.bottomMargin = originalParams.bottomMargin
            }
            if (this is LinearLayout.LayoutParams && originalParams is LinearLayout.LayoutParams) {
                this.gravity = originalParams.gravity
                this.weight = originalParams.weight
            }
            if (this is FrameLayout.LayoutParams && originalParams is FrameLayout.LayoutParams) {
                this.gravity = originalParams.gravity
            }
        }

        // Insert before the original clock position
        val index = parent.indexOfChild(originalClock)
        parent.addView(wrapper, index, layoutParams)
    }

    /**
     * Build the complete ZK clock layout for the given style.
     * Each style mirrors the layout from status_bar_clock.xml.
     */
    private fun buildZkClockLayout(context: Context, style: Int, systemClockSizePx: Float): View? {
        val view = when (style) {
            1 -> buildStyle1(context, systemClockSizePx)
            2 -> buildStyle2(context, systemClockSizePx)
            3 -> buildStyle3(context, systemClockSizePx)
            4 -> buildStyle4(context, systemClockSizePx)
            5 -> buildStyle5(context, systemClockSizePx)
            6 -> buildStyle6(context, systemClockSizePx)
            7 -> buildStyle7(context, systemClockSizePx)
            8 -> buildStyle8(context, systemClockSizePx)
            9 -> buildStyle9(context, systemClockSizePx)
            10 -> buildStyle10(context, systemClockSizePx)
            11 -> buildStyle11(context, systemClockSizePx)
            12 -> buildStyle12(context, systemClockSizePx)
            13 -> buildStyle13(context, systemClockSizePx)
            14 -> buildStyle14(context, systemClockSizePx)
            15 -> buildStyle15(context, systemClockSizePx)
            16 -> buildStyle16(context, systemClockSizePx)
            17 -> buildStyle17(context, systemClockSizePx)
            18 -> buildStyle18(context, systemClockSizePx)
            19 -> buildStyle19(context, systemClockSizePx)
            20 -> buildStyle20(context, systemClockSizePx)
            21 -> buildStyle21(context, systemClockSizePx)
            22 -> buildStyle22(context, systemClockSizePx)
            23 -> buildStyle23(context, systemClockSizePx)
            24 -> buildStyle24(context, systemClockSizePx)
            else -> null
        }
        if (view != null && style in 16..23 && zkClockStyleIconifyBg) {
            view.background = createDarkCapsule(context)
            view.setPadding(dpToPx(context, 7f * zkClockStyleScale), 0, dpToPx(context, 7f * zkClockStyleScale), 0)
        }
        return view
    }

    // ==================== Helper Methods ====================

    private fun createDarkCapsule(context: Context): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(0x4D000000) // ~30% alpha black
            cornerRadius = dpToPxF(context, 15f * zkClockStyleScale)
        }
    }

    private fun updateCustomClockTint(miuiClock: TextView, tint: Int) {
        val parent = miuiClock.parent as? ViewGroup ?: return
        var customContainer: View? = null
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.tag == "zk_clock_container") {
                customContainer = child
                break
            }
        }
        if (customContainer == null) return

        val context = miuiClock.context
        val isDark = isTintDark(tint)
        val primaryColor = if (isDark) tint else Color.WHITE
        val accentColor = if (isDark) {
            resolveColor(context, "accent1_600", 0xFF1976D2.toInt())
        } else {
            resolveColor(context, "accent1_300", 0xFF7CACF8.toInt())
        }

        // Dynamically update the capsule background if enabled
        val containerGroup = customContainer as? ViewGroup
        if (containerGroup != null && containerGroup.childCount > 0) {
            val zkContainer = containerGroup.getChildAt(0)
            val style = zkClockStyle
            if (zkContainer != null && style in 16..23 && zkClockStyleIconifyBg) {
                if (isDark) {
                    // LIGHT mode: use solid light accent capsule
                    zkContainer.background = ZkClockDrawables.getContainerBackground(1, context)
                } else {
                    // DARK mode: use dark semi-transparent capsule
                    zkContainer.background = createDarkCapsule(context)
                }
                zkContainer.setPadding(dpToPx(context, 7f * zkClockStyleScale), 0, dpToPx(context, 7f * zkClockStyleScale), 0)
            }
        }

        updateTintRecursively(customContainer, primaryColor, accentColor)
    }

    private fun isTintDark(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b
        return luminance < 120f
    }

    private fun updateTintRecursively(view: View, primaryColor: Int, accentColor: Int) {
        when (view.tag as? String) {
            "primary_text" -> {
                if (view is TextView) {
                    view.setTextColor(primaryColor)
                }
            }
            "accent_text" -> {
                if (view is TextView) {
                    view.setTextColor(accentColor)
                }
            }
            "separator_line" -> {
                view.setBackgroundColor(accentColor)
            }
            "avatar_circle" -> {
                if (view is ImageView) {
                    val drawable = view.drawable
                    if (drawable is GradientDrawable) {
                        drawable.setColor(accentColor)
                    }
                }
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                updateTintRecursively(view.getChildAt(i), primaryColor, accentColor)
            }
        }
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics
        ).toInt()
    }

    private fun dpToPxF(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics
        )
    }

    private fun createTextClock(
        context: Context,
        format12: String,
        format24: String,
        textSizePx: Float,
        textColor: Int = Color.WHITE,
        bold: Boolean = true,
        singleLine: Boolean = true,
        gravity: Int = Gravity.START or Gravity.CENTER_VERTICAL,
        maxEms: Int = 0
    ): TextClock {
        return TextClock(context).apply {
            format12Hour = format12
            format24Hour = format24
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            setTextColor(textColor)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            isSingleLine = singleLine
            this.gravity = gravity
            if (maxEms > 0) this.maxEms = maxEms
        }
    }

    private fun resolveColor(context: Context, colorName: String, fallback: Int): Int {
        return ZkClockDrawables.run {
            when (colorName) {
                "accent1_200" -> accent1_200(context)
                "accent1_300" -> accent1_300(context)
                "accent1_500" -> accent1_500(context)
                "accent1_600" -> accent1_600(context)
                "accent1_800" -> accent1_800(context)
                "accent1_100" -> accent1_100(context)
                "accent2_300" -> accent2_300(context)
                else -> fallback
            }
        }
    }

    /**
     * Resolve statusbar_back_clock — this was a custom color in modded SystemUI
     * that auto-adapts between light/dark. We approximate it from system_accent1_100.
     */
    private fun statusbarBackClock(context: Context): Int {
        return resolveColor(context, "accent1_100", 0xFFD3E3FD.toInt())
    }

    private fun createParams(
        context: Context,
        leftDp: Float = 0f,
        topDp: Float = 0f,
        rightDp: Float = 0f,
        bottomDp: Float = 0f,
        gravity: Int = Gravity.START or Gravity.CENTER_VERTICAL
    ): LinearLayout.LayoutParams {
        val scale = zkClockStyleScale
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dpToPx(context, leftDp * scale)
            topMargin = dpToPx(context, topDp * scale)
            rightMargin = dpToPx(context, rightDp * scale)
            bottomMargin = dpToPx(context, bottomDp * scale)
            this.gravity = gravity
        }
    }

    // ==================== Style Builders ====================

    /**
     * Style 1: MiuiClock-like TextClock with clock_bg1 (accent pill, 15dp radius)
     */
    private fun buildStyle1(context: Context, systemClockSizePx: Float): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(context, 22f)
            )
            val clock = createTextClock(context, "hh:mm", "HH:mm", textSizePx = systemClockSizePx).apply {
                // Apply original padding from clock_bg1.xml: left 7dp, right 7dp
                setPadding(dpToPx(context, 7f), 0, dpToPx(context, 7f), 0)
            }
            clock.background = ZkClockDrawables.getContainerBackground(1, context)
            addView(clock, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
    }

    /**
     * Style 2: TextClock with clock_bg2 (asymmetric accent pill)
     */
    private fun buildStyle2(context: Context, systemClockSizePx: Float): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(context, 22f)
            )
            val clock = createTextClock(context, "hh:mm", "HH:mm", textSizePx = systemClockSizePx).apply {
                // Apply original padding from clock_bg2.xml: left 6dp, top 0.8dp, right 6dp, bottom 0.8dp
                setPadding(dpToPx(context, 6f), dpToPx(context, 0.8f), dpToPx(context, 6f), dpToPx(context, 0.8f))
            }
            clock.background = ZkClockDrawables.getContainerBackground(2, context)
            addView(clock, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
    }

    /**
     * Style 3: Dual column — hours/minutes stacked left + day name/date right,
     * Background3 outer + Background4 inner
     */
    private fun buildStyle3(context: Context, systemClockSizePx: Float): View {
        val accent800 = resolveColor(context, "accent1_800", 0xFF0842A0.toInt())

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(3, context)
            // Background3 padding: right 5dp
            setPadding(0, 0, dpToPx(context, 5f), 0)

            // Left column: HH / mm stacked
            val leftCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = ZkClockDrawables.getInnerTimeBackground(3, context)
                // Background4 padding: left 5dp, right 5dp
                setPadding(dpToPx(context, 5f), 0, dpToPx(context, 5f), 0)

                addView(createTextClock(context, "hh", "HH", textSizePx = systemClockSizePx * (10f / 12f), textColor = Color.RED),
                    createParams(context, topDp = -2f)
                )
                addView(createTextClock(context, "mm", "mm", textSizePx = systemClockSizePx * (10f / 12f), textColor = 0xEE000000.toInt()),
                    createParams(context, topDp = -4f)
                )
            }
            addView(leftCol)

            // Right column: EEEE / dd/MM/yyyy
            val rightCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(createTextClock(context, " EEEE", " EEEE", textSizePx = systemClockSizePx * (10f / 12f), textColor = accent800, maxEms = 6).apply {
                    gravity = Gravity.START
                }, createParams(context, topDp = -1f))
                addView(createTextClock(context, "dd/MM/yyyy", "dd/MM/yyyy", textSizePx = systemClockSizePx * (8.5f / 12f), textColor = accent800).apply {
                    gravity = Gravity.START
                }, createParams(context, topDp = -4f))
            }
            addView(rightCol, createParams(context, leftDp = 3f))
        }
    }

    /**
     * Style 4: Horizontal bar — time | date, clock_bg4b background
     */
    private fun buildStyle4(context: Context, systemClockSizePx: Float): View {
        val backClock = statusbarBackClock(context)
        val accent200 = resolveColor(context, "accent1_200", 0xFFA8C7FA.toInt())

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(4, context)
            // clock_bg4b padding: left 2dp, bottom 1dp, right 2dp
            // Plus original layout offset: top -1dp, right 1dp
            setPadding(dpToPx(context, 2f), dpToPx(context, 0.5f), dpToPx(context, 3f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm", "HH:mm", textSizePx = systemClockSizePx * (13f / 12f), textColor = backClock),
                createParams(context, topDp = 0.9f)
            )
            addView(createTextClock(context, "a", "", textSizePx = systemClockSizePx * (7f / 12f), textColor = backClock),
                createParams(context, leftDp = 2f, topDp = 1.8f)
            )
            addView(TextView(context).apply {
                text = "|"
                setTextSize(TypedValue.COMPLEX_UNIT_PX, systemClockSizePx * (9f / 12f))
                setTextColor(accent200)
                typeface = Typeface.DEFAULT_BOLD
            }, createParams(context, leftDp = 2f, topDp = 0.5f))
            
            addView(createTextClock(context, "dd MMMM", "dd MMMM", textSizePx = systemClockSizePx * (13f / 12f), textColor = backClock, singleLine = false),
                createParams(context, leftDp = 2f, topDp = 0.5f)
            )
        }
    }

    /**
     * Style 5: Background1 outer + Background2 inner time + date/day side
     */
    private fun buildStyle5(context: Context, systemClockSizePx: Float): View {
        val accent600 = resolveColor(context, "accent1_600", 0xFF1B6EF3.toInt())

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(5, context)
            // Background1 padding: right 5dp, layout: top -1dp
            setPadding(0, dpToPx(context, 0.5f), dpToPx(context, 5f), 0)

            // Inner time badge
            val timeBadge = LinearLayout(context).apply {
                background = ZkClockDrawables.getInnerTimeBackground(5, context)
                // Background2 padding: left 5dp, top 0.5dp, right 5dp, bottom 1dp
                setPadding(dpToPx(context, 5f), dpToPx(context, 0.5f), dpToPx(context, 5f), dpToPx(context, 1f))
                
                addView(createTextClock(context, "hh:mm:ss", "HH:mm:ss", textSizePx = systemClockSizePx))
            }
            addView(timeBadge)

            // Day/month text
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizePx = systemClockSizePx * (6.5f / 12f), textColor = accent600, singleLine = false, maxEms = 2),
                createParams(context, leftDp = 3f)
            )
            // Day number
            addView(createTextClock(context, "dd", "dd", textSizePx = systemClockSizePx, textColor = accent600),
                createParams(context, leftDp = 1.5f)
            )
        }
    }

    /**
     * Style 6: Red gradient bg, white text — time | date
     */
    private fun buildStyle6(context: Context, systemClockSizePx: Float): View {
        val accent200 = resolveColor(context, "accent1_200", 0xFFA8C7FA.toInt())

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(6, context)
            // clock_bg6 padding: left 2.5dp, top -0.5dp, right 7dp, bottom 1dp
            // Plus original layout offset: top -1dp, right 1dp
            setPadding(dpToPx(context, 2.5f), dpToPx(context, 0.5f), dpToPx(context, 8f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm", "HH:mm", textSizePx = systemClockSizePx * (13f / 12f), textColor = Color.WHITE),
                createParams(context, topDp = 0.9f)
            )
            addView(createTextClock(context, "a", "", textSizePx = systemClockSizePx * (7f / 12f), textColor = Color.WHITE),
                createParams(context, leftDp = 2f, topDp = 1.8f)
            )
            addView(TextView(context).apply {
                text = "|"
                setTextSize(TypedValue.COMPLEX_UNIT_PX, systemClockSizePx * (9f / 12f))
                setTextColor(accent200)
                typeface = Typeface.DEFAULT_BOLD
            }, createParams(context, leftDp = 2f, topDp = 0.5f))
            
            addView(createTextClock(context, "dd MMMM", "dd MMMM", textSizePx = systemClockSizePx * (13f / 12f), textColor = Color.WHITE, singleLine = false),
                createParams(context, leftDp = 2f, topDp = 0.5f)
            )
        }
    }

    /**
     * Style 7: accent1_200 asymmetric bg, time + accent gradient inner + date
     */
    private fun buildStyle7(context: Context, systemClockSizePx: Float): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(7, context)
            // clock_bg7 padding: left 2dp, top 1dp, right 2dp, bottom 1dp
            setPadding(dpToPx(context, 2f), dpToPx(context, 1f), dpToPx(context, 2f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizePx = systemClockSizePx * (13f / 12f), textColor = Color.BLACK).apply {
                background = ZkClockDrawables.getInnerTimeBackground(7, context)
                // clock_bg10 padding: left 2dp, right 2dp
                setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), 0)
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizePx = systemClockSizePx * (7.5f / 12f), textColor = Color.BLACK, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizePx = systemClockSizePx * (13f / 12f), textColor = Color.BLACK),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 8: Black asymmetric bg, time + accent inner + date
     */
    private fun buildStyle8(context: Context, systemClockSizePx: Float): View {
        val backClock = statusbarBackClock(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(8, context)
            // clock_bg8 padding: left 6dp, top 0dp, right 6dp, bottom 1dp
            setPadding(dpToPx(context, 6f), 0, dpToPx(context, 6f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizePx = systemClockSizePx * (13f / 12f), textColor = Color.BLACK).apply {
                background = ZkClockDrawables.getInnerTimeBackground(8, context)
                // clock_bg7 padding: left 2dp, top 1dp, right 2dp, bottom 1dp
                setPadding(dpToPx(context, 2f), dpToPx(context, 1f), dpToPx(context, 2f), dpToPx(context, 1f))
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizePx = systemClockSizePx * (7.5f / 12f), textColor = backClock, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizePx = systemClockSizePx * (13f / 12f), textColor = backClock),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 9: accent bg + black inner time + date
     */
    private fun buildStyle9(context: Context, systemClockSizePx: Float): View {
        val backClock = statusbarBackClock(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(9, context)
            // clock_bg7 padding: left 2dp, top 1dp, right 2dp, bottom 1dp
            setPadding(dpToPx(context, 2f), dpToPx(context, 1f), dpToPx(context, 2f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizePx = systemClockSizePx * (13f / 12f), textColor = backClock).apply {
                background = ZkClockDrawables.getInnerTimeBackground(9, context)
                // clock_bg9 padding: left 6dp, top 1dp, right 6dp, bottom 1dp
                setPadding(dpToPx(context, 6f), dpToPx(context, 1f), dpToPx(context, 6f), dpToPx(context, 1f))
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizePx = systemClockSizePx * (7.5f / 12f), textColor = Color.BLACK, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizePx = systemClockSizePx * (13f / 12f), textColor = Color.BLACK),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 10: Black bg + accent gradient inner + accent date
     */
    private fun buildStyle10(context: Context, systemClockSizePx: Float): View {
        val clockColor = resolveColor(context, "accent1_100", 0xFFD3E3FD.toInt())
        val backClock = statusbarBackClock(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(10, context)
            // clock_bg4b padding: left 2dp, top 0dp, right 2dp, bottom 1dp
            setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizePx = systemClockSizePx * (13f / 12f), textColor = clockColor).apply {
                background = ZkClockDrawables.getInnerTimeBackground(10, context)
                // clock_bg10 padding: left 2dp, right 2dp
                setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), 0)
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizePx = systemClockSizePx * (7.5f / 12f), textColor = backClock, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizePx = systemClockSizePx * (13f / 12f), textColor = resolveColor(context, "accent1_100", 0xFFD3E3FD.toInt())),
                createParams(context, leftDp = 2f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 11: White pill + black inner pill + red day + date
     */
    private fun buildStyle11(context: Context, systemClockSizePx: Float): View {
        val backClock = statusbarBackClock(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(11, context)
            // clock_bg4 padding: left 6dp, top 0.8dp, right 6dp, bottom 0.8dp
            setPadding(dpToPx(context, 6f), dpToPx(context, 0.8f), dpToPx(context, 6f), dpToPx(context, 0.8f))

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizePx = systemClockSizePx * (13f / 12f), textColor = backClock).apply {
                background = ZkClockDrawables.getInnerTimeBackground(11, context)
                // clock_bg4b padding: left 2dp, bottom 1dp, right 2dp
                setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), dpToPx(context, 1f))
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizePx = systemClockSizePx * (7.5f / 12f), textColor = Color.BLACK, singleLine = false, maxEms = 2),
                createParams(context, leftDp = 2f, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizePx = systemClockSizePx * (13f / 12f), textColor = Color.RED),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 12: Black bg + orange gradient inner + accent date
     */
    private fun buildStyle12(context: Context, systemClockSizePx: Float): View {
        val backClock = statusbarBackClock(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(12, context)
            // clock_bg4b padding: left 2dp, top 0dp, right 2dp, bottom 1dp
            setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizePx = systemClockSizePx * (13f / 12f), textColor = backClock).apply {
                background = ZkClockDrawables.getInnerTimeBackground(12, context)
                // clock_bg12 padding: right 2dp
                setPadding(0, 0, dpToPx(context, 2f), 0)
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizePx = systemClockSizePx * (7.5f / 12f), textColor = backClock, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizePx = systemClockSizePx * (13f / 12f), textColor = backClock),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 13: Green gradient bg + accent inner + accent date
     */
    private fun buildStyle13(context: Context, systemClockSizePx: Float): View {
        val backClock = statusbarBackClock(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(13, context)
            // clock_bg11 padding: right 2dp
            setPadding(0, 0, dpToPx(context, 2f), 0)

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizePx = systemClockSizePx * (13f / 12f), textColor = backClock).apply {
                background = ZkClockDrawables.getInnerTimeBackground(13, context)
                // clock_bg10 padding: left 2dp, right 2dp
                setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), 0)
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizePx = systemClockSizePx * (7.5f / 12f), textColor = backClock, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizePx = systemClockSizePx * (13f / 12f), textColor = backClock),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 14: Black bg + white inner pill + accent date
     */
    private fun buildStyle14(context: Context, systemClockSizePx: Float): View {
        val backClock = statusbarBackClock(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(14, context)
            // clock_bg4b padding: left 2dp, top 0dp, right 2dp, bottom 1dp
            setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizePx = systemClockSizePx * (13f / 12f), textColor = Color.BLACK).apply {
                background = ZkClockDrawables.getInnerTimeBackground(14, context)
                // clock_bg4 padding: left 6dp, top 0.8dp, right 6dp, bottom 0.8dp
                setPadding(dpToPx(context, 6f), dpToPx(context, 0.8f), dpToPx(context, 6f), dpToPx(context, 0.8f))
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizePx = systemClockSizePx * (7.5f / 12f), textColor = backClock, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizePx = systemClockSizePx * (13f / 12f), textColor = backClock),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 15: Accent gradient bg + orange gradient inner + white text
     */
    private fun buildStyle15(context: Context, systemClockSizePx: Float): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(15, context)
            // clock_bg10 padding: left 2dp, right 2dp
            setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), 0)

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizePx = systemClockSizePx * (13f / 12f), textColor = Color.WHITE).apply {
                background = ZkClockDrawables.getInnerTimeBackground(15, context)
                // clock_bg12 padding: right 2dp
                setPadding(0, 0, dpToPx(context, 2f), 0)
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizePx = systemClockSizePx * (7.5f / 12f), textColor = Color.WHITE, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizePx = systemClockSizePx * (13f / 12f), textColor = Color.WHITE),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 16: Iconify Style 1 (Horizontal with vertical accent line separator)
     */
    private fun buildStyle16(context: Context, systemClockSizePx: Float): View {
        val accentColor = resolveColor(context, "accent1_300", 0xFF7CACF8.toInt())
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            // Left: Time TextClock
            addView(createTextClock(context, "hh:mm", "HH:mm", textSizePx = systemClockSizePx, bold = true).apply {
                tag = "primary_text"
            }, createParams(context, rightDp = 2f))

            // Middle: Vertical Separator Line
            addView(View(context).apply {
                tag = "separator_line"
                val lineParams = LinearLayout.LayoutParams(dpToPx(context, 1.5f * zkClockStyleScale), dpToPx(context, 14f * zkClockStyleScale)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    leftMargin = dpToPx(context, 2f * zkClockStyleScale)
                    rightMargin = dpToPx(context, 2f * zkClockStyleScale)
                }
                layoutParams = lineParams
                setBackgroundColor(accentColor)
            })

            // Right: Date column
            val dateCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                
                addView(createTextClock(context, "EEEE", "EEEE", textSizePx = systemClockSizePx * 0.65f, textColor = accentColor, bold = true).apply {
                    tag = "accent_text"
                }, createParams(context, topDp = -1f))
                
                addView(createTextClock(context, "dd/MM", "dd/MM", textSizePx = systemClockSizePx * 0.55f, bold = true).apply {
                    tag = "primary_text"
                }, createParams(context, topDp = -3f))
            }
            addView(dateCol, createParams(context, leftDp = 2f))
        }
    }

    /**
     * Style 17: Iconify Style 2 (Vertical Stacked Date/Time)
     */
    private fun buildStyle17(context: Context, systemClockSizePx: Float): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            addView(createTextClock(context, "hh:mm", "HH:mm", textSizePx = systemClockSizePx, bold = true).apply {
                tag = "primary_text"
            }, createParams(context, topDp = -1f, gravity = Gravity.CENTER_HORIZONTAL))
            
            addView(createTextClock(context, "EEEE, dd/MM", "EEEE, dd/MM", textSizePx = systemClockSizePx * 0.65f, bold = true).apply {
                tag = "primary_text"
            }, createParams(context, topDp = -3f, gravity = Gravity.CENTER_HORIZONTAL))
        }
    }

    /**
     * Style 18: Iconify Style 3 (Accent Hours)
     */
    private fun buildStyle18(context: Context, systemClockSizePx: Float): View {
        val accentColor = resolveColor(context, "accent1_300", 0xFF7CACF8.toInt())
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            // Time hours in accent
            addView(createTextClock(context, "h", "HH", textSizePx = systemClockSizePx, textColor = accentColor, bold = true).apply {
                tag = "accent_text"
            })
            // Time minutes in white/primary
            addView(createTextClock(context, ":mm", ":mm", textSizePx = systemClockSizePx, bold = true).apply {
                tag = "primary_text"
            })

            // Date text in all caps
            addView(createTextClock(context, " EEE d MMM", " EEE d MMM", textSizePx = systemClockSizePx * 0.75f, bold = true).apply {
                tag = "primary_text"
                isAllCaps = true
            }, createParams(context, leftDp = 4f, topDp = 0.5f))
        }
    }

    /**
     * Style 19: Iconify Style 4 (Analog Clock + Date)
     */
    private fun buildStyle19(context: Context, systemClockSizePx: Float): View {
        val accentColor = resolveColor(context, "accent1_300", 0xFF7CACF8.toInt())
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            // Tiny Analog Clock (safely wrapped)
            val clockView = try {
                AnalogClock(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(context, 16f * zkClockStyleScale), dpToPx(context, 16f * zkClockStyleScale)).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        rightMargin = dpToPx(context, 4f * zkClockStyleScale)
                    }
                }
            } catch (_: Throwable) {
                TextView(context).apply {
                    text = "🕒"
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, systemClockSizePx)
                }
            }
            addView(clockView)

            // Date column
            val dateCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL

                addView(createTextClock(context, "EEEE", "EEEE", textSizePx = systemClockSizePx * 0.65f, textColor = accentColor, bold = true).apply {
                    tag = "accent_text"
                }, createParams(context, topDp = -1f))
                
                addView(createTextClock(context, "dd/MM", "dd/MM", textSizePx = systemClockSizePx * 0.55f, bold = true).apply {
                    tag = "primary_text"
                }, createParams(context, topDp = -3f))
            }
            addView(dateCol, createParams(context, leftDp = 2f))
        }
    }

    /**
     * Style 20: Iconify Style 5 (Small Seconds)
     */
    private fun buildStyle20(context: Context, systemClockSizePx: Float): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            // Time hours/minutes
            addView(createTextClock(context, "hh:mm", "HH:mm", textSizePx = systemClockSizePx, bold = true).apply {
                tag = "primary_text"
            })

            // Seconds text with small scale and alpha
            addView(createTextClock(context, "ss", "ss", textSizePx = systemClockSizePx * 0.65f, bold = true).apply {
                tag = "primary_text"
                alpha = 0.6f
            }, createParams(context, leftDp = 2f, topDp = 1.5f, gravity = Gravity.BOTTOM))
        }
    }

    /**
     * Style 21: Iconify Style 6 (Avatar + Greeting)
     */
    private fun buildStyle21(context: Context, systemClockSizePx: Float): View {
        val accentColor = resolveColor(context, "accent1_300", 0xFF7CACF8.toInt())
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            // Avatar circle
            addView(ImageView(context).apply {
                tag = "avatar_circle"
                layoutParams = LinearLayout.LayoutParams(dpToPx(context, 14f * zkClockStyleScale), dpToPx(context, 14f * zkClockStyleScale)).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    rightMargin = dpToPx(context, 4f * zkClockStyleScale)
                }
                setImageDrawable(GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accentColor)
                })
            })

            // Hello + Time stacked
            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL

                addView(TextView(context).apply {
                    tag = "accent_text"
                    text = "Hello!"
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, systemClockSizePx * 0.55f)
                    setTextColor(accentColor)
                    typeface = Typeface.defaultFromStyle(Typeface.ITALIC)
                }, createParams(context, topDp = -1f))

                val timeRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    
                    addView(TextView(context).apply {
                        tag = "primary_text"
                        text = "It's "
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, systemClockSizePx * 0.7f)
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                    })
                    addView(createTextClock(context, "hh:mm", "HH:mm", textSizePx = systemClockSizePx * 0.7f, bold = true).apply {
                        tag = "primary_text"
                    })
                }
                addView(timeRow, createParams(context, topDp = -3f))
            }
            addView(textCol)
        }
    }

    /**
     * Style 22: Iconify Style 7 (Accent Minutes)
     */
    private fun buildStyle22(context: Context, systemClockSizePx: Float): View {
        val accentColor = resolveColor(context, "accent1_300", 0xFF7CACF8.toInt())
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            // Hours (white)
            addView(createTextClock(context, "hh ", "HH ", textSizePx = systemClockSizePx, bold = true).apply {
                tag = "primary_text"
                typeface = Typeface.defaultFromStyle(Typeface.BOLD_ITALIC)
            })
            // Minutes (accent)
            addView(createTextClock(context, "mm", "mm", textSizePx = systemClockSizePx, textColor = accentColor, bold = true).apply {
                tag = "accent_text"
                typeface = Typeface.defaultFromStyle(Typeface.BOLD_ITALIC)
            }, createParams(context, leftDp = -3f)) // overlap slightly

            // Date
            addView(createTextClock(context, "dd/MM", "dd/MM", textSizePx = systemClockSizePx * 0.75f, bold = true).apply {
                tag = "primary_text"
                typeface = Typeface.DEFAULT_BOLD
            }, createParams(context, leftDp = 6f, topDp = 0.5f))
        }
    }

    /**
     * Style 23: Iconify Style 8 (Bebas Stacked)
     */
    private fun buildStyle23(context: Context, systemClockSizePx: Float): View {
        val accentColor = resolveColor(context, "accent1_300", 0xFF7CACF8.toInt())
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            // Month (Bebas type stacked)
            addView(createTextClock(context, "MMM", "MMM", textSizePx = systemClockSizePx * 0.6f, bold = true).apply {
                tag = "primary_text"
                alpha = 0.7f
                isAllCaps = true
            }, createParams(context, topDp = -1f, gravity = Gravity.CENTER_HORIZONTAL))

            // Day (Futurist type stacked)
            addView(createTextClock(context, "EEEE", "EEEE", textSizePx = systemClockSizePx * 0.75f, textColor = accentColor, bold = true).apply {
                tag = "accent_text"
                isAllCaps = true
            }, createParams(context, topDp = -3f, gravity = Gravity.CENTER_HORIZONTAL))

            // Time
            addView(createTextClock(context, "• hh:mm •", "• HH:mm •", textSizePx = systemClockSizePx * 0.6f, bold = true).apply {
                tag = "primary_text"
                alpha = 0.7f
            }, createParams(context, topDp = -3f, gravity = Gravity.CENTER_HORIZONTAL))
        }
    }

    /**
     * Style 24: Iconify Style 9 (Gradient Capsule)
     */
    private fun buildStyle24(context: Context, systemClockSizePx: Float): View {
        val containerBg = ZkClockDrawables.getContainerBackground(1, context) // Pill with accent color
        
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = containerBg
            // Capsule padding: left 3dp, right 5dp
            setPadding(dpToPx(context, 3f * zkClockStyleScale), 0, dpToPx(context, 5f * zkClockStyleScale), 0)

            // Inner Time capsule (dark semi-transparent black background)
            val timeBadge = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(0x3A000000)
                    cornerRadius = dpToPxF(context, 10f * zkClockStyleScale)
                }
                // Padding inside time badge: left 5dp, right 5dp
                setPadding(dpToPx(context, 5f * zkClockStyleScale), 0, dpToPx(context, 5f * zkClockStyleScale), 0)

                addView(createTextClock(context, "hh:mm", "HH:mm", textSizePx = systemClockSizePx * 0.95f, textColor = Color.WHITE, bold = true))
            }
            addView(timeBadge, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(context, 17f * zkClockStyleScale) // Keep it slightly smaller than outer 22dp container
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
            })

            // Date text beside the time badge
            addView(createTextClock(context, "EEE, dd/MM", "EEE, dd/MM", textSizePx = systemClockSizePx * 0.7f, textColor = Color.WHITE, bold = true).apply {
                tag = "primary_text"
            }, createParams(context, leftDp = 4f))
        }
    }
}
