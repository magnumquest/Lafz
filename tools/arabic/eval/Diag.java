import helium314.keyboard.latin.urdu.RomanTransliterator;
import java.io.*; import java.nio.file.*; import java.util.*;
/** java Diag model.bin typed.tsv [v]: per-word diagnosis on phrases where typed tokens == expected words (no gluing):
 *  top1 / in top8 / not in dictionary / typed form given context.  v prints the misses. */
public class Diag { public static void main(String[] a) throws Exception {
  RomanTransliterator t = RomanTransliterator.load(new FileInputStream(a[0]));
  int n = 0, top1 = 0, top8 = 0, top3 = 0, top5 = 0, oov = 0, ctxless1 = 0; List<String> misses = new ArrayList<>();
  for (String line : Files.readAllLines(Paths.get(a[1]))) {
    if (line.startsWith("#") || !line.contains("\t")) continue;
    String[] p = line.split("\t"); String[] ty = p[0].split(" "), ex = p[1].split(" ");
    if (ty.length != ex.length) continue;
    String prev = null;
    for (int i = 0; i < ty.length; i++) {
      List<RomanTransliterator.Candidate> c = t.suggest(ty[i], prev, i == 0, 8); n++;
      int rank = -1; for (int k = 0; k < c.size(); k++) if (c.get(k).text.equals(ex[i])) { rank = k; break; }
      boolean known = t.isKnownWord(ex[i]);
      if (!known) oov++; if (rank == 0) top1++; if (rank >= 0 && rank < 3) top3++; if (rank >= 0 && rank < 5) top5++; if (rank >= 0) top8++;
      { List<RomanTransliterator.Candidate> c0 = t.suggest(ty[i], null, true, 1); if (!c0.isEmpty() && c0.get(0).text.equals(ex[i])) ctxless1++; }
      if (rank != 0) misses.add((known ? (rank < 0 ? "MISS   " : "rank" + rank + "   ") : "OOV    ") + ty[i] + " -> " + ex[i] + "  got " + (c.isEmpty() ? "[]" : c.subList(0, Math.min(3, c.size()))));
      prev = ex[i];            // teacher forcing: judge each word with the correct context
    }
  }
  System.out.printf("words=%d top1=%.1f%% top3=%.1f%% top5=%.1f%% top8=%.1f%% notInDict=%.1f%% (no-context top1 %.1f%%)%n", n, 100.0*top1/n, 100.0*top3/n, 100.0*top5/n, 100.0*top8/n, 100.0*oov/n, 100.0*ctxless1/n);
  if (a.length > 2) { Collections.shuffle(misses, new Random(1)); for (int i = 0; i < Math.min(60, misses.size()); i++) System.out.println(misses.get(i)); }
}}
