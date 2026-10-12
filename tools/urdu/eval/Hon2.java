import helium314.keyboard.latin.urdu.ReligiousShortcuts;
import java.io.*; import java.util.*;
public class Hon2 { public static void main(String[] a) throws Exception {
  ReligiousShortcuts r = ReligiousShortcuts.load(new FileInputStream(a[0]));
  for (String w : new String[]{"Salallahualaihiwasallam","sallalahu","RadiAllahuanh","radiallahuanhu","rasool","Rasoolullah","Rasulallah","eesa","Isa","moosa","umar","omar","Alaihisalaam","alaihisalam","rasul","rasu","umr","the","all","fat","jazakallah","jazakAllahukhair"})
    System.out.println(w + " -> " + r.suggest(w, null, false, 3));
}}
