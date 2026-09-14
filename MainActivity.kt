package com.touchoverlay.app

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.touchoverlay.app.databinding.ActivityMainBinding
import com.touchoverlay.app.ui.LayoutEditorActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_EDITOR = "open_editor"
    }

    private lateinit var binding: ActivityMainBinding

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshStatus() }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
        binding.btnEnableAccessibility.setOnClickListener { openAccessibilitySettings() }
        binding.btnStartStop.setOnClickListener { toggleOverlay() }
        binding.btnOpenEditor.setOnClickListener {
            startActivity(Intent(this, LayoutEditorActivity::class.java))
        }

        maybeRequestNotificationPermission()

        if (intent?.getBooleanExtra(EXTRA_OPEN_EDITOR, false) == true) {
            startActivity(Intent(this, LayoutEditorActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        overlayPermissionLauncher.launch(intent)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        if (OverlayService.isRunning) {
            stopService(Intent(this, OverlayService::class.java))
        } else {
            ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
        }
        binding.root.postDelayed({ refreshStatus() }, 300)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, TouchAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    private fun refreshStatus() {
        val overlayGranted = Settings.canDrawOverlays(this)
        binding.tvOverlayStatus.text = getString(
            if (overlayGranted) R.string.status_granted else R.string.status_not_granted
        )
        binding.btnGrantOverlay.isEnabled = !overlayGranted

        val accessibilityEnabled = isAccessibilityServiceEnabled()
        binding.tvAccessibilityStatus.text = getString(
            if (accessibilityEnabled) R.string.status_granted else R.string.status_not_granted
        )

        binding.btnStartStop.isEnabled = overlayGranted
        binding.btnStartStop.text = getString(
            if (OverlayService.isRunning) R.string.stop_overlay else R.string.start_overlay
        )
    }
}
