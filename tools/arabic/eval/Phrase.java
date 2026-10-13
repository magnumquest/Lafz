import helium314.keyboard.latin.urdu.RomanTransliterator;
import java.io.*; import java.nio.file.*; import java.util.*;
/** java Phrase model.bin phrases.tsv [v] : decodes each typed phrase word by word the way the keyboard does (top candidate committed on space,
 *  previous committed text as context) and compares with the expected Arabic. Strict = exact; loose = hamza/alef, ya/alef-maqsura, ta marbuta/ha folded. */
public class Phrase {
  static final Set<String> CLITICS = new HashSet<>(Arrays.asList("و", "ف", "ب", "ل", "ك", "ال", "وال", "فال", "بال", "كال", "لل", "ولل"));
  /** key=value arguments set public tunables of the engine (channelWeight=1.4 bigramWeight=.5 triWeight=.5 segPenalty=3 ...) */
  static void tune(RomanTransliterator t, String[] a, int from) throws Exception {
    for (int i = from; i < a.length; i++) { String[] kv = a[i].split("="); if (kv.length != 2) continue;
      java.lang.reflect.Field f = t.getClass().getField(kv[0]); if (f.getType() == int.class) f.setInt(t, Integer.parseInt(kv[1])); else f.setDouble(t, Double.parseDouble(kv[1])); }
  }
  static String loose(String s) { return s.replaceAll("[أإآٱ]", "ا").replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي').replaceAll("\\s+", " ").trim(); }
  public static void main(String[] a) throws Exception {
    RomanTransliterator t = RomanTransliterator.load(new FileInputStream(a[0]));
    tune(t, a, 2);
    boolean verbose = a.length > 2 && a[2].equals("v"); int n = 0, strict = 0, lenient = 0, words = 0, wordsOk = 0; long t0 = System.nanoTime(); int queries = 0;
    for (String line : Files.readAllLines(Paths.get(a[1]))) {
      if (line.startsWith("#") || !line.contains("\t")) continue;
      String[] p = line.split("\t"); n++;
      StringBuilder out = new StringBuilder(); String prev = null; boolean start = true; boolean attach = false;
      for (String tok : p[0].split(" ")) {
        if (tok.isEmpty()) continue;
        List<RomanTransliterator.Candidate> c = t.suggest(tok, prev, start, 6); queries++;
        String best = c.isEmpty() ? tok : c.get(0).text;
        if (out.length() > 0 && !attach) out.append(' ');
        out.append(best);
        // one-letter prefixes and article forms typed as separate words are written attached to the next word (wa ma -> وما); context stays the word before
        attach = CLITICS.contains(best);
        if (!attach) { prev = best; start = false; }
      }
      String got = out.toString();
      boolean s = got.equals(p[1]), l = loose(got).equals(loose(p[1]));
      if (s) strict++; if (l) lenient++;
      String[] g = loose(got).split(" "), e = loose(p[1]).split(" ");
      // word-level credit: expected words found in order in the output
      words += e.length; Set<String> gs = new HashSet<>(Arrays.asList(g)); for (String w : e) if (gs.contains(w)) wordsOk++;
      if (verbose) System.out.println((l ? "OK   " : "FAIL ") + p[0] + "\n       got " + got + (l ? "" : "\n       exp " + p[1]));
    }
    System.out.printf("phrases=%d strict=%.1f%% loose=%.1f%% wordsFound=%.1f%%  %.1f ms/token%n", n, 100.0*strict/n, 100.0*lenient/n, 100.0*wordsOk/words, (System.nanoTime()-t0)/1e6/queries);
  }
}
