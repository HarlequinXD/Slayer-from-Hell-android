package com.harlequin.slayer;

import android.content.Context;
import java.util.ArrayList;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** The game: update loop, the third-person camera, and OpenGL ES 3 rendering of the level, effects, the warden and her shotgun. */
final class Game implements GLSurfaceView.Renderer {
    private final Context ctx;Hud hud;Sfx sfx;
    final SectorMap map;final Level level;final Player pl=new Player();final Gun gun=new Gun();final Fx fx=new Fx();final Hero hero=new Hero();final Enemies enemies;private int lastDeaths;
    // HUD state (read by the UI thread)
    volatile String loading="Forging materials…";volatile float progress;volatile int ammo,reserve,fps;volatile float health=100,hurt,spread,hint=6;volatile boolean reloading,hasGun=true;volatile int demonsLeft,demonsTotal;

    /** What the game tells the screen around it (called on the GL thread). */
    interface Listener {void levelComplete(int kills,int total,float seconds);}
    Listener listener;
    volatile boolean paused;private boolean completed;private float playTime;
    volatile String banner;volatile float bannerT;

    Game(Context c,SectorMap m){ctx=c;map=m;level=new Level(m);pl.spawn(level);hero.bodyYaw=pl.yaw;enemies=new Enemies(level,pl);demonsTotal=enemies.demons.size();}

    // ------------------------------------------------------------------ GL resources
    private int prog,progDecal,progPart;
    private final java.util.HashMap<String,Integer> uni=new java.util.HashMap<>();
    private Gfx.Mesh[] levelMesh;private Gfx.Mesh[] parts;private int maskTex;
    private final int[][] tex=new int[Tex.COUNT][];
    private static volatile Tex.Mat[] mats;private static final AtomicInteger made=new AtomicInteger();
    private Gfx.Stream decalStream,partStream;
    /** Render resolution: 270 lines tall (the RPG Pocket Editor's default), width from the screen's shape; the frame is
     *  drawn into an offscreen buffer and scaled up to the screen with point sampling for crisp, chunky pixels. */
    static int RENDER_LINES=270;
    /** Settings (from the menu). */
    static float lookSens=.0055f;static boolean invertLook;
    private int W=1,H=1,SW=1,SH=1,fbo,fboColor,fboDepth;private long last;private float time,flash;private int frames;private long fpsStart;
    private float[] decalBuf=new float[Fx.MAXD*6*6],partBuf=new float[Fx.MAXP*6*9];

    @Override public void onSurfaceCreated(GL10 unused,EGLConfig cfg){
        prog=Gfx.program(Shaders.VS_PBR,Shaders.FS_PBR);progDecal=Gfx.program(Shaders.VS_DECAL,Shaders.FS_DECAL);progPart=Gfx.program(Shaders.VS_PART,Shaders.FS_PART);uni.clear();
        java.util.ArrayList<float[]>[] geo=level.build();levelMesh=new Gfx.Mesh[Tex.COUNT];for(int i=0;i<Tex.COUNT;i++)levelMesh[i]=new Gfx.Mesh(geo[i].get(0));
        float[][] g=Gun.build();parts=new Gfx.Mesh[g.length];for(int i=0;i<g.length;i++)parts[i]=g[i]==null?null:new Gfx.Mesh(g[i]);
        loadBakedGun();loadHero();loadSaw();loadDemon();
        maskTex=Gfx.maskTexture(level.lightMask,level.W,level.H);
        decalStream=new Gfx.Stream(3,2,1);partStream=new Gfx.Stream(3,2,4);
        for(int i=0;i<Tex.COUNT;i++)tex[i]=null;
        if(mats==null){mats=new Tex.Mat[Tex.COUNT];
            ExecutorService ex=Executors.newFixedThreadPool(Math.max(2,Runtime.getRuntime().availableProcessors()-1));
            for(int i=0;i<Tex.COUNT;i++){final int id=i;ex.submit(()->{mats[id]=Tex.make(id);made.incrementAndGet();});}ex.shutdown();}
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);GLES30.glDepthFunc(GLES30.GL_LEQUAL);GLES30.glDisable(GLES30.GL_CULL_FACE);
        last=System.nanoTime();
    }
    @Override public void onSurfaceChanged(GL10 unused,int w,int h){
        SW=Math.max(1,w);SH=Math.max(1,h);H=Math.min(SH,RENDER_LINES);W=Math.max(1,Math.round(H*SW/(float)SH));
        int[] t=new int[2];
        if(fbo!=0){GLES30.glDeleteFramebuffers(1,new int[]{fbo},0);GLES30.glDeleteRenderbuffers(2,new int[]{fboColor,fboDepth},0);}
        GLES30.glGenFramebuffers(1,t,0);fbo=t[0];GLES30.glGenRenderbuffers(2,t,0);fboColor=t[0];fboDepth=t[1];
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER,fboColor);GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER,GLES30.GL_RGBA8,W,H);
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER,fboDepth);GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER,GLES30.GL_DEPTH_COMPONENT24,W,H);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,fbo);
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER,GLES30.GL_COLOR_ATTACHMENT0,GLES30.GL_RENDERBUFFER,fboColor);
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER,GLES30.GL_DEPTH_ATTACHMENT,GLES30.GL_RENDERBUFFER,fboDepth);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);GLES30.glViewport(0,0,SW,SH);
    }

    // ------------------------------------------------------------------ the baked double barrel
    private Gfx.Mesh[] gunMesh=new Gfx.Mesh[2];private int[] gunTex;private float gunPom;
    /** Loads the low poly gun (gun.bin) and its baked maps from the assets. */
    private void loadBakedGun(){
        try(java.io.InputStream in=ctx.getAssets().open("gun.bin")){
            byte[] all=readAll(in);java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(all).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            if(b.getInt()!=0x4e554753)throw new RuntimeException("gun.bin");gunPom=b.getFloat();int groups=b.getInt();
            for(int gI=0;gI<groups&&gI<2;gI++){int n=b.getInt();float[] v=new float[n*11];for(int i=0;i<v.length;i++)v[i]=b.getFloat();gunMesh[gI]=new Gfx.Mesh(v);}
            gunTex=new int[]{asset("gun_a.png",true),asset("gun_b.png",false),asset("gun_c.png",false)};
        }catch(Exception e){throw new RuntimeException("baked gun: "+e);}
    }
    // ------------------------------------------------------------------ the warden (player model: one mesh per body part)
    private Gfx.Mesh[] heroParts;private int[] heroTex;private float heroPom;
    private void loadHero(){
        try(java.io.InputStream in=ctx.getAssets().open("hero.bin")){
            java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(readAll(in)).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            if(b.getInt()!=0x4e554753)throw new RuntimeException("hero.bin");heroPom=b.getFloat();int groups=b.getInt();
            heroParts=new Gfx.Mesh[groups];
            for(int g=0;g<groups;g++){int n=b.getInt();if(n==0)continue;float[] v=new float[n*11];for(int i=0;i<v.length;i++)v[i]=b.getFloat();heroParts[g]=new Gfx.Mesh(v);}
            heroTex=new int[]{asset("hero_a.png",true),asset("hero_b.png",false),asset("hero_c.png",false)};
        }catch(Exception e){throw new RuntimeException("baked hero: "+e);}
    }
    // ------------------------------------------------------------------ the chainsaw arm (body + a chain loop whose texture scrolls)
    private Gfx.Mesh sawBody,sawChain;private int[] sawTex,chainTex;private float sawPom,chainScroll;
    private void loadSaw(){
        try(java.io.InputStream in=ctx.getAssets().open("chainsaw.bin")){
            java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(readAll(in)).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            if(b.getInt()!=0x4e554753)throw new RuntimeException("chainsaw.bin");sawPom=b.getFloat();int groups=b.getInt();
            for(int g=0;g<groups;g++){int n=b.getInt();float[] v=new float[n*11];for(int i=0;i<v.length;i++)v[i]=b.getFloat();if(g==0)sawBody=new Gfx.Mesh(v);else sawChain=new Gfx.Mesh(v);}
            sawTex=new int[]{asset("chainsaw_a.png",true),asset("chainsaw_b.png",false),asset("chainsaw_c.png",false)};
            chainTex=new int[]{asset("chain_a.png",true,true),asset("chain_b.png",false,true),asset("chain_c.png",false,true)};
        }catch(Exception e){throw new RuntimeException("baked chainsaw: "+e);}
    }
    // ------------------------------------------------------------------ the demons (same rig as the warden) and their hand cannons
    private Gfx.Mesh[] demonParts;private int[] demonTex,cannonTex;private float demonPom,cannonPom;private Gfx.Mesh cannonMesh;
    private void loadDemon(){
        try(java.io.InputStream in=ctx.getAssets().open("demon.bin")){
            java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(readAll(in)).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            if(b.getInt()!=0x4e554753)throw new RuntimeException("demon.bin");demonPom=b.getFloat();int groups=b.getInt();
            demonParts=new Gfx.Mesh[groups];
            for(int g=0;g<groups;g++){int n=b.getInt();if(n==0)continue;float[] v=new float[n*11];for(int i=0;i<v.length;i++)v[i]=b.getFloat();demonParts[g]=new Gfx.Mesh(v);}
            demonTex=new int[]{asset("demon_a.png",true),asset("demon_b.png",false),asset("demon_c.png",false)};
        }catch(Exception e){throw new RuntimeException("baked demon: "+e);}
        try(java.io.InputStream in=ctx.getAssets().open("cannon.bin")){
            java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(readAll(in)).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            if(b.getInt()!=0x4e554753)throw new RuntimeException("cannon.bin");cannonPom=b.getFloat();b.getInt();
            int n=b.getInt();float[] v=new float[n*11];for(int i=0;i<v.length;i++)v[i]=b.getFloat();cannonMesh=new Gfx.Mesh(v);
            cannonTex=new int[]{asset("cannon_a.png",true),asset("cannon_b.png",false),asset("cannon_c.png",false)};
        }catch(Exception e){throw new RuntimeException("baked cannon: "+e);}
    }
    /** Every demon in its pose (scaled about its hooves, or toppling when dead), then the hand cannons (the core glows brighter while charging). */
    private void drawDemons(float[] eye){
        for(int k=0;k<3;k++){GLES30.glActiveTexture(GLES30.GL_TEXTURE0+k);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,demonTex[k]);}
        GLES30.glUniform1f(u(prog,"uPom"),demonPom*.35f);GLES30.glUniform1f(u(prog,"uEmit"),5f);
        for(Demon d:enemies.demons){if(d.rig.mat[Hero.P_HEAD]==null||far(d,eye))continue;
            for(int p=0;p<demonParts.length&&p<Hero.PARTS;p++){if(demonParts[p]==null||d.rig.mat[p]==null)continue;model(M.mul(d.world,d.rig.mat[p]));demonParts[p].draw();}}
        for(int k=0;k<3;k++){GLES30.glActiveTexture(GLES30.GL_TEXTURE0+k);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,cannonTex[k]);}
        GLES30.glUniform1f(u(prog,"uPom"),cannonPom);
        for(Demon d:enemies.demons){if(d.rig.sawMat==null||far(d,eye))continue;
            GLES30.glUniform1f(u(prog,"uEmit"),d.dead?Math.max(0,3-d.deathT*2):3+d.charge*9);model(M.mul(d.world,d.rig.sawMat));cannonMesh.draw();}
    }
    private static boolean far(Demon d,float[] eye){float dx=d.body.x-eye[0],dy=d.body.y-eye[1];return dx*dx+dy*dy>70*70;}
    /** The warden in her animated pose, then the shotgun in her hands and any shells in the chambers or her hand. */
    private void drawPlayer(){
        for(int k=0;k<3;k++){GLES30.glActiveTexture(GLES30.GL_TEXTURE0+k);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,heroTex[k]);}
        GLES30.glUniform1f(u(prog,"uPom"),heroPom*.35f);GLES30.glUniform1f(u(prog,"uEmit"),4f);
        for(int p=0;p<heroParts.length&&p<Hero.PARTS;p++){if(heroParts[p]==null||hero.mat[p]==null)continue;model(hero.mat[p]);heroParts[p].draw();}
        float[] E=hero.eyeFrame;
        if(hero.hasGun){bindGun();model(M.mul(E,gun.mat[Gun.P_ACTION]));gunMesh[0].draw();model(M.mul(E,gun.mat[Gun.P_BARRELS]));gunMesh[1].draw();}
        for(int k=0;k<gun.shellCount;k++){model(M.mul(E,gun.shellMats[k]));bindMat(Tex.SHELL,false);parts[Gun.P_HULL].draw();bindMat(Tex.BRASS,false);parts[Gun.P_BASE].draw();}
        for(int k=0;k<hero.caddyCount;k++){model(hero.caddyMats[k]);bindMat(Tex.SHELL,false);parts[Gun.P_HULL].draw();bindMat(Tex.BRASS,false);parts[Gun.P_BASE].draw();}
        // the chainsaw: body, then the chain loop with its texture running along the bar
        model(hero.sawMat);
        for(int k=0;k<3;k++){GLES30.glActiveTexture(GLES30.GL_TEXTURE0+k);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,sawTex[k]);}
        GLES30.glUniform1f(u(prog,"uPom"),sawPom);GLES30.glUniform1f(u(prog,"uEmit"),0);sawBody.draw();
        for(int k=0;k<3;k++){GLES30.glActiveTexture(GLES30.GL_TEXTURE0+k);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,chainTex[k]);}
        GLES30.glUniform1f(u(prog,"uPom"),0);GLES30.glUniform2f(u(prog,"uUvScroll"),-chainScroll,0);sawChain.draw();GLES30.glUniform2f(u(prog,"uUvScroll"),0,0);
    }
    private int asset(String name,boolean srgb)throws java.io.IOException{return asset(name,srgb,false);}
    private int asset(String name,boolean srgb,boolean repeat)throws java.io.IOException{
        android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();o.inPremultiplied=false;o.inScaled=false;o.inPreferredConfig=android.graphics.Bitmap.Config.ARGB_8888;
        android.graphics.Bitmap bm;try(java.io.InputStream in=ctx.getAssets().open(name)){bm=android.graphics.BitmapFactory.decodeStream(in,null,o);}
        int w=bm.getWidth(),h=bm.getHeight();int[] px=new int[w*h];bm.getPixels(px,0,w,0,0,w,h);bm.recycle();
        if(!name.equals("gun_c.png")&&!bm.hasAlpha()){}
        return Gfx.texture(px,w,h,srgb,repeat);
    }
    private static byte[] readAll(java.io.InputStream in)throws java.io.IOException{java.io.ByteArrayOutputStream o=new java.io.ByteArrayOutputStream();byte[] b=new byte[65536];int n;while((n=in.read(b))>0)o.write(b,0,n);return o.toByteArray();}
    private void bindGun(){for(int k=0;k<3;k++){GLES30.glActiveTexture(GLES30.GL_TEXTURE0+k);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,gunTex[k]);}
        GLES30.glUniform1f(u(prog,"uPom"),gunPom);GLES30.glUniform1f(u(prog,"uEmit"),0);}

    private int u(int p,String n){String k=p+n;Integer v=uni.get(k);if(v==null){v=GLES30.glGetUniformLocation(p,n);uni.put(k,v);}return v;}

    @Override public void onDrawFrame(GL10 unused){
        long now=System.nanoTime();float dt=Math.min(.05f,(now-last)/1e9f);last=now;time+=dt;
        frames++;if(now-fpsStart>1e9){fps=frames;frames=0;fpsStart=now;}
        if(!texturesReady()){GLES30.glClearColor(.07f,.02f,.015f,1);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT|GLES30.GL_DEPTH_BUFFER_BIT);return;}
        loading=null;
        if(!paused)update(dt);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,fbo);
        render();
        // scale the low-resolution frame up to the screen with point sampling
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER,fbo);GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER,0);
        GLES30.glBlitFramebuffer(0,0,W,H,0,0,SW,SH,GLES30.GL_COLOR_BUFFER_BIT,GLES30.GL_NEAREST);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);
    }
    /** Uploads finished materials (two per frame) and reports progress; true when everything is on the GPU. */
    private boolean texturesReady(){
        int up=0,done=0;
        for(int i=0;i<Tex.COUNT;i++){if(tex[i]!=null){done++;continue;}Tex.Mat m=mats[i];if(m==null||up>=2)continue;
            tex[i]=new int[]{Gfx.texture(m.albedoAO,m.size,m.size,true,true),Gfx.texture(m.normalHeight,m.size,m.size,false,true),Gfx.texture(m.rme,m.size,m.size,false,true)};up++;done++;}
        progress=(made.get()+done)/(2f*Tex.COUNT);
        if(done<Tex.COUNT){loading="Forging materials "+made.get()+"/"+Tex.COUNT+" · uploading "+done+"/"+Tex.COUNT;return false;}
        return true;
    }

    // ------------------------------------------------------------------ update
    private int lastStep;private boolean wasOnGround=true;
    private void update(float dt){
        Hud.Input in=hud.in;float[] look=in.takeLook();boolean jump=in.takeJump()||(in.jumpHeld&&pl.onGround);
        int ev=0;
        pl.update(dt,level,in.moveX,in.moveY,look[0],invertLook?-look[1]:look[1],jump,lookSens);
        if(pl.justJumped)sfx.play(Sfx.JUMP,.5f);
        if(pl.justLanded)sfx.play(Sfx.LAND,Math.min(1,pl.landSpeed/12));
        if(pl.hurtFlash>.99f&&(int)(time*4)%2==0)sfx.play(Sfx.SIZZLE,.4f);
        boolean rl=in.takeReload();
        gun.update(dt,in.fire,rl);ev=gun.events;
        hero.update(dt,time,pl,level,pl.yaw,pl.viewPitch(),in.fire,in.saw,gun);
        // walking into a wall: the animation layer recoils off it
        if(pl.bumpSpeed>0){sfx.play(Sfx.LAND,Math.min(1,pl.bumpSpeed/5)*.7f,1.2f);hero.bump(pl.bumpNx,pl.bumpNy,pl.bumpSpeed);}
        updateSaw(dt);
        if(hero.steps>0&&pl.onGround)sfx.play(Sfx.STEP,.3f+.15f*hero.run,.9f+fx.rng.nextFloat()*.2f);
        if(pl.deaths!=lastDeaths){lastDeaths=pl.deaths;enemies.reset();for(Level.Pickup pk:level.pickups)pk.taken=false;gun.reserve=Math.max(gun.reserve,24);banner="YOU DIED";bannerT=2.5f;}   // she died: the level starts over
        pickups(dt);
        playTime+=dt;bannerT=Math.max(0,bannerT-dt);
        {SectorMap.Sector here=level.sectorAt(pl.x,pl.y);if(!completed&&here!=null&&here.special==SectorMap.SP_EXIT&&pl.onGround){completed=true;if(listener!=null)listener.levelComplete(demonsTotal-enemies.alive(),demonsTotal,playTime);}}
        enemies.update(dt,time);demonEvents();
        updateCamera(dt);
        float[] f=camF,right=camR,up=camU;float[] E=hero.eyeFrame;
        if((ev&Gun.EV_FIRE)!=0){
            sfx.play(Sfx.BLAST,1f);flash=.07f;pl.kickVel+=1.6f;enemies.hear(pl.x,pl.y,pl.z,32);boolean struck=false;
            // the crosshair picks the aim point; the pellets leave the muzzle toward it
            float[] mz=M.pt(E,gun.muzzleCam);
            float[] aimHit=level.ray(camPos[0],camPos[1],camPos[2],f[0],f[1],f[2],80);
            float[] aimPt=aimHit!=null?new float[]{aimHit[0],aimHit[1],aimHit[2]}:Model.add(camPos,f,80);
            float[] dir=Model.norm(Model.sub(aimPt,mz));if(Model.dot(dir,f)<.3f)dir=f;
            float[] r2=Model.norm(Model.cross(dir,new float[]{0,0,1})),u2=Model.cross(r2,dir);
            for(int k=0;k<9;k++){   // nine buckshot pellets in a cone
                float a=fx.rng.nextFloat()*6.2832f,rr=(float)Math.sqrt(fx.rng.nextFloat())*.05f;
                float[] d=Model.norm(new float[]{dir[0]+r2[0]*(float)Math.cos(a)*rr+u2[0]*(float)Math.sin(a)*rr,dir[1]+r2[1]*(float)Math.cos(a)*rr+u2[1]*(float)Math.sin(a)*rr,dir[2]+r2[2]*(float)Math.cos(a)*rr+u2[2]*(float)Math.sin(a)*rr});
                float[] hit=level.ray(mz[0],mz[1],mz[2],d[0],d[1],d[2],70);
                float[] dh=enemies.ray(mz[0],mz[1],mz[2],d[0],d[1],d[2],hit!=null?hit[6]:70);
                if(dh!=null){   // a demon is nearer than the wall: damage falls off with range, head shots double
                    Demon dm=enemies.demons.get((int)dh[1]);float dmg=11f*Math.max(.35f,Math.min(1,1.35f-dh[0]/18f))*(dh[2]>0?2:1);dm.damage(dmg,d[0],d[1]);
                    fx.blood(mz[0]+d[0]*dh[0],mz[1]+d[1]*dh[0],mz[2]+d[2]*dh[0],d[0],d[1],d[2]);struck=true;}
                else if(hit!=null){fx.impact(hit[0],hit[1],hit[2],hit[3],hit[4],hit[5],(int)hit[7]);if((int)hit[7]!=Tex.LAVA&&(int)hit[7]!=Tex.LIGHT)fx.hole(hit[0],hit[1],hit[2],hit[3],hit[4],hit[5]);}}
            float[] gf=Model.norm(M.dir(M.mul(E,gun.mat[Gun.P_ACTION]),new float[]{1,0,0}));fx.muzzle(mz[0],mz[1],mz[2],gf[0],gf[1],gf[2]);
            if(struck)sfx.play(Sfx.HIT,.9f,.9f+fx.rng.nextFloat()*.2f);
        }
        if((ev&Gun.EV_OPEN)!=0)sfx.play(Sfx.PUMP_BACK,.8f,.8f);
        if((ev&Gun.EV_CLOSE)!=0)sfx.play(Sfx.PUMP_FWD,.9f,.75f);
        if((ev&Gun.EV_SHELL_IN)!=0)sfx.play(Sfx.SHELL_IN,.7f);
        if((ev&Gun.EV_DRY)!=0)sfx.play(Sfx.DRY,.8f);
        if((ev&Gun.EV_EJECT)!=0){   // the extractors throw the fired cases up and back over her shoulder
            float[] gf=Model.norm(M.dir(M.mul(E,gun.mat[Gun.P_ACTION]),new float[]{1,0,0})),gr=Model.norm(M.dir(M.mul(E,gun.mat[Gun.P_ACTION]),new float[]{0,-1,0})),gu=Model.norm(M.dir(M.mul(E,gun.mat[Gun.P_ACTION]),new float[]{0,0,1}));
            float[] back=Model.norm(new float[]{-gf[0]*.85f+gr[0]*.35f,-gf[1]*.85f+gr[1]*.35f,-gf[2]*.85f+gr[2]*.35f});
            for(int b=0;b<2;b++)if(gun.spent[b]){float[] p=M.pt(E,gun.chamberCam[b]);fx.eject(p[0],p[1],p[2],back,gu,gr);}
            gun.clearSpent();}
        // barrel smoke for a moment after each shot
        if(gun.state==Gun.FIRE&&gun.t<1.2f&&fx.rng.nextFloat()<.35f){float[] mz=M.pt(hero.eyeFrame,gun.muzzleCam);fx.spawn(Fx.SMOKE,mz[0],mz[1],mz[2],fx.rnd(-.05f,.05f),fx.rnd(-.05f,.05f),fx.rnd(.15f,.35f),fx.rnd(.8f,1.4f),.025f,.6f,0,1f,.5f,.48f,.46f,.25f);}
        // embers rising from the lava
        for(int k=0;k<2;k++){float ex=pl.x+fx.rnd(-12,12),ey=pl.y+fx.rnd(-12,12);Level.Cell c=level.atF(ex,ey);
            if(!c.solid&&c.lava)fx.spawn(Fx.ADD,ex,ey,c.floor+.05f,fx.rnd(-.2f,.2f),fx.rnd(-.2f,.2f),fx.rnd(.6f,1.6f),fx.rnd(1.2f,2.6f),fx.rnd(.012f,.025f),-.2f,.1f,.2f,1f,.4f,.08f,1);}
        fx.update(dt,level);
        if(fx.updateShells(dt,level)>0)sfx.play(Sfx.TINK,.5f,.9f+fx.rng.nextFloat()*.3f);
        flash=Math.max(0,flash-dt);hint=Math.max(0,hint-dt);
        demonsLeft=enemies.alive();
        hasGun=hero.hasGun;ammo=hero.hasGun?gun.ammo():0;reserve=gun.reserve;health=pl.health;hurt=pl.hurtFlash;reloading=gun.reloading();spread=gun.recoilNow;
    }
    // ------------------------------------------------------------------ pickups: health orbs and boxes of shells
    private void pickups(float dt){
        for(Level.Pickup p:level.pickups){if(p.taken)continue;
            float bob=.55f+.08f*(float)Math.sin(time*2.4f+p.x);
            if(p.type==SectorMap.T_HEALTH){   // a pulsing orb of hellfire
                if(fx.rng.nextFloat()<.7f)fx.spawn(Fx.ADD,p.x+fx.rnd(-.06f,.06f),p.y+fx.rnd(-.06f,.06f),p.z+bob+fx.rnd(-.06f,.06f),fx.rnd(-.1f,.1f),fx.rnd(-.1f,.1f),fx.rnd(.05f,.3f),fx.rnd(.25f,.45f),fx.rnd(.05f,.09f),-.1f,0,.5f,1f,.18f,.12f,1);
                if(fx.rng.nextFloat()<.25f)fx.spawn(Fx.STAR,p.x,p.y,p.z+bob,0,0,0,.12f,.22f+.05f*(float)Math.sin(time*6),0,0,0,1f,.3f,.25f,1);}
            float dx=p.x-pl.x,dy=p.y-pl.y;if(dx*dx+dy*dy>.85f*.85f||Math.abs(p.z-pl.z)>1.2f)continue;
            if(p.type==SectorMap.T_HEALTH){if(pl.health>=100)continue;pl.health=Math.min(100,pl.health+25);sfx.play(Sfx.PICKUP,.9f,1.2f);banner="+25 HEALTH";}
            else{if(gun.reserve>=60)continue;gun.reserve=Math.min(60,gun.reserve+8);sfx.play(Sfx.PICKUP,.8f,.85f);sfx.play(Sfx.SHELL_IN,.6f);banner="+8 SHELLS";}
            bannerT=1.4f;p.taken=true;}
    }
    /** Boxes of shells: four shells standing in a cluster, turning slowly. */
    private void drawPickups(){
        for(Level.Pickup p:level.pickups){if(p.taken||p.type!=SectorMap.T_SHELLS)continue;
            float yaw=time*.8f+p.x;for(int k=0;k<4;k++){float a=yaw+k*1.5708f;float[] Mx=M.mul(M.tr(p.x+(float)Math.cos(a)*.05f,p.y+(float)Math.sin(a)*.05f,p.z+.03f+.04f*(float)Math.sin(time*2+p.y)),M.ry(-1.5708f));
                model(Mx);bindMat(Tex.SHELL,false);parts[Gun.P_HULL].draw();bindMat(Tex.BRASS,false);parts[Gun.P_BASE].draw();}}
    }

    // ------------------------------------------------------------------ the demons' events: sounds, effects, lights
    private final ArrayList<float[]> flashes=new ArrayList<>();   // x,y,z,time left (explosion light)
    private float vol(float x,float y,float z){float d=(float)Math.sqrt((x-pl.x)*(x-pl.x)+(y-pl.y)*(y-pl.y)+(z-pl.z)*(z-pl.z));return Math.max(0,1-d/32f);}
    private void demonEvents(){
        for(Enemies.Event e:enemies.events){float v=vol(e.x,e.y,e.z);
            switch(e.type){
                case Enemies.EV_ROAR:sfx.play(Sfx.ROAR,.9f*v,.85f+fx.rng.nextFloat()*.3f);break;
                case Enemies.EV_CHARGE:sfx.play(Sfx.CHARGE,.6f*v);break;
                case Enemies.EV_SHOOT:sfx.play(Sfx.FIREBALL,.9f*v);fx.spawn(Fx.STAR,e.x,e.y,e.z,e.dx,e.dy,e.dz,.08f,.32f,2,0,0,.4f,.7f,1f,1);flashes.add(new float[]{e.x,e.y,e.z,.08f});break;
                case Enemies.EV_EXPLODE:sfx.play(Sfx.EXPLODE,.9f*v);fx.explosion(e.x,e.y,e.z,e.dx,e.dy,e.dz);flashes.add(new float[]{e.x,e.y,e.z,.3f});break;
                case Enemies.EV_HIT_PLAYER:sfx.play(Sfx.EXPLODE,.9f,1.2f);sfx.play(Sfx.SIZZLE,.6f);fx.explosion(e.x,e.y,e.z,e.dx,e.dy,e.dz);flashes.add(new float[]{e.x,e.y,e.z,.25f});pl.kickVel-=2.5f;break;
                case Enemies.EV_PAIN:sfx.play(Sfx.PAIN,.9f*v,.85f+fx.rng.nextFloat()*.3f);break;
                case Enemies.EV_DIE:sfx.play(Sfx.DIE,v);break;
                case Enemies.EV_STEP:if(v>.2f)sfx.play(Sfx.STEP,.35f*v,.7f+fx.rng.nextFloat()*.15f);break;
            }}
        enemies.events.clear();
        for(Enemies.Fireball f:enemies.fireballs)fx.fireball(f.x,f.y,f.z,f.vx,f.vy,f.vz);
        for(Demon d:enemies.demons)if(!d.dead&&d.charge>.2f){float[] m=d.muzzle();fx.charge(m[0],m[1],m[2],d.charge);}
        for(float[] fl:flashes)fl[3]-=1/60f;flashes.removeIf(fl->fl[3]<=0);
    }
    /** The two brightest nearby dynamic blue lights (fireballs in flight, blasts) for light slots 13 and 14. */
    private void dynamicLights(float[] eye){
        float[][] cand=new float[enemies.fireballs.size()+flashes.size()][];int n=0;
        for(Enemies.Fireball f:enemies.fireballs)cand[n++]=new float[]{f.x,f.y,f.z,1f};
        for(float[] fl:flashes)cand[n++]=new float[]{fl[0],fl[1],fl[2],2.2f*Math.min(1,fl[3]/.12f)};
        java.util.Arrays.sort(cand,0,n,(a,b)->Float.compare(dd(a,eye)/a[3],dd(b,eye)/b[3]));
        for(int k=0;k<2;k++){int i=13+k;
            if(k<n){float[] c=cand[k];lights[i*4]=c[0];lights[i*4+1]=c[1];lights[i*4+2]=c[2];lights[i*4+3]=8;lightCol[i*3]=.8f*c[3];lightCol[i*3+1]=1.9f*c[3];lightCol[i*3+2]=4.5f*c[3];}
            else{lights[i*4+3]=0;lightCol[i*3]=lightCol[i*3+1]=lightCol[i*3+2]=0;}}
    }
    private static float dd(float[] a,float[] e){float x=a[0]-e[0],y=a[1]-e[1],z=a[2]-e[2];return x*x+y*y+z*z;}

    // ------------------------------------------------------------------ the chainsaw running and cutting
    private float sawRev,cutTimer,lastGrind;
    private void updateSaw(float dt){
        float target=hero.saw>.5f?1:.12f;sawRev+=(target-sawRev)*Math.min(1,dt*(target>sawRev?6:2.5f));
        chainScroll+=dt*(.2f+sawRev*2.6f);chainScroll-=(float)Math.floor(chainScroll);
        sfx.engine(.18f+sawRev*.55f,.55f+sawRev*1.25f);
        // the bar's leading edge biting into walls: sparks, scars and a grind
        if(hero.saw>.6f){float[] sa=M.pt(hero.sawMat,new float[]{.47f,0,0}),sb=M.pt(hero.sawMat,new float[]{.97f,0,0});
            float[] flesh=enemies.saw(sa,sb,dt);
            if(flesh!=null){fx.blood(flesh[0],flesh[1],flesh[2],sb[0]-sa[0],sb[1]-sa[1],sb[2]-sa[2]);if(time-lastGrind>.14f){lastGrind=time;sfx.play(Sfx.GRIND,.8f,.7f+fx.rng.nextFloat()*.1f);sfx.play(Sfx.HIT,.5f,.7f);}pl.kickVel+=dt*2;}}
        if(hero.saw>.6f){float[] a=M.pt(hero.sawMat,new float[]{.47f,0,0}),b=M.pt(hero.sawMat,new float[]{.97f,0,0});float[] d=Model.sub(b,a);float l=Model.len(d);d=Model.mul(d,1/l);
            float[] hit=level.ray(a[0],a[1],a[2],d[0],d[1],d[2],l);
            if(hit!=null){cutTimer-=dt;if(cutTimer<=0){cutTimer=.06f;fx.impact(hit[0],hit[1],hit[2],hit[3],hit[4],hit[5],(int)hit[7]);if(fx.rng.nextFloat()<.3f)fx.hole(hit[0],hit[1],hit[2],hit[3],hit[4],hit[5]);}
                if(time-lastGrind>.18f){lastGrind=time;sfx.play(Sfx.GRIND,.7f,.9f+fx.rng.nextFloat()*.2f);}pl.kickVel+=dt*3;}}
        // blue-grey exhaust puffs from the pipes
        if(fx.rng.nextFloat()<.25f+sawRev*.5f){float[] e=M.pt(hero.sawMat,new float[]{.1f,-.115f,.13f});float[] v=M.dir(hero.sawMat,new float[]{-.4f,-.3f,.2f});
            fx.spawn(Fx.SMOKE,e[0],e[1],e[2],v[0]+fx.rnd(-.05f,.05f),v[1]+fx.rnd(-.05f,.05f),v[2]+.15f,fx.rnd(.5f,.9f),.012f,.35f,0,1f,.42f,.44f,.48f,.12f+sawRev*.1f);}
    }

    // ------------------------------------------------------------------ third-person camera
    /**
     * Over the right shoulder: a pivot at her head height (smoothed over steps and jumps) offset to the right, the
     * camera pulled back along the view direction. It moves in closer and tighter while she aims, and never goes
     * through walls: rays from her head to the shoulder pivot and from the pivot back to the camera pull it in
     * instantly and let it ease back out.
     */
    private float[] camPos={0,0,0},camF={1,0,0},camR={0,-1,0},camU={0,0,1};private float camDist=2.6f,camPZ=Float.NaN,camSide=.45f,camFov=64;
    private void updateCamera(float dt){
        float p=pl.viewPitch(),cy=(float)Math.cos(pl.yaw),sy=(float)Math.sin(pl.yaw),cp=(float)Math.cos(p);
        camF=new float[]{cy*cp,sy*cp,(float)Math.sin(p)};camR=Model.norm(Model.cross(camF,new float[]{0,0,1}));camU=Model.cross(camR,camF);
        float headZ=pl.z+1.6f-.06f*hero.run;
        camPZ=Float.isNaN(camPZ)?headZ:camPZ+(headZ-camPZ)*Math.min(1,dt*(pl.onGround?14:6));
        float[] head={pl.x,pl.y,camPZ};float[] rh={sy,-cy,0};
        float side=Hero.mix(.42f,.5f,hero.aim);
        float[] hs=level.ray(head[0],head[1],head[2],rh[0],rh[1],0,side+.25f);if(hs!=null)side=Math.max(0,hs[6]-.25f);
        camSide+=(side-camSide)*Math.min(1,dt*(side<camSide?30:5));
        float[] pivot=Model.add(head,rh,camSide);pivot[2]+=.05f;
        float want=Hero.mix(2.7f,1.65f,hero.aim)+.25f*hero.run;
        float[] back=Model.mul(camF,-1);
        float[] hit=level.ray(pivot[0],pivot[1],pivot[2],back[0],back[1],back[2],want+.3f);
        float d=hit!=null?Math.max(.25f,hit[6]-.3f):want;
        camDist=d<camDist?d:camDist+(d-camDist)*Math.min(1,dt*4);
        camPos=Model.add(pivot,back,camDist);
        camFov+=(Hero.mix(64,54,hero.aim)+4*hero.run-camFov)*Math.min(1,dt*8);
    }

    // ------------------------------------------------------------------ render
    private final float[] lights=new float[16*4],lightCol=new float[16*3];
    private void render(){
        float[] f=camF,right=camR,upR=camU;float[] eye=camPos;
        float[] view=M.lookAt(eye,f,upR);float aspect=W/(float)H;
        float[] vp=M.mul(M.perspective((float)Math.toRadians(camFov),aspect,.05f,120f),view);
        GLES30.glViewport(0,0,W,H);GLES30.glClearColor(.02f,.01f,.008f,1);GLES30.glDepthMask(true);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT|GLES30.GL_DEPTH_BUFFER_BIT);
        // lights: the level's, flickering over lava, plus the muzzle flash in the last slot
        int n=Math.min(13,level.lights.size());
        for(int i=0;i<16;i++){
            if(i<n){Level.Light L=level.lights.get(i);float k=L.flicker?.75f+.25f*(float)Math.sin(time*7.3f+i*1.7f)*(float)Math.sin(time*3.1f+i):1;
                lights[i*4]=L.x;lights[i*4+1]=L.y;lights[i*4+2]=L.z;lights[i*4+3]=L.radius;lightCol[i*3]=L.r*k;lightCol[i*3+1]=L.g*k;lightCol[i*3+2]=L.b*k;}
            else if(i==15&&flash>0){float[] mz=M.pt(hero.eyeFrame,gun.muzzleCam);float k=flash/.07f;lights[60]=mz[0]+f[0]*.3f;lights[61]=mz[1]+f[1]*.3f;lights[62]=mz[2]+f[2]*.3f;lights[63]=11;lightCol[45]=9*k;lightCol[46]=5.5f*k;lightCol[47]=2.2f*k;}
            else{lights[i*4+3]=0;lightCol[i*3]=lightCol[i*3+1]=lightCol[i*3+2]=0;}}
        dynamicLights(eye);
        GLES30.glUseProgram(prog);
        GLES30.glUniform4fv(u(prog,"uL"),16,lights,0);GLES30.glUniform3fv(u(prog,"uLC"),16,lightCol,0);GLES30.glUniform1i(u(prog,"uLCount"),16);
        GLES30.glUniform3f(u(prog,"uCam"),eye[0],eye[1],eye[2]);GLES30.glUniform2i(u(prog,"uGrid"),level.W,level.H);GLES30.glUniform2f(u(prog,"uGridO"),level.ox,level.oy);GLES30.glUniform1f(u(prog,"uGridS"),Level.CELL);
        GLES30.glUniform3f(u(prog,"uSky"),.035f,.03f,.03f);GLES30.glUniform3f(u(prog,"uGround"),.03f,.012f,.008f);
        GLES30.glUniform4f(u(prog,"uFog"),.035f,.012f,.008f,.022f);GLES30.glUniform1f(u(prog,"uTime"),time);GLES30.glUniform1f(u(prog,"uExposure"),1.15f);GLES30.glUniform1f(u(prog,"uEnvK"),1f);GLES30.glUniform1f(u(prog,"uShadowK"),7f);
        GLES30.glUniform1i(u(prog,"uA"),0);GLES30.glUniform1i(u(prog,"uB"),1);GLES30.glUniform1i(u(prog,"uC"),2);GLES30.glUniform1i(u(prog,"uMask"),3);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,maskTex);
        GLES30.glUniformMatrix4fv(u(prog,"uVP"),1,false,vp,0);
        float[] I=M.id();model(I);
        for(int m=0;m<Tex.COUNT;m++){if(levelMesh[m].count==0)continue;bindMat(m,true);
            GLES30.glUniform2f(u(prog,"uUvScroll"),m==Tex.LAVA?time*.012f:0,m==Tex.LAVA?time*.007f:0);GLES30.glUniform1f(u(prog,"uPulse"),m==Tex.LAVA?1:0);
            levelMesh[m].draw();}
        GLES30.glUniform2f(u(prog,"uUvScroll"),0,0);GLES30.glUniform1f(u(prog,"uPulse"),0);
        GLES30.glUniform1f(u(prog,"uEnvK"),5f);drawPlayer();drawDemons(eye);GLES30.glUniform1f(u(prog,"uEnvK"),1f);
        drawPickups();
        // spent shells lying around
        for(Fx.Shell s:fx.shells){float[] Mx=M.mul(M.tr(s.x,s.y,s.z),M.mul(M.rz(s.yaw),M.mul(M.ry(s.pitch),M.rx(s.roll))));model(Mx);
            bindMat(Tex.SHELL,false);parts[Gun.P_HULL].draw();bindMat(Tex.BRASS,false);parts[Gun.P_BASE].draw();}
        drawDecals(vp,eye);
        drawParticles(vp,right,upR);
        GLES30.glBindVertexArray(0);
    }
    private void model(float[] m){GLES30.glUniformMatrix4fv(u(prog,"uModel"),1,false,m,0);GLES30.glUniformMatrix3fv(u(prog,"uNrmM"),1,false,M.nrm(m),0);}
    private void bindMat(int m,boolean pom){
        int[] t=tex[m];for(int k=0;k<3;k++){GLES30.glActiveTexture(GLES30.GL_TEXTURE0+k);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,t[k]);}
        Tex.Mat mt=mats[m];GLES30.glUniform1f(u(prog,"uPom"),pom?mt.pom:0);GLES30.glUniform1f(u(prog,"uEmit"),mt.emit);
    }
    private void drawDecals(float[] vp,float[] eye){
        int n=0;float[] b=decalBuf;
        for(int i=0;i<fx.decals;i++){float[] d=fx.decal[i];float nx=d[3],ny=d[4],nz=d[5],s=d[6],rot=d[8];
            float[] N={nx,ny,nz},a=Math.abs(nz)>.9f?new float[]{1,0,0}:new float[]{0,0,1};float[] T=Model.norm(Model.cross(a,N)),B=Model.cross(N,T);
            float c=(float)Math.cos(rot),sn=(float)Math.sin(rot);float[] t2={T[0]*c+B[0]*sn,T[1]*c+B[1]*sn,T[2]*c+B[2]*sn},b2=Model.cross(N,t2);
            float[][] q={{-1,-1},{1,-1},{1,1},{-1,-1},{1,1},{-1,1}};
            for(float[] k:q){b[n++]=d[0]+(t2[0]*k[0]+b2[0]*k[1])*s;b[n++]=d[1]+(t2[1]*k[0]+b2[1]*k[1])*s;b[n++]=d[2]+(t2[2]*k[0]+b2[2]*k[1])*s;b[n++]=k[0]*.5f+.5f;b[n++]=k[1]*.5f+.5f;b[n++]=d[7];}}
        if(n==0)return;
        GLES30.glUseProgram(progDecal);GLES30.glUniformMatrix4fv(u(progDecal,"uVP"),1,false,vp,0);GLES30.glUniform3f(u(progDecal,"uCam"),eye[0],eye[1],eye[2]);GLES30.glUniform4f(u(progDecal,"uFog"),0,0,0,.022f);
        GLES30.glEnable(GLES30.GL_BLEND);GLES30.glBlendFunc(GLES30.GL_DST_COLOR,GLES30.GL_ZERO);GLES30.glDepthMask(false);
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL);GLES30.glPolygonOffset(-1,-2);
        decalStream.draw(b,n);
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL);GLES30.glDepthMask(true);GLES30.glDisable(GLES30.GL_BLEND);
    }
    private void drawParticles(float[] vp,float[] right,float[] up){
        GLES30.glUseProgram(progPart);GLES30.glUniformMatrix4fv(u(progPart,"uVP"),1,false,vp,0);
        GLES30.glEnable(GLES30.GL_BLEND);GLES30.glDepthMask(false);
        for(int pass=0;pass<2;pass++){int n=0;float[] b=partBuf;
            for(int i=0;i<fx.n;i++){boolean smoke=fx.kind[i]==Fx.SMOKE;if(smoke!=(pass==0))continue;
                float life=fx.life[i]/fx.max[i];float al=fx.a[i]*(smoke?Math.min(1,life*1.5f)*Math.min(1,(1-life)*6+.2f):Math.min(1,life*2));
                float s=fx.size[i];float uo=fx.kind[i]==Fx.STAR?2:0;
                // sparks stretch along their motion
                float[] rx=right,ry=up;
                float[][] q={{-1,-1},{1,-1},{1,1},{-1,-1},{1,1},{-1,1}};
                for(float[] k:q){b[n++]=fx.px[i]+(rx[0]*k[0]+ry[0]*k[1])*s;b[n++]=fx.py[i]+(rx[1]*k[0]+ry[1]*k[1])*s;b[n++]=fx.pz[i]+(rx[2]*k[0]+ry[2]*k[1])*s;
                    b[n++]=k[0]*.5f+.5f+uo;b[n++]=k[1]*.5f+.5f;b[n++]=fx.r[i];b[n++]=fx.g[i];b[n++]=fx.b[i];b[n++]=al;}}
            if(n==0)continue;
            if(pass==0)GLES30.glBlendFunc(GLES30.GL_ONE,GLES30.GL_ONE_MINUS_SRC_ALPHA);else GLES30.glBlendFunc(GLES30.GL_ONE,GLES30.GL_ONE);
            partStream.draw(b,n);}
        GLES30.glDepthMask(true);GLES30.glDisable(GLES30.GL_BLEND);
    }
}
