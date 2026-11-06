package com.harlequin.slayer;

import android.content.Context;
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
    final Level level=new Level();final Player pl=new Player();final Gun gun=new Gun();final Fx fx=new Fx();final Hero hero=new Hero();final Ragdoll rag=new Ragdoll();DroppedGun dropped;
    // HUD state (read by the UI thread)
    volatile String loading="Forging materials…";volatile float progress;volatile int ammo,reserve,fps;volatile float health=100,hurt,spread,hint=6;volatile boolean reloading,hasGun=true;

    Game(Context c){ctx=c;pl.spawn(level);hero.bodyYaw=pl.yaw;}

    // ------------------------------------------------------------------ GL resources
    private int prog,progDecal,progPart;
    private final java.util.HashMap<String,Integer> uni=new java.util.HashMap<>();
    private Gfx.Mesh[] levelMesh;private Gfx.Mesh[] parts;private int maskTex;
    private final int[][] tex=new int[Tex.COUNT][];
    private static volatile Tex.Mat[] mats;private static final AtomicInteger made=new AtomicInteger();
    private Gfx.Stream decalStream,partStream;
    private int W=1,H=1;private long last;private float time,flash;private int frames;private long fpsStart;
    private float[] decalBuf=new float[Fx.MAXD*6*6],partBuf=new float[Fx.MAXP*6*9];

    @Override public void onSurfaceCreated(GL10 unused,EGLConfig cfg){
        prog=Gfx.program(Shaders.VS_PBR,Shaders.FS_PBR);progDecal=Gfx.program(Shaders.VS_DECAL,Shaders.FS_DECAL);progPart=Gfx.program(Shaders.VS_PART,Shaders.FS_PART);uni.clear();
        java.util.ArrayList<float[]>[] geo=level.build();levelMesh=new Gfx.Mesh[Tex.COUNT];for(int i=0;i<Tex.COUNT;i++)levelMesh[i]=new Gfx.Mesh(geo[i].get(0));
        float[][] g=Gun.build();parts=new Gfx.Mesh[g.length];for(int i=0;i<g.length;i++)parts[i]=g[i]==null?null:new Gfx.Mesh(g[i]);
        loadBakedGun();loadHero();loadSaw();
        maskTex=Gfx.maskTexture(level.lightMask,level.W,level.H);
        decalStream=new Gfx.Stream(3,2,1);partStream=new Gfx.Stream(3,2,4);
        for(int i=0;i<Tex.COUNT;i++)tex[i]=null;
        if(mats==null){mats=new Tex.Mat[Tex.COUNT];
            ExecutorService ex=Executors.newFixedThreadPool(Math.max(2,Runtime.getRuntime().availableProcessors()-1));
            for(int i=0;i<Tex.COUNT;i++){final int id=i;ex.submit(()->{mats[id]=Tex.make(id);made.incrementAndGet();});}ex.shutdown();}
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);GLES30.glDepthFunc(GLES30.GL_LEQUAL);GLES30.glDisable(GLES30.GL_CULL_FACE);
        last=System.nanoTime();
    }
    @Override public void onSurfaceChanged(GL10 unused,int w,int h){W=Math.max(1,w);H=Math.max(1,h);GLES30.glViewport(0,0,W,H);}

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
    /** The warden in her animated pose, then the shotgun in her hands and any shells in the chambers or her hand. */
    private void drawPlayer(){
        for(int k=0;k<3;k++){GLES30.glActiveTexture(GLES30.GL_TEXTURE0+k);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,heroTex[k]);}
        GLES30.glUniform1f(u(prog,"uPom"),heroPom*.35f);GLES30.glUniform1f(u(prog,"uEmit"),4f);
        for(int p=0;p<heroParts.length&&p<Hero.PARTS;p++){if(heroParts[p]==null||hero.mat[p]==null)continue;model(hero.mat[p]);heroParts[p].draw();}
        float[] E=hero.eyeFrame;
        if(hero.hasGun||dropped!=null){bindGun();model(M.mul(E,gun.mat[Gun.P_ACTION]));gunMesh[0].draw();model(M.mul(E,gun.mat[Gun.P_BARRELS]));gunMesh[1].draw();}
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
        update(dt);
        render();
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
        boolean wantRag=in.takeRagdoll();
        int ev=0;
        if(!ragOn){
            pl.update(dt,level,in.moveX,in.moveY,look[0],look[1],jump,.0055f);
            if(pl.justJumped)sfx.play(Sfx.JUMP,.5f);
            if(pl.justLanded)sfx.play(Sfx.LAND,Math.min(1,pl.landSpeed/12));
            if(pl.hurtFlash>.99f&&(int)(time*4)%2==0)sfx.play(Sfx.SIZZLE,.4f);
            boolean rl=in.takeReload();
            if(hero.hasGun){gun.update(dt,in.fire,rl);ev=gun.events;}
            hero.update(dt,time,pl,level,pl.yaw,pl.viewPitch(),in.fire,in.saw,gun);
            if(!hero.hasGun&&dropped!=null)gun.poseWorld(dropped.matrix());
            blendFromGetUp(dt);
            // walking into walls: a recoil, or a hard run into one knocks her down
            if(pl.bumpSpeed>0){sfx.play(Sfx.LAND,Math.min(1,pl.bumpSpeed/5)*.7f,1.2f);
                if(pl.bumpSpeed>3.7f&&hero.run>.7f)startRagdoll(new float[]{pl.bumpNx*1.6f,pl.bumpNy*1.6f,.4f});
                else hero.bump(pl.bumpNx,pl.bumpNy,pl.bumpSpeed);}
            if(pl.justLanded&&pl.landSpeed>13.5f)startRagdoll(new float[]{pl.vx*.3f,pl.vy*.3f,0});
            if(wantRag&&!ragOn){float c=(float)Math.cos(hero.bodyYaw),s=(float)Math.sin(hero.bodyYaw);float side=(fx.rng.nextFloat()-.5f)*.8f;
                startRagdoll(new float[]{c*1.3f-s*side,s*1.3f+c*side,.3f});}
            // walking over the dropped shotgun picks it up
            if(!hero.hasGun&&dropped!=null&&dropped.resting&&!hero.picking()){float dx=dropped.c[0]-pl.x,dy=dropped.c[1]-pl.y;
                if(dx*dx+dy*dy<.55f&&Math.abs(dropped.c[2]-pl.z)<.7f)hero.pickUp(dropped.c);}
            if(!hero.hasGun&&dropped!=null&&hero.pickGrabbed()){hero.hasGun=true;dropped=null;gun.clearSpent();sfx.play(Sfx.PUMP_FWD,.8f,1.1f);}
        }else updateRagdoll(dt);
        if(dropped!=null)dropped.update(dt,level);
        updateSaw(dt);
        if(hero.steps>0&&pl.onGround)sfx.play(Sfx.STEP,.3f+.15f*hero.run,.9f+fx.rng.nextFloat()*.2f);
        updateCamera(dt);
        float[] f=camF,right=camR,up=camU;float[] E=hero.eyeFrame;
        if((ev&Gun.EV_FIRE)!=0){
            sfx.play(Sfx.BLAST,1f);flash=.07f;pl.kickVel+=1.6f;
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
                if(hit!=null){fx.impact(hit[0],hit[1],hit[2],hit[3],hit[4],hit[5],(int)hit[7]);if((int)hit[7]!=Tex.LAVA&&(int)hit[7]!=Tex.LIGHT)fx.hole(hit[0],hit[1],hit[2],hit[3],hit[4],hit[5]);}}
            float[] gf=Model.norm(M.dir(M.mul(E,gun.mat[Gun.P_ACTION]),new float[]{1,0,0}));fx.muzzle(mz[0],mz[1],mz[2],gf[0],gf[1],gf[2]);
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
            if(!c.solid&&c.floorMat==Tex.LAVA)fx.spawn(Fx.ADD,ex,ey,c.floor+.05f,fx.rnd(-.2f,.2f),fx.rnd(-.2f,.2f),fx.rnd(.6f,1.6f),fx.rnd(1.2f,2.6f),fx.rnd(.012f,.025f),-.2f,.1f,.2f,1f,.4f,.08f,1);}
        fx.update(dt,level);
        if(fx.updateShells(dt,level)>0)sfx.play(Sfx.TINK,.5f,.9f+fx.rng.nextFloat()*.3f);
        flash=Math.max(0,flash-dt);hint=Math.max(0,hint-dt);
        hasGun=hero.hasGun;ammo=hero.hasGun?gun.ammo():0;reserve=gun.reserve;health=pl.health;hurt=pl.hurtFlash;reloading=gun.reloading();spread=gun.recoilNow;
    }
    // ------------------------------------------------------------------ ragdoll, get-up, dropped shotgun
    private boolean ragOn;private float[][] getUpEnd;private float blendT=9;
    private void startRagdoll(float[] push){
        if(ragOn)return;ragOn=true;
        rag.start(hero.joints,hero.prevJoints,1/60f,new float[]{pl.vx*0+push[0],push[1],push[2]});
        if(hero.hasGun){   // she lets go of the shotgun
            float[] G=gun.mat[Gun.P_ACTION];float[] hv=Model.sub(hero.joints[Hero.J_HAND_L],hero.prevJoints[Hero.J_HAND_L]);
            dropped=new DroppedGun(G,new float[]{hv[0]*60+push[0]*.6f,hv[1]*60+push[1]*.6f,hv[2]*60+1.5f},fx.rng);hero.hasGun=false;
            if(gun.reloading()){gun.state=Gun.IDLE;gun.t=0;}}
        sfx.play(Sfx.JUMP,.7f,.8f);
    }
    private void updateRagdoll(float dt){
        rag.step(dt,level,time);
        float[][] J=rag.state==Ragdoll.FALL||rag.state==Ragdoll.DOWN?rag.p:rag.getUpPose(dt,level);
        if(J==null)J=rag.p;
        hero.fromJoints(J,dt);
        if(rag.impact>3.5f&&time-lastThud>.12f){lastThud=time;sfx.play(Sfx.LAND,Math.min(1,rag.impact/9),.85f+fx.rng.nextFloat()*.2f);}
        float[] pel=J[Hero.J_PELVIS];pl.x=pel[0];pl.y=pel[1];pl.z=level.floorUnder(pl.x,pl.y,.1f);pl.vx=pl.vy=pl.vz=0;
        if(dropped!=null)gun.poseWorld(dropped.matrix());
        if(rag.state==Ragdoll.DONE){
            ragOn=false;pl.x=rag.standAt[0];pl.y=rag.standAt[1];pl.z=level.floorUnder(pl.x,pl.y,Player.RADIUS);pl.onGround=true;
            hero.resetFacing(rag.standYaw);getUpEnd=J;blendT=0;}
    }
    private float lastThud;
    /** Blends from the end of a get-up into locomotion. */
    private void blendFromGetUp(float dt){
        if(blendT>=.4f||getUpEnd==null)return;blendT+=dt;float b=Hero.smooth(0,.4f,blendT);
        float[][] L=hero.currentJoints();float[][] J=new float[Hero.JOINTS][];for(int i=0;i<Hero.JOINTS;i++)J[i]=Hero.lerpW(getUpEnd[i],L[i],b);
        hero.fromJoints(J,dt);
    }

    // ------------------------------------------------------------------ the chainsaw running and cutting
    private float sawRev,cutTimer,lastGrind;
    private void updateSaw(float dt){
        float target=hero.saw>.5f||hero.fidgetRev?1:.12f;sawRev+=(target-sawRev)*Math.min(1,dt*(target>sawRev?6:2.5f));
        chainScroll+=dt*(.2f+sawRev*2.6f);chainScroll-=(float)Math.floor(chainScroll);
        sfx.engine(.18f+sawRev*.55f,.55f+sawRev*1.25f);
        // the bar's leading edge biting into walls: sparks, scars and a grind
        if(hero.saw>.6f&&!ragOn){float[] a=M.pt(hero.sawMat,new float[]{.47f,0,0}),b=M.pt(hero.sawMat,new float[]{.97f,0,0});float[] d=Model.sub(b,a);float l=Model.len(d);d=Model.mul(d,1/l);
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
        float headZ=ragOn?Math.max(pl.z+.55f,hero.joints[Hero.J_PELVIS][2]+.65f):pl.z+1.6f-.06f*hero.run;
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
        int n=Math.min(15,level.lights.size());
        for(int i=0;i<16;i++){
            if(i<n){Level.Light L=level.lights.get(i);float k=L.flicker?.75f+.25f*(float)Math.sin(time*7.3f+i*1.7f)*(float)Math.sin(time*3.1f+i):1;
                lights[i*4]=L.x;lights[i*4+1]=L.y;lights[i*4+2]=L.z;lights[i*4+3]=L.radius;lightCol[i*3]=L.r*k;lightCol[i*3+1]=L.g*k;lightCol[i*3+2]=L.b*k;}
            else if(i==15&&flash>0){float[] mz=M.pt(hero.eyeFrame,gun.muzzleCam);float k=flash/.07f;lights[60]=mz[0]+f[0]*.3f;lights[61]=mz[1]+f[1]*.3f;lights[62]=mz[2]+f[2]*.3f;lights[63]=11;lightCol[45]=9*k;lightCol[46]=5.5f*k;lightCol[47]=2.2f*k;}
            else{lights[i*4+3]=0;lightCol[i*3]=lightCol[i*3+1]=lightCol[i*3+2]=0;}}
        GLES30.glUseProgram(prog);
        GLES30.glUniform4fv(u(prog,"uL"),16,lights,0);GLES30.glUniform3fv(u(prog,"uLC"),16,lightCol,0);GLES30.glUniform1i(u(prog,"uLCount"),16);
        GLES30.glUniform3f(u(prog,"uCam"),eye[0],eye[1],eye[2]);GLES30.glUniform2i(u(prog,"uGrid"),level.W,level.H);
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
        GLES30.glUniform1f(u(prog,"uEnvK"),5f);drawPlayer();GLES30.glUniform1f(u(prog,"uEnvK"),1f);
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
