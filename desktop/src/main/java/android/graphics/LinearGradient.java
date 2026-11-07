package android.graphics;
import java.awt.MultipleGradientPaint;
public class LinearGradient extends Shader {
    private final java.awt.Paint p;
    public LinearGradient(float x0,float y0,float x1,float y1,int c0,int c1,TileMode t){this(x0,y0,x1,y1,new int[]{c0,c1},null,t);}
    public LinearGradient(float x0,float y0,float x1,float y1,int[] c,float[] pos,TileMode t){
        if(x0==x1&&y0==y1)y1+=.01f;
        p=new java.awt.LinearGradientPaint(x0,y0,x1,y1,fractions(pos,c.length),colors(c),t==TileMode.REPEAT?MultipleGradientPaint.CycleMethod.REPEAT:t==TileMode.MIRROR?MultipleGradientPaint.CycleMethod.REFLECT:MultipleGradientPaint.CycleMethod.NO_CYCLE);
    }
    java.awt.Paint awt(){return p;}
}
