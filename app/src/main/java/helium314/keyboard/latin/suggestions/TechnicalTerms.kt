// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import android.content.Context
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Low-priority STEM completions, separate from the conversational dictionaries and n-gram model. */
internal object TechnicalTerms {
    private val termsByLanguage = ConcurrentHashMap<String, List<String>>()

    fun completion(context: Context, language: String, typed: String): String? {
        val minLength = if (language == "ur") 2 else 4
        if (typed.length < minLength) return null
        val file = when (language) {
            "en" -> "technical_words/en.txt"
            "ur" -> "technical_words/ur.txt"
            else -> return null
        }
        val terms = termsByLanguage.getOrPut(language) {
            context.applicationContext.assets.open(file).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }
                    .map { it.lowercase(Locale.ROOT) }.distinct().sorted().toList()
            }
        }
        val prefix = typed.lowercase(Locale.ROOT)
        var low = 0
        var high = terms.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (terms[middle] < prefix) low = middle + 1 else high = middle
        }
        for (index in low until terms.size) {
            val candidate = terms[index]
            if (!candidate.startsWith(prefix)) return null
            if (!candidate.equals(prefix, ignoreCase = true)) return candidate
        }
        return null
    }
}
