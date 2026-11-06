package com.harlequin.slayer;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** The game: update loop and OpenGL ES 3 rendering of the level, effects and the first-person shotgun. */
final class Game implements GLSurfaceView.Renderer {
    private final Context ctx;Hud hud;Sfx sfx;
    final Level level=new Level();final Player pl=new Player();final Gun gun=new Gun();final Fx fx=new Fx();
    // HUD state (read by the UI thread)
    volatile String loading="Forging materials…";volatile float progress;volatile int ammo,reserve,fps;volatile float health=100,hurt,spread,hint=6;volatile boolean reloading;

    Game(Context c){ctx=c;pl.spawn(level);}

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
        loadBakedGun();loadHero();
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
    // ------------------------------------------------------------------ the warden (baked showcase figure in the start room)
    private Gfx.Mesh heroMesh;private int[] heroTex;private float heroPom;
    private void loadHero(){
        try(java.io.InputStream in=ctx.getAssets().open("hero.bin")){
            java.nio.ByteBuffer b=java.nio.ByteBuffer.wrap(readAll(in)).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            if(b.getInt()!=0x4e554753)throw new RuntimeException("hero.bin");heroPom=b.getFloat();b.getInt();
            int n=b.getInt();float[] v=new float[n*11];for(int i=0;i<v.length;i++)v[i]=b.getFloat();heroMesh=new Gfx.Mesh(v);
            heroTex=new int[]{asset("hero_a.png",true),asset("hero_b.png",false),asset("hero_c.png",false)};
        }catch(Exception e){throw new RuntimeException("baked hero: "+e);}
    }
    /** She stands a few metres ahead of the spawn point, turned to face it, with her visor and blade glowing. */
    private void drawHero(){
        float hx=level.spawnX+4.6f,hy=level.spawnY+.9f,hz=level.floorUnder(hx,hy,.3f);
        float yaw=(float)Math.atan2(level.spawnY-hy,level.spawnX-hx);
        float breathe=1+.006f*(float)Math.sin(time*1.6f);
        model(M.mul(M.tr(hx,hy,hz),M.mul(M.rz(yaw),M.scale(1,1,breathe))));
        for(int k=0;k<3;k++){GLES30.glActiveTexture(GLES30.GL_TEXTURE0+k);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,heroTex[k]);}
        GLES30.glUniform1f(u(prog,"uPom"),heroPom*.35f);GLES30.glUniform1f(u(prog,"uEmit"),4f);GLES30.glUniform1f(u(prog,"uPulse"),1);
        heroMesh.draw();GLES30.glUniform1f(u(prog,"uPulse"),0);
    }
    private int asset(String name,boolean srgb)throws java.io.IOException{
        android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();o.inPremultiplied=false;o.inScaled=false;o.inPreferredConfig=android.graphics.Bitmap.Config.ARGB_8888;
        android.graphics.Bitmap bm;try(java.io.InputStream in=ctx.getAssets().open(name)){bm=android.graphics.BitmapFactory.decodeStream(in,null,o);}
        int w=bm.getWidth(),h=bm.getHeight();int[] px=new int[w*h];bm.getPixels(px,0,w,0,0,w,h);bm.recycle();
        if(!name.equals("gun_c.png")&&!bm.hasAlpha()){}
        return Gfx.texture(px,w,h,srgb,false);
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
        pl.update(dt,level,in.moveX,in.moveY,look[0],look[1],jump,.0055f);
        if(pl.justJumped)sfx.play(Sfx.JUMP,.5f);
        if(pl.justLanded)sfx.play(Sfx.LAND,Math.min(1,pl.landSpeed/12));
        if(pl.stepCount!=lastStep){lastStep=pl.stepCount;sfx.play(Sfx.STEP,.35f,.9f+fx.rng.nextFloat()*.2f);}
        if(pl.hurtFlash>.99f&&(int)(time*4)%2==0)sfx.play(Sfx.SIZZLE,.4f);
        gun.update(dt,in.fire,in.takeReload());
        int ev=gun.events;
        float[] f=pl.forward(),right=Model.norm(Model.cross(f,new float[]{0,0,1})),up=Model.cross(right,f);
        float[] eye={pl.x,pl.y,pl.eyeZ()};
        float[] cam=camMatrix(eye,f,right,up);
        if((ev&Gun.EV_FIRE)!=0){
            sfx.play(Sfx.BLAST,1f);flash=.07f;pl.kickVel+=4.2f;
            for(int k=0;k<9;k++){   // nine buckshot pellets in a cone
                float a=fx.rng.nextFloat()*6.2832f,rr=(float)Math.sqrt(fx.rng.nextFloat())*.05f;
                float[] d=Model.norm(new float[]{f[0]+right[0]*(float)Math.cos(a)*rr+up[0]*(float)Math.sin(a)*rr,f[1]+right[1]*(float)Math.cos(a)*rr+up[1]*(float)Math.sin(a)*rr,f[2]+right[2]*(float)Math.cos(a)*rr+up[2]*(float)Math.sin(a)*rr});
                float[] hit=level.ray(eye[0],eye[1],eye[2],d[0],d[1],d[2],70);
                if(hit!=null){fx.impact(hit[0],hit[1],hit[2],hit[3],hit[4],hit[5],(int)hit[7]);if((int)hit[7]!=Tex.LAVA&&(int)hit[7]!=Tex.LIGHT)fx.hole(hit[0],hit[1],hit[2],hit[3],hit[4],hit[5]);}}
        }
        if((ev&Gun.EV_OPEN)!=0)sfx.play(Sfx.PUMP_BACK,.8f,.8f);
        if((ev&Gun.EV_CLOSE)!=0)sfx.play(Sfx.PUMP_FWD,.9f,.75f);
        if((ev&Gun.EV_SHELL_IN)!=0)sfx.play(Sfx.SHELL_IN,.7f);
        if((ev&Gun.EV_DRY)!=0)sfx.play(Sfx.DRY,.8f);
        gun.pose(pl.bobPhase,pl.bobAmt,look[0],look[1],time,dt);
        if((ev&Gun.EV_FIRE)!=0){float[] mz=M.pt(cam,gun.muzzleCam);fx.muzzle(mz[0],mz[1],mz[2],f[0],f[1],f[2]);}
        if((ev&Gun.EV_EJECT)!=0){   // the extractors throw the fired cases up and back over the shoulder
            float[] back=Model.norm(new float[]{-f[0]*.85f+right[0]*.35f,-f[1]*.85f+right[1]*.35f,-f[2]*.85f+right[2]*.35f});
            for(int b=0;b<2;b++)if(gun.spent[b]){float[] p=M.pt(cam,gun.chamberCam[b]);fx.eject(p[0],p[1],p[2],back,up,right);}
            gun.clearSpent();}
        // barrel smoke for a moment after each shot
        if(gun.state==Gun.FIRE&&gun.t<1.2f&&fx.rng.nextFloat()<.35f){float[] mz=M.pt(cam,gun.muzzleCam);fx.spawn(Fx.SMOKE,mz[0],mz[1],mz[2],fx.rnd(-.05f,.05f),fx.rnd(-.05f,.05f),fx.rnd(.15f,.35f),fx.rnd(.8f,1.4f),.025f,.6f,0,1f,.5f,.48f,.46f,.25f);}
        // embers rising from the lava
        for(int k=0;k<2;k++){float ex=pl.x+fx.rnd(-12,12),ey=pl.y+fx.rnd(-12,12);Level.Cell c=level.atF(ex,ey);
            if(!c.solid&&c.floorMat==Tex.LAVA)fx.spawn(Fx.ADD,ex,ey,c.floor+.05f,fx.rnd(-.2f,.2f),fx.rnd(-.2f,.2f),fx.rnd(.6f,1.6f),fx.rnd(1.2f,2.6f),fx.rnd(.012f,.025f),-.2f,.1f,.2f,1f,.4f,.08f,1);}
        fx.update(dt,level);
        if(fx.updateShells(dt,level)>0)sfx.play(Sfx.TINK,.5f,.9f+fx.rng.nextFloat()*.3f);
        flash=Math.max(0,flash-dt);hint=Math.max(0,hint-dt);
        ammo=gun.ammo();reserve=gun.reserve;health=pl.health;hurt=pl.hurtFlash;reloading=gun.reloading();spread=gun.recoilNow;
    }
    /** Camera-space (x forward, y left, z up) to world, with the view roll applied to the up vector. */
    private float[] camMatrix(float[] eye,float[] f,float[] right,float[] up){
        float c=(float)Math.cos(pl.roll),s=(float)Math.sin(pl.roll);float[] upR={up[0]*c+right[0]*s,up[1]*c+right[1]*s,up[2]*c+right[2]*s};
        float[] left=Model.cross(upR,f);
        return M.basis(eye,f,left,upR);
    }

    // ------------------------------------------------------------------ render
    private final float[] lights=new float[16*4],lightCol=new float[16*3];
    private void render(){
        float[] f=pl.forward(),right=Model.norm(Model.cross(f,new float[]{0,0,1})),up=Model.cross(right,f);
        float[] eye={pl.x,pl.y,pl.eyeZ()};float[] cam=camMatrix(eye,f,right,up);float[] upR={cam[8],cam[9],cam[10]};
        float[] view=M.lookAt(eye,f,upR);float aspect=W/(float)H;
        float[] vp=M.mul(M.perspective((float)Math.toRadians(66),aspect,.05f,120f),view);
        float[] vpGun=M.mul(M.perspective((float)Math.toRadians(54),aspect,.012f,10f),view);
        GLES30.glViewport(0,0,W,H);GLES30.glClearColor(.02f,.01f,.008f,1);GLES30.glDepthMask(true);GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT|GLES30.GL_DEPTH_BUFFER_BIT);
        // lights: the level's, flickering over lava, plus the muzzle flash in the last slot
        int n=Math.min(15,level.lights.size());
        for(int i=0;i<16;i++){
            if(i<n){Level.Light L=level.lights.get(i);float k=L.flicker?.75f+.25f*(float)Math.sin(time*7.3f+i*1.7f)*(float)Math.sin(time*3.1f+i):1;
                lights[i*4]=L.x;lights[i*4+1]=L.y;lights[i*4+2]=L.z;lights[i*4+3]=L.radius;lightCol[i*3]=L.r*k;lightCol[i*3+1]=L.g*k;lightCol[i*3+2]=L.b*k;}
            else if(i==15&&flash>0){float[] mz=M.pt(cam,gun.muzzleCam);float k=flash/.07f;lights[60]=mz[0]+f[0]*.3f;lights[61]=mz[1]+f[1]*.3f;lights[62]=mz[2]+f[2]*.3f;lights[63]=11;lightCol[45]=9*k;lightCol[46]=5.5f*k;lightCol[47]=2.2f*k;}
            else{lights[i*4+3]=0;lightCol[i*3]=lightCol[i*3+1]=lightCol[i*3+2]=0;}}
        GLES30.glUseProgram(prog);
        GLES30.glUniform4fv(u(prog,"uL"),16,lights,0);GLES30.glUniform3fv(u(prog,"uLC"),16,lightCol,0);GLES30.glUniform1i(u(prog,"uLCount"),16);
        GLES30.glUniform3f(u(prog,"uCam"),eye[0],eye[1],eye[2]);GLES30.glUniform2i(u(prog,"uGrid"),level.W,level.H);
        GLES30.glUniform3f(u(prog,"uSky"),.035f,.03f,.03f);GLES30.glUniform3f(u(prog,"uGround"),.03f,.012f,.008f);
        GLES30.glUniform4f(u(prog,"uFog"),.035f,.012f,.008f,.022f);GLES30.glUniform1f(u(prog,"uTime"),time);GLES30.glUniform1f(u(prog,"uExposure"),1.15f);GLES30.glUniform1f(u(prog,"uShadowK"),7f);
        GLES30.glUniform1i(u(prog,"uA"),0);GLES30.glUniform1i(u(prog,"uB"),1);GLES30.glUniform1i(u(prog,"uC"),2);GLES30.glUniform1i(u(prog,"uMask"),3);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,maskTex);
        GLES30.glUniformMatrix4fv(u(prog,"uVP"),1,false,vp,0);
        float[] I=M.id();model(I);
        for(int m=0;m<Tex.COUNT;m++){if(levelMesh[m].count==0)continue;bindMat(m,true);
            GLES30.glUniform2f(u(prog,"uUvScroll"),m==Tex.LAVA?time*.012f:0,m==Tex.LAVA?time*.007f:0);GLES30.glUniform1f(u(prog,"uPulse"),m==Tex.LAVA?1:0);
            levelMesh[m].draw();}
        GLES30.glUniform2f(u(prog,"uUvScroll"),0,0);GLES30.glUniform1f(u(prog,"uPulse"),0);
        drawHero();
        // spent shells lying around
        for(Fx.Shell s:fx.shells){float[] Mx=M.mul(M.tr(s.x,s.y,s.z),M.mul(M.rz(s.yaw),M.mul(M.ry(s.pitch),M.rx(s.roll))));model(Mx);
            bindMat(Tex.SHELL,false);parts[Gun.P_HULL].draw();bindMat(Tex.BRASS,false);parts[Gun.P_BASE].draw();}
        drawDecals(vp,eye);
        drawParticles(vp,right,upR);
        // first-person shotgun and hands on top
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT);GLES30.glUseProgram(prog);GLES30.glUniformMatrix4fv(u(prog,"uVP"),1,false,vpGun,0);
        for(int p=0;p<Gun.PARTS;p++){if(!gun.visible[p])continue;float[] Mx=M.mul(cam,gun.mat[p]);model(Mx);
            if(Gun.PART_MAT[p]<0){bindGun();gunMesh[p==Gun.P_ACTION?0:1].draw();continue;}
            bindMat(Gun.PART_MAT[p],false);parts[p].draw();
            if(p==Gun.P_SLEEVE||p==Gun.P_CUFF){model(M.mul(cam,gun.leftArm));parts[p].draw();}}
        for(int k=0;k<gun.shellCount;k++){model(M.mul(cam,gun.shellMats[k]));bindMat(Tex.SHELL,false);parts[Gun.P_HULL].draw();bindMat(Tex.BRASS,false);parts[Gun.P_BASE].draw();}
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
