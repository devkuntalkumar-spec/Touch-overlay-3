package com.touchoverlay.app.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * A single generic on-screen button (A, B, C, or D).
 *
 * [xPercent]/[yPercent] store the button's CENTER as a fraction (0f..1f) of the
 * screen width/height, so the same layout scales sensibly across different
 * screen sizes and orientations. [sizeDp] is the diameter in density-independent
 * pixels, and [alpha] is opacity from 0f (invisible) to 1f (opaque).
 */
data class ButtonConfig(
    val id: String,
    val label: String,
    var xPercent: Float,
    var yPercent: Float,
    var sizeDp: Float,
    var alpha: Float
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("x", xPercent.toDouble())
        put("y", yPercent.toDouble())
        put("size", sizeDp.toDouble())
        put("alpha", alpha.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject): ButtonConfig = ButtonConfig(
            id = o.getString("id"),
            label = o.getString("label"),
            xPercent = o.getDouble("x").toFloat(),
            yPercent = o.getDouble("y").toFloat(),
            sizeDp = o.getDouble("size").toFloat(),
            alpha = o.getDouble("alpha").toFloat()
        )
    }
}

/** The single virtual joystick. Same coordinate/size/alpha conventions as [ButtonConfig]. */
data class JoystickConfig(
    var xPercent: Float,
    var yPercent: Float,
    var sizeDp: Float,
    var alpha: Float
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("x", xPercent.toDouble())
        put("y", yPercent.toDouble())
        put("size", sizeDp.toDouble())
        put("alpha", alpha.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject): JoystickConfig = JoystickConfig(
            xPercent = o.getDouble("x").toFloat(),
            yPercent = o.getDouble("y").toFloat(),
            sizeDp = o.getDouble("size").toFloat(),
            alpha = o.getDouble("alpha").toFloat()
        )
    }
}

/** The full saved layout: every button currently on screen, plus the joystick. */
data class LayoutConfig(
    val buttons: MutableList<ButtonConfig>,
    val joystick: JoystickConfig
) {
    fun toJson(): JSONObject = JSONObject().apply {
        val arr = JSONArray()
        buttons.forEach { arr.put(it.toJson()) }
        put("buttons", arr)
        put("joystick", joystick.toJson())
    }

    companion object {
        fun fromJson(o: JSONObject): LayoutConfig {
            val arr = o.getJSONArray("buttons")
            val list = mutableListOf<ButtonConfig>()
            for (i in 0 until arr.length()) {
                list.add(ButtonConfig.fromJson(arr.getJSONObject(i)))
            }
            return LayoutConfig(list, JoystickConfig.fromJson(o.getJSONObject("joystick")))
        }

        /** The layout shown the very first time the app runs, before anything is saved. */
        fun default(): LayoutConfig = LayoutConfig(
            buttons = mutableListOf(
                ButtonConfig("btn_a", "A", 0.85f, 0.55f, 56f, 0.75f),
                ButtonConfig("btn_b", "B", 0.92f, 0.42f, 56f, 0.75f),
                ButtonConfig("btn_c", "C", 0.78f, 0.42f, 56f, 0.75f),
                ButtonConfig("btn_d", "D", 0.85f, 0.30f, 56f, 0.75f)
            ),
            joystick = JoystickConfig(0.18f, 0.55f, 120f, 0.75f)
        )
    }
}
