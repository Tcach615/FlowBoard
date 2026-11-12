package com.flowboard.keyboard.onboarding

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.flowboard.keyboard.gesture.Chord
import com.flowboard.keyboard.gesture.Direction
import com.flowboard.keyboard.settings.FlowBoardPreferences
import com.flowboard.keyboard.ui.ChordPadView

/**
 * Three steps: directions, all sixteen chords, then enabling the keyboard.
 * Practice uses the real gesture engine, not a simulation.
 */
class OnboardingActivity : Activity() {

    private lateinit var prefs: FlowBoardPreferences
    private lateinit var root: LinearLayout

    private val leftSeen = mutableSetOf<Direction>()
    private val rightSeen = mutableSetOf<Direction>()
    private val chordsSeen = mutableSetOf<Chord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = FlowBoardPreferences(this)
        val scroll = ScrollView(this).apply { setBackgroundColor(BG) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(32))
        }
        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(scroll)
        showWelcome()
    }

    private fun showWelcome() {
        clear()
        root.addView(title("Welcome to FlowBoard"))
        root.addView(
            body(
                "FlowBoard replaces letter hunting with two-thumb chords. Each thumb has four " +
                    "directions: NW, NE, SW and SE. Together they make sixteen chords, and each chord " +
                    "selects one candidate from the current context. Nothing is inserted until you " +
                    "commit a chord, and the ↺ button undoes the last insertion."
            )
        )
        root.addView(button("Start practice") { showDirections() })
        root.addView(button("Skip to setup") { showEnable() })
    }

    private fun showDirections() {
        leftSeen.clear()
        rightSeen.clear()
        clear()
        root.addView(title("Step 1 · Four directions"))
        root.addView(body("Put one thumb on each pad, drag both to a corner, then lift. Cover all four corners with both thumbs."))

        val progress = TextView(this).apply { setTextColor(TEXT); textSize = 14f }
        val pad = addPad()
        val next = button("Continue") { showChords() }.apply { isEnabled = false }

        pad.onChord = { chord ->
            leftSeen.add(chord.left)
            rightSeen.add(chord.right)
            progress.text = "Left ${leftSeen.size}/4 · Right ${rightSeen.size}/4"
            next.isEnabled = leftSeen.size == 4 && rightSeen.size == 4
        }

        root.addView(pad)
        root.addView(progress)
        root.addView(next)
        root.addView(secondaryButton("Skip for now") { showChords() })
    }

    private fun showChords() {
        chordsSeen.clear()
        clear()
        root.addView(title("Step 2 · All sixteen chords"))
        root.addView(body("Every combination of two directions is a chord. Try them all: the readout shows what FlowBoard detected."))

        val progress = TextView(this).apply { setTextColor(TEXT); textSize = 14f; text = "0 / 16" }
        val pad = addPad()
        val next = button("Continue") { showEnable() }.apply { isEnabled = false }

        pad.onChord = { chord ->
            chordsSeen.add(chord)
            progress.text = "${chordsSeen.size} / 16"
            next.isEnabled = chordsSeen.size == Chord.COUNT
        }

        root.addView(pad)
        root.addView(progress)
        root.addView(next)
        root.addView(secondaryButton("Skip for now") { showEnable() })
    }

    private fun showEnable() {
        clear()
        root.addView(title("Step 3 · Enable the keyboard"))
        root.addView(
            body(
                "1. Open the system keyboard settings and enable FlowBoard.\n" +
                    "2. Choose FlowBoard as the current input method.\n" +
                    "3. Open any text field and type with two thumbs."
            )
        )
        root.addView(button("Open keyboard settings") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(button("Choose input method") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        root.addView(button("Finish") {
            prefs.onboardingComplete = chordsSeen.size == Chord.COUNT
            startActivity(Intent(this, com.flowboard.keyboard.settings.SettingsActivity::class.java))
            finish()
        })
    }

    private fun addPad(): ChordPadView = ChordPadView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(260)
        )
    }

    private fun clear() = root.removeAllViews()

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(TEXT)
        textSize = 24f
        setPadding(0, 0, 0, dp(10))
    }

    private fun body(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(MUTED)
        textSize = 14f
        setLineSpacing(dp(4).toFloat(), 1f)
        setPadding(0, 0, 0, dp(12))
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(TEXT)
        setBackgroundColor(SURFACE)
        setOnClickListener { onClick() }
    }

    private fun secondaryButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(MUTED)
        setBackgroundColor(0x00000000)
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val BG = 0xFF0F1115.toInt()
        const val SURFACE = 0xFF1B2431.toInt()
        const val TEXT = 0xFFE7ECF6.toInt()
        const val MUTED = 0xFF93A1B8.toInt()
    }
}
