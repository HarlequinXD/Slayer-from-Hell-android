package com.harlequin.slayer;

import static org.junit.Assert.*;

import org.junit.Test;

/** Blood droplets in a real level: they fly, land, and leave stains on floors and walls; pools spread. */
public class BloodTest {
    static final float DT=1/60f;
    final Level L=new Level(Maps.campaign(0));

    void run(Blood b,float secs){for(int i=0;i<secs*60;i++)b.update(DT,L);}

    @Test public void dropletsLandOnTheFloorAsSplats(){
        Blood b=new Blood();float x=L.spawnX,y=L.spawnY,z=L.floorUnder(x,y,0);
        b.spray(x,y,z+1.2f,1,0,.3f,30,3,.5f);assertEquals(30,b.drops.size());
        run(b,3);
        assertEquals("every droplet has landed",0,b.drops.size());
        int floor=0;for(int i=0;i<b.count;i++){Blood.Splat s=b.splats[i];if(s.type==Blood.FLOOR&&s.nz>0){floor++;
            assertEquals("on the floor where it landed",L.floorUnder(s.x,s.y,0),s.z,.05f);assertTrue(s.r>0&&s.r<.3f);}}
        assertTrue("splats on the floor: "+floor,floor>=10);
    }
    @Test public void sprayAtAWallStainsItAndRunsDown(){
        Blood b=new Blood();float x=L.spawnX,y=L.spawnY,z=L.floorUnder(x,y,0)+1.3f;float[] best=null;
        for(int k=0;k<16;k++){float a=k*.3927f;float[] h=L.ray(x,y,z,(float)Math.cos(a),(float)Math.sin(a),0,8);if(h!=null&&Math.abs(h[5])<.1f&&(best==null||h[6]<best[6]))best=h;}
        assertNotNull("a wall near the spawn",best);
        float dx=-best[3],dy=-best[4];
        float sx=best[0]+best[3]*1f,sy=best[1]+best[4]*1f;   // a metre in front of it
        b.spray(sx,sy,z,dx,dy,.15f,30,6,.15f);run(b,4);
        int wall=0;float maxDrip=0;
        for(int i=0;i<b.count;i++){Blood.Splat s=b.splats[i];if(s.type!=Blood.WALL)continue;wall++;maxDrip=Math.max(maxDrip,s.drip);
            assertEquals("faces back out of the wall",0,s.nz,1e-6);assertTrue("above the floor",s.z>L.floorUnder(s.x+s.nx*.05f,s.y+s.ny*.05f,0));}
        System.out.printf("wall splats %d, longest run %.1f radii%n",wall,maxDrip);
        assertTrue("splats on the wall: "+wall,wall>=8);assertTrue("they can run down",maxDrip>1);
    }
    @Test public void aPoolSpreadsOut(){
        Blood b=new Blood();b.pool(0,0,0,.7f);Blood.Splat s=b.splats[0];
        float r0=s.r;run(b,1);float r1=s.r;run(b,5);
        assertTrue("it grows",r0<r1&&r1<s.r);assertEquals("to its size",.7f,s.r,.05f);
    }
    @Test public void oldestStainsAreRecycled(){
        Blood b=new Blood();for(int i=0;i<Blood.MAXS+50;i++)b.add(0,0,0,0,0,1,.1f,Blood.FLOOR);
        assertEquals(Blood.MAXS,b.count);
    }
}
