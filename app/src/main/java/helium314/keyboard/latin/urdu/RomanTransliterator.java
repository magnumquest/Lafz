// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Offline Roman -> native script transliteration (Urdu, Arabic: one model file per language, same engine),
 * modelled on the approach described in
 * "Transliterated mobile keyboard input via weighted finite-state transducers" (Hellsten et al., 2017):
 * a lexicon + language model decoded through a noisy-channel model, instead of a fixed phonetic map.
 * <p>
 * score(word) = ln P(word | previous word) + channelWeight * ln P(roman | word)
 * <p>
 * P(roman | word) is a monotone character-alignment model (each Urdu letter emits a short Roman chunk, e.g.
 * ک -> k / ka / ki) learned with EM from human-written romanizations (or, where none are available, a documented prior),
 * summed over all alignments (forward algorithm).
 * Words the model has seen people romanize also get a bonus for those exact spellings.
 * No Android dependencies: this class is unit-testable on a plain JVM.
 */
public final class RomanTransliterator {
    public static final class Candidate {
        /** the word in native script (may contain a space for multi-word entries) */
        public final String text;
        public final double score;
        /** true if the typed Roman text is only a prefix of this word's romanization (word completion) */
        public final boolean completion;
        Candidate(String text, double score, boolean completion) {
            this.text = text; this.score = score; this.completion = completion;
        }
        @Override public String toString() { return text + (completion ? "…" : "") + String.format(" %.2f", score); }
    }

    // tunables (set from offline evaluation, see tools/urdu and tools/arabic); per-language values are set by RomanScript
    public double channelWeight = 1.4;
    public double observedBonus = 1.0;
    public double completionPenalty = 1.2; // per skipped trailing Urdu letter (ln units)
    public double bigramWeight = 0.5;
    /** candidates whose ln channel probability is below this are junk (nothing like what was typed) and are not shown */
    public double minLogChannel = -18;
    /** candidates scoring this much (ln) worse than the best one are not shown */
    public double maxScoreGap = 8;
    /** word completions are only offered for words at least this frequent (ln unigram) */
    public double minCompletionUni = -12.5;

    private static final int MAXN = 24, MAXM = 40;

    private static final class Chan {
        final char[][] chunks; final double[] p; final int[] len;
        Chan(char[][] chunks, double[] p) {
            this.chunks = chunks; this.p = p; len = new int[chunks.length];
            for (int i = 0; i < chunks.length; i++) len[i] = chunks[i].length;
        }
    }

    private final int[] charIdx = new int[0x800];   // native-script char -> channel alphabet index (+1), 0 = unknown
    private Chan[] chans;                           // [idx * 3 + pos]  pos: 0 initial, 1 medial, 2 final
    private String[] words;
    private char[][] wordChars;
    private int[][] wordCharIdx;   // channel alphabet index per letter (spaces of multi-word entries removed)
    private byte[][] wordPos;      // 0 token-initial, 1 medial, 2 token-final
    private float[] uniLn;
    private String[][] variants;
    private float[][] variantP;
    private final HashMap<String, Integer> wordId = new HashMap<>();
    private int[] bigramKeys;     // sorted w1 ids, -1 = sentence start
    private int[][] bigramNext;
    private byte[][] bigramQ;
    /** typed symbols the model understands: a-z, digits (Arabizi 2 3 5 6 7 8 9) and the apostrophe, see {@link #slot} */
    private static final int SLOTS = 37;
    private int[][] firstLetterBuckets = new int[SLOTS][];

    private RomanTransliterator() {}

    public static RomanTransliterator load(InputStream raw) throws IOException {
        final DataInputStream in = new DataInputStream(new BufferedInputStream(raw, 1 << 16));
        final byte[] magic = new byte[4];
        in.readFully(magic);
        if (!new String(magic, "US-ASCII").equals("UTRL")) throw new IOException("bad model file");
        in.readInt(); // version
        final RomanTransliterator t = new RomanTransliterator();
        final int nKeys = in.readInt();
        final HashMap<Integer, Chan> tmp = new HashMap<>();
        int nChars = 0;
        final HashMap<Character, Integer> alpha = new HashMap<>();
        for (int k = 0; k < nKeys; k++) {
            final char c = (char) in.readUnsignedShort();
            final int pos = "IMF".indexOf(in.readUnsignedByte());
            final int n = in.readUnsignedByte();
            final char[][] ch = new char[n][];
            final double[] p = new double[n];
            for (int i = 0; i < n; i++) {
                ch[i] = readAscii(in).toCharArray();
                p[i] = Math.exp(in.readFloat());
            }
            Integer idx = alpha.get(c);
            if (idx == null) { idx = ++nChars; alpha.put(c, idx); }
            if (c < t.charIdx.length) t.charIdx[c] = idx;
            tmp.put(idx * 3 + pos, new Chan(ch, p));
        }
        t.chans = new Chan[(nChars + 1) * 3];
        for (HashMap.Entry<Integer, Chan> e : tmp.entrySet()) t.chans[e.getKey()] = e.getValue();

        final int nWords = in.readInt();
        t.words = new String[nWords];
        t.wordChars = new char[nWords][];
        t.wordCharIdx = new int[nWords][];
        t.uniLn = new float[nWords];
        t.variants = new String[nWords][];
        t.variantP = new float[nWords][];
        t.wordPos = new byte[nWords][];
        for (int i = 0; i < nWords; i++) {
            final int len = in.readUnsignedByte();
            final char[] w = new char[len];
            for (int j = 0; j < len; j++) w[j] = in.readChar();
            t.words[i] = new String(w);
            t.wordChars[i] = w;
            int dp = 0;
            for (char c : w) if (c != ' ') dp++;
            final int[] ix = new int[dp];
            final byte[] pos = new byte[dp];
            int k = 0;
            for (int j = 0; j < len; j++) {
                if (w[j] == ' ') continue;
                ix[k] = w[j] < t.charIdx.length ? t.charIdx[w[j]] : 0;
                final boolean first = j == 0 || w[j - 1] == ' ';
                final boolean last = j == len - 1 || w[j + 1] == ' ';
                pos[k] = (byte) (first ? 0 : last ? 2 : 1);
                k++;
            }
            t.wordCharIdx[i] = ix;
            t.wordPos[i] = pos;
            t.uniLn[i] = in.readFloat();
            final int nv = in.readUnsignedByte();
            t.variants[i] = new String[nv];
            t.variantP[i] = new float[nv];
            for (int v = 0; v < nv; v++) {
                t.variants[i][v] = readAscii(in);
                t.variantP[i][v] = in.readUnsignedByte() / 255f;
            }
            t.wordId.put(t.words[i], i);
        }
        final int nb = in.readInt();
        t.bigramKeys = new int[nb];
        t.bigramNext = new int[nb][];
        t.bigramQ = new byte[nb][];
        for (int k = 0; k < nb; k++) {
            t.bigramKeys[k] = in.readInt();
            final int n = in.readInt();
            final int[] nx = new int[n];
            final byte[] q = new byte[n];
            int prev = 0;
            for (int i = 0; i < n; i++) {
                int d = 0, shift = 0, b;
                do { b = in.readUnsignedByte(); d |= (b & 127) << shift; shift += 7; } while ((b & 128) != 0);
                prev += d; nx[i] = prev; q[i] = in.readByte();
            }
            t.bigramNext[k] = nx; t.bigramQ[k] = q;
        }
        t.buildBuckets();
        return t;
    }

    private static String readAscii(DataInputStream in) throws IOException {
        final int n = in.readUnsignedByte();
        final byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, "UTF-8");
    }

    /** index of a typed symbol in the first-letter buckets, or -1 if the symbol is not used for transliteration */
    private static int slot(char c) {
        if (c >= 'a' && c <= 'z') return c - 'a';
        if (c >= '0' && c <= '9') return 26 + (c - '0');
        if (c == '\'') return 36;
        return -1;
    }

    private void buildBuckets() {
        final ArrayList<ArrayList<Integer>> lists = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) lists.add(new ArrayList<>());
        for (int w = 0; w < words.length; w++) {
            final boolean[] seen = new boolean[SLOTS];
            firstLetters(wordCharIdx[w], wordPos[w], 0, seen, 0);
            for (String v : variants[w]) { final int l = slot(v.charAt(0)); if (l >= 0) seen[l] = true; }
            for (int l = 0; l < SLOTS; l++) if (seen[l]) lists.get(l).add(w);
        }
        for (int l = 0; l < SLOTS; l++) {
            firstLetterBuckets[l] = new int[lists.get(l).size()];
            for (int i = 0; i < firstLetterBuckets[l].length; i++) firstLetterBuckets[l][i] = lists.get(l).get(i);
        }
    }

    private void firstLetters(int[] ix, byte[] pos, int i, boolean[] seen, int depth) {
        if (i >= ix.length || depth > 3 || ix[i] == 0) return;
        final Chan c = chans[ix[i] * 3 + pos[i]];
        if (c == null) return;
        for (int k = 0; k < c.chunks.length; k++) {
            if (c.len[k] == 0) firstLetters(ix, pos, i + 1, seen, depth + 1);
            else { final int l = slot(c.chunks[k][0]); if (l >= 0) seen[l] = true; }
        }
    }

    public boolean isKnownWord(String word) { return wordId.containsKey(word); }
    /** ln unigram probability of a dictionary word, or -Infinity if unknown */
    public double unigramLn(String word) {
        final Integer i = wordId.get(word);
        return i == null ? Double.NEGATIVE_INFINITY : uniLn[i];
    }
    public int vocabularySize() { return words.length; }

    private int bigramIndex(int w1) {
        return Arrays.binarySearch(bigramKeys, w1);
    }

    /** ln P(w | prev). prev: native-script word, or null at sentence start; unknown words fall back to the unigram. */
    private double lmLn(int w, int prevKey, int bgIdx) {
        final double uni = Math.exp(uniLn[w]);
        if (bgIdx < 0) return Math.log(uni);
        final int j = Arrays.binarySearch(bigramNext[bgIdx], w);
        if (j < 0) return Math.log((1 - bigramWeight) * uni);
        final double pb = Math.exp(-(bigramQ[bgIdx][j] & 0xff) / 12.0);
        return Math.log(bigramWeight * pb + (1 - bigramWeight) * uni);
    }

    /**
     * @param roman  Roman text typed so far (current word only)
     * @param prev   previous word (native script), or null if at sentence start / unknown
     * @param atStart true if sentence start
     */
    public List<Candidate> suggest(String roman, String prev, boolean atStart, int max) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < roman.length() && sb.length() < MAXM; i++) {
            final char c = Character.toLowerCase(roman.charAt(i));
            if (slot(c) >= 0) sb.append(c);
        }
        final char[] r = new char[sb.length()];
        sb.getChars(0, r.length, r, 0);
        final int m = r.length;
        final ArrayList<Candidate> out = new ArrayList<>();
        if (m == 0) return out;
        int bg = -1;
        int prevKey = -2;
        if (atStart) { prevKey = -1; bg = bigramIndex(-1); }
        else if (prev != null) {
            final Integer p = wordId.get(prev.substring(prev.lastIndexOf(' ') + 1)); // context is the last word of a phrase
            if (p != null) { prevKey = p; bg = bigramIndex(p); }
        }
        final String rs = sb.toString();
        final double[][] A = new double[MAXN + 1][m + 1];
        // keep a bounded best-list
        final int cap = Math.max(max, 1);
        final double[] topScore = new double[cap];
        final int[] topWord = new int[cap];
        final boolean[] topComp = new boolean[cap];
        Arrays.fill(topScore, Double.NEGATIVE_INFINITY);
        for (final int w : firstLetterBuckets[slot(r[0])]) {
            final int n = wordCharIdx[w].length;
            if (n > MAXN || n == 0) continue;
            final int[] ix = wordCharIdx[w];
            for (int i = 0; i <= n; i++) Arrays.fill(A[i], 0.0);
            A[0][0] = 1.0;
            boolean ok = true;
            for (int i = 0; i < n && ok; i++) {
                final double[] row = A[i], nxt = A[i + 1];
                boolean live = false;
                for (int j = 0; j <= m; j++) if (row[j] != 0.0) { live = true; break; }
                if (!live) break;   // nothing in the typed text can be aligned any more: the remaining rows stay 0
                final Chan c = ix[i] == 0 ? null : chans[ix[i] * 3 + wordPos[w][i]];
                if (c == null) { ok = false; break; }
                for (int j = 0; j <= m; j++) {
                    final double a = row[j];
                    if (a == 0.0) continue;
                    for (int k = 0; k < c.chunks.length; k++) {
                        final int l = c.len[k];
                        if (j + l > m) continue;
                        final char[] ch = c.chunks[k];
                        int q = 0;
                        while (q < l && ch[q] == r[j + q]) q++;
                        if (q == l) nxt[j + l] += a * c.p[k];
                    }
                }
            }
            if (!ok) continue;
            double chan = A[n][m];
            boolean comp = false;
            if (m >= 2) {
                for (int i = 1; i < n; i++) {
                    final double v = A[i][m] * Math.exp(-completionPenalty * (n - i));
                    if (v > chan) { chan = v; comp = true; }
                }
            }
            final String[] vs = variants[w];
            for (int v = 0; v < vs.length; v++) {
                if (vs[v].equals(rs)) { chan += observedBonus * variantP[w][v]; comp = false; break; }
            }
            if (chan <= 0) continue;
            final double lnChan = Math.log(chan);
            if (lnChan < minLogChannel) continue;                       // nothing like what was typed
            if (comp && uniLn[w] < minCompletionUni) continue;          // don't complete to rare words
            final double score = lmLn(w, prevKey, bg) + channelWeight * Math.log(chan);
            if (score > topScore[cap - 1]) {
                int pos = cap - 1;
                while (pos > 0 && topScore[pos - 1] < score) {
                    topScore[pos] = topScore[pos - 1]; topWord[pos] = topWord[pos - 1]; topComp[pos] = topComp[pos - 1]; pos--;
                }
                topScore[pos] = score; topWord[pos] = w; topComp[pos] = comp;
            }
        }
        for (int i = 0; i < cap; i++) {
            if (topScore[i] == Double.NEGATIVE_INFINITY || topScore[i] < topScore[0] - maxScoreGap) break;
            out.add(new Candidate(words[topWord[i]], topScore[i], topComp[i]));
        }
        return out;
    }

    /** Most likely next words after [prev] (or at sentence start), from the bigram table. */
    public List<String> nextWords(String prev, boolean atStart, int max) {
        final ArrayList<String> out = new ArrayList<>();
        int key = -2;
        if (atStart) key = -1;
        else if (prev != null) { final Integer p = wordId.get(prev.substring(prev.lastIndexOf(' ') + 1)); if (p != null) key = p; }
        if (key == -2) return out;
        final int bg = bigramIndex(key);
        if (bg < 0) return out;
        final int[] nx = bigramNext[bg]; final byte[] q = bigramQ[bg];
        final int[] best = new int[Math.max(max, 1)];
        final int[] bq = new int[best.length];
        Arrays.fill(bq, Integer.MAX_VALUE);
        for (int i = 0; i < nx.length; i++) {
            final int v = q[i] & 0xff;
            if (v >= bq[best.length - 1]) continue;
            int pos = best.length - 1;
            while (pos > 0 && bq[pos - 1] > v) { bq[pos] = bq[pos - 1]; best[pos] = best[pos - 1]; pos--; }
            bq[pos] = v; best[pos] = nx[i];
        }
        for (int i = 0; i < best.length && bq[i] != Integer.MAX_VALUE; i++) out.add(words[best[i]]);
        return out;
    }
}
