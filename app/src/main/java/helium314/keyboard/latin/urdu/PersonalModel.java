// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the keyboard has learned from the owner's typing in a Roman -> native script language: how often each word is used,
 * which word follows which, and which Roman spelling the owner types for which word. Pure Java, no Android dependencies.
 * Lookups are a few hash reads, so it is safe to call from the decoder thread while the UI thread learns.
 */
public final class PersonalModel {
    // ln-unit bonuses added to the decoder score (RomanTransliterator.maxScoreGap is 8), each saturating
    static final double MAX_SPELLING_BONUS = 3.0, MAX_PAIR_BONUS = 1.5, MAX_WORD_BONUS = 1.2;
    static final int MAX_WORDS = 20000, MAX_PAIRS = 40000, MAX_SPELLINGS = 30000;
    static final int HALVE_EVERY = 4000;
    static final int MAX_TEXT = 40;
    /** a word that the decoder did not know is only offered after it was used for the same spelling this often */
    static final float MIN_COUNT_TO_INJECT = 2f;

    private final HashMap<String, Float> words = new HashMap<>();
    private final HashMap<String, HashMap<String, Float>> pairs = new HashMap<>();      // prev -> word -> count
    private final HashMap<String, HashMap<String, Float>> spellings = new HashMap<>();  // roman -> word -> count
    private int events;
    private boolean dirty;

    public static String normalizeRoman(String roman) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < roman.length(); i++) {
            final char c = Character.toLowerCase(roman.charAt(i));
            if (Character.isLetterOrDigit(c) || c == '\'') sb.append(c);
        }
        return sb.toString();
    }

    private static boolean usable(String s) {
        return s != null && !s.isEmpty() && s.length() <= MAX_TEXT;
    }

    private static void add(HashMap<String, Float> m, String k, float delta) {
        final float v = m.getOrDefault(k, 0f) + delta;
        if (v <= 0.01f) m.remove(k); else m.put(k, v);
    }

    private static void addNested(HashMap<String, HashMap<String, Float>> m, String k1, String k2, float delta) {
        HashMap<String, Float> inner = m.get(k1);
        if (inner == null) { if (delta <= 0) return; inner = new HashMap<>(4); m.put(k1, inner); }
        add(inner, k2, delta);
        if (inner.isEmpty()) m.remove(k1);
    }

    /** @param prev last word before [word] (native script), or null at sentence start; @param weight 1 = auto-committed, 2 = picked by hand */
    public synchronized void learn(String roman, String word, String prev, float weight) {
        change(roman, word, prev, weight);
        if (++events % HALVE_EVERY == 0) halve();
    }

    /** takes back what [learn] added for the same arguments (the owner reverted the commit) */
    public synchronized void unlearn(String roman, String word, String prev, float weight) {
        change(roman, word, prev, -weight);
    }

    private void change(String roman, String word, String prev, float delta) {
        if (!usable(word)) return;
        final String r = roman == null ? "" : normalizeRoman(roman);
        add(words, word, delta);
        if (usable(prev)) addNested(pairs, prev, word, delta);
        if (!r.isEmpty() && r.length() <= MAX_TEXT) addNested(spellings, r, word, delta);
        dirty = true;
    }

    private void halve() {
        halveMap(words);
        for (HashMap<String, Float> m : pairs.values()) halveMap(m);
        for (HashMap<String, Float> m : spellings.values()) halveMap(m);
        pairs.values().removeIf(HashMap::isEmpty);
        spellings.values().removeIf(HashMap::isEmpty);
    }

    private static void halveMap(HashMap<String, Float> m) {
        m.replaceAll((k, v) -> v / 2);
        m.values().removeIf(v -> v < 0.4f);
    }

    private static double saturate(float count, double max, double halfCount) {
        return count <= 0 ? 0 : max * (1 - Math.exp(-count / halfCount));
    }

    /** decoder bonus (ln units, >= 0) for [word] typed as [roman] after [prev]; the sum of three saturating terms */
    public synchronized double bonus(String prev, String word, String roman) {
        double b = 0;
        final Float w = words.get(word);
        if (w != null) b += saturate(w, MAX_WORD_BONUS, 4);
        if (prev != null) {
            final HashMap<String, Float> p = pairs.get(prev);
            final Float c = p == null ? null : p.get(word);
            if (c != null) b += saturate(c, MAX_PAIR_BONUS, 2);
        }
        if (roman != null) {
            final HashMap<String, Float> s = spellings.get(roman);
            final Float c = s == null ? null : s.get(word);
            if (c != null) b += saturate(c - 1, MAX_SPELLING_BONUS, 2);   // the first use counts for nothing: a one-off slip changes nothing
        }
        return b;
    }

    /** words used at least twice for exactly this (already normalized) spelling, most used first */
    public synchronized List<String> injected(String roman, int max) {
        final ArrayList<String> out = new ArrayList<>();
        final HashMap<String, Float> s = spellings.get(roman);
        if (s == null) return out;
        for (Map.Entry<String, Float> e : s.entrySet()) if (e.getValue() >= MIN_COUNT_TO_INJECT) out.add(e.getKey());
        out.sort((a, b) -> Float.compare(s.get(b), s.get(a)));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** words that followed [prev] at least once, most used first */
    public synchronized List<String> nextWords(String prev, int max) {
        final ArrayList<String> out = new ArrayList<>();
        final HashMap<String, Float> p = prev == null ? null : pairs.get(prev);
        if (p == null) return out;
        out.addAll(p.keySet());
        out.sort((a, b) -> Float.compare(p.get(b), p.get(a)));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    public synchronized boolean isEmpty() { return words.isEmpty(); }
    public synchronized int wordCount() { return words.size(); }
    public synchronized boolean isDirty() { return dirty; }

    public synchronized void clear() {
        words.clear(); pairs.clear(); spellings.clear();
        events = 0; dirty = true;
    }

    /** keeps the strongest entries of each table within its cap */
    private void trim() {
        trimFlat(words, MAX_WORDS);
        trimNested(pairs, MAX_PAIRS);
        trimNested(spellings, MAX_SPELLINGS);
    }

    private static void trimFlat(HashMap<String, Float> m, int cap) {
        if (m.size() <= cap) return;
        final ArrayList<Float> vs = new ArrayList<>(m.values());
        vs.sort(null);
        final float cut = vs.get(vs.size() - cap);
        m.values().removeIf(v -> v < cut);
    }

    private static void trimNested(HashMap<String, HashMap<String, Float>> m, int cap) {
        int n = 0;
        for (HashMap<String, Float> inner : m.values()) n += inner.size();
        if (n <= cap) return;
        final ArrayList<Float> vs = new ArrayList<>(n);
        for (HashMap<String, Float> inner : m.values()) vs.addAll(inner.values());
        vs.sort(null);
        final float cut = vs.get(vs.size() - cap);
        for (HashMap<String, Float> inner : m.values()) inner.values().removeIf(v -> v < cut);
        m.values().removeIf(HashMap::isEmpty);
    }

    private static final int MAGIC = 0x4C46505A; // "LFPZ"
    private static final int VERSION = 1;

    public synchronized void save(OutputStream raw) throws IOException {
        trim();
        final DataOutputStream out = new DataOutputStream(raw);
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(events);
        out.writeInt(words.size());
        for (Map.Entry<String, Float> e : words.entrySet()) { out.writeUTF(e.getKey()); out.writeFloat(e.getValue()); }
        writeNested(out, pairs);
        writeNested(out, spellings);
        out.flush();
        dirty = false;
    }

    private static void writeNested(DataOutputStream out, HashMap<String, HashMap<String, Float>> m) throws IOException {
        out.writeInt(m.size());
        for (Map.Entry<String, HashMap<String, Float>> e : m.entrySet()) {
            out.writeUTF(e.getKey());
            out.writeInt(e.getValue().size());
            for (Map.Entry<String, Float> f : e.getValue().entrySet()) { out.writeUTF(f.getKey()); out.writeFloat(f.getValue()); }
        }
    }

    /** a missing or damaged file gives an empty model */
    public static PersonalModel load(InputStream raw) {
        final PersonalModel pm = new PersonalModel();
        try {
            final DataInputStream in = new DataInputStream(raw);
            if (in.readInt() != MAGIC || in.readInt() != VERSION) return pm;
            pm.events = in.readInt();
            final int n = in.readInt();
            for (int i = 0; i < n; i++) { final String k = in.readUTF(); pm.words.put(k, in.readFloat()); }
            readNested(in, pm.pairs);
            readNested(in, pm.spellings);
        } catch (IOException | RuntimeException e) {
            pm.clear();
            pm.dirty = false;
        }
        return pm;
    }

    private static void readNested(DataInputStream in, HashMap<String, HashMap<String, Float>> m) throws IOException {
        final int n = in.readInt();
        for (int i = 0; i < n; i++) {
            final String k = in.readUTF();
            final int c = in.readInt();
            final HashMap<String, Float> inner = new HashMap<>(Math.max(4, c * 2));
            for (int j = 0; j < c; j++) { final String k2 = in.readUTF(); inner.put(k2, in.readFloat()); }
            m.put(k, inner);
        }
    }
}
