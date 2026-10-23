// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gesture;

import android.content.Context;

import com.android.inputmethod.latin.BinaryDictionary;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.latin.common.InputPointers;
import helium314.keyboard.latin.dictionary.Dictionary;
import helium314.keyboard.latin.makedict.WordProperty;
import helium314.keyboard.latin.utils.Log;

/** Small offline fallback for the optional native gesture decoder. Runs on the suggestion worker. */
public final class SwipeDecoder {
    private static final int SAMPLES = 16;
    private static final String TAG = "SwipeDecoder";
    private static Map<String, Integer> englishWords;
    private static Index cachedIndex;
    private static long cachedLayoutSignature;
    private static Map<String, Integer> cachedWords;

    public static final class Match {
        public final String word;
        public final float score;
        Match(String word, float score) { this.word = word; this.score = score; }
    }

    private static final class Entry {
        final String word;
        final float[] path;
        final int last;
        final int frequency;
        Entry(String word, float[] path, int frequency) {
            this.word = word;
            this.path = path;
            this.last = word.charAt(word.length() - 1) - 'a';
            this.frequency = frequency;
        }
    }

    private static final class Index {
        final ArrayList<Entry>[] byFirst;
        final float[] centers;
        final float width;
        final float height;
        @SuppressWarnings("unchecked")
        Index(Keyboard keyboard, Map<String, Integer> words) {
            byFirst = new ArrayList[26];
            for (int i = 0; i < 26; i++) byFirst[i] = new ArrayList<>();
            centers = new float[52];
            for (Key key : keyboard.getSortedKeys()) {
                int code = Character.toLowerCase(key.getCode());
                if (code >= 'a' && code <= 'z') {
                    int index = (code - 'a') * 2;
                    centers[index] = key.getX() + key.getWidth() / 2f;
                    centers[index + 1] = key.getY() + key.getHeight() / 2f;
                }
            }
            float maxX = 1, maxY = 1;
            for (Key key : keyboard.getSortedKeys()) {
                maxX = Math.max(maxX, key.getX() + key.getWidth());
                maxY = Math.max(maxY, key.getY() + key.getHeight());
            }
            width = maxX;
            height = maxY;
            for (Map.Entry<String, Integer> item : words.entrySet()) {
                String word = item.getKey().toLowerCase(Locale.ROOT);
                if (word.length() < 2 || word.length() > 24 || !word.matches("[a-z]+")) continue;
                float[] raw = new float[word.length() * 2];
                int count = 0;
                boolean valid = true;
                for (int i = 0; i < word.length(); i++) {
                    int keyIndex = (word.charAt(i) - 'a') * 2;
                    float x = centers[keyIndex], y = centers[keyIndex + 1];
                    if (x == 0 && y == 0) { valid = false; break; }
                    if (count == 0 || x != raw[(count - 1) * 2] || y != raw[(count - 1) * 2 + 1]) {
                        raw[count * 2] = x;
                        raw[count * 2 + 1] = y;
                        count++;
                    }
                }
                if (valid && count > 1) {
                    byFirst[word.charAt(0) - 'a'].add(new Entry(word,
                            resample(raw, count, width, height), item.getValue()));
                }
            }
        }
    }

    private SwipeDecoder() {}

    private static volatile boolean englishLoading;

    /** Drops the cached word lists and layout index (swipe typing switched off). */
    public static synchronized void releaseWords() {
        englishWords = null;
        cachedIndex = null;
        cachedWords = null;
        cachedLayoutSignature = 0;
    }

    public static boolean englishWordsLoaded() {
        return englishWords != null;
    }

    /** Reads the English word list on a background thread; the suggestion thread must never do this mid-swipe. */
    public static void preloadEnglishWords(final Context context) {
        if (englishWords != null || englishLoading) return;
        englishLoading = true;
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try { englishWords(app); } finally { englishLoading = false; }
        }, "SwipeEnglishWords").start();
    }

    /** Reads the already bundled English dictionary; no network or second word-list asset. */
    public static synchronized Map<String, Integer> englishWords(Context context) {
        if (englishWords != null) return englishWords;
        HashMap<String, Integer> result = new HashMap<>();
        File file = new File(context.getCacheDir(), "swipe_en-US.dict");
        try {
            if (!file.isFile() || file.length() == 0) {
                try (InputStream input = context.getAssets().open("dicts/main_en-US.dict");
                     FileOutputStream output = new FileOutputStream(file)) {
                    byte[] buffer = new byte[32768];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
            }
            BinaryDictionary dictionary = new BinaryDictionary(file.getAbsolutePath(), 0,
                    file.length(), false, Locale.US, Dictionary.TYPE_MAIN, false);
            try {
                int token = 0;
                do {
                    BinaryDictionary.GetNextWordPropertyResult next = dictionary.getNextWordProperty(token);
                    WordProperty property = next.mWordProperty;
                    if (property == null) break;
                    if (!property.mIsNotAWord && !property.mIsPossiblyOffensive
                            && property.getProbability() > 0) {
                        result.put(property.mWord.toLowerCase(Locale.ROOT), property.getProbability());
                    }
                    token = next.mNextToken;
                } while (token != 0);
            } finally { dictionary.close(); }
        } catch (Exception | Error e) {
            Log.e(TAG, "Could not load swipe dictionary", e);
        }
        englishWords = result;
        return englishWords;
    }

    public static synchronized List<Match> match(Keyboard keyboard, InputPointers pointers,
            Map<String, Integer> words, int limit) {
        if (pointers.getPointerSize() < 2 || words.isEmpty()) return List.of();
        long layoutSignature = layoutSignature(keyboard);
        if (cachedIndex == null || cachedLayoutSignature != layoutSignature || cachedWords != words) {
            cachedIndex = new Index(keyboard, words);
            cachedLayoutSignature = layoutSignature;
            cachedWords = words;
        }
        Index index = cachedIndex;
        int size = pointers.getPointerSize();
        float[] stroke = new float[size * 2];
        for (int i = 0; i < size; i++) {
            stroke[i * 2] = pointers.getXCoordinates()[i];
            stroke[i * 2 + 1] = pointers.getYCoordinates()[i];
        }
        float[] path = resample(stroke, size, index.width, index.height);
        int first = closest(index.centers, stroke[0], stroke[1]);
        int last = closest(index.centers, stroke[(size - 1) * 2], stroke[(size - 1) * 2 + 1]);
        if (first < 0) return List.of();
        ArrayList<Match> matches = new ArrayList<>();
        for (Entry entry : index.byFirst[first]) {
            float distance = 0;
            for (int p = 0; p < path.length; p++) {
                float difference = path[p] - entry.path[p];
                distance += difference * difference;
            }
            // The end point matters, but keep a relaxed path for imprecise swipes.
            if (entry.last != last) distance += 0.05f;
            float score = distance / SAMPLES - 0.006f * (float)Math.log1p(entry.frequency);
            matches.add(new Match(entry.word, score));
        }
        matches.sort(Comparator.comparingDouble(match -> match.score));
        if (matches.size() > limit) return new ArrayList<>(matches.subList(0, limit));
        return matches;
    }

    private static long layoutSignature(Keyboard keyboard) {
        long hash = 1469598103934665603L;
        for (Key key : keyboard.getSortedKeys()) {
            int code = Character.toLowerCase(key.getCode());
            if (code < 'a' || code > 'z') continue;
            hash = (hash ^ code) * 1099511628211L;
            hash = (hash ^ key.getX()) * 1099511628211L;
            hash = (hash ^ key.getY()) * 1099511628211L;
            hash = (hash ^ key.getWidth()) * 1099511628211L;
            hash = (hash ^ key.getHeight()) * 1099511628211L;
        }
        return hash;
    }

    private static int closest(float[] centers, float x, float y) {
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < 26; i++) {
            float cx = centers[i * 2], cy = centers[i * 2 + 1];
            if (cx == 0 && cy == 0) continue;
            float distance = (x - cx) * (x - cx) + (y - cy) * (y - cy);
            if (distance < bestDistance) { bestDistance = distance; best = i; }
        }
        return best;
    }

    private static float[] resample(float[] points, int count, float width, float height) {
        float[] distances = new float[count];
        for (int i = 1; i < count; i++) {
            float dx = (points[i * 2] - points[(i - 1) * 2]) / width;
            float dy = (points[i * 2 + 1] - points[(i - 1) * 2 + 1]) / height;
            distances[i] = distances[i - 1] + (float)Math.hypot(dx, dy);
        }
        float[] output = new float[SAMPLES * 2];
        int segment = 1;
        for (int sample = 0; sample < SAMPLES; sample++) {
            float target = distances[count - 1] * sample / (SAMPLES - 1f);
            while (segment < count - 1 && distances[segment] < target) segment++;
            int previous = Math.max(0, segment - 1);
            float span = distances[segment] - distances[previous];
            float fraction = span <= 0 ? 0 : (target - distances[previous]) / span;
            output[sample * 2] = (points[previous * 2]
                    + fraction * (points[segment * 2] - points[previous * 2])) / width;
            output[sample * 2 + 1] = (points[previous * 2 + 1]
                    + fraction * (points[segment * 2 + 1] - points[previous * 2 + 1])) / height;
        }
        return output;
    }
}
