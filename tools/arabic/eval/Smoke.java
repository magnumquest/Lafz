import helium314.keyboard.latin.urdu.RomanTransliterator;
import java.io.*; import java.nio.file.*; import java.util.*;
/** java Smoke model.bin smoke.tsv [verbose] : top-1 / top-3 / top-6 on a roman<TAB>word list (no context), plus latency */
public class Smoke {
  public static void main(String[] a) throws Exception {
    RomanTransliterator t = RomanTransliterator.load(new FileInputStream(a[0]));
    for (int i = 3; i + 1 < a.length; i += 2) { // optional tunables: cw ob cp gap mlc
      double v = Double.parseDouble(a[i + 1]);
      switch (a[i]) { case "cw": t.channelWeight = v; break; case "ob": t.observedBonus = v; break; case "cp": t.completionPenalty = v; break;
        case "gap": t.maxScoreGap = v; break; case "mlc": t.minLogChannel = v; break; }
    }
    int n = 0, h1 = 0, h3 = 0, h6 = 0, oov = 0; long t0 = System.nanoTime();
    for (String line : Files.readAllLines(Paths.get(a[1]))) {
      if (line.startsWith("#") || !line.contains("\t")) continue;
      String[] p = line.split("\t"); n++;
      if (!t.isKnownWord(p[1])) oov++;
      List<RomanTransliterator.Candidate> c = t.suggest(p[0], null, true, 6);
      int rank = -1; for (int k = 0; k < c.size(); k++) if (c.get(k).text.equals(p[1])) { rank = k; break; }
      if (rank == 0) h1++; if (rank >= 0 && rank < 3) h3++; if (rank >= 0) h6++;
      if (a.length > 2 && rank != 0) System.out.println((rank < 0 ? "MISS " : "rank" + rank + " ") + p[0] + " -> " + p[1] + (t.isKnownWord(p[1]) ? "" : " (not in dictionary)") + "   got " + c);
    }
    for (int rep = 0; rep < 3; rep++) { // timing after JIT warm-up
      t0 = System.nanoTime();
      for (String line : Files.readAllLines(Paths.get(a[1]))) { if (line.startsWith("#") || !line.contains("\t")) continue; t.suggest(line.split("\t")[0], null, true, 6); }
    }
    double ms = (System.nanoTime() - t0) / 1e6 / n;
    System.out.printf("n=%d notInDict=%d top1=%.1f%% top3=%.1f%% top6=%.1f%%  %.1f ms/query%n", n, oov, 100.0*h1/n, 100.0*h3/n, 100.0*h6/n, ms);
  }
}
