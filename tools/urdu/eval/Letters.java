import helium314.keyboard.latin.urdu.RomanTransliterator;
import java.io.*;
/** java Letters model.bin keys...  prints the letters each key can stand for inside a word */
public class Letters {
  public static void main(String[] a) throws Exception {
    RomanTransliterator t = RomanTransliterator.load(new FileInputStream(a[0]));
    for (int i = 1; i < a.length; i++) System.out.println(a[i] + " -> " + t.lettersFor(a[i]));
  }
}
