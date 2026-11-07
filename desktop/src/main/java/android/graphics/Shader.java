package android.graphics;
/** A fill gradient (desktop: becomes a Java2D paint). */
public abstract class Shader {
    public enum TileMode {CLAMP,REPEAT,MIRROR}
    abstract java.awt.Paint awt();
    static java.awt.Color color(int c){return new java.awt.Color((c>>16)&255,(c>>8)&255,c&255,(c>>>24));}
    static float[] fractions(float[] pos,int n){
        float[] f=new float[n];for(int i=0;i<n;i++)f[i]=pos!=null?pos[i]:(n==1?0:i/(float)(n-1));
        for(int i=1;i<n;i++)if(f[i]<=f[i-1])f[i]=Math.min(1,f[i-1]+1e-4f);   // Java2D needs strictly increasing stops
        if(f[n-1]>1)for(int i=0;i<n;i++)f[i]=f[i]/f[n-1];return f;
    }
    static java.awt.Color[] colors(int[] c){java.awt.Color[] r=new java.awt.Color[c.length];for(int i=0;i<c.length;i++)r[i]=color(c[i]);return r;}
}
