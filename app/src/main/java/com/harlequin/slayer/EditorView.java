package com.harlequin.slayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;

/**
 * The level editor, after RPG Pocket Editor's sector editor: everything is done with an on-screen cursor that snaps
 * to the grid, moved with the d-pad (hold to repeat) and acted on with Apply. Sectors mode has three pages of tools
 * in the inspector on the right (Draw, Edit, Surface) and a card describing what is selected or under the cursor;
 * Entities mode has the entity list on the left and Place / Select / Move / Rotate / Delete on the right.
 * Changes are validated (MapEditor), undoable and saved automatically.
 */
final class EditorView extends View implements Pointer.Aware {
    interface Host {void exitEditor();void testPlay(SectorMap m);void open3D();}
    private static final int SECTORS=0,ENTITIES=1;
    // sector tools (cursor + Apply) and immediate actions
    private static final int DRAW=1,LINE=2,RECT=3,STAIRS=4,SELECT=5,MOVE=6,SPLIT=7,EXTRUDE=8,DUPLICATE=9,DELETE=10,SPECIAL=11,START=12,PILLAR=13;
    private static final int CLOSE=101,UNDO_PT=102,CANCEL=103,UNDO=104,REDO=105,VALIDATE=106,CLEAN=107,DESELECT=108,
            FLOOR_UP=110,FLOOR_DN=111,CEIL_UP=112,CEIL_DN=113,FLOOR_TEX=116,CEIL_TEX=117,WALL_TEX=118,LAVA=119,HEIGHTS=120,GRID=121;
    private static final String[] PAGE_NAMES={"Draw","Edit","Surface"};
    private static final int[][] PAGES={
        {DRAW,LINE,RECT,STAIRS,CLOSE,UNDO_PT,CANCEL,UNDO,REDO,VALIDATE,CLEAN,GRID},
        {SELECT,MOVE,SPLIT,EXTRUDE,DUPLICATE,DELETE,SPECIAL,PILLAR,START,DESELECT,UNDO,REDO},
        {FLOOR_UP,FLOOR_DN,CEIL_UP,CEIL_DN,FLOOR_TEX,CEIL_TEX,WALL_TEX,LAVA,HEIGHTS,GRID,UNDO,REDO}};
    private static final String[] ENTITY_TOOLS={"Place","Select","Move","Rotate","Delete"};
    private static final int[] ENTITY_TOOL_ICONS={Icons.PLUS_SQUARE,Icons.POINTER,Icons.MOVE,Icons.RESET,Icons.TRASH};
    /** The entity list, in display order. */
    private static final int[] ENTITY_LIST={SectorMap.T_PLAYER,SectorMap.T_DEMON,SectorMap.T_HEALTH,SectorMap.T_SHELLS,SectorMap.T_LAMP_WARM,SectorMap.T_LAMP_RED,SectorMap.T_LAMP_BLUE,SectorMap.T_LAMP_WHITE};
    // touch targets
    private static final int H_SKILLF=300;
    private static final int H_BACK=1,H_SAVE=2,H_PLAY=3,H_TAB=10,H_UNDO=20,H_REDO=21,H_NAME=22,H_PAGE=30,H_TOOL=40,H_APPLY=60,H_ZOOMIN=61,H_ZOOMOUT=62,H_ENT=100,H_ETOOL=200;

    private static String toolName(int t){switch(t){case DRAW:return "Draw";case LINE:return "Line";case RECT:return "Rectangle";case STAIRS:return "Stairs";
        case SELECT:return "Select";case MOVE:return "Move";case SPLIT:return "Split edge";case EXTRUDE:return "Extrude";case DUPLICATE:return "Duplicate";case DELETE:return "Delete";
        case SPECIAL:return "Special";case START:return "Player start";case PILLAR:return "Pillar";case CLOSE:return "Close";case UNDO_PT:return "Undo pt";case CANCEL:return "Cancel";
        case UNDO:return "Undo";case REDO:return "Redo";case VALIDATE:return "Validate";case CLEAN:return "Clean up";case DESELECT:return "Deselect";
        case FLOOR_UP:return "Floor +";case FLOOR_DN:return "Floor −";case CEIL_UP:return "Ceiling +";case CEIL_DN:return "Ceiling −";
        case FLOOR_TEX:return "Floor tex";case CEIL_TEX:return "Ceiling tex";case WALL_TEX:return "Wall tex";case LAVA:return "Lava";case HEIGHTS:return "Heights…";default:return "Grid";}}
    private static int toolIcon(int t){switch(t){case DRAW:return Icons.PEN;case LINE:return Icons.LINE_TOOL;case RECT:return Icons.FRAME;case STAIRS:return Icons.STAIRS;
        case SELECT:return Icons.POINTER;case MOVE:return Icons.MOVE;case SPLIT:return Icons.SCISSORS;case EXTRUDE:return Icons.EXTRUDE;case DUPLICATE:return Icons.COPY;case DELETE:return Icons.TRASH;
        case SPECIAL:return Icons.DOOR;case START:return Icons.FLAG;case PILLAR:return Icons.SQUARE_FILL;case CLOSE:return Icons.CLOSE_SHAPE;case UNDO_PT:return Icons.BACKSPACE;case CANCEL:return Icons.CANCEL;
        case UNDO:return Icons.UNDO;case REDO:return Icons.REDO;case VALIDATE:return Icons.VALIDATE;case CLEAN:return Icons.SPARKLE;case DESELECT:return Icons.CANCEL;
        case FLOOR_UP:return Icons.FLOOR_UP;case FLOOR_DN:return Icons.FLOOR_DOWN;case CEIL_UP:return Icons.CEIL_UP;case CEIL_DN:return Icons.CEIL_DOWN;
        case FLOOR_TEX:return Icons.FLOOR_TEX;case CEIL_TEX:return Icons.CEIL_TEX;case WALL_TEX:return Icons.WALL_TEX;case LAVA:return Icons.ZAP;case HEIGHTS:return Icons.LAYERS;default:return Icons.GRID;}}

    private final Ui ui;private final Paint p;private final Path path=new Path();private final RectF r=new RectF();
    private final Host host;private final MapStore store;String mapId;
    final MapEditor ed;
    private int mode=SECTORS,toolPage=0,tool=DRAW,stage=0;
    private final ArrayList<float[]> pending=new ArrayList<>();
    private int selKind=0,selSector=-1,selEdge=-1,selVertex=-1;   // kind: 0 none, 1 sector, 2 edge, 3 vertex
    private int pickSector=-1,pickEdge=-1;private float anchorX,anchorY;
    private int[] moveIds=new int[0];private float[] moveOrig=new float[0];
    private float cursorX,cursorY,viewX,viewY,zoom=1,grid=1;
    private int dpadPointer=-1,dpadDirection=-1;private long nextDpadRepeat;
    private float w,h,toolbarH,statusH,cell;
    private float downX,downY,lastX,lastY;private boolean dragged;private int scrollTarget;private float entScroll;
    private int entType=SectorMap.T_DEMON,entityTool=0,entStage=0,selThing=-1;
    private String message="";private long messageUntil;

    EditorView(Context ctx,SectorMap m,String id,MapStore store,Host host){
        super(ctx);this.host=host;this.store=store;mapId=id;ed=new MapEditor(m);
        ui=new Ui(ctx);p=ui.p;
        cursorX=Math.round(m.playerX);cursorY=Math.round(m.playerY);viewX=cursorX;viewY=cursorY;
        setFocusable(true);hint(Hud.touchControls?"Pick a tool, move the cursor with the d-pad, tap Apply":"Pick a tool, then click on the map · wheel zooms, right-drag pans");
    }
    private float dp(float v){return ui.dp(v);}
    SectorMap map(){return ed.map;}

    // ---------------------------------------------------------------- layout
    private float inspectorW(){return Math.max(dp(208),Math.min(dp(290),w*.26f));}
    private float entPanelW(){return Math.max(dp(164),Math.min(dp(230),w*.2f));}
    private float mapL(){return mode==ENTITIES?entPanelW():0;}
    private float mapR(){return w-inspectorW();}
    private float mapT(){return toolbarH;}
    private float mapB(){return h-statusH;}
    private float snap(){return mode==ENTITIES?.5f:grid;}
    private float dpadR(){return dp(58);}
    private float dpadCX(){return mapL()+dp(20)+dpadR();}
    private float dpadCY(){return mapB()-dp(16)-dpadR();}
    private float applyR(){return dp(32);}
    private float applyCX(){return mapR()-dp(24)-applyR();}
    private float applyCY(){return dpadCY()+dp(8);}
    // north is up on screen
    private float sx(float wx){return (mapL()+mapR())*.5f+(wx-viewX)*cell;}
    private float sy(float wy){return (mapT()+mapB())*.5f-(wy-viewY)*cell;}
    private float wx(float x){return viewX+(x-(mapL()+mapR())*.5f)/cell;}
    private float wy(float y){return viewY-(y-(mapT()+mapB())*.5f)/cell;}
    private boolean inMap(float x,float y){return x>=mapL()&&x<mapR()&&y>=mapT()&&y<mapB();}

    // ---------------------------------------------------------------- frame
    @Override protected void onDraw(Canvas c){
        w=getWidth();h=getHeight();toolbarH=dp(52);statusH=dp(26);
        ui.clearHits();c.drawColor(Ui.BG);
        drawMap(c);
        if(mode==ENTITIES)drawEntityList(c);
        drawInspector(c);drawCursorControls(c);drawStatusBar(c);drawTopBar(c);drawToast(c);
    }
    private void hint(String s){message=s;messageUntil=System.currentTimeMillis()+2600;invalidate();postInvalidateDelayed(2700);}
    private void drawToast(Canvas c){
        if(System.currentTimeMillis()>messageUntil||message.isEmpty())return;
        float tw=Math.min(ui.measure(message,12.5f,1)+dp(32),mapR()-mapL()-dp(20)),cx=(mapL()+mapR())*.5f,t=mapT()+dp(12);
        ui.pill(c,cx-tw*.5f,t,cx+tw*.5f,t+dp(32),0xf2241210);ui.textMid(c,ui.ellipsize(message,12.5f,1,tw-dp(24)),cx,t+dp(16),12.5f,Ui.TEXT,1,0);
    }
    private void drawTopBar(Canvas c){
        ui.hellPanel(c,0,0,w,toolbarH);ui.hline(c,0,w,toolbarH-1,0x80ff5a1a);
        float bh=dp(36),top=(toolbarH-bh)*.5f;
        r.set(dp(8),top,dp(8)+bh,top+bh);ui.button(c,r,Icons.ARROW_LEFT,null,Ui.GHOST);ui.hit(r,H_BACK);
        float x=dp(8)+bh+dp(8);
        ui.glowText(c,"LEVEL EDITOR",x,top+dp(14),10,0xffff7a3a,-1);
        String nm=ui.ellipsize(map().name,15,1,dp(170));ui.text(c,nm,x,top+dp(31),15,Ui.TEXT,2,-1);
        ui.hit(x,top,x+ui.measure(nm,15,1)+dp(10),top+bh,H_NAME);
        // mode tabs
        float tabW=dp(100),tl=Math.max(x+dp(190),w*.5f-tabW*1.5f);
        for(int i=0;i<3;i++){r.set(tl+i*tabW,top,tl+(i+1)*tabW-dp(4),top+bh);ui.button(c,r,i==0?Icons.SECTORS:i==1?Icons.ENTITIES:Icons.CUBE,i==0?"Sectors":i==1?"Entities":"3D View",mode==i?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_TAB+i);}
        float rr=w-dp(8);
        r.set(rr-dp(92),top,rr,top+bh);ui.button(c,r,Icons.PLAY,"Play",Ui.PRIMARY);ui.hit(r,H_PLAY);rr-=dp(100);
        r.set(rr-dp(88),top,rr,top+bh);ui.button(c,r,Icons.SAVE,"Save",Ui.NORMAL);ui.hit(r,H_SAVE);rr-=dp(96);
        if(rr-bh*2>tl+tabW*3+dp(8)){
            r.set(rr-bh,top,rr,top+bh);ui.button(c,r,Icons.REDO,null,ed.canRedo()?Ui.NORMAL:Ui.DISABLED);ui.hit(r,H_REDO);rr-=bh+dp(6);
            r.set(rr-bh,top,rr,top+bh);ui.button(c,r,Icons.UNDO,null,ed.canUndo()?Ui.NORMAL:Ui.DISABLED);ui.hit(r,H_UNDO);}
    }

    // ---------------------------------------------------------------- 2D map
    private void updateView(){
        cell=(mapB()-mapT())/16f*zoom;if(!follow)return;
        float cx=sx(cursorX),cy=sy(cursorY),mx=(mapR()-mapL())*.28f,my=(mapB()-mapT())*.28f,ox=(mapL()+mapR())*.5f,oy=(mapT()+mapB())*.5f;
        float tx=viewX,ty=viewY;
        if(Math.abs(cx-ox)>mx)tx=cursorX-Math.signum(cx-ox)*mx/cell;if(Math.abs(cy-oy)>my)ty=cursorY+Math.signum(cy-oy)*my/cell;
        viewX+=(tx-viewX)*.2f;viewY+=(ty-viewY)*.2f;
        if(Math.abs(tx-viewX)>.01f||Math.abs(ty-viewY)>.01f)postInvalidateOnAnimation();
    }
    private int sectorColor(int k,SectorMap.Sector s){
        if(s.ceil-s.floor<.05f)return 0x55303640;   // pillar
        if(s.special==SectorMap.SP_LAVA||s.floorTex==Tex.LAVA)return 0x40ff6a20;
        if(s.special==SectorMap.SP_EXIT)return 0x403ecf8e;
        float t=Math.max(0,Math.min(1,(s.floor+1.5f)/4.5f));int a=(int)(0x20+t*0x34);return (a<<24)|0xc89a86;
    }
    private void drawMap(Canvas c){
        updateView();SectorMap m=map();
        float L=mapL(),R=mapR(),T=mapT(),B=mapB();
        int save=c.save();c.clipRect(L,T,R,B);
        ui.rect(c,L,T,R,B,Ui.CANVAS);
        p.setShader(new android.graphics.RadialGradient((L+R)*.5f,B+(B-T)*.3f,(B-T)*1.1f,0x38ff3a0a,0x00ff3a0a,android.graphics.Shader.TileMode.CLAMP));c.drawRect(L,T,R,B,p);p.setShader(null);
        float g=snap()<1?1:snap();
        int x0=(int)Math.floor(wx(L)/g)-1,x1=(int)Math.ceil(wx(R)/g)+1,y0=(int)Math.floor(wy(B)/g)-1,y1=(int)Math.ceil(wy(T)/g)+1;
        float lw=Math.max(1,dp(.75f));
        for(int i=x0;i<=x1;i++){float wxv=i*g;int col=Math.abs(wxv)<1e-4f?0xff4a2a20:Math.round(wxv)%4==0?0xff2c1913:0xff1e110d;if(col==0xff1e110d&&cell*g<dp(7))continue;ui.rect(c,sx(wxv),T,sx(wxv)+lw,B,col);}
        for(int i=y0;i<=y1;i++){float wyv=i*g;int col=Math.abs(wyv)<1e-4f?0xff4a2a20:Math.round(wyv)%4==0?0xff2c1913:0xff1e110d;if(col==0xff1e110d&&cell*g<dp(7))continue;ui.rect(c,L,sy(wyv),R,sy(wyv)+lw,col);}
        p.setShader(null);p.setStyle(Paint.Style.FILL);
        int curSec=sectorUnderCursor();
        // sectors, biggest first so nested ones draw on top
        Integer[] order=new Integer[m.sectors.size()];for(int i=0;i<order.length;i++)order[i]=i;
        java.util.Arrays.sort(order,(a,b)->Float.compare(Math.abs(m.area(m.sectors.get(b))),Math.abs(m.area(m.sectors.get(a)))));
        for(int k:order){SectorMap.Sector s=m.sectors.get(k);if(s.loop.size()<3)continue;path.reset();
            for(int j=0;j<s.loop.size();j++){SectorMap.Vertex v=m.vertices.get(s.loop.get(j));if(j==0)path.moveTo(sx(v.x),sy(v.y));else path.lineTo(sx(v.x),sy(v.y));}path.close();
            boolean sel=mode==SECTORS&&selKind==1&&k==selSector;
            int col=sel?0x50ff5a1a:sectorColor(k,s);
            if(mode==SECTORS&&k==curSec&&!sel&&(tool==SELECT||tool==DELETE||tool==SPECIAL||tool==STAIRS||tool==PILLAR||(tool==DUPLICATE&&stage==0)||(tool==MOVE&&stage==0)||toolPage==2))col=0x60d8a890;
            p.setColor(0xff140b09);c.drawPath(path,p);p.setColor(col);c.drawPath(path,p);
            if(cell>dp(22)){float[] ce=m.centroid(s);if(m.sectorAt(ce[0],ce[1])==k){
                ui.textMid(c,EditorViewFmt.f(s.floor)+(s.ceil-s.floor<.05f?" ▮":" / "+EditorViewFmt.f(s.ceil)),sx(ce[0]),sy(ce[1]),9.5f,0x99ffffff,1,0);
                if(s.special==SectorMap.SP_EXIT)ui.icon(c,Icons.FLAG,sx(ce[0]),sy(ce[1])-dp(14),13,0xcc3ecf8e);}}}
        p.setStrokeCap(Paint.Cap.ROUND);
        for(SectorMap.Line ln:m.lines){SectorMap.Vertex a=m.vertices.get(ln.a),b=m.vertices.get(ln.b);boolean two=ln.back>=0;
            p.setColor(two?0xffff8a3a:0xfff0ddd0);p.setStrokeWidth(two?dp(1.5f):dp(2));c.drawLine(sx(a.x),sy(a.y),sx(b.x),sy(b.y),p);}
        if(mode==SECTORS)drawSelection(c);
        p.setStrokeCap(Paint.Cap.BUTT);
        float vs=Math.max(dp(2.5f),Math.min(dp(4),cell*.09f));
        for(int i=0;i<m.vertices.size();i++){SectorMap.Vertex v=m.vertices.get(i);
            boolean hot=mode==SECTORS&&((selKind==3&&i==selVertex)||contains(moveIds,i)&&stage==1);
            ui.rect(c,sx(v.x)-vs-dp(1),sy(v.y)-vs-dp(1),sx(v.x)+vs+dp(1),sy(v.y)+vs+dp(1),0xff0e0807);
            ui.rect(c,sx(v.x)-vs,sy(v.y)-vs,sx(v.x)+vs,sy(v.y)+vs,hot?Ui.ORANGE:0xfff2dccf);}
        if(mode==SECTORS&&!pending.isEmpty()){
            p.setStyle(Paint.Style.STROKE);p.setColor(Ui.ACCENT);p.setStrokeWidth(dp(2));path.reset();
            for(int i=0;i<pending.size();i++){float[] v=pending.get(i);if(i==0)path.moveTo(sx(v[0]),sy(v[1]));else path.lineTo(sx(v[0]),sy(v[1]));}
            c.drawPath(path,p);float[] last=pending.get(pending.size()-1);
            p.setPathEffect(new DashPathEffect(new float[]{dp(5),dp(4)},0));p.setColor(0xaaff6a2a);c.drawLine(sx(last[0]),sy(last[1]),sx(cursorX),sy(cursorY),p);p.setPathEffect(null);p.setStyle(Paint.Style.FILL);
            for(int i=0;i<pending.size();i++){float[] v=pending.get(i);p.setColor(0xff0e0807);c.drawCircle(sx(v[0]),sy(v[1]),dp(5.5f),p);p.setColor(i==0?Ui.GREEN:Ui.ACCENT);c.drawCircle(sx(v[0]),sy(v[1]),dp(4),p);}}
        drawThings(c);
        // previews for two-stage tools
        if(mode==SECTORS&&stage==1){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.5f));p.setColor(Ui.ACCENT);p.setPathEffect(new DashPathEffect(new float[]{dp(6),dp(4)},0));
            if(tool==RECT){float l=sx(Math.min(anchorX,cursorX)),t=sy(Math.max(anchorY,cursorY)),rr=sx(Math.max(anchorX,cursorX)),b=sy(Math.min(anchorY,cursorY));
                p.setStyle(Paint.Style.FILL);p.setColor(0x26ff5a1a);p.setPathEffect(null);c.drawRect(l,t,rr,b,p);p.setStyle(Paint.Style.STROKE);p.setColor(Ui.ACCENT);p.setPathEffect(new DashPathEffect(new float[]{dp(6),dp(4)},0));c.drawRect(l,t,rr,b,p);}
            else if(tool==LINE){p.setStrokeWidth(dp(2.5f));p.setColor(Ui.ORANGE);c.drawLine(sx(anchorX),sy(anchorY),sx(cursorX),sy(cursorY),p);}
            else if(tool==DUPLICATE&&pickSector>=0&&pickSector<m.sectors.size()){SectorMap.Sector s=m.sectors.get(pickSector);path.reset();float dx=cursorX-anchorX,dy=cursorY-anchorY;
                for(int j=0;j<s.loop.size();j++){SectorMap.Vertex v=m.vertices.get(s.loop.get(j));if(j==0)path.moveTo(sx(v.x+dx),sy(v.y+dy));else path.lineTo(sx(v.x+dx),sy(v.y+dy));}path.close();c.drawPath(path,p);}
            else if(tool==EXTRUDE&&pickSector>=0){float[] e=edgeCoords(pickSector,pickEdge);if(e!=null){float d=extrudeDistance(e);float[] n=edgeNormal(pickSector,pickEdge);
                path.reset();path.moveTo(sx(e[0]),sy(e[1]));path.lineTo(sx(e[2]),sy(e[3]));path.lineTo(sx(e[2]+n[0]*d),sy(e[3]+n[1]*d));path.lineTo(sx(e[0]+n[0]*d),sy(e[1]+n[1]*d));path.close();c.drawPath(path,p);}}
            p.setPathEffect(null);p.setStyle(Paint.Style.FILL);}
        drawCursor(c);
        c.restoreToCount(save);
    }
    private static boolean contains(int[] a,int v){for(int x:a)if(x==v)return true;return false;}
    private void drawSelection(Canvas c){
        SectorMap m=map();p.setStyle(Paint.Style.STROKE);
        if(selKind==1&&selSector>=0&&selSector<m.sectors.size()){SectorMap.Sector s=m.sectors.get(selSector);p.setColor(Ui.ACCENT);p.setStrokeWidth(dp(2.5f));
            for(int j=0;j<s.loop.size();j++){SectorMap.Vertex a=m.vertices.get(s.loop.get(j)),b=m.vertices.get(s.loop.get((j+1)%s.loop.size()));c.drawLine(sx(a.x),sy(a.y),sx(b.x),sy(b.y),p);}}
        int es=selKind==2?selSector:(stage==1&&(tool==EXTRUDE||tool==MOVE)&&pickEdge>=0)?pickSector:-1,ee=selKind==2?selEdge:pickEdge;
        if(es>=0){float[] e=edgeCoords(es,ee);if(e!=null){p.setColor(Ui.ORANGE);p.setStrokeWidth(dp(4));c.drawLine(sx(e[0]),sy(e[1]),sx(e[2]),sy(e[3]),p);}}
        p.setStyle(Paint.Style.FILL);
    }
    private static int thingColor(int t){switch(t){case SectorMap.T_DEMON:return Ui.RED;case SectorMap.T_HEALTH:return 0xffff5a8a;case SectorMap.T_SHELLS:return Ui.AMBER;
        case SectorMap.T_PLAYER:return Ui.GREEN;default:{int c=SectorMap.LAMP_RGB[t];return 0xff000000|c;}}}
    private static int thingIcon(int t){switch(t){case SectorMap.T_DEMON:return Icons.SKULL;case SectorMap.T_HEALTH:return Icons.HEART;case SectorMap.T_SHELLS:return Icons.PACKAGE;
        case SectorMap.T_PLAYER:return Icons.PERSON;default:return Icons.SUN;}}
    private void drawThings(Canvas c){
        SectorMap m=map();boolean full=mode==ENTITIES;
        for(int i=0;i<m.things.size();i++){SectorMap.Thing t=m.things.get(i);drawThing(c,t.type,t.x,t.y,t.angle,full&&i==selThing,full);}
        drawThing(c,SectorMap.T_PLAYER,m.playerX,m.playerY,m.facing,full&&selThing==-2,true);
    }
    private void drawThing(Canvas c,int type,float x,float y,float angle,boolean sel,boolean full){
        float px=sx(x),py=sy(y),rad=Math.max(dp(full?7:4),(type==SectorMap.T_DEMON?.36f:.25f)*cell);int col=thingColor(type);
        if(!full&&type!=SectorMap.T_PLAYER){p.setColor((col&0xffffff)|0xcc000000);c.drawCircle(px,py,dp(3),p);return;}
        p.setStyle(Paint.Style.FILL);p.setColor((col&0xffffff)|0x40000000);c.drawCircle(px,py,rad,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sel?dp(2.5f):dp(1.25f));p.setColor(sel?Ui.ACCENT:col);c.drawCircle(px,py,rad,p);
        if(type==SectorMap.T_DEMON||type==SectorMap.T_PLAYER){float cs=(float)Math.cos(angle),sn=-(float)Math.sin(angle);p.setStrokeWidth(dp(2));c.drawLine(px+cs*rad*.6f,py+sn*rad*.6f,px+cs*(rad+dp(6)),py+sn*(rad+dp(6)),p);}
        p.setStyle(Paint.Style.FILL);
        if(rad>dp(8))ui.icon(c,thingIcon(type),px,py,Math.min(18,rad/ui.dp*1.2f),col);
    }
    private void drawCursor(Canvas c){
        float px=sx(cursorX),py=sy(cursorY);
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(dp(4));p.setColor(0x9915181c);c.drawCircle(px,py,dp(10),p);
        p.setStrokeWidth(dp(1.75f));p.setColor(Ui.AMBER);c.drawCircle(px,py,dp(10),p);
        float g=dp(5),len=dp(16);c.drawLine(px-len,py,px-g,py,p);c.drawLine(px+g,py,px+len,py,p);c.drawLine(px,py-len,px,py-g,p);c.drawLine(px,py+g,px,py+len,p);
        p.setStrokeCap(Paint.Cap.BUTT);p.setStyle(Paint.Style.FILL);p.setColor(Ui.AMBER);c.drawCircle(px,py,dp(2),p);
    }
    private void drawStatusBar(Canvas c){
        SectorMap m=map();float L=mapL(),R=mapR(),top=mapB(),cy=top+statusH*.5f;
        ui.hellPanel(c,L,top,R,h);
        float x=L+dp(12);
        x=statusItem(c,x,cy,Icons.CROSSHAIR,EditorViewFmt.f(cursorX)+", "+EditorViewFmt.f(cursorY));
        x=statusItem(c,x,cy,Icons.GRID,"Snap "+EditorViewFmt.f(snap())+" m");
        if(R-L>dp(430)){x=statusItem(c,x,cy,Icons.SECTORS,m.sectors.size()+" sectors");x=statusItem(c,x,cy,Icons.SKULL,count(SectorMap.T_DEMON)+" demons");}
        float bw=dp(34),bt=top+dp(3),bb=h-dp(3);
        r.set(R-dp(6)-bw,bt,R-dp(6),bb);ui.round(c,r,dp(6),Ui.SURFACE_2);ui.icon(c,Icons.PLUS,r.centerX(),r.centerY(),15,Ui.TEXT);ui.hit(r.left,top,r.right,h,H_ZOOMIN);
        ui.textMid(c,Math.round(zoom*100)+"%",R-dp(12)-bw-dp(24),cy,11,Ui.TEXT_2,1,0);
        r.set(R-dp(18)-bw*2-dp(36),bt,R-dp(18)-bw-dp(36),bb);ui.round(c,r,dp(6),Ui.SURFACE_2);ui.icon(c,Icons.MINUS,r.centerX(),r.centerY(),15,Ui.TEXT);ui.hit(r.left,top,r.right,h,H_ZOOMOUT);
    }
    private float statusItem(Canvas c,float x,float cy,int icon,String text){ui.icon(c,icon,x+dp(6),cy,12,Ui.TEXT_3);ui.textMid(c,text,x+dp(17),cy,11,Ui.TEXT_2,0,-1);return x+dp(17)+ui.measure(text,11,0)+dp(18);}
    private int count(int type){int n=0;for(SectorMap.Thing t:map().things)if(t.type==type)n++;return n;}

    // ---------------------------------------------------------------- inspector (Sectors) and entity tools (Entities)
    private void drawInspector(Canvas c){
        float x0=mapR(),pad=dp(10),l=x0+pad,rr=w-pad;
        ui.hellPanel(c,x0,toolbarH,w,h);ui.vline(c,x0,toolbarH,h,0x60ff5a1a);
        if(mode==SECTORS){
            float tt=toolbarH+dp(10),th=dp(30),tg=dp(5),tw=(rr-l-tg*2)/3f;
            for(int i=0;i<3;i++){float a=l+i*(tw+tg);r.set(a,tt,a+tw,tt+th);ui.button(c,r,-1,PAGE_NAMES[i],i==toolPage?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_PAGE+i);}
            float cardH=dp(70),gridTop=tt+th+dp(10),gridBottom=h-cardH-dp(18);
            int[] tools=PAGES[toolPage];int rows=(tools.length+1)/2;
            float rowH=Math.min(dp(38),(gridBottom-gridTop)/rows),colW=(rr-l)/2f,gap=dp(3);
            for(int i=0;i<tools.length;i++){int t=tools[i],col=i%2,row=i/2;
                r.set(l+col*colW+(col==0?0:gap),gridTop+row*rowH,l+(col+1)*colW-(col==0?gap:0),gridTop+(row+1)*rowH-gap*2);
                boolean active=t==tool&&t<100;int style=active?Ui.ACTIVE:t==DELETE?Ui.DANGER:(t==UNDO&&!ed.canUndo()||t==REDO&&!ed.canRedo())?Ui.DISABLED:Ui.NORMAL;
                ui.button(c,r,toolIcon(t),t==GRID?"Grid "+EditorViewFmt.f(grid)+" m":toolName(t),style);ui.hit(r,H_TOOL+i);}
            float ct=h-cardH-dp(10);
            ui.round(c,l,ct,rr,h-dp(10),dp(8),Ui.SURFACE_2);ui.outline(c,l,ct,rr,h-dp(10),dp(8),0x14ffffff,dp(1));
            drawSelectionCard(c,l,ct,rr);
        }else{
            float y=toolbarH+dp(12);
            ui.caption(c,"Place with cursor",l,y+dp(8),Ui.TEXT_3);y+=dp(16);
            float tw2=(rr-l)/5f;
            for(int i=0;i<5;i++){r.set(l+i*tw2+dp(1),y,l+(i+1)*tw2-dp(1),y+dp(34));ui.button(c,r,ENTITY_TOOL_ICONS[i],null,entityTool==i?Ui.ACTIVE:i==4?Ui.DANGER:Ui.NORMAL);ui.hit(r,H_ETOOL+i);}
            y+=dp(40);ui.text(c,ENTITY_TOOLS[entityTool]+(entityTool==0?" · "+SectorMap.THING_NAMES[entType]:" entity"),l,y+dp(10),12,Ui.TEXT_2,1,-1);y+=dp(26);
            float hw=(rr-l-dp(6))/2f;
            r.set(l,y,l+hw,y+dp(32));ui.button(c,r,Icons.UNDO,"Undo",ed.canUndo()?Ui.NORMAL:Ui.DISABLED);ui.hit(r,H_UNDO);
            r.set(l+hw+dp(6),y,rr,y+dp(32));ui.button(c,r,Icons.REDO,"Redo",ed.canRedo()?Ui.NORMAL:Ui.DISABLED);ui.hit(r,H_REDO);y+=dp(46);
            ui.caption(c,"Selected",l,y,Ui.TEXT_3);y+=dp(8);
            ui.round(c,l,y,rr,y+dp(96),dp(8),Ui.SURFACE_2);
            SectorMap m=map();
            if(selThing==-2){ui.text(c,"Player start",l+dp(10),y+dp(22),14,Ui.TEXT,1,-1);stat(c,l+dp(10),y+dp(54),EditorViewFmt.f(m.playerX)+", "+EditorViewFmt.f(m.playerY),"Position");stat(c,l+dp(120),y+dp(54),Math.round(Math.toDegrees(m.facing))+"°","Facing");}
            else if(selThing>=0&&selThing<m.things.size()){SectorMap.Thing t=m.things.get(selThing);ui.text(c,SectorMap.THING_NAMES[t.type],l+dp(10),y+dp(22),14,Ui.TEXT,1,-1);
                stat(c,l+dp(10),y+dp(54),EditorViewFmt.f(t.x)+", "+EditorViewFmt.f(t.y),"Position");stat(c,l+dp(120),y+dp(54),Math.round(Math.toDegrees(t.angle))+"°","Facing");
                // which difficulties it appears on (Doom's skill flags)
                float sy=y+dp(104);ui.caption(c,"Appears on",l,sy,0xffd08060);String[] sk={"Easy","Normal","Hard"};float bw=(rr-l-dp(8))/3f;
                for(int k=0;k<3;k++){r.set(l+k*(bw+dp(4)),sy+dp(8),l+k*(bw+dp(4))+bw,sy+dp(40));ui.button(c,r,-1,sk[k],(t.skills&(1<<k))!=0?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,H_SKILLF+k);}
                y+=dp(56);}
            else{ui.text(c,"Nothing selected",l+dp(10),y+dp(22),12.5f,Ui.TEXT_2,1,-1);ui.text(c,"Use Select, then Apply on an entity.",l+dp(10),y+dp(42),11,Ui.TEXT_3);}
            y+=dp(110);
            ui.caption(c,"In this map",l,y,Ui.TEXT_3);y+=dp(18);
            ui.text(c,count(SectorMap.T_DEMON)+" demons · "+count(SectorMap.T_HEALTH)+" health · "+count(SectorMap.T_SHELLS)+" shells",l,y,11.5f,Ui.TEXT_2);y+=dp(18);
            int lamps=0;for(SectorMap.Thing t:m.things)if(t.type>=SectorMap.T_LAMP_WARM&&t.type<=SectorMap.T_LAMP_WHITE)lamps++;
            ui.text(c,lamps+" lamps"+(lamps>Level.MAX_LIGHTS?" (only "+Level.MAX_LIGHTS+" light the map)":""),l,y,11.5f,lamps>Level.MAX_LIGHTS?Ui.AMBER:Ui.TEXT_2);
        }
    }
    private void drawSelectionCard(Canvas c,float l,float ct,float rr){
        SectorMap m=map();int s=targetSector();
        if(selKind==3&&selVertex>=0&&selVertex<m.vertices.size()){SectorMap.Vertex v=m.vertices.get(selVertex);
            ui.caption(c,"Vertex "+(selVertex+1),l+dp(10),ct+dp(18),Ui.TEXT_3);float cw=(rr-l-dp(20))/3f;
            stat(c,l+dp(10),ct+dp(46),EditorViewFmt.f(v.x),"X");stat(c,l+dp(10)+cw,ct+dp(46),EditorViewFmt.f(v.y),"Y");return;}
        if(selKind==2){float[] e=edgeCoords(selSector,selEdge);if(e!=null){ui.caption(c,"Edge · sector "+(selSector+1),l+dp(10),ct+dp(18),Ui.TEXT_3);
            stat(c,l+dp(10),ct+dp(46),EditorViewFmt.f((float)Math.hypot(e[2]-e[0],e[3]-e[1]))+" m","Length");return;}}
        if(s>=0){SectorMap.Sector sec=m.sectors.get(s);
            ui.caption(c,(selKind==1?"Sector ":"Under cursor · sector ")+(s+1)+(sec.special!=0?" · "+SectorMap.SPECIAL_NAMES[sec.special]:""),l+dp(10),ct+dp(18),Ui.TEXT_3);
            float cw=(rr-l-dp(20))/3f;
            stat(c,l+dp(10),ct+dp(46),EditorViewFmt.f(sec.floor),"Floor");stat(c,l+dp(10)+cw,ct+dp(46),EditorViewFmt.f(sec.ceil),"Ceiling");
            stat(c,l+dp(10)+cw*2,ct+dp(46),ui.ellipsize(Tex.NAMES[sec.wallTex],12,1,cw-dp(4)),"Walls");
        }else{ui.caption(c,"Nothing selected",l+dp(10),ct+dp(18),Ui.TEXT_3);
            ui.text(c,"Move the cursor onto a sector, or",l+dp(10),ct+dp(42),11.5f,Ui.TEXT_2);ui.text(c,"use Select then Apply.",l+dp(10),ct+dp(58),11.5f,Ui.TEXT_2);}
    }
    private void stat(Canvas c,float x,float y,String value,String label){ui.text(c,value,x,y,14,Ui.TEXT,1,-1);ui.text(c,label,x,y+dp(14),10.5f,Ui.TEXT_3);}
    private void drawEntityList(Canvas c){
        float pw=entPanelW(),pad=dp(10);
        ui.hellPanel(c,0,toolbarH,pw,h);ui.vline(c,pw-dp(1),toolbarH,h,0x60ff5a1a);
        float ht=toolbarH+dp(10);ui.caption(c,"Entities",pad,ht+dp(12),0xffd08060);
        float top=ht+dp(24),bottom=h,rowH=dp(48);
        float maxScroll=Math.max(0,ENTITY_LIST.length*rowH-(bottom-top)+dp(8));entScroll=Math.max(0,Math.min(maxScroll,entScroll));
        int save=c.save();c.clipRect(0,top,pw-dp(1),bottom);
        for(int i=0;i<ENTITY_LIST.length;i++){int t=ENTITY_LIST[i];float y=top+i*rowH-entScroll;if(y+rowH<top||y>bottom)continue;
            boolean sel=t==entType;
            if(sel){ui.round(c,dp(6),y+dp(2),pw-dp(6),y+rowH-dp(2),dp(8),Ui.ACCENT_SOFT);ui.outline(c,dp(6),y+dp(2),pw-dp(6),y+rowH-dp(2),dp(8),0xb0ff6a2a,dp(1));}
            ui.round(c,dp(12),y+dp(7),dp(46),y+rowH-dp(7),dp(6),Ui.BG);ui.icon(c,thingIcon(t),dp(29),y+rowH*.5f,18,thingColor(t));
            ui.text(c,ui.ellipsize(SectorMap.THING_NAMES[t],12.5f,1,pw-dp(64)),dp(54),y+dp(21),12.5f,Ui.TEXT,1,-1);
            String sub=t==SectorMap.T_PLAYER?"1 placed":count(t)+" placed";
            p.setColor(thingColor(t));c.drawCircle(dp(57),y+dp(32),dp(3),p);ui.text(c,sub,dp(64),y+dp(36),10.5f,Ui.TEXT_3);
            float vt=Math.max(top,y),vb=Math.min(bottom,y+rowH);if(vb>vt)ui.hit(0,vt,pw,vb,H_ENT+i);}
        c.restoreToCount(save);
    }
    private void drawCursorControls(Canvas c){
        if(!Hud.touchControls)return;   // mouse / controller: click the map instead (the d-pad still steps the cursor)
        float cx=dpadCX(),cy=dpadCY(),R=dpadR();
        p.setShader(null);p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);c.drawCircle(cx,cy+dp(3),R,p);p.setColor(0xe6201110);c.drawCircle(cx,cy,R,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.2f));p.setColor(0x70ff5a1a);c.drawCircle(cx,cy,R,p);p.setColor(0x30ff8a5a);c.drawCircle(cx,cy,R*.34f,p);p.setStyle(Paint.Style.FILL);
        if(dpadDirection>=0){p.setColor(0x50ff5a1a);r.set(cx-R,cy-R,cx+R,cy+R);c.drawArc(r,new float[]{225,315,45,135}[dpadDirection],90,true,p);p.setColor(0xe6201110);c.drawCircle(cx,cy,R*.34f,p);}
        int[] icons={Icons.CHEVRON_UP,Icons.CHEVRON_RIGHT,Icons.CHEVRON_DOWN,Icons.CHEVRON_LEFT};
        for(int i=0;i<4;i++){float ox=i==1?1:i==3?-1:0,oy=i==2?1:i==0?-1:0;ui.icon(c,icons[i],cx+ox*R*.67f,cy+oy*R*.67f,22,i==dpadDirection?0xffffb080:Ui.TEXT);}
        p.setColor(0x33ffffff);c.drawCircle(cx,cy,dp(3),p);
        float ax=applyCX(),ay=applyCY(),ar=applyR();
        p.setColor(0x66000000);c.drawCircle(ax,ay+dp(3),ar,p);p.setShadowLayer(dp(12),0,0,0xc0ff4000);p.setColor(Ui.ACCENT);c.drawCircle(ax,ay,ar,p);p.setShadowLayer(0,0,0,0);
        p.setShader(new android.graphics.RadialGradient(ax,ay-ar*.3f,ar,0xffffa060,0xffd8380a,android.graphics.Shader.TileMode.CLAMP));c.drawCircle(ax,ay,ar,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0x33ffffff);c.drawCircle(ax,ay,ar-dp(.5f),p);p.setStyle(Paint.Style.FILL);
        ui.icon(c,Icons.CHECK,ax,ay,26,0xffffffff);ui.hit(ax-ar-dp(6),ay-ar-dp(6),ax+ar+dp(6),ay+ar+dp(6),H_APPLY);
        String action=applyLabel();float tw=ui.measure(action,11.5f,1)+dp(22),pt=ay-ar-dp(40);
        ui.pill(c,ax+ar-tw,pt,ax+ar,pt+dp(26),0xf2241210);ui.textMid(c,action,ax+ar-tw*.5f,pt+dp(13),11.5f,Ui.TEXT,1,0);
    }
    private String applyLabel(){
        if(mode==ENTITIES){switch(entityTool){case 0:return "Place "+SectorMap.THING_NAMES[entType];case 1:return "Select entity";case 2:return entStage==0?"Pick entity":"Drop here";case 3:return "Rotate 45°";default:return "Delete entity";}}
        switch(tool){
            case DRAW:return pending.isEmpty()?"Add first corner":"Add corner · "+pending.size();
            case LINE:return stage==0?"Line start (on a wall)":"Line end · cut sector";
            case RECT:return stage==0?"First corner":"Opposite corner";case STAIRS:return "Make stairs";case SELECT:return "Select";
            case MOVE:return stage==0?"Pick to move":"Drop here";case SPLIT:return "Split edge here";
            case EXTRUDE:return stage==0?"Pick edge":"Extrude "+(pickSector>=0&&edgeCoords(pickSector,pickEdge)!=null?EditorViewFmt.f(extrudeDistance(edgeCoords(pickSector,pickEdge)))+" m":"");
            case DUPLICATE:return stage==0?"Pick sector":"Place copy";case DELETE:return "Delete sector";case SPECIAL:return "Set special";case START:return "Set player start";case PILLAR:return "Make pillar";
            default:return "Apply";}
    }

    // ---------------------------------------------------------------- geometry queries
    private int sectorUnderCursor(){return sectorNear(cursorX,cursorY);}
    private int sectorNear(float x,float y){SectorMap m=map();int s=m.sectorAt(x,y);if(s>=0)return s;float[][] off={{.15f,.15f},{-.15f,.15f},{.15f,-.15f},{-.15f,-.15f}};for(float[] o:off){s=m.sectorAt(x+o[0],y+o[1]);if(s>=0)return s;}return -1;}
    private int targetSector(){if((selKind==1||selKind==2)&&selSector>=0&&selSector<map().sectors.size())return selSector;return sectorUnderCursor();}
    private int vertexNear(float x,float y,float max){int best=-1;float bd=max;for(int i=0;i<map().vertices.size();i++){SectorMap.Vertex v=map().vertices.get(i);float d=(float)Math.hypot(v.x-x,v.y-y);if(d<bd){bd=d;best=i;}}return best;}
    /** Nearest edge as {sector, edgeIndex}, preferring the sector under the point. */
    private int[] edgeNear(float x,float y,float max){
        SectorMap m=map();int bs=-1,be=-1;float bd=max;int under=sectorNear(x,y);
        for(int s=0;s<m.sectors.size();s++){SectorMap.Sector sec=m.sectors.get(s);
            for(int j=0;j<sec.loop.size();j++){SectorMap.Vertex a=m.vertices.get(sec.loop.get(j)),b=m.vertices.get(sec.loop.get((j+1)%sec.loop.size()));
                float d=SectorMap.segDist(x,y,a.x,a.y,b.x,b.y)-(s==under?.001f:0);if(d<bd){bd=d;bs=s;be=j;}}}
        return bs<0?null:new int[]{bs,be};
    }
    private float[] edgeCoords(int s,int e){SectorMap m=map();if(s<0||s>=m.sectors.size())return null;SectorMap.Sector sec=m.sectors.get(s);if(e<0||e>=sec.loop.size())return null;
        SectorMap.Vertex a=m.vertices.get(sec.loop.get(e)),b=m.vertices.get(sec.loop.get((e+1)%sec.loop.size()));return new float[]{a.x,a.y,b.x,b.y};}
    private float[] edgeNormal(int s,int e){float[] c=edgeCoords(s,e);SectorMap.Sector sec=map().sectors.get(s);float area=map().area(sec);
        float dx=c[2]-c[0],dy=c[3]-c[1],len=(float)Math.hypot(dx,dy),sign=area>0?1:-1;return new float[]{sign*dy/len,-sign*dx/len};}
    private float extrudeDistance(float[] e){float[] n=edgeNormal(pickSector,pickEdge);float d=(cursorX-e[0])*n[0]+(cursorY-e[1])*n[1];return Math.max(grid,Math.round(Math.abs(d)/grid)*grid);}

    // ---------------------------------------------------------------- Sectors: cursor tools
    private void setTool(int t){cancelStage();tool=t;
        switch(t){case DRAW:hint("Move the cursor and Apply on each corner · Apply on the first corner to close");break;
            case LINE:hint("Apply on a wall, then on the opposite wall: the sector is cut in two");break;
            case RECT:hint("Apply on one corner, then the opposite corner");break;case STAIRS:hint("Apply on a rectangular sector; stairs rise away from the cursor");break;
            case SELECT:hint("Apply selects the vertex, edge or sector under the cursor");break;case MOVE:hint("Apply to pick a vertex, edge or sector, move, then Apply to drop");break;
            case SPLIT:hint("Put the cursor on an edge and Apply to split it");break;case EXTRUDE:hint("Apply on an edge, move outwards, Apply again");break;
            case DUPLICATE:hint("Apply on a sector, move, Apply to place the copy");break;case DELETE:hint("Apply on a sector to delete it");break;
            case SPECIAL:hint("Apply on a sector to make it lava, an exit, a secret…");break;case START:hint("Apply to move the player start; Apply again to turn it");break;
            case PILLAR:hint("Apply on a sector drawn inside a room to raise it into a solid pillar");break;}}
    private void cancelStage(){if(stage==1&&tool==MOVE)restoreMove();stage=0;pickSector=-1;pickEdge=-1;moveIds=new int[0];}
    private void restoreMove(){for(int i=0;i<moveIds.length;i++){if(moveIds[i]>=map().vertices.size())continue;SectorMap.Vertex v=map().vertices.get(moveIds[i]);v.x=moveOrig[i*2];v.y=moveOrig[i*2+1];}}
    private void previewMove(){float dx=cursorX-anchorX,dy=cursorY-anchorY;for(int i=0;i<moveIds.length;i++){if(moveIds[i]>=map().vertices.size())continue;SectorMap.Vertex v=map().vertices.get(moveIds[i]);v.x=moveOrig[i*2]+dx;v.y=moveOrig[i*2+1]+dy;}}
    private void cursorApply(){
        if(mode==ENTITIES){entityApply();return;}
        SectorMap m=map();float cx=cursorX,cy=cursorY;
        switch(tool){
            case DRAW:{float[] pt=ed.snapPoint(cx,cy,grid);
                if(pending.size()>=3&&Math.hypot(pt[0]-pending.get(0)[0],pt[1]-pending.get(0)[1])<.01){closeSector();return;}
                for(float[] v:pending)if(Math.hypot(v[0]-pt[0],v[1]-pt[1])<.01){hint("Corner already added");return;}
                pending.add(pt);hint(pending.size()<3?"Corner "+pending.size()+" added":"Corner "+pending.size()+" added · Apply on the first corner or tap Close");return;}
            case LINE:{float[] pt=ed.snapPoint(cx,cy,grid);
                if(stage==0){anchorX=pt[0];anchorY=pt[1];stage=1;hint("Now Apply on the far wall");return;}
                stage=0;if(ed.splitSector(anchorX,anchorY,pt[0],pt[1])){selKind=0;changed("Sector cut in two");}else hint(ed.error);return;}
            case RECT:
                if(stage==0){anchorX=cx;anchorY=cy;stage=1;hint("Move to the opposite corner, then Apply");}
                else{stage=0;SectorMap.Sector s=ed.rectangle(anchorX,anchorY,cx,cy);if(s!=null){selectSector(m.sectors.indexOf(s));changed("Rectangle created");}else hint(ed.error);}
                return;
            case STAIRS:{int s=sectorUnderCursor();if(s<0){hint("Move the cursor onto a sector");return;}
                if(ed.makeStairs(s,cx,cy,.25f,.5f)){selKind=0;changed("Stairs built (0.25 m steps)");}else hint(ed.error);return;}
            case SELECT:{
                int v=vertexNear(cx,cy,.3f);if(v>=0){selKind=3;selVertex=v;hint("Vertex "+(v+1)+" selected");return;}
                int[] e=edgeNear(cx,cy,.05f);if(e!=null){selKind=2;selSector=e[0];selEdge=e[1];hint("Edge selected");return;}
                int s=sectorUnderCursor();if(s>=0){selectSector(s);hint("Sector "+(s+1)+" selected");}else{selKind=0;hint("Nothing under the cursor");}return;}
            case MOVE:{
                if(stage==1){int[] ids=moveIds;float dx=cursorX-anchorX,dy=cursorY-anchorY;restoreMove();stage=0;
                    if(dx==0&&dy==0){hint("Move cancelled");return;}
                    if(ed.moveVertices(ids,dx,dy))changed("Moved");else hint(ed.error.isEmpty()?"Can't move there":ed.error);return;}
                int v=vertexNear(cx,cy,.3f);int[] ids;
                if(v>=0){ids=new int[]{v};selKind=3;selVertex=v;}
                else{int[] e=edgeNear(cx,cy,.05f);
                    if(e!=null){SectorMap.Sector sec=m.sectors.get(e[0]);ids=new int[]{sec.loop.get(e[1]),sec.loop.get((e[1]+1)%sec.loop.size())};pickSector=e[0];pickEdge=e[1];}
                    else{int s=sectorUnderCursor();if(s<0){hint("Nothing to move here");return;}SectorMap.Sector sec=m.sectors.get(s);ids=new int[sec.loop.size()];for(int i=0;i<ids.length;i++)ids[i]=sec.loop.get(i);selectSector(s);}}
                moveIds=ids;moveOrig=new float[ids.length*2];for(int i=0;i<ids.length;i++){SectorMap.Vertex vv=m.vertices.get(ids[i]);moveOrig[i*2]=vv.x;moveOrig[i*2+1]=vv.y;}
                anchorX=cx;anchorY=cy;stage=1;hint("Move the cursor, then Apply to drop");return;}
            case SPLIT:{int[] e=edgeNear(cx,cy,.05f);if(e==null){hint("Put the cursor exactly on an edge");return;}
                if(ed.splitWall(e[0],e[1],cx,cy))changed("Edge split");else hint(ed.error);return;}
            case EXTRUDE:
                if(stage==0){int[] e=edgeNear(cx,cy,.3f);if(e==null){hint("Move the cursor onto an edge");return;}pickSector=e[0];pickEdge=e[1];stage=1;hint("Move outwards, then Apply");}
                else{float d=extrudeDistance(edgeCoords(pickSector,pickEdge));stage=0;int n=m.sectors.size();if(ed.extrude(pickSector,pickEdge,d)){selectSector(n);changed("Extruded");}else hint(ed.error.isEmpty()?"Can't extrude there":ed.error);pickSector=-1;}
                return;
            case DUPLICATE:
                if(stage==0){int s=sectorUnderCursor();if(s<0){hint("Move the cursor onto a sector");return;}pickSector=s;anchorX=cx;anchorY=cy;stage=1;hint("Move to the new place, then Apply");}
                else{stage=0;int n=m.sectors.size();if(ed.duplicateSector(pickSector,cx-anchorX,cy-anchorY)){selectSector(n);changed("Sector duplicated");}else hint(ed.error.isEmpty()?"Copy would overlap":ed.error);pickSector=-1;}
                return;
            case DELETE:{int s=sectorUnderCursor();if(s<0){hint("Move the cursor onto a sector");return;}if(ed.deleteSector(s)){selKind=0;changed("Sector deleted");}return;}
            case SPECIAL:{int s=sectorUnderCursor();if(s<0){hint("Move the cursor onto a sector");return;}selectSector(s);chooseSpecial(s);return;}
            case PILLAR:{int s=sectorUnderCursor();if(s<0){hint("Move the cursor onto a sector");return;}if(m.parent[s]<0){hint("Draw the pillar inside a room first");return;}
                if(ed.makePillar(s)){selKind=0;changed("Pillar raised");}return;}
            case START:{if(m.sectorAt(cx,cy)<0){hint("The player start must be inside a sector");return;}
                if(Math.hypot(m.playerX-cx,m.playerY-cy)<.01f){ed.rotateThing(-2,(float)Math.PI/4);changed("Player start turned");}else{ed.placeThing(SectorMap.T_PLAYER,cx,cy,0);changed("Player start moved");}return;}
        }
    }
    private void selectSector(int s){selKind=s>=0?1:0;selSector=s;}
    private void chooseSpecial(int s){
        SectorMap.Sector sec=map().sectors.get(s);
        choose("Sector "+(s+1)+" special",SectorMap.SPECIAL_NAMES,sec.special,i->{if(ed.setSpecial(s,i,0))changed(SectorMap.SPECIAL_NAMES[i]);});
    }
    private void changed(String msg){autoSave();if(msg!=null)hint(msg);invalidate();}
    private void closeSector(){int n=map().sectors.size();if(ed.addSector(new ArrayList<>(pending))!=null){selectSector(n);pending.clear();changed("Sector created");}else hint(ed.error);}
    private void sectorAction(int t){
        if(t<100){if(tool==t&&stage==0&&t!=DRAW)hint(toolName(t)+" is active");setTool(t);return;}
        SectorMap m=map();
        switch(t){
            case CLOSE:if(pending.size()<3)hint("Add at least three corners first");else closeSector();return;
            case UNDO_PT:if(!pending.isEmpty()){pending.remove(pending.size()-1);hint("Corner removed");}else hint("No corners to remove");return;
            case CANCEL:pending.clear();cancelStage();hint("Cancelled");return;
            case UNDO:case REDO:{cancelStage();if(t==UNDO?ed.undo():ed.redo()){selKind=0;pending.clear();changed(t==UNDO?"Undo":"Redo");}else hint(t==UNDO?"Nothing to undo":"Nothing to redo");return;}
            case VALIDATE:hint(ed.validateMap());return;
            case CLEAN:{int n=ed.cleanOrphans();if(n>0)changed(null);hint("Cleaned "+n+" unused vertices");return;}
            case DESELECT:selKind=0;hint("Selection cleared");return;
            case GRID:{grid=grid==1?.5f:grid==.5f?2:1;cursorX=MapEditor.snap(cursorX,grid);cursorY=MapEditor.snap(cursorY,grid);hint("Grid "+EditorViewFmt.f(grid)+" m");return;}
        }
        int s=targetSector();
        if(s<0){hint("Move the cursor onto a sector, or select one");return;}
        SectorMap.Sector sec=m.sectors.get(s);boolean ok=true;
        switch(t){
            case FLOOR_UP:ok=ed.changeHeight(s,.25f,0);break;case FLOOR_DN:ok=ed.changeHeight(s,-.25f,0);break;
            case CEIL_UP:ok=ed.changeHeight(s,0,.25f);break;case CEIL_DN:ok=ed.changeHeight(s,0,-.25f);break;
            case LAVA:{boolean on=sec.special==SectorMap.SP_LAVA;ok=ed.setSpecial(s,on?SectorMap.SP_NONE:SectorMap.SP_LAVA,0);if(on&&ok)ed.setTexture(s,1,Tex.FLOOR_TILES);break;}
            case HEIGHTS:{prompt("Floor and ceiling (m)","e.g. 0 4",EditorViewFmt.f(sec.floor)+" "+EditorViewFmt.f(sec.ceil),txt->{
                    try{String[] a=txt.trim().split("[\\s,]+");if(ed.setHeights(s,Float.parseFloat(a[0]),Float.parseFloat(a[1])))changed("Heights set");else hint(ed.error);}catch(Exception e){hint("Type two numbers: floor ceiling");}});return;}
            case FLOOR_TEX:case CEIL_TEX:case WALL_TEX:{int surf=t==FLOOR_TEX?1:t==CEIL_TEX?2:0;int cur=surf==1?sec.floorTex:surf==2?sec.ceilTex:sec.wallTex;
                int sel=-1;for(int i=0;i<SectorMap.MAP_TEXTURES.length;i++)if(SectorMap.MAP_TEXTURES[i]==cur)sel=i;
                Overlay.get().pickTexture(toolName(t)+" · sector "+(s+1),sel,i->{ed.setTexture(s,surf,SectorMap.MAP_TEXTURES[i]);changed(Tex.NAMES[SectorMap.MAP_TEXTURES[i]]);invalidate();});return;}
        }
        if(ok)changed(toolName(t)+" · sector "+(s+1)+": floor "+EditorViewFmt.f(sec.floor)+", ceiling "+EditorViewFmt.f(sec.ceil));else hint(ed.error);
    }

    // ---------------------------------------------------------------- Entities: cursor tools
    private void entityApply(){
        SectorMap m=map();float cx=cursorX,cy=cursorY;
        switch(entityTool){
            case 0:{if(ed.placeThing(entType,cx,cy,entType==SectorMap.T_DEMON?(float)Math.PI*1.5f:0)){selThing=entType==SectorMap.T_PLAYER?-2:m.things.size()-1;changed(SectorMap.THING_NAMES[entType]+" placed");}else hint(ed.error);return;}
            case 1:{int i=ed.thingNear(cx,cy,.6f);selThing=i;if(i>=0){entType=m.things.get(i).type;hint(SectorMap.THING_NAMES[entType]+" selected");}else if(i==-2){entType=SectorMap.T_PLAYER;hint("Player start selected");}else hint("No entity under the cursor");return;}
            case 2:{
                if(entStage==0){int i=ed.thingNear(cx,cy,.6f);if(i==-1){hint("No entity under the cursor");return;}selThing=i;entStage=1;hint("Move the cursor, then Apply to drop");return;}
                entStage=0;if(ed.moveThing(selThing,cx,cy))changed("Entity moved");else hint(ed.error);return;}
            case 3:{int i=ed.thingNear(cx,cy,.6f);if(i==-1){hint("No entity under the cursor");return;}selThing=i;ed.rotateThing(i,(float)Math.PI/4);changed("Turned 45°");return;}
            default:{int i=ed.thingNear(cx,cy,.6f);if(ed.deleteThing(i)){selThing=-1;changed("Entity deleted");}else hint(ed.error);}
        }
    }

    // ---------------------------------------------------------------- saving, dialogs
    void autoSave(){try{mapId=store.save(mapId,map());}catch(Exception e){hint("Couldn't save: "+e.getMessage());}}
    interface Choice {void pick(int i);}interface Text {void got(String s);}
    private void choose(String title,String[] items,int sel,Choice c){
        Overlay.get().choose(title,items,sel,i->{c.pick(i);invalidate();});
    }
    private void prompt(String title,String msg,String value,Text cb){
        Overlay.get().prompt(msg!=null?title+" · "+msg:title,value,s->{cb.got(s);invalidate();});
    }

    // ---------------------------------------------------------------- touch
    private int dpadHit(float x,float y){if(!Hud.touchControls)return -1;float dx=x-dpadCX(),dy=y-dpadCY();float d=(float)Math.hypot(dx,dy);if(d>dpadR()*1.15f||d<dpadR()*.2f)return -1;
        if(Math.abs(dx)>Math.abs(dy))return dx>0?1:3;return dy>0?2:0;}
    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked(),idx=e.getActionIndex();float x=e.getX(idx),y=e.getY(idx);
        if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN){
            downX=lastX=x;downY=lastY=y;dragged=false;scrollTarget=0;
            int hid=ui.hitAt(x,y);
            if(inMap(x,y)){
                int dir=dpadHit(x,y);
                if(dir>=0){dpadPointer=e.getPointerId(idx);dpadDirection=dir;cursorStep(dir);nextDpadRepeat=System.currentTimeMillis()+270;postDpadRepeat();return true;}
                if(hid==H_APPLY){cursorApply();invalidate();return true;}
                if(Pointer.dispatching&&hid<0){pointerHover(x,y);cursorApply();invalidate();return true;}   // the global cursor (mouse / controller): click to Apply right there
                return true;   // cursor-only editing with a finger: other touches on the map are ignored
            }
            if(mode==ENTITIES&&x<entPanelW()&&y>toolbarH)scrollTarget=1;
            return true;
        }
        if(a==MotionEvent.ACTION_MOVE){
            if(dpadPointer>=0){int pi=e.findPointerIndex(dpadPointer);if(pi>=0){int dir=dpadHit(e.getX(pi),e.getY(pi));if(dir!=dpadDirection){dpadDirection=dir;if(dir>=0){cursorStep(dir);nextDpadRepeat=System.currentTimeMillis()+270;}invalidate();}}return true;}
            if(Math.abs(x-downX)+Math.abs(y-downY)>dp(8))dragged=true;
            if(dragged&&scrollTarget==1){entScroll-=(y-lastY);invalidate();}
            lastX=x;lastY=y;return true;
        }
        if(a==MotionEvent.ACTION_POINTER_UP){if(e.getPointerId(idx)==dpadPointer){dpadPointer=-1;dpadDirection=-1;invalidate();}return true;}
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){
            if(dpadPointer>=0){dpadPointer=-1;dpadDirection=-1;invalidate();return true;}
            if(a==MotionEvent.ACTION_UP&&!dragged)tap(ui.hitAt(x,y));
            scrollTarget=0;invalidate();return true;
        }
        return true;
    }
    private void tap(int hid){
        if(hid==H_BACK){autoSave();host.exitEditor();return;}
        if(hid==H_SAVE){autoSave();hint("Saved");return;}
        if(hid==H_PLAY){String v=ed.validateMap();if(!v.equals("Geometry valid")){hint(v);return;}autoSave();host.testPlay(map().copy());return;}
        if(hid==H_NAME){prompt("Map name",null,map().name,t->{if(!t.trim().isEmpty()){map().name=t.trim();changed("Renamed");}});return;}
        if(hid==H_TAB+2){autoSave();cancelStage();pending.clear();host.open3D();return;}
        if(hid==H_TAB||hid==H_TAB+1){mode=hid-H_TAB;cancelStage();pending.clear();hint(mode==SECTORS?"Pick a tool, move the cursor, tap Apply":"Pick an entity, then Apply to place it");return;}
        if(hid==H_UNDO||hid==H_REDO){sectorAction(hid==H_UNDO?UNDO:REDO);return;}
        if(hid==H_ZOOMIN||hid==H_ZOOMOUT){zoom=Math.max(.3f,Math.min(4f,zoom*(hid==H_ZOOMIN?1.25f:.8f)));return;}
        if(hid>=H_PAGE&&hid<H_PAGE+3){toolPage=hid-H_PAGE;return;}
        if(hid>=H_TOOL&&hid<H_TOOL+PAGES[toolPage].length&&mode==SECTORS){sectorAction(PAGES[toolPage][hid-H_TOOL]);return;}
        if(hid>=H_ENT&&hid<H_ENT+ENTITY_LIST.length){entType=ENTITY_LIST[hid-H_ENT];entityTool=0;entStage=0;hint(SectorMap.THING_NAMES[entType]+" · Apply to place");return;}
        if(hid>=H_SKILLF&&hid<H_SKILLF+3&&selThing>=0&&selThing<map().things.size()){SectorMap.Thing t=map().things.get(selThing);
            if(ed.setThingSkills(selThing,t.skills^(1<<(hid-H_SKILLF))))changed(null);else{hint(ed.error);return;}
            String on=((t.skills&1)!=0?"Easy ":"")+((t.skills&2)!=0?"Normal ":"")+((t.skills&4)!=0?"Hard":"");hint("Appears on: "+on.trim());return;}
        if(hid>=H_ETOOL&&hid<H_ETOOL+5){entityTool=hid-H_ETOOL;entStage=0;hint(ENTITY_TOOLS[entityTool]+" · move the cursor and tap Apply");}
    }
    private void postDpadRepeat(){
        postDelayed(new Runnable(){public void run(){
            if(dpadPointer<0||dpadDirection<0)return;
            if(System.currentTimeMillis()>=nextDpadRepeat){cursorStep(dpadDirection);nextDpadRepeat=System.currentTimeMillis()+90;}
            postDelayed(this,30);}},30);
    }
    // ---------------------------------------------------------------- the global cursor (mouse / controller)
    private boolean follow=true;private float hoverLX=Float.NaN,hoverLY=Float.NaN;
    @Override public Ui pointerUi(){return ui;}
    /** Over the open map the map cursor follows the pointer, snapped to the grid (the view stops chasing it). */
    @Override public void pointerHover(float x,float y){
        if(x==hoverLX&&y==hoverLY&&!Pointer.dispatching)return;hoverLX=x;hoverLY=y;   // only when the pointer moves (the d-pad may have stepped the cursor)
        if(!inMap(x,y)||dpadHit(x,y)>=0||ui.hitAt(x,y)>=0)return;
        float s=snap(),nx=Math.max(-500,Math.min(500,MapEditor.snap(wx(x),s))),ny=Math.max(-500,Math.min(500,MapEditor.snap(wy(y),s)));
        follow=false;if(nx==cursorX&&ny==cursorY)return;cursorX=nx;cursorY=ny;
        if(stage==1&&tool==MOVE&&mode==SECTORS)previewMove();invalidate();
    }
    @Override public void pointerPan(float dx,float dy){if(cell<=0)return;follow=false;viewX+=dx/cell;viewY-=dy/cell;invalidate();}
    @Override public void pointerZoom(float steps,float x,float y){
        if(cell<=0)return;float bx=wx(x),by=wy(y);zoom=Math.max(.3f,Math.min(4f,zoom*(float)Math.pow(1.25f,steps)));cell=(mapB()-mapT())/16f*zoom;
        if(inMap(x,y)){viewX+=bx-wx(x);viewY+=by-wy(y);}follow=false;invalidate();}
    @Override public void pointerShortcut(char k){if(k=='z')tap(H_UNDO);else if(k=='y')tap(H_REDO);else if(k=='s')tap(H_SAVE);invalidate();}
    @Override public boolean pointerDpad(int dx,int dy){
        if(Float.isNaN(Ui.hoverX)||!inMap(Ui.hoverX,Ui.hoverY))return false;
        cursorStep(dx>0?1:dx<0?3:dy<0?0:2);return true;}
    private void cursorStep(int direction){
        float s=snap();follow=true;
        if(direction==0)cursorY+=s;if(direction==1)cursorX+=s;if(direction==2)cursorY-=s;if(direction==3)cursorX-=s;
        cursorX=Math.max(-500,Math.min(500,MapEditor.snap(cursorX,s)));cursorY=Math.max(-500,Math.min(500,MapEditor.snap(cursorY,s)));
        if(stage==1&&tool==MOVE&&mode==SECTORS)previewMove();
        invalidate();
    }
    /** Back key: cancel what's in progress, otherwise leave. */
    boolean back(){if(stage!=0||!pending.isEmpty()||entStage!=0){cancelStage();pending.clear();entStage=0;hint("Cancelled");return true;}autoSave();host.exitEditor();return true;}
}

/** Number formatting shared by the editor views. */
final class EditorViewFmt {
    static String f(float v){if(Math.abs(v-Math.round(v))<.001f)return String.valueOf(Math.round(v));return String.format(java.util.Locale.US,"%.2f",v).replaceAll("0+$","");}
}
