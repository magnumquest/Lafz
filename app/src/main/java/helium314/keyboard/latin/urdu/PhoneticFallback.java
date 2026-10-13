// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.urdu;

import java.util.Locale;

/**
 * Instant rule-based Roman to Urdu / Arabic placeholder. It is only shown while typing, until the dictionary
 * decoder ({@link RomanTransliterator}) has produced its candidate. It is deliberately simple: greedy
 * longest-match on consonant digraphs and vowel pairs, short vowels written only word-initially or word-finally
 * (plus a few long-vowel cases), and non-letters passed through unchanged.
 */
public final class PhoneticFallback {

    private PhoneticFallback() { }

    public static String toUrdu(String roman) {
        return convert(roman, false);
    }

    public static String toArabic(String roman) {
        return convert(roman, true);
    }

    private static String convert(String roman, boolean arabic) {
        if (roman == null || roman.isEmpty())
            return "";
        final String s = roman.toLowerCase(Locale.ROOT);
        final int n = s.length();
        final StringBuilder sb = new StringBuilder(n * 2);
        int i = 0;
        while (i < n) {
            if (!isWordChar(s, i, arabic)) {
                sb.append(s.charAt(i));
                i++;
                continue;
            }
            int j = i + 1;
            while (j < n && isWordChar(s, j, arabic))
                j++;
            convertWord(s, i, j, arabic, sb);
            i = j;
        }
        return sb.toString();
    }

    /**
     * Latin letters, plus (Arabic only) an apostrophe between two letters, which becomes ع, and an Arabizi digit
     * next to a letter (ma3a, 3ilm), which is a letter of the word. A lone digit is not a word character.
     */
    private static boolean isWordChar(String s, int k, boolean arabic) {
        final char c = s.charAt(k);
        if (isLatinLetter(c))
            return true;
        if (arabic && isArabiziDigit(c))
            return (k > 0 && isLatinLetter(s.charAt(k - 1))) || (k + 1 < s.length() && isLatinLetter(s.charAt(k + 1)));
        return arabic && c == '\'' && k > 0 && k + 1 < s.length()
                && isLatinLetter(s.charAt(k - 1)) && isLatinLetter(s.charAt(k + 1));
    }

    /** Arabizi (chat) digits for letters that have no Latin equivalent. */
    private static boolean isArabiziDigit(char c) {
        return c == '2' || c == '3' || c == '5' || c == '6' || c == '7' || c == '8' || c == '9';
    }

    private static char arabiziLetter(char c) {
        switch (c) {
            case '2': return 'ء';
            case '3': return 'ع';
            case '5': return 'خ';
            case '6': return 'ط';
            case '7': return 'ح';
            case '8': return 'غ';
            default: return 'ق'; // 9
        }
    }

    private static void convertWord(String s, int from, int to, boolean arabic, StringBuilder sb) {
        int vowelsSeen = 0;
        char lastCons = 0; // last single consonant emitted with no vowel in between (doubled letters collapse)
        int i = from;
        while (i < to) {
            final char c = s.charAt(i);
            if (isVowel(c)) {
                final int g = (i + 1 < to && isVowelPair(c, s.charAt(i + 1))) ? 2 : 1;
                emitVowel(s, from, to, i, g, vowelsSeen, arabic, sb);
                vowelsSeen++;
                lastCons = 0;
                i += g;
                continue;
            }
            if (c == '\'') { // Arabic ayn (only reached between two letters)
                sb.append('ع');
                lastCons = 0;
                i++;
                continue;
            }
            if (arabic && isArabiziDigit(c)) { // Arabizi digit used as a letter
                sb.append(arabiziLetter(c));
                lastCons = 0;
                i++;
                continue;
            }
            // Urdu: a glide y between two vowels is already carried by the vowel (kiya -> کیا)
            if (c == 'y' && !arabic && i > from && isVowel(s.charAt(i - 1))
                    && i + 1 < to && isVowel(s.charAt(i + 1))) {
                i++;
                continue;
            }
            final String two = i + 1 < to ? digraph(c, s.charAt(i + 1), arabic) : null;
            if (two != null) {
                sb.append(two);
                lastCons = 0;
                i += 2;
                continue;
            }
            if (c != lastCons) {
                final String one = single(c, arabic);
                if (one != null)
                    sb.append(one);
                else
                    sb.append(c);
                lastCons = c;
            }
            i++;
        }
    }

    /**
     * Emits the script for the vowel (or vowel pair, g == 2) starting at index i.
     * Short vowels are written only at the start and end of a word (Urdu also writes final a/i/u/o/e and
     * an alif for a mid-word a that closes a word like -ab/-am); elsewhere they are omitted, as in
     * unvocalised Urdu and Arabic script.
     */
    private static void emitVowel(String s, int from, int to, int i, int g, int vowelsSeen,
                                  boolean arabic, StringBuilder sb) {
        final char c = s.charAt(i);
        final boolean initial = i == from;
        final boolean end = i + g == to;
        if (g == 2) {
            final char d = s.charAt(i + 1);
            if (c == d) {
                if (c == 'a') {
                    sb.append(arabic || !initial ? "ا" : "آ");
                } else if (c == 'e' || c == 'i') {
                    if (arabic)
                        sb.append('ي');
                    else
                        sb.append(initial ? "ای" : "ی");
                } else { // oo, uu
                    if (arabic)
                        sb.append('و');
                    else
                        sb.append(initial ? "او" : "و");
                }
            } else if (d == 'i' || d == 'y') { // ai, ay, ei, ey
                if (arabic)
                    sb.append('ي');
                else if (initial)
                    sb.append("ای");
                else if (end)
                    sb.append('ے');
                else
                    sb.append('ی');
            } else { // au, ou
                if (arabic)
                    sb.append('و');
                else
                    sb.append(initial ? "او" : "و");
            }
            return;
        }
        if (arabic) {
            if (initial)
                sb.append('ا');
            return;
        }
        switch (c) {
            case 'a':
                if (initial || end
                        || (vowelsSeen > 0 && i + 2 == to && isLatinConsonant(s.charAt(i + 1))))
                    sb.append('ا');
                break;
            case 'i':
                if (initial)
                    sb.append("ای");
                else if (end)
                    sb.append('ی');
                else if (i + 2 < to && s.charAt(i + 1) == 'y' && isVowel(s.charAt(i + 2)))
                    sb.append('ی');
                break;
            case 'u':
            case 'o':
                if (initial)
                    sb.append("او");
                else if (end)
                    sb.append('و');
                break;
            case 'e':
                if (initial)
                    sb.append("ای");
                else if (end)
                    sb.append('ے');
                break;
            default:
                break;
        }
    }

    /** Two-letter consonant digraphs (all end in h), or null. */
    private static String digraph(char c, char d, boolean arabic) {
        if (d != 'h')
            return null;
        switch (c) {
            case 's': return "ش";
            case 'c': return arabic ? "ش" : "چ";
            case 'k': return "خ";
            case 'g': return "غ";
            case 't': return arabic ? "ث" : "تھ";
            case 'd': return arabic ? "ذ" : "دھ";
            case 'p': return "ف";
            case 'b': return arabic ? null : "بھ";
            case 'j': return arabic ? null : "جھ";
            case 'z': return arabic ? null : "ژ";
            default: return null;
        }
    }

    private static String single(char c, boolean arabic) {
        switch (c) {
            case 'b': return "ب";
            case 'p': return arabic ? "ب" : "پ";
            case 't': return "ت";
            case 'j': return "ج";
            case 'c': return arabic ? "ك" : "ک";
            case 'h': return arabic ? "ه" : "ہ";
            case 'd': return "د";
            case 'r': return "ر";
            case 'z': return "ز";
            case 's': return "س";
            case 'f': return "ف";
            case 'q': return "ق";
            case 'k': return arabic ? "ك" : "ک";
            case 'g': return arabic ? "ج" : "گ";
            case 'l': return "ل";
            case 'm': return "م";
            case 'n': return "ن";
            case 'v': return arabic ? "ف" : "و";
            case 'w': return "و";
            case 'y': return arabic ? "ي" : "ی";
            case 'x': return arabic ? "كس" : "کس";
            default: return null;
        }
    }

    private static boolean isVowelPair(char c, char d) {
        if (c == d)
            return isVowel(c);
        return (c == 'a' && (d == 'i' || d == 'y' || d == 'u'))
                || (c == 'e' && (d == 'i' || d == 'y'))
                || (c == 'o' && d == 'u');
    }

    private static boolean isLatinLetter(char c) {
        return c >= 'a' && c <= 'z';
    }

    private static boolean isVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }

    private static boolean isLatinConsonant(char c) {
        return isLatinLetter(c) && !isVowel(c);
    }
}
