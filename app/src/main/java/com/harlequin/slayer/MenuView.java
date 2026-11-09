package com.harlequin.slayer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Random;

/**
 * The menus: the title screen (Campaign, Custom levels, Level editor, Settings, Quit) over an animated hellscape,
 * and the in-game overlays (pause, level complete). Drawn on a canvas with the editor's theme helpers.
 */
final class MenuView extends View implements Pointer.Aware {
    interface Host {
        void playCampaign(int index);void playCustom(String id);void editMap(String id,SectorMap from);void quit();
        void resume();void restart();void leaveGame();void nextLevel();
    }
    /** The window's display modes (the Windows build sets this; phones have none). */
    interface Display {String[] modes();int mode();void setMode(int i);}
    static Display display;
    static final int MAIN=0,CAMPAIGN=1,CUSTOM=2,SETTINGS=3,PAUSE=4,COMPLETE=5,SKILL=6;
    private static final int H_ITEM=100,H_BACK=1,H_NEW=2,H_PLAY=300,H_EDIT=400,H_DEL=500,H_SET=600,H_SKILL=700,H_EDITC=800,H_PLAYC=900,H_CONT=3;
    /** What to play once a skill is chosen: a campaign map, or a custom level's id. */
    private int pendingCampaign=-1;private String pendingCustom;private int skillFrom=MAIN;

    private final Ui ui;private final Paint p,glow=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private final RectF r=new RectF(),hv=new RectF();
    private final Host host;private final MapStore store;private final SharedPreferences prefs;
    int page=MAIN;
    /** Overlay details: whether the game came from the editor, completion stats, whether there is a next map. */
    boolean fromEditor,hasNext,finale;String doneName="";int kills,total;float seconds;
    private ArrayList<MapStore.Entry> customs=new ArrayList<>();private float scroll;private int pressed=-1;
    private final Random rng=new Random(7);private final float[][] embers=new float[90][];
    private long t0=System.currentTimeMillis(),last=System.nanoTime();
    private float downY,lastY;private boolean dragged;

    MenuView(Context c,Host host,MapStore store){
        super(c);this.host=host;this.store=store;ui=new Ui(c);p=ui.p;prefs=c.getSharedPreferences("slayer",0);
        for(int i=0;i<embers.length;i++)embers[i]=new float[]{rng.nextFloat(),rng.nextFloat(),.2f+rng.nextFloat(),rng.nextFloat()*6.28f};
        glow.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));glow.setTextAlign(Paint.Align.LEFT);
        loadSettings(c);
    }
    private float dp(float v){return ui.dp(v);}
    void show(int pg){page=pg;scroll=0;pressed=-1;if(pg==CUSTOM)customs=store.list();invalidate();}

    // ---------------------------------------------------------------- settings
    static void loadSettings(Context c){SharedPreferences s=c.getSharedPreferences("slayer",0);
        Skill.current=Math.max(0,Math.min(4,s.getInt("skill",Skill.MEDIUM)));Game.lookSens=s.getFloat("sens",.0055f);Game.invertLook=s.getBoolean("invert",false);Game.RENDER_LINES=s.getInt("lines",270);Hud.showFps=s.getBoolean("fps",true);Game.aimAssist=s.getBoolean("aim",true);
        Music.volume=s.getFloat("music",.8f);Narrator.enabled=s.getBoolean("narration",true);}
    /** The furthest campaign map she has reached (maps up to it can be played). */
    static int unlocked(Context c){return Math.max(0,Math.min(Maps.count()-1,c.getSharedPreferences("slayer",0).getInt("campaign",0)));}
    /** Map `next` is open now (she finished the one before it). */
    static void unlock(Context c,int next){SharedPreferences s=c.getSharedPreferences("slayer",0);int n=Math.min(next,Maps.count()-1);
        SharedPreferences.Editor e=s.edit();if(n>s.getInt("campaign",0))e.putInt("campaign",n);if(next>=Maps.count())e.putBoolean("finished",true);e.apply();}
    private void saveSettings(){prefs.edit().putFloat("sens",Game.lookSens).putBoolean("invert",Game.invertLook).putInt("lines",Game.RENDER_LINES).putBoolean("fps",Hud.showFps).putBoolean("aim",Game.aimAssist).putFloat("music",Music.volume).putBoolean("narration",Narrator.enabled).apply();}

    // ---------------------------------------------------------------- drawing
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();ui.clearHits();
        long now=System.nanoTime();float dt=Math.min(.05f,(now-last)/1e9f);last=now;float t=(System.currentTimeMillis()-t0)/1000f;
        boolean overlay=page==PAUSE||page==COMPLETE;
        if(overlay){c.drawColor(0xb0080404);}
        else backdrop(c,w,h,t,dt);
        switch(page){
            case MAIN:title(c,w,h,t);mainMenu(c,w,h);break;
            case CAMPAIGN:header(c,w,"CAMPAIGN","The story of Slayer, in eight maps");campaign(c,w,h);break;
            case CUSTOM:header(c,w,"CUSTOM LEVELS","Levels you have built in the editor");custom(c,w,h);break;
            case SETTINGS:header(c,w,"SETTINGS","Controls, graphics and music");settings(c,w,h);break;
            case SKILL:header(c,w,"CHOOSE YOUR SKILL","As in Doom: the harder the skill, the more demons");skills(c,w,h);break;
            case PAUSE:pause(c,w,h);break;
            case COMPLETE:complete(c,w,h);break;
        }
        if(!overlay||page==COMPLETE)postInvalidateOnAnimation();
    }
    /** A hellscape: a furnace glow on the horizon, jagged spires, drifting embers, a vignette. */
    private void backdrop(Canvas c,float w,float h,float t,float dt){
        p.setShader(new LinearGradient(0,0,0,h,new int[]{0xff070304,0xff1a0604,0xff4a0e04,0xff8a2406},new float[]{0,.5f,.82f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
        float pulse=.85f+.15f*(float)Math.sin(t*.9f);
        p.setShader(new RadialGradient(w*.62f,h*1.05f,h*.9f*pulse,0x99ff5a10,0x00ff3000,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
        // spires: three layers, darkest in front
        int[] cols={0xff2a0a06,0xff1a0604,0xff0c0303};float[] base={.78f,.84f,.9f};
        for(int L=0;L<3;L++){path.reset();path.moveTo(0,h);Random g=new Random(31+L*7);float x=-dp(20);float drift=(t*(4+L*6))%dp(200);
            path.lineTo(x,h*base[L]);
            while(x<w+dp(200)){float wdt=dp(30+g.nextInt(70));float ht=h*(.06f+g.nextFloat()*(L==2?.28f:.18f));float px=x-drift*.0f;
                path.lineTo(px+wdt*.35f,h*base[L]-ht*.4f);path.lineTo(px+wdt*.45f,h*base[L]-ht);path.lineTo(px+wdt*.55f,h*base[L]-ht*.45f);path.lineTo(px+wdt,h*base[L]);x+=wdt;}
            path.lineTo(w,h);path.close();p.setColor(cols[L]);c.drawPath(path,p);}
        // embers
        for(float[] e:embers){e[1]-=dt*e[2]*.08f;e[0]+=dt*.02f*(float)Math.sin(t*1.3f+e[3]);if(e[1]<-.02f){e[1]=1.02f;e[0]=rng.nextFloat();}
            float a=Math.min(1,e[1]*1.4f)*(.5f+.5f*(float)Math.sin(t*4+e[3]));p.setColor(((int)(a*220)<<24)|0xffa040);c.drawCircle(e[0]*w,e[1]*h,dp(1+e[2]*1.6f),p);}
        p.setShader(new RadialGradient(w*.5f,h*.5f,Math.max(w,h)*.75f,0x00000000,0xc0000000,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
    }
    private void title(Canvas c,float w,float h,float t){
        float x=dp(48),y=h*.38f;
        float s1=Math.min(dp(96),w*.11f);glow.setTextSize(s1);
        glow.setShadowLayer(dp(18),0,0,0xffff3a00);glow.setColor(0xffff5a1a);c.drawText("SLAYER",x,y,glow);
        glow.setShadowLayer(0,0,0,0);glow.setShader(new LinearGradient(0,y-s1,0,y,0xffffd9a0,0xffd8300a,Shader.TileMode.CLAMP));c.drawText("SLAYER",x,y,glow);glow.setShader(null);
        float s2=s1*.42f;glow.setTextSize(s2);glow.setLetterSpacing(.35f);glow.setColor(0xffe8c8a8);glow.setShadowLayer(dp(8),0,0,0xffb02000);c.drawText("FROM HELL",x+dp(4),y+s2*1.35f,glow);glow.setShadowLayer(0,0,0,0);glow.setLetterSpacing(0);
        ui.rect(c,x+dp(4),y+s2*1.35f+dp(14),x+dp(4)+s1*2.2f,y+s2*1.35f+dp(16),0x80ff5a1a);
        ui.text(c,"A third-person shooter · build your own levels",x+dp(4),y+s2*1.35f+dp(38),13,0xb0e0c0a0,0,-1);
    }
    private void header(Canvas c,float w,String t,String sub){
        r.set(dp(20),dp(20),dp(64),dp(64));ui.button(c,r,Icons.ARROW_LEFT,null,Ui.NORMAL);ui.hit(r,H_BACK);
        glow.setTextSize(dp(30));glow.setColor(0xffff6a2a);glow.setShadowLayer(dp(10),0,0,0xffc02000);c.drawText(t,dp(80),dp(50),glow);glow.setShadowLayer(0,0,0,0);
        ui.text(c,sub,dp(82),dp(70),12.5f,0xb0e0c0a0,0,-1);
    }
    /** A big menu entry: dark glass panel, accent bar, icon, label and a line of detail. */
    @Override public Ui pointerUi(){return ui;}
    @Override public void pointerPan(float dx,float dy){if(page==CUSTOM||page==CAMPAIGN){scroll+=dy;invalidate();}}
    @Override public void pointerZoom(float steps,float x,float y){if(page==CUSTOM||page==CAMPAIGN){scroll-=steps*dp(72);invalidate();}}
    private void entry(Canvas c,float l,float t,float rr,float b,int icon,String label,String sub,int id,boolean primary){
        boolean down=pressed==id;hv.set(l,t,rr,b);primary|=Ui.hovered(hv);
        ui.round(c,l,t,rr,b,dp(10),down?0xe03a120a:primary?0xd0281008:0xb0140808);
        ui.outline(c,l,t,rr,b,dp(10),down||primary?0xa0ff6a2a:0x40ffffff,dp(1));
        ui.rect(c,l+dp(1),t+dp(10),l+dp(4),b-dp(10),primary?0xffff5a1a:0x80ff6a2a);
        float cy=(t+b)*.5f;ui.icon(c,icon,l+dp(32),cy,22,primary?0xffffb080:0xffe0c0a0);
        ui.text(c,label,l+dp(58),sub==null?cy+dp(6):cy-dp(1),17,0xfff4e8e0,2,-1);
        if(sub!=null)ui.text(c,sub,l+dp(58),cy+dp(16),11.5f,0xa0e0c0a0,0,-1);
        ui.hit(l,t,rr,b,id);
    }
    private static final String[] MAIN_ITEMS={"Campaign","Custom levels","Level editor","Settings","Quit"};
    private static final String[] MAIN_SUBS={"Eight maps · the story of Slayer","Play the levels you've built","Build a level with sectors and entities","Look speed, graphics, music, narration","Leave the game"};
    private static final int[] MAIN_ICONS={Icons.PLAY,Icons.FOLDER,Icons.SECTORS,Icons.LAYERS,Icons.CANCEL};
    private void mainMenu(Canvas c,float w,float h){
        float bw=Math.min(dp(360),w*.42f),l=w-bw-dp(40),rr=w-dp(40),bh=Math.min(dp(62),(h-dp(80))/5.6f),gap=dp(10);
        float top=(h-(bh*5+gap*4))*.5f;
        for(int i=0;i<5;i++){float t=top+i*(bh+gap);entry(c,l,t,rr,t+bh,MAIN_ICONS[i],MAIN_ITEMS[i],i==3&&display!=null?"Display, look speed, graphics, music":MAIN_SUBS[i],H_ITEM+i,i==0);}
        ui.text(c,"v0.2 · sector maps",dp(48),h-dp(20),10.5f,0x70e0c0a0,0,-1);
    }
    private void campaign(Canvas c,float w,float h){
        int open=unlocked(getContext());boolean done=prefs.getBoolean("finished",false);
        float l=dp(80),rr=Math.min(w-dp(40),l+dp(620)),bh=Math.min(dp(70),Math.max(dp(56),h*.17f)),gap=dp(10),top=dp(92);
        r.set(rr-dp(210),dp(26),rr,dp(62));ui.button(c,r,Icons.PLAY,open==0&&!done?"Begin":"Continue · Map "+(open+1),Ui.PRIMARY);ui.hit(r,H_CONT);
        float list=Maps.count()*(bh+gap)+dp(36),maxS=Math.max(0,list-(h-top-dp(6)));scroll=Math.max(0,Math.min(maxS,scroll));
        int save=c.save();c.clipRect(0,top-dp(4),w,h);
        for(int i=0;i<Maps.count();i++){float t=top+i*(bh+gap)-scroll;if(t+bh<top-dp(4)||t>h)continue;
            if(i>open){   // locked: the name is known, the way is not
                ui.round(c,l,t,rr,t+bh,dp(10),0x70100606);ui.outline(c,l,t,rr,t+bh,dp(10),0x20ffffff,dp(1));
                float cx=l+dp(32),cy=t+bh*.5f;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2.2f));p.setColor(0x80e0c0a0);
                r.set(cx-dp(6),cy-dp(13),cx+dp(6),cy+dp(1));c.drawArc(r,180,180,false,p);p.setStyle(Paint.Style.FILL);ui.round(c,cx-dp(9),cy-dp(5),cx+dp(9),cy+dp(9),dp(2),0x80e0c0a0);
                ui.text(c,"MAP "+(i+1)+" · "+Maps.CAMPAIGN[i].toUpperCase(),l+dp(58),cy-dp(1),17,0x70f4e8e0,2,-1);
                ui.text(c,"Finish map "+i+" to open the way",l+dp(58),cy+dp(16),11.5f,0x60e0c0a0,0,-1);continue;}
            entry(c,l,t,rr,t+bh,i<open||done?Icons.CHECK:Icons.FLAG,"MAP "+(i+1)+" · "+Maps.CAMPAIGN[i].toUpperCase(),ui.ellipsize(Maps.BLURBS[i],11.5f,0,rr-l-dp(200)),H_PLAY+i,i==open&&!done);
            r.set(rr-dp(128),t+bh*.5f-dp(18),rr-dp(14),t+bh*.5f+dp(18));ui.button(c,r,Icons.SECTORS,"Edit copy",Ui.NORMAL);ui.hit(r,H_EDIT+i);}
        ui.text(c,"\"Edit copy\" opens the map in the level editor as a new custom level.",l,top+Maps.count()*(bh+gap)+dp(14)-scroll,11.5f,0x90e0c0a0,0,-1);
        c.restoreToCount(save);
        if(scroll>1){p.setShader(new android.graphics.LinearGradient(0,top-dp(4),0,top+dp(24),0xc0080404,0x00080404,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,top-dp(4),w,top+dp(24),p);p.setShader(null);}
        if(scroll<maxS-1){p.setShader(new android.graphics.LinearGradient(0,h-dp(30),0,h,0x00080404,0xc0080404,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,h-dp(30),w,h,p);p.setShader(null);
            ui.icon(c,Icons.CHEVRON_DOWN,w/2,h-dp(12),16,0xa0ffb080);}
    }
    private void custom(Canvas c,float w,float h){
        float l=dp(80),rr=Math.min(w-dp(40),l+dp(640)),top=dp(92);
        r.set(rr-dp(150),dp(26),rr,dp(62));ui.button(c,r,Icons.PLUS,"New level",Ui.PRIMARY);ui.hit(r,H_NEW);
        if(customs.isEmpty()){ui.text(c,"No custom levels yet.",l,top+dp(40),16,0xd0f4e8e0,1,-1);ui.text(c,"Tap New level, or open the Level editor from the main menu.",l,top+dp(64),12.5f,0xa0e0c0a0,0,-1);return;}
        float rowH=dp(64),gap=dp(8),maxS=Math.max(0,customs.size()*(rowH+gap)-(h-top-dp(16)));scroll=Math.max(0,Math.min(maxS,scroll));
        int save=c.save();c.clipRect(0,top,w,h);
        for(int i=0;i<customs.size();i++){MapStore.Entry e=customs.get(i);float t=top+i*(rowH+gap)-scroll;if(t+rowH<top||t>h)continue;
            ui.round(c,l,t,rr,t+rowH,dp(10),0xb0140808);ui.outline(c,l,t,rr,t+rowH,dp(10),0x40ffffff,dp(1));
            ui.icon(c,Icons.SECTORS,l+dp(28),t+rowH*.5f,20,0xffe0c0a0);
            ui.text(c,ui.ellipsize(e.name,16,2,rr-l-dp(330)),l+dp(52),t+dp(28),16,0xfff4e8e0,2,-1);
            ui.text(c,"Edited "+android.text.format.DateFormat.format("d MMM yyyy, HH:mm",e.modified),l+dp(52),t+dp(47),11,0xa0e0c0a0,0,-1);
            float bx=rr-dp(12);
            r.set(bx-dp(44),t+dp(14),bx,t+rowH-dp(14));ui.button(c,r,Icons.TRASH,null,Ui.DANGER);ui.hit(r,H_DEL+i);bx-=dp(52);
            r.set(bx-dp(88),t+dp(14),bx,t+rowH-dp(14));ui.button(c,r,Icons.SECTORS,"Edit",Ui.NORMAL);ui.hit(r,H_EDITC+i);bx-=dp(96);
            r.set(bx-dp(88),t+dp(14),bx,t+rowH-dp(14));ui.button(c,r,Icons.PLAY,"Play",Ui.PRIMARY);ui.hit(r,H_PLAYC+i);}
        c.restoreToCount(save);
    }
    private void skills(Canvas c,float w,float h){
        float l=dp(80),rr=Math.min(w-dp(40),l+dp(560)),gap=dp(8),top=dp(92),bh=Math.min(dp(60),(h-top-dp(16)-gap*4)/5);
        for(int i=0;i<5;i++){float t=top+i*(bh+gap);
            entry(c,l,t,rr,t+bh,Icons.SKULL,Skill.NAMES[i],Skill.BLURBS[i],H_SKILL+i,i==Skill.current);
            for(int k=0;k<=i;k++)ui.icon(c,Icons.SKULL,rr-dp(20)-k*dp(18),t+bh*.5f,14,i==4?0xffff3a2a:0xc0ff8a5a);}
    }
    private static final int[] LINES={200,270,360,540};
    private void settings(Canvas c,float w,float h){
        String[] labels=display!=null?new String[]{"Display","Look speed","Invert look","Render resolution","Show frame rate","Aim assist","Music","Narration"}:new String[]{"Look speed","Invert look","Render resolution","Show frame rate","Aim assist","Music","Narration"};
        int off=display!=null?1:0;
        float l=dp(80),rr=Math.min(w-dp(40),l+dp(560)),top=dp(88),gap=Math.min(dp(8),Math.max(dp(4),(h-top-dp(12))*.025f)),
            rowH=Math.min(dp(50),(h-top-dp(12)-gap*(labels.length-1))/labels.length);   // rows shrink to fit short screens
        String[] vals={String.format(java.util.Locale.US,"%.1f",Game.lookSens*1000),Game.invertLook?"On":"Off",Game.RENDER_LINES+" lines"+(Game.RENDER_LINES==270?" (like RPG Pocket Editor)":""),Hud.showFps?"On":"Off",Game.aimAssist?"On":"Off",Music.volume<=0?"Off":Math.round(Music.volume*100)+"%",Narrator.enabled?"On":"Off"};
        if(display!=null){String[] v=new String[vals.length+1];v[0]=display.modes()[display.mode()]+"  ·  F11";System.arraycopy(vals,0,v,1,vals.length);vals=v;}
        for(int i=0;i<labels.length;i++){float t=top+i*(rowH+gap);
            ui.round(c,l,t,rr,t+rowH,dp(10),0xb0140808);ui.outline(c,l,t,rr,t+rowH,dp(10),0x40ffffff,dp(1));
            ui.textMid(c,labels[i],l+dp(20),t+rowH*.5f,16,0xfff4e8e0,1,-1);float in=Math.min(dp(10),rowH*.18f);
            if(i==off){r.set(rr-dp(150),t+in,rr-dp(106),t+rowH-in);ui.button(c,r,Icons.MINUS,null,Ui.NORMAL);ui.hit(r,H_SET+10);
                ui.textMid(c,vals[i],rr-dp(78),t+rowH*.5f,15,0xffffb080,2,0);
                r.set(rr-dp(56),t+in,rr-dp(12),t+rowH-in);ui.button(c,r,Icons.PLUS,null,Ui.NORMAL);ui.hit(r,H_SET+11);}
            else{r.set(rr-dp(250),t+in,rr-dp(12),t+rowH-in);ui.button(c,r,-1,vals[i],Ui.ACTIVE);ui.hit(r,i<off?H_SET+20:H_SET+i-off);}}
    }
    /** Button sizes for n stacked buttons between top and bottom: as tall as 58dp, smaller on short screens. */
    private float[] stack(int n,float top,float bottom){float gap=Math.min(dp(10),Math.max(dp(5),(bottom-top)*.03f));float bh=Math.min(dp(58),(bottom-top-gap*(n-1))/n);
        return new float[]{bh,gap,top+Math.max(0,(bottom-top-(bh*n+gap*(n-1)))*.5f)};}
    private void title(Canvas c,String text,float x,float y,float size,int col,int glowCol){
        glow.setTextSize(dp(size));glow.setTextAlign(Paint.Align.CENTER);glow.setColor(col);glow.setShadowLayer(dp(16),0,0,glowCol);c.drawText(text,x,y,glow);glow.setShadowLayer(0,0,0,0);glow.setTextAlign(Paint.Align.LEFT);
    }
    private void pause(Canvas c,float w,float h){
        float size=Math.min(54,h/dp(1)*.13f);title(c,"PAUSED",w/2,dp(16)+dp(size),size,0xffff5a1a,0xffc02000);
        float bw=Math.min(dp(380),w*.5f),l=(w-bw)/2;float[] st=stack(3,dp(36)+dp(size),h-dp(16));float bh=st[0],gap=st[1],t=st[2];
        entry(c,l,t,l+bw,t+bh,Icons.PLAY,"Resume",null,H_ITEM+20,true);t+=bh+gap;
        entry(c,l,t,l+bw,t+bh,Icons.RESET,"Restart level",null,H_ITEM+21,false);t+=bh+gap;
        entry(c,l,t,l+bw,t+bh,fromEditor?Icons.SECTORS:Icons.ARROW_LEFT,fromEditor?"Back to the editor":"Quit to main menu",null,H_ITEM+22,false);
    }
    /** Level complete: the title across the top; the result on the left and the choices on the right (landscape), so everything fits on a phone. */
    private void complete(Canvas c,float w,float h){
        float size=Math.min(46,h/dp(1)*.11f);float ty=dp(14)+dp(size);title(c,"LEVEL COMPLETE",w/2,ty,size,0xffffb040,0xffff5000);
        ui.text(c,doneName.toUpperCase(),w/2,ty+dp(24),15,0xffe8c8a8,1,0);
        int m=(int)(seconds/60),s=(int)(seconds%60);
        float top=ty+dp(40),bottom=h-dp(16);boolean side=w>h*1.35f;
        float cx=side?w*.3f:w/2,sy=side?(top+bottom)*.5f:top+dp(30);
        ui.text(c,kills+" / "+total,cx-dp(80),sy,28,kills==total?0xff80ff90:0xffffffff,2,0);ui.text(c,"DEMONS SLAIN",cx-dp(80),sy+dp(20),11,0xa0e0c0a0,1,0);
        ui.text(c,m+":"+(s<10?"0":"")+s,cx+dp(80),sy,28,0xffffffff,2,0);ui.text(c,"TIME",cx+dp(80),sy+dp(20),11,0xa0e0c0a0,1,0);
        int n=hasNext?3:2;float bw=Math.min(dp(380),side?w*.42f:w*.5f),l=side?w*.53f:(w-bw)/2;
        float[] st=stack(n,side?top:sy+dp(36),bottom);float bh=st[0],gap=st[1],t=st[2];
        if(hasNext){entry(c,l,t,l+bw,t+bh,Icons.PLAY,finale?"Continue":"Next level",finale?"The end of the road":null,H_ITEM+30,true);t+=bh+gap;}
        entry(c,l,t,l+bw,t+bh,Icons.RESET,"Play again",null,H_ITEM+21,!hasNext);t+=bh+gap;
        entry(c,l,t,l+bw,t+bh,fromEditor?Icons.SECTORS:Icons.ARROW_LEFT,fromEditor?"Back to the editor":"Main menu",null,H_ITEM+22,false);
    }

    // ---------------------------------------------------------------- input
    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked();float x=e.getX(),y=e.getY();
        if(a==MotionEvent.ACTION_DOWN){pressed=ui.hitAt(x,y);downY=lastY=y;dragged=false;invalidate();return true;}
        if(a==MotionEvent.ACTION_MOVE){if(Math.abs(y-downY)>dp(10)){dragged=true;pressed=-1;}if(dragged&&(page==CUSTOM||page==CAMPAIGN)){scroll-=y-lastY;invalidate();}lastY=y;return true;}
        if(a==MotionEvent.ACTION_UP){int hid=ui.hitAt(x,y);if(!dragged&&hid==pressed&&hid>=0)tap(hid);pressed=-1;invalidate();return true;}
        if(a==MotionEvent.ACTION_CANCEL){pressed=-1;invalidate();}
        return true;
    }
    private void tap(int id){
        if(id==H_BACK){show(page==SKILL?skillFrom:MAIN);return;}
        if(page==SKILL&&id>=H_SKILL&&id<H_SKILL+5){Skill.current=id-H_SKILL;prefs.edit().putInt("skill",Skill.current).apply();
            if(pendingCustom!=null)host.playCustom(pendingCustom);else if(pendingCampaign>=0)host.playCampaign(pendingCampaign);return;}
        if(page==MAIN&&id>=H_ITEM&&id<H_ITEM+5){switch(id-H_ITEM){case 0:show(CAMPAIGN);break;case 1:show(CUSTOM);break;case 2:newLevel();break;case 3:show(SETTINGS);break;default:host.quit();}return;}
        if(page==CAMPAIGN&&id==H_CONT){pendingCampaign=unlocked(getContext());pendingCustom=null;skillFrom=CAMPAIGN;show(SKILL);return;}
        if(page==CAMPAIGN){if(id>=H_PLAY&&id<H_PLAY+Maps.count()){pendingCampaign=id-H_PLAY;pendingCustom=null;skillFrom=CAMPAIGN;show(SKILL);return;}
            if(id>=H_EDIT&&id<H_EDIT+Maps.count()){SectorMap m=Maps.campaign(id-H_EDIT);m.name=m.name+" (copy)";host.editMap(null,m);return;}}
        if(page==CUSTOM){
            if(id==H_NEW){newLevel();return;}
            if(id>=H_PLAYC&&id<H_PLAYC+customs.size()){pendingCustom=customs.get(id-H_PLAYC).id;pendingCampaign=-1;skillFrom=CUSTOM;show(SKILL);return;}
            if(id>=H_EDITC&&id<H_EDITC+customs.size()){host.editMap(customs.get(id-H_EDITC).id,null);return;}
            if(id>=H_DEL&&id<H_DEL+customs.size()){MapStore.Entry en=customs.get(id-H_DEL);
                Overlay.get().confirm("Delete \""+en.name+"\"?","This can't be undone.","Delete",true,()->{store.delete(en.id);show(CUSTOM);});return;}}
        if(page==SETTINGS){
            if(id==H_SET+10||id==H_SET+11){Game.lookSens=Math.max(.002f,Math.min(.012f,Game.lookSens+(id==H_SET+11?.0005f:-.0005f)));}
            else if(id==H_SET+20&&display!=null){display.setMode((display.mode()+1)%display.modes().length);invalidate();return;}
            else if(id==H_SET+1)Game.invertLook=!Game.invertLook;
            else if(id==H_SET+2){int k=0;for(int i=0;i<LINES.length;i++)if(LINES[i]==Game.RENDER_LINES)k=i;Game.RENDER_LINES=LINES[(k+1)%LINES.length];}
            else if(id==H_SET+3)Hud.showFps=!Hud.showFps;
            else if(id==H_SET+4)Game.aimAssist=!Game.aimAssist;
            else if(id==H_SET+5){float[] v={0,.4f,.6f,.8f,1f};int k=0;for(int i=0;i<v.length;i++)if(Math.abs(v[i]-Music.volume)<.05f)k=i;Music.volume=v[(k+1)%v.length];}
            else if(id==H_SET+6)Narrator.enabled=!Narrator.enabled;
            saveSettings();return;}
        if(id==H_ITEM+20){host.resume();return;}
        if(id==H_ITEM+21){host.restart();return;}
        if(id==H_ITEM+22){host.leaveGame();return;}
        if(id==H_ITEM+30){host.nextLevel();}
    }
    private void newLevel(){
        String[] items=new String[1+Maps.count()];items[0]="Blank level (one room)";for(int i=0;i<Maps.count();i++)items[i+1]="Start from "+Maps.CAMPAIGN[i];
        Overlay.get().choose("New level",items,-1,i->{
            SectorMap m=i==0?Maps.blank():Maps.campaign(i-1);m.name=i==0?"New level":m.name+" (copy)";host.editMap(null,m);});
    }
    /** Back key. Returns false when the app should close. */
    boolean back(){if(page==PAUSE){host.resume();return true;}if(page==COMPLETE){host.leaveGame();return true;}if(page==SKILL){show(skillFrom);return true;}if(page!=MAIN){show(MAIN);return true;}return false;}
}
