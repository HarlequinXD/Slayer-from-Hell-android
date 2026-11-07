package android.graphics;
import java.awt.image.BufferedImage;
public class Bitmap {
    public enum Config {ARGB_8888,RGB_565}
    public final BufferedImage img;
    public Bitmap(BufferedImage i){img=i;}
    public static Bitmap createBitmap(int[] px,int w,int h,Config c){BufferedImage i=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);i.setRGB(0,0,w,h,px,0,w);return new Bitmap(i);}
    public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB));}
    public static Bitmap createScaledBitmap(Bitmap b,int w,int h,boolean filter){
        BufferedImage i=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D g=i.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,filter?java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR:java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(b.img,0,0,w,h,null);g.dispose();return new Bitmap(i);}
    public int getWidth(){return img.getWidth();} public int getHeight(){return img.getHeight();}
    public void getPixels(int[] px,int off,int stride,int x,int y,int w,int h){img.getRGB(x,y,w,h,px,off,stride);}
    public boolean hasAlpha(){return img.getColorModel().hasAlpha();}
    public void recycle(){}
}
