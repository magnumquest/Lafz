import helium314.keyboard.latin.urdu.UrduTransliterator;
import java.io.*; import java.util.*;
public class Try { public static void main(String[] a) throws Exception {
  UrduTransliterator t = UrduTransliterator.load(new FileInputStream(a[0]));
  for (int s = 1; s < a.length; s++) {
    String prev = null; boolean start = true; StringBuilder out = new StringBuilder();
    for (String w : a[s].split(" ")) {
      List<UrduTransliterator.Candidate> c = t.suggest(w, prev, start, 4);
      out.append(c.isEmpty() ? "?" : c.get(0).urdu).append(" ");
      System.out.println("  " + w + " -> " + c);
      prev = c.isEmpty() ? null : c.get(0).urdu; start = false;
    }
    System.out.println(a[s] + "  =>  " + out + "\n");
  }
  System.out.println("typing 'kal' partial: " + t.suggest("ka", null, true, 5) + " | " + t.suggest("kal", "میں", false, 5));
  System.out.println("next after میں: " + t.nextWords("میں", false, 5));
}}
