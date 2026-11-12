package com.flowboard.keyboard.ime

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import com.flowboard.keyboard.decode.ChordBoard
import com.flowboard.keyboard.gesture.Chord
import com.flowboard.keyboard.input.InputController
import com.flowboard.keyboard.model.Candidate
import com.flowboard.keyboard.predict.InMemoryPersonalization
import com.flowboard.keyboard.predict.PredictionEngine
import com.flowboard.keyboard.predict.Vocabulary
import com.flowboard.keyboard.predict.VocabularyParser
import com.flowboard.keyboard.settings.FlowBoardPreferences
import com.flowboard.keyboard.ui.KeyboardView

/**
 * The system keyboard. It owns the engines and forwards editor operations to
 * [InputController]; the view only reports chords and toolbar commands.
 */
class FlowBoardImeService : InputMethodService(), KeyboardView.Listener {

    private lateinit var preferences: FlowBoardPreferences
    private lateinit var vocabulary: Vocabulary
    private lateinit var predictor: PredictionEngine
    private lateinit var strictPredictor: PredictionEngine
    private lateinit var personalization: InMemoryPersonalization
    private lateinit var controller: InputController

    private val board = ChordBoard()
    private var keyboardView: KeyboardView? = null
    private var sensitive = false

    override fun onCreate() {
        super.onCreate()
        preferences = FlowBoardPreferences(this)
        personalization = InMemoryPersonalization()
        vocabulary = loadVocabulary(preferences.language)
        predictor = PredictionEngine(vocabulary, personalization = personalization)
        // Password fields get no personalization or context reuse.
        strictPredictor = PredictionEngine(vocabulary, personalization = null)
        controller = InputController(InputConnectionTarget(null))
    }

    override fun onCreateInputView(): View {
        val view = KeyboardView(this)
        view.listener = this
        view.applyPreferences(preferences)
        keyboardView = view
        refreshBoard(assign = true)
        return view
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        sensitive = isSensitive(attribute)
        controller.target = InputConnectionTarget(currentInputConnection)
        controller.setSensitive(sensitive)
        controller.resetForEditor()
        board.clear()
        refreshBoard(assign = true)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        controller.target = InputConnectionTarget(currentInputConnection)
        // Pick up settings changed while the keyboard was hidden.
        keyboardView?.applyPreferences(preferences)
        keyboardView?.setPrivateMode(sensitive)
        refreshBoard(assign = true)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller.clearComposing()
        keyboardView?.resetGesture()
        super.onFinishInputView(finishingInput)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        // The editor dropped our composing region (cursor moved, field switched): stay in sync.
        if (candidatesStart < 0 && controller.composingText.isNotEmpty()) {
            controller.onEditorSelectionChanged()
            refreshBoard(assign = true)
            updatePreview()
        }
    }

    override fun onChord(chord: Chord) {
        val candidate = board.candidateFor(chord)
        if (candidate == null) {
            keyboardView?.showStatus("No candidate on ${chord.left.label} + ${chord.right.label}")
            return
        }
        controller.insert(candidate)
        if (!sensitive) {
            if (preferences.personalizationEnabled) personalization.record(candidate.text)
            preferences.recordChord()
        }
        keyboardView?.showStatus("${chord.left.label} + ${chord.right.label}  ·  ${candidate.text.trim()}")
        refreshBoard(assign = false)
        updatePreview()
    }

    override fun onCommand(command: KeyboardView.Command) {
        when (command) {
            KeyboardView.Command.BACKSPACE -> {
                controller.backspace()
                refreshBoard(assign = false)
            }
            KeyboardView.Command.SPACE -> {
                controller.insertSpace()
                refreshBoard(assign = true)
            }
            KeyboardView.Command.COMPLETE -> {
                controller.completeToken()
                refreshBoard(assign = true)
            }
            KeyboardView.Command.UNDO -> {
                if (controller.undo()) {
                    preferences.recordCorrection()
                    refreshBoard(assign = true)
                }
            }
            KeyboardView.Command.ALTERNATIVES -> {
                if (controller.undo()) {
                    preferences.recordCorrection()
                    refreshBoard(assign = false)
                }
            }
        }
        updatePreview()
    }

    private fun refreshBoard(assign: Boolean) {
        val candidates: List<Candidate> =
            (if (sensitive) strictPredictor else predictor).predict(controller.context, CANDIDATE_SLOTS)
        if (assign) board.update(candidates)
        keyboardView?.let { view ->
            view.setBoard(board.current)
            view.setPrivateMode(sensitive)
        }
        updatePreview()
    }

    private fun updatePreview() {
        keyboardView?.setComposingPreview(controller.composingText)
    }

    private fun isSensitive(info: EditorInfo?): Boolean {
        if (info == null) return false
        val type = info.inputType
        if ((type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return true
        val variation = type and InputType.TYPE_MASK_VARIATION
        // Compare variations exactly: the constants share bits, so masking would
        // misclassify e.g. TYPE_TEXT_VARIATION_URI as a password field.
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun loadVocabulary(language: String): Vocabulary {
        val assetName = "vocabulary_$language.json"
        val json = runCatching {
            assets.open(assetName).bufferedReader().use { it.readText() }
        }.recoverCatching {
            assets.open("vocabulary_en.json").bufferedReader().use { it.readText() }
        }.getOrNull()
        if (json == null) return Vocabulary(com.flowboard.keyboard.predict.VocabularyData(language = "en"))
        return runCatching { Vocabulary(VocabularyParser.parse(json)) }
            .getOrElse { Vocabulary(com.flowboard.keyboard.predict.VocabularyData(language = "en")) }
    }

    private companion object {
        const val CANDIDATE_SLOTS = 16
    }
}
