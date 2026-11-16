import helium314.keyboard.latin.urdu.RomanTransliterator;
import java.io.*;
/** prints sound spellings next to the dictionary candidates: java Phon model.bin word... */
public class Phon {
  public static void main(String[] a) throws Exception {
    RomanTransliterator t = RomanTransliterator.load(new FileInputStream(a[0]));
    if (System.getProperty("oov") != null) t.oovLn = Double.parseDouble(System.getProperty("oov"));
    for (int i = 1; i < a.length; i++) {
      long t0 = System.nanoTime();
      var ph = t.phonetic(a[i], 2);
      double ms = (System.nanoTime() - t0) / 1e6;
      System.out.printf("%-10s phon=%s (%.1f ms)  dict=%s%n", a[i], ph, ms, t.suggest(a[i], null, false, 4));
    }
  }
}
