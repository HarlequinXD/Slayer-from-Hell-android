package com.harlequin.slayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

/**
 * The level editor's 3D view (after RPG Pocket Editor's): the map in the game's own renderer underneath, with a
 * texture browser on the left. Touch: drag on the left half to move (a floating joystick), drag on the right half
 * to look; tap a floor, ceiling or wall to select it and double-tap to paint it with the chosen texture.
 * Controller and mouse: hold RT / the right button to look and fly (the cursor hides); let go and the cursor
 * selects (A / click), the d-pad / arrows raise and lower the selection and step through textures, X / Enter (or a
 * double-click) paints. Fly or walk (with collision and steps), back to the player start, and Play here.
 */
final class Edit3DView extends View implements Pointer.Aware {
    interface Host {void back3D();void playHere(SectorMap m);void changed3D();}
    private static final int H_BACK=1,H_FLY=2,H_START=3,H_PLAY=4,H_UP=5,H_DOWN=6,H_RAISE=7,H_LOWER=8,H_RESET=9,H_UNDO=10,H_REDO=11,H_MAT=100;
    private final Ui ui;private final Paint p,pixel=new Paint();private final Path path=new Path();private final RectF r=new RectF();
    private final Game game;private final MapEditor ed;private final Host host;private final Handler main=new Handler(Looper.getMainLooper());
    private int selMat=0;
    private float[] sel;   // {kind, sector, a, b, x, y, z}
    private int joyPtr=-1,lookPtr=-1,upPtr=-1,downPtr=-1,scrollPtr=-1,clickPtr=-1;private float clickX,clickY;private float joyOX,joyOY,joyX,joyY,lookLX,lookLY,lookDX,lookDY,scrollY;
    private long lastTap;private float lastTapX,lastTapY;private String message="";private long messageUntil;

    Edit3DView(Context c,Game g,MapEditor ed,Host host){
        super(c);game=g;this.ed=ed;this.host=host;ui=new Ui(c);p=ui.p;pixel.setFilterBitmap(false);
        g.pickListener=(hit,twice)->main.post(()->picked(hit,twice));
        hint(Hud.touchControls?"Left: move · right: look · tap a surface, double-tap to paint it":Controls.padHint?"Hold RT to look and fly · A selects · X paints":"Hold the right mouse button to look · WASD fly · click selects");
    }
    private float dp(float v){return ui.dp(v);}
    private float browserW(){return Math.max(dp(116),Math.min(dp(156),getWidth()*.17f));}
    private float topH(){return dp(52);}
    private void hint(String s){message=s;messageUntil=System.currentTimeMillis()+2800;invalidate();}

    // ---------------------------------------------------------------- picking and painting
    private void picked(float[] hit,boolean twice){
        sel=hit;
        if(hit==null){hint("Nothing there");return;}
        if(twice)paint();else hint(describe()+" · double-tap to paint "+Tex.NAMES[SectorMap.MAP_TEXTURES[selMat]]);
        invalidate();
    }
    private String describe(){
        if(sel==null)return "";SectorMap m=ed.map;int s=(int)sel[1];
        if(sel[0]==Level.PICK_WALL){int tex=m.wallTexture((int)sel[2],(int)sel[3],s>=0&&s<m.sectors.size()?wallDefault((int)sel[2],(int)sel[3],s):Tex.BRICK);boolean own=m.wallTex.containsKey(SectorMap.wallKey((int)sel[2],(int)sel[3]));
            return "Wall · "+Tex.NAMES[tex]+(own?" (painted)":"");}
        if(s<0||s>=m.sectors.size())return "";SectorMap.Sector S=m.sectors.get(s);boolean fl=sel[0]==Level.PICK_FLOOR;
        return (fl?"Floor ":"Ceiling ")+EditorViewFmt.f(fl?S.floor:S.ceil)+" m · sector "+(s+1)+" · "+Tex.NAMES[fl?S.floorTex:S.ceilTex];
    }
    /** The texture a wall face shows without paint (the sector behind it for steps, else the sector it faces). */
    private int wallDefault(int a,int b,int sec){
        SectorMap m=ed.map;for(SectorMap.Line L:m.lines)if(L.a==a&&L.b==b||L.a==b&&L.b==a){int other=L.front==sec?L.back:L.front;
            if(other>=0){SectorMap.Sector S=m.sectors.get(sec),O=m.sectors.get(other);if(O.floor>S.floor+1e-3f||O.ceil<S.ceil-1e-3f)return O.wallTex;}break;}
        return m.sectors.get(sec).wallTex;
    }
    private void paint(){
        if(sel==null)return;int tex=SectorMap.MAP_TEXTURES[selMat];int s=(int)sel[1];
        if(sel[0]==Level.PICK_WALL)ed.setWallTexture((int)sel[2],(int)sel[3],tex);
        else{if(s<0)return;ed.setTexture(s,sel[0]==Level.PICK_FLOOR?1:2,tex);}
        changed(Tex.NAMES[tex]+" applied");
    }
    private void changed(String msg){game.setMap(ed.map.copy());host.changed3D();hint(msg);}
    /** Raises or lowers the selection: a floor or a ceiling, or for a wall the ceiling of the sector it faces. */
    private void adjust(boolean up){
        if(sel==null){hint(Hud.touchControls?"Tap a surface first":"Select a surface first");return;}int s=(int)sel[1];if(s<0||s>=ed.map.sectors.size())return;float d=up?.25f:-.25f;
        boolean fl=sel[0]==Level.PICK_FLOOR;
        boolean ok=fl?ed.changeHeight(s,d,0):ed.changeHeight(s,0,d);
        if(ok){SectorMap.Sector S=ed.map.sectors.get(s);changed((fl?"Floor ":"Ceiling ")+EditorViewFmt.f(fl?S.floor:S.ceil)+" m");}else hint(ed.error);
    }
    private void cycleTexture(int d){int[] ls=browser.list();int k=0;for(int i=0;i<ls.length;i++)if(ls[i]==selMat)k=i;selMat=ls[((k+d)%ls.length+ls.length)%ls.length];
        float left=browserW(),pad=dp(10),size=(left-pad*2-dp(4)-dp(6))/2;browser.reveal(selMat,2,size+dp(6),getHeight()-topH()-dp(140));
        hint(Tex.NAMES[SectorMap.MAP_TEXTURES[selMat]]+(sel!=null?" · "+(Hud.touchControls?"double-tap":Controls.padHint?"X":"Enter")+" to paint":""));}

    // ---------------------------------------------------------------- drawing
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();ui.clearHits();
        if(game.loading!=null){ui.rect(c,0,0,w,h,Ui.BG);ui.glowText(c,"FORGING THE MAP",w/2,h*.45f,22,0xffff7a3a,0);ui.text(c,game.loading,w/2,h*.45f+dp(28),12.5f,Ui.TEXT_2,0,0);postInvalidateDelayed(100);return;}
        // selection outline
        float[] o=game.outline;
        if(o!=null&&o.length>=4){p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(dp(4));p.setColor(0x80000000);for(int i=0;i+3<o.length;i+=4)c.drawLine(o[i],o[i+1],o[i+2],o[i+3],p);
            p.setStrokeWidth(dp(2));p.setColor(0xffff7a2a);for(int i=0;i+3<o.length;i+=4)c.drawLine(o[i],o[i+1],o[i+2],o[i+3],p);p.setStyle(Paint.Style.FILL);}
        // crosshair
        if(Hud.touchControls||looking){float cx=(browserW()+w)*.5f,cy=(topH()+h)*.5f;p.setColor(0xccffffff);c.drawCircle(cx,cy,dp(2),p);
            if(looking){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));c.drawCircle(cx,cy,dp(9),p);p.setStyle(Paint.Style.FILL);}}
        drawTop(c,w);drawBrowser(c,h);drawJoystick(c);
        if(game.editFly&&Hud.touchControls){float bx=w-dp(14)-dp(56),by=h-dp(16)-dp(56);
            r.set(bx,by-dp(64),bx+dp(56),by-dp(8));ui.stackButton(c,r,Icons.ARROW_UP,"Up",game.editUp?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_UP);
            r.set(bx,by,bx+dp(56),by+dp(56));ui.stackButton(c,r,Icons.ARROW_DOWN,"Down",game.editDown?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_DOWN);}
        drawSelection(c,w,h);
        if(!Hud.touchControls)drawKeys(c,w,h);
        if(System.currentTimeMillis()<messageUntil&&!message.isEmpty()){float tw=Math.min(ui.measure(message,12.5f,1)+dp(32),w-browserW()-dp(40)),cx=(browserW()+w)*.5f,t=topH()+dp(12);
            ui.pill(c,cx-tw*.5f,t,cx+tw*.5f,t+dp(32),0xf2241210);ui.textMid(c,ui.ellipsize(message,12.5f,1,tw-dp(24)),cx,t+dp(16),12.5f,Ui.TEXT,1,0);}
        postInvalidateOnAnimation();
    }
    private void drawTop(Canvas c,float w){
        float th=topH();ui.hellPanel(c,0,0,w,th);ui.hline(c,0,w,th-1,0x80ff5a1a);
        float bh=dp(36),top=(th-bh)*.5f;
        r.set(dp(8),top,dp(8)+dp(110),top+bh);ui.button(c,r,Icons.ARROW_LEFT,"2D map",Ui.NORMAL);ui.hit(r,H_BACK);
        float x=dp(128);ui.glowText(c,"3D VIEW",x,top+dp(14),10,0xffff7a3a,-1);ui.text(c,ui.ellipsize(ed.map.name,15,2,dp(180)),x,top+dp(31),15,Ui.TEXT,2,-1);
        float rr=w-dp(8);
        r.set(rr-dp(118),top,rr,top+bh);ui.button(c,r,Icons.PLAY,"Play here",Ui.SUCCESS);ui.hit(r,H_PLAY);rr-=dp(126);
        r.set(rr-bh,top,rr,top+bh);ui.button(c,r,Icons.FLAG,null,Ui.NORMAL);ui.hit(r,H_START);rr-=bh+dp(8);
        r.set(rr-dp(92),top,rr,top+bh);ui.button(c,r,game.editFly?Icons.LAYERS:Icons.PERSON,game.editFly?"Fly":"Walk",game.editFly?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_FLY);rr-=dp(100);
        if(rr-bh*2>x+dp(200)){r.set(rr-bh,top,rr,top+bh);ui.button(c,r,Icons.REDO,null,ed.canRedo()?Ui.NORMAL:Ui.DISABLED);ui.hit(r,H_REDO);rr-=bh+dp(6);
            r.set(rr-bh,top,rr,top+bh);ui.button(c,r,Icons.UNDO,null,ed.canUndo()?Ui.NORMAL:Ui.DISABLED);ui.hit(r,H_UNDO);}
    }
    private final TexBrowser browser=new TexBrowser();private static final int H_TAB=90;
    private void drawBrowser(Canvas c,float h){
        float left=browserW(),pad=dp(10),top0=topH();
        ui.hellPanel(c,0,top0,left,h);ui.vline(c,left-dp(1),top0,h,0x60ff5a1a);
        ui.caption(c,"Textures · "+SectorMap.MAP_TEXTURES.length,pad,top0+dp(22),0xffd08060);
        float top=browser.drawTabs(c,ui,pad,top0+dp(30),left-pad,2,H_TAB)+dp(4),bottom=h-dp(44);
        browser.drawGrid(c,ui,pad,top,left-pad-dp(4),bottom,2,selMat,H_MAT);
        if(browser.waiting)postInvalidateDelayed(120);
        int hov=Float.isNaN(Ui.hoverX)?-1:TexBrowser.at(ui,Ui.hoverX,Ui.hoverY,H_MAT);int show=hov>=0?hov:selMat;
        ui.text(c,ui.ellipsize(Tex.NAMES[SectorMap.MAP_TEXTURES[show]],11.5f,1,left-pad*2),pad,h-dp(28),11.5f,hov>=0&&hov!=selMat?Ui.EMBER_TEXT:Ui.TEXT,1,-1);
        ui.text(c,Hud.touchControls?"Double-tap a surface":Controls.padHint?"D-pad ◀ ▶ · LB RB tabs":"[ ] next · double-click",pad,h-dp(12),10.5f,Ui.TEXT_3);
    }
    @Override public void pointerZoom(float steps,float x,float y){if(x<browserW()){browser.scroll-=steps*dp(60);invalidate();}}
    private void drawJoystick(Canvas c){
        if(joyPtr<0)return;float R=dp(56);
        p.setStyle(Paint.Style.FILL);p.setColor(0x6e140806);c.drawCircle(joyOX,joyOY,R,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(0x80ff6a2a);c.drawCircle(joyOX,joyOY,R,p);p.setStyle(Paint.Style.FILL);
        p.setColor(0xe8ffb080);c.drawCircle(joyX,joyY,R*.4f,p);
    }
    private void drawSelection(Canvas c,float w,float h){
        if(sel==null)return;String t=describe();if(t.isEmpty())return;
        boolean wall=sel[0]==Level.PICK_WALL;float dockL=w-dp(14)-dp(60);if(game.editFly&&Hud.touchControls)dockL-=dp(68);if(!Hud.touchControls)dockL=w-dp(234);
        float left=browserW(),maxTw=dockL-left-dp(24);t=ui.ellipsize(t,12.5f,1,maxTw-dp(50));
        float tw=ui.measure(t,12.5f,1)+dp(50),cx=(left+dockL)*.5f,bt=h-dp(50);
        ui.pill(c,cx-tw*.5f,bt,cx+tw*.5f,bt+dp(36),0xf2241210);
        ui.icon(c,wall?Icons.WALL_TEX:sel[0]==Level.PICK_FLOOR?Icons.FLOOR_TEX:Icons.CEIL_TEX,cx-tw*.5f+dp(20),bt+dp(18),16,0xffffb080);ui.textMid(c,t,cx-tw*.5f+dp(36),bt+dp(18),12.5f,Ui.TEXT,1,-1);
        float bw=dp(60),bx=w-dp(14)-bw,y=topH()+dp(14),gap=dp(8),bh=dp(54);
        if(Hud.touchControls&&!wall){r.set(bx,y,bx+bw,y+bh);ui.stackButton(c,r,Icons.ARROW_UP,"Raise",Ui.NORMAL);ui.hit(r,H_RAISE);y+=bh+gap;
            r.set(bx,y,bx+bw,y+bh);ui.stackButton(c,r,Icons.ARROW_DOWN,"Lower",Ui.NORMAL);ui.hit(r,H_LOWER);}
        else if(ed.map.wallTex.containsKey(SectorMap.wallKey((int)sel[2],(int)sel[3]))){r.set(bx,y,bx+bw,y+bh);ui.stackButton(c,r,Icons.ERASER,"Default",Ui.NORMAL);ui.hit(r,H_RESET);}
    }

    // ---------------------------------------------------------------- touch
    // ---------------------------------------------------------------- the global cursor: controller and keyboard fly the camera
    @Override public Ui pointerUi(){return ui;}
    @Override public boolean pointerCamera(){return true;}
    /** Look mode (RT / right button held): set by Controls. */
    private boolean looking;
    void setLooking(boolean on){looking=on;if(on)hint(Controls.padHint?"Looking · left stick move · d-pad up/down · release RT for the cursor":"Looking · WASD move · E/Q up and down · release to use the cursor");invalidate();}
    /** Selects the surface under the crosshair (A or a left click while looking). */
    void pickCentre(boolean twice){game.requestPick((browserW()+getWidth())*.5f,(topH()+getHeight())*.5f,twice);}
    /** Keyboard and controller shortcuts (from Controls). */
    void shortcut(char k){
        switch(k){
            case '\n':if(sel==null)hint("Select a surface first");else paint();break;
            case 'f':game.editFly=!game.editFly;hint(game.editFly?"Fly mode":"Walk mode · gravity and steps");break;
            case 'p':playHere();break;
            case 'h':toStart();break;
            case '[':cycleTexture(-1);break;case ']':cycleTexture(1);break;
            case '{':case '}':{browser.filter=(browser.filter+(k=='}'?1:-1)+TexBrowser.TABS.length)%TexBrowser.TABS.length;browser.scroll=0;hint(TexBrowser.TABS[browser.filter]+" textures");break;}
            case '+':adjust(true);break;case '-':adjust(false);break;
            case 'x':if(sel!=null&&sel[0]==Level.PICK_WALL){ed.setWallTexture((int)sel[2],(int)sel[3],-1);changed("Wall back to its sector's texture");}break;
            default:break;}
        invalidate();
    }
    @Override public void pointerShortcut(char k){if(k=='z'||k=='y'){if(k=='z'?ed.undo():ed.redo()){sel=null;game.selection=null;changed(k=='z'?"Undo":"Redo");}else hint(k=='z'?"Nothing to undo":"Nothing to redo");}}
    /** The d-pad / arrows in cursor mode: up and down raise and lower the selection, left and right pick textures. */
    @Override public boolean pointerDpad(int dx,int dy){if(looking)return true;if(dy!=0)adjust(dy<0);else cycleTexture(dx);return true;}
    private void playHere(){float[] cam=game.editCamera();SectorMap m=ed.map.copy();m.playerX=cam[0];m.playerY=cam[1];m.facing=cam[3];
        if(m.index().sectorAt(cam[0],cam[1])<0){hint("Move inside the map first");return;}host.playHere(m);}
    private void toStart(){SectorMap m=ed.map;float z=game.level.floorUnder(m.playerX,m.playerY,.3f);game.setEditCamera(m.playerX,m.playerY,Math.max(0,z),m.facing,0);hint("Back at the player start");}
    /** The controls for the device in hand, along the bottom right. */
    private void drawKeys(Canvas c,float w,float h){
        boolean pad=Controls.padHint;String[] rows=pad?new String[]{"Hold RT  look and fly","A  select · X  paint","D-pad  raise/lower · textures","Y  fly/walk · B  2D map"}
            :new String[]{"Hold right mouse  look · WASD","Click  select · Enter  paint","↑↓ PgUp PgDn  raise/lower","F fly · P play · H start · Esc"};
        float lh=dp(16),bw=dp(210),bx=w-bw-dp(12),by=h-dp(12)-rows.length*lh-dp(10);if(sel!=null)by-=0;
        ui.round(c,bx,by,bx+bw,h-dp(12),dp(8),0xb0140808);
        for(int i=0;i<rows.length;i++)ui.text(c,rows[i],bx+dp(10),by+dp(16)+i*lh,10.5f,i==0?0xffffb080:Ui.TEXT_2,i==0?1:0,-1);
    }
    private boolean padDriving;
    /** Move (strafe, forward), look (rates, -1..1) and up/down from a controller or WASD/QE. */
    void padCamera(float mx,float my,float lx,float ly,float upDown,float dt){
        boolean any=mx!=0||my!=0||upDown!=0;
        if(any||padDriving){game.editMoveX=mx;game.editMoveY=my;game.editUp=upDown>.3f;game.editDown=upDown<-.3f;padDriving=any;}
        if(lx!=0||ly!=0)game.editLook(lx*dt*420,ly*dt*300);
    }
    /** Mouse look while the right (or middle) button is held. */
    void mouseLook(float dx,float dy){game.editLook(dx*.6f,dy*.6f);}
    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked(),idx=e.getActionIndex(),id=e.getPointerId(idx);float x=e.getX(idx),y=e.getY(idx);float w=getWidth(),left=browserW();
        if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN){
            int hid=ui.hitAt(x,y);
            if(hid>=H_TAB&&hid<H_TAB+TexBrowser.TABS.length){browser.filter=hid-H_TAB;browser.scroll=0;invalidate();return true;}
            if(hid>=H_MAT&&hid<H_MAT+SectorMap.MAP_TEXTURES.length){selMat=hid-H_MAT;scrollPtr=id;scrollY=y;hint(Tex.NAMES[SectorMap.MAP_TEXTURES[selMat]]+" · double-tap a surface to apply");return true;}
            if(x<left&&y>topH()){scrollPtr=id;scrollY=y;return true;}
            if(hid==H_BACK){host.back3D();return true;}
            if(hid==H_PLAY){playHere();return true;}
            if(hid==H_START){toStart();return true;}
            if(hid==H_FLY){game.editFly=!game.editFly;hint(game.editFly?"Fly mode · use Up and Down":"Walk mode · gravity and steps");return true;}
            if(hid==H_UP){game.editUp=true;upPtr=id;return true;}
            if(hid==H_DOWN){game.editDown=true;downPtr=id;return true;}
            if(hid==H_RAISE||hid==H_LOWER){adjust(hid==H_RAISE);return true;}
            if(hid==H_RESET&&sel!=null){ed.setWallTexture((int)sel[2],(int)sel[3],-1);changed("Wall back to its sector's texture");return true;}
            if(hid==H_UNDO||hid==H_REDO){if(hid==H_UNDO?ed.undo():ed.redo()){sel=null;game.selection=null;changed(hid==H_UNDO?"Undo":"Redo");}else hint(hid==H_UNDO?"Nothing to undo":"Nothing to redo");return true;}
            if(y<topH())return true;
            if(!Hud.touchControls){clickPtr=id;clickX=x;clickY=y;return true;}   // the cursor (controller, mouse): a click selects, no joystick
            if(x<left+(w-left)*.45f){if(joyPtr<0){joyPtr=id;joyOX=joyX=x;joyOY=joyY=y;}}
            else if(lookPtr<0){lookPtr=id;lookLX=lookDX=x;lookLY=lookDY=y;}
            return true;
        }
        if(a==MotionEvent.ACTION_MOVE){
            for(int i=0;i<e.getPointerCount();i++){int pid=e.getPointerId(i);float px=e.getX(i),py=e.getY(i);
                if(pid==scrollPtr){browser.scroll-=py-scrollY;scrollY=py;}
                if(pid==joyPtr){float dx=px-joyOX,dy=py-joyOY,len=(float)Math.hypot(dx,dy),lim=dp(56)*.68f,k=len>lim?lim/len:1;joyX=joyOX+dx*k;joyY=joyOY+dy*k;
                    game.editMoveX=(joyX-joyOX)/lim;game.editMoveY=-(joyY-joyOY)/lim;}
                if(pid==lookPtr){game.editLook((px-lookLX)*1.2f,(py-lookLY)*1.2f);lookLX=px;lookLY=py;}}
            return true;
        }
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP||a==MotionEvent.ACTION_CANCEL){
            if(id==scrollPtr)scrollPtr=-1;
            if(id==clickPtr){clickPtr=-1;if(a!=MotionEvent.ACTION_CANCEL&&Math.hypot(x-clickX,y-clickY)<dp(12)){
                long now=System.currentTimeMillis();boolean twice=now-lastTap<420&&Math.hypot(x-lastTapX,y-lastTapY)<dp(16);
                game.requestPick(x,y,twice);lastTap=twice?0:now;lastTapX=x;lastTapY=y;}}
            if(id==upPtr){game.editUp=false;upPtr=-1;}
            if(id==downPtr){game.editDown=false;downPtr=-1;}
            if(id==lookPtr){
                if(a!=MotionEvent.ACTION_CANCEL&&Math.hypot(x-lookDX,y-lookDY)<dp(10)){
                    long now=System.currentTimeMillis();boolean twice=now-lastTap<380&&Math.hypot(x-lastTapX,y-lastTapY)<dp(16);
                    game.requestPick(x,y,twice);lastTap=twice?0:now;lastTapX=x;lastTapY=y;}
                lookPtr=-1;}
            if(id==joyPtr){joyPtr=-1;game.editMoveX=game.editMoveY=0;}
            if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){joyPtr=lookPtr=upPtr=downPtr=scrollPtr=clickPtr=-1;game.editUp=game.editDown=false;game.editMoveX=game.editMoveY=0;}
            return true;
        }
        return true;
    }
}
