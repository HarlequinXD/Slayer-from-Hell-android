package com.harlequin.slayer;

import android.view.View;

/**
 * Routes the controller, keyboard and mouse once a frame (on the UI thread):
 *
 *  Playing   left stick / WASD move, right stick / mouse look, RT / left button fire, LT / RB / right button / F
 *            the chainsaw (and glory kills), A / Space jump, Start / Esc pause. The touch controls hide while a
 *            controller or keyboard is in use, and come back at the first touch.
 *  Menus     the global cursor: left stick or mouse moves it, A / left button clicks (hold to drag), the d-pad
 *            jumps between buttons, B / Esc goes back, right stick / wheel scrolls.
 *  Editor    as menus, plus: hovering the map moves the map cursor, A / click applies, the d-pad steps the map
 *            cursor, right stick / right- or middle-drag pans, bumpers / wheel zoom.
 *  3D view   two modes. Hold RT / the right mouse button to look: the cursor hides, the left stick / WASD fly,
 *            the right stick / mouse look, the d-pad / E Q (Space C) go up and down in fly mode, A / left click picks
 *            the surface under the crosshair. Let go and it's the cursor: click (A) a surface to select it, the
 *            d-pad / arrows raise and lower it (a wall: its ceiling) and pick textures, X / Enter paints.
 */
final class Controls {
    /** What the platform's app tells the router. */
    interface Shell {
        /** The screen under the overlay that receives the cursor's clicks. */
        View screen();
        /** The in-game HUD while a level is being played (not paused), else null. */
        Hud playing();
        /** Esc / B / the back button. */
        void back();
        /** Start / Esc while playing. */
        void pause();
        /** Hide the mouse and take relative motion (mouse look), or give it back. */
        void captureMouse(boolean on);
    }
    final Devices dev;final Overlay overlay;final Shell shell;
    private int prevButtons;private boolean prevL,prevR,prevM,prevKeysEsc,prevEnter,prevBack,prevF11;
    private boolean drovePad,droveKeys,captured;private float lastMX=Float.NaN,lastMY=Float.NaN;
    Runnable toggleFullscreen;
    /** The HUD shows controller button names (else keyboard and mouse ones). */
    static volatile boolean padHint;

    Controls(Devices d,Overlay o,Shell s){dev=d;overlay=o;shell=s;}

    void frame(float dt){
        int btn=dev.buttons,pressedB=btn&~prevButtons;prevButtons=btn;
        boolean esc=dev.keys[Devices.K_ESC],escP=esc&&!prevKeysEsc;prevKeysEsc=esc;
        boolean ent=dev.keys[Devices.K_ENTER],entP=ent&&!prevEnter;prevEnter=ent;
        boolean bs=dev.keys[Devices.K_BACKSPACE],bsP=bs&&!prevBack;prevBack=bs;
        boolean f11=dev.keys[Devices.K_F11];if(f11&&!prevF11&&toggleFullscreen!=null)toggleFullscreen.run();prevF11=f11;
        Hud hud=overlay.busy()?null:shell.playing();
        Pointer ptr=overlay.pointer;
        int src=dev.last;
        Hud.touchControls=src==Devices.SRC_TOUCH;if(src==Devices.SRC_PAD)padHint=true;else if(src!=Devices.SRC_TOUCH)padHint=false;
        if(hud!=null){game(hud,dt,pressedB,escP);ptr.visible=false;ptr.cancel();overlay.invalidate();return;}
        if(captured){shell.captureMouse(false);captured=false;}
        View screen=overlay.busy()?overlay:shell.screen();
        Pointer.Aware aw=screen instanceof Pointer.Aware?(Pointer.Aware)screen:null;
        float w=overlay.getWidth(),h=overlay.getHeight();
        if(Float.isNaN(ptr.x)){ptr.place(w*.5f,h*.5f);}
        ptr.visible=src==Devices.SRC_MOUSE||src==Devices.SRC_PAD||src==Devices.SRC_KEYS;
        // keyboard text into dialogs
        String typed=dev.takeTyped();if(overlay.busy()){overlay.typed(typed);if(bsP)overlay.backspace();if(entP)overlay.enter();}
        // back
        if(escP||(pressedB&Devices.B)!=0){ptr.cancel();if(!overlay.back())shell.back();}
        boolean cam3d=aw!=null&&aw.pointerCamera();Edit3DView v3=cam3d?(Edit3DView)screen:null;
        // the 3D view's look mode: right trigger or right mouse button held. The cursor hides; the sticks or WASD
        // and the mouse fly and look. Released, it's the cursor again.
        boolean lookPad=cam3d&&dev.padConnected&&dev.rt>.5f,lookMouse=cam3d&&dev.mouseR&&!overlay.busy();
        boolean look=lookPad||lookMouse;
        if(lookMouse&&!captured){shell.captureMouse(true);captured=true;dev.takeMouseDelta();}
        if(!lookMouse&&captured){shell.captureMouse(false);captured=false;lastMX=lastMY=Float.NaN;}
        if(v3!=null){if(look!=wasLook){v3.setLooking(look);if(!look)v3.padCamera(0,0,0,0,0,dt);}wasLook=look;}else wasLook=false;
        if(look){ptr.visible=false;ptr.cancel();
            boolean[] k=dev.keys;float mx=0,my=0,lx=0,ly=0,ud=0;
            if(lookPad){float[] ls=Devices.stick(dev.lx,dev.ly,.16f,1.9f),rs=Devices.stick(dev.rx,dev.ry,.16f,1.6f);mx=ls[0];my=-ls[1];lx=rs[0];ly=rs[1];
                ud=((btn&Devices.UP)!=0?1:0)-((btn&Devices.DOWN)!=0?1:0);
                if((pressedB&Devices.A)!=0)v3.pickCentre(false);if((pressedB&Devices.X)!=0)v3.shortcut('\n');}
            mx+=(k[Devices.K_D]?1:0)-(k[Devices.K_A]?1:0);my+=(k[Devices.K_W]?1:0)-(k[Devices.K_S]?1:0);
            ud+=(k[Devices.K_E]||k[Devices.K_SPACE]?1:0)-(k[Devices.K_Q]||k[Devices.K_C]?1:0);
            float ml=(float)Math.hypot(mx,my);if(ml>1){mx/=ml;my/=ml;}if(k[Devices.K_SHIFT]){mx*=2;my*=2;}
            v3.padCamera(mx,my,lx,ly,Math.max(-1,Math.min(1,ud)),dt);
            if(lookMouse){float[] md=dev.takeMouseDelta();if(md[0]!=0||md[1]!=0)v3.mouseLook(md[0],md[1]);
                if(dev.mouseL&&!prevL)v3.pickCentre(false);}
            prevL=dev.mouseL;prevR=dev.mouseR;prevM=dev.mouseM;dev.takeWheel();
            prevArrows=0;
            overlay.invalidate();screen.invalidate();return;}
        boolean ctrl=dev.keys[Devices.K_CTRL];int sc=(ctrl&&dev.keys[Devices.K_Z]?1:0)|(ctrl&&dev.keys[Devices.K_Y]?2:0)|(ctrl&&dev.keys[Devices.K_S]?4:0);
        if(aw!=null&&!overlay.busy()){int p=sc&~prevShort;if((p&1)!=0)aw.pointerShortcut('z');if((p&2)!=0)aw.pointerShortcut('y');if((p&4)!=0)aw.pointerShortcut('s');}prevShort=sc;
        // the 3D view's keys and buttons in cursor mode
        if(v3!=null&&!overlay.busy()){boolean[] k=dev.keys;
            int ks=(k[Devices.K_F]?1:0)|(k[Devices.K_P]?2:0)|(k[Devices.K_H]?4:0)|(k[Devices.K_LBR]?8:0)|(k[Devices.K_RBR]?16:0)|(k[Devices.K_PGUP]?32:0)|(k[Devices.K_PGDN]?64:0)|(k[Devices.K_DELETE]?128:0)|(k[Devices.K_ENTER]?256:0);
            int kp=ctrl?0:ks&~prevKeys3d;prevKeys3d=ks;
            char[] map={'f','p','h','[',']','+','-','x','\n'};for(int i=0;i<map.length;i++)if((kp&(1<<i))!=0)v3.shortcut(map[i]);
            if(dev.padConnected){if((pressedB&Devices.X)!=0)v3.shortcut('\n');if((pressedB&Devices.Y)!=0)v3.shortcut('f');
                if((pressedB&Devices.LB)!=0)v3.shortcut('{');if((pressedB&Devices.RB)!=0)v3.shortcut('}');}
            entP=false;}   // Enter paints here instead of clicking
        // ---------------------------------------------------------------- mouse
        float mx=dev.mouseX,my=dev.mouseY;
        if(src==Devices.SRC_MOUSE&&!Float.isNaN(mx)){
            if(mx!=lastMX||my!=lastMY){float ddx=Float.isNaN(lastMX)?0:mx-lastMX,ddy=Float.isNaN(lastMY)?0:my-lastMY;
                if((dev.mouseR||dev.mouseM)&&aw!=null&&!cam3d)aw.pointerPan(-ddx,-ddy);
                else ptr.place(mx,my);
                if(ptr.down)ptr.drag();}
            if(!(dev.mouseR||dev.mouseM))ptr.place(mx,my);
            if(dev.mouseL&&!prevL)ptr.press(screen);if(!dev.mouseL&&prevL)ptr.release();
            float wh=dev.takeWheel();if(wh!=0&&aw!=null)aw.pointerZoom(wh,ptr.x,ptr.y);
        }
        lastMX=mx;lastMY=my;prevL=dev.mouseL;prevR=dev.mouseR;prevM=dev.mouseM;
        // ---------------------------------------------------------------- controller
        if(dev.padConnected&&src==Devices.SRC_PAD){
            float[] ls=Devices.stick(dev.lx,dev.ly,.16f,1.9f),rs=Devices.stick(dev.rx,dev.ry,.16f,1.6f);
            ptr.stick(ls[0],ls[1],dt,w,h);if(ptr.down)ptr.drag();
            if(aw!=null&&(rs[0]!=0||rs[1]!=0))aw.pointerPan(rs[0]*dt*900*ptr.dp/2.6f,rs[1]*dt*900*ptr.dp/2.6f);
            if(aw!=null&&!cam3d){if((pressedB&Devices.RB)!=0)aw.pointerZoom(1,ptr.x,ptr.y);if((pressedB&Devices.LB)!=0)aw.pointerZoom(-1,ptr.x,ptr.y);}
            if((pressedB&Devices.A)!=0)ptr.press(screen);if((btn&Devices.A)==0&&ptr.down&&src==Devices.SRC_PAD)ptr.release();
            if((pressedB&Devices.START)!=0&&!overlay.back())shell.back();
        }
        // ---------------------------------------------------------------- d-pad / arrow keys
        int ddx=0,ddy=0;
        int arrows=(dev.keys[Devices.K_LEFT]?Devices.LEFT:0)|(dev.keys[Devices.K_RIGHT]?Devices.RIGHT:0)|(dev.keys[Devices.K_UP]?Devices.UP:0)|(dev.keys[Devices.K_DOWN]?Devices.DOWN:0)|(dev.keys[Devices.K_TAB]?Devices.RIGHT:0);
        pressedB|=arrows&~prevArrows;prevArrows=arrows;
        if((pressedB&Devices.LEFT)!=0)ddx=-1;else if((pressedB&Devices.RIGHT)!=0)ddx=1;else if((pressedB&Devices.UP)!=0)ddy=-1;else if((pressedB&Devices.DOWN)!=0)ddy=1;
        if(ddx!=0||ddy!=0){if(aw==null||!aw.pointerDpad(ddx,ddy))ptr.dpadJump(ddx,ddy,aw!=null?aw.pointerUi():null);}
        if(src==Devices.SRC_KEYS&&entP&&!overlay.busy()){ptr.press(screen);ptr.release();}
        ptr.update(dt,aw);
        if(aw!=null&&ptr.visible&&!overlay.busy())aw.pointerHover(ptr.x,ptr.y);
        overlay.invalidate();if(ptr.visible)screen.invalidate();
    }
    private static Edit3DView v3(View v){return (Edit3DView)v;}

    /** In play: the pad and keyboard/mouse feed the HUD's input alongside the touch controls. */
    private void game(Hud hud,float dt,int pressedB,boolean escP){
        Hud.Input in=hud.in;boolean[] k=dev.keys;
        boolean pad=dev.padConnected&&dev.last==Devices.SRC_PAD;
        boolean kb=dev.last==Devices.SRC_KEYS||dev.last==Devices.SRC_MOUSE;
        // mouse look: capture the mouse while playing with it
        if(kb&&!captured){shell.captureMouse(true);captured=true;dev.takeMouseDelta();}
        if(!kb&&captured){shell.captureMouse(false);captured=false;}
        float mvx=0,mvy=0,lookX=0,lookY=0;boolean fire=false,saw=false,jump=false;
        if(dev.padConnected){
            float[] ls=Devices.stick(dev.lx,dev.ly,.18f,1.4f),rs=Devices.stick(dev.rx,dev.ry,.14f,2.1f);
            mvx+=ls[0];mvy+=-ls[1];
            lookX+=rs[0]*dt*520;lookY+=rs[1]*dt*360;   // dp-equivalents per second at full tilt (about 165 and 115 degrees/s at the default speed)
            fire|=dev.rt>.35f;saw|=dev.lt>.35f||(dev.buttons&Devices.RB)!=0;jump|=(dev.buttons&Devices.A)!=0;
            if((pressedB&Devices.A)!=0)in.pressJump();
            if((pressedB&(Devices.START|Devices.BACK))!=0){shell.pause();return;}
        }
        mvx+=(k[Devices.K_D]?1:0)-(k[Devices.K_A]?1:0);mvy+=(k[Devices.K_W]?1:0)-(k[Devices.K_S]?1:0);
        if(!k[Devices.K_SHIFT]&&(k[Devices.K_W]||k[Devices.K_A]||k[Devices.K_S]||k[Devices.K_D])){float l=(float)Math.hypot(mvx,mvy);if(l>0){mvx/=l;mvy/=l;}}   // Shift walks
        else if(k[Devices.K_SHIFT]){mvx*=.6f;mvy*=.6f;}
        if(captured){float[] md=dev.takeMouseDelta();lookX+=md[0]*.55f;lookY+=md[1]*.55f;fire|=dev.mouseL;saw|=dev.mouseR;}
        saw|=k[Devices.K_F]||k[Devices.K_E];
        boolean space=k[Devices.K_SPACE];if(space&&!jumpKey)in.pressJump();jumpKey=space;jump|=space;
        if(escP){shell.pause();return;}
        float ml=(float)Math.hypot(mvx,mvy);if(ml>1){mvx/=ml;mvy/=ml;}
        boolean any=pad||kb;
        if(any){in.devMoveX=mvx;in.devMoveY=mvy;in.devFire=fire;in.devSaw=saw;in.devJump=jump;if(lookX!=0||lookY!=0)in.look(lookX,lookY);drovePad=true;}
        else if(drovePad){in.devMoveX=in.devMoveY=0;in.devFire=in.devSaw=in.devJump=false;drovePad=false;}
    }
    private boolean jumpKey,wasLook;private int prevArrows,prevShort,prevKeys3d;
}
