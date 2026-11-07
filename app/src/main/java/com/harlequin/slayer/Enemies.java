package com.harlequin.slayer;

import java.util.ArrayList;

/**
 * Everything the demons share: the list of demons, the navigation flow field toward the warden, their fireballs,
 * hit tests for her pellets and chainsaw, keeping bodies apart, and a queue of events (roars, shots, explosions,
 * hits on her, deaths) for the game to turn into sound and effects. Pure Java, so the AI runs in the unit tests.
 */
final class Enemies {
    static final int EV_ROAR=0,EV_CHARGE=1,EV_SHOOT=2,EV_EXPLODE=3,EV_HIT_PLAYER=4,EV_PAIN=5,EV_DIE=6,EV_STEP=7,EV_HIT=8;
    /** An event at a position (for sound and effects); demon = index or -1. */
    static final class Event {final int type,demon;final float x,y,z,dx,dy,dz;Event(int t,int d,float x,float y,float z,float dx,float dy,float dz){type=t;demon=d;this.x=x;this.y=y;this.z=z;this.dx=dx;this.dy=dy;this.dz=dz;}}
    static final class Fireball {float x,y,z,vx,vy,vz,life;int owner;boolean alive=true;}

    static final float FIREBALL_SPEED=10.5f,FIREBALL_DAMAGE=12f,SPLASH_DAMAGE=7f,SPLASH_RADIUS=1.6f;
    final Level L;final Nav nav;final Player player;
    final ArrayList<Demon> demons=new ArrayList<>();
    final ArrayList<Fireball> fireballs=new ArrayList<>();
    final ArrayList<Event> events=new ArrayList<>();
    private float navTimer;
    /** Attack tokens: only this many demons may be winding up or firing at once (the rest strafe and close in). */
    static final int MAX_ATTACKERS=2;int attackers;
    boolean canAttack(){return attackers<MAX_ATTACKERS;}

    Enemies(Level l,Player p){L=l;player=p;nav=new Nav(l);
        for(float[] s:l.demonSpawns){Demon d=new Demon(demons.size(),s[0],s[1],s[2]);d.E=this;demons.add(d);}
        reset();}

    /** Everyone back to their spawn, fireballs gone (when she dies and respawns). */
    void reset(){fireballs.clear();events.clear();attackers=0;for(Demon d:demons)d.reset(L);attackers=0;navTimer=0;}
    int alive(){int n=0;for(Demon d:demons)if(!d.dead)n++;return n;}

    void event(int type,Demon d){float[] e=d.eye();events.add(new Event(type,d.id,e[0],e[1],e[2],0,0,0));}

    void update(float dt,float time){
        navTimer-=dt;if(navTimer<=0){nav.target(player.x,player.y);navTimer=.2f;}
        for(Demon d:demons){d.update(dt,time,L);if(!d.dead&&d.rig.steps>0&&d.body.onGround)events.add(new Event(EV_STEP,d.id,d.body.x,d.body.y,d.body.z,0,0,0));}
        separate();
        updateFireballs(dt);
    }
    /** A gunshot: every demon within earshot (path distance) knows where she is. */
    void hear(float x,float y,float z,float radius){for(Demon d:demons){if(d.dead)continue;float pd=nav.distAt(d.body.x,d.body.y);
        float straight=(float)Math.hypot(d.body.x-x,d.body.y-y);if(Math.min(pd,straight*1.6f)<radius)d.ai.hear(x,y,z);}}

    /** Bodies don't overlap: demons push apart from each other and from the warden (moving only into free space). */
    private void separate(){
        float min=Demon.RADIUS*Demon.SCALE+Player.RADIUS;
        for(int i=0;i<demons.size();i++){Demon a=demons.get(i);if(a.dead)continue;
            push(a.body,player,min,true);
            for(int j=i+1;j<demons.size();j++){Demon b=demons.get(j);if(b.dead)continue;push(a.body,b.body,2*Demon.RADIUS*Demon.SCALE,false);}}
    }
    private void push(Player a,Player b,float min,boolean onlyA){
        float dx=a.x-b.x,dy=a.y-b.y;if(Math.abs(a.z-b.z)>1.5f)return;float d=(float)Math.hypot(dx,dy);if(d>=min)return;
        if(d<1e-4f){dx=1;dy=0;d=1;}float k=(min-d)/d;
        float wa=onlyA?1:.5f,wb=onlyA?0:.5f;
        float ax=a.x+dx*k*wa,ay=a.y+dy*k*wa;if(!L.blocked(ax,ay,a.z,Player.RADIUS,Player.HEIGHT)&&Nav.standable(L.atF(ax,ay))){a.x=ax;a.y=ay;}
        if(wb>0){float bx=b.x-dx*k*wb,by=b.y-dy*k*wb;if(!L.blocked(bx,by,b.z,Player.RADIUS,Player.HEIGHT)&&Nav.standable(L.atF(bx,by))){b.x=bx;b.y=by;}}
    }

    // ---------------------------------------------------------------- fireballs
    /** Launches a fireball from the demon's muzzle at where the warden will be when it gets there. */
    void fireball(Demon d){
        float[] m=d.muzzle();Player p=player;
        float tz=p.z+1.05f;float dist=(float)Math.sqrt((p.x-m[0])*(p.x-m[0])+(p.y-m[1])*(p.y-m[1])+(tz-m[2])*(tz-m[2]));
        float lead=dist/FIREBALL_SPEED*(.2f+.4f*d.ai.rng.nextFloat());
        float ax=p.x+p.vx*lead,ay=p.y+p.vy*lead,az=tz;
        float dx=ax-m[0],dy=ay-m[1],dz=az-m[2];float l=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(l<1e-3f)return;
        float spread=.06f;dx=dx/l+(d.ai.rng.nextFloat()-.5f)*spread;dy=dy/l+(d.ai.rng.nextFloat()-.5f)*spread;dz=dz/l+(d.ai.rng.nextFloat()-.5f)*spread;l=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        Fireball f=new Fireball();f.x=m[0];f.y=m[1];f.z=m[2];f.vx=dx/l*FIREBALL_SPEED;f.vy=dy/l*FIREBALL_SPEED;f.vz=dz/l*FIREBALL_SPEED;f.life=4;f.owner=d.id;fireballs.add(f);
        events.add(new Event(EV_SHOOT,d.id,m[0],m[1],m[2],dx/l,dy/l,dz/l));
    }
    private void updateFireballs(float dt){
        for(Fireball f:fireballs){if(!f.alive)continue;f.life-=dt;if(f.life<=0){f.alive=false;continue;}
            int sub=4;float h=dt/sub;
            for(int s=0;s<sub&&f.alive;s++){f.x+=f.vx*h;f.y+=f.vy*h;f.z+=f.vz*h;
                // the warden: a capsule around her body
                float dx=f.x-player.x,dy=f.y-player.y;
                if(dx*dx+dy*dy<.42f*.42f&&f.z>player.z&&f.z<player.z+Player.HEIGHT+.05f){
                    player.health-=FIREBALL_DAMAGE;player.hurtFlash=1;float l=(float)Math.hypot(f.vx,f.vy);if(l>0){player.vx+=f.vx/l*2.5f;player.vy+=f.vy/l*2.5f;}
                    events.add(new Event(EV_HIT_PLAYER,f.owner,f.x,f.y,f.z,-f.vx/FIREBALL_SPEED,-f.vy/FIREBALL_SPEED,-f.vz/FIREBALL_SPEED));f.alive=false;break;}
                if(L.inside(f.x,f.y,f.z)){explode(f);break;}}}
        fireballs.removeIf(f->!f.alive);
    }
    private void explode(Fireball f){
        f.alive=false;float bx=f.x-f.vx*.01f,by=f.y-f.vy*.01f,bz=f.z-f.vz*.01f;
        events.add(new Event(EV_EXPLODE,f.owner,bx,by,bz,-f.vx/FIREBALL_SPEED,-f.vy/FIREBALL_SPEED,-f.vz/FIREBALL_SPEED));
        float dx=player.x-bx,dy=player.y-by,dz=player.z+.9f-bz;float d=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(d<SPLASH_RADIUS){player.health-=SPLASH_DAMAGE*(1-d/SPLASH_RADIUS);player.hurtFlash=Math.max(player.hurtFlash,.6f);}
    }

    // ---------------------------------------------------------------- her weapons against them
    /** Nearest demon a ray hits within max: {distance, demon index, 1 if a head shot}; null if none. */
    float[] ray(float ox,float oy,float oz,float dx,float dy,float dz,float max){
        float best=max;int who=-1;boolean head=false;
        for(Demon d:demons){if(d.dead||d.rig.mat[Hero.P_HEAD]==null)continue;
            float bx=d.body.x,by=d.body.y,bz=d.body.z;float cx=ox-bx,cy=oy-by;
            if(Math.abs(cx*dy-cy*dx)>1.2f&&cx*cx+cy*cy>1.5f)continue;   // quick reject: far from the ray in the plane
            float r=Demon.RADIUS*Demon.SCALE;
            for(int k=0;k<5;k++){float z=bz+(.3f+k*.28f)*Demon.SCALE;float t=sphere(ox,oy,oz,dx,dy,dz,bx,by,z,k==0?r*.85f:r);if(t>=0&&t<best){best=t;who=d.id;head=false;}}
            float[] hp=d.headPos();float t=sphere(ox,oy,oz,dx,dy,dz,hp[0],hp[1],hp[2],.17f*Demon.SCALE);if(t>=0&&t<best){best=t;who=d.id;head=true;}}
        return who<0?null:new float[]{best,who,head?1:0};
    }
    private static float sphere(float ox,float oy,float oz,float dx,float dy,float dz,float cx,float cy,float cz,float r){
        float px=ox-cx,py=oy-cy,pz=oz-cz;float b=px*dx+py*dy+pz*dz,c=px*px+py*py+pz*pz-r*r;float h=b*b-c;if(h<0)return -1;h=(float)Math.sqrt(h);float t=-b-h;return t>=0?t:(-b+h>=0?0:-1);
    }
    /** The chainsaw's bar from a to b biting into any demon it touches: damage over time; returns the contact point or null. */
    float[] saw(float[] a,float[] b,float dt){
        float[] hit=null;
        for(Demon d:demons){if(d.dead)continue;float r=Demon.RADIUS*Demon.SCALE+.03f;
            for(int k=0;k<=6;k++){float t=k/6f;float x=a[0]+(b[0]-a[0])*t,y=a[1]+(b[1]-a[1])*t,z=a[2]+(b[2]-a[2])*t;
                float dx=x-d.body.x,dy=y-d.body.y;if(dx*dx+dy*dy<r*r&&z>d.body.z+.1f&&z<d.body.z+1.85f*Demon.SCALE){
                    float l=(float)Math.hypot(dx,dy);d.damage(130*dt,l>0?-dx/l:1,l>0?-dy/l:0);if(d.ai.state()==Demon.PAIN)d.painCool=.6f;
                    hit=new float[]{x,y,z};break;}}}
        return hit;
    }
}
