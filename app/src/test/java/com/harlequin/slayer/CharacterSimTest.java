package com.harlequin.slayer;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Reproducible scenarios for the character simulation, run on the JVM in CI (gradle testDebugUnitTest). Each test
 * builds the real level, the warden's animation (Hero) and her physical body (ActiveRagdoll) with default settings,
 * drives them at 60 fps, and checks measurable acceptance criteria. Timings are printed (JVM on the CI machine,
 * not the phone).
 */
public class CharacterSimTest {
    static final float DT=1/60f;

    /** One character: controller, animation, physics. */
    static final class Rig {
        final Level L=new Level();final Hero h=new Hero();final Gun g=new Gun();final Player pl=new Player();final SimSettings S=new SimSettings();final ActiveRagdoll r;float time;
        final StringBuilder log=new StringBuilder();boolean nonFinite;float minBodyAboveFloor=9,maxKE;
        Rig(float x,float y){pl.spawn(L);pl.x=x;pl.y=y;r=new ActiveRagdoll(S,L,h);
            r.events=new ActiveRagdoll.Events(){public void onImpact(float s){}public void onStumble(){log.append("stumble ");}public void onFall(){log.append("fall ");}
                public void onBalanceLost(){log.append("lost ");}public void onBalanceRestored(){log.append("restored ");}public void onRecover(){log.append("recover ");}
                public void onMuscleFatigue(String j){}public void onMovementStateChanged(String a,String b){log.append(b).append(' ');}};}
        Rig(){this(5.5f,19.5f);}
        void frame(float moveY){time+=DT;
            if(!r.grounded()){pl.update(DT,L,0,moveY,0,0,false,.0055f);
                if(r.stepRequest[0]!=0||r.stepRequest[1]!=0){pl.vx+=r.stepRequest[0]*DT*6;pl.vy+=r.stepRequest[1]*DT*6;}
                h.update(DT,time,pl,L,pl.yaw,0,false,false,g);}
            r.update(DT,time,pl);
            if(r.grounded()){float[] p=h.joints[Hero.J_PELVIS];pl.x=p[0];pl.y=p[1];}
            for(RigidBody b:r.world.bodies){if(!Q.finite(b.x)||!Q.finite(b.q)||!Q.finite(b.v))nonFinite=true;
                if(!b.kinematic)minBodyAboveFloor=Math.min(minBodyAboveFloor,b.x[2]-L.floorUnder(b.x[0],b.x[1],.02f));}
            maxKE=Math.max(maxKE,r.world.kineticEnergy());}
        void run(int frames,float moveY){for(int i=0;i<frames;i++)frame(moveY);}
        boolean saw(String s){return log.indexOf(s)>=0;}
    }

    @Test public void idleStandingIsStable(){
        Rig R=new Rig();R.run(600,0);
        assertFalse(R.nonFinite);assertEquals(ActiveRagdoll.KINEMATIC,R.r.mode);
        float pz=R.r.pelvis.x[2];assertTrue("pelvis height "+pz,pz>.85f&&pz<1.05f);
        assertTrue("standing should be balanced: "+R.r.stability,R.r.stability>0);
    }
    @Test public void poweredHoldStaysTogetherAndReturnsToAnimation(){
        Rig R=new Rig();R.run(30,0);float z0=R.r.pelvis.x[2];R.r.push(1,0,.1f);float maxAnchor=0,minZ=9;
        for(int i=0;i<40;i++){R.frame(0);maxAnchor=Math.max(maxAnchor,R.r.world.maxAnchorError);minZ=Math.min(minZ,R.r.pelvis.x[2]);}
        R.run(120,0);
        System.out.printf("powered hold: max anchor error %.1f mm, pelvis dip %.1f cm, log %s%n",maxAnchor*1000,(z0-minZ)*100,R.log);
        assertFalse(R.nonFinite);assertTrue("joints must stay together: "+maxAnchor,maxAnchor<.02f);assertTrue("pelvis dip "+(z0-minZ),z0-minZ<.06f);
        assertEquals(ActiveRagdoll.KINEMATIC,R.r.mode);
    }
    @Test public void lightPushIsAbsorbedHeavyPushKnocksHerDown(){
        float[] impulses={25,60,110,220};float[] minStab=new float[impulses.length];boolean[] fell=new boolean[impulses.length];
        for(int k=0;k<impulses.length;k++){Rig R=new Rig();R.run(30,0);R.r.push(1,0,impulses[k]);float ms=9;
            for(int i=0;i<240;i++){R.frame(0);ms=Math.min(ms,R.r.stability);}
            minStab[k]=ms;fell[k]=R.saw("fall");assertFalse(R.nonFinite);
            System.out.printf("push %.0f N·s: min stability %+.2f m, fell=%b, log %s%n",impulses[k],ms,fell[k],R.log);}
        assertFalse("a light push must not knock her down",fell[0]);
        assertTrue("a heavy push must knock her down",fell[impulses.length-1]);
        for(int k=1;k<impulses.length;k++)assertTrue("stronger pushes destabilise more",minStab[k]<=minStab[k-1]+.02f);
    }
    private void fallAndRecover(float dirX,String label){
        Rig R=new Rig();R.run(60,dirX>0?1:0);R.r.fallNow(new float[]{1.3f*dirX,0,.3f});
        long t0=System.nanoTime();int s0=R.r.world.steps;float maxLimit=0;float kneeMin=9,kneeMax=-9;int frames=0;
        while(R.r.mode!=ActiveRagdoll.KINEMATIC&&frames<900){R.frame(0);frames++;maxLimit=Math.max(maxLimit,R.r.world.maxLimitError);
            for(PhysJoint j:R.r.world.joints)if(j.name.endsWith("knee")){float a=j.angle();kneeMin=Math.min(kneeMin,a);kneeMax=Math.max(kneeMax,a);}}
        double ms=(System.nanoTime()-t0)/1e6/Math.max(1,R.r.world.steps-s0);
        System.out.printf("%s: back in control after %.2f s, max KE %.0f J, lowest body %.3f m above floor, knee range [%.2f, %.2f] rad, %.3f ms/step (JVM), log %s%n",
            label,frames*DT,R.maxKE,R.minBodyAboveFloor,kneeMin,kneeMax,ms,R.log);
        assertFalse(R.nonFinite);
        assertEquals(label+" should get up and hand back to animation",ActiveRagdoll.KINEMATIC,R.r.mode);
        assertTrue(label+" kinetic energy must stay bounded: "+R.maxKE,R.maxKE<4000);
        assertTrue(label+" no body may sink into the floor: "+R.minBodyAboveFloor,R.minBodyAboveFloor>-.03f);
        assertTrue(label+" knees bend within limits: "+kneeMin+".."+kneeMax,kneeMin>-.25f&&kneeMax<2.65f);
        assertTrue(R.saw("recover"));
    }
    @Test public void fallForwardAndGetUp(){fallAndRecover(1,"forward fall");}
    @Test public void fallBackwardAndGetUp(){fallAndRecover(-1,"backward fall");}

    @Test public void walkingUpStairsKeepsFeetOnTheSteps(){
        Rig R=new Rig(24.5f,13f);R.pl.yaw=(float)Math.PI/2;R.h.bodyYaw=R.pl.yaw;float worst=0,maxPelvisJump=0;float prevZ=Float.NaN;
        for(int i=0;i<480;i++){R.pl.yaw=(float)Math.PI/2;R.frame(.5f);
            for(int s=0;s<2;s++){float[] a=R.h.joints[Hero.J_ANK_L+s];float fl=R.L.floorUnder(a[0],a[1],.02f);worst=Math.min(worst,a[2]-.088f-fl);}
            float pz=R.h.joints[Hero.J_PELVIS][2];if(!Float.isNaN(prevZ))maxPelvisJump=Math.max(maxPelvisJump,Math.abs(pz-prevZ));prevZ=pz;}
        System.out.printf("stairs: climbed to z=%.2f, deepest ankle below its step %.1f cm, largest pelvis jump per frame %.1f cm%n",R.pl.z,-worst*100,maxPelvisJump*100);
        assertTrue("she should climb: "+R.pl.z,R.pl.z>.7f);
        assertTrue("feet must not sink into steps: "+worst,worst>-.04f);
        assertTrue("pelvis must not pop: "+maxPelvisJump,maxPelvisJump<.06f);
    }
    @Test public void severalCharactersPerformance(){
        Rig[] rigs=new Rig[4];for(int i=0;i<4;i++){rigs[i]=new Rig(4.5f+i*1.2f,19.5f);rigs[i].run(20,0);rigs[i].r.fallNow(new float[]{1,0,.3f});}
        long t0=System.nanoTime();int steps=0;for(int f=0;f<300;f++)for(Rig R:rigs){int s=R.r.world.steps;R.frame(0);steps+=R.r.world.steps-s;}
        double ms=(System.nanoTime()-t0)/1e6;
        System.out.printf("4 simultaneous ragdolls, 300 frames: %.1f ms total, %.3f ms per character step (JVM)%n",ms,ms/Math.max(1,steps));
        for(Rig R:rigs)assertFalse(R.nonFinite);
    }
    @Test public void settingsLoadFromProperties(){
        java.util.Properties p=new java.util.Properties();p.setProperty("totalMass","90");p.setProperty("quality","3");p.setProperty("stiffness","oops");
        SimSettings s=SimSettings.load(p);assertEquals(90,s.totalMass,1e-4);assertEquals(12,s.substeps);assertEquals(14,s.stiffness,1e-4);
    }
}
