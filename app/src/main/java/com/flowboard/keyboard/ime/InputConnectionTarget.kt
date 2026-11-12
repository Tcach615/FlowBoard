package com.flowboard.keyboard.ime

import android.view.inputmethod.InputConnection
import com.flowboard.keyboard.input.TextTarget

/** Thin adapter over the editor. All text goes through InputConnection. */
class InputConnectionTarget(private val connection: InputConnection?) : TextTarget {

    override fun setComposingText(text: String) {
        connection?.setComposingText(text, 1)
    }

    override fun commitText(text: String) {
        connection?.commitText(text, 1)
    }

    override fun finishComposing() {
        connection?.finishComposingText()
    }

    override fun deleteBackward(length: Int) {
        connection?.deleteSurroundingText(length, 0)
    }

    override fun deleteForward(length: Int) {
        connection?.deleteSurroundingText(0, length)
    }

    override fun textBeforeCursor(maxChars: Int): String? =
        connection?.getTextBeforeCursor(maxChars, 0)?.toString()

    override fun hasSelection(): Boolean =
        connection?.getSelectedText(0)?.isNotEmpty() == true
}
