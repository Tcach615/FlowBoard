package com.flowboard.keyboard.ime

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import com.flowboard.keyboard.input.InputController
import com.flowboard.keyboard.gesture.Chord
import com.flowboard.keyboard.predict.InMemoryPersonalization
import com.flowboard.keyboard.settings.FlowBoardPreferences
import com.flowboard.keyboard.steno.LanguageModel
import com.flowboard.keyboard.steno.StenoDictionary
import com.flowboard.keyboard.steno.StenoEngine
import com.flowboard.keyboard.steno.StenoPredictor
import com.flowboard.keyboard.steno.StenoSession
import com.flowboard.keyboard.steno.TranslationEngine
import com.flowboard.keyboard.ui.KeyboardView
import java.util.zip.GZIPInputStream

/**
 * The system keyboard, now driven by FSL-1.
 *
 * Ownership is unchanged in spirit: the service owns every engine and decides what is inserted.
 * What changed is the linguistic layer — the pads emit fixed phonetic symbols, the dictionary and
 * predictor only *propose*, and text is inserted only when the user commits a translation.
 */
class FlowBoardImeService : InputMethodService(), KeyboardView.Listener {

    private lateinit var preferences: FlowBoardPreferences
    private lateinit var personalization: InMemoryPersonalization
    private lateinit var controller: InputController
    private lateinit var engine: StenoEngine

    private var dictionary: StenoDictionary = StenoDictionary.empty()
    private var languageModel: LanguageModel = LanguageModel.empty()
    private var predictor: StenoPredictor? = null
    private var translator: TranslationEngine? = null
    private var session: StenoSession? = null
    private var keyboardView: KeyboardView? = null
    private var sensitive = false

    /** Load failures are surfaced in the preview bar instead of crashing the IME. */
    private var loadFailure: String? = null

    /** Measured cold-start cost of parsing the bundled assets; reported by the tests/report. */
    internal var assetLoadMillis: Long = 0L
        private set

    internal var dictionarySize: Int = 0
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = FlowBoardPreferences(this)
        personalization = InMemoryPersonalization()
        engine = StenoEngine()
        controller = InputController(InputConnectionTarget(null))
        loadModels()
    }

    private fun loadModels() {
        val started = SystemClock.uptimeMillis()
        runCatching {
            val loadedDictionary = readGzipAsset("steno_en.tsvz").use { StenoDictionary.load(it) }
            val loadedModel = readGzipAsset("lm_en.tsvz").use { LanguageModel.load(it) }
            dictionary = loadedDictionary
            languageModel = loadedModel
            dictionarySize = loadedDictionary.size
        }.onFailure { error ->
            loadFailure = "Dictionary unavailable: ${error.message ?: error.javaClass.simpleName}"
            dictionary = StenoDictionary.empty()
            languageModel = LanguageModel.empty()
        }
        attachModels()
        assetLoadMillis = SystemClock.uptimeMillis() - started
    }

    /**
     * Reads a gzip-compressed asset. The suffix is not `.gz` on purpose: aapt2 decompresses and
     * renames `.gz` assets, so a plain `.tsv` fallback keeps the IME working if a future toolchain
     * strips the compression again.
     */
    private fun readGzipAsset(name: String): java.io.InputStream {
        val gzipped = runCatching { assets.open(name) }.getOrNull()
        if (gzipped != null) return GZIPInputStream(gzipped)
        val plain = name.removeSuffix("z")
        return assets.open(plain)
    }

    private fun attachModels() {
        if (dictionary.size == 0) {
            predictor = StenoPredictor(dictionary, languageModel)
            translator = TranslationEngine(engine, predictor!!)
            session = StenoSession(engine, translator!!, predictor!!)
            return
        }
        val stenoPredictor = StenoPredictor(
            dictionary = dictionary,
            languageModel = languageModel,
            personalization = personalization
        )
        val translationEngine = TranslationEngine(engine, stenoPredictor)
        predictor = stenoPredictor
        translator = translationEngine
        session = StenoSession(engine, translationEngine, stenoPredictor, personalization)
    }

    override fun onCreateInputView(): View {
        val view = KeyboardView(this)
        view.listener = this
        view.applyPreferences(preferences)
        keyboardView = view
        loadFailure?.let { view.showStatus(it) }
        refresh()
        return view
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        sensitive = isSensitive(attribute)
        controller.target = InputConnectionTarget(currentInputConnection)
        controller.setSensitive(sensitive)
        controller.resetForEditor()
        session?.resetForEditor()
        if (sensitive) session?.context?.clear()
        refresh()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        controller.target = InputConnectionTarget(currentInputConnection)
        keyboardView?.applyPreferences(preferences)
        keyboardView?.setPrivateMode(sensitive)
        refresh()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        session?.clearSketch()
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
        // The cursor moved outside anything we were composing: drop the half-written sketch so a
        // later commit cannot insert a word meant for the old caret position.
        if (candidatesStart < 0 && (session?.chordCount ?: 0) > 0) {
            session?.clearSketch()
            refresh()
        }
    }

    override fun onChord(chord: Chord) {
        val current = session ?: return
        val state = current.addChord(chord)
        if (!sensitive) preferences.recordChord()
        keyboardView?.setSession(state)
        keyboardView?.setPrivateMode(sensitive)
        val preview = when (val translation = state.translation) {
            is com.flowboard.keyboard.steno.Translation.Candidates -> translation.top.entry.word
            is com.flowboard.keyboard.steno.Translation.Incomplete ->
                if (state.chordCount == 1) translation.reason else ""
            com.flowboard.keyboard.steno.Translation.Empty -> ""
        }
        keyboardView?.showStatus(preview)
    }

    override fun onCommand(command: KeyboardView.Command) {
        val current = session ?: return
        when (command) {
            KeyboardView.Command.BACKSPACE -> {
                if (current.backspace()) {
                    refresh()
                    keyboardView?.showStatus("Stroke removed")
                } else {
                    controller.backspace()
                }
            }

            KeyboardView.Command.SPACE -> commit(withSpace = true)

            KeyboardView.Command.COMPLETE -> commit(withSpace = false)

            KeyboardView.Command.UNDO -> {
                val deleted = current.undo()
                if (deleted != null) {
                    controller.target.deleteBackward(deleted.length)
                    if (!sensitive) preferences.recordCorrection()
                    refresh()
                    keyboardView?.showStatus("Undone")
                } else if (current.backspace()) {
                    refresh()
                }
            }

            KeyboardView.Command.ALTERNATIVES -> {
                val index = current.cycleAlternative()
                val count = current.candidates().size
                refresh()
                keyboardView?.showStatus(if (count > 0) "Alternative ${index + 1}/$count" else "No alternatives")
                if (count > 1 && !sensitive) preferences.recordCorrection()
            }
        }
    }

    override fun onSelectCandidate(index: Int) {
        val current = session ?: return
        current.select(index)
        commit(withSpace = true)
    }

    private fun commit(withSpace: Boolean) {
        val current = session ?: return
        val text = current.accept(withSpace, remember = !sensitive)
        if (text == null) {
            keyboardView?.showStatus("Not a word yet")
            return
        }
        controller.target.commitText(text)
        controller.target.finishComposing()
        // A password field keeps no linguistic state: no personalisation, no context.
        if (sensitive) current.context.clear()
        refresh()
        keyboardView?.showStatus(text.trim())
    }

    private fun refresh() {
        val current = session ?: return
        keyboardView?.setSession(current.snapshot())
        keyboardView?.setPrivateMode(sensitive)
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
}
