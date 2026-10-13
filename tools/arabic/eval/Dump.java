import helium314.keyboard.latin.urdu.RomanTransliterator;
import java.io.*; import java.nio.file.*; import java.util.*;
/** java Dump model.bin inputs.txt : prints every candidate list (used to prove that engine optimisations do not change results) */
public class Dump { public static void main(String[] a) throws Exception {
  RomanTransliterator t = RomanTransliterator.load(new FileInputStream(a[0]));
  long t0 = System.nanoTime(); int n = 0;
  for (String line : Files.readAllLines(Paths.get(a[1]))) {
    if (line.startsWith("#") || line.isEmpty()) continue;
    String w = line.split("\t")[0];
    for (String prev : new String[]{null, "في"}) {
      StringBuilder sb = new StringBuilder(w + " [" + prev + "]:");
      for (RomanTransliterator.Candidate c : t.suggest(w, prev, prev == null, 6)) sb.append(' ').append(c.text).append(c.completion ? "…" : "").append(String.format("%.4f", c.score));
      System.out.println(sb); n++;
    }
  }
  System.err.printf("%d queries %.2f ms/query%n", n, (System.nanoTime() - t0) / 1e6 / n);
}}
