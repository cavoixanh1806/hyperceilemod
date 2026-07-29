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
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue

/**
 * Programmatic recreation of the ZK Clock background drawables.
 *
 * Original source: decoded_systemui/res/drawable/clock_bg*.xml, Background*.xml
 * These are recreated in code because Xposed hooks run inside the SystemUI process
 * and cannot access resource files from the modded APK.
 */
object ZkClockDrawables {

    private fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics
        )
    }

    private fun dpToPxInt(context: Context, dp: Float): Int {
        return dpToPx(context, dp).toInt()
    }

    /**
     * Resolve a system monet color by name. Falls back to a hardcoded default if not found.
     */
    private fun resolveSystemColor(context: Context, colorName: String, fallback: Int): Int {
        return try {
            val resId = context.resources.getIdentifier(colorName, "color", "android")
            if (resId != 0) context.resources.getColor(resId, context.theme) else fallback
        } catch (_: Throwable) {
            fallback
        }
    }

    /** system_accent1_200 */
    fun accent1_200(ctx: Context) = resolveSystemColor(ctx, "system_accent1_200", 0xFFA8C7FA.toInt())
    /** system_accent1_300 */
    fun accent1_300(ctx: Context) = resolveSystemColor(ctx, "system_accent1_300", 0xFF7CACF8.toInt())
    /** system_accent1_500 */
    fun accent1_500(ctx: Context) = resolveSystemColor(ctx, "system_accent1_500", 0xFF4285F4.toInt())
    /** system_accent1_600 */
    fun accent1_600(ctx: Context) = resolveSystemColor(ctx, "system_accent1_600", 0xFF1B6EF3.toInt())
    /** system_accent1_800 */
    fun accent1_800(ctx: Context) = resolveSystemColor(ctx, "system_accent1_800", 0xFF0842A0.toInt())
    /** system_accent1_100 */
    fun accent1_100(ctx: Context) = resolveSystemColor(ctx, "system_accent1_100", 0xFFD3E3FD.toInt())
    /** system_accent2_300 */
    fun accent2_300(ctx: Context) = resolveSystemColor(ctx, "system_accent2_300", 0xFF8DA7C4.toInt())

    /**
     * Get the appropriate container/outer background for a given ZK clock style.
     */
    fun getContainerBackground(style: Int, context: Context): Drawable? {
        return when (style) {
            // Style 0: default, no custom background
            0 -> null
            // Style 1: clock_bg1 — pill accent1_300, 15dp radius
            1 -> createClockBg1(context)
            // Style 2: clock_bg2 — asymmetric pill accent1_300
            2 -> createClockBg2(context)
            // Style 3: Background3 — accent2_300, 5dp corners, white stroke
            3 -> createBackground3(context)
            // Style 4: clock_bg4b — black pill 20dp radius
            4 -> createClockBg4b(context)
            // Style 5: Background1 — accent1_300, 15dp corners, white stroke
            5 -> createBackground1(context)
            // Style 6: clock_bg6 — red gradient pill 20dp
            6 -> createClockBg6(context)
            // Style 7: clock_bg7 — accent1_200, asymmetric corners
            7 -> createClockBg7(context)
            // Style 8: clock_bg8 — black, asymmetric corners
            8 -> createClockBg8(context)
            // Style 9: clock_bg7 again (outer), clock_bg9 for inner
            9 -> createClockBg7(context)
            // Style 10: clock_bg4b (outer)
            10 -> createClockBg4b(context)
            // Style 11: clock_bg4 — white pill 20dp radius
            11 -> createClockBg4(context)
            // Style 12: clock_bg4b (outer)
            12 -> createClockBg4b(context)
            // Style 13: clock_bg11 — green gradient layer
            13 -> createClockBg11(context)
            // Style 14: clock_bg4b (outer)
            14 -> createClockBg4b(context)
            // Style 15: clock_bg10 — accent1_300 gradient
            15 -> createClockBg10(context)
            else -> null
        }
    }

    /**
     * Get the inner/time background for styles that have a separate time badge.
     */
    fun getInnerTimeBackground(style: Int, context: Context): Drawable? {
        return when (style) {
            3 -> createBackground4(context) // white, 5dp left corners
            5 -> createBackground2(context) // accent1_500, 15dp corners
            7 -> createClockBg10(context)   // accent gradient pill
            8 -> createClockBg7(context)    // accent1_200 asymmetric
            9 -> createClockBg9(context)    // black asymmetric
            10 -> createClockBg10(context)  // accent gradient pill
            11 -> createClockBg4b(context)  // black pill
            12 -> createClockBg12(context)  // orange gradient layer
            13 -> createClockBg10(context)  // accent gradient pill
            14 -> createClockBg4(context)   // white pill
            15 -> createClockBg12(context)  // orange gradient layer
            else -> null
        }
    }

    // ==================== Drawable Factories ====================

    /**
     * clock_bg1: Ripple with accent1_300, 15dp radius pill, 7dp horizontal padding
     */
    private fun createClockBg1(ctx: Context): Drawable {
        val color = accent1_300(ctx)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dpToPx(ctx, 15f)
            setPaddingCompat(ctx, 7f, 0f, 7f, 0f)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dpToPx(ctx, 7f)
        }
        return RippleDrawable(ColorStateList.valueOf(color), bg, mask)
    }

    /**
     * clock_bg2: Ripple with accent1_300, asymmetric corners (20/8/8/20), 6dp h-padding
     */
    private fun createClockBg2(ctx: Context): Drawable {
        val color = accent1_300(ctx)
        val r = dpToPx(ctx, 20f)
        val r2 = dpToPx(ctx, 8f)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadii = floatArrayOf(r, r, r2, r2, r, r, r2, r2)
            setPaddingCompat(ctx, 6f, 0.8f, 6f, 0.8f)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dpToPx(ctx, 7f)
        }
        return RippleDrawable(ColorStateList.valueOf(color), bg, mask)
    }

    /**
     * Background1: accent1_300 solid, 15dp all corners, white 1dp stroke, right padding 5dp
     */
    private fun createBackground1(ctx: Context): Drawable {
        val color = accent1_300(ctx)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            setStroke(dpToPxInt(ctx, 1f), Color.WHITE)
            cornerRadii = floatArrayOf(
                dpToPx(ctx, 15f), dpToPx(ctx, 15f),
                dpToPx(ctx, 15f), dpToPx(ctx, 15f),
                dpToPx(ctx, 15f), dpToPx(ctx, 15f),
                dpToPx(ctx, 15f), dpToPx(ctx, 15f)
            )
            setPaddingCompat(ctx, 0f, 0f, 5f, 0f)
        }
    }

    /**
     * Background2: accent1_500 solid, 15dp all corners, white 1dp stroke, 5dp h-padding
     */
    private fun createBackground2(ctx: Context): Drawable {
        val color = accent1_500(ctx)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            setStroke(dpToPxInt(ctx, 1f), Color.WHITE)
            cornerRadius = dpToPx(ctx, 15f)
            setPaddingCompat(ctx, 5f, 0.5f, 5f, 1f)
        }
    }

    /**
     * Background3: accent2_300 solid, 5dp all corners, white 1dp stroke
     */
    private fun createBackground3(ctx: Context): Drawable {
        val color = accent2_300(ctx)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            setStroke(dpToPxInt(ctx, 1f), Color.WHITE)
            cornerRadius = dpToPx(ctx, 5f)
            setPaddingCompat(ctx, 0f, 0f, 5f, 0f)
        }
    }

    /**
     * Background4: white solid, left corners 5dp, right corners 0dp
     */
    private fun createBackground4(ctx: Context): Drawable {
        val r = dpToPx(ctx, 5f)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            setStroke(dpToPxInt(ctx, 1f), Color.WHITE)
            cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            setPaddingCompat(ctx, 5f, 0f, 5f, 0f)
        }
    }

    /**
     * clock_bg4: White tint, 20dp all corners
     */
    private fun createClockBg4(ctx: Context): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = dpToPx(ctx, 20f)
            setPaddingCompat(ctx, 6f, 0.8f, 6f, 0.8f)
        }
    }

    /**
     * clock_bg4b: Black tint, 20dp all corners
     */
    private fun createClockBg4b(ctx: Context): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.BLACK)
            cornerRadius = dpToPx(ctx, 20f)
            setPaddingCompat(ctx, 2f, 0f, 2f, 1f)
        }
    }

    /**
     * clock_bg6: Red gradient solid, 20dp radius
     */
    private fun createClockBg6(ctx: Context): Drawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.RED, Color.TRANSPARENT)
        ).apply {
            cornerRadius = dpToPx(ctx, 20f)
            setPaddingCompat(ctx, 2.5f, 0f, 7f, 1f)
        }
    }

    /**
     * clock_bg7: Ripple, accent1_200 tint, asymmetric corners (8/20/8/20)
     */
    private fun createClockBg7(ctx: Context): Drawable {
        val color = accent1_200(ctx)
        val r1 = dpToPx(ctx, 8f)
        val r2 = dpToPx(ctx, 20f)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadii = floatArrayOf(r1, r1, r2, r2, r1, r1, r2, r2)
            setPaddingCompat(ctx, 2f, 1f, 2f, 1f)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.BLACK)
            cornerRadius = dpToPx(ctx, 4f)
        }
        return RippleDrawable(
            ColorStateList.valueOf(resolveControlHighlight(ctx)),
            bg, mask
        )
    }

    /**
     * clock_bg8: Ripple, black bg, asymmetric corners (8/20/8/20)
     */
    private fun createClockBg8(ctx: Context): Drawable {
        val r1 = dpToPx(ctx, 8f)
        val r2 = dpToPx(ctx, 20f)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.BLACK)
            cornerRadii = floatArrayOf(r1, r1, r2, r2, r1, r1, r2, r2)
            setPaddingCompat(ctx, 6f, 0f, 6f, 1f)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(0xF3433EFF.toInt())
            cornerRadius = dpToPx(ctx, 4f)
        }
        return RippleDrawable(
            ColorStateList.valueOf(resolveControlHighlight(ctx)),
            bg, mask
        )
    }

    /**
     * clock_bg9: Ripple, black bg, asymmetric corners (8/20/8/20), 1dp v-padding
     */
    private fun createClockBg9(ctx: Context): Drawable {
        val r1 = dpToPx(ctx, 8f)
        val r2 = dpToPx(ctx, 20f)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.BLACK)
            cornerRadii = floatArrayOf(r1, r1, r2, r2, r1, r1, r2, r2)
            setPaddingCompat(ctx, 6f, 1f, 6f, 1f)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(0xF3433EFF.toInt())
            cornerRadius = dpToPx(ctx, 4f)
        }
        return RippleDrawable(
            ColorStateList.valueOf(resolveControlHighlight(ctx)),
            bg, mask
        )
    }

    /**
     * clock_bg10: accent1_300 → transparent gradient, 10dp radius, 2dp h-padding
     */
    private fun createClockBg10(ctx: Context): Drawable {
        val color = accent1_300(ctx)
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(color, Color.TRANSPARENT)
        ).apply {
            cornerRadius = dpToPx(ctx, 10f)
            setPaddingCompat(ctx, 2f, 0f, 2f, 0f)
        }
    }

    /**
     * clock_bg11: LayerDrawable — transparent base + holo_green_light gradient overlay, 20dp radius
     */
    private fun createClockBg11(ctx: Context): Drawable {
        val greenColor = resolveSystemColor(ctx, "holo_green_light", 0xFF99CC00.toInt())
        val layer0 = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT)
        ).apply {
            cornerRadius = dpToPx(ctx, 20f)
        }
        val layer1 = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(greenColor, Color.TRANSPARENT)
        ).apply {
            cornerRadius = dpToPx(ctx, 20f)
        }
        return LayerDrawable(arrayOf(layer0, layer1)).apply {
            setPadding(0, 0, dpToPxInt(ctx, 2f), 0)
        }
    }

    /**
     * clock_bg12: LayerDrawable — transparent base + holo_orange_light gradient overlay, 20dp radius
     */
    private fun createClockBg12(ctx: Context): Drawable {
        val orangeColor = resolveSystemColor(ctx, "holo_orange_light", 0xFFFFBB33.toInt())
        val layer0 = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT)
        ).apply {
            cornerRadius = dpToPx(ctx, 20f)
        }
        val layer1 = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(orangeColor, Color.TRANSPARENT)
        ).apply {
            cornerRadius = dpToPx(ctx, 20f)
        }
        return LayerDrawable(arrayOf(layer0, layer1)).apply {
            setPadding(0, 0, dpToPxInt(ctx, 2f), 0)
        }
    }

    private fun resolveControlHighlight(ctx: Context): Int {
        return try {
            val tv = TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.colorControlHighlight, tv, true)
            tv.data
        } catch (_: Throwable) {
            0x40FFFFFF
        }
    }

    /**
     * Extension: set padding on GradientDrawable using dp values
     */
    private fun GradientDrawable.setPaddingCompat(ctx: Context, l: Float, t: Float, r: Float, b: Float) {
        // GradientDrawable.setPadding is not directly available in all API levels;
        // we handle padding at the view level instead. This is a no-op placeholder.
        // Actual padding is applied by the layout builder in ZkClockStyleHook.
    }
}
