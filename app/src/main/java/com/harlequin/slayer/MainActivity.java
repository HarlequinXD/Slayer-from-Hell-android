package com.harlequin.slayer;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * Full-screen landscape. Three screens share one window: the menus, the level editor, and the game (an OpenGL ES 3
 * view with the touch HUD on top, and the pause / level-complete overlays over that).
 */
public final class MainActivity extends Activity implements MenuView.Host,EditorView.Host {
    private FrameLayout root;private Sfx sfx;private MapStore store;
    private MenuView menu,overlay;private EditorView editor;
    private GLSurfaceView gl;private Game game;private Hud hud;
    /** What is being played: a campaign map index, or -1 (custom / editor test); the map itself for restarts. */
    private int campaign=-1;private SectorMap playing;private boolean fromEditor;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if(android.os.Build.VERSION.SDK_INT>=28)getWindow().getAttributes().layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        sfx=new Sfx(this);store=new MapStore(getFilesDir());
        root=new FrameLayout(this);setContentView(root);
        showMenu(MenuView.MAIN);
        immersive();
    }
    private void immersive(){
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean f){super.onWindowFocusChanged(f);if(f)immersive();}

    // ---------------------------------------------------------------- screens
    private void showMenu(int page){stopGame();root.removeAllViews();editor=null;menu=new MenuView(this,this,store);root.addView(menu);menu.show(page);}
    private void stopGame(){
        if(gl!=null){gl.onPause();gl=null;}game=null;hud=null;overlay=null;sfx.engine(0,1);
    }
    private void startGame(SectorMap m){
        stopGame();root.removeAllViews();playing=m.copy();
        game=new Game(this,m);game.sfx=sfx;
        hud=new Hud(this,game);game.hud=hud;hud.onPause=()->runOnUiThread(this::pauseGame);
        game.listener=(k,t,s)->runOnUiThread(()->levelComplete(k,t,s));
        gl=new GLSurfaceView(this);   // full-resolution surface: Game renders at RENDER_LINES lines and point-scales up itself
        gl.setEGLContextClientVersion(3);gl.setEGLConfigChooser(8,8,8,8,24,0);gl.setPreserveEGLContextOnPause(true);
        gl.setRenderer(game);gl.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(gl);root.addView(hud);
    }
    private void showOverlay(int page){
        if(game==null)return;
        if(overlay!=null)root.removeView(overlay);
        overlay=new MenuView(this,this,store);overlay.fromEditor=fromEditor;root.addView(overlay);
        if(page==MenuView.COMPLETE)overlay.hasNext=campaign>=0&&campaign+1<Maps.count();
        overlay.show(page);
    }
    private void pauseGame(){if(game==null||overlay!=null)return;game.paused=true;sfx.engine(0,1);showOverlay(MenuView.PAUSE);}
    private void levelComplete(int kills,int total,float seconds){
        if(game==null)return;game.paused=true;sfx.engine(0,1);showOverlay(MenuView.COMPLETE);
        overlay.kills=kills;overlay.total=total;overlay.seconds=seconds;overlay.doneName=playing.name;overlay.invalidate();
    }

    // ---------------------------------------------------------------- MenuView.Host
    @Override public void playCampaign(int i){campaign=i;fromEditor=false;startGame(Maps.campaign(i));}
    @Override public void playCustom(String id){
        try{campaign=-1;fromEditor=false;startGame(store.load(id));}catch(Exception e){Toast.makeText(this,"Couldn't open that level: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override public void editMap(String id,SectorMap from){
        SectorMap m=from;
        if(m==null)try{m=store.load(id);}catch(Exception e){Toast.makeText(this,"Couldn't open that level: "+e.getMessage(),Toast.LENGTH_LONG).show();return;}
        stopGame();root.removeAllViews();menu=null;
        editor=new EditorView(this,m,id,store,this);if(id==null)editor.autoSave();
        root.addView(editor);
    }
    @Override public void quit(){finish();}
    @Override public void resume(){if(game==null)return;if(overlay!=null){root.removeView(overlay);overlay=null;}game.paused=false;}
    @Override public void restart(){SectorMap m=playing;if(m!=null)startGame(m);}
    @Override public void leaveGame(){
        if(fromEditor&&editor!=null){stopGame();root.removeAllViews();root.addView(editor);editor.invalidate();return;}
        showMenu(campaign>=0?MenuView.CAMPAIGN:MenuView.CUSTOM);
    }
    @Override public void nextLevel(){if(campaign>=0&&campaign+1<Maps.count())playCampaign(campaign+1);else showMenu(MenuView.MAIN);}

    // ---------------------------------------------------------------- EditorView.Host
    @Override public void exitEditor(){editor=null;showMenu(MenuView.CUSTOM);}
    @Override public void testPlay(SectorMap m){
        EditorView keep=editor;campaign=-1;fromEditor=true;startGame(m);editor=keep;
    }

    // ---------------------------------------------------------------- lifecycle
    @Override public void onBackPressed(){
        if(overlay!=null){overlay.back();return;}
        if(game!=null){pauseGame();return;}
        if(editor!=null){editor.back();return;}
        if(menu!=null&&menu.back())return;
        super.onBackPressed();
    }
    @Override protected void onPause(){super.onPause();if(gl!=null)gl.onPause();if(game!=null&&overlay==null)pauseGame();sfx.pause();if(editor!=null)editor.autoSave();}
    @Override protected void onResume(){super.onResume();if(gl!=null)gl.onResume();sfx.resume();immersive();}
}
