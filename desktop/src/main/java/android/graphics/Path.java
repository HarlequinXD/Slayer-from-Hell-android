package android.graphics;
import java.awt.geom.*;
public class Path {
    public enum Direction {CW,CCW}
    public enum FillType {WINDING,EVEN_ODD}
    final Path2D.Float p=new Path2D.Float(Path2D.WIND_NON_ZERO);private float lx,ly;private boolean open;
    public void reset(){p.reset();open=false;}
    public void rewind(){reset();}
    public void moveTo(float x,float y){p.moveTo(x,y);lx=x;ly=y;open=true;}
    private void ensure(){if(!open){p.moveTo(lx,ly);open=true;}}
    public void lineTo(float x,float y){ensure();p.lineTo(x,y);lx=x;ly=y;}
    public void rLineTo(float dx,float dy){lineTo(lx+dx,ly+dy);}
    public void quadTo(float x1,float y1,float x2,float y2){ensure();p.quadTo(x1,y1,x2,y2);lx=x2;ly=y2;}
    public void cubicTo(float x1,float y1,float x2,float y2,float x3,float y3){ensure();p.curveTo(x1,y1,x2,y2,x3,y3);lx=x3;ly=y3;}
    public void close(){if(open)p.closePath();}
    public void addCircle(float x,float y,float r,Direction d){p.append(new Ellipse2D.Float(x-r,y-r,2*r,2*r),false);}
    public void addRect(float l,float t,float r,float b,Direction d){p.append(new Rectangle2D.Float(l,t,r-l,b-t),false);}
    public void addRoundRect(RectF r,float rx,float ry,Direction d){p.append(new RoundRectangle2D.Float(r.left,r.top,r.width(),r.height(),rx*2,ry*2),false);}
    public void addOval(RectF r,Direction d){p.append(new Ellipse2D.Float(r.left,r.top,r.width(),r.height()),false);}
    public void setFillType(FillType f){p.setWindingRule(f==FillType.EVEN_ODD?Path2D.WIND_EVEN_ODD:Path2D.WIND_NON_ZERO);}
}
