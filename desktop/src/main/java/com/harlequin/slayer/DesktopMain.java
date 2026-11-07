package com.harlequin.slayer;

import android.content.Context;
import android.graphics.Canvas;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.view.View;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Slayer from Hell on Windows (and Linux): the same game, menus and editor as the Android app, in a GLFW window with
 * desktop OpenGL 3.3. Screens are the app's own views; their 2D drawing goes through Java2D into a UI layer that is
 * composited over the 3D frame. Input: mouse and keyboard, and Xbox controllers (any XInput / SDL-mapped pad), all
 * routed by {@link Controls} with the app's global cursor.
 *
 *   F11 toggles full screen.  --shots DIR  renders a few screens to DIR and exits (CI smoke test).
 */
public final class DesktopMain implements MenuView.Host,EditorView.Host,Edit3DView.Host,Controls.Shell {
    private long window;private int fbW=1600,fbH=900;private float scaleX=1,scaleY=1;
    private Context ctx;private Sfx sfx;private MapStore store;private Music music;private StoryView story;private final Devices dev=new Devices();private Overlay layer;private Controls controls;
    private final ArrayList<View> screens=new ArrayList<>();
    private MenuView menu,overlay;private EditorView editor;private Edit3DView view3d;private float[] cam3d;
    private GLSurfaceView gl;private Game game;private Hud hud;
    private int campaign=-1;private SectorMap playing;private boolean fromEditor;
    private boolean fullscreen;private int winX,winY,winW,winH;
    private double lastCX=Double.NaN,lastCY=Double.NaN;private boolean captured;

    public static void main(String[] args){
        String shots=null;for(int i=0;i<args.length-1;i++)if(args[i].equals("--shots"))shots=args[i+1];
        System.setProperty("java.awt.headless","true");
        new DesktopMain().run(shots);
    }

    // ---------------------------------------------------------------- window
    private void run(String shots){
        GLFWErrorCallback.createPrint(System.err).set();
        if(!glfwInit())throw new IllegalStateException("GLFW");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT,GLFW_TRUE);
        glfwWindowHint(GLFW_DEPTH_BITS,24);glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
        GLFWVidMode vm=glfwGetVideoMode(glfwGetPrimaryMonitor());
        int ww=1600,wh=900;if(vm!=null&&shots==null){ww=Math.min(1600,(int)(vm.width()*.85f));wh=ww*9/16;}
        window=glfwCreateWindow(ww,wh,"Slayer from Hell",0,0);
        if(window==0)throw new IllegalStateException("Couldn't open a window with OpenGL 3.3");
        if(vm!=null)glfwSetWindowPos(window,(vm.width()-ww)/2,(vm.height()-wh)/2);
        icon();
        glfwMakeContextCurrent(window);GL.createCapabilities();glfwSwapInterval(1);
        updateSize();
        // the app's density: lay the phone UI out as on a ~410dp-tall landscape phone, so it keeps its proportions
        float density=Math.max(1f,Math.round(Math.min(fbH/410f,fbW/860f)*4)/4f);
        File home=new File(System.getenv("APPDATA")!=null?System.getenv("APPDATA"):System.getProperty("user.home"),System.getenv("APPDATA")!=null?"SlayerFromHell":".slayerfromhell");
        ctx=new Context(home,density);
        sfx=new Sfx(ctx);store=new MapStore(ctx.getFilesDir());
        music=new Music();if(shots==null){music.prepare(MusicSynth.HELL);music.prepare(MusicSynth.TECH);}
        layer=new Overlay(ctx);controls=new Controls(dev,layer,this);controls.toggleFullscreen=this::toggleFullscreen;
        dev.last=Devices.SRC_MOUSE;
        callbacks();
        glfwSetInputMode(window,GLFW_CURSOR,GLFW_CURSOR_HIDDEN);   // the app draws its own cursor
        uiInit();
        showMenu(MenuView.MAIN);
        glfwShowWindow(window);
        if(shots!=null){shots(shots);return;}
        long last=System.nanoTime();
        while(!glfwWindowShouldClose(window)){
            long now=System.nanoTime();float dt=Math.min(.05f,(now-last)/1e9f);last=now;
            frame(dt);
        }
        if(editor!=null)editor.autoSave();
        sfx.pause();glfwDestroyWindow(window);glfwTerminate();System.exit(0);
    }
    private void frame(float dt){
        glfwPollEvents();pollPad();updateSize();
        Handler.runPending();
        controls.frame(dt);
        if(gl!=null&&screens.contains(gl)){gl.frame(fbW,fbH);}
        else{GL11C.glViewport(0,0,fbW,fbH);GL11C.glClearColor(0,0,0,1);GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT|GL11C.GL_DEPTH_BUFFER_BIT);}
        drawUi();
        if(shotPath!=null){save(shotPath);shotPath=null;}
        glfwSwapBuffers(window);
    }
    private void updateSize(){
        try(MemoryStack s=MemoryStack.stackPush()){IntBuffer w=s.mallocInt(1),h=s.mallocInt(1),ww=s.mallocInt(1),wh=s.mallocInt(1);
            glfwGetFramebufferSize(window,w,h);glfwGetWindowSize(window,ww,wh);
            fbW=Math.max(1,w.get(0));fbH=Math.max(1,h.get(0));scaleX=fbW/(float)Math.max(1,ww.get(0));scaleY=fbH/(float)Math.max(1,wh.get(0));}
    }
    private void toggleFullscreen(){
        long mon=glfwGetPrimaryMonitor();GLFWVidMode vm=glfwGetVideoMode(mon);if(vm==null)return;
        if(!fullscreen){try(MemoryStack s=MemoryStack.stackPush()){IntBuffer a=s.mallocInt(1),b=s.mallocInt(1);glfwGetWindowPos(window,a,b);winX=a.get(0);winY=b.get(0);glfwGetWindowSize(window,a,b);winW=a.get(0);winH=b.get(0);}
            glfwSetWindowMonitor(window,mon,0,0,vm.width(),vm.height(),vm.refreshRate());}
        else glfwSetWindowMonitor(window,0,winX,winY,winW,winH,0);
        fullscreen=!fullscreen;glfwSwapInterval(1);
    }
    private void icon(){
        try(java.io.InputStream in=DesktopMain.class.getResourceAsStream("/icon.png")){if(in==null)return;
            BufferedImage img=javax.imageio.ImageIO.read(in);int w=img.getWidth(),h=img.getHeight();ByteBuffer px=org.lwjgl.BufferUtils.createByteBuffer(w*h*4);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=img.getRGB(x,y);px.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>>24));}px.flip();
            GLFWImage.Buffer imgs=GLFWImage.malloc(1);imgs.get(0).set(w,h,px);glfwSetWindowIcon(window,imgs);imgs.free();
        }catch(Exception ignored){}
    }

    // ---------------------------------------------------------------- input
    private void callbacks(){
        glfwSetKeyCallback(window,(w,key,sc,action,mods)->{if(action==GLFW_REPEAT){if(key==GLFW_KEY_BACKSPACE&&layer.busy())layer.backspace();return;}
            int k=mapKey(key);if(k>=0)dev.key(k,action==GLFW_PRESS);});
        glfwSetCharCallback(window,(w,cp)->{if(layer.busy()&&cp>=32&&cp<0x10000)dev.type((char)cp);});
        glfwSetCursorPosCallback(window,(w,x,y)->{
            if(captured){if(!Double.isNaN(lastCX))dev.mouseDelta((float)(x-lastCX),(float)(y-lastCY));lastCX=x;lastCY=y;}
            else{dev.mouseMove((float)x*scaleX,(float)y*scaleY);lastCX=x;lastCY=y;}});
        glfwSetMouseButtonCallback(window,(w,b,action,mods)->{boolean d=action==GLFW_PRESS;
            if(b==GLFW_MOUSE_BUTTON_LEFT)dev.mouseL=d;else if(b==GLFW_MOUSE_BUTTON_RIGHT)dev.mouseR=d;else if(b==GLFW_MOUSE_BUTTON_MIDDLE)dev.mouseM=d;
            if(!captured&&!Double.isNaN(lastCX))dev.mouseMove((float)lastCX*scaleX,(float)lastCY*scaleY);else dev.last=Devices.SRC_MOUSE;});
        glfwSetScrollCallback(window,(w,x,y)->dev.scroll((float)y));
        glfwSetWindowCloseCallback(window,w->{if(editor!=null)editor.autoSave();});
    }
    private static int mapKey(int key){
        switch(key){case GLFW_KEY_W:return Devices.K_W;case GLFW_KEY_A:return Devices.K_A;case GLFW_KEY_S:return Devices.K_S;case GLFW_KEY_D:return Devices.K_D;
            case GLFW_KEY_SPACE:return Devices.K_SPACE;case GLFW_KEY_LEFT_SHIFT:case GLFW_KEY_RIGHT_SHIFT:return Devices.K_SHIFT;case GLFW_KEY_ESCAPE:return Devices.K_ESC;
            case GLFW_KEY_ENTER:case GLFW_KEY_KP_ENTER:return Devices.K_ENTER;case GLFW_KEY_BACKSPACE:return Devices.K_BACKSPACE;case GLFW_KEY_TAB:return Devices.K_TAB;
            case GLFW_KEY_E:return Devices.K_E;case GLFW_KEY_F:return Devices.K_F;case GLFW_KEY_Q:return Devices.K_Q;case GLFW_KEY_R:return Devices.K_R;
            case GLFW_KEY_UP:return Devices.K_UP;case GLFW_KEY_DOWN:return Devices.K_DOWN;case GLFW_KEY_LEFT:return Devices.K_LEFT;case GLFW_KEY_RIGHT:return Devices.K_RIGHT;
            case GLFW_KEY_LEFT_CONTROL:case GLFW_KEY_RIGHT_CONTROL:return Devices.K_CTRL;case GLFW_KEY_Z:return Devices.K_Z;case GLFW_KEY_Y:return Devices.K_Y;
            case GLFW_KEY_F11:return Devices.K_F11;case GLFW_KEY_DELETE:return Devices.K_DELETE;case GLFW_KEY_P:return Devices.K_P;case GLFW_KEY_H:case GLFW_KEY_HOME:return Devices.K_H;
            case GLFW_KEY_LEFT_BRACKET:return Devices.K_LBR;case GLFW_KEY_RIGHT_BRACKET:return Devices.K_RBR;case GLFW_KEY_PAGE_UP:return Devices.K_PGUP;case GLFW_KEY_PAGE_DOWN:return Devices.K_PGDN;case GLFW_KEY_C:return Devices.K_C;default:return -1;}
    }
    private final GLFWGamepadState pad=GLFWGamepadState.create();
    /** The first connected gamepad (Xbox controllers via XInput, others via SDL's mapping database). */
    private void pollPad(){
        int found=-1;for(int j=GLFW_JOYSTICK_1;j<=GLFW_JOYSTICK_LAST;j++)if(glfwJoystickIsGamepad(j)){found=j;break;}
        dev.padConnected=found>=0;if(found<0){dev.buttons=0;dev.lx=dev.ly=dev.rx=dev.ry=dev.lt=dev.rt=0;return;}
        if(!glfwGetGamepadState(found,pad))return;
        dev.lx=pad.axes(GLFW_GAMEPAD_AXIS_LEFT_X);dev.ly=pad.axes(GLFW_GAMEPAD_AXIS_LEFT_Y);dev.rx=pad.axes(GLFW_GAMEPAD_AXIS_RIGHT_X);dev.ry=pad.axes(GLFW_GAMEPAD_AXIS_RIGHT_Y);
        dev.lt=(pad.axes(GLFW_GAMEPAD_AXIS_LEFT_TRIGGER)+1)*.5f;dev.rt=(pad.axes(GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER)+1)*.5f;
        int[][] map={{GLFW_GAMEPAD_BUTTON_A,Devices.A},{GLFW_GAMEPAD_BUTTON_B,Devices.B},{GLFW_GAMEPAD_BUTTON_X,Devices.X},{GLFW_GAMEPAD_BUTTON_Y,Devices.Y},
            {GLFW_GAMEPAD_BUTTON_LEFT_BUMPER,Devices.LB},{GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER,Devices.RB},{GLFW_GAMEPAD_BUTTON_BACK,Devices.BACK},{GLFW_GAMEPAD_BUTTON_START,Devices.START},
            {GLFW_GAMEPAD_BUTTON_LEFT_THUMB,Devices.LS},{GLFW_GAMEPAD_BUTTON_RIGHT_THUMB,Devices.RS},{GLFW_GAMEPAD_BUTTON_DPAD_UP,Devices.UP},{GLFW_GAMEPAD_BUTTON_DPAD_DOWN,Devices.DOWN},
            {GLFW_GAMEPAD_BUTTON_DPAD_LEFT,Devices.LEFT},{GLFW_GAMEPAD_BUTTON_DPAD_RIGHT,Devices.RIGHT}};
        int b=0;for(int[] m:map)if(pad.buttons(m[0])==GLFW_PRESS)b|=m[1];dev.buttons=b;dev.padActivity();
    }

    // ---------------------------------------------------------------- the UI layer (Java2D, composited over the 3D frame)
    private BufferedImage ui;private int uiTex,uiProg,uiVao;private IntBuffer uiBuf;
    private void uiInit(){
        String vs="#version 330 core\nconst vec2 P[4]=vec2[](vec2(-1,-1),vec2(1,-1),vec2(-1,1),vec2(1,1));out vec2 vUv;\n"+
            "void main(){vec2 p=P[gl_VertexID];vUv=vec2(p.x*.5+.5,.5-p.y*.5);gl_Position=vec4(p,0,1);}\n";
        String fs="#version 330 core\nin vec2 vUv;uniform sampler2D uT;out vec4 c;void main(){c=texture(uT,vUv);}\n";
        uiProg=Gfx.program(vs,fs);uiVao=GL30C.glGenVertexArrays();uiTex=GL11C.glGenTextures();
    }
    private void drawUi(){
        if(ui==null||ui.getWidth()!=fbW||ui.getHeight()!=fbH){ui=new BufferedImage(fbW,fbH,BufferedImage.TYPE_INT_ARGB_PRE);uiBuf=org.lwjgl.BufferUtils.createIntBuffer(fbW*fbH);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,uiTex);GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D,0,GL11C.GL_RGBA8,fbW,fbH,0,GL12C.GL_BGRA,GL12C.GL_UNSIGNED_INT_8_8_8_8_REV,(IntBuffer)null);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MIN_FILTER,GL11C.GL_NEAREST);GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MAG_FILTER,GL11C.GL_NEAREST);}
        int[] px=((DataBufferInt)ui.getRaster().getDataBuffer()).getData();java.util.Arrays.fill(px,0);
        Graphics2D g=ui.createGraphics();Canvas c=new Canvas(g);
        for(View v:new ArrayList<>(screens)){if(v instanceof GLSurfaceView)continue;v.layout(fbW,fbH);int s=c.save();v.draw(c);c.restoreToCount(s);}
        layer.layout(fbW,fbH);layer.draw(c);g.dispose();
        uiBuf.clear();uiBuf.put(px).flip();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER,0);GL11C.glViewport(0,0,fbW,fbH);
        GL11C.glDisable(GL11C.GL_DEPTH_TEST);GL11C.glEnable(GL11C.GL_BLEND);GL11C.glBlendFunc(GL11C.GL_ONE,GL11C.GL_ONE_MINUS_SRC_ALPHA);
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,uiTex);
        GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D,0,0,0,fbW,fbH,GL12C.GL_BGRA,GL12C.GL_UNSIGNED_INT_8_8_8_8_REV,uiBuf);
        GL20C.glUseProgram(uiProg);GL20C.glUniform1i(GL20C.glGetUniformLocation(uiProg,"uT"),0);GL30C.glBindVertexArray(uiVao);
        GL11C.glDrawArrays(GL11C.GL_TRIANGLE_STRIP,0,4);
        GL11C.glDisable(GL11C.GL_BLEND);GL11C.glEnable(GL11C.GL_DEPTH_TEST);GL30C.glBindVertexArray(0);
    }

    // ---------------------------------------------------------------- screens (as MainActivity)
    private void clearScreens(){screens.clear();}
    private void addScreen(View v){screens.add(v);}
    private void showMenu(int page){stopGame();clearScreens();editor=null;story=null;music.stop();menu=new MenuView(ctx,this,store);addScreen(menu);menu.show(page);}
    private void stopGame(){if(gl!=null)gl=null;game=null;hud=null;overlay=null;sfx.engine(0,1);}
    private void startGame(SectorMap m){
        stopGame();clearScreens();playing=m.copy();view3d=null;
        game=new Game(ctx,m);game.sfx=sfx;sfx.restartLoops();
        music.play(Music.themeOf(m));game.onCombat=c->music.combat(c);
        hud=new Hud(ctx,game);game.hud=hud;hud.onPause=()->new Handler().post(this::pauseGame);
        game.listener=(k,t,s)->new Handler().post(()->levelComplete(k,t,s));
        gl=new GLSurfaceView(ctx);gl.setRenderer(game);
        addScreen(gl);addScreen(hud);
    }
    private void showOverlay(int page){
        if(game==null)return;
        if(overlay!=null)screens.remove(overlay);
        overlay=new MenuView(ctx,this,store);overlay.fromEditor=fromEditor;addScreen(overlay);
        if(page==MenuView.COMPLETE){overlay.hasNext=campaign>=0;overlay.finale=campaign+1>=Maps.count();}
        overlay.show(page);
    }
    private void pauseGame(){if(game==null||overlay!=null)return;game.paused=true;sfx.engine(0,1);showOverlay(MenuView.PAUSE);}
    private void levelComplete(int kills,int total,float seconds){
        if(game==null)return;game.paused=true;sfx.engine(0,1);music.combat(false);
        if(campaign>=0)MenuView.unlock(ctx,campaign+1);
        showOverlay(MenuView.COMPLETE);
        overlay.kills=kills;overlay.total=total;overlay.seconds=seconds;overlay.doneName=playing.name;
    }
    @Override public void playCampaign(int i){campaign=i;fromEditor=false;SectorMap m=Maps.campaign(i);story(Story.before(i),Music.themeOf(m),()->startGame(m));}
    private void story(Story.Chapter ch,int theme,Runnable then){
        stopGame();clearScreens();menu=null;editor=null;music.play(theme);
        story=new StoryView(ctx,ch,()->new Handler().post(()->{story=null;then.run();}));addScreen(story);
    }
    @Override public void playCustom(String id){try{campaign=-1;fromEditor=false;startGame(store.load(id));}catch(Exception e){layer.toast("Couldn't open that level: "+e.getMessage());}}
    @Override public void editMap(String id,SectorMap from){
        SectorMap m=from;
        if(m==null)try{m=store.load(id);}catch(Exception e){layer.toast("Couldn't open that level: "+e.getMessage());return;}
        stopGame();clearScreens();menu=null;music.stop();
        editor=new EditorView(ctx,m,id,store,this);if(id==null)editor.autoSave();
        addScreen(editor);
    }
    @Override public void quit(){glfwSetWindowShouldClose(window,true);}
    @Override public void resume(){if(game==null)return;if(overlay!=null){screens.remove(overlay);overlay=null;}game.paused=false;}
    @Override public void restart(){SectorMap m=playing;if(m!=null)startGame(m);}
    @Override public void leaveGame(){
        if(fromEditor&&editor!=null){stopGame();clearScreens();music.stop();addScreen(editor);return;}
        showMenu(campaign>=0?MenuView.CAMPAIGN:MenuView.CUSTOM);
    }
    @Override public void nextLevel(){
        if(campaign<0){showMenu(MenuView.MAIN);return;}
        MenuView.unlock(ctx,campaign+1);
        if(campaign+1<Maps.count())playCampaign(campaign+1);
        else{campaign=-1;story(Story.epilogue(),MusicSynth.HELL,()->showMenu(MenuView.CAMPAIGN));}
    }
    @Override public void exitEditor(){editor=null;showMenu(MenuView.CUSTOM);}
    @Override public void testPlay(SectorMap m){EditorView keep=editor;campaign=-1;fromEditor=true;startGame(m);editor=keep;}
    @Override public void open3D(){
        if(editor==null)return;
        stopGame();clearScreens();music.stop();
        game=new Game(ctx,editor.map().copy());game.editMode=true;game.sfx=sfx;
        if(cam3d!=null)game.setEditCamera(cam3d[0],cam3d[1],cam3d[2],cam3d[3],cam3d[4]);
        gl=new GLSurfaceView(ctx);gl.setRenderer(game);
        view3d=new Edit3DView(ctx,game,editor.ed,this);
        addScreen(gl);addScreen(view3d);
    }
    @Override public void back3D(){if(game!=null)cam3d=game.editCamera();view3d=null;stopGame();clearScreens();if(editor!=null)addScreen(editor);}
    @Override public void changed3D(){if(editor!=null)editor.autoSave();}
    @Override public void playHere(SectorMap m){if(game!=null)cam3d=game.editCamera();view3d=null;EditorView keep=editor;campaign=-1;fromEditor=true;startGame(m);editor=keep;}

    // ---------------------------------------------------------------- Controls.Shell
    @Override public View screen(){return screens.isEmpty()?layer:screens.get(screens.size()-1);}
    @Override public Hud playing(){return game!=null&&hud!=null&&overlay==null&&view3d==null&&!game.paused?hud:null;}
    @Override public void back(){
        if(layer.back())return;
        if(story!=null){story.finish();return;}
        if(overlay!=null){overlay.back();return;}
        if(view3d!=null){back3D();return;}
        if(game!=null){pauseGame();return;}
        if(editor!=null){editor.back();return;}
        if(menu!=null&&menu.back())return;
        layer.confirm("Quit Slayer from Hell?","Back to the desktop.","Quit",true,this::quit);
    }
    @Override public void pause(){pauseGame();}
    @Override public void captureMouse(boolean on){
        captured=on;lastCX=Double.NaN;
        glfwSetInputMode(window,GLFW_CURSOR,on?GLFW_CURSOR_DISABLED:GLFW_CURSOR_HIDDEN);
        if(on&&glfwRawMouseMotionSupported())glfwSetInputMode(window,GLFW_RAW_MOUSE_MOTION,GLFW_TRUE);
    }

    // ---------------------------------------------------------------- CI smoke test: render screens to PNGs
    private void shots(String dir){
        new File(dir).mkdirs();
        try{
            for(int i=0;i<20;i++)frame(1/60f);
            dev.mouseMove(fbW*.32f,fbH*.36f);for(int i=0;i<10;i++)frame(1/60f);shot(dir+"/1-main-menu.png");
            showMenu(MenuView.SETTINGS);for(int i=0;i<10;i++)frame(1/60f);shot(dir+"/2-settings.png");
            editMap(null,Maps.campaign(0));dev.mouseMove(fbW*.45f,fbH*.5f);for(int i=0;i<10;i++)frame(1/60f);shot(dir+"/3-editor.png");
            layer.prompt("Level name","Hell's Gate",s->{});for(int i=0;i<10;i++)frame(1/60f);shot(dir+"/4-dialog.png");layer.dismiss();
            open3D();for(int i=0;i<240;i++)frame(1/60f);shot(dir+"/5-editor-3d.png");
            dev.mouseMove(fbW*.58f,fbH*.62f);frame(1/60f);dev.mouseL=true;frame(1/60f);dev.mouseL=false;for(int i=0;i<20;i++)frame(1/60f);dev.key(Devices.K_UP,true);frame(1/60f);dev.key(Devices.K_UP,false);for(int i=0;i<30;i++)frame(1/60f);shot(dir+"/5c-editor-3d-selected.png");
            dev.mouseR=true;dev.key(Devices.K_W,true);for(int i=0;i<40;i++)frame(1/60f);shot(dir+"/5d-editor-3d-look.png");dev.key(Devices.K_W,false);dev.mouseR=false;dev.mouseMove(fbW*.5f,fbH*.5f);for(int i=0;i<5;i++)frame(1/60f);
            back3D();
            {SectorMap m=editor.map();for(int k=0;k<m.sectors.size();k++){SectorMap.Sector sc=m.sectors.get(k);sc.wallTex=Tex.FIRST_LIB+(k*7)%50;sc.floorTex=Tex.FIRST_LIB+50+(k*11)%50;sc.ceilTex=Tex.FIRST_LIB+(k*13+3)%50;}}
            open3D();for(int i=0;i<400;i++)frame(1/60f);shot(dir+"/5b-new-textures-3d.png");
            showMenu(MenuView.CAMPAIGN);for(int i=0;i<10;i++)frame(1/60f);shot(dir+"/2b-campaign.png");
            playCampaign(1);for(int i=0;i<150;i++)frame(1/60f);shot(dir+"/2c-story.png");
            story.advance();for(int i=0;i<5;i++)frame(1/60f);shot(dir+"/2d-story-read.png");story.finish();for(int i=0;i<3;i++)frame(1/60f);
            for(int i=0;i<600;i++){frame(1/60f);if(game!=null&&game.loading==null&&i>200)break;}
            game.debugPlace(0,63.1f,(float)Math.PI/2);for(int i=0;i<60;i++)frame(1/60f);shot(dir+"/6b-red-door.png");
            playCampaign(0);story.finish();for(int i=0;i<3;i++)frame(1/60f);
            for(int i=0;i<600;i++){frame(1/60f);if(game!=null&&game.loading==null&&i>200)break;}
            game.debugPlace(4,37.6f,(float)Math.PI/2);for(int i=0;i<20;i++)frame(1/60f);shot(dir+"/6c-door-shut.png");game.debugPlace(4,38.9f,(float)Math.PI/2);for(int i=0;i<36;i++)frame(1/60f);shot(dir+"/6d-door-opening.png");
            game.debugPlace(4,1.5f,(float)Math.PI/2);dev.key(Devices.K_W,true);for(int i=0;i<600;i++){frame(1/60f);if(game!=null&&game.loading==null&&i>200)break;}
            for(int i=0;i<90;i++)frame(1/60f);dev.key(Devices.K_W,false);shot(dir+"/6-game.png");
            pauseGame();for(int i=0;i<10;i++)frame(1/60f);shot(dir+"/7-paused.png");
            levelComplete(12,13,312);for(int i=0;i<10;i++)frame(1/60f);shot(dir+"/8-complete.png");
            System.out.println("shots saved to "+dir);
        }catch(Throwable t){t.printStackTrace();System.exit(1);}
        glfwDestroyWindow(window);glfwTerminate();System.exit(0);
    }
    private String shotPath;
    private void shot(String path){shotPath=path;frame(1/60f);}
    private void save(String path){
        ByteBuffer b=org.lwjgl.BufferUtils.createByteBuffer(fbW*fbH*4);GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER,0);
        GL11C.glReadPixels(0,0,fbW,fbH,GL11C.GL_RGBA,GL11C.GL_UNSIGNED_BYTE,b);
        BufferedImage img=new BufferedImage(fbW,fbH,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<fbH;y++)for(int x=0;x<fbW;x++){int i=((fbH-1-y)*fbW+x)*4;img.setRGB(x,y,((b.get(i)&255)<<16)|((b.get(i+1)&255)<<8)|(b.get(i+2)&255));}
        try{javax.imageio.ImageIO.write(img,"png",new File(path));}catch(Exception e){throw new RuntimeException(e);}
    }
}
