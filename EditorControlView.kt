package com.touchoverlay.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

/**
 * A simple circular label used only inside [LayoutEditorActivity]'s canvas to
 * represent a button or the joystick while the user arranges the layout.
 */
class EditorControlView(context: Context, label: String) : FrameLayout(context) {
    init {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#3378C2F5"))
            setStroke(4, Color.parseColor("#78C2F5"))
        }
        val textView = TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 14f
        }
        addView(textView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
}
