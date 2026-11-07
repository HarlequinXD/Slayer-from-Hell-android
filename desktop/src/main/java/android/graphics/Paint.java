package android.graphics;
import java.awt.*;
import java.awt.font.*;
import java.awt.image.BufferedImage;
import java.util.*;
public class Paint {
    public static final int ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2;
    public enum Style {FILL,STROKE,FILL_AND_STROKE}
    public enum Align {LEFT,CENTER,RIGHT}
    public enum Cap {BUTT,ROUND,SQUARE}
    public enum Join {MITER,ROUND,BEVEL}
    int color=0xff000000;Style style=Style.FILL;float strokeWidth=0,textSize=12,letterSpacing;Align align=Align.LEFT;Cap cap=Cap.BUTT;Join join=Join.MITER;
    Typeface typeface=Typeface.DEFAULT;Shader shader;PathEffect effect;boolean aa,filter=true;
    float shadowR,shadowDx,shadowDy;int shadowColor;
    private Font font;private float fontSize=-1;private Typeface fontFace;private float fontSpacing;
    public Paint(){} public Paint(int flags){aa=(flags&ANTI_ALIAS_FLAG)!=0;}
    public Paint(Paint o){color=o.color;style=o.style;strokeWidth=o.strokeWidth;textSize=o.textSize;align=o.align;cap=o.cap;join=o.join;typeface=o.typeface;shader=o.shader;effect=o.effect;aa=o.aa;letterSpacing=o.letterSpacing;}
    public void setColor(int c){color=c;} public int getColor(){return color;}
    public void setAlpha(int a){color=(color&0xffffff)|((a&255)<<24);} public int getAlpha(){return color>>>24;}
    public void setARGB(int a,int r,int g,int b){color=(a<<24)|(r<<16)|(g<<8)|b;}
    public void setStyle(Style s){style=s;} public Style getStyle(){return style;}
    public void setStrokeWidth(float w){strokeWidth=w;} public float getStrokeWidth(){return strokeWidth;}
    public void setStrokeCap(Cap c){cap=c;} public void setStrokeJoin(Join j){join=j;}
    public void setTextSize(float s){textSize=s;} public float getTextSize(){return textSize;}
    public void setTextAlign(Align a){align=a;} public Align getTextAlign(){return align;}
    public Typeface setTypeface(Typeface t){typeface=t==null?Typeface.DEFAULT:t;return t;}
    public Shader setShader(Shader s){shader=s;return s;}
    public PathEffect setPathEffect(PathEffect e){effect=e;return e;}
    public void setShadowLayer(float r,float dx,float dy,int c){shadowR=r;shadowDx=dx;shadowDy=dy;shadowColor=c;}
    public void clearShadowLayer(){shadowR=0;}
    public void setLetterSpacing(float s){letterSpacing=s;}
    public void setAntiAlias(boolean a){aa=a;} public void setFilterBitmap(boolean f){filter=f;} public void setDither(boolean d){}
    Font font(){
        if(font==null||fontSize!=textSize||fontFace!=typeface||fontSpacing!=letterSpacing){fontSize=textSize;fontFace=typeface;fontSpacing=letterSpacing;
            Font f=typeface.font.deriveFont(Math.max(1f,textSize));
            if(letterSpacing!=0){Map<TextAttribute,Object> m=new HashMap<>();m.put(TextAttribute.TRACKING,letterSpacing);f=f.deriveFont(m);}
            font=f;}
        return font;
    }
    private static final FontRenderContext FRC=new FontRenderContext(null,true,true);
    static FontRenderContext frc(){return FRC;}
    public float measureText(String s){if(s==null||s.isEmpty())return 0;return (float)font().getStringBounds(s,FRC).getWidth();}
    public float measureText(char[] t,int i,int n){return measureText(new String(t,i,n));}
    public float ascent(){return -font().getLineMetrics("Hg",FRC).getAscent();}
    public float descent(){return font().getLineMetrics("Hg",FRC).getDescent();}
    java.awt.Stroke stroke(){
        int c=cap==Cap.ROUND?BasicStroke.CAP_ROUND:cap==Cap.SQUARE?BasicStroke.CAP_SQUARE:BasicStroke.CAP_BUTT;
        int j=join==Join.ROUND?BasicStroke.JOIN_ROUND:join==Join.BEVEL?BasicStroke.JOIN_BEVEL:BasicStroke.JOIN_MITER;
        float w=Math.max(strokeWidth,1f);
        if(effect instanceof DashPathEffect){DashPathEffect d=(DashPathEffect)effect;return new BasicStroke(w,c,j,10,d.intervals,d.phase);}
        return new BasicStroke(w,c,j,10);
    }
}
