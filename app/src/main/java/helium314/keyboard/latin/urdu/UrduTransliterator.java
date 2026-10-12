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
 * Offline Roman -> Urdu script transliteration, modelled on the approach described in
 * "Transliterated mobile keyboard input via weighted finite-state transducers" (Hellsten et al., 2017):
 * a lexicon + language model decoded through a noisy-channel model, instead of a fixed phonetic map.
 * <p>
 * score(word) = ln P(word | previous word) + channelWeight * ln P(roman | word)
 * <p>
 * P(roman | word) is a monotone character-alignment model (each Urdu letter emits a short Roman chunk, e.g.
 * ک -> k / ka / ki) learned with EM from human-written romanizations, summed over all alignments (forward algorithm).
 * Words the model has seen people romanize also get a bonus for those exact spellings.
 * No Android dependencies: this class is unit-testable on a plain JVM.
 */
public final class UrduTransliterator {
    public static final class Candidate {
        public final String urdu;
        public final double score;
        /** true if the typed Roman text is only a prefix of this word's romanization (word completion) */
        public final boolean completion;
        Candidate(String urdu, double score, boolean completion) {
            this.urdu = urdu; this.score = score; this.completion = completion;
        }
        @Override public String toString() { return urdu + (completion ? "…" : "") + String.format(" %.2f", score); }
    }

    // tunables (set from offline evaluation, see tools/urdu)
    public double channelWeight = 1.0;
    public double observedBonus = 1.0;
    public double completionPenalty = 1.2; // per skipped trailing Urdu letter (ln units)
    public double bigramWeight = 0.7;

    private static final int MAXN = 24, MAXM = 40;

    private static final class Chan {
        final char[][] chunks; final double[] p; final int[] len;
        Chan(char[][] chunks, double[] p) {
            this.chunks = chunks; this.p = p; len = new int[chunks.length];
            for (int i = 0; i < chunks.length; i++) len[i] = chunks[i].length;
        }
    }

    private final int[] charIdx = new int[0x800];   // Urdu char -> channel alphabet index (+1), 0 = unknown
    private Chan[] chans;                           // [idx * 3 + pos]  pos: 0 initial, 1 medial, 2 final
    private String[] words;
    private char[][] wordChars;
    private int[][] wordCharIdx;
    private float[] uniLn;
    private String[][] variants;
    private float[][] variantP;
    private final HashMap<String, Integer> wordId = new HashMap<>();
    private int[] bigramKeys;     // sorted w1 ids, -1 = sentence start
    private int[][] bigramNext;
    private byte[][] bigramQ;
    private int[][] firstLetterBuckets = new int[26][];

    private UrduTransliterator() {}

    public static UrduTransliterator load(InputStream raw) throws IOException {
        final DataInputStream in = new DataInputStream(new BufferedInputStream(raw, 1 << 16));
        final byte[] magic = new byte[4];
        in.readFully(magic);
        if (!new String(magic, "US-ASCII").equals("UTRL")) throw new IOException("bad model file");
        in.readInt(); // version
        final UrduTransliterator t = new UrduTransliterator();
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
        for (int i = 0; i < nWords; i++) {
            final int len = in.readUnsignedByte();
            final char[] w = new char[len];
            final int[] ix = new int[len];
            for (int j = 0; j < len; j++) { w[j] = in.readChar(); ix[j] = w[j] < t.charIdx.length ? t.charIdx[w[j]] : 0; }
            t.words[i] = new String(w);
            t.wordChars[i] = w;
            t.wordCharIdx[i] = ix;
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

    private void buildBuckets() {
        final ArrayList<ArrayList<Integer>> lists = new ArrayList<>();
        for (int i = 0; i < 26; i++) lists.add(new ArrayList<>());
        for (int w = 0; w < words.length; w++) {
            final boolean[] seen = new boolean[26];
            firstLetters(wordCharIdx[w], 0, seen, 0);
            for (int l = 0; l < 26; l++) if (seen[l]) lists.get(l).add(w);
        }
        for (int l = 0; l < 26; l++) {
            firstLetterBuckets[l] = new int[lists.get(l).size()];
            for (int i = 0; i < firstLetterBuckets[l].length; i++) firstLetterBuckets[l][i] = lists.get(l).get(i);
        }
    }

    private void firstLetters(int[] ix, int i, boolean[] seen, int depth) {
        if (i >= ix.length || depth > 3 || ix[i] == 0) return;
        final Chan c = chans[ix[i] * 3 + (i == 0 ? 0 : i == ix.length - 1 ? 2 : 1)];
        if (c == null) return;
        for (int k = 0; k < c.chunks.length; k++) {
            if (c.len[k] == 0) firstLetters(ix, i + 1, seen, depth + 1);
            else { final int l = c.chunks[k][0] - 'a'; if (l >= 0 && l < 26) seen[l] = true; }
        }
    }

    public boolean isKnownWord(String urdu) { return wordId.containsKey(urdu); }
    public int vocabularySize() { return words.length; }

    private int bigramIndex(int w1) {
        return Arrays.binarySearch(bigramKeys, w1);
    }

    /** ln P(w | prev). prev: Urdu word, or null at sentence start; unknown words fall back to the unigram. */
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
     * @param prev   previous Urdu word, or null if at sentence start / unknown
     * @param atStart true if sentence start
     */
    public List<Candidate> suggest(String roman, String prev, boolean atStart, int max) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < roman.length() && sb.length() < MAXM; i++) {
            final char c = Character.toLowerCase(roman.charAt(i));
            if (c >= 'a' && c <= 'z') sb.append(c);
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
            final Integer p = wordId.get(prev);
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
        for (final int w : firstLetterBuckets[r[0] - 'a']) {
            final int n = wordCharIdx[w].length;
            if (n > MAXN || n == 0) continue;
            final int[] ix = wordCharIdx[w];
            for (int i = 0; i <= n; i++) Arrays.fill(A[i], 0.0);
            A[0][0] = 1.0;
            boolean ok = true;
            for (int i = 0; i < n && ok; i++) {
                final Chan c = ix[i] == 0 ? null : chans[ix[i] * 3 + (i == 0 ? 0 : i == n - 1 ? 2 : 1)];
                if (c == null) { ok = false; break; }
                final double[] row = A[i], nxt = A[i + 1];
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
            if (topScore[i] == Double.NEGATIVE_INFINITY) break;
            out.add(new Candidate(words[topWord[i]], topScore[i], topComp[i]));
        }
        return out;
    }
}
