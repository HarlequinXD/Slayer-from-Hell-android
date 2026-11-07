package android.graphics;
import java.awt.MultipleGradientPaint;
public class RadialGradient extends Shader {
    private final java.awt.Paint p;
    public RadialGradient(float cx,float cy,float r,int c0,int c1,TileMode t){this(cx,cy,r,new int[]{c0,c1},null,t);}
    public RadialGradient(float cx,float cy,float r,int[] c,float[] pos,TileMode t){
        p=new java.awt.RadialGradientPaint(cx,cy,Math.max(.01f,r),fractions(pos,c.length),colors(c),MultipleGradientPaint.CycleMethod.NO_CYCLE);
    }
    java.awt.Paint awt(){return p;}
}
