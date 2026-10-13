import helium314.keyboard.latin.urdu.ReligiousShortcuts;
import java.io.*; import java.util.*;
/** java HonAr honorifics.tsv : prints Arabic (and Latin/Urdu for comparison) honorific suggestions */
public class HonAr { public static void main(String[] a) throws Exception {
  ReligiousShortcuts r = ReligiousShortcuts.load(new FileInputStream(a[0]));
  String[][] cases = {{"umar",""},{"omar",""},{"3omar",""},{"othman",""},{"rasul",""},{"rasool",""},{"isa",""},{"issa",""},{"ra","Umar"},{"ra","Aisha"},{"as","Isa"},{"saw","Muhammad"},{"as","the"},{"inshallah",""},{"mashallah",""},{"alhamdulillah",""},{"jazakallah",""},{"assalam",""},{"salamualaikum",""},{"bismillah",""},{"hello",""},{"the",""},{"fatima",""},{"abubakr",""},{"quran",""},{"عمر",""},{"فاطمة",""},{"إبراهيم",""},{"هود",""},{"محمد",""},{"ra","عمر"},{"ra","عائشة"},{"rh","Bukhari"}};
  for (int script = 2; script >= 0; script--) {
    System.out.println("--- script " + (script == 2 ? "ARABIC" : script == 1 ? "URDU" : "LATIN"));
    if (script == 1) { for (String[] c : new String[][]{{"umar",""},{"inshallah",""}}) System.out.println(c[0] + " -> " + r.suggest(c[0], null, script, 3)); continue; }
    if (script == 0) { for (String[] c : new String[][]{{"umar",""},{"inshallah",""}}) System.out.println(c[0] + " -> " + r.suggest(c[0], null, script, 3)); continue; }
    for (String[] c : cases) System.out.println(c[0] + (c[1].isEmpty() ? "" : " (after " + c[1] + ")") + " -> " + r.suggest(c[0], c[1].isEmpty() ? null : c[1], script, 4));
    System.out.println("after عمر: " + r.afterWord("عمر", script, 3) + " | after عائشة: " + r.afterWord("عائشة", script, 3) + " | after Aisha: " + r.afterWord("Aisha", script, 3));
  }
}}
