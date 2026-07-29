/*
 * This file is part of HyperCeiler.

 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */
package com.sevtinge.hyperceiler.libhook.rules.systemui.statusbar.clock

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.sevtinge.hyperceiler.common.utils.PrefsBridge
import com.sevtinge.hyperceiler.libhook.base.BaseHook
import io.github.kyuubiran.ezxhelper.core.finder.ConstructorFinder.`-Static`.constructorFinder
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

    /** Track which MiuiClock views we've already processed to avoid duplicate inserts */
    private val processedClocks: MutableSet<View> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

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

        // Hide original clock
        originalClock.visibility = View.GONE
        originalClock.textSize = 0f
        originalClock.layoutParams.width = 0
        originalClock.layoutParams.height = 0

        // Build the ZK clock layout
        val zkContainer = buildZkClockLayout(context, style) ?: return

        // Create a wrapper to center the container vertically and prevent stretching
        val wrapper = LinearLayout(context).apply {
            tag = "zk_clock_container"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Set zkContainer layout params to WRAP_CONTENT (or 22dp for style 1, 2)
        val containerHeight = if (style in setOf(1, 2)) dpToPx(context, 22f) else ViewGroup.LayoutParams.WRAP_CONTENT
        zkContainer.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            containerHeight
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
            if (style in setOf(4, 5, 6)) {
                topMargin = dpToPx(context, -1f)
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
        }

        // Insert before the original clock position
        val index = parent.indexOfChild(originalClock)
        parent.addView(wrapper, index, layoutParams)
    }

    /**
     * Build the complete ZK clock layout for the given style.
     * Each style mirrors the layout from status_bar_clock.xml.
     */
    private fun buildZkClockLayout(context: Context, style: Int): View? {
        return when (style) {
            1 -> buildStyle1(context)
            2 -> buildStyle2(context)
            3 -> buildStyle3(context)
            4 -> buildStyle4(context)
            5 -> buildStyle5(context)
            6 -> buildStyle6(context)
            7 -> buildStyle7(context)
            8 -> buildStyle8(context)
            9 -> buildStyle9(context)
            10 -> buildStyle10(context)
            11 -> buildStyle11(context)
            12 -> buildStyle12(context)
            13 -> buildStyle13(context)
            14 -> buildStyle14(context)
            15 -> buildStyle15(context)
            else -> null
        }
    }

    // ==================== Helper Methods ====================

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
        textSizeDp: Float = 12f,
        textColor: Int = Color.WHITE,
        bold: Boolean = true,
        singleLine: Boolean = true,
        gravity: Int = Gravity.START or Gravity.CENTER_VERTICAL,
        maxEms: Int = 0
    ): TextClock {
        return TextClock(context).apply {
            format12Hour = format12
            format24Hour = format24
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSizeDp)
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
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dpToPx(context, leftDp)
            topMargin = dpToPx(context, topDp)
            rightMargin = dpToPx(context, rightDp)
            bottomMargin = dpToPx(context, bottomDp)
            this.gravity = gravity
        }
    }

    // ==================== Style Builders ====================

    /**
     * Style 1: MiuiClock-like TextClock with clock_bg1 (accent pill, 15dp radius)
     */
    private fun buildStyle1(context: Context): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(context, 22f)
            )
            val clock = createTextClock(context, "hh:mm", "HH:mm", textSizeDp = 12f).apply {
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
    private fun buildStyle2(context: Context): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(context, 22f)
            )
            val clock = createTextClock(context, "hh:mm", "HH:mm", textSizeDp = 12f).apply {
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
    private fun buildStyle3(context: Context): View {
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

                addView(createTextClock(context, "hh", "HH", textSizeDp = 10f, textColor = Color.RED),
                    createParams(context, topDp = -2f)
                )
                addView(createTextClock(context, "mm", "mm", textSizeDp = 10f, textColor = 0xEE000000.toInt()),
                    createParams(context, topDp = -4f)
                )
            }
            addView(leftCol)

            // Right column: EEEE / dd/MM/yyyy
            val rightCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(createTextClock(context, " EEEE", " EEEE", textSizeDp = 10f, textColor = accent800, maxEms = 6).apply {
                    gravity = Gravity.START
                }, createParams(context, topDp = -1f))
                addView(createTextClock(context, "dd/MM/yyyy", "dd/MM/yyyy", textSizeDp = 8.5f, textColor = accent800).apply {
                    gravity = Gravity.START
                }, createParams(context, topDp = -4f))
            }
            addView(rightCol, createParams(context, leftDp = 3f))
        }
    }

    /**
     * Style 4: Horizontal bar — time | date, clock_bg4b background
     */
    private fun buildStyle4(context: Context): View {
        val backClock = statusbarBackClock(context)
        val accent200 = resolveColor(context, "accent1_200", 0xFFA8C7FA.toInt())

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(4, context)
            // clock_bg4b padding: left 2dp, bottom 1dp, right 2dp
            // Plus original layout offset: top -1dp, right 1dp
            setPadding(dpToPx(context, 2f), dpToPx(context, 0.5f), dpToPx(context, 3f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm", "HH:mm", textSizeDp = 13f, textColor = backClock),
                createParams(context, topDp = 0.9f)
            )
            addView(createTextClock(context, "a", "", textSizeDp = 7f, textColor = backClock),
                createParams(context, leftDp = 2f, topDp = 1.8f)
            )
            addView(TextView(context).apply {
                text = "|"
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 9f)
                setTextColor(accent200)
                typeface = Typeface.DEFAULT_BOLD
            }, createParams(context, leftDp = 2f, topDp = 0.5f))
            
            addView(createTextClock(context, "dd MMMM", "dd MMMM", textSizeDp = 13f, textColor = backClock, singleLine = false),
                createParams(context, leftDp = 2f, topDp = 0.5f)
            )
        }
    }

    /**
     * Style 5: Background1 outer + Background2 inner time + date/day side
     */
    private fun buildStyle5(context: Context): View {
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
                
                addView(createTextClock(context, "hh:mm:ss", "HH:mm:ss", textSizeDp = 12f, textColor = Color.WHITE))
            }
            addView(timeBadge)

            // Day/month text
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizeDp = 6.5f, textColor = accent600, singleLine = false, maxEms = 2),
                createParams(context, leftDp = 3f)
            )
            // Day number
            addView(createTextClock(context, "dd", "dd", textSizeDp = 12f, textColor = accent600),
                createParams(context, leftDp = 1.5f)
            )
        }
    }

    /**
     * Style 6: Red gradient bg, white text — time | date
     */
    private fun buildStyle6(context: Context): View {
        val accent200 = resolveColor(context, "accent1_200", 0xFFA8C7FA.toInt())

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(6, context)
            // clock_bg6 padding: left 2.5dp, top -0.5dp, right 7dp, bottom 1dp
            // Plus original layout offset: top -1dp, right 1dp
            setPadding(dpToPx(context, 2.5f), dpToPx(context, 0.5f), dpToPx(context, 8f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm", "HH:mm", textSizeDp = 13f, textColor = Color.WHITE),
                createParams(context, topDp = 0.9f)
            )
            addView(createTextClock(context, "a", "", textSizeDp = 7f, textColor = Color.WHITE),
                createParams(context, leftDp = 2f, topDp = 1.8f)
            )
            addView(TextView(context).apply {
                text = "|"
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 9f)
                setTextColor(accent200)
                typeface = Typeface.DEFAULT_BOLD
            }, createParams(context, leftDp = 2f, topDp = 0.5f))
            
            addView(createTextClock(context, "dd MMMM", "dd MMMM", textSizeDp = 13f, textColor = Color.WHITE, singleLine = false),
                createParams(context, leftDp = 2f, topDp = 0.5f)
            )
        }
    }

    /**
     * Style 7: accent1_200 asymmetric bg, time + accent gradient inner + date
     */
    private fun buildStyle7(context: Context): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(7, context)
            // clock_bg7 padding: left 2dp, top 1dp, right 2dp, bottom 1dp
            setPadding(dpToPx(context, 2f), dpToPx(context, 1f), dpToPx(context, 2f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizeDp = 13f, textColor = Color.BLACK).apply {
                background = ZkClockDrawables.getInnerTimeBackground(7, context)
                // clock_bg10 padding: left 2dp, right 2dp
                setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), 0)
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizeDp = 7.5f, textColor = Color.BLACK, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizeDp = 13f, textColor = Color.BLACK),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 8: Black asymmetric bg, time + accent inner + date
     */
    private fun buildStyle8(context: Context): View {
        val backClock = statusbarBackClock(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(8, context)
            // clock_bg8 padding: left 6dp, top 0dp, right 6dp, bottom 1dp
            setPadding(dpToPx(context, 6f), 0, dpToPx(context, 6f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizeDp = 13f, textColor = Color.BLACK).apply {
                background = ZkClockDrawables.getInnerTimeBackground(8, context)
                // clock_bg7 padding: left 2dp, top 1dp, right 2dp, bottom 1dp
                setPadding(dpToPx(context, 2f), dpToPx(context, 1f), dpToPx(context, 2f), dpToPx(context, 1f))
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizeDp = 7.5f, textColor = backClock, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizeDp = 13f, textColor = backClock),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 9: accent bg + black inner time + date
     */
    private fun buildStyle9(context: Context): View {
        val backClock = statusbarBackClock(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(9, context)
            // clock_bg7 padding: left 2dp, top 1dp, right 2dp, bottom 1dp
            setPadding(dpToPx(context, 2f), dpToPx(context, 1f), dpToPx(context, 2f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizeDp = 13f, textColor = backClock).apply {
                background = ZkClockDrawables.getInnerTimeBackground(9, context)
                // clock_bg9 padding: left 6dp, top 1dp, right 6dp, bottom 1dp
                setPadding(dpToPx(context, 6f), dpToPx(context, 1f), dpToPx(context, 6f), dpToPx(context, 1f))
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizeDp = 7.5f, textColor = Color.BLACK, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizeDp = 13f, textColor = Color.BLACK),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 10: Black bg + accent gradient inner + accent date
     */
    private fun buildStyle10(context: Context): View {
        val clockColor = resolveColor(context, "accent1_100", 0xFFD3E3FD.toInt())
        val backClock = statusbarBackClock(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(10, context)
            // clock_bg4b padding: left 2dp, top 0dp, right 2dp, bottom 1dp
            setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizeDp = 13f, textColor = clockColor).apply {
                background = ZkClockDrawables.getInnerTimeBackground(10, context)
                // clock_bg10 padding: left 2dp, right 2dp
                setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), 0)
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizeDp = 7.5f, textColor = backClock, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizeDp = 13f, textColor = resolveColor(context, "accent1_100", 0xFFD3E3FD.toInt())),
                createParams(context, leftDp = 2f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 11: White pill + black inner pill + red day + date
     */
    private fun buildStyle11(context: Context): View {
        val backClock = statusbarBackClock(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(11, context)
            // clock_bg4 padding: left 6dp, top 0.8dp, right 6dp, bottom 0.8dp
            setPadding(dpToPx(context, 6f), dpToPx(context, 0.8f), dpToPx(context, 6f), dpToPx(context, 0.8f))

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizeDp = 13f, textColor = backClock).apply {
                background = ZkClockDrawables.getInnerTimeBackground(11, context)
                // clock_bg4b padding: left 2dp, bottom 1dp, right 2dp
                setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), dpToPx(context, 1f))
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizeDp = 7.5f, textColor = Color.BLACK, singleLine = false, maxEms = 2),
                createParams(context, leftDp = 2f, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizeDp = 13f, textColor = Color.RED),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 12: Black bg + orange gradient inner + accent date
     */
    private fun buildStyle12(context: Context): View {
        val backClock = statusbarBackClock(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(12, context)
            // clock_bg4b padding: left 2dp, top 0dp, right 2dp, bottom 1dp
            setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizeDp = 13f, textColor = backClock).apply {
                background = ZkClockDrawables.getInnerTimeBackground(12, context)
                // clock_bg12 padding: right 2dp
                setPadding(0, 0, dpToPx(context, 2f), 0)
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizeDp = 7.5f, textColor = backClock, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizeDp = 13f, textColor = backClock),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 13: Green gradient bg + accent inner + accent date
     */
    private fun buildStyle13(context: Context): View {
        val backClock = statusbarBackClock(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(13, context)
            // clock_bg11 padding: right 2dp
            setPadding(0, 0, dpToPx(context, 2f), 0)

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizeDp = 13f, textColor = backClock).apply {
                background = ZkClockDrawables.getInnerTimeBackground(13, context)
                // clock_bg10 padding: left 2dp, right 2dp
                setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), 0)
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizeDp = 7.5f, textColor = backClock, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizeDp = 13f, textColor = backClock),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 14: Black bg + white inner pill + accent date
     */
    private fun buildStyle14(context: Context): View {
        val backClock = statusbarBackClock(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(14, context)
            // clock_bg4b padding: left 2dp, top 0dp, right 2dp, bottom 1dp
            setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), dpToPx(context, 1f))

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizeDp = 13f, textColor = Color.BLACK).apply {
                background = ZkClockDrawables.getInnerTimeBackground(14, context)
                // clock_bg4 padding: left 6dp, top 0.8dp, right 6dp, bottom 0.8dp
                setPadding(dpToPx(context, 6f), dpToPx(context, 0.8f), dpToPx(context, 6f), dpToPx(context, 0.8f))
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizeDp = 7.5f, textColor = backClock, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizeDp = 13f, textColor = backClock),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }

    /**
     * Style 15: Accent gradient bg + orange gradient inner + white text
     */
    private fun buildStyle15(context: Context): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ZkClockDrawables.getContainerBackground(15, context)
            // clock_bg10 padding: left 2dp, right 2dp
            setPadding(dpToPx(context, 2f), 0, dpToPx(context, 2f), 0)

            addView(createTextClock(context, "hh:mm a", "HH:mm", textSizeDp = 13f, textColor = Color.WHITE).apply {
                background = ZkClockDrawables.getInnerTimeBackground(15, context)
                // clock_bg12 padding: right 2dp
                setPadding(0, 0, dpToPx(context, 2f), 0)
            })
            addView(createTextClock(context, "EEE MMM", "EEE MMM", textSizeDp = 7.5f, textColor = Color.WHITE, singleLine = false, maxEms = 2),
                createParams(context, topDp = 0.5f)
            )
            addView(createTextClock(context, "dd", "dd", textSizeDp = 13f, textColor = Color.WHITE),
                createParams(context, leftDp = -3f, topDp = 0.9f, rightDp = 3f)
            )
        }
    }
}
