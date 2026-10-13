import helium314.keyboard.latin.urdu.RomanTransliterator;
import java.io.*; import java.nio.file.*; import java.util.*;
public class Eval {
  public static void main(String[] a) throws Exception {
    RomanTransliterator t = RomanTransliterator.load(new FileInputStream(a[0]));
    for (int i = 2; i + 1 < a.length; i += 2) {
      double v = Double.parseDouble(a[i + 1]);
      switch (a[i]) { case "cw": t.channelWeight = v; break; case "ob": t.observedBonus = v; break;
        case "cp": t.completionPenalty = v; break; case "bw": t.bigramWeight = v; break; case "gap": t.maxScoreGap = v; break; case "mlc": t.minLogChannel = v; break; }
    }
    int limit = a.length > 1 ? Integer.parseInt(a[1]) : 2000;
    long tot = 0, oov = 0, h1 = 0, h3 = 0, h5 = 0; long t0 = System.nanoTime(); long calls = 0;
    int ln = 0;
    for (String line : Files.readAllLines(Paths.get("eval.tsv"))) {
      if (ln++ >= limit) break;
      String[] p = line.split("\t"); String[] u = p[0].split(" "), r = p[1].split(" ");
      String prev = null; boolean start = true;
      for (int i = 0; i < u.length; i++) {
        tot++;
        boolean known = t.isKnownWord(u[i]);
        if (!known) oov++;
        List<RomanTransliterator.Candidate> c = t.suggest(r[i], prev, start, 5); calls++;
        for (int k = 0; k < c.size(); k++) if (c.get(k).text.equals(u[i])) { if (k < 1) h1++; if (k < 3) h3++; h5++; break; }
        prev = c.isEmpty() ? null : c.get(0).text; start = false;
      }
    }
    double ms = (System.nanoTime() - t0) / 1e6 / calls;
    System.out.printf("tokens=%d oov=%.1f%% top1=%.1f%% top3=%.1f%% top5=%.1f%%  (in-vocab top1=%.1f%%)  %.2f ms/query%n",
      tot, 100.0*oov/tot, 100.0*h1/tot, 100.0*h3/tot, 100.0*h5/tot, 100.0*h1/(tot-oov), ms);
  }
}
