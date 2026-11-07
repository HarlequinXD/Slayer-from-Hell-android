package com.harlequin.slayer;

import java.util.ArrayList;
import java.util.Random;

/**
 * Blood, in the spirit of RPG Pocket Editor's (droplets that fly and stick where they land) but taken further:
 *
 *  - droplets are real projectiles under gravity and air drag, drawn as streaks along their motion, and collide with
 *    the level's floors, walls, step risers and ceilings;
 *  - where one lands it leaves a splat shaped by how it arrived: round from straight above, a stretched splash with
 *    satellite droplets thrown ahead when it hit at a grazing angle;
 *  - splats on walls grow runs that creep down the wall over the next few seconds (stopping above the floor);
 *  - a body leaves a pool that spreads out under it;
 *  - wounds keep bleeding for a few seconds, so a hurt demon leaves a trail of drips;
 *  - fresh blood is bright and wet-looking, and darkens to a dried brown with a darker rim over half a minute.
 * Shapes are worked out in the decal shader from a seed (no textures), in 8 steps per radius to match the chunky
 * pixels. Pure Java; Game turns it into vertices.
 */
final class Blood {
    static final class Drop {float x,y,z,vx,vy,vz,size,life;boolean chunk;}
    /** A stain: centre, surface normal, radius (and the radius a pool grows to), the direction it was travelling in
     *  the surface's frame, how stretched it is, its age, seed, and for walls how far its runs can go (in radii). */
    static final class Splat {float x,y,z,nx,ny,nz,r,rMax,ang,stretch,age,seed,drip;int type;}
    static final int FLOOR=0,WALL=1,POOL=2;
    static final int MAXD=420,MAXS=360;
    final Random rng=new Random(11);
    final ArrayList<Drop> drops=new ArrayList<>();
    final Splat[] splats=new Splat[MAXS];int count,head;

    float rnd(float a,float b){return a+rng.nextFloat()*(b-a);}

    /** n droplets from (x,y,z) in a cone (spread 0..1) around direction d at about `speed` m/s. */
    void spray(float x,float y,float z,float dx,float dy,float dz,int n,float speed,float spread){
        float l=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(l<1e-4f){dx=0;dy=0;dz=1;l=1;}dx/=l;dy/=l;dz/=l;
        for(int i=0;i<n&&drops.size()<MAXD;i++){
            float ex=dx+rnd(-spread,spread),ey=dy+rnd(-spread,spread),ez=dz+rnd(-spread,spread)+rnd(0,.35f);float el=(float)Math.sqrt(ex*ex+ey*ey+ez*ez);
            float s=speed*rnd(.35f,1.15f);
            Drop d=new Drop();d.x=x;d.y=y;d.z=z;d.vx=ex/el*s;d.vy=ey/el*s;d.vz=ez/el*s;d.size=rnd(.012f,.03f);d.life=4;drops.add(d);}
    }
    /** Heavier gobbets (the rip of a glory kill): bigger, slower, leaving big splats. */
    void chunks(float x,float y,float z,float dx,float dy,float dz,int n,float speed){
        for(int i=0;i<n&&drops.size()<MAXD;i++){Drop d=new Drop();d.x=x;d.y=y;d.z=z;float s=speed*rnd(.4f,1f);
            d.vx=(dx+rnd(-.7f,.7f))*s;d.vy=(dy+rnd(-.7f,.7f))*s;d.vz=(dz+rnd(.2f,1f))*s;d.size=rnd(.04f,.065f);d.chunk=true;d.life=4;drops.add(d);}
    }
    /** A slow drip falling from a wound. */
    void drip(float x,float y,float z){
        if(drops.size()>=MAXD)return;Drop d=new Drop();d.x=x+rnd(-.03f,.03f);d.y=y+rnd(-.03f,.03f);d.z=z;d.vx=rnd(-.15f,.15f);d.vy=rnd(-.15f,.15f);d.vz=rnd(-.3f,0);d.size=rnd(.012f,.022f);d.life=3;drops.add(d);
    }
    /** A pool spreading out on the floor at (x,y,z) to radius r. */
    void pool(float x,float y,float z,float r){Splat s=add(x,y,z+.003f,0,0,1,.05f,POOL);s.rMax=r;s.stretch=1;}

    Splat add(float x,float y,float z,float nx,float ny,float nz,float r,int type){
        Splat s=new Splat();s.x=x;s.y=y;s.z=z;s.nx=nx;s.ny=ny;s.nz=nz;s.r=r;s.rMax=r;s.type=type;s.seed=rng.nextFloat()*97;s.stretch=1;s.ang=rng.nextFloat()*6.2832f;
        splats[head]=s;head=(head+1)%MAXS;count=Math.min(MAXS,count+1);return s;
    }

    void update(float dt,Level L){
        for(int i=0;i<count;i++){Splat s=splats[i];s.age+=dt;if(s.type==POOL)s.r=s.rMax*(1-(float)Math.exp(-s.age*.7f))+.05f*(float)Math.exp(-s.age*.7f);}
        for(int i=drops.size()-1;i>=0;i--){Drop d=drops.get(i);d.life-=dt;if(d.life<=0){drops.remove(i);continue;}
            float k=Math.max(0,1-(d.chunk?.15f:.35f)*dt);d.vx*=k;d.vy*=k;d.vz=d.vz*k-9.8f*dt;
            float nx=d.x+d.vx*dt,ny=d.y+d.vy*dt,nz=d.z+d.vz*dt;
            if(!L.inside(nx,ny,nz)){d.x=nx;d.y=ny;d.z=nz;continue;}
            // it hit something: find the surface and leave a splat shaped by how it arrived
            float sx=nx-d.x,sy=ny-d.y,sz=nz-d.z,sl=(float)Math.sqrt(sx*sx+sy*sy+sz*sz);
            float[] h=sl>1e-5f?L.ray(d.x,d.y,d.z,sx/sl,sy/sl,sz/sl,sl+.05f):null;
            float hx,hy,hz,qx,qy,qz;
            if(h!=null){hx=h[0];hy=h[1];hz=h[2];qx=h[3];qy=h[4];qz=h[5];}
            else{hx=d.x;hy=d.y;float f=L.floorUnder(d.x,d.y,0);hz=f>-50?f:d.z;qx=0;qy=0;qz=1;}
            land(d,hx,hy,hz,qx,qy,qz,L);drops.remove(i);}
    }
    private void land(Drop d,float x,float y,float z,float nx,float ny,float nz,Level L){
        float sp=(float)Math.sqrt(d.vx*d.vx+d.vy*d.vy+d.vz*d.vz);if(sp<1e-4f)sp=1e-4f;
        float into=-(d.vx*nx+d.vy*ny+d.vz*nz)/sp;   // 1 = head on, 0 = grazing
        float stretch=1+Math.min(2.2f,(1-Math.max(0,into))*2.4f*Math.min(1,sp/3));
        float r=d.size*rnd(1.8f,3f)*(d.chunk?1.7f:1)*(.8f+.2f*Math.min(3,sp));
        if(nz>.5f){   // floor
            Splat s=add(x,y,z+.003f,0,0,1,r,FLOOR);s.ang=(float)Math.atan2(d.vy,d.vx);s.stretch=stretch;}
        else if(nz<-.5f){add(x,y,z-.003f,0,0,-1,r*.8f,FLOOR);}   // ceiling
        else{   // wall: oriented in the wall's frame; runs may go down to just above the floor
            Splat s=add(x+nx*.004f,y+ny*.004f,z,nx,ny,0,r,WALL);
            float tx=-ny,ty=nx;float along=d.vx*tx+d.vy*ty;s.ang=(float)Math.atan2(d.vz,along);s.stretch=stretch;
            float fl=L.floorUnder(x+nx*.05f,y+ny*.05f,0);float room=fl>-50?z-fl-.02f:1;
            s.drip=Math.max(0,Math.min(5,room/r-1.2f));}
    }
}
