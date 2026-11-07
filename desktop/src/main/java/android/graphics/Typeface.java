package android.graphics;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.*;
/** Fonts: Segoe UI on Windows (Semibold for "medium"), the JVM's sans-serif elsewhere. */
public class Typeface {
    public static final int NORMAL=0,BOLD=1,ITALIC=2,BOLD_ITALIC=3;
    final Font font;
    private Typeface(Font f){font=f;}
    private static Set<String> families;
    private static boolean has(String f){if(families==null){families=new HashSet<>();try{families.addAll(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));}catch(Throwable ignored){}}return families.contains(f);}
    private static Font make(String family,int style){
        boolean medium=family!=null&&family.contains("medium"),mono=family!=null&&family.contains("mono");
        int s=((style&BOLD)!=0?Font.BOLD:0)|((style&ITALIC)!=0?Font.ITALIC:0);
        if(mono)return new Font(Font.MONOSPACED,s,12);
        if(medium&&has("Segoe UI Semibold"))return new Font("Segoe UI Semibold",s,12);
        if(has("Segoe UI"))return new Font("Segoe UI",s|(medium?Font.BOLD:0),12);
        return new Font(Font.SANS_SERIF,s|(medium?Font.BOLD:0),12);
    }
    public static final Typeface DEFAULT=new Typeface(make("sans-serif",NORMAL)),DEFAULT_BOLD=new Typeface(make("sans-serif",BOLD)),MONOSPACE=new Typeface(make("monospace",NORMAL)),SANS_SERIF=DEFAULT;
    public static Typeface create(String family,int style){return new Typeface(make(family,style));}
    public static Typeface create(Typeface base,int style){return new Typeface(base==null?make("sans-serif",style):base.font.deriveFont(((style&BOLD)!=0?Font.BOLD:0)|((style&ITALIC)!=0?Font.ITALIC:0)));}
}
