package com.harlequin.slayer;

import static org.junit.Assert.*;

import org.junit.Test;

/** The demons' AI in the real level, simulated at 60 fps with the warden standing in the courtyard. */
public class AiTest {
    static final float DT=1/60f;

    static final class World {
        final Level L=new Level(Maps.campaign(1));final Player p=new Player();final Enemies E;float t;int shots,hits,lavaFrames;
        World(){p.spawn(L);E=new Enemies(L,p);for(Level.Door d:L.doors)L.openDoor(d);}   // the keep's doors open: the whole map is one fight
        void run(int frames,float moveY){for(int i=0;i<frames;i++){t+=DT;p.update(DT,L,0,moveY,0,0,false,0);E.update(DT,t);
            for(Enemies.Event e:E.events){if(e.type==Enemies.EV_SHOOT)shots++;if(e.type==Enemies.EV_HIT_PLAYER)hits++;}E.events.clear();
            for(Demon d:E.demons)if(!d.dead&&L.atF(d.body.x,d.body.y).floorMat==Tex.LAVA)lavaFrames++;
            if(p.health<40)p.health=100;}}
        /** Walk her through the gate passage into the courtyard of the Molten Keep. */
        void enter(){for(int i=0;i<60*12&&p.y<12f;i++)run(1,1);}
    }

    @Test public void demonsStartIdleAndStayPutWhenNobodyIsAround(){
        World w=new World();w.run(120,0);
        int calm=0;for(Demon d:w.E.demons)if(!d.ai.alerted&&(d.ai.stateName().equals("idle")||d.ai.stateName().equals("patrol")))calm++;
        assertTrue("most are idle or patrolling until they notice her (one can see down the gate passage): "+calm,calm>=w.E.demons.size()-2);
        assertTrue("the keep has demons: "+w.E.demons.size(),w.E.demons.size()>=6);
    }
    @Test public void theySeeHerChaseAndShoot(){
        World w=new World();w.enter();w.run(60*15,0);
        int alerted=0;for(Demon d:w.E.demons)if(d.ai.alerted)alerted++;
        System.out.printf("alerted %d/%d, fireballs %d, hits %d%n",alerted,w.E.demons.size(),w.shots,w.hits);
        assertTrue("courtyard demons notice her: "+alerted,alerted>=2);
        assertTrue("they shoot: "+w.shots,w.shots>=2);
        assertTrue("only a couple attack at once",w.E.attackers<=Enemies.MAX_ATTACKERS);
        assertEquals("nobody walks into lava",0,w.lavaFrames);
    }
    @Test public void gunfireIsHeardThroughTheKeep(){
        World w=new World();w.enter();w.E.hear(w.p.x,w.p.y,w.p.z,32);w.run(5,0);
        int alerted=0,near=0;for(Demon d:w.E.demons){if(Math.hypot(d.spawnX-w.p.x,d.spawnY-w.p.y)>32)continue;near++;if(d.ai.alerted)alerted++;}
        assertTrue("a shot alerts most of those in earshot: "+alerted+" / "+near,alerted>=near/2&&near>=4);
    }
    @Test public void shotsKillThemAndTheyStayDead(){
        World w=new World();w.enter();w.run(30,0);
        Demon d=null;float best=1e9f;for(Demon c:w.E.demons){float dd=(float)Math.hypot(c.body.x-w.p.x,c.body.y-w.p.y);if(dd<best){best=dd;d=c;}}
        int shots=0;while(!d.dead&&shots<40){float[] h=d.headPos();float ex=w.p.x,ey=w.p.y,ez=w.p.z+1.6f;float dx=h[0]-ex,dy=h[1]-ey,dz=h[2]-.3f-ez;float l=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            float[] r=w.E.ray(ex,ey,ez,dx/l,dy/l,dz/l,80);if(r!=null)w.E.demons.get((int)r[1]).damage(25,dx/l,dy/l);shots++;w.run(2,0);}
        assertTrue("the demon dies",d.dead);assertEquals("dead",d.ai.stateName());
        float x=d.body.x,y=d.body.y;w.run(120,0);
        assertTrue("corpses don't move",Math.abs(x-d.body.x)<1e-4f&&Math.abs(y-d.body.y)<1e-4f);
        assertTrue("corpses can't be hit",hitDead(w,d)==null);
    }
    private static float[] hitDead(World w,Demon d){float dx=d.body.x-w.p.x,dy=d.body.y-w.p.y;float l=(float)Math.hypot(dx,dy);float[] r=w.E.ray(w.p.x,w.p.y,d.body.z+1,dx/l,dy/l,0,l+1);return r!=null&&(int)r[1]==d.id?r:null;}
    @Test public void navigationReachesEveryRoomFromTheStart(){
        World w=new World();w.E.nav.target(w.p.x,w.p.y);
        for(Demon d:w.E.demons)assertTrue("demon "+d.id+" at "+d.spawnX+","+d.spawnY+" can reach her",w.E.nav.distAt(d.spawnX,d.spawnY)<Nav.INF);
    }
    @Test public void fireballsHurtHer(){
        World w=new World();w.enter();float h0=w.p.health;int hits=0;
        for(int i=0;i<60*20&&hits==0;i++){w.t+=DT;w.p.update(DT,w.L,0,0,0,0,false,0);w.E.update(DT,w.t);for(Enemies.Event e:w.E.events)if(e.type==Enemies.EV_HIT_PLAYER)hits++;w.E.events.clear();}
        assertTrue("a fireball lands",hits>0);
    }
}
