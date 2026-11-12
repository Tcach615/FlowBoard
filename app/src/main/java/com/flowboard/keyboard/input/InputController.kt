package com.flowboard.keyboard.input

import com.flowboard.keyboard.model.Candidate
import com.flowboard.keyboard.model.CandidateKind
import com.flowboard.keyboard.predict.ContextState
import com.flowboard.keyboard.predict.TextContext

/**
 * Owns the composing token, the linguistic context and the undo history.
 *
 * Nothing is inserted without an explicit [insert] call: the prediction engine only offers
 * candidates, it never chooses for the user.
 *
 * Undo history records what the action committed to the editor, so undo deletes that text
 * instead of only restoring the composing region.
 */
class InputController(
    var target: TextTarget,
    val context: TextContext = TextContext()
) {
    private data class UndoState(
        val composing: String,
        val context: ContextState,
        val committed: String
    )

    private val undoStack = ArrayDeque<UndoState>()
    private val composing = StringBuilder()

    var sensitive: Boolean = false
        private set

    var insertions: Int = 0
        private set
    var corrections: Int = 0
        private set

    val composingText: String get() = composing.toString()
    val canUndo: Boolean get() = undoStack.isNotEmpty()

    fun setSensitive(value: Boolean) {
        sensitive = value
        if (value) {
            // Never carry text from a normal field into a password field.
            context.clear()
            undoStack.clear()
            composing.setLength(0)
        }
    }

    fun resetForEditor() {
        composing.setLength(0)
        context.clear()
        undoStack.clear()
        target.finishComposing()
    }

    /** Inserts a candidate the user selected with a chord. */
    fun insert(candidate: Candidate) {
        pushUndo()
        when (candidate.kind) {
            CandidateKind.SPACE -> commitWithSpace()
            CandidateKind.PUNCTUATION -> commitWithPunctuation(candidate.text)
            else -> appendToken(candidate.text)
        }
        insertions += 1
    }

    fun backspace() {
        if (composing.isNotEmpty()) {
            pushUndo()
            composing.setLength(composing.length - 1)
            context.setPrefix(currentToken())
            if (composing.isEmpty()) target.finishComposing() else target.setComposingText(composing.toString())
        } else {
            // Already-committed text is not undoable; deleting it is final.
            target.deleteBackward(1)
        }
    }

    fun deleteForward() {
        target.deleteForward(1)
    }

    /** Reverts the last insertion. Returns false when there is nothing to undo. */
    fun undo(): Boolean {
        val state = undoStack.removeLastOrNull() ?: return false
        if (state.committed.isNotEmpty()) {
            target.deleteBackward(state.committed.length)
        }
        composing.setLength(0)
        composing.append(state.composing)
        context.restore(state.context)
        corrections += 1
        if (composing.isEmpty()) target.finishComposing() else target.setComposingText(composing.toString())
        return true
    }

    /** Explicit "this word is finished" action: commits without adding a space. */
    fun completeToken(): Boolean {
        if (composing.isEmpty()) return false
        pushUndo()
        val token = composing.toString()
        composing.setLength(0)
        context.commitPrefix()
        target.commitText(token)
        target.finishComposing()
        markCommitted(token)
        return true
    }

    /** Replaces the last insertion with another candidate. */
    fun replaceWith(candidate: Candidate): Boolean {
        if (!undo()) return false
        insert(candidate)
        return true
    }

    fun insertSpace() {
        insert(Candidate(" ", CandidateKind.SPACE))
    }

    /** Called when the editor moves the cursor or replaces text in a way we did not cause. */
    fun onEditorSelectionChanged() {
        if (composing.isNotEmpty()) {
            composing.setLength(0)
            context.setPrefix("")
            target.finishComposing()
        }
    }

    fun clearComposing() {
        composing.setLength(0)
        context.setPrefix("")
        target.finishComposing()
    }

    private fun appendToken(text: String) {
        composing.append(text)
        context.setPrefix(currentToken())
        target.setComposingText(composing.toString())
    }

    private fun commitWithSpace() {
        val outgoing = if (composing.isEmpty()) " " else composing.toString() + " "
        composing.setLength(0)
        context.commitPrefix()
        target.commitText(outgoing)
        target.finishComposing()
        markCommitted(outgoing)
    }

    private fun commitWithPunctuation(symbol: String) {
        val outgoing = composing.toString() + symbol
        composing.setLength(0)
        context.commitPrefix()
        target.commitText(outgoing)
        target.finishComposing()
        markCommitted(outgoing)
    }

    private fun currentToken(): String = composing.toString().substringAfterLast(' ')

    private fun pushUndo() {
        undoStack.addLast(UndoState(composing.toString(), context.snapshot(), ""))
        while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
    }

    private fun markCommitted(text: String) {
        val last = undoStack.lastOrNull() ?: return
        undoStack.removeLast()
        undoStack.addLast(last.copy(committed = text))
    }

    private companion object {
        const val MAX_UNDO = 40
    }
}
