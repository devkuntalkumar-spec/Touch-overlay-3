package com.touchoverlay.app.ui

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.touchoverlay.app.R
import com.touchoverlay.app.data.LayoutRepository
import com.touchoverlay.app.databinding.ActivityLayoutEditorBinding
import com.touchoverlay.app.model.ButtonConfig
import com.touchoverlay.app.model.JoystickConfig
import com.touchoverlay.app.model.LayoutConfig
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Full layout editor. The activity is locked to portrait orientation (see the
 * manifest) so this screen's canvas is a true 1:1, to-scale preview of the
 * device's portrait screen — not just an abstract mock-up. Positions and sizes
 * are stored as percentages of that canvas, which is how [OverlayService] later
 * maps them back onto the real screen (including in landscape, as a best-effort
 * approximation — see the note in MainActivity's limitations text).
 */
class LayoutEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLayoutEditorBinding
    private lateinit var repository: LayoutRepository

    private var layout: LayoutConfig = LayoutConfig.default()
    private var selectedId: String? = null
    private val controlViews = mutableMapOf<String, EditorControlView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLayoutEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = LayoutRepository(applicationContext)

        binding.btnSave.setOnClickListener { saveLayout() }
        binding.btnReset.setOnClickListener { resetLayout() }
        binding.btnAddControl.setOnClickListener { showAddDialog() }
        binding.btnDeleteSelected.setOnClickListener { deleteSelected() }

        binding.sliderSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) applySize(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.sliderOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) applyOpacity(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Wait for the canvas to be laid out so its pixel width/height are known
        // before converting saved percentages into on-screen positions.
        binding.canvas.post { loadLayout() }
    }

    private fun loadLayout() {
        lifecycleScope.launch {
            layout = repository.getLayout()
            renderAll()
        }
    }

    private fun renderAll() {
        binding.canvas.removeAllViews()
        controlViews.clear()
        layout.buttons.forEach { addButtonToCanvas(it) }
        addJoystickToCanvas(layout.joystick)
        showSelectionPanel(false)
    }

    private fun canvasWidth() = binding.canvas.width.takeIf { it > 0 } ?: 1
    private fun canvasHeight() = binding.canvas.height.takeIf { it > 0 } ?: 1

    private fun addButtonToCanvas(config: ButtonConfig) {
        val view = EditorControlView(this, config.label)
        val sizePx = dp(config.sizeDp)
        binding.canvas.addView(view, FrameLayout.LayoutParams(sizePx, sizePx))
        view.alpha = config.alpha
        positionView(view, config.xPercent, config.yPercent, sizePx)
        attachDrag(view, config.id)
        controlViews[config.id] = view
    }

    private fun addJoystickToCanvas(config: JoystickConfig) {
        val view = EditorControlView(this, getString(R.string.joystick_short_label))
        val sizePx = dp(config.sizeDp)
        binding.canvas.addView(view, FrameLayout.LayoutParams(sizePx, sizePx))
        view.alpha = config.alpha
        positionView(view, config.xPercent, config.yPercent, sizePx)
        attachDrag(view, "joystick")
        controlViews["joystick"] = view
    }

    private fun positionView(view: View, xPercent: Float, yPercent: Float, sizePx: Int) {
        view.translationX = (xPercent * canvasWidth() - sizePx / 2f).coerceIn(0f, (canvasWidth() - sizePx).toFloat())
        view.translationY = (yPercent * canvasHeight() - sizePx / 2f).coerceIn(0f, (canvasHeight() - sizePx).toFloat())
    }

    private fun attachDrag(view: View, id: String) {
        var downX = 0f
        var downY = 0f
        var startTx = 0f
        var startTy = 0f

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startTx = v.translationX
                    startTy = v.translationY
                    selectControl(id)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newTx = startTx + (event.rawX - downX)
                    val newTy = startTy + (event.rawY - downY)
                    v.translationX = newTx.coerceIn(0f, (canvasWidth() - v.width).toFloat())
                    v.translationY = newTy.coerceIn(0f, (canvasHeight() - v.height).toFloat())
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    commitPosition(id, v)
                    true
                }
                else -> false
            }
        }
    }

    private fun commitPosition(id: String, v: View) {
        val xPercent = (v.translationX + v.width / 2f) / canvasWidth()
        val yPercent = (v.translationY + v.height / 2f) / canvasHeight()
        if (id == "joystick") {
            layout.joystick.xPercent = xPercent
            layout.joystick.yPercent = yPercent
        } else {
            layout.buttons.find { it.id == id }?.let {
                it.xPercent = xPercent
                it.yPercent = yPercent
            }
        }
    }

    private fun selectControl(id: String) {
        selectedId = id
        showSelectionPanel(true)

        val sizeDp: Float
        val alpha: Float
        if (id == "joystick") {
            sizeDp = layout.joystick.sizeDp
            alpha = layout.joystick.alpha
        } else {
            val btn = layout.buttons.find { it.id == id }
            sizeDp = btn?.sizeDp ?: 56f
            alpha = btn?.alpha ?: 1f
        }
        binding.sliderSize.progress = sizeDp.toInt()
        binding.sliderOpacity.progress = (alpha * 100).toInt()
        binding.tvSelectedLabel.text = getString(R.string.editing_control, idToLabel(id))
    }

    private fun idToLabel(id: String): String =
        if (id == "joystick") getString(R.string.joystick_short_label)
        else layout.buttons.find { it.id == id }?.label ?: id

    private fun showSelectionPanel(show: Boolean) {
        binding.selectionPanel.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) selectedId = null
    }

    private fun applySize(progressDp: Int) {
        val id = selectedId ?: return
        val clamped = max(30, progressDp)
        val view = controlViews[id] ?: return
        val sizePx = dp(clamped.toFloat())

        val lp = view.layoutParams
        lp.width = sizePx
        lp.height = sizePx
        view.layoutParams = lp

        if (id == "joystick") layout.joystick.sizeDp = clamped.toFloat()
        else layout.buttons.find { it.id == id }?.sizeDp = clamped.toFloat()

        commitPosition(id, view)
    }

    private fun applyOpacity(progress: Int) {
        val id = selectedId ?: return
        val alpha = progress.coerceIn(10, 100) / 100f
        controlViews[id]?.alpha = alpha
        if (id == "joystick") layout.joystick.alpha = alpha
        else layout.buttons.find { it.id == id }?.alpha = alpha
    }

    private fun deleteSelected() {
        val id = selectedId ?: return
        if (id == "joystick") {
            Toast.makeText(this, R.string.cannot_delete_joystick, Toast.LENGTH_SHORT).show()
            return
        }
        layout.buttons.removeAll { it.id == id }
        controlViews[id]?.let { binding.canvas.removeView(it) }
        controlViews.remove(id)
        showSelectionPanel(false)
    }

    private fun showAddDialog() {
        val allButtons = listOf("btn_a" to "A", "btn_b" to "B", "btn_c" to "C", "btn_d" to "D")
        val missing = allButtons.filter { (id, _) -> layout.buttons.none { it.id == id } }
        if (missing.isEmpty()) {
            Toast.makeText(this, R.string.all_buttons_added, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = missing.map { it.second }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.add_control_title)
            .setItems(labels) { _, which ->
                val (id, label) = missing[which]
                val newButton = ButtonConfig(id, label, 0.5f, 0.5f, 56f, 0.75f)
                layout.buttons.add(newButton)
                addButtonToCanvas(newButton)
            }
            .show()
    }

    private fun saveLayout() {
        lifecycleScope.launch {
            repository.saveLayout(layout)
            Toast.makeText(this@LayoutEditorActivity, R.string.layout_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetLayout() {
        lifecycleScope.launch {
            layout = repository.resetLayout()
            renderAll()
            Toast.makeText(this@LayoutEditorActivity, R.string.layout_reset, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
}
