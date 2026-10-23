// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    /** weight of the trigram (scripture phrase memory) in the language model, when the two previous words are known */
    public double triWeight = 0.5;
    /** glued input ("innamal'amal"): extra ln penalty per additional word, at most [maxSegments] words, each segment at least [minSegChars] typed symbols */
    public double segPenalty = 3.0;
    public int maxSegments = 3, minSegChars = 2;
    /** the glued-word search only runs for typed texts at least this long (shorter ones are single words) */
    public int segMinTyped = 6;
    /** a segment of a glued word must have at least this ln channel probability */
    public double segMinLogChannel = -9;
    /** a completion on top of the list is replaced by the best full reading that scores at most this much (ln) worse */
    public double completionCommitGap = 4.0;

    private static final int MAXN = 24, MAXM = 48;

    private static final class Chan {
        final char[][] chunks; final double[] p; final int[] len;
        /** chunk indices (original order) that can match when the next typed symbol has the given slot: empty chunks + chunks starting with it */
        final int[][] cand = new int[SLOTS][];
        /** empty chunks only: the only ones that can match at the end of the typed text */
        final int[] emptyIdx;
        Chan(char[][] chunks, double[] p) {
            this.chunks = chunks; this.p = p; len = new int[chunks.length];
            for (int i = 0; i < chunks.length; i++) len[i] = chunks[i].length;
            for (int s = 0; s < SLOTS; s++) {
                int n = 0;
                for (int k = 0; k < chunks.length; k++) if (len[k] == 0 || slot(chunks[k][0]) == s) n++;
                cand[s] = new int[n];
                n = 0;
                for (int k = 0; k < chunks.length; k++) if (len[k] == 0 || slot(chunks[k][0]) == s) cand[s][n++] = k;
            }
            int e = 0;
            for (int k = 0; k < chunks.length; k++) if (len[k] == 0) e++;
            emptyIdx = new int[e];
            e = 0;
            for (int k = 0; k < chunks.length; k++) if (len[k] == 0) emptyIdx[e++] = k;
        }
    }

    private final short[] charIdx = new short[0x10000]; // native-script (or private-use pseudo-letter) char -> channel alphabet index (+1), 0 = unknown
    private Chan[] chans;                           // [idx * 3 + pos]  pos: 0 initial, 1 medial, 2 final
    private String[] words;      // as stored (may contain private-use pseudo-letters, see the model builder)
    private String[] shown;      // words as displayed (pseudo-letters replaced by the real letter)
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
    private long[] triKeys = new long[0];    // ((w1 + 1) << 32) | (w2 + 1), sorted; w = -1 is the sentence start
    private int[][] triNext = new int[0][];
    private byte[][] triQ = new byte[0][];
    /** typed symbols the model understands: a-z, digits (Arabizi 2 3 5 6 7 8 9) and the apostrophe, see {@link #slot} */
    private static final int SLOTS = 37;
    private int[][] firstLetterBuckets = new int[SLOTS][];
    private int[][] bucketLcp = new int[SLOTS][];   // common prefix (in channel keys) with the previous word of the bucket, so scan() can share DP rows

    private RomanTransliterator() {}

    /** Attested Roman spellings for the offline swipe decoder. The decoder ranks paths first;
     *  suggest() still chooses the native-script output using language context. */
    public java.util.Map<String, Integer> swipeSpellings() {
        final java.util.HashMap<String, Integer> spellings = new java.util.HashMap<>();
        for (int i = 0; i < variants.length; i++) {
            for (int j = 0; j < variants[i].length; j++) {
                final String spelling = variants[i][j];
                if (spelling.length() < 2 || spelling.length() > 24) continue;
                final int frequency = Math.max(1, (int) (160 + 8 * uniLn[i] + 32 * variantP[i][j]));
                spellings.merge(spelling, frequency, Math::max);
            }
        }
        return spellings;
    }

    public static RomanTransliterator load(InputStream raw) throws IOException {
        final DataInputStream in = new DataInputStream(new BufferedInputStream(raw, 1 << 16));
        final byte[] magic = new byte[4];
        in.readFully(magic);
        if (!new String(magic, "US-ASCII").equals("UTRL")) throw new IOException("bad model file");
        final int version = in.readInt();
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
            t.charIdx[c] = (short) (int) idx;
            tmp.put(idx * 3 + pos, new Chan(ch, p));
        }
        t.chans = new Chan[(nChars + 1) * 3];
        for (HashMap.Entry<Integer, Chan> e : tmp.entrySet()) t.chans[e.getKey()] = e.getValue();

        final HashMap<Character, Character> pseudo = new HashMap<>();
        if (version >= 2) t.channelWeight = 1.2;       // tuned on the v2 (scripture) models; v1 files keep the Urdu setting
        if (version >= 2) {
            final int nm = in.readUnsignedByte();
            for (int i = 0; i < nm; i++) { final char a = (char) in.readUnsignedShort(); pseudo.put(a, (char) in.readUnsignedShort()); }
        }
        final int nWords = in.readInt();
        t.words = new String[nWords];
        t.shown = new String[nWords];
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
            if (pseudo.isEmpty()) t.shown[i] = t.words[i];
            else { final char[] d = w.clone(); for (int j = 0; j < d.length; j++) { final Character x = pseudo.get(d[j]); if (x != null) d[j] = x; } t.shown[i] = new String(d); }
            int dp = 0;
            for (char c : w) if (c != ' ') dp++;
            final int[] ix = new int[dp];
            final byte[] pos = new byte[dp];
            int k = 0;
            for (int j = 0; j < len; j++) {
                if (w[j] == ' ') continue;
                ix[k] = t.charIdx[w[j]];
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
            t.wordId.put(t.shown[i], i);
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
        if (version >= 2) {
            final int nt = in.readInt();
            t.triKeys = new long[nt]; t.triNext = new int[nt][]; t.triQ = new byte[nt][];
            for (int k = 0; k < nt; k++) {
                t.triKeys[k] = in.readLong();
                final int n = in.readInt();
                final int[] nx = new int[n]; final byte[] q = new byte[n];
                int prev = 0;
                for (int i = 0; i < n; i++) {
                    int d = 0, shift = 0, b;
                    do { b = in.readUnsignedByte(); d |= (b & 127) << shift; shift += 7; } while ((b & 128) != 0);
                    prev += d; nx[i] = prev; q[i] = in.readByte();
                }
                t.triNext[k] = nx; t.triQ[k] = q;
            }
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

    private int chanKey(int w, int i) { return wordCharIdx[w][i] * 3 + wordPos[w][i]; }

    private int lcpKeys(int a, int b) {
        final int n = Math.min(wordCharIdx[a].length, wordCharIdx[b].length);
        int i = 0;
        while (i < n && chanKey(a, i) == chanKey(b, i)) i++;
        return i;
    }

    private void buildBuckets() {
        final ArrayList<ArrayList<Integer>> lists = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) lists.add(new ArrayList<>());
        final Integer[] order = new Integer[words.length];
        for (int w = 0; w < order.length; w++) order[w] = w;
        Arrays.sort(order, (a, b) -> {
            final int l = lcpKeys(a, b);
            final int na = wordCharIdx[a].length, nb = wordCharIdx[b].length;
            if (l == na || l == nb) return Integer.compare(na, nb);
            return Integer.compare(chanKey(a, l), chanKey(b, l));
        });
        for (final int w : order) {
            final int n = wordCharIdx[w].length;
            if (n == 0 || n > MAXN) continue;
            final boolean[] seen = new boolean[SLOTS];
            firstLetters(wordCharIdx[w], wordPos[w], 0, seen, 0);
            for (String v : variants[w]) { final int l = slot(v.charAt(0)); if (l >= 0) seen[l] = true; }
            for (int l = 0; l < SLOTS; l++) if (seen[l]) lists.get(l).add(w);
        }
        for (int l = 0; l < SLOTS; l++) {
            final ArrayList<Integer> li = lists.get(l);
            firstLetterBuckets[l] = new int[li.size()];
            bucketLcp[l] = new int[li.size()];
            for (int i = 0; i < li.size(); i++) {
                firstLetterBuckets[l][i] = li.get(i);
                bucketLcp[l][i] = i == 0 ? 0 : lcpKeys(li.get(i - 1), li.get(i));
            }
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

    private int trigramIndex(int w2, int w1) {
        if (triKeys.length == 0) return -1;
        return Arrays.binarySearch(triKeys, (((long) (w2 + 1)) << 32) | (w1 + 1));
    }

    /** previous words as the language model sees them: p1 = last word, p2 = the one before; -1 = sentence start, -2 = unknown */
    private static final class Ctx {
        final int p1, p2, bg, tri;
        Ctx(int p1, int p2, int bg, int tri) { this.p1 = p1; this.p2 = p2; this.bg = bg; this.tri = tri; }
    }

    private Ctx context(String prev, boolean atStart) {
        int p1 = -2, p2 = -2;
        if (atStart) p1 = -1;
        else if (prev != null) {
            final String[] tok = prev.replaceAll("[\u200e\u200f\u061c]", "").trim().split(" +");   // bidi marks stick to the first word of a paragraph      // a phrase entry ("إن شاء الله") or several words: only the last two count
            Integer a = tok.length > 0 ? wordId.get(tok[tok.length - 1]) : null;
            if (a != null) {
                p1 = a;
                final Integer b = tok.length > 1 ? wordId.get(tok[tok.length - 2]) : null;
                if (b != null) p2 = b;
            }
        }
        return extend(p1, p2);
    }

    private Ctx extend(int p1, int p2) {
        return new Ctx(p1, p2, p1 == -2 ? -1 : bigramIndex(p1), p1 == -2 || p2 == -2 ? -1 : trigramIndex(p2, p1));
    }

    /** ln P(w | previous words): interpolated unigram + bigram, and a trigram on top where the two previous words are in the table */
    private double lmLn(int w, Ctx c) {
        final double uni = Math.exp(uniLn[w]);
        double base = uni;
        if (c.bg >= 0) {
            final int j = Arrays.binarySearch(bigramNext[c.bg], w);
            base = j < 0 ? (1 - bigramWeight) * uni : bigramWeight * Math.exp(-(bigramQ[c.bg][j] & 0xff) / 12.0) + (1 - bigramWeight) * uni;
        }
        if (c.tri >= 0) {
            final int j = Arrays.binarySearch(triNext[c.tri], w);
            final double pt = j < 0 ? 0 : Math.exp(-(triQ[c.tri][j] & 0xff) / 12.0);
            return Math.log(triWeight * pt + (1 - triWeight) * base);
        }
        return Math.log(base);
    }

    /** one scored way to read r[start, end) as a single word */
    private static final class Seg {
        final int w, end; final double lnChan, score; final boolean comp;
        Seg(int w, int end, double lnChan, double score, boolean comp) { this.w = w; this.end = end; this.lnChan = lnChan; this.score = score; this.comp = comp; }
    }

    private static int slotOf(char[] r, int i) { return slot(r[i]); }

    /**
     * Runs the channel model for every dictionary word that can start r[start..] and keeps, for each end position, the best [keep] words
     * (ranked by channelWeight * ln channel + the language model in [ctx]).  [fullOnly] restricts to words that account for r[start, end) exactly.
     * Only ends with at least minSeg typed symbols are reported; the end == m case may also be a completion (a word that is only started).
     */
    private ArrayList<ArrayList<Seg>> scan(char[] r, int[] rSlot, int start, int m, int minSeg, int keep, Ctx ctx, String exactTyped, boolean allowCompletion) {
        final int mm = m - start;
        final ArrayList<ArrayList<Seg>> best = new ArrayList<>(m + 1);
        for (int e = 0; e <= m; e++) best.add(null);
        final double[][] A = new double[MAXN + 1][mm + 1];
        final int[] bucket = firstLetterBuckets[rSlot[start]], lcps = bucketLcp[rSlot[start]];
        for (int i = 0; i <= MAXN; i++) A[i][0] = 0.0;
        A[0][0] = 1.0;
        int computed = 0, deadLen = Integer.MAX_VALUE;   // rows A[0..computed] hold the previous word's prefix; every prefix of length >= deadLen has no live alignment
        for (int bi = 0; bi < bucket.length; bi++) {
            final int w = bucket[bi];
            final int lcp = lcps[bi];
            if (deadLen <= lcp) continue;                 // shares a dead prefix with an earlier word
            final int n = wordCharIdx[w].length;
            final int[] ix = wordCharIdx[w];
            deadLen = Integer.MAX_VALUE;
            for (int i = Math.min(computed, lcp); i < n; i++) {
                final double[] row = A[i], nxt = A[i + 1];
                computed = i;
                boolean live = false;
                for (int j = 0; j <= mm; j++) if (row[j] != 0.0) { live = true; break; }
                if (!live) { deadLen = i; break; }
                final Chan c = ix[i] == 0 ? null : chans[ix[i] * 3 + wordPos[w][i]];
                if (c == null) { deadLen = i + 1; break; }
                Arrays.fill(nxt, 0.0);
                for (int j = 0; j <= mm; j++) {
                    final double a = row[j];
                    if (a == 0.0) continue;
                    final int[] ks = j < mm ? c.cand[rSlot[start + j]] : c.emptyIdx;
                    for (int kk = 0; kk < ks.length; kk++) {
                        final int k = ks[kk];
                        final int l = c.len[k];
                        if (j + l > mm) continue;
                        final char[] ch = c.chunks[k];
                        int q = 0;
                        while (q < l && ch[q] == r[start + j + q]) q++;
                        if (q == l) nxt[j + l] += a * c.p[k];
                    }
                }
                computed = i + 1;
            }
            if (deadLen != Integer.MAX_VALUE) continue;
            for (int e = minSeg; e <= mm; e++) {
                double chan = A[n][e];
                boolean comp = false;
                if (e == mm && allowCompletion && mm >= 2) {
                    for (int i = 1; i < n; i++) {
                        final double v = A[i][mm] * Math.exp(-completionPenalty * (n - i));
                        if (v > chan) { chan = v; comp = true; }
                    }
                }
                if (start == 0 && e == mm && exactTyped != null) {
                    final String[] vs = variants[w];
                    for (int v = 0; v < vs.length; v++) {
                        if (vs[v].equals(exactTyped)) { chan += observedBonus * variantP[w][v]; comp = false; break; }
                    }
                }
                if (chan <= 0) continue;
                final double lnChan = Math.log(chan);
                if (lnChan < (start == 0 && e == mm ? minLogChannel : Math.max(minLogChannel, segMinLogChannel))) continue;
                if (comp && uniLn[w] < minCompletionUni) continue;
                final double score = lmLn(w, ctx) + channelWeight * lnChan;
                ArrayList<Seg> list = best.get(start + e);
                if (list == null) { list = new ArrayList<>(keep + 1); best.set(start + e, list); }
                if (list.size() < keep || score > list.get(list.size() - 1).score) {
                    int pos = list.size();
                    list.add(new Seg(w, start + e, lnChan, score, comp));
                    while (pos > 0 && list.get(pos - 1).score < score) { Collections.swap(list, pos, pos - 1); pos--; }
                    if (list.size() > keep) list.remove(list.size() - 1);
                }
            }
        }
        return best;
    }

    private static boolean isProclitic(String s) {
        switch (s) {   // one-letter prefixes and article forms are written attached to the next word
            case "و": case "ف": case "ب": case "ل": case "ك": case "ال": case "وال": case "فال": case "بال": case "لل": case "ولل": case "كال": case "وب": case "ول": case "وك":
                return true;
            default: return false;
        }
    }

    private static final class Hyp {
        final double score; final String text; final int p1, p2, n; final boolean comp;
        Hyp(double score, String text, int p1, int p2, int n, boolean comp) { this.score = score; this.text = text; this.p1 = p1; this.p2 = p2; this.n = n; this.comp = comp; }
    }

    /**
     * @param roman  Roman text typed so far (current word only)
     * @param prev   previous word(s) (native script, the last two are used), or null if at sentence start / unknown
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
        final Ctx ctx = context(prev, atStart);
        final int[] rSlot = new int[m];
        for (int j = 0; j < m; j++) rSlot[j] = slot(r[j]);
        final int cap = Math.max(max, 1);
        // --- single words (the whole typed text is one word, or the beginning of one)
        final ArrayList<ArrayList<Seg>> first = scan(r, rSlot, 0, m, 1, Math.max(cap * 2, 12), ctx, sb.toString(), true);
        final ArrayList<Seg> singles = first.get(m) == null ? new ArrayList<Seg>() : first.get(m);
        final ArrayList<double[]> scored = new ArrayList<>();   // [score]
        final ArrayList<String> texts = new ArrayList<>();
        final ArrayList<Boolean> comps = new ArrayList<>();
        for (Seg s : singles) { scored.add(new double[]{s.score}); texts.add(shown[s.w]); comps.add(s.comp); }
        final int singleCount = scored.size();
        // --- glued words: "innamal'amal" = إنما الأعمال, "ma'al'usri" = مع العسر (each piece a dictionary word, scored with the bigram / trigram model)
        if (m >= segMinTyped && maxSegments > 1) {
            @SuppressWarnings("unchecked") final ArrayList<Hyp>[] hyps = new ArrayList[m + 1];
            hyps[0] = new ArrayList<>();
            hyps[0].add(new Hyp(0, "", ctx.p1, ctx.p2, 0, false));
            for (int pos = 0; pos < m; pos++) {
                final ArrayList<Hyp> hs = hyps[pos];
                if (hs == null || hs.isEmpty()) continue;
                if (pos > 0 && m - pos < minSegChars) continue;
                final Hyp lead = hs.get(0);
                final ArrayList<ArrayList<Seg>> sc = pos == 0 ? first : scan(r, rSlot, pos, m, minSegChars, 5, extend(lead.p1, lead.p2), null, true);
                for (int e = pos + minSegChars; e <= m; e++) {
                    final ArrayList<Seg> segs = sc.get(e);
                    if (segs == null) continue;
                    for (Hyp h : hs) {
                        if (h.n >= maxSegments) continue;
                        final Ctx hc = pos == 0 ? ctx : extend(h.p1, h.p2);
                        for (Seg s : segs) {
                            if (e < m && s.comp) continue;
                            if (e == m && h.n == 0) continue;          // single word: handled above
                            final double sco = h.score + lmLn(s.w, hc) + channelWeight * s.lnChan - (h.n > 0 || e < m ? segPenalty : 0);
                            final String piece = shown[s.w];
                            final String txt = h.text.isEmpty() ? piece : (isProclitic(h.text.substring(h.text.lastIndexOf(' ') + 1)) ? h.text + piece : h.text + " " + piece);
                            if (hyps[e] == null) hyps[e] = new ArrayList<>();
                            final ArrayList<Hyp> tgt = hyps[e];
                            // beam of 3 per end position
                            if (tgt.size() < 3 || sco > tgt.get(tgt.size() - 1).score) {
                                tgt.add(new Hyp(sco, txt, s.w, h.p1, h.n + 1, s.comp));
                                Collections.sort(tgt, (x, y) -> Double.compare(y.score, x.score));
                                if (tgt.size() > 3) tgt.remove(tgt.size() - 1);
                            }
                        }
                    }
                }
            }
            if (hyps[m] != null) for (Hyp h : hyps[m]) if (h.n >= 2 || h.text.indexOf(' ') < 0) { scored.add(new double[]{h.score}); texts.add(h.text); comps.add(h.comp); }
        }
        applyPersonal(sb.toString(), prev, scored, texts, comps);
        // --- merge: best score per distinct text, best first
        final int cnt = scored.size();
        final Integer[] order = new Integer[cnt];
        for (int i = 0; i < cnt; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(scored.get(b)[0], scored.get(a)[0]));
        final java.util.HashSet<String> seen = new java.util.HashSet<>();
        double top = Double.NEGATIVE_INFINITY;
        // membership quotas: segmented readings ("ثم ما") and completions ("ثمن…") may not crowd the plain whole-word readings out of the strip
        final int segCap = Math.max(1, (cap * 2 + 4) / 5), compCap = Math.max(1, (cap * 2 + 4) / 5);
        int nSeg = 0, nComp = 0;
        final ArrayList<Candidate> skipped = new ArrayList<>();
        for (int oi = 0; oi < cnt; oi++) {
            final int i = order[oi];
            final double sc = scored.get(i)[0];
            if (top == Double.NEGATIVE_INFINITY) top = sc;
            if (sc < top - maxScoreGap) break;
            if (!seen.add(texts.get(i))) continue;
            final Candidate cd = new Candidate(texts.get(i), sc, comps.get(i));
            final boolean seg = i >= singleCount;
            if (out.size() < cap && (seg ? nSeg < segCap : cd.completion ? nComp < compCap : true)) {
                out.add(cd);
                if (seg) nSeg++; else if (cd.completion) nComp++;
            } else skipped.add(cd);
        }
        for (int i = 0; i < skipped.size() && out.size() < cap; i++) out.add(skipped.get(i));
        Collections.sort(out, (x, y) -> Double.compare(y.score, x.score));
        // the first candidate is what a space commits: never a mere completion ("جزاك الله خيرا" for the typed "jazakallahu") when a full reading is about as likely
        if (!out.isEmpty() && out.get(0).completion) {
            for (int i = 1; i < out.size(); i++) {
                if (!out.get(i).completion && out.get(i).score >= out.get(0).score - completionCommitGap) { out.add(0, out.remove(i)); break; }
            }
        }
        return out;
    }

    /** what the owner's typing taught the keyboard; null = nothing learned / learning off. Set from another thread. */
    public volatile PersonalModel personal;

    private static String lastWord(String prev) {
        if (prev == null) return null;
        final String t = prev.replaceAll("[\u200e\u200f\u061c]", "").trim();
        final int sp = t.lastIndexOf(' ');
        final String w = sp < 0 ? t : t.substring(sp + 1);
        return w.isEmpty() ? null : w;
    }

    /**
     * Adds the owner's habits to the candidate scores: a bonus per candidate, and words used for exactly this spelling before
     * (e.g. a name kept in Roman letters) that the decoder did not offer. Runs on the finished candidate list, a few hash reads.
     */
    private void applyPersonal(String roman, String prev, ArrayList<double[]> scored, ArrayList<String> texts, ArrayList<Boolean> comps) {
        final PersonalModel pm = personal;
        if (pm == null || pm.isEmpty()) return;
        final String pw = lastWord(prev);
        final String rn = PersonalModel.normalizeRoman(roman);
        double top = -12 + 2.5;
        for (int i = 0; i < scored.size(); i++) {
            top = Math.max(top, scored.get(i)[0]);
            scored.get(i)[0] += pm.bonus(pw, texts.get(i), rn);
        }
        for (String w : pm.injected(rn, 3)) {
            if (texts.contains(w)) continue;
            scored.add(new double[]{top - 2.5 + pm.bonus(pw, w, rn)});
            texts.add(w);
            comps.add(false);
        }
    }

    /** Most likely next words after [prev] (or at sentence start): trigram continuations first, then bigram ones. */
    public List<String> nextWords(String prev, boolean atStart, int max) {
        final ArrayList<String> out = new ArrayList<>();
        final Ctx c = context(prev, atStart);
        final PersonalModel pm = personal;
        if (pm != null && !atStart) {
            final String pw = lastWord(prev);
            for (String w : pm.nextWords(pw, 3)) if (out.size() < max) out.add(w);
        }
        if (c.p1 == -2) return out;
        if (c.tri >= 0) topNext(triNext[c.tri], triQ[c.tri], max, out);
        if (c.bg >= 0 && out.size() < max) topNext(bigramNext[c.bg], bigramQ[c.bg], max, out);
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    private void topNext(int[] nx, byte[] q, int max, ArrayList<String> out) {
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
        for (int i = 0; i < best.length && bq[i] != Integer.MAX_VALUE && out.size() < max; i++) if (!out.contains(shown[best[i]])) out.add(shown[best[i]]);
    }
}
