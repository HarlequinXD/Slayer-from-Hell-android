package com.harlequin.slayer;

import static org.junit.Assert.*;

import org.junit.Test;

/** The chainsaw glory kill played through at 60 fps in a real level: staging, choreography contact, cues, camera. */
public class GloryTest {
    static final float DT=1/60f;

    /** Hell's Gate with the warden placed a step and a half in front of a demon, facing him. */
    static final class Scene {
        final Level L=new Level(Maps.campaign(0));final Player p=new Player();final Enemies E;final Hero hero=new Hero();final Gun gun=new Gun();final Demon d;float t;
        Scene(){p.spawn(L);E=new Enemies(L,p);Demon found=null;
            for(Demon dm:E.demons){for(int k=0;k<16&&found==null;k++){float a=k*.3927f;float x=dm.body.x+(float)Math.cos(a)*1.6f,y=dm.body.y+(float)Math.sin(a)*1.6f;
                    float f=L.floorUnder(x,y,Player.RADIUS);if(f<-50||Math.abs(f-dm.body.z)>.2f||L.blocked(x,y,f,Player.RADIUS,Player.HEIGHT)||!Nav.standable(L.atF(x,y)))continue;
                    float[] w=L.ray(x,y,f+1.2f,-(float)Math.cos(a),-(float)Math.sin(a),0,1.6f);if(w!=null)continue;
                    p.x=x;p.y=y;p.z=f;p.yaw=a+(float)Math.PI;found=dm;}
                if(found!=null)break;}
            d=found;hero.bodyYaw=p.yaw;if(d!=null)stagger(d);
            for(int i=0;i<30;i++){t+=DT;p.update(DT,L,0,0,0,0,false,0);hero.update(DT,t,p,L,p.yaw,0,false,false,gun);for(Demon dm:E.demons)dm.pose(DT,t,L);}}
    }

    /** Shot down to a quarter health: he staggers. */
    static void stagger(Demon d){d.damage(Demon.MAX_HEALTH-Demon.STAGGER_HEALTH+1,1,0);}

    @Test public void playsThroughWithEveryBeatInOrderAndKills(){
        Scene s=new Scene();assertNotNull("a demon with room around him",s.d);
        Glory g=Glory.tryStart(s.p,s.E,s.L);assertNotNull("in reach and in front: a glory kill",g);assertSame(s.d,g.d);
        s.hero.cine=g.her;g.d.rig.cine=g.him;
        int[] order=new int[7];int n=0;float minBar=9,maxGrab=0,camMin=9;int frames=0;boolean cut0=false,cut1=false,cut2=false;
        while(frames<600){frames++;float dt=DT*g.timeScale();s.t+=DT;
            int cue=g.step(dt,s.L,s.hero.sawMat);
            for(int b=0;b<7;b++)if((cue&(1<<b))!=0)order[n++]=1<<b;
            s.p.x=g.px;s.p.y=g.py;s.p.z=g.pz;s.p.vx=s.p.vy=0;
            s.hero.update(dt,s.t,s.p,s.L,g.herYaw,0,false,true,s.gun);
            if(!g.d.dead)g.d.cinePose(dt,s.t,s.L,g.dx,g.dy,g.dz,g.himYaw);else g.d.update(dt,s.t,s.L);
            if((cue&Glory.CUE_DIE)!=0)g.d.damage(g.d.health+1,g.ux,g.uy);
            // the saw is in him while it rips
            if(g.t>Glory.T_RIP+.05f&&g.t<Glory.T_OUT-.05f){float best=9;
                for(float k=.5f;k<=.97f;k+=.05f){float[] q=M.pt(s.hero.sawMat,new float[]{k,0,0});float r=(float)Math.hypot(q[0]-g.dx,q[1]-g.dy);
                    if(q[2]>g.dz+.5f&&q[2]<g.dz+1.9f)best=Math.min(best,r);}
                minBar=Math.min(minBar,best);assertTrue("the bar stays in his body during the rip at t="+g.t+": "+best,best<Demon.RADIUS*Demon.SCALE+.04f);}
            // her left hand on his shoulder while she holds him
            if(g.t>Glory.T_GRAB+.05f&&g.t<Glory.T_OUT-.05f){float[] hand=M.pt(s.hero.mat[Hero.sidePart(1,Hero.S_FORE)],Hero.handCentre(1));
                float[] sh=M.pt(g.d.world,M.pt(g.d.rig.mat[Hero.P_TORSO],Hero.shoulder(-1)));maxGrab=Math.max(maxGrab,Model.len(Model.sub(hand,sh)));}
            // the camera: finite, out of the walls, never on top of them
            for(float c:g.camPos)assertFalse(Float.isNaN(c));
            assertFalse("camera inside a wall at t="+g.t,s.L.inside(g.camPos[0],g.camPos[1],g.camPos[2]));
            camMin=Math.min(camMin,(float)Math.hypot(g.camPos[0]-(g.px+g.dx)*.5f,g.camPos[1]-(g.py+g.dy)*.5f));
            // both of them in frame (a 2:1 screen) through the first two shots, and him as he falls
            {float[] f=Model.norm(Model.sub(g.camTarget,g.camPos)),r=Model.norm(Model.cross(f,new float[]{0,0,1})),u=Model.cross(r,f);float th=(float)Math.tan(Math.toRadians(g.camFov)*.5f);
                float[][] who=g.shot<2?new float[][]{{g.px,g.py,g.pz+1.1f},{g.dx,g.dy,g.dz+1.2f}}:new float[][]{{g.dx,g.dy,g.dz+.5f}};
                for(float[] w:who){float[] e=Model.sub(w,g.camPos);float z=Model.dot(e,f);assertTrue("in front of the camera",z>.3f);
                    assertTrue("in frame at t="+g.t+" shot "+g.shot,Math.abs(Model.dot(e,r)/z)<th*2*.95f&&Math.abs(Model.dot(e,u)/z)<th*.95f);}}
            if(g.shot==0)cut0=true;if(g.shot==1)cut1=true;if(g.shot==2)cut2=true;
            if((cue&Glory.CUE_END)!=0)break;}
        System.out.printf("glory: %d frames, bar %.3f m from his axis, grab within %.3f m, camera >= %.2f m%n",frames,minBar,maxGrab,camMin);
        int[] want={Glory.CUE_GRAB,Glory.CUE_HIT,Glory.CUE_RIP,Glory.CUE_OUT,Glory.CUE_SHOVE,Glory.CUE_DIE,Glory.CUE_END};
        assertEquals("every beat once",7,n);for(int i=0;i<7;i++)assertEquals("beat "+i,want[i],order[i]);
        assertTrue("about two seconds plus the hit-stop: "+frames,frames>130&&frames<175);
        assertTrue("he is dead",g.d.dead);
        float fall=Hero.wrap(g.d.fallYaw-(float)Math.atan2(g.uy,g.ux));assertTrue("he falls away from her: "+fall,Math.abs(fall)<.01f);
        assertTrue("her hand is on his shoulder: "+maxGrab,maxGrab<.2f);
        assertTrue("she stepped in to the kill distance",Math.abs((float)Math.hypot(g.dx-g.px,g.dy-g.py)-Glory.GAP)<.5f);
        assertTrue("all three shots were used",cut0&&cut1&&cut2);
        assertTrue("the camera keeps its distance: "+camMin,camMin>.4f);
    }
    @Test public void needsTheDemonInFrontAndInReach(){
        Scene s=new Scene();
        s.p.yaw+=(float)Math.PI;assertNull("not behind her",Glory.tryStart(s.p,s.E,s.L));
        s.p.yaw-=(float)Math.PI;
        Demon fresh=s.d;fresh.reset(s.L);assertFalse(fresh.staggered());assertNull("not a demon at full fight",Glory.tryStart(s.p,s.E,s.L));stagger(fresh);
        s.d.dead=true;assertNull("not a corpse",Glory.tryStart(s.p,s.E,s.L));
        s.d.dead=false;float ux=s.d.body.x-s.p.x,uy=s.d.body.y-s.p.y,l=(float)Math.hypot(ux,uy);
        s.p.x=s.d.body.x-ux/l*(Glory.REACH+.6f);s.p.y=s.d.body.y-uy/l*(Glory.REACH+.6f);
        Glory g=Glory.tryStart(s.p,s.E,s.L);assertTrue("not out of reach",g==null||g.d!=s.d);
    }
    @Test public void staggerWindowGlowsThenRecovers(){
        AiTest.World w=new AiTest.World();Demon d=w.E.demons.get(0);
        for(int i=0;i<14;i++)d.damage(Enemies.BOLT_DAMAGE,1,0);
        assertFalse("14 pistol shots leave him fighting: "+d.health,d.staggered());
        d.damage(Enemies.BOLT_DAMAGE,1,0);assertTrue("the 15th puts him down to a quarter: he staggers",d.staggered());assertEquals(1,d.glow());
        float x=d.body.x,y=d.body.y;int frames=0;boolean warned=false;
        while(d.staggered()&&frames<600){w.run(1,0);frames++;if(d.glow()==2)warned=true;}
        float secs=frames/60f;System.out.printf("stagger lasted %.2f s, moved %.2f m%n",secs,(float)Math.hypot(d.body.x-x,d.body.y-y));
        assertEquals("the stagger lasts its window",Demon.STAGGER_TIME,secs,.1f);assertTrue("it flashes a warning before it ends",warned);
        assertTrue("he reels on the spot",Math.hypot(d.body.x-x,d.body.y-y)<.5f);
        assertTrue("he shakes it off with some health back",d.health>=Demon.RECOVER_HEALTH&&!d.dead);
        d.damage(d.health-Demon.STAGGER_HEALTH+1,1,0);assertTrue("staggers again",d.staggered());d.damage(d.health,1,0);assertTrue("shooting a staggered demon still kills him",d.dead);
    }
    @Test public void keyframesHitTheirKeysAndStayInRange(){
        float[] T={0,1,2},V={0,1,-1};
        assertEquals(0,Glory.key(T,V,-1),1e-6);assertEquals(1,Glory.key(T,V,1),1e-6);assertEquals(-1,Glory.key(T,V,5),1e-6);assertEquals(.5f,Glory.key(T,V,.5f),1e-6);
        for(float x=0;x<=2;x+=.01f){float k=Glory.key(T,V,x);assertTrue(k>=-1-1e-6&&k<=1+1e-6);}
    }
}
