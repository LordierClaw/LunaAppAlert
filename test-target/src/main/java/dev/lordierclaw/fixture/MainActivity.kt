package dev.lordierclaw.fixture

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Ordinary external app used to exercise actual Android usage events. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val panel = fixturePanel(getString(R.string.app_name), "Màn hình chính")
        panel.addView(Button(this).apply {
            text = "Mở màn hình thứ hai"
            isAllCaps = false
            setOnClickListener { startActivity(Intent(this@MainActivity, SecondActivity::class.java)) }
        })
        setContentView(panel)
    }
}

class SecondActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val panel = fixturePanel(getString(R.string.app_name), "Màn hình thứ hai")
        panel.addView(Button(this).apply {
            text = "Quay lại màn hình chính"
            isAllCaps = false
            setOnClickListener { finish() }
        })
        setContentView(panel)
    }
}

private fun Activity.fixturePanel(title: String, subtitle: String): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(48, 64, 48, 64)
        setBackgroundColor(Color.rgb(246, 244, 238))
        addView(TextView(this@fixturePanel).apply {
            text = title
            textSize = 28f
            setTextColor(Color.rgb(33, 45, 35))
            gravity = Gravity.CENTER
        })
        addView(TextView(this@fixturePanel).apply {
            text = subtitle
            textSize = 20f
            setTextColor(Color.rgb(68, 83, 71))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 32)
        })
    }
