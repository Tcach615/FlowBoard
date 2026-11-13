package com.flowboard.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import com.flowboard.keyboard.decode.ChordBoard
import com.flowboard.keyboard.gesture.Chord
import com.flowboard.keyboard.gesture.Direction
import com.flowboard.keyboard.gesture.ZoneLayout
import com.flowboard.keyboard.ime.FlowBoardImeService
import com.flowboard.keyboard.ime.InputConnectionTarget
import com.flowboard.keyboard.input.InputController
import com.flowboard.keyboard.settings.FlowBoardPreferences
import com.flowboard.keyboard.ui.KeyboardView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper

/**
 * Headless end-to-end typing: the real [FlowBoardImeService], the real [KeyboardView] and the
 * real engines, driven by synthetic two-pointer [android.view.MotionEvent]s, writing into a real
 * Android editor through [BaseInputConnection].
 *
 * This is the closest thing to typing on a device that runs without an emulator: every layer
 * below the system's own input plumbing is the production code path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HeadlessTypingTest {

    private val width = 1080
    private val height = 700

    private class Harness(
        val service: FlowBoardImeService,
        val view: KeyboardView,
        val editor: EditText,
        val connection: InputConnection
    )

    private fun boot(): Harness {
        val service = Robolectric.buildService(FlowBoardImeService::class.java).create().get()
        val view = service.onCreateInputView() as KeyboardView
        measure(view, width, height)
        val editor = EditText(RuntimeEnvironment.getApplication())
        // The editor's own connection: real Android editing code, no test double.
        val connection = editor.onCreateInputConnection(EditorInfo())!!
        setField(service, "controller", InputController(InputConnectionTarget(connection)))
        return Harness(service, view, editor, connection)
    }

    private fun measure(view: View, w: Int, h: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, w, h)
    }

    private fun field(instance: Any, name: String): Any? {
        var type: Class<*>? = instance.javaClass
        while (type != null) {
            try {
                val candidate = type.getDeclaredField(name)
                candidate.isAccessible = true
                return candidate.get(instance)
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        throw AssertionError("No field $name on ${instance.javaClass.name}")
    }

    private fun setField(instance: Any, name: String, value: Any?) {
        var type: Class<*>? = instance.javaClass
        while (type != null) {
            try {
                val candidate = type.getDeclaredField(name)
                candidate.isAccessible = true
                candidate.set(instance, value)
                return
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        throw AssertionError("No field $name on ${instance.javaClass.name}")
    }

    private fun layout(h: Harness): ZoneLayout =
        field(h.view, "zoneLayout") as? ZoneLayout ?: throw AssertionError("view not laid out")

    private fun mapping(h: Harness): Map<Chord, com.flowboard.keyboard.model.Candidate> =
        (field(h.service, "board") as ChordBoard).current

    private fun tapCommand(h: Harness, command: KeyboardView.Command) {
        @Suppress("UNCHECKED_CAST")
        val buttons = field(h.view, "buttons") as List<Pair<KeyboardView.Command, android.graphics.RectF>>
        val rect = buttons.first { it.first == command }.second
        HeadlessGesture.tap(h.view, rect.centerX(), rect.centerY())
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun play(h: Harness, chord: Chord) {
        assertTrue("view did not accept the gesture", HeadlessGesture.playChord(h.view, layout(h), chord))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a two-thumb chord inserts the candidate mapped to it`() {
        val h = boot()
        val chord = Chord(Direction.NW, Direction.NE)
        val expected = mapping(h)[chord]
        assertNotNull("no candidate was mapped to $chord", expected)

        play(h, chord)

        assertEquals(
            "chord $chord should insert '${expected!!.text}' but the editor holds '${h.editor.text}'",
            expected.text,
            h.editor.text.toString()
        )
    }

    @Test
    fun `every one of the sixteen chords inserts something distinct`() {
        val h = boot()
        val inserted = mutableListOf<String>()

        for (chord in Chord.ALL) {
            h.editor.setText("")
            play(h, chord)
            val text = h.editor.text.toString()
            assertTrue("chord $chord inserted nothing", text.isNotEmpty())
            inserted.add(text)
        }

        assertTrue(
            "chords collapsed onto the same text: $inserted",
            inserted.toSet().size >= Chord.COUNT - 6
        )
    }

    @Test
    fun `space commits the composing token with a trailing space and undo restores it`() {
        val h = boot()
        val chord = Chord(Direction.SW, Direction.SE)
        val expected = mapping(h)[chord]
        assertNotNull(expected)
        play(h, chord)
        val token = h.editor.text.toString()

        tapCommand(h, KeyboardView.Command.SPACE)
        assertEquals("$token ", h.editor.text.toString())

        tapCommand(h, KeyboardView.Command.UNDO)
        assertEquals(token, h.editor.text.toString())
    }

    @Test
    fun `backspace deletes a character from the editor when nothing is composing`() {
        val h = boot()
        h.editor.setText("hello")
        h.editor.setSelection(5)

        tapCommand(h, KeyboardView.Command.BACKSPACE)

        assertEquals("hell", h.editor.text.toString())
    }

    @Test
    fun `finish commits the token without adding a space`() {
        val h = boot()
        play(h, Chord(Direction.NE, Direction.NW))
        val token = h.editor.text.toString()

        tapCommand(h, KeyboardView.Command.COMPLETE)

        assertEquals(token, h.editor.text.toString())
    }

    @Test
    fun `a cancelled two-thumb gesture is rejected and inserts nothing`() {
        val h = boot()
        val chord = Chord(Direction.NE, Direction.SW)
        assertNotNull(mapping(h)[chord])

        HeadlessGesture.startChord(h.view, layout(h), chord)
        HeadlessGesture.cancel(h.view)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("a cancelled gesture inserted text", "", h.editor.text.toString())
        val status = field(h.view, "statusText") as String
        assertTrue("no rejection feedback shown, status='$status'", status.isNotEmpty())
    }

    @Test
    fun `a finger leaving its pad rejects the gesture`() {
        val h = boot()
        val zone = layout(h)
        HeadlessGesture.startChord(h.view, zone, Chord(Direction.NW, Direction.NE))

        h.view.dispatchTouchEvent(
            HeadlessGesture.singlePointer(
                android.view.MotionEvent.ACTION_MOVE, HeadlessGesture.LEFT_ID,
                zone.leftCenterX + zone.leftRadius * 5f, zone.leftCenterY, 50
            )
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("", h.editor.text.toString())
        assertEquals("Finger left its pad", field(h.view, "statusText"))
    }

    @Test
    fun `a password field inserts text but records no statistics`() {
        val h = boot()
        val prefs = FlowBoardPreferences(RuntimeEnvironment.getApplication())
        h.service.onStartInput(
            EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            },
            false
        )
        setField(h.service, "controller", InputController(InputConnectionTarget(h.connection)))

        play(h, Chord(Direction.SE, Direction.SW))

        assertTrue("password field inserted nothing", h.editor.text.toString().isNotEmpty())
        assertEquals(0, prefs.chordsTyped)
        assertEquals("private mode banner not shown", true, field(h.view, "privateMode"))
    }

    @Test
    fun `a normal field records the chord in statistics`() {
        val h = boot()
        val prefs = FlowBoardPreferences(RuntimeEnvironment.getApplication())
        h.service.onStartInput(
            EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT },
            false
        )
        setField(h.service, "controller", InputController(InputConnectionTarget(h.connection)))

        play(h, Chord(Direction.NW, Direction.SW))

        assertEquals(1, prefs.chordsTyped)
        assertFalse(field(h.view, "privateMode") as Boolean)
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    @Test
    fun `the keyboard draws a real frame that can be saved as a screenshot`() {
        val service = Robolectric.buildService(FlowBoardImeService::class.java).create().get()
        val view = service.onCreateInputView() as KeyboardView
        view.setComposingPreview("flow")
        view.showStatus("NW + NE  ·  flow")
        val w = 1233
        val h = 990
        measure(view, w, h)

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        val colours = HashSet<Int>()
        for (x in 0 until w step 7) {
            for (y in 0 until h step 7) colours.add(bitmap.getPixel(x, y))
        }
        assertTrue("frame is a single flat colour; nothing was rendered ($colours)", colours.size > 5)

        val dir = File("build/reports/headless").apply { mkdirs() }
        File(dir, "keyboard.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
