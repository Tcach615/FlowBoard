package com.flowboard.keyboard.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.flowboard.keyboard.onboarding.OnboardingActivity

/** Settings and onboarding entry point. Everything here is local to the device. */
class SettingsActivity : Activity() {

    private lateinit var prefs: FlowBoardPreferences
    private lateinit var statsView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = FlowBoardPreferences(this)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    private fun buildScreen(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(BG) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }
        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(title("FlowBoard"))
        root.addView(
            body(
                "FlowBoard writes with FSL-1, a stenographic alphabet: the left thumb picks an " +
                    "onset family and the right thumb a vowel family, so one chord is one syllable " +
                    "core. Stroke several chords to spell a word; the board above shows the " +
                    "dictionary's readings of what you have stroked."
            )
        )

        root.addView(section("Activation"))
        root.addView(body("FlowBoard must be enabled in the system keyboard settings, then selected as the current keyboard."))
        root.addView(button("Open keyboard settings") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(button("Choose input method") {
            val manager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            manager.showInputMethodPicker()
        })
        root.addView(button("Replay the tutorial") {
            startActivity(Intent(this, OnboardingActivity::class.java))
        })

        root.addView(section("Gesture"))
        root.addView(
            sliderRow(
                label = "Synchronisation window",
                value = prefs.syncWindowMs,
                min = FlowBoardPreferences.MIN_SYNC_WINDOW,
                max = FlowBoardPreferences.MAX_SYNC_WINDOW,
                step = 20,
                suffix = " ms"
            ) { prefs.syncWindowMs = it }
        )
        root.addView(body("How long a half-finished chord waits for the second thumb before it is rejected."))
        root.addView(
            sliderRow(
                label = "Dead zone",
                value = (prefs.deadZone * 100).toInt(),
                min = 15,
                max = 45,
                step = 1,
                suffix = " %"
            ) { prefs.deadZone = it / 100f }
        )
        root.addView(body("Movement smaller than this share of the pad radius counts as no direction."))

        root.addView(section("Display"))
        root.addView(switchRow("Alphabet labels on the pads", prefs.showCandidateLabels) {
            prefs.showCandidateLabels = it
        })
        root.addView(switchRow("Highlight likely next chords", prefs.predictionHighlights) {
            prefs.predictionHighlights = it
        })
        root.addView(switchRow("Light theme", prefs.theme == "light") {
            prefs.theme = if (it) "light" else "dark"
        })
        root.addView(switchRow("Haptic feedback", prefs.hapticsEnabled) {
            prefs.hapticsEnabled = it
        })

        root.addView(section("Language"))
        val languages = arrayOf("English")
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                languages
            )
        }
        root.addView(spinner)
        root.addView(body("Only the English vocabulary is bundled in this build. The language list is a placeholder until more vocabularies ship."))

        root.addView(section("Personalization and privacy"))
        root.addView(switchRow("Boost words I use often", prefs.personalizationEnabled) {
            prefs.personalizationEnabled = it
        })
        root.addView(
            body(
                "Typed text is never transmitted. The dictionary and language model ship inside the app, " +
                    "and FlowBoard has no INTERNET permission and no analytics. " +
                    "The keyboard keeps the last few committed words in memory for prediction, and stores " +
                    "settings plus a typing counter in this app's private storage. Password fields use a " +
                    "stateless predictor and are never recorded."
            )
        )

        root.addView(section("Statistics"))
        statsView = TextView(this).apply {
            setTextColor(TEXT)
            textSize = 14f
        }
        root.addView(statsView)
        root.addView(button("Reset statistics") {
            prefs.resetStatistics()
            refreshStats()
        })

        root.addView(section("About"))
        root.addView(body("FlowBoard 1.0.0 · local prediction, no cloud service, no paid API."))

        return scroll
    }

    private fun refreshStats() {
        statsView.text = "Chords committed: ${prefs.chordsTyped}\nCorrections: ${prefs.corrections}"
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(TEXT)
        textSize = 26f
        setPadding(0, 0, 0, dp(8))
    }

    private fun section(text: String): TextView = TextView(this).apply {
        this.text = text.uppercase()
        setTextColor(ACCENT)
        textSize = 11f
        letterSpacing = 0.12f
        setPadding(0, dp(22), 0, dp(6))
    }

    private fun body(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(MUTED)
        textSize = 13f
        setLineSpacing(dp(3).toFloat(), 1f)
        setPadding(0, 0, 0, dp(6))
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(TEXT)
        setBackgroundColor(SURFACE)
        setOnClickListener { onClick() }
    }

    private fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val text = TextView(this).apply {
            this.text = label
            setTextColor(TEXT)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val toggle = Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }
        row.addView(text)
        row.addView(toggle)
        return row
    }

    private fun sliderRow(
        label: String,
        value: Int,
        min: Int,
        max: Int,
        step: Int,
        suffix: String,
        onChange: (Int) -> Unit
    ): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = TextView(this).apply {
            text = "$label: $value$suffix"
            setTextColor(TEXT)
            textSize = 14f
        }
        val bar = SeekBar(this).apply {
            this.max = (max - min) / step
            progress = (value - min) / step
        }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actual = min + progress * step
                header.text = "$label: $actual$suffix"
                if (fromUser) onChange(actual)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        container.addView(header)
        container.addView(bar)
        return container
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val BG = 0xFF0F1115.toInt()
        const val SURFACE = 0xFF1B2431.toInt()
        const val TEXT = 0xFFE7ECF6.toInt()
        const val MUTED = 0xFF93A1B8.toInt()
        const val ACCENT = 0xFF6E9BFF.toInt()
    }
}
