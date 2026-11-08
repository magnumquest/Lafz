/*
 * Copyright (C) 2008 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin.suggestions

import android.content.Context
import android.text.TextUtils
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils
import helium314.keyboard.compat.CompatBreakIterator
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.internal.keyboard_parser.getEmojiDefaultVersion
import helium314.keyboard.latin.CapsMode
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.DictionaryFacilitator
import helium314.keyboard.latin.InputStyle
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.define.DecoderSpecificConstants.SHOULD_AUTO_CORRECT_USING_NON_WHITE_LISTED_SUGGESTION
import helium314.keyboard.latin.define.DecoderSpecificConstants.SHOULD_REMOVE_PREVIOUSLY_REJECTED_SUGGESTION
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.GESTURE_ENGINE_FUTO
import helium314.keyboard.latin.settings.GESTURE_ENGINE_GOOGLE
import helium314.keyboard.latin.settings.GESTURE_ENGINE_LAFZ
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.AutoCorrectionUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.BackgroundGatheringCache
import helium314.keyboard.latin.utils.TextAtCursor
import helium314.keyboard.latin.utils.WordData
import helium314.keyboard.latin.utils.useBackgroundGathering
import helium314.keyboard.latin.urdu.Honorifics
import helium314.keyboard.latin.urdu.ReligiousShortcuts
import helium314.keyboard.latin.urdu.EmojiKeywords
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import java.io.InputStreamReader
import helium314.keyboard.latin.urdu.RomanScript
import helium314.keyboard.latin.gesture.FutoSwipe
import helium314.keyboard.latin.gesture.SwipeDecoder
import helium314.keyboard.latin.urdu.RomanTransliterator
import helium314.keyboard.latin.utils.JniUtils
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * This class loads a dictionary and provides a list of suggestions for a given sequence of
 * characters. This includes corrections and completions.
 */
class Suggest(private val mDictionaryFacilitator: DictionaryFacilitator) {
    private var mAutoCorrectionThreshold = 0f
    private val mPlausibilityThreshold = 0f
    private val nextWordSuggestionsCache = HashMap<NgramContext, SuggestionResults>()
    private val swipeWords = java.util.concurrent.ConcurrentHashMap<RomanScript, Map<String, Int>>()

    // cache cleared whenever LatinIME.loadSettings is called, notably on changing layout and switching input fields
    fun clearNextWordSuggestionsCache() = nextWordSuggestionsCache.clear()

    /**
     * Set the normalized-score threshold for a suggestion to be considered strong enough that we
     * will auto-correct to this.
     * @param threshold the threshold
     */
    fun setAutoCorrectionThreshold(threshold: Float) {
        mAutoCorrectionThreshold = threshold
    }

    // todo: remove when InputLogic is ready
    interface OnGetSuggestedWordsCallback {
        fun onGetSuggestedWords(suggestedWords: SuggestedWords)
    }

    fun getSuggestedWords(wordComposer: WordComposer, textAtCursor: TextAtCursor?, ngramContext: NgramContext, keyboard: Keyboard,
                          settingsValuesForSuggestion: SettingsValuesForSuggestion, isCorrectionEnabled: Boolean,
                          inputStyle: InputStyle, sequenceNumber: Int): SuggestedWords =
        if (wordComposer.isBatchMode) {
            getSuggestedWordsForBatchInput(wordComposer, ngramContext, keyboard, settingsValuesForSuggestion,
                inputStyle, isCorrectionEnabled, sequenceNumber)
        } else {
            getSuggestedWordsForNonBatchInput(wordComposer, textAtCursor, ngramContext, keyboard, settingsValuesForSuggestion,
                inputStyle, isCorrectionEnabled, sequenceNumber)
        }

    // Retrieves suggestions for non-batch input (typing, recorrection, predictions...)
    // and calls the callback function with the suggestions.
    private fun getSuggestedWordsForNonBatchInput(wordComposer: WordComposer, textAtCursor: TextAtCursor?, ngramContext: NgramContext, keyboard: Keyboard,
                      settingsValuesForSuggestion: SettingsValuesForSuggestion, inputStyleIfNotPrediction: InputStyle,
                      isCorrectionEnabled: Boolean, sequenceNumber: Int): SuggestedWords {
        val typedWordString = wordComposer.typedWord
        RomanScript.of(mDictionaryFacilitator.mainLocale)?.let { script ->
            getRomanScriptSuggestions(script, wordComposer, ngramContext, inputStyleIfNotPrediction, sequenceNumber, keyboard, settingsValuesForSuggestion)?.let { return it }
        }
        val resultsArePredictions = !wordComposer.isComposingWord
        val suggestionResults = if (typedWordString.isEmpty())
                getNextWordSuggestions(ngramContext, keyboard, inputStyleIfNotPrediction, settingsValuesForSuggestion)
            else mDictionaryFacilitator.getSuggestionResults(wordComposer.composedDataSnapshot, ngramContext, keyboard,
                settingsValuesForSuggestion, SESSION_ID_TYPING, inputStyleIfNotPrediction)
        val trailingSingleQuotesCount = StringUtils.getTrailingSingleQuotesCount(typedWordString)
        val capsMode = getCapsModeForTyping(wordComposer, keyboard)
        val suggestionsContainer = ArrayList(suggestionResults)

        // add suggestions for the "extended word" at cursor
        // using a different iterator that also includes digits and maybe more
        val extendedWord = textAtCursor?.getWordAtCursor(CompatBreakIterator.getWordInstance(keyboard.mId.locale))?.word
        if (extendedWord != null && typedWordString.isNotEmpty() && extendedWord.length > typedWordString.length) {
            // ideally we'd extendComposed data for better suggestions, but that's for later (if ever)
            val extendedWord = textAtCursor.getWordAtCursor(CompatBreakIterator.getWordInstance(keyboard.mId.locale)).word.toString()
            val altSuggestions = mDictionaryFacilitator.getSuggestionResults(ComposedData.createForWord(extendedWord), ngramContext, keyboard,
                settingsValuesForSuggestion, SESSION_ID_TYPING, inputStyleIfNotPrediction)
            val extendingPart = extendedWord.substring(0, extendedWord.length - typedWordString.length)
            altSuggestions.forEach {
                if (it.word.startsWith(extendingPart) || (it.kind == SuggestedWordInfo.KIND_SHORTCUT && it.score > 0)) {
                    it.wordToReplace = extendedWord
                    suggestionsContainer.add(it)
                }
            }
            suggestionsContainer.sortByDescending { it.score }
        }

        capitalizeAndAddTrailingSingleQuotes(suggestionsContainer, capsMode, trailingSingleQuotesCount, mDictionaryFacilitator.mainLocale)
        val capitalizedTypedWord = capitalize(typedWordString, capsMode, mDictionaryFacilitator.mainLocale)

        // store the original SuggestedWordInfo for typed word, as it will be removed
        // we may want to re-add it in case auto-correction happens, so that the original word can at least be selected
        // we check against the capitalizedTypedWord because getTransformedSuggestedWordInfoList adjusts for capsMode
        val typedWordFirstOccurrenceWordInfo = suggestionsContainer.firstOrNull { it.word == capitalizedTypedWord }
        val firstOccurrenceOfTypedWordInSuggestions = SuggestedWordInfo.removeDupsAndTypedWord(capitalizedTypedWord, suggestionsContainer)
        makeFirstTwoSuggestionsNonEmoji(suggestionsContainer)

        val (allowsToBeAutoCorrected, hasAutoCorrection) = shouldBeAutoCorrected(
            trailingSingleQuotesCount,
            capitalizedTypedWord,
            suggestionsContainer.firstOrNull(),
            {
                val first = suggestionsContainer.firstOrNull() ?: suggestionResults.first()
                val suggestions = getNextWordSuggestions(ngramContext, keyboard, inputStyleIfNotPrediction, settingsValuesForSuggestion)
                val suggestionForFirstInContainer = suggestions.firstOrNull { it.word == first.word }
                val suggestionForTypedWord = suggestions.firstOrNull { it.word == capitalizedTypedWord }
                suggestionForFirstInContainer to suggestionForTypedWord
            },
            isCorrectionEnabled,
            wordComposer,
            suggestionResults,
            firstOccurrenceOfTypedWordInSuggestions,
            typedWordFirstOccurrenceWordInfo
        )
        val typedWordInfo = SuggestedWordInfo(typedWordString, "", SuggestedWordInfo.MAX_SCORE,
            SuggestedWordInfo.KIND_TYPED, typedWordFirstOccurrenceWordInfo?.sourceDict ?: Dictionary.DICTIONARY_USER_TYPED,
            SuggestedWordInfo.NOT_AN_INDEX , SuggestedWordInfo.NOT_A_CONFIDENCE)
        if (typedWordString.isNotEmpty()) {
            suggestionsContainer.add(0, typedWordInfo)
        }
        val suggestionsList = if (SuggestionStripView.DEBUG_SUGGESTIONS && suggestionsContainer.isNotEmpty())
                getSuggestionsInfoListWithDebugInfo(capitalizedTypedWord, suggestionsContainer)
            else suggestionsContainer

        val inputStyle = if (resultsArePredictions) {
            if (suggestionResults.ngramContext.isBeginningOfSentenceContext) InputStyle.BEGINNING_OF_SENTENCE_PREDICTION
            else InputStyle.PREDICTION
        } else {
            inputStyleIfNotPrediction
        }

        useDefaultEmojiSkinTone(suggestionsList)

        // If there is an incoming autocorrection, make sure typed word is shown, so user is able to override it.
        // Otherwise, if the relevant setting is enabled, show the typed word in the middle.
        val typedWordWasCapitalized = capitalizedTypedWord != typedWordString
        val correctToCapitalizedWord = typedWordWasCapitalized && isCorrectionEnabled && Settings.getValues().mAutoCorrectCapitalizedSuggestion
            && !wordComposer.isCursorFrontOrMiddleOfComposingWord && typedWordString.drop(1).none { it.isUpperCase() }
        val indexOfTypedWord = 1 + if (hasAutoCorrection) SuggestedWords.INDEX_OF_AUTO_CORRECTION else SuggestedWords.INDEX_OF_TYPED_WORD
        if (
            (hasAutoCorrection
                || (Settings.getValues().mCenterSuggestionTextToEnter && !wordComposer.isResumed)
                || typedWordWasCapitalized
            ) && suggestionsList.size >= indexOfTypedWord && capitalizedTypedWord.isNotEmpty()) {
            if (typedWordFirstOccurrenceWordInfo != null) {
                addDebugInfo(typedWordFirstOccurrenceWordInfo, capitalizedTypedWord)
                suggestionsList.add(indexOfTypedWord, typedWordFirstOccurrenceWordInfo)
            } else {
                suggestionsList.add(indexOfTypedWord,
                    SuggestedWordInfo(capitalizedTypedWord, "", 0, SuggestedWordInfo.KIND_TYPED,
                        Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
                )
            }
        }
        // religious honorifics, e.g. Rasul -> "Rasulullah ﷺ", "Umar " -> "Radiallahu Anhu". With auto-correction the first
        // two slots are taken (correction and typed word), so honorifics must never become the auto-correction target
        addHonorificSuggestions(suggestionsList, typedWordString, ngramContext, resultsArePredictions,
            if (resultsArePredictions) 0 else if (hasAutoCorrection || correctToCapitalizedWord) 3 else 1, null)
        addContextEmoji(suggestionsList, typedWordString, ngramContext, resultsArePredictions,
            if (resultsArePredictions) 1 else if (hasAutoCorrection || correctToCapitalizedWord) 3 else 2)
        val isTypedWordValid = firstOccurrenceOfTypedWordInSuggestions > -1 || (!resultsArePredictions && !allowsToBeAutoCorrected)
        addRomanUrduOnEnglishSuggestions(suggestionsList, typedWordString, ngramContext, resultsArePredictions, isTypedWordValid)
        addTechnicalCompletion(suggestionsList, typedWordString, resultsArePredictions, wordComposer)
        return SuggestedWords(suggestionsList, suggestionResults.rawSuggestions, typedWordInfo,
            isTypedWordValid, hasAutoCorrection || correctToCapitalizedWord, false, inputStyle, sequenceNumber)
    }

    /** Adds at most one low-priority STEM completion; it never changes conversational ranking or autocorrection. */
    private fun addTechnicalCompletion(
        suggestions: ArrayList<SuggestedWordInfo>, typed: String, isPrediction: Boolean, wordComposer: WordComposer
    ) {
        if (isPrediction || typed.isEmpty() || wordComposer.isCursorFrontOrMiddleOfComposingWord) return
        val context = Settings.getCurrentContext() ?: return
        val language = mDictionaryFacilitator.mainLocale.language
        var candidate = TechnicalTerms.completion(context, language, typed) ?: return
        if (typed.first().isUpperCase()) candidate = candidate.replaceFirstChar { it.uppercase(Locale.ROOT) }
        if (suggestions.any { it.word.equals(candidate, ignoreCase = true) }) return
        val source = Dictionary.PhonyDictionary("technical_terms")
        val technical = SuggestedWordInfo(candidate, "", 300, SuggestedWordInfo.KIND_COMPLETION,
            source, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
        // Keep the typed word and the best conversational alternative ahead of the technical term.
        suggestions.add(min(2, suggestions.size), technical)
    }

    /**
     * Roman -> Urdu / Arabic: the typed Roman text stays in the composing region, the strip shows native-script candidates
     * and the best one is auto-committed on space / punctuation (see [helium314.keyboard.latin.urdu.RomanTransliterator]).
     * Returns null if the model is not loaded yet, in which case regular suggestions are used.
     */
    private fun getRomanScriptSuggestions(script: RomanScript, wordComposer: WordComposer, ngramContext: NgramContext,
                                          inputStyleIfNotPrediction: InputStyle, sequenceNumber: Int, keyboard: Keyboard,
                                          settingsValuesForSuggestion: SettingsValuesForSuggestion): SuggestedWords? {
        val engine = script.engine(Settings.getCurrentContext()) ?: return null
        engine.personal = if (Settings.getValues()?.mLearnRomanTyping == true) script.personal else null
        val typed = wordComposer.typedWord
        val prevInfo = if (ngramContext.prevWordCount > 0) ngramContext.getNthPrevWord(1) else null
        val atStart = ngramContext.isBeginningOfSentenceContext || prevInfo == null
        // the last two committed words: the model has a trigram table, and a space-joined pair is parsed as two context words
        // a right-to-left mark is inserted at the start of each paragraph and sticks to the first word: not part of the word
        fun String.noBidiMarks() = filter { it != '\u200f' && it != '\u200e' && it != '\u061c' }
        val prev2 = if (atStart || ngramContext.prevWordCount < 2) null else ngramContext.getNthPrevWord(2)?.toString()?.noBidiMarks()?.takeIf { it.isNotEmpty() }
        val prev1 = prevInfo?.toString()?.noBidiMarks()
        val prev = if (atStart || prev1.isNullOrEmpty()) null else if (prev2 != null) "$prev2 $prev1" else prev1
        val source = urduDictionary
        val list = ArrayList<SuggestedWordInfo>()
        if (typed.isEmpty()) { // next-word prediction
            engine.nextWords(prev, atStart, 6).forEachIndexed { i, w ->
                list.add(SuggestedWordInfo(w, "", 1000 - i, SuggestedWordInfo.KIND_PREDICTION, source,
                    SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
            }
            addHonorificSuggestions(list, typed, ngramContext, true, 0, script.honorificScript)
            addContextEmoji(list, typed, ngramContext, true, 1)
            return SuggestedWords(list, null, null, false, false, false,
                if (atStart) InputStyle.BEGINNING_OF_SENTENCE_PREDICTION else InputStyle.PREDICTION,
                sequenceNumber)
        }
        // numbers (digits are also Arabizi letters, so they are word characters in Arabic mode) are never transliterated
        if (typed.all { it.isDigit() || it == '\'' }) return null
        val typedInfo = SuggestedWordInfo(typed, "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED,
            Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
        list.add(typedInfo)
        val perfStart = if (BuildConfig.DEBUG) System.nanoTime() else 0L
        val candidates = engine.suggest(typed, prev, atStart, 6)
        if (BuildConfig.DEBUG) Log.d("LafzPerf", "decode ms=${"%.2f".format(Locale.ROOT, (System.nanoTime() - perfStart) / 1e6)} typed=${typed.length} script=${script.name} prev=[${prev}] atStart=$atStart count=${ngramContext.prevWordCount}")
        candidates.forEachIndexed { i, c ->
            list.add(SuggestedWordInfo(c.text, "", 1000 - i,
                if (c.completion) SuggestedWordInfo.KIND_COMPLETION else SuggestedWordInfo.KIND_CORRECTION,
                source, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
        }
        // auto-commit the best Urdu word when space / punctuation is typed; a trailing completion is also committed,
        // as the user would otherwise end up with Latin text in an Urdu sentence. Never for a resumed word (revert
        // after an auto-commit), which stays in the Roman spelling as in HeliBoard.
        val willAutoCorrect = candidates.isNotEmpty() && isCorrectionEnabledForUrdu && !wordComposer.isResumed
        addHonorificSuggestions(list, typed, ngramContext, false, if (willAutoCorrect) 2 else 1, script.honorificScript)
        // keep the typed Roman spelling available as a tappable choice (English words in an Urdu sentence, e.g. "United Nations"),
        // also when the decoder has no candidate: then it is what gets committed
        val romanChoice = SuggestedWordInfo(typed, "", 500, SuggestedWordInfo.KIND_CORRECTION, source,
            SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
        list.add(3.coerceAtMost(list.size), romanChoice)
        addContactSuggestions(list, wordComposer, ngramContext, keyboard, settingsValuesForSuggestion, typed)
        addContextEmoji(list, typed, ngramContext, false, 2)
        return SuggestedWords(list, null, typedInfo, false, willAutoCorrect, false, inputStyleIfNotPrediction, sequenceNumber)
    }

    private val emojiSource = Dictionary.PhonyDictionary("lafz_emoji_keywords")

    private fun addContextEmoji(list: ArrayList<SuggestedWordInfo>, typed: String, context: NgramContext,
                               prediction: Boolean, insertIndex: Int) {
        val settings = Settings.getValues() ?: return
        if (!settings.mSuggestEmojis || settings.mInputAttributes.mIsPasswordField) return
        val keywords = bundledEmojiKeywords ?: return
        val previous = if (context.prevWordCount > 0 && !context.isBeginningOfSentenceContext)
            context.getNthPrevWord(1)?.toString() else null
        val query = if (prediction) previous ?: return else typed
        if (query.isBlank()) return
        val matches = linkedSetOf<String>()
        if (!prediction && previous != null) matches.addAll(keywords.suggest("$previous $typed", 2))
        matches.addAll(keywords.suggest(query, 2))
        // Keep the typed word and the automatic correction protected. Emoji are explicit choices only.
        var index = insertIndex.coerceAtMost(list.size)
        matches.filterNot { SupportedEmojis.isUnsupported(it) }.take(2).forEach { emoji ->
            val info = useDefaultEmojiSkinTone(SuggestedWordInfo(emoji, "", 850,
                SuggestedWordInfo.KIND_PREDICTION, emojiSource,
                SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
            if (list.none { it.word == info.word }) list.add(index++, info)
        }
    }

    /**
     * Roman Urdu / Arabic skip the regular dictionaries, so names from the phone's contacts (a Latin word such as "Ahmed") are looked up here
     * and offered as plain choices behind the Roman spelling; never auto-committed. Only when the contacts dictionary is on (setting + permission).
     */
    private fun addContactSuggestions(list: ArrayList<SuggestedWordInfo>, wordComposer: WordComposer, ngramContext: NgramContext,
                                      keyboard: Keyboard, settingsValuesForSuggestion: SettingsValuesForSuggestion, typed: String) {
        if (typed.length < 2) return
        val contacts = mDictionaryFacilitator.getContactSuggestions(wordComposer.composedDataSnapshot, ngramContext, keyboard,
            settingsValuesForSuggestion, SESSION_ID_TYPING)
        var index = 4.coerceAtMost(list.size)
        // the contacts dictionary also returns typo-tolerant matches ("rasul" -> "Hashmi"): only names that start with what was typed
        for (c in contacts.filter { it.word.startsWith(typed, ignoreCase = true) }.take(2)) {
            if (list.any { it.word == c.word }) continue
            list.add(index++, SuggestedWordInfo(c.word, "", 600, SuggestedWordInfo.KIND_COMPLETION, c.sourceDict,
                SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
        }
    }

    private val honorificsDictionary = Dictionary.PhonyDictionary("honorifics")

    /**
     * adds honorific / religious term suggestions at [insertIndex]; [forcedScript] (a [ReligiousShortcuts] script) is set for
     * Roman->Urdu / Roman->Arabic typing, otherwise the output script follows the typed text (Latin stays Latin, Arabic-block
     * text is Arabic in an Arabic keyboard and Urdu otherwise)
     */
    private fun addHonorificSuggestions(list: ArrayList<SuggestedWordInfo>, typed: String, ngramContext: NgramContext,
                                        isPrediction: Boolean, insertIndex: Int, forcedScript: Int?) {
        if (Settings.getValues()?.mHonorificSuggestions == false) return
        val shortcuts = Honorifics.get(Settings.getCurrentContext()) ?: return
        val prev = if (ngramContext.prevWordCount > 0 && !ngramContext.isBeginningOfSentenceContext)
            ngramContext.getNthPrevWord(1)?.toString()?.takeIf { it.isNotEmpty() } else null
        val forms = if (isPrediction) {
            if (prev == null) return
            shortcuts.afterWord(prev, forcedScript ?: scriptOf(prev), 2)
        } else {
            if (typed.isEmpty()) return
            shortcuts.suggest(typed, prev, forcedScript ?: scriptOf(typed), if (insertIndex <= 1) 3 else 2)
        }
        var index = insertIndex.coerceAtMost(list.size)
        // the typed word is not shown in the strip while typing, and names like "Allah" are often not in the
        // dictionary: offer the plain word next to its honorific forms (not for abbreviations like "ra")
        if (!isPrediction && forcedScript == null) {
            val head = forms.firstOrNull()?.substringBefore(' ')
            if (head != null && head.equals(typed, ignoreCase = true) && list.drop(1).none { it.word == head }) {
                list.add(index++, SuggestedWordInfo(head, "", 901, SuggestedWordInfo.KIND_COMPLETION,
                    honorificsDictionary, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
            }
        }
        for (form in forms) {
            if (list.any { it.word == form }) continue
            list.add(index++, SuggestedWordInfo(form, "", 900,
                if (isPrediction) SuggestedWordInfo.KIND_PREDICTION else SuggestedWordInfo.KIND_COMPLETION,
                honorificsDictionary, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
        }
    }

    /**
     * English keyboard: also offer Urdu words typed in English letters (kahan, zarorat, kidher). English stays first: when the
     * typed word is not English the best Roman-Urdu word takes the right-hand slot (list index 3), otherwise they only follow
     * the English suggestions. After a Roman-Urdu word that is not English, likely next words are offered (kia -> haal).
     */
    private fun addRomanUrduOnEnglishSuggestions(list: ArrayList<SuggestedWordInfo>, typed: String, ngramContext: NgramContext,
                                                 isPrediction: Boolean, isTypedWordValid: Boolean) {
        val locale = mDictionaryFacilitator.mainLocale
        if (locale.language != "en" || RomanScript.of(locale) != null) return
        val sv = Settings.getValues() ?: return
        if (!sv.mRomanUrduOnEnglish || sv.mInputAttributes.mIsPasswordField) return
        val engine = RomanScript.URDU.engine(Settings.getCurrentContext()) ?: return
        val lexicon = engine.romanLexicon()
        val words: List<String>
        val insertAt: Int
        if (isPrediction) {
            val prev = if (ngramContext.prevWordCount > 0 && !ngramContext.isBeginningOfSentenceContext)
                ngramContext.getNthPrevWord(1)?.toString()?.lowercase()?.trim('\u200f', '\u200e') else null
            if (prev == null || prev.length < 2 || !lexicon.isWord(prev) || mDictionaryFacilitator.isValidSpellingWord(prev)) return
            words = engine.romanNext(prev, 2)
            insertAt = 0
        } else {
            val t = typed.lowercase()
            if (t.length < 2 || !t.all { it in 'a'..'z' || it == '\'' }) return
            val found = lexicon.complete(t, 4).filter { it.length > t.length || it != t || !isTypedWordValid }
            words = if (t.length >= 3) found else found.filter { it == t }
            insertAt = if (isTypedWordValid) list.size else 3
        }
        val capitalize = typed.firstOrNull()?.isUpperCase() == true
        var index = insertAt.coerceAtMost(list.size)
        var added = 0
        for (w in words) {
            val shown = if (capitalize) w.replaceFirstChar { it.uppercase() } else w
            if (list.drop(if (isPrediction) 0 else 1).any { it.word.equals(shown, ignoreCase = true) }) continue
            list.add(index++, SuggestedWordInfo(shown, "", 700 - added, if (isPrediction) SuggestedWordInfo.KIND_PREDICTION else SuggestedWordInfo.KIND_COMPLETION,
                romanUrduOnEnglishDictionary, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
            if (++added >= 2) break
        }
    }

    private val romanUrduOnEnglishDictionary = Dictionary.PhonyDictionary("roman_urdu_on_english")

    private fun scriptOf(text: String) =
        RomanScript.honorificScript(mDictionaryFacilitator.mainLocale, ReligiousShortcuts.isUrduScript(text))

    private val urduDictionary = ROMAN_SCRIPT_SOURCE
    private val isCorrectionEnabledForUrdu = true

    // returns [allowsToBeAutoCorrected, hasAutoCorrection]
    // public for testing
    // todo: now we can do better tests, maybe make it private again and test via getSuggestedWords (and simplify if possible)
    fun shouldBeAutoCorrected(
        trailingSingleQuotesCount: Int,
        typedWordString: String,
        firstSuggestionInContainer: SuggestedWordInfo?,
        getEmptyWordSuggestions: () -> Pair<SuggestedWordInfo?, SuggestedWordInfo?>,
        isCorrectionEnabled: Boolean,
        wordComposer: WordComposer,
        suggestionResults: SuggestionResults,
        firstOccurrenceOfTypedWordInSuggestions: Int,
        typedWordInfo: SuggestedWordInfo?
    ): Pair<Boolean, Boolean> {
        val consideredWord = typedWordString.dropLast(trailingSingleQuotesCount)
        val firstAndTypedEmptyInfos by lazy { getEmptyWordSuggestions() }

        val scoreLimit = Settings.getValues().mScoreLimitForAutocorrect
        // We allow auto-correction if whitelisting is not required or the word is whitelisted,
        // or if the word had more than one char and was not suggested.
        val allowsToBeAutoCorrected: Boolean
        if (SHOULD_AUTO_CORRECT_USING_NON_WHITE_LISTED_SUGGESTION
                || firstSuggestionInContainer?.isKindOf(SuggestedWordInfo.KIND_WHITELIST) == true
                || (consideredWord.length > 1
                    && typedWordInfo?.sourceDict == null // more than 1 letter and not in dictionary
                    // if the typed word contains @ or ., the suggestion also needs to contain it
                    // (avoid autocorrecting mail addresses, URLs & similar to something different)
                    && (!typedWordString.contains('@') || firstSuggestionInContainer?.word?.contains('@') == true)
                    && (!typedWordString.contains('.') || firstSuggestionInContainer?.word?.contains('.') == true)
                    )
            ) {
            allowsToBeAutoCorrected = true
        } else if (firstSuggestionInContainer != null && typedWordString.isNotEmpty()) {
            // maybe allow autocorrect, depending on scores and emptyWordSuggestions
            val first = firstAndTypedEmptyInfos.first
            val typed = firstAndTypedEmptyInfos.second
            allowsToBeAutoCorrected = when {
                firstSuggestionInContainer.score > scoreLimit -> true // suggestion has good score, allow
                first == null -> false // no autocorrect if first suggestion unknown in this ngram context
                typed == null -> true // allow autocorrect if typed word not known in this ngram context, todo: this may be too aggressive
                else -> first.score - typed.score > 20 // autocorrect if suggested word has clearly higher score for empty word suggestions
            }
        } else {
            allowsToBeAutoCorrected = false
        }
        // If correction is not enabled, we never auto-correct. This is for example for when
        // the setting "Auto-correction" is "off": we still suggest, but we don't auto-correct.
        val hasAutoCorrection: Boolean
        if (!isCorrectionEnabled
            || !allowsToBeAutoCorrected // If the word does not allow to be auto-corrected, then we don't auto-correct.
            || !wordComposer.isComposingWord // If we are doing prediction, then we never auto-correct of course
            || suggestionResults.isEmpty() // If we don't have suggestion results, we can't evaluate the first suggestion for auto-correction
            || wordComposer.hasDigits() // If the word has digits, we never auto-correct because it's likely the word was type with a lot of care // todo: but what if user touched the number row?
            || (wordComposer.isMostlyCaps && !wordComposer.isAllUpperCase) // If the word is mostly caps, we never auto-correct because this is almost certainly intentional
            || wordComposer.isResumed // We never auto-correct when suggestions are resumed because it would be unexpected
            // If we don't have a main dictionary, we never want to auto-correct. The reason
            // for this is, the user may have a contact whose name happens to match a valid
            // word in their language, and it will unexpectedly auto-correct. For example, if
            // the user types in English with no dictionary and has a "Will" in their contact
            // list, "will" would always auto-correct to "Will" which is unwanted. Hence, no
            // main dict => no auto-correct. Also, it would probably get obnoxious quickly.
            // TODO: now that we have personalization, we may want to re-evaluate this decision
            || !mDictionaryFacilitator.hasAtLeastOneInitializedMainDictionary()
        ) {
            hasAutoCorrection = false
        } else {
            val firstSuggestion = firstSuggestionInContainer ?: suggestionResults.first()
            if (suggestionResults.firstSuggestionExceedsConfidenceThreshold && firstOccurrenceOfTypedWordInSuggestions != 0) {
                // mFirstSuggestionExceedsConfidenceThreshold is always set to false, so currently this branch is useless
                return true to true
            }
            if (!AutoCorrectionUtils.suggestionExceedsThreshold(firstSuggestion, consideredWord, mAutoCorrectionThreshold)) {
                // Score is too low for autocorrect
                // todo: maybe also do something here depending on ngram context?
                return true to false
            }
            // We have a high score, so we need to check if this suggestion is in the correct
            // form to allow auto-correcting to it in this language. For details of how this
            // is determined, see #isAllowedByAutoCorrectionWithSpaceFilter.
            val allowed = isAllowedByAutoCorrectionWithSpaceFilter(firstSuggestion)
            if (allowed && typedWordInfo != null && typedWordInfo.score > scoreLimit) {
                // typed word is valid and has good score
                // do not auto-correct if typed word is better match than first suggestion
                val dictLocale = mDictionaryFacilitator.currentLocale
                if (firstSuggestion.score < scoreLimit) {
                    // don't allow if suggestion has too low score
                    return true to false
                }
                if (firstSuggestion.sourceDict.mLocale !== typedWordInfo.sourceDict.mLocale) {
                    // dict locale different -> return the better match
                    return true to (dictLocale == firstSuggestion.sourceDict.mLocale)
                }
                // the score difference may need tuning, but so far it seems alright
                val firstWordBonusScore =
                    ((if (firstSuggestion.isKindOf(SuggestedWordInfo.KIND_WHITELIST)) 20 else 0) // large bonus because it's wanted by dictionary
                            + (if (StringUtils.isLowerCaseAscii(typedWordString)) 5 else 0) // small bonus because typically only lower case ascii is typed (applies to latin keyboards only)
                            + if (firstSuggestion.score > typedWordInfo.score) 5 else 0) // small bonus if score is higher
                val firstScoreForEmpty = firstAndTypedEmptyInfos.first?.score ?: 0
                val typedScoreForEmpty = firstAndTypedEmptyInfos.second?.score ?: 0
                if (firstScoreForEmpty + firstWordBonusScore >= typedScoreForEmpty + 20) {
                    // first word is clearly better match for this ngram context
                    return true to true
                }
                hasAutoCorrection = false
            } else {
                hasAutoCorrection = allowed
            }
        }
        return allowsToBeAutoCorrected to hasAutoCorrection
    }

    // Retrieves suggestions for the batch input
    // and calls the callback function with the suggestions.
    private fun getSuggestedWordsForBatchInput(
        wordComposer: WordComposer,
        ngramContext: NgramContext, keyboard: Keyboard,
        settingsValuesForSuggestion: SettingsValuesForSuggestion,
        inputStyle: InputStyle, isCorrectionEnabled: Boolean, sequenceNumber: Int
    ): SuggestedWords {
        val locale = mDictionaryFacilitator.mainLocale
        val romanScript = RomanScript.of(locale)
        val engineChoice = Settings.getValues().mGestureEngine
        // The Google library only decodes the locales of a normal main dictionary; Roman Urdu/Arabic always use our engines
        val useGoogleLibrary = engineChoice == GESTURE_ENGINE_GOOGLE && JniUtils.sHaveGestureLib && romanScript == null
        val builtInLanguage = romanScript != null || locale.language == "en"
        if (!useGoogleLibrary && builtInLanguage) {
            val builtIn = getBuiltInSwipeSuggestions(wordComposer, ngramContext, keyboard, inputStyle,
                sequenceNumber, romanScript, engineChoice)
            if (builtIn != null) return builtIn
            // Roman scripts never have a dictionary decoder. No result for English may only fall through to the
            // dictionary decoder when the Google library is really loaded.
            if (romanScript != null || !JniUtils.sHaveGestureLib) return SuggestedWords.emptyBatchInstance
        }
        // HeliBoard's own native gesture decoder only exists in the Google library: without it the native
        // call dereferences a null pointer and kills the keyboard process, so it must never be reached.
        if (!JniUtils.sHaveGestureLib) return SuggestedWords.emptyBatchInstance
        val suggestionResults = mDictionaryFacilitator.getSuggestionResults(
            wordComposer.composedDataSnapshot, ngramContext, keyboard,
            settingsValuesForSuggestion, SESSION_ID_GESTURE, inputStyle
        )

        // For transforming words that don't come from a dictionary, because it's our best bet
        val capsMode = getCapsModeForGesture(wordComposer, keyboard)
        val suggestionsContainer = ArrayList(suggestionResults)
        replaceSingleLetterFirstSuggestion(suggestionsContainer)

        val rejected: SuggestedWordInfo?
        if (SHOULD_REMOVE_PREVIOUSLY_REJECTED_SUGGESTION && suggestionsContainer.size > 1 && TextUtils.equals(
                suggestionsContainer[0].word,
                wordComposer.rejectedBatchModeSuggestion
            )
        ) {
            rejected = suggestionsContainer.removeAt(0)
            suggestionsContainer.add(1, rejected)
        } else {
            rejected = null
        }
        // unchanged first suggestion, but considering adjusted order
        // we try to suppress emojis, because they will be moved to a later position if possible
        val pseudoTypedWord = suggestionsContainer.firstOrNull { !it.isEmoji } ?: suggestionsContainer.firstOrNull()

        // order is important, because capitalize may result in duplicate suggestions (e.g. frank and Frank),
        // which we want to remove before making the first 2 suggestions non-emoji
        capitalizeAndAddTrailingSingleQuotes(suggestionsContainer, capsMode, 0, locale)
        SuggestedWordInfo.removeDupsAndTypedWord(null, suggestionsContainer)
        makeFirstTwoSuggestionsNonEmoji(suggestionsContainer)

        // For some reason some suggestions with MIN_VALUE are making their way here.
        // TODO: Find a more robust way to detect distracters.
        for (i in suggestionsContainer.indices.reversed()) {
            if (suggestionsContainer[i].score < SUPPRESS_SUGGEST_THRESHOLD) {
                suggestionsContainer.removeAt(i)
            }
        }

        val capitalizedTypedWord = capitalize(wordComposer.typedWord, capsMode, locale)
        val addCapitalizedSuggestion = capitalizedTypedWord != wordComposer.typedWord && suggestionsContainer.drop(1).none { it.word == capitalizedTypedWord }
        if (addCapitalizedSuggestion) {
            suggestionsContainer.add(min(1, suggestionsContainer.size),
                SuggestedWordInfo(capitalizedTypedWord, "", 0, SuggestedWordInfo.KIND_TYPED,
                    Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
            )
        }

        useDefaultEmojiSkinTone(suggestionsContainer)

        // In the batch input mode, the most relevant suggested word should act as a "typed word"
        // (typedWordValid=true), not as an "auto correct word" (willAutoCorrect=false).
        // Exception is when using shift to change capitalization of suggestions.
        // Note that because this method is never used to get predictions, there is no need to
        // modify inputType such in getSuggestedWordsForNonBatchInput.
        val pseudoTypedWordInfo = preferNextWordSuggestion(
            pseudoTypedWord, suggestionsContainer,
            getNextWordSuggestions(ngramContext, keyboard, inputStyle, settingsValuesForSuggestion), rejected
        )
        val suggestionsList = if (SuggestionStripView.DEBUG_SUGGESTIONS && suggestionsContainer.isNotEmpty()) {
            getSuggestionsInfoListWithDebugInfo(suggestionResults.first().word, suggestionsContainer)
        } else {
            suggestionsContainer
        }

        if (useBackgroundGathering && inputStyle == InputStyle.TAIL_BATCH) {
            val wordData = WordData(null, suggestionResults, wordComposer.composedDataSnapshot,
                ngramContext, keyboard, inputStyle, false, pseudoTypedWordInfo)
            BackgroundGatheringCache.addWord(wordData)
        }

        val autocorrectCapitalization = addCapitalizedSuggestion && Settings.getValues().mAutoCorrectCapitalizedSuggestion && isCorrectionEnabled && !wordComposer.isCursorFrontOrMiddleOfComposingWord
        return SuggestedWords(suggestionsList, suggestionResults.rawSuggestions, pseudoTypedWordInfo, true,
            autocorrectCapitalization, false, inputStyle, sequenceNumber)
    }

    private fun getBuiltInSwipeSuggestions(wordComposer: WordComposer, ngramContext: NgramContext,
                                           keyboard: Keyboard, inputStyle: InputStyle, sequenceNumber: Int,
                                           script: RomanScript?, engineChoice: String): SuggestedWords? {
        val context = Settings.getCurrentContext() ?: return null
        val engine = script?.engine(context)
        val previous = if (ngramContext.isBeginningOfSentenceContext || ngramContext.prevWordCount == 0)
            null else ngramContext.getNthPrevWord(1)?.toString()?.filter { it != '\u200f' && it != '\u200e' }
        val matches = recognizeSwipe(context, keyboard, wordComposer, script, engine, previous, engineChoice)
        if (matches.isEmpty()) return null
        val candidates = LinkedHashSet<String>()
        for (match in matches) {
            if (engine != null) {
                engine.suggest(match, previous, previous == null, 2).forEach { candidates.add(it.text) }
            }
            candidates.add(match)
            if (candidates.size >= 8) break
        }
        val locale = mDictionaryFacilitator.mainLocale
        val caps = getCapsModeForGesture(wordComposer, keyboard)
        val results = ArrayList<SuggestedWordInfo>()
        candidates.take(8).forEachIndexed { index, word ->
            val output = if (script == null) capitalize(word, caps, locale) else word
            results.add(SuggestedWordInfo(output, "", 1000 - index,
                SuggestedWordInfo.KIND_CORRECTION, Dictionary.DICTIONARY_USER_TYPED,
                SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
        }
        return SuggestedWords(results, null, results.first(), true, false, false, inputStyle, sequenceNumber)
    }

    /**
     * Roman words for the swipe, from the chosen engine. Runs on the suggestion thread in the middle of a swipe, so it never
     * loads anything: engines that are not loaded yet are started in the background and give no result meanwhile.
     */
    private fun recognizeSwipe(context: Context, keyboard: Keyboard, wordComposer: WordComposer,
                               script: RomanScript?, engine: RomanTransliterator?, previous: String?,
                               engineChoice: String): List<String> {
        val language = script?.name?.lowercase() ?: "en"
        if (engineChoice != GESTURE_ENGINE_LAFZ && !FutoSwipe.failed) {
            if (!FutoSwipe.isReady(language)) {
                preloadFutoSwipe(context, script)
                return emptyList()
            }
            val request = FutoSwipe.Request(language, previous)
            FutoSwipe.recognize(keyboard, wordComposer.inputPointers, request, 8)?.let {
                if (BuildConfig.DEBUG) Log.d("LafzSwipe", "futo $language -> $it")
                return it
            }
            if (!FutoSwipe.failed) return emptyList() // transient (e.g. layout without all letters): no result, no stall
        }
        // Lafz's own matcher: chosen in settings, or FUTO Swipe could not load
        if (!SwipeDecoder.englishWordsLoaded()) {
            SwipeDecoder.preloadEnglishWords(context)
            return emptyList()
        }
        val english = SwipeDecoder.englishWords(context)
        val words = if (script != null && engine != null) swipeWords.getOrPut(script) {
            HashMap<String, Int>(english).apply { putAll(engine.swipeSpellings()) }
        } else english
        return SwipeDecoder.match(keyboard, wordComposer.inputPointers, words, 8).map { it.word }
    }

    private fun useDefaultEmojiSkinTone(suggestionsList: ArrayList<SuggestedWordInfo>) {
        for (i in suggestionsList.indices) {
            suggestionsList[i] = useDefaultEmojiSkinTone(suggestionsList[i])
        }
    }

    /** get suggestions based on the current ngram context, with an empty typed word (that's what next word suggestions do)  */
    private fun getNextWordSuggestions(ngramContext: NgramContext, keyboard: Keyboard, inputStyle: InputStyle,
                                       settingsValuesForSuggestion: SettingsValuesForSuggestion): SuggestionResults {
        val cachedResults = nextWordSuggestionsCache[ngramContext]
        if (cachedResults != null) return cachedResults
        val newResults = mDictionaryFacilitator.getSuggestionResults(ComposedData(InputPointers(1),
            false, ""), ngramContext, keyboard, settingsValuesForSuggestion, SESSION_ID_TYPING, inputStyle)
        nextWordSuggestionsCache[ngramContext] = newResults
        return newResults
    }

    /** Swipe typing is off: let go of everything the swipe engines hold (no-op when nothing was loaded). */
    fun releaseSwipeEngine() {
        swipeWords.clear()
        FutoSwipe.release()
        SwipeDecoder.releaseWords()
    }

    companion object {
        /** Starts loading the swipe engine for [locale] in the background (keyboard start, language switch). */
        @JvmStatic
        fun preloadSwipeEngine(context: Context, locale: Locale, engineChoice: String) {
            val script = RomanScript.of(locale)
            if (script == null && locale.language != "en") return
            if (engineChoice == GESTURE_ENGINE_LAFZ || FutoSwipe.failed) SwipeDecoder.preloadEnglishWords(context)
            if (engineChoice != GESTURE_ENGINE_LAFZ) preloadFutoSwipe(context, script)
        }

        private fun preloadFutoSwipe(context: Context, script: RomanScript?) {
            FutoSwipe.preload(context, script?.name?.lowercase() ?: "en",
                { SwipeDecoder.englishWords(context) },
                { script?.engineBlocking(context)?.swipeSpellings() ?: emptyMap() })
        }

        // First access is on the existing suggestion worker. No disk reads on the UI keystroke path.
        private val bundledEmojiKeywords: EmojiKeywords? by lazy {
            val ctx = Settings.getCurrentContext()
            try {
                ctx?.assets?.open("emoji_keywords.tsv")?.use { input ->
                    InputStreamReader(input, Charsets.UTF_8).use { EmojiKeywords.load(it) }
                }
            } catch (e: java.io.IOException) {
                Log.e("Suggest", "Could not load bundled emoji keywords", e)
                null
            }
        }

        private val TAG: String = Suggest::class.java.simpleName

        // Source of the Roman -> Urdu / Arabic decoder candidates (SuggestedWordInfo.sourceDict). InputLogic uses it
        // to pick the live-transliteration candidate, so honorifics and the typed Roman word are never picked.
        @JvmField val ROMAN_SCRIPT_SOURCE = Dictionary.PhonyDictionary("roman_script_translit")

        // Session id for {@link #getSuggestedWords(WordComposer,String,ProximityInfo,boolean,int)}.
        // We are sharing the same ID between typing and gesture to save RAM footprint.
        const val SESSION_ID_TYPING = 0
        const val SESSION_ID_GESTURE = 0

        // Close to -2**31
        private const val SUPPRESS_SUGGEST_THRESHOLD = -2000000000

        private const val MAXIMUM_AUTO_CORRECT_LENGTH_FOR_GERMAN = 12
        // TODO: should we add Finnish here?
        private val sLanguageToMaximumAutoCorrectionWithSpaceLength = hashMapOf(Locale.GERMAN.language to MAXIMUM_AUTO_CORRECT_LENGTH_FOR_GERMAN)

        private fun capitalizeAndAddTrailingSingleQuotes(
            suggestions: ArrayList<SuggestedWordInfo>, capsMode: CapsMode, trailingSingleQuotesCount: Int, defaultLocale: Locale
        ) {
            val suggestionsCount = suggestions.size
            if (capsMode != CapsMode.OFF || 0 != trailingSingleQuotesCount) {
                for (i in 0 until suggestionsCount) {
                    val wordInfo = suggestions[i]
                    val wordLocale = wordInfo.sourceDict.mLocale
                    val transformedWordInfo = capitalizeAndAddTrailingSingleQuotes(
                        wordInfo, wordLocale ?: defaultLocale, capsMode, trailingSingleQuotesCount
                    )
                    suggestions[i] = transformedWordInfo
                }
            }
        }

        private fun capitalizeAndAddTrailingSingleQuotes(
            wordInfo: SuggestedWordInfo, locale: Locale, capsMode: CapsMode, trailingSingleQuotesCount: Int
        ): SuggestedWordInfo {
            var capitalizedWord = capitalize(wordInfo.word, capsMode, locale)
            // Appending quotes is here to help people quote words. However, it's not helpful
            // when they type words with quotes toward the end like "it's" or "didn't", where
            // it's more likely the user missed the last character (or didn't type it yet).
            val quotesToAppend = trailingSingleQuotesCount - if (wordInfo.word.contains('\'')) 1 else 0
            repeat(max(0, quotesToAppend)) { capitalizedWord += '\'' }
            return wordInfo.copy(word = capitalizedWord)
        }

        private fun getSuggestionsInfoListWithDebugInfo(
            typedWord: String, suggestions: ArrayList<SuggestedWordInfo>
        ): ArrayList<SuggestedWordInfo> {
            val suggestionsSize = suggestions.size
            val suggestionsList = ArrayList<SuggestedWordInfo>(suggestionsSize)
            for (cur in suggestions) {
                addDebugInfo(cur, typedWord)
                suggestionsList.add(cur)
            }
            return suggestionsList
        }

        @JvmStatic
        fun addDebugInfo(wordInfo: SuggestedWordInfo?, typedWord: String) {
            if (!SuggestionStripView.DEBUG_SUGGESTIONS)
                return
            val normalizedScore = BinaryDictionaryUtils.calcNormalizedScore(typedWord, wordInfo.toString(), wordInfo!!.score)
            val scoreInfoString: String
            val dict = wordInfo.sourceDict.mDictType + ":" + wordInfo.sourceDict.mLocale
            scoreInfoString = if (normalizedScore > 0) {
                String.format(Locale.ROOT, "%d (%4.2f), %s", wordInfo.score, normalizedScore, dict)
            } else {
                String.format(Locale.ROOT, "%d, %s", wordInfo.score, dict)
            }
            wordInfo.debugString = scoreInfoString
        }

        @JvmStatic
        fun useDefaultEmojiSkinTone(suggestion: SuggestedWordInfo): SuggestedWordInfo {
            val defaultVersion = getEmojiDefaultVersion(suggestion.word)
            if (defaultVersion == suggestion.word) {
                return suggestion
            }

            return suggestion.copy(word = defaultVersion)
        }

        /**
         * Computes whether this suggestion should be blocked or not in this language
         *
         * This function implements a filter that avoids auto-correcting to suggestions that contain
         * spaces that are above a certain language-dependent character limit. In languages like German
         * where it's possible to concatenate many words, it often happens our dictionary does not
         * have the longer words. In this case, we offer a lot of unhelpful suggestions that contain
         * one or several spaces. Ideally we should understand what the user wants and display useful
         * suggestions by improving the dictionary and possibly having some specific logic. Until
         * that's possible we should avoid displaying unhelpful suggestions. But it's hard to tell
         * whether a suggestion is useful or not. So at least for the time being we block
         * auto-correction when the suggestion is long and contains a space, which should avoid the
         * worst damage.
         * This function is implementing that filter. If the language enforces no such limit, then it
         * always returns true. If the suggestion contains no space, it also returns true. Otherwise,
         * it checks the length against the language-specific limit.
         *
         * @param info the suggestion info
         * @return whether it's fine to auto-correct to this.
         */
        private fun isAllowedByAutoCorrectionWithSpaceFilter(info: SuggestedWordInfo): Boolean {
            val locale = info.sourceDict.mLocale ?: return true
            val maximumLengthForThisLanguage = sLanguageToMaximumAutoCorrectionWithSpaceLength[locale.language]
                ?: return true // This language does not enforce a maximum length to auto-correction
            return (info.word.length <= maximumLengthForThisLanguage
                    || -1 == info.word.indexOf(Constants.CODE_SPACE.toChar()))
        }

        /** returns CapsMode.MANUAL, CapsMode.MANUAL_LOCKED, or CAPS_MODE_OFF */
        private fun getCapsModeForTyping(wordComposer: WordComposer, keyboard: Keyboard): CapsMode {
            val capsMode = keyboard.mId.element.capsMode
            if (capsMode == CapsMode.MANUAL || capsMode == CapsMode.MANUAL_LOCKED)
                return capsMode
            // we have some auto-mode which we ignore
            // instead we determine mode from the typed word (that's how it was done for a long time, todo: maybe adjust if necessary?)
            if (wordComposer.isAllUpperCase && !wordComposer.isResumed) return CapsMode.MANUAL_LOCKED
            if (wordComposer.isOrWillBeOnlyFirstCharCapitalized) return CapsMode.MANUAL
            return CapsMode.OFF
        }

        /** returns CapsMode.MANUAL, CapsMode.MANUAL_LOCKED, or CAPS_MODE_OFF */
        // maybe could check the details in differences to getCapsModeForTyping and unify?
        private fun getCapsModeForGesture(wordComposer: WordComposer, keyboard: Keyboard): CapsMode {
            val capsMode = keyboard.mId.element.capsMode
            if (capsMode == CapsMode.MANUAL || capsMode == CapsMode.MANUAL_LOCKED)
                return capsMode
            if (wordComposer.isAllUpperCase) return CapsMode.MANUAL_LOCKED
            if (wordComposer.wasShiftedNoLock()) return CapsMode.MANUAL
            return CapsMode.OFF
        }

        private fun capitalize(word: String, capsMode: CapsMode, locale: Locale) = when (capsMode) {
            CapsMode.MANUAL_LOCKED -> word.uppercase(locale)
            CapsMode.MANUAL -> StringUtils.capitalizeFirstCodePoint(word, locale)
            else -> word
        }

        private fun makeFirstTwoSuggestionsNonEmoji(words: MutableList<SuggestedWordInfo>) {
            for (i in 0..1) {
                if (words.size > 2 && words[i].isEmoji) {
                    val relativeIndex = words.subList(2, words.size).indexOfFirst { !it.isEmoji }
                    if (relativeIndex < 0) break
                    val firstNonEmojiIndex = relativeIndex + 2
                    if (firstNonEmojiIndex > i) {
                        words.add(i, words.removeAt(firstNonEmojiIndex))
                    }
                }
            }
        }

        /** reduces score of the first suggestion if next one is close and has more than a single letter */
        private fun replaceSingleLetterFirstSuggestion(suggestionResults: MutableList<SuggestedWordInfo>) {
            if (suggestionResults.size < 2 || suggestionResults.first().word.length != 1) return
            // suppress single letter suggestions if next suggestion is close and has more than one letter
            val first = suggestionResults[0]
            val second = suggestionResults[1]
            if (second.word.length > 1 && second.score > 0.94 * first.score) {
                suggestionResults.remove(first) // remove and re-add with lower score
                val modifiedFirst = first.copy(word = first.word, score = (first.score * 0.93).toInt())
                val insertIndex = suggestionResults.indexOfFirst { it.score < modifiedFirst.score }
                if (insertIndex == -1) suggestionResults.add(modifiedFirst)
                else suggestionResults.add(insertIndex, modifiedFirst)

                if (DebugFlags.DEBUG_ENABLED)
                    Log.d(TAG, "reduced score of ${first.word} from ${first.score}, new first: ${suggestionResults.first().word} (${suggestionResults.first().score})")
            }
        }

        /** returns new pseudoTypedWordInfo, puts it in suggestionsContainer, modifies nextWordSuggestions */
        private fun preferNextWordSuggestion(
            pseudoTypedWordInfo: SuggestedWordInfo?,
            suggestionsContainer: ArrayList<SuggestedWordInfo>,
            nextWordSuggestions: SuggestionResults, rejected: SuggestedWordInfo?
        ): SuggestedWordInfo? {
            if (pseudoTypedWordInfo == null || !Settings.getValues().mUsePersonalizedDicts
                || pseudoTypedWordInfo.sourceDict.mDictType != Dictionary.TYPE_MAIN || suggestionsContainer.size < 2
            ) return pseudoTypedWordInfo
            val goodNextSuggestions = nextWordSuggestions.filter { it.score >= 170 } // we only want reasonably often typed words, value may require tuning
            if (goodNextSuggestions.isEmpty()) return pseudoTypedWordInfo

            // for each suggestion, check whether the word was already typed in this ngram context (i.e. is nextWordSuggestion)
            for (suggestion in suggestionsContainer) {
                if (suggestion.score < pseudoTypedWordInfo.score * 0.93) break // we only want reasonably good suggestions, value may require tuning
                if (suggestion === rejected) continue  // ignore rejected suggestions
                for (nextWordSuggestion in goodNextSuggestions) {
                    if (nextWordSuggestion.word != suggestion.word) continue
                    // if we have a high scoring suggestion in next word suggestions, take it (because it's expected that user might want to type it again)
                    suggestionsContainer.remove(suggestion)
                    suggestionsContainer.add(0, suggestion)
                    if (DebugFlags.DEBUG_ENABLED)
                        Log.d(TAG, "replaced batch word $pseudoTypedWordInfo with $suggestion")
                    return suggestion
                }
            }
            return pseudoTypedWordInfo
        }
    }
}
