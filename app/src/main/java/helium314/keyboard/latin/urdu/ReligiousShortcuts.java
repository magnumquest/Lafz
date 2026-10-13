// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Religious honorific shortcuts and common religious terms, for English (Latin), Urdu and Arabic typing:
 * <pre>
 *   "Rasul"     -> "Rasulullah ﷺ", "Rasulullah Sallallahu Alaihi Wasallam"
 *   "Umar" + "ra" -> "Radiallahu Anhu"  (the form depends on the type of the previous name: Anha, Anhuma, ...)
 *   "Isa"       -> "Isa Alaihis Salaam"
 *   "inshallah" -> "InshaAllah", "ان شاء اللہ" (Urdu output)
 * </pre>
 * Data: assets/honorifics/honorifics.tsv. No Android dependencies.
 */
public final class ReligiousShortcuts {
    /** output scripts: Latin letters (English), Urdu spelling, Arabic spelling */
    public static final int LATIN = 0, URDU = 1, ARABIC = 2;

    private static final class Entry {
        char type;            // 0 for explicit phrase entries, otherwise H P Q M F D W
        String latinName, urduName, arabicName;
        String[] latin, urdu, arabic; // explicit forms for phrase entries
    }

    /** honorific suffix forms by type: {latin..., urdu..., arabic...} indexed by output script */
    private static final Map<Character, String[][]> SUFFIX = new HashMap<>();
    static {
        SUFFIX.put('H', new String[][]{{"ﷺ", "Sallallahu Alaihi Wasallam"}, {"ﷺ", "صلی اللہ علیہ وآلہ وسلم"}, {"ﷺ", "صلى الله عليه وسلم"}});
        SUFFIX.put('P', new String[][]{{"Alaihis Salaam", "(AS)"}, {"علیہ السلام"}, {"عليه السلام"}});
        SUFFIX.put('Q', new String[][]{{"Alaihas Salaam"}, {"علیہا السلام"}, {"عليها السلام"}});
        SUFFIX.put('M', new String[][]{{"Radiallahu Anhu", "(RA)"}, {"رضی اللہ عنہ"}, {"رضي الله عنه"}});
        SUFFIX.put('F', new String[][]{{"Radiallahu Anha", "(RA)"}, {"رضی اللہ عنہا"}, {"رضي الله عنها"}});
        SUFFIX.put('D', new String[][]{{"Radiallahu Anhuma", "(RA)"}, {"رضی اللہ عنہما"}, {"رضي الله عنهما"}});
        SUFFIX.put('W', new String[][]{{"Rahimahullah", "Rahmatullahi Alaihi"}, {"رحمہ اللہ", "رحمۃ اللہ علیہ"}, {"رحمه الله", "رحمة الله عليه"}});
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, List<Entry>> exact = new HashMap<>();
    private final List<String> triggers = new ArrayList<>();
    private final List<Entry> triggerEntry = new ArrayList<>();
    private final Map<String, List<Entry>> foldExact = new HashMap<>();   // spelling-folded triggers
    private final List<String> foldTriggers = new ArrayList<>();

    private ReligiousShortcuts() {}

    public static ReligiousShortcuts load(InputStream in) throws IOException {
        final ReligiousShortcuts r = new ReligiousShortcuts();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                final String[] p = line.split("\t", -1);
                if (p.length < 5) continue;
                final Entry e = new Entry();
                final boolean hasArabic = p.length > 5 && !p[5].isEmpty();   // column 6, falls back to the Urdu spelling
                if (p[0].equals("N")) { e.type = p[1].charAt(0); e.latinName = p[3]; e.urduName = p[4]; e.arabicName = hasArabic ? p[5] : p[4]; }
                else { e.latin = p[3].split("\\|"); e.urdu = p[4].split("\\|"); e.arabic = (hasArabic ? p[5] : p[4]).split("\\|"); }
                r.entries.add(e);
                for (String t : p[2].split(",")) {
                    final String key = normalize(t);
                    if (key.isEmpty()) continue;
                    List<Entry> l = r.exact.get(key);
                    if (l == null) { l = new ArrayList<>(); r.exact.put(key, l); }
                    l.add(e);
                    r.triggers.add(key); r.triggerEntry.add(e);
                    final String fk = fold(key);
                    List<Entry> fl = r.foldExact.get(fk);
                    if (fl == null) { fl = new ArrayList<>(); r.foldExact.put(fk, fl); }
                    fl.add(e);
                    r.foldTriggers.add(fk);
                }
            }
        }
        return r;
    }

    public static boolean isUrduScript(String s) {
        for (int i = 0; i < s.length(); i++) { final char c = s.charAt(i); if (c >= 0x0600 && c <= 0x06FF) return true; }
        return false;
    }

    /** lower-case latin letters only, or Urdu letters without diacritics/spaces */
    static String normalize(String s) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) {
                if ((c >= 0x064B && c <= 0x065F) || c == 0x0670 || c == 0x0640) continue;
                if (c == 'ي' || c == 'ى') c = 'ی'; else if (c == 'ك') c = 'ک'; else if (c == 'ۃ' || c == 'ة' || c == 'ه') c = 'ہ';
                else if (c == 'أ' || c == 'إ' || c == 'ٱ') c = 'ا';
                sb.append(c);
            } else if (Character.isLetter(c) && c < 128) sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /** spelling-insensitive form of a latin key: doubled letters collapsed, y=i, u=o (salallahu / sallallahu, rasool / rasul) */
    static String fold(String k) {
        if (isUrduScript(k)) return k;
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            if (c == 'y') c = 'i'; else if (c == 'u') c = 'o';
            if (sb.length() == 0 || sb.charAt(sb.length() - 1) != c) sb.append(c);
        }
        return sb.toString();
    }

    private char typeOfName(String word) {
        if (word == null) return 0;
        final List<Entry> l = exact.get(normalize(word));
        if (l == null) return 0;
        for (Entry e : l) if (e.type != 0) return e.type;
        return 0;
    }

    private static void addAll(LinkedHashSet<String> out, String... forms) { for (String f : forms) out.add(f); }

    private static String[] suffix(char type, int script) {
        final String[][] s = SUFFIX.get(type);
        return s == null ? new String[0] : s[script];
    }

    private static void expand(Entry e, int script, LinkedHashSet<String> out) {
        if (e.type == 0) { addAll(out, script == ARABIC ? e.arabic : script == URDU ? e.urdu : e.latin); return; }
        final String name = script == ARABIC ? e.arabicName : script == URDU ? e.urduName : e.latinName;
        for (String s : suffix(e.type, script)) out.add(name + " " + s);
    }

    /** Contextual abbreviations like "ra" after a name: suffix-only forms, or null if t is not an abbreviation. */
    private List<String> abbreviation(String t, String prev, int script) {
        final char pt = typeOfName(prev);
        final LinkedHashSet<String> out = new LinkedHashSet<>();
        switch (t) {
            case "ra": case "rz": case "rdh": case "rta":
                final char[] order = pt == 'F' ? new char[]{'F', 'M', 'D'} : pt == 'D' ? new char[]{'D', 'M', 'F'} : new char[]{'M', 'F', 'D'};
                for (char c : order) { final String[] s = suffix(c, script); if (!s[0].equals("(RA)")) out.add(s[0]); }
                out.add(script == ARABIC ? "رضي الله عنهم" : script == URDU ? "رضی اللہ عنہم" : "Radiallahu Anhum");
                break;
            case "as": case "a.s":
                if (pt == 'P' || pt == 'Q') for (String s : suffix(pt, script)) out.add(s);
                break;
            case "saw": case "saww": case "saws":
                if (pt == 'H') for (String s : suffix('H', script)) out.add(s);
                break;
            case "rh": case "ra.": 
                if (pt == 'W') for (String s : suffix('W', script)) out.add(s);
                else addAll(out, script == ARABIC ? new String[]{"رحمه الله", "رحمة الله عليه", "رحمة الله عليها"}
                        : script == URDU ? new String[]{"رحمہ اللہ", "رحمۃ اللہ علیہ", "رحمۃ اللہ علیہا"}
                        : new String[]{"Rahimahullah", "Rahmatullahi Alaihi", "Rahmatullahi Alaiha"});
                break;
            default: return null;
        }
        return new ArrayList<>(out);
    }

    public List<String> suggest(String typed, String prev, boolean urduOutput, int max) {
        return suggest(typed, prev, urduOutput ? URDU : LATIN, max);
    }

    /**
     * @param typed   the word being typed (Latin, Urdu or Arabic script)
     * @param prev    the previous word, or null
     * @param script  output script: [LATIN], [URDU] or [ARABIC] (Roman->Urdu / Roman->Arabic typing and native typing
     *                use the matching script; English typing uses LATIN)
     */
    public List<String> suggest(String typed, String prev, int script, int max) {
        final String key = normalize(typed);
        final ArrayList<String> result = new ArrayList<>();
        if (key.length() < 2) return result;
        final boolean urduTyped = isUrduScript(typed);
        if (!urduTyped) {
            final List<String> a = abbreviation(key, prev, script);
            if (a != null) { for (String s : a) { if (result.size() < max) result.add(s); } return result; }
        }
        final int minPrefix = urduTyped ? 3 : 4; // short prefixes (all, fat, mar ...) would fire on ordinary words
        final ArrayList<Entry> hit = new ArrayList<>();
        final ArrayList<Integer> rank = new ArrayList<>();
        final List<Entry> ex = exact.get(key);
        if (ex != null) for (Entry e : ex) { hit.add(e); rank.add(0); }
        final String fk = fold(key);
        final List<Entry> fex = foldExact.get(fk);
        if (fex != null) for (Entry e : fex) if (!hit.contains(e)) { hit.add(e); rank.add(0); }
        if (key.length() >= minPrefix) {
            for (int i = 0; i < foldTriggers.size(); i++) {
                final String t = foldTriggers.get(i);
                if (t.length() > fk.length() && t.startsWith(fk) && !hit.contains(triggerEntry.get(i))) {
                    hit.add(triggerEntry.get(i)); rank.add(1 + t.length() - fk.length());
                }
            }
        }
        // stable sort by rank
        final Integer[] idx = new Integer[hit.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (x, y) -> rank.get(x) != rank.get(y).intValue() ? Integer.compare(rank.get(x), rank.get(y)) : Integer.compare(x, y));
        final LinkedHashSet<String> out = new LinkedHashSet<>();
        for (int i : idx) expand(hit.get(i), script, out);
        for (String s : out) { if (result.size() < max) result.add(s); }
        return result;
    }

    /** Honorific forms to offer right after a committed name, e.g. after "Umar " -> "Radiallahu Anhu". */
    public List<String> afterWord(String prev, boolean urduOutput, int max) {
        return afterWord(prev, urduOutput ? URDU : LATIN, max);
    }

    public List<String> afterWord(String prev, int script, int max) {
        final ArrayList<String> out = new ArrayList<>();
        final char t = typeOfName(prev);
        if (t == 0) return out;
        for (String s : suffix(t, script)) if (out.size() < max) out.add(s);
        return out;
    }
}
