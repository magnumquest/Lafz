import helium314.keyboard.latin.urdu.UrduTransliterator;
import java.io.*; import java.nio.file.*; import java.util.*; import java.util.stream.*;
/** keeps mined (urdu, english) pairs the decoder independently reproduces => true transliterations */
public class MineCheck {
  public static void main(String[] a) throws Exception {
    UrduTransliterator t = UrduTransliterator.load(new FileInputStream(a[0]));
    Map<String,String[]> g = new HashMap<>();
    for (String l : Files.readAllLines(Paths.get(a[1]))) { String[] p = l.split("\t"); g.put(p[0], p[1].split(",")); }
    List<String> out = Files.readAllLines(Paths.get(a[2])).parallelStream().map(l -> {
      String[] p = l.split("\t"); String u = p[0], e = p[1];
      List<String> in = new ArrayList<>(); in.add(e); if (g.containsKey(e)) in.addAll(Arrays.asList(g.get(e)));
      for (String x : in) for (UrduTransliterator.Candidate c : t.suggest(x, null, false, 3)) if (!c.completion && c.urdu.equals(u)) return l;
      return null; }).filter(Objects::nonNull).collect(Collectors.toList());
    Files.write(Paths.get(a[3]), out); System.out.println(out.size() + " confirmed of " + Files.readAllLines(Paths.get(a[2])).size());
  }
}
