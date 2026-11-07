package android.opengl;
import android.content.Context;
import android.view.View;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
/** Desktop: holds the renderer; the window's main loop calls it (the GL context belongs to the window). */
public class GLSurfaceView extends View {
    public interface Renderer {void onSurfaceCreated(GL10 gl,EGLConfig cfg);void onSurfaceChanged(GL10 gl,int w,int h);void onDrawFrame(GL10 gl);}
    public static final int RENDERMODE_CONTINUOUSLY=1;
    public Renderer renderer;public boolean created;public int sw=-1,sh=-1;
    public GLSurfaceView(Context c){super(c);}
    public void setRenderer(Renderer r){renderer=r;}
    public void setEGLContextClientVersion(int v){} public void setEGLConfigChooser(int r,int g,int b,int a,int d,int s){} public void setPreserveEGLContextOnPause(boolean p){}
    public void setRenderMode(int m){} public void onPause(){} public void onResume(){}
    /** Called by the desktop main loop with the GL context current. */
    public void frame(int w,int h){
        if(renderer==null)return;
        if(!created){created=true;renderer.onSurfaceCreated(null,null);}
        if(w!=sw||h!=sh){sw=w;sh=h;renderer.onSurfaceChanged(null,w,h);}
        renderer.onDrawFrame(null);
    }
}
