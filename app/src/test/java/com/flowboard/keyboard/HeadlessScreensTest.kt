package com.flowboard.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import com.flowboard.keyboard.gesture.Chord
import com.flowboard.keyboard.gesture.ZoneLayout
import com.flowboard.keyboard.onboarding.OnboardingActivity
import com.flowboard.keyboard.settings.FlowBoardPreferences
import com.flowboard.keyboard.settings.SettingsActivity
import com.flowboard.keyboard.ui.ChordPadView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Headless checks of the app-side screens (settings, onboarding) and of the keyboard's drawing.
 * Screens are laid out and drawn into a real [Bitmap], so a rendering crash fails the test and
 * the resulting PNGs can be inspected without a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class HeadlessScreensTest {

    private val pixelWidth = 1233
    private val pixelHeight = 2673

    private fun <T : View> allViews(root: View, type: Class<T>): List<T> {
        val found = mutableListOf<T>()
        if (type.isInstance(root)) found.add(type.cast(root)!!)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) found.addAll(allViews(root.getChildAt(index), type))
        }
        return found
    }

    private fun content(activity: android.app.Activity): ViewGroup =
        activity.findViewById(android.R.id.content)

    private fun button(root: View, label: String): Button =
        allViews(root, Button::class.java).firstOrNull { it.text.toString() == label }
            ?: throw AssertionError("no button labelled '$label'")

    private fun text(root: View, label: String): TextView? =
        allViews(root, TextView::class.java).firstOrNull { it.text.toString() == label }

    private fun switchFor(root: View, label: String): Switch {
        for (row in allViews(root, ViewGroup::class.java)) {
            val labelView = (0 until row.childCount)
                .map { row.getChildAt(it) }
                .filterIsInstance<TextView>()
                .firstOrNull { it.text.toString() == label } ?: continue
            return (0 until row.childCount)
                .map { row.getChildAt(it) }
                .filterIsInstance<Switch>()
                .firstOrNull()
                ?: throw AssertionError("row '$label' has no switch (found $labelView)")
        }
        throw AssertionError("no switch row labelled '$label'")
    }

    private fun render(root: View, name: String): Set<Int> {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(pixelWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(pixelHeight, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, pixelWidth, pixelHeight)
        shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val colours = HashSet<Int>()
        for (x in 0 until pixelWidth step 11) {
            for (y in 0 until pixelHeight step 11) colours.add(bitmap.getPixel(x, y))
        }
        val dir = File("build/reports/headless").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return colours
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `settings screen draws and its switches persist`() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val root = content(activity)
        val prefs = FlowBoardPreferences(activity)

        val colours = render(root, "settings")
        assertTrue("settings screen rendered a flat colour ($colours)", colours.size > 5)

        assertTrue(text(root, "FlowBoard") != null)
        val lightTheme = switchFor(root, "Light theme")
        assertFalse(lightTheme.isChecked)
        lightTheme.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("light", prefs.theme)

        val stats = allViews(root, TextView::class.java)
            .firstOrNull { it.text.toString().startsWith("Chords committed") }
        assertNotNull("statistics row missing", stats)
        assertTrue(stats!!.text.contains("0"))
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `onboarding walks from welcome to the enable step`() {
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java).setup().get()
        var root = content(activity)
        assertTrue(render(root, "onboarding-1-welcome").size > 5)

        button(root, "Start practice").performClick()
        shadowOf(Looper.getMainLooper()).idle()
        root = content(activity)
        assertNotNull(text(root, "Step 1 · Four directions"))

        val pad = allViews(root, ChordPadView::class.java).first()
        pad.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        )
        pad.layout(0, 0, 1080, 600)
        val layout = padLayout(pad)

        val continueButton = button(root, "Continue")
        assertFalse("Continue should start disabled", continueButton.isEnabled)
        for (direction in com.flowboard.keyboard.gesture.Direction.entries) {
            HeadlessGesture.playChord(pad, layout, Chord(direction, direction))
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Continue stayed disabled after covering all four directions", continueButton.isEnabled)

        continueButton.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        root = content(activity)
        assertNotNull(text(root, "Step 2 · All sixteen chords"))

        val pad2 = allViews(root, ChordPadView::class.java).first()
        pad2.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        )
        pad2.layout(0, 0, 1080, 600)
        val layout2 = padLayout(pad2)
        val next = button(root, "Continue")
        for (chord in Chord.ALL) HeadlessGesture.playChord(pad2, layout2, chord)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Continue stayed disabled after all sixteen chords", next.isEnabled)
        assertTrue(render(root, "onboarding-2-chords").size > 5)

        next.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        root = content(activity)
        assertNotNull(text(root, "Step 3 · Enable the keyboard"))
        assertTrue(render(root, "onboarding-3-enable").size > 5)
    }

    private fun padLayout(pad: ChordPadView): ZoneLayout {
        val field = ChordPadView::class.java.getDeclaredField("layout")
        field.isAccessible = true
        return field.get(pad) as? ZoneLayout ?: throw AssertionError("pad has no layout")
    }
}
