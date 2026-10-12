import helium314.keyboard.latin.urdu.UrduTransliterator;
import java.io.*; import java.nio.file.*; import java.util.*; import java.util.stream.*;
/** English word -> Urdu loanword: vote over decoder outputs for the spelling + phonetic guesses. */
public class Loan {
  public static void main(String[] a) throws Exception {
    UrduTransliterator t = UrduTransliterator.load(new FileInputStream(a[0]));
    List<String> lines = Files.readAllLines(Paths.get(a[1]));
    List<String> out = lines.parallelStream().map(l -> {
      String[] p = l.split("\t"); String eng = p[0];
      List<String> inputs = new ArrayList<>(); inputs.add(eng); inputs.addAll(Arrays.asList(p[1].split(",")));
      Map<String,Integer> votes = new HashMap<>(); Map<String,Double> best = new HashMap<>();
      for (String in : inputs) {
        List<UrduTransliterator.Candidate> c = t.suggest(in, null, false, 1);
        if (c.isEmpty() || c.get(0).completion) continue;
        String u = c.get(0).urdu; votes.merge(u, 1, Integer::sum); best.merge(u, c.get(0).score, Math::max);
      }
      String top = null; for (String u : votes.keySet()) if (top == null || votes.get(u) > votes.get(top) || (votes.get(u).equals(votes.get(top)) && best.get(u) > best.get(top))) top = u;
      if (top == null || votes.get(top) < 2 || t.unigramLn(top) < -12.5) return null;
      return eng + "\t" + top + "\t" + votes.get(top);
    }).filter(Objects::nonNull).collect(Collectors.toList());
    Files.write(Paths.get(a[2]), out);
    System.out.println(out.size() + " loanword mappings");
  }
}
