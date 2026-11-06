package com.harlequin.slayer;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/** Full-screen landscape: the OpenGL ES 3 view with the touch HUD on top. */
public final class MainActivity extends Activity {
    private GLSurfaceView gl;private Game game;private Sfx sfx;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if(android.os.Build.VERSION.SDK_INT>=28)getWindow().getAttributes().layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        game=new Game(this);sfx=new Sfx(this);game.sfx=sfx;
        Hud hud=new Hud(this,game);game.hud=hud;
        gl=new GLSurfaceView(this);   // full-resolution surface: Game renders at 270 lines and point-scales up itself
        gl.setEGLContextClientVersion(3);gl.setEGLConfigChooser(8,8,8,8,24,0);gl.setPreserveEGLContextOnPause(true);
        gl.setRenderer(game);gl.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        FrameLayout root=new FrameLayout(this);root.addView(gl);root.addView(hud);setContentView(root);
        immersive();
    }
    private void immersive(){
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean f){super.onWindowFocusChanged(f);if(f)immersive();}
    @Override protected void onPause(){super.onPause();gl.onPause();sfx.pause();}
    @Override protected void onResume(){super.onResume();gl.onResume();sfx.resume();immersive();}
}
