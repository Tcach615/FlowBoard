package com.flowboard.keyboard.input

/**
 * The editor operations the controller needs. Abstracting them away from
 * android.view.inputmethod.InputConnection keeps the controller unit testable.
 */
interface TextTarget {
    fun setComposingText(text: String)
    fun commitText(text: String)
    fun finishComposing()
    fun deleteBackward(length: Int)
    fun deleteForward(length: Int)
    fun textBeforeCursor(maxChars: Int): String?
    fun hasSelection(): Boolean
}
