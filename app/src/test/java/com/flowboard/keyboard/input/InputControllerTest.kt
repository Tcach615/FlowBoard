package com.flowboard.keyboard.input

import com.flowboard.keyboard.model.Candidate
import com.flowboard.keyboard.model.CandidateKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputControllerTest {

    private class FakeTarget : TextTarget {
        val committed = StringBuilder()
        var composing: String = ""
        var deletions = 0

        override fun setComposingText(text: String) {
            composing = text
        }

        override fun commitText(text: String) {
            committed.append(text)
            composing = ""
        }

        override fun finishComposing() {
            composing = ""
        }

        override fun deleteBackward(length: Int) {
            deletions += length
            val end = committed.length
            val start = (end - length).coerceAtLeast(0)
            committed.delete(start, end)
        }

        override fun deleteForward(length: Int) {
            deletions += length
            val end = (length).coerceAtMost(committed.length)
            committed.delete(0, end)
        }

        override fun textBeforeCursor(maxChars: Int): String = committed.toString().takeLast(maxChars)

        override fun hasSelection(): Boolean = false
    }

    private fun controller(): Pair<InputController, FakeTarget> {
        val target = FakeTarget()
        return InputController(target) to target
    }

    private fun word(text: String) = Candidate(text, CandidateKind.WORD)

    @Test
    fun `candidate is composed but not committed until asked`() {
        val (controller, target) = controller()
        controller.insert(word("hello"))
        assertEquals("hello", target.composing)
        assertEquals("", target.committed.toString())
        assertEquals("hello", controller.composingText)
    }

    @Test
    fun `space commits the token and starts a new one`() {
        val (controller, target) = controller()
        controller.insert(word("hello"))
        controller.insertSpace()
        assertEquals("hello ", target.committed.toString())
        assertEquals("", controller.composingText)
        assertEquals("hello", controller.context.previousWord)
    }

    @Test
    fun `space on empty composing still inserts a single space`() {
        val (controller, target) = controller()
        controller.insertSpace()
        assertEquals(" ", target.committed.toString())
    }

    @Test
    fun `punctuation appends to the composing token`() {
        val (controller, target) = controller()
        controller.insert(word("world"))
        controller.insert(Candidate(".", CandidateKind.PUNCTUATION))
        assertEquals("world.", target.committed.toString())
        assertEquals("world", controller.context.previousWord)
    }

    @Test
    fun `complete token commits without a space`() {
        val (controller, target) = controller()
        controller.insert(word("hello"))
        assertTrue(controller.completeToken())
        assertEquals("hello", target.committed.toString())
        assertEquals("", controller.composingText)
    }

    @Test
    fun `complete token on empty composing does nothing`() {
        val (controller, target) = controller()
        assertFalse(controller.completeToken())
        assertEquals("", target.committed.toString())
    }

    @Test
    fun `backspace edits the composing token`() {
        val (controller, target) = controller()
        controller.insert(word("hello"))
        controller.backspace()
        assertEquals("hell", target.composing)
        assertEquals(0, target.deletions)
    }

    @Test
    fun `backspace deletes from the editor when nothing is composing`() {
        val (controller, target) = controller()
        controller.backspace()
        assertEquals(1, target.deletions)
        assertFalse(controller.canUndo)
    }

    @Test
    fun `undo removes the committed text and restores the composing token`() {
        val (controller, target) = controller()
        controller.insert(word("hello"))
        controller.insertSpace()
        assertEquals("hello ", target.committed.toString())

        assertTrue(controller.undo())

        assertEquals("", target.committed.toString())
        assertEquals("hello", target.composing)
        assertNull(controller.context.previousWord)
    }

    @Test
    fun `undo after a plain insertion restores the previous token`() {
        val (controller, target) = controller()
        controller.insert(word("hello"))
        assertEquals("hello", controller.composingText)
        controller.insert(word("!"))
        assertEquals("hello!", controller.composingText)
        assertTrue(controller.undo())
        assertEquals("hello", target.composing)
    }

    @Test
    fun `undo with empty history reports false`() {
        val (controller, _) = controller()
        assertFalse(controller.undo())
    }

    @Test
    fun `replaceWith swaps the last candidate`() {
        val (controller, target) = controller()
        controller.insert(word("hello"))
        assertTrue(controller.replaceWith(word("help")))
        assertEquals("help", target.composing)
        assertEquals("", target.committed.toString())
    }

    @Test
    fun `sensitive mode drops context and history`() {
        val (controller, _) = controller()
        controller.insert(word("secret"))
        controller.insertSpace()
        assertEquals("secret", controller.context.previousWord)

        controller.setSensitive(true)

        assertNull(controller.context.previousWord)
        assertFalse(controller.canUndo)
        assertEquals("", controller.composingText)
    }

    @Test
    fun `editor switch clears the composing token`() {
        val (controller, _) = controller()
        controller.insert(word("half"))
        controller.resetForEditor()
        assertEquals("", controller.composingText)
    }

    @Test
    fun `insertion counts are tracked`() {
        val (controller, _) = controller()
        controller.insert(word("a"))
        controller.insertSpace()
        assertEquals(2, controller.insertions)
    }
}
