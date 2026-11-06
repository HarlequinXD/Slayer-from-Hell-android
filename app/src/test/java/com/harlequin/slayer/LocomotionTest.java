package com.harlequin.slayer;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Reproducible locomotion scenarios, run on the JVM in CI (gradle testDebugUnitTest): the real level, controller
 * and procedural animation driven at 60 fps, checked against measurable criteria.
 */
public class LocomotionTest {
    static final float DT=1/60f;

    static final class Rig {
        final Level L=new Level();final Hero h=new Hero();final Gun g=new Gun();final Player pl=new Player();float time;
        Rig(float x,float y){pl.spawn(L);pl.x=x;pl.y=y;h.bodyYaw=pl.yaw;}
        Rig(){this(5.5f,19.5f);}
        void frame(float moveY){time+=DT;pl.update(DT,L,0,moveY,0,0,false,.0055f);g.update(DT,false,false);h.update(DT,time,pl,L,pl.yaw,0,false,false,g);}
        void run(int frames,float moveY){for(int i=0;i<frames;i++)frame(moveY);}
    }
    static boolean finite(float[] v){for(float x:v)if(Float.isNaN(x)||Float.isInfinite(x))return false;return true;}

    @Test public void idleStandingIsSteady(){
        Rig R=new Rig();R.run(120,0);float lo=9,hi=-9;
        for(int i=0;i<480;i++){R.frame(0);float[] p=R.h.pelvisPos();assertTrue(finite(p));float z=p[2]-R.pl.z;lo=Math.min(lo,z);hi=Math.max(hi,z);}
        System.out.printf("idle: pelvis height %.3f..%.3f m%n",lo,hi);
        assertTrue("pelvis height "+lo+".."+hi,lo>.8f&&hi<1.15f);
        assertTrue("idle sway must stay small: "+(hi-lo),hi-lo<.05f);
    }
    @Test public void walkingForwardMoves(){
        Rig R=new Rig();R.run(30,0);float x0=R.pl.x,y0=R.pl.y;R.run(180,1);
        float d=(float)Math.hypot(R.pl.x-x0,R.pl.y-y0);
        for(int s=0;s<2;s++)assertTrue(finite(R.h.anklePos(s)));
        assertTrue("she should cover ground: "+d,d>1f);
    }
    @Test public void walkingUpStairsKeepsFeetOnTheSteps(){
        Rig R=new Rig(24.5f,13f);R.pl.yaw=(float)Math.PI/2;R.h.bodyYaw=R.pl.yaw;float worst=0,maxPelvisJump=0;float prevZ=Float.NaN;
        for(int i=0;i<480;i++){R.pl.yaw=(float)Math.PI/2;R.frame(.5f);
            for(int s=0;s<2;s++){float[] a=R.h.hoofPos(s);float fl=R.L.floorUnder(a[0],a[1],.02f);worst=Math.min(worst,a[2]-fl);}
            float pz=R.h.pelvisPos()[2];if(!Float.isNaN(prevZ))maxPelvisJump=Math.max(maxPelvisJump,Math.abs(pz-prevZ));prevZ=pz;}
        System.out.printf("stairs: climbed to z=%.2f, deepest hoof below its step %.1f cm, largest pelvis jump per frame %.1f cm%n",R.pl.z,-worst*100,maxPelvisJump*100);
        assertTrue("she should climb: "+R.pl.z,R.pl.z>.7f);
        assertTrue("hooves must not sink into steps: "+worst,worst>-.04f);
        assertTrue("pelvis must not pop: "+maxPelvisJump,maxPelvisJump<.06f);
    }
}
