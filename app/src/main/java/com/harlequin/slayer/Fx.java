package com.harlequin.slayer;

import java.util.ArrayList;
import java.util.Random;

/**
 * World effects: particles (sparks, dust, smoke, splinters, muzzle flashes, lava embers), bullet-hole decals, and
 * spent shotgun shells that tumble, bounce and come to rest on the floor. Pure Java; Game turns them into vertices.
 */
final class Fx {
    final Random rng=new Random(7);
    // ------------------------------------------------------------------ particles
    static final int ADD=0,SMOKE=1,STAR=2;
    static final int MAXP=900;
    final float[] px=new float[MAXP],py=new float[MAXP],pz=new float[MAXP],vx=new float[MAXP],vy=new float[MAXP],vz=new float[MAXP],
            life=new float[MAXP],max=new float[MAXP],size=new float[MAXP],grow=new float[MAXP],grav=new float[MAXP],drag=new float[MAXP],r=new float[MAXP],g=new float[MAXP],b=new float[MAXP],a=new float[MAXP];
    final int[] kind=new int[MAXP];int n;
    void spawn(int k,float x,float y,float z,float dx,float dy,float dz,float lifeS,float sz,float gr,float gv,float dr,float cr,float cg,float cb,float ca){
        if(n>=MAXP)return;int i=n++;kind[i]=k;px[i]=x;py[i]=y;pz[i]=z;vx[i]=dx;vy[i]=dy;vz[i]=dz;life[i]=max[i]=lifeS;size[i]=sz;grow[i]=gr;grav[i]=gv;drag[i]=dr;r[i]=cr;g[i]=cg;b[i]=cb;a[i]=ca;
    }
    float rnd(float a,float b){return a+rng.nextFloat()*(b-a);}
    /** Pellet impact: sparks on metal, chips and dust on stone, splinters on wood, molten spray on lava. */
    void impact(float x,float y,float z,float nx,float ny,float nz,int mat){
        boolean metal=mat==Tex.METAL_PANEL||mat==Tex.DIAMOND_PLATE||mat==Tex.LIGHT,wood=mat==Tex.CRATE,lava=mat==Tex.LAVA;
        int sparks=metal?10:lava?14:3;
        for(int k=0;k<sparks;k++){float s=rnd(2,7);float[] d=jitter(nx,ny,nz,.9f);
            spawn(ADD,x,y,z,d[0]*s,d[1]*s,d[2]*s,rnd(.12f,.35f),rnd(.012f,.025f),-.5f,-9f,.5f,lava?1f:1f,lava?.45f:.75f,lava?.1f:.35f,1);}
        int dust=metal?2:lava?3:6;
        for(int k=0;k<dust;k++){float s=rnd(.3f,1.2f);float[] d=jitter(nx,ny,nz,.7f);
            float c=wood?.3f:lava?.12f:.32f;spawn(SMOKE,x+nx*.03f,y+ny*.03f,z+nz*.03f,d[0]*s,d[1]*s,d[2]*s+.2f,rnd(.6f,1.3f),rnd(.06f,.12f),.35f,-.2f,2.5f,c*(wood?1.3f:1),c*(wood?.9f:1),c*(wood?.6f:1),.55f);}
        if(!metal&&!lava)for(int k=0;k<5;k++){float s=rnd(1.5f,4);float[] d=jitter(nx,ny,nz,.8f);
            float c=wood?.35f:.25f;spawn(SMOKE,x,y,z,d[0]*s,d[1]*s,d[2]*s,rnd(.3f,.6f),rnd(.012f,.02f),0,-9.8f,.2f,c*(wood?1.2f:1),c*(wood?.75f:1),c*(wood?.45f:1),1);}
    }
    private float[] jitter(float nx,float ny,float nz,float k){float x=nx+rnd(-k,k),y=ny+rnd(-k,k),z=nz+rnd(-k,k)+.2f;float l=(float)Math.sqrt(x*x+y*y+z*z);return new float[]{x/l,y/l,z/l};}
    /** Muzzle flash and smoke at the barrel's end, along direction d. */
    void muzzle(float x,float y,float z,float dx,float dy,float dz){
        spawn(STAR,x,y,z,dx*.5f,dy*.5f,dz*.5f,.055f,rnd(.22f,.3f),1.5f,0,0,1f,.75f,.4f,1);
        for(int k=0;k<4;k++){float t=rnd(.05f,.3f);spawn(ADD,x+dx*t,y+dy*t,z+dz*t,dx*rnd(2,6),dy*rnd(2,6),dz*rnd(2,6),rnd(.04f,.08f),rnd(.08f,.14f),-.5f,0,6,1f,.6f,.25f,1);}
        for(int k=0;k<7;k++){float s=rnd(.3f,1.6f);spawn(SMOKE,x+dx*.05f,y+dy*.05f,z+dz*.05f,dx*s+rnd(-.2f,.2f),dy*s+rnd(-.2f,.2f),dz*s+rnd(0,.3f),rnd(.8f,1.8f),rnd(.04f,.08f),.4f,.15f,2.2f,.42f,.4f,.38f,.35f);}
    }
    void update(float dt,Level L){
        for(int i=0;i<n;i++){life[i]-=dt;if(life[i]<=0){copy(--n,i);i--;continue;}
            float k=Math.max(0,1-drag[i]*dt);vx[i]*=k;vy[i]*=k;vz[i]=vz[i]*k+grav[i]*dt;
            float nx=px[i]+vx[i]*dt,ny=py[i]+vy[i]*dt,nz=pz[i]+vz[i]*dt;
            if(L.inside(nx,ny,nz)){vx[i]*=-.3f;vy[i]*=-.3f;vz[i]*=-.3f;}else{px[i]=nx;py[i]=ny;pz[i]=nz;}
            size[i]+=grow[i]*dt*size[i];}
    }
    private void copy(int f,int t){kind[t]=kind[f];px[t]=px[f];py[t]=py[f];pz[t]=pz[f];vx[t]=vx[f];vy[t]=vy[f];vz[t]=vz[f];life[t]=life[f];max[t]=max[f];size[t]=size[f];grow[t]=grow[f];grav[t]=grav[f];drag[t]=drag[f];r[t]=r[f];g[t]=g[f];b[t]=b[f];a[t]=a[f];}

    // ------------------------------------------------------------------ bullet holes
    static final int MAXD=180;
    final float[][] decal=new float[MAXD][];int decals,decalHead;
    void hole(float x,float y,float z,float nx,float ny,float nz){
        float s=rnd(.035f,.06f);float[] d={x+nx*.002f,y+ny*.002f,z+nz*.002f,nx,ny,nz,s,rng.nextFloat()*100,rng.nextFloat()*6.28f};
        decal[decalHead]=d;decalHead=(decalHead+1)%MAXD;decals=Math.min(MAXD,decals+1);
    }

    // ------------------------------------------------------------------ spent shells
    static final class Shell {float x,y,z,vx,vy,vz,yaw,pitch,roll,wy,wp,wr,age;boolean rest;int bounces;}
    final ArrayList<Shell> shells=new ArrayList<>();
    void eject(float x,float y,float z,float[] right,float[] up,float[] fwd){
        Shell s=new Shell();s.x=x;s.y=y;s.z=z;float sr=rnd(2.2f,3.2f),su=rnd(1.6f,2.6f),sf=rnd(-.3f,.4f);
        s.vx=right[0]*sr+up[0]*su+fwd[0]*sf;s.vy=right[1]*sr+up[1]*su+fwd[1]*sf;s.vz=right[2]*sr+up[2]*su+fwd[2]*sf;
        s.yaw=(float)Math.atan2(fwd[1],fwd[0])+1.5f;s.wy=rnd(-14,14);s.wp=rnd(-20,20);s.wr=rnd(-8,8);
        shells.add(s);if(shells.size()>30)shells.remove(0);
    }
    /** Returns how many shells hit the floor this frame (for the tink sound). */
    int updateShells(float dt,Level L){
        int hits=0;
        for(int i=shells.size()-1;i>=0;i--){Shell s=shells.get(i);s.age+=dt;if(s.age>25){shells.remove(i);continue;}
            if(s.rest)continue;
            s.vz-=9.8f*dt;float nx=s.x+s.vx*dt,ny=s.y+s.vy*dt,nz=s.z+s.vz*dt;
            Level.Cell c=L.atF(nx,ny);
            if(c.solid||nz<c.floor-.4f){s.vx*=-.35f;s.vy*=-.35f;nx=s.x;ny=s.y;c=L.atF(nx,ny);}
            float fl=c.floor+.012f;
            if(nz<fl){nz=fl;if(s.vz<-.6f){hits++;s.bounces++;}s.vz=-s.vz*.35f;s.vx*=.6f;s.vy*=.6f;s.wy*=.6f;s.wp*=.4f;s.wr*=.5f;
                if(Math.abs(s.vz)<.4f&&s.vx*s.vx+s.vy*s.vy<.05f){s.rest=true;s.pitch=0;s.roll=(float)Math.round(s.roll/3.14159f)*3.14159f;s.z=fl;}}
            s.x=nx;s.y=ny;s.z=nz;s.yaw+=s.wy*dt;s.pitch+=s.wp*dt;s.roll+=s.wr*dt;
            if(s.rest)s.pitch=0;}
        return hits;
    }
}
