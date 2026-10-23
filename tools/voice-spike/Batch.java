import helium314.keyboard.latin.urdu.RomanTransliterator;
import java.io.*; import java.nio.charset.StandardCharsets; import java.util.*;
/** stdin: one Roman sentence per line; stdout: the Urdu sentence (word by word with the previous word as context, like typing) */
public class Batch { public static void main(String[] a) throws Exception {
  RomanTransliterator t = RomanTransliterator.load(new FileInputStream(a[0]));
  BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
  PrintStream out = new PrintStream(System.out, true, "UTF-8");
  String line; long ns = 0, words = 0;
  while ((line = in.readLine()) != null) {
    String prev = null; boolean start = true; StringBuilder sb = new StringBuilder();
    for (String w : line.trim().split("\\s+")) {
      if (w.isEmpty()) continue;
      long t0 = System.nanoTime();
      List<RomanTransliterator.Candidate> c = t.suggest(w, prev, start, 1);
      ns += System.nanoTime() - t0; words++;
      String best = c.isEmpty() ? w : c.get(0).text;
      sb.append(best).append(' ');
      prev = c.isEmpty() ? null : best; start = false;
    }
    out.println(sb.toString().trim());
  }
  System.err.println("decoder ms/word=" + (ns / 1e6 / Math.max(1, words)));
}}
