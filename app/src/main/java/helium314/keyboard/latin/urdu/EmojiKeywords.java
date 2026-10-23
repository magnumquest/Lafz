// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small immutable exact-keyword index. It never decodes text or runs a search over the vocabulary. */
public final class EmojiKeywords {
    private final Map<String, List<String>> keywords;

    private EmojiKeywords(Map<String, List<String>> keywords) { this.keywords = keywords; }

    /** TSV: space-separated emoji, then pipe-separated aliases. Data is bundled, never downloaded. */
    public static EmojiKeywords load(Reader input) throws IOException {
        Map<String, LinkedHashSet<String>> entries = new LinkedHashMap<>();
        BufferedReader reader = new BufferedReader(input);
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] columns = line.split("\t", -1);
            if (columns.length != 2) throw new IOException("Invalid emoji keyword row");
            List<String> emoji = List.of(columns[0].trim().split(" +"));
            for (String alias : columns[1].split("\\|")) {
                String key = normalize(alias);
                if (!key.isEmpty()) entries.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addAll(emoji);
            }
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        entries.forEach((key, value) -> result.put(key, Collections.unmodifiableList(new ArrayList<>(value))));
        return new EmojiKeywords(Collections.unmodifiableMap(result));
    }

    public List<String> suggest(String text, int limit) {
        if (text == null || limit <= 0) return Collections.emptyList();
        List<String> result = keywords.get(normalize(text));
        if (result == null) return Collections.emptyList();
        return result.subList(0, Math.min(limit, result.size()));
    }

    private static String normalize(String text) {
        String lower = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
        StringBuilder key = new StringBuilder();
        lower.codePoints().forEach(cp -> {
            // Direction marks, tatweel and Arabic vowel marks do not change the keyword.
            if (cp == 0x200E || cp == 0x200F || cp == 0x061C || cp == 0x0640
                    || (cp >= 0x064B && cp <= 0x065F) || cp == 0x0670) return;
            if (cp == 0x064A || cp == 0x0649) cp = 0x06CC; // Arabic / Persian yeh
            if (cp == 0x0643) cp = 0x06A9; // Arabic / Persian kaf
            key.appendCodePoint(cp);
        });
        return key.toString();
    }
}
