package android.graphics;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
/**
 * Android's Canvas on Java2D (desktop). Shapes, paths, text, gradients, dashes, clipping and transforms map directly;
 * a paint's shadow layer (used for glows) is drawn as a few soft, widening passes under the shape.
 */
public class Canvas {
    private final Graphics2D g;private final ArrayList<Object[]> stack=new ArrayList<>();
    public Canvas(Graphics2D g){this.g=g;}
    public Graphics2D graphics(){return g;}
    public int save(){stack.add(new Object[]{g.getTransform(),g.getClip()});return stack.size();}
    public void restore(){if(stack.isEmpty())return;Object[] s=stack.remove(stack.size()-1);g.setTransform((AffineTransform)s[0]);g.setClip((Shape)s[1]);}
    public void restoreToCount(int n){while(stack.size()>=n&&!stack.isEmpty())restore();}
    public void translate(float x,float y){g.translate(x,y);}
    public void scale(float x,float y){g.scale(x,y);}
    public void rotate(float deg){g.rotate(Math.toRadians(deg));}
    public void rotate(float deg,float px,float py){g.rotate(Math.toRadians(deg),px,py);}
    public boolean clipRect(float l,float t,float r,float b){g.clip(new Rectangle2D.Float(l,t,r-l,b-t));return true;}
    public boolean clipRect(RectF r){return clipRect(r.left,r.top,r.right,r.bottom);}
    public boolean clipPath(Path p){g.clip(p.p);return true;}
    public void drawColor(int c){Shape clip=g.getClip();AffineTransform t=g.getTransform();g.setTransform(new AffineTransform());g.setComposite(AlphaComposite.SrcOver);
        g.setColor(Shader.color(c));Rectangle b=g.getDeviceConfiguration().getBounds();g.fill(clip!=null?t.createTransformedShape(clip):new Rectangle(0,0,100000,100000));g.setTransform(t);}
    public void drawRect(float l,float t,float r,float b,Paint p){shape(new Rectangle2D.Float(Math.min(l,r),Math.min(t,b),Math.abs(r-l),Math.abs(b-t)),p);}
    public void drawRect(RectF r,Paint p){drawRect(r.left,r.top,r.right,r.bottom,p);}
    public void drawRoundRect(RectF r,float rx,float ry,Paint p){shape(new RoundRectangle2D.Float(r.left,r.top,r.width(),r.height(),rx*2,ry*2),p);}
    public void drawRoundRect(float l,float t,float r,float b,float rx,float ry,Paint p){shape(new RoundRectangle2D.Float(l,t,r-l,b-t,rx*2,ry*2),p);}
    public void drawCircle(float x,float y,float r,Paint p){shape(new Ellipse2D.Float(x-r,y-r,2*r,2*r),p);}
    public void drawOval(RectF r,Paint p){shape(new Ellipse2D.Float(r.left,r.top,r.width(),r.height()),p);}
    public void drawArc(RectF r,float start,float sweep,boolean center,Paint p){shape(new Arc2D.Float(r.left,r.top,r.width(),r.height(),-start,-sweep,center?Arc2D.PIE:Arc2D.OPEN),p);}
    public void drawLine(float x0,float y0,float x1,float y1,Paint p){Paint.Style s=p.style;p.style=Paint.Style.STROKE;shape(new Line2D.Float(x0,y0,x1,y1),p);p.style=s;}
    public void drawPath(Path path,Paint p){shape(path.p,p);}
    public void drawBitmap(Bitmap b,Rect src,RectF dst,Paint p){
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,p!=null&&!p.filter?RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR:RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int sx0=src==null?0:src.left,sy0=src==null?0:src.top,sx1=src==null?b.getWidth():src.right,sy1=src==null?b.getHeight():src.bottom;
        AffineTransform t=g.getTransform();g.translate(dst.left,dst.top);g.scale(dst.width()/(sx1-sx0),dst.height()/(sy1-sy0));g.drawImage(b.img,-sx0,-sy0,null);g.setTransform(t);
    }
    public void drawBitmap(Bitmap b,float x,float y,Paint p){g.drawImage(b.img,Math.round(x),Math.round(y),null);}
    public void drawText(String s,float x,float y,Paint p){
        if(s==null||s.isEmpty())return;Font f=p.font();
        float w=p.align==Paint.Align.LEFT?0:p.measureText(s);float ox=p.align==Paint.Align.CENTER?x-w/2:p.align==Paint.Align.RIGHT?x-w:x;
        if(p.shadowR>0||p.style!=Paint.Style.FILL){Shape outline=f.createGlyphVector(Paint.frc(),s).getOutline(ox,y);shape(outline,p);return;}
        hints(p);g.setFont(f);fillPaint(p);g.drawString(s,ox,y);
    }
    private void hints(Paint p){
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,p.aa?RenderingHints.VALUE_ANTIALIAS_ON:RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }
    private void fillPaint(Paint p){g.setPaint(p.shader!=null?p.shader.awt():Shader.color(p.color));}
    private void shape(Shape s,Paint p){
        hints(p);
        if(p.shadowR>0&&(p.shadowColor>>>24)!=0){   // glow: soft widening passes in the shadow colour
            AffineTransform t=g.getTransform();g.translate(p.shadowDx,p.shadowDy);int a=p.shadowColor>>>24;
            for(int i=3;i>=1;i--){float w=p.shadowR*i/1.6f;g.setStroke(new BasicStroke(w,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.setPaint(new Color((p.shadowColor>>16)&255,(p.shadowColor>>8)&255,p.shadowColor&255,Math.min(255,(int)(a*.16f))));g.draw(s);}
            g.setPaint(new Color((p.shadowColor>>16)&255,(p.shadowColor>>8)&255,p.shadowColor&255,Math.min(255,(int)(a*.25f))));g.fill(s);
            g.setTransform(t);}
        fillPaint(p);
        if(p.style!=Paint.Style.STROKE)g.fill(s);
        if(p.style!=Paint.Style.FILL){g.setStroke(p.stroke());g.draw(s);}
    }
}
