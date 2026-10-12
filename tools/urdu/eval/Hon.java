import helium314.keyboard.latin.urdu.ReligiousShortcuts;
import java.io.*; import java.util.*;
public class Hon { public static void main(String[] a) throws Exception {
  ReligiousShortcuts r = ReligiousShortcuts.load(new FileInputStream(a[0]));
  String[][] cases = {{"Rasul",""},{"rasulallah",""},{"ras",""},{"Umar",""},{"ra","Umar"},{"ra","Aisha"},{"ra","Hasan"},{"as","Isa"},{"as","the"},{"Isa",""},{"alaihi","Isa"},
    {"saw","Muhammad"},{"saw","I"},{"inshallah",""},{"jazak",""},{"bismillah",""},{"namaz",""},{"rh","Bukhari"},{"swt",""},{"Fatima",""},{"hello",""},{"the",""},{"ali",""}};
  for (String[] c : cases) System.out.println(c[0] + (c[1].isEmpty() ? "" : "  (after " + c[1] + ")") + "  ->  " + r.suggest(c[0], c[1].isEmpty() ? null : c[1], false, 4));
  System.out.println("--- urdu output");
  for (String[] c : new String[][]{{"rasul",""},{"umar",""},{"inshallah",""},{"ra","Aisha"},{"isa",""},{"رسول",""},{"عمر",""}})
    System.out.println(c[0] + "  ->  " + r.suggest(c[0], c[1].isEmpty() ? null : c[1], true, 4));
  System.out.println("after Umar: " + r.afterWord("Umar", false, 3) + " | after Aisha urdu: " + r.afterWord("Aisha", true, 3) + " | after عمر: " + r.afterWord("عمر", true, 3) + " | after the: " + r.afterWord("the", false, 3));
}}
