package com.harlequin.slayer;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;

/**
 * Full-screen landscape. Three screens share one window: the menus, the level editor, and the game (an OpenGL ES 3
 * view with the touch HUD on top, and the pause / level-complete overlays over that).
 */
public final class MainActivity extends Activity implements MenuView.Host,EditorView.Host,Edit3DView.Host,Controls.Shell {
    private FrameLayout root;private Sfx sfx;private MapStore store;private Music music;private StoryView story;
    private final Devices dev=new Devices();private Overlay layer;private Controls controls;private long lastFrame;
    private final Choreographer.FrameCallback tick=new Choreographer.FrameCallback(){public void doFrame(long t){
        float dt=lastFrame==0?1/60f:Math.min(.05f,(t-lastFrame)/1e9f);lastFrame=t;controls.frame(dt);Choreographer.getInstance().postFrameCallback(this);}};
    private void clearScreens(){for(int i=root.getChildCount()-1;i>=0;i--)if(root.getChildAt(i)!=layer)root.removeViewAt(i);}
    private void addScreen(View v){root.addView(v,root.indexOfChild(layer));}
    private void toast(String s){layer.toast(s);}
    private MenuView menu,overlay;private EditorView editor;private Edit3DView view3d;private float[] cam3d;
    private GLSurfaceView gl;private Game game;private Hud hud;
    /** What is being played: a campaign map index, or -1 (custom / editor test); the map itself for restarts. */
    private int campaign=-1;private SectorMap playing;private boolean fromEditor;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if(android.os.Build.VERSION.SDK_INT>=28)getWindow().getAttributes().layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        sfx=new Sfx(this);store=new MapStore(getFilesDir());
        music=new Music();music.prepare(MusicSynth.HELL);music.prepare(MusicSynth.TECH);   // rendered in the background while she reads the menu
        root=new FrameLayout(this);setContentView(root);
        layer=new Overlay(this);root.addView(layer);controls=new Controls(dev,layer,this);
        root.setPointerIcon(PointerIcon.getSystemIcon(this,PointerIcon.TYPE_NULL));   // our own cursor is drawn instead
        scanPads();
        Choreographer.getInstance().postFrameCallback(tick);
        showMenu(MenuView.MAIN);
        immersive();
    }
    private void immersive(){
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean f){super.onWindowFocusChanged(f);if(f)immersive();}

    // ---------------------------------------------------------------- screens
    private void showMenu(int page){stopGame();clearScreens();editor=null;story=null;music.stop();menu=new MenuView(this,this,store);addScreen(menu);menu.show(page);}
    private void stopGame(){
        if(gl!=null){gl.onPause();gl=null;}game=null;hud=null;overlay=null;sfx.engine(0,1);
    }
    private void startGame(SectorMap m){
        stopGame();clearScreens();playing=m.copy();view3d=null;
        game=new Game(this,m);game.sfx=sfx;sfx.restartLoops();
        music.play(Music.themeOf(m));game.onCombat=c->music.combat(c);
        hud=new Hud(this,game);game.hud=hud;hud.onPause=()->runOnUiThread(this::pauseGame);
        game.listener=(k,t,s)->runOnUiThread(()->levelComplete(k,t,s));
        gl=new GLSurfaceView(this);   // full-resolution surface: Game renders at RENDER_LINES lines and point-scales up itself
        gl.setEGLContextClientVersion(3);gl.setEGLConfigChooser(8,8,8,8,24,0);gl.setPreserveEGLContextOnPause(true);
        gl.setRenderer(game);gl.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);hookCapture(gl);
        addScreen(gl);addScreen(hud);
    }
    private void showOverlay(int page){
        if(game==null)return;
        if(overlay!=null)root.removeView(overlay);
        overlay=new MenuView(this,this,store);overlay.fromEditor=fromEditor;addScreen(overlay);
        if(page==MenuView.COMPLETE){overlay.hasNext=campaign>=0;overlay.finale=campaign+1>=Maps.count();}
        overlay.show(page);
    }
    private void pauseGame(){if(game==null||overlay!=null)return;game.paused=true;sfx.engine(0,1);showOverlay(MenuView.PAUSE);}
    private void levelComplete(int kills,int total,float seconds){
        if(game==null)return;game.paused=true;sfx.engine(0,1);music.combat(false);
        if(campaign>=0)MenuView.unlock(this,campaign+1);   // the next map is open now
        showOverlay(MenuView.COMPLETE);
        overlay.kills=kills;overlay.total=total;overlay.seconds=seconds;overlay.doneName=playing.name;overlay.invalidate();
    }

    // ---------------------------------------------------------------- MenuView.Host
    @Override public void playCampaign(int i){campaign=i;fromEditor=false;SectorMap m=Maps.campaign(i);story(Story.before(i),Music.themeOf(m),()->startGame(m));}
    /** A chapter of the story on its own screen; `then` runs when she has read it (or skipped it). */
    private void story(Story.Chapter ch,int theme,Runnable then){
        stopGame();clearScreens();menu=null;editor=null;music.play(theme);
        story=new StoryView(this,ch,()->root.post(()->{story=null;then.run();}));addScreen(story);
    }
    @Override public void playCustom(String id){
        try{campaign=-1;fromEditor=false;startGame(store.load(id));}catch(Exception e){toast("Couldn't open that level: "+e.getMessage());}
    }
    @Override public void editMap(String id,SectorMap from){
        SectorMap m=from;
        if(m==null)try{m=store.load(id);}catch(Exception e){toast("Couldn't open that level: "+e.getMessage());return;}
        stopGame();clearScreens();menu=null;music.stop();
        editor=new EditorView(this,m,id,store,this);if(id==null)editor.autoSave();
        addScreen(editor);
    }
    @Override public void quit(){finish();}
    @Override public void resume(){if(game==null)return;if(overlay!=null){root.removeView(overlay);overlay=null;}game.paused=false;}
    @Override public void restart(){SectorMap m=playing;if(m!=null)startGame(m);}
    @Override public void leaveGame(){
        if(fromEditor&&editor!=null){stopGame();clearScreens();music.stop();addScreen(editor);editor.invalidate();return;}
        showMenu(campaign>=0?MenuView.CAMPAIGN:MenuView.CUSTOM);
    }
    @Override public void nextLevel(){
        if(campaign<0){showMenu(MenuView.MAIN);return;}
        MenuView.unlock(this,campaign+1);
        if(campaign+1<Maps.count())playCampaign(campaign+1);
        else{campaign=-1;story(Story.epilogue(),MusicSynth.HELL,()->showMenu(MenuView.CAMPAIGN));}   // the end
    }

    // ---------------------------------------------------------------- EditorView.Host
    @Override public void exitEditor(){editor=null;showMenu(MenuView.CUSTOM);}
    @Override public void testPlay(SectorMap m){
        EditorView keep=editor;campaign=-1;fromEditor=true;startGame(m);editor=keep;
    }

    // ---------------------------------------------------------------- the editor's 3D view (the game renderer in edit mode)
    @Override public void open3D(){
        if(editor==null)return;
        stopGame();clearScreens();music.stop();
        game=new Game(this,editor.map().copy());game.editMode=true;game.sfx=sfx;
        if(cam3d!=null)game.setEditCamera(cam3d[0],cam3d[1],cam3d[2],cam3d[3],cam3d[4]);
        gl=new GLSurfaceView(this);gl.setEGLContextClientVersion(3);gl.setEGLConfigChooser(8,8,8,8,24,0);gl.setPreserveEGLContextOnPause(true);
        gl.setRenderer(game);gl.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);hookCapture(gl);
        view3d=new Edit3DView(this,game,editor.ed,this);
        addScreen(gl);addScreen(view3d);
    }
    @Override public void back3D(){
        if(game!=null)cam3d=game.editCamera();
        view3d=null;stopGame();clearScreens();if(editor!=null){addScreen(editor);editor.invalidate();}
    }
    @Override public void changed3D(){if(editor!=null)editor.autoSave();}
    @Override public void playHere(SectorMap m){if(game!=null)cam3d=game.editCamera();view3d=null;EditorView keep=editor;campaign=-1;fromEditor=true;startGame(m);editor=keep;}

    // ---------------------------------------------------------------- Controls.Shell
    @Override public View screen(){for(int i=root.getChildCount()-1;i>=0;i--){View v=root.getChildAt(i);if(v!=layer)return v;}return layer;}
    @Override public Hud playing(){return game!=null&&hud!=null&&overlay==null&&view3d==null&&!game.paused?hud:null;}
    @Override public void back(){onBackPressed();}
    @Override public void pause(){pauseGame();}
    @Override public void captureMouse(boolean on){
        if(android.os.Build.VERSION.SDK_INT<26||gl==null)return;
        if(on){gl.setFocusable(true);gl.setFocusableInTouchMode(true);gl.requestFocus();gl.requestPointerCapture();}else gl.releasePointerCapture();
    }

    // ---------------------------------------------------------------- controller, keyboard, mouse
    private void scanPads(){boolean any=false;for(int id:InputDevice.getDeviceIds()){InputDevice d=InputDevice.getDevice(id);if(d!=null&&isPad(d.getSources()))any=true;}dev.padConnected=any;}
    private static boolean isPad(int src){return (src&InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD||(src&InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK;}
    private static int padBit(int code){
        switch(code){case KeyEvent.KEYCODE_BUTTON_A:return Devices.A;case KeyEvent.KEYCODE_BUTTON_B:return Devices.B;case KeyEvent.KEYCODE_BUTTON_X:return Devices.X;
            case KeyEvent.KEYCODE_BUTTON_Y:return Devices.Y;case KeyEvent.KEYCODE_BUTTON_L1:return Devices.LB;case KeyEvent.KEYCODE_BUTTON_R1:return Devices.RB;
            case KeyEvent.KEYCODE_BUTTON_SELECT:return Devices.BACK;case KeyEvent.KEYCODE_BUTTON_START:return Devices.START;case KeyEvent.KEYCODE_BUTTON_THUMBL:return Devices.LS;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:return Devices.RS;case KeyEvent.KEYCODE_DPAD_UP:return Devices.UP;case KeyEvent.KEYCODE_DPAD_DOWN:return Devices.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:return Devices.LEFT;case KeyEvent.KEYCODE_DPAD_RIGHT:return Devices.RIGHT;default:return 0;}
    }
    private static int key(int code){
        switch(code){case KeyEvent.KEYCODE_W:return Devices.K_W;case KeyEvent.KEYCODE_A:return Devices.K_A;case KeyEvent.KEYCODE_S:return Devices.K_S;case KeyEvent.KEYCODE_D:return Devices.K_D;
            case KeyEvent.KEYCODE_SPACE:return Devices.K_SPACE;case KeyEvent.KEYCODE_SHIFT_LEFT:case KeyEvent.KEYCODE_SHIFT_RIGHT:return Devices.K_SHIFT;case KeyEvent.KEYCODE_ESCAPE:return Devices.K_ESC;
            case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_NUMPAD_ENTER:return Devices.K_ENTER;case KeyEvent.KEYCODE_DEL:return Devices.K_BACKSPACE;case KeyEvent.KEYCODE_TAB:return Devices.K_TAB;
            case KeyEvent.KEYCODE_E:return Devices.K_E;case KeyEvent.KEYCODE_F:return Devices.K_F;case KeyEvent.KEYCODE_Q:return Devices.K_Q;case KeyEvent.KEYCODE_R:return Devices.K_R;
            case KeyEvent.KEYCODE_CTRL_LEFT:case KeyEvent.KEYCODE_CTRL_RIGHT:return Devices.K_CTRL;case KeyEvent.KEYCODE_Z:return Devices.K_Z;case KeyEvent.KEYCODE_Y:return Devices.K_Y;
            case KeyEvent.KEYCODE_FORWARD_DEL:return Devices.K_DELETE;default:return -1;}
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        boolean down=e.getAction()==KeyEvent.ACTION_DOWN;int code=e.getKeyCode();
        boolean fromPad=isPad(e.getSource())||KeyEvent.isGamepadButton(code);
        if(fromPad){int bit=padBit(code);if(bit!=0){dev.padConnected=true;if(down)dev.buttons|=bit;else dev.buttons&=~bit;dev.padActivity();return true;}}
        if(code==KeyEvent.KEYCODE_BACK)return super.dispatchKeyEvent(e);
        // arrow keys from a keyboard
        int arrow=code==KeyEvent.KEYCODE_DPAD_UP?Devices.K_UP:code==KeyEvent.KEYCODE_DPAD_DOWN?Devices.K_DOWN:code==KeyEvent.KEYCODE_DPAD_LEFT?Devices.K_LEFT:code==KeyEvent.KEYCODE_DPAD_RIGHT?Devices.K_RIGHT:-1;
        int k=arrow>=0?arrow:key(code);
        if(k>=0||e.getUnicodeChar()!=0){if(k>=0)dev.key(k,down);
            if(down&&layer.busy()){int u=e.getUnicodeChar();if(u>=32)dev.type((char)u);}
            return true;}
        return super.dispatchKeyEvent(e);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e){
        if(isPad(e.getSource())&&e.getAction()==MotionEvent.ACTION_MOVE){dev.padConnected=true;
            dev.lx=e.getAxisValue(MotionEvent.AXIS_X);dev.ly=e.getAxisValue(MotionEvent.AXIS_Y);dev.rx=e.getAxisValue(MotionEvent.AXIS_Z);dev.ry=e.getAxisValue(MotionEvent.AXIS_RZ);
            dev.lt=Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER),e.getAxisValue(MotionEvent.AXIS_BRAKE));dev.rt=Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER),e.getAxisValue(MotionEvent.AXIS_GAS));
            float hx=e.getAxisValue(MotionEvent.AXIS_HAT_X),hy=e.getAxisValue(MotionEvent.AXIS_HAT_Y);int hat=(hx<-.5f?Devices.LEFT:0)|(hx>.5f?Devices.RIGHT:0)|(hy<-.5f?Devices.UP:0)|(hy>.5f?Devices.DOWN:0);
            dev.buttons=(dev.buttons&~(Devices.LEFT|Devices.RIGHT|Devices.UP|Devices.DOWN))|hat|hatHeld();dev.padActivity();return true;}
        if(e.isFromSource(InputDevice.SOURCE_MOUSE)){
            if(e.getActionMasked()==MotionEvent.ACTION_HOVER_MOVE||e.getActionMasked()==MotionEvent.ACTION_HOVER_ENTER){dev.mouseMove(e.getX(),e.getY());return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_SCROLL){dev.scroll(e.getAxisValue(MotionEvent.AXIS_VSCROLL));return true;}}
        return super.dispatchGenericMotionEvent(e);
    }
    private int hatHeld(){return 0;}
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(e.isFromSource(InputDevice.SOURCE_MOUSE)&&!Pointer.dispatching){   // the mouse drives our cursor, which does the clicking
            int b=e.getButtonState();dev.mouseL=(b&MotionEvent.BUTTON_PRIMARY)!=0;dev.mouseR=(b&MotionEvent.BUTTON_SECONDARY)!=0;dev.mouseM=(b&MotionEvent.BUTTON_TERTIARY)!=0;
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){dev.mouseL=dev.mouseR=dev.mouseM=false;}
            dev.mouseMove(e.getX(),e.getY());return true;}
        dev.touched();return super.dispatchTouchEvent(e);
    }
    /** Relative mouse motion while it is captured for mouse look. */
    private void hookCapture(GLSurfaceView v){
        if(android.os.Build.VERSION.SDK_INT<26)return;
        v.setOnCapturedPointerListener((view,ev)->{
            if(ev.getActionMasked()==MotionEvent.ACTION_MOVE)dev.mouseDelta(ev.getX(),ev.getY());
            int b=ev.getButtonState();dev.mouseL=(b&MotionEvent.BUTTON_PRIMARY)!=0;dev.mouseR=(b&MotionEvent.BUTTON_SECONDARY)!=0;
            if(ev.getActionMasked()==MotionEvent.ACTION_SCROLL)dev.scroll(ev.getAxisValue(MotionEvent.AXIS_VSCROLL));
            return true;});
    }

    // ---------------------------------------------------------------- lifecycle
    @Override public void onBackPressed(){
        if(layer.back())return;
        if(story!=null){story.finish();return;}   // back skips the chapter
        if(overlay!=null){overlay.back();return;}
        if(view3d!=null){back3D();return;}
        if(game!=null){pauseGame();return;}
        if(editor!=null){editor.back();return;}
        if(menu!=null&&menu.back())return;
        super.onBackPressed();
    }
    @Override protected void onDestroy(){music.release();super.onDestroy();}
    @Override protected void onPause(){super.onPause();if(gl!=null)gl.onPause();if(game!=null&&overlay==null&&view3d==null)pauseGame();sfx.pause();music.pause();if(editor!=null)editor.autoSave();}
    @Override protected void onResume(){super.onResume();scanPads();if(gl!=null)gl.onResume();sfx.resume();music.resume();immersive();}
}
