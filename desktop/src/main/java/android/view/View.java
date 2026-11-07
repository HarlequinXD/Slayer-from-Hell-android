package android.view;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.os.Handler;
/** A full-window view on the desktop: drawn every frame into the UI layer, fed touch events by the cursor. */
public class View {
    private final Context ctx;private int w,h;private final Handler handler=new Handler();
    public View(Context c){ctx=c;}
    public Context getContext(){return ctx;}
    public Resources getResources(){return ctx.getResources();}
    public final int getWidth(){return w;} public final int getHeight(){return h;}
    /** Desktop: the window's size. */
    public void layout(int nw,int nh){if(nw!=w||nh!=h){int ow=w,oh=h;w=nw;h=nh;onSizeChanged(nw,nh,ow,oh);}}
    protected void onSizeChanged(int w,int h,int ow,int oh){}
    protected void onDraw(Canvas c){}
    /** Desktop: draws the view. */
    public void draw(Canvas c){onDraw(c);}
    public boolean onTouchEvent(MotionEvent e){return false;}
    public boolean dispatchTouchEvent(MotionEvent e){return onTouchEvent(e);}
    public void invalidate(){} public void postInvalidate(){} public void postInvalidateOnAnimation(){} public void postInvalidateDelayed(long ms){}
    public boolean post(Runnable r){return handler.post(r);} public boolean postDelayed(Runnable r,long ms){return handler.postDelayed(r,ms);}
    public void setFocusable(boolean f){} public void setFocusableInTouchMode(boolean f){} public boolean requestFocus(){return true;}
}
