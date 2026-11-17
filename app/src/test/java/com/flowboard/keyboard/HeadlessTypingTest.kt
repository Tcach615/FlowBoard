package com.flowboard.keyboard

import android.graphics.RectF
import android.os.SystemClock
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import com.flowboard.keyboard.gesture.ZoneLayout
import com.flowboard.keyboard.ime.FlowBoardImeService
import com.flowboard.keyboard.ime.InputConnectionTarget
import com.flowboard.keyboard.input.InputController
import com.flowboard.keyboard.predict.InMemoryPersonalization
import com.flowboard.keyboard.steno.Fsl
import com.flowboard.keyboard.steno.StenoSession
import com.flowboard.keyboard.ui.KeyboardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

/**
 * Headless end-to-end typing: the real [FlowBoardImeService], the real [KeyboardView], the real
 * FSL-1 engines and dictionary, driven by synthetic two-pointer [MotionEvent]s and writing into a
 * real Android editor through [BaseInputConnection].
 *
 * The path exercised here is the production one: MotionEvent → GestureEngine → Chord → FSL-1
 * symbol → StenoSession → dictionary/predictor → explicit commit → InputConnection.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class HeadlessTypingTest {

    private val width = 1080
    private val height = 700

    private class Harness(
        val service: FlowBoardImeService,
        val view: KeyboardView,
        val editor: EditText,
        val connection: InputConnection,
        val controller: InputController
    ) {
        val session: StenoSession get() = HeadlessTypingTest.getField(service, "session")
    }

    private fun boot(): Harness {
        val service = Robolectric.buildService(FlowBoardImeService::class.java).create().get()
        val view = service.onCreateInputView() as KeyboardView
        measure(view)
        val editor = EditText(RuntimeEnvironment.getApplication())
        val connection = editor.onCreateInputConnection(EditorInfo())
            ?: BaseInputConnection(editor, true)
        val controller = InputController(InputConnectionTarget(connection))
        setField(service, "controller", controller)
        ShadowLooper.idleMainLooper()
        return Harness(service, view, editor, connection, controller)
    }

    private fun measure(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
    }

    private companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T> getField(target: Any, name: String): T {
            val field = target.javaClass.getDeclaredField(name)
            field.isAccessible = true
            return field.get(target) as T
        }

        fun <T> setField(target: Any, name: String, value: T) {
            val field = target.javaClass.getDeclaredField(name)
            field.isAccessible = true
            field.set(target, value)
        }
    }

    private fun layoutOf(view: View): ZoneLayout = getField(view, "zoneLayout")

    private fun chord(harness: Harness, sketch: String, startTime: Long = 100L) {
        val layout = layoutOf(harness.view)
        Fsl.parse(sketch).forEachIndexed { index, symbol ->
            HeadlessGesture.playChord(harness.view, layout, Fsl.chordOf(symbol), startTime + index * 300L)
            ShadowLooper.idleMainLooper()
        }
    }

    private fun tap(view: View, x: Float, y: Float) {
        val time = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(time, time + 24, MotionEvent.ACTION_UP, x, y, 0))
        ShadowLooper.idleMainLooper()
    }

    @Suppress("UNCHECKED_CAST")
    private fun press(view: KeyboardView, command: KeyboardView.Command) {
        val buttons = getField<List<Pair<KeyboardView.Command, RectF>>>(view, "buttons")
        val rect = buttons.first { it.first == command }.second
        tap(view, rect.centerX(), rect.centerY())
    }

    @Suppress("UNCHECKED_CAST")
    private fun chipRects(view: KeyboardView): List<Pair<Int, RectF>> = getField(view, "chips")

    @Test
    fun `bundled assets load inside the startup budget`() {
        val harness = boot()
        val failure = getField<String?>(harness.service, "loadFailure")
        assertTrue(
            "dictionary should be bundled (load failure: $failure)",
            harness.service.dictionarySize > 15_000
        )
        assertTrue("view should be created", harness.view.width > 0)
    }

    @Test
    fun `a chord never inserts text on its own`() {
        val harness = boot()
        chord(harness, "COR·A")
        assertEquals("", harness.editor.text.toString())
        assertEquals(1, harness.session.chordCount)
        assertEquals("COR·A", harness.session.snapshot().key)
    }

    @Test
    fun `explicit space commits the top translation`() {
        val harness = boot()
        chord(harness, "COR·F COR·∅")
        val expected = harness.session.candidates().first().entry.word
        press(harness.view, KeyboardView.Command.SPACE)
        assertEquals("$expected ", harness.editor.text.toString())
        assertEquals(0, harness.session.chordCount)
    }

    @Test
    fun `all sixteen physical chords reach the language engine`() {
        val harness = boot()
        val layout = layoutOf(harness.view)
        Fsl.ALL_CHORDS.forEachIndexed { index, chord ->
            HeadlessGesture.playChord(harness.view, layout, chord, 100L + index * 300L)
            ShadowLooper.idleMainLooper()
        }
        assertEquals("every chord should commit one stroke", 16, harness.session.chordCount)
        assertEquals(
            Fsl.ALL_CHORDS.map { Fsl.symbolOf(it).label },
            harness.session.chords.map { Fsl.symbolOf(it).label }
        )
    }

    @Test
    fun `tapping a candidate chip commits that reading`() {
        val harness = boot()
        chord(harness, "COR·F COR·∅")
        val words = harness.session.candidates().map { it.entry.word }
        assertTrue("needs several readings to test selection", words.size >= 3)
        val target = 2
        val rect = chipRects(harness.view)[target].second
        tap(harness.view, rect.centerX(), rect.centerY())
        assertEquals("${words[target]} ", harness.editor.text.toString())
    }

    @Test
    fun `alternatives command changes which reading is committed`() {
        val harness = boot()
        chord(harness, "COR·F COR·∅")
        val words = harness.session.candidates().map { it.entry.word }
        press(harness.view, KeyboardView.Command.ALTERNATIVES)
        press(harness.view, KeyboardView.Command.SPACE)
        assertEquals("${words[1]} ", harness.editor.text.toString())
    }

    @Test
    fun `complete inserts without a trailing space`() {
        val harness = boot()
        chord(harness, "SIB·U")
        val word = harness.session.candidates().first().entry.word
        press(harness.view, KeyboardView.Command.COMPLETE)
        assertEquals(word, harness.editor.text.toString())
    }

    @Test
    fun `undo deletes the committed text and restores the strokes`() {
        val harness = boot()
        chord(harness, "COR·A")
        val word = harness.session.candidates().first().entry.word
        press(harness.view, KeyboardView.Command.SPACE)
        assertEquals("$word ", harness.editor.text.toString())

        press(harness.view, KeyboardView.Command.UNDO)
        assertEquals("", harness.editor.text.toString())
        assertEquals("the sketch must come back, not just the text", 1, harness.session.chordCount)
        assertTrue(harness.session.candidates().map { it.entry.word }.contains(word))
    }

    @Test
    fun `backspace removes strokes before it touches committed text`() {
        val harness = boot()
        harness.editor.setText("ab")
        harness.editor.setSelection(2)
        chord(harness, "DOR·F COR·∅")
        press(harness.view, KeyboardView.Command.BACKSPACE)
        assertEquals("DOR·F", harness.session.snapshot().key)
        assertEquals("ab", harness.editor.text.toString())
        press(harness.view, KeyboardView.Command.BACKSPACE)
        press(harness.view, KeyboardView.Command.BACKSPACE)
        assertEquals("a", harness.editor.text.toString())
    }

    @Test
    fun `password fields stay private and do not learn from typing`() {
        val harness = boot()
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        harness.service.onStartInput(info, false)
        setField(harness.service, "controller", harness.controller)
        chord(harness, "COR·A", startTime = 5_000L)
        press(harness.view, KeyboardView.Command.SPACE)

        assertTrue("controller should be in sensitive mode", harness.controller.sensitive)
        assertTrue("view should show the private banner", getField(harness.view, "privateMode"))
        val personal = getField<InMemoryPersonalization>(harness.service, "personalization")
        val committed = harness.session.context.committedWords
        assertEquals("no personalisation in a password field", 0, committed.size + personal.count("the"))
    }

    @Test
    fun `uri fields are not mistaken for password fields`() {
        val harness = boot()
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        harness.service.onStartInput(info, false)
        setField(harness.service, "controller", harness.controller)
        assertFalse(getField(harness.view, "privateMode"))
        assertFalse(harness.controller.sensitive)
    }

    @Test
    fun `cancelled gestures leave the sketch untouched`() {
        val harness = boot()
        chord(harness, "COR·A")
        HeadlessGesture.cancel(harness.view, 9_000L)
        ShadowLooper.idleMainLooper()
        assertEquals(1, harness.session.chordCount)
        assertEquals("COR·A", harness.session.snapshot().key)
    }

    @Test
    fun `an incomplete consonant-only sketch cannot be committed`() {
        val harness = boot()
        chord(harness, "COR·∅")
        press(harness.view, KeyboardView.Command.SPACE)
        assertEquals("", harness.editor.text.toString())
        assertEquals(1, harness.session.chordCount)
    }
}
