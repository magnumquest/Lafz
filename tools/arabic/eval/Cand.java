import helium314.keyboard.latin.urdu.RomanTransliterator;
import java.io.*; import java.util.*;
/** java Cand model.bin "prev words|-" typed...  : top candidates (score) for each typed word given the previous words */
public class Cand { public static void main(String[] a) throws Exception {
  RomanTransliterator t = RomanTransliterator.load(new FileInputStream(a[0]));
  String prev = a[1].equals("-") ? null : a[1];
  for (int i = 2; i < a.length; i++) System.out.println(a[i] + "  [" + (prev == null ? "start" : prev) + "] -> " + t.suggest(a[i], prev, prev == null, 8));
}}
