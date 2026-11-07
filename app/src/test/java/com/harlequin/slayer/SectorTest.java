package com.harlequin.slayer;

import static org.junit.Assert.*;

import java.util.ArrayList;
import org.junit.Test;

/** Sector maps: geometry, collision, rays, the editor's operations, saving, and the campaign maps. */
public class SectorTest {
    static ArrayList<float[]> poly(float... xy){ArrayList<float[]> p=new ArrayList<>();for(int i=0;i+1<xy.length;i+=2)p.add(new float[]{xy[i],xy[i+1]});return p;}
    static float triArea(float[] t){float a=0;for(int k=0;k+5<t.length;k+=6)a+=((t[k+2]-t[k])*(t[k+5]-t[k+1])-(t[k+4]-t[k])*(t[k+3]-t[k+1]))*.5f;return a;}
    /** A 10 x 8 room with a pillar, a pit and a platform against the north wall. */
    static MapEditor room(){
        SectorMap m=new SectorMap();MapEditor ed=new MapEditor(m);
        assertNotNull(ed.rectangle(0,0,10,8));
        assertNotNull("pillar inside the room",ed.rectangle(2,2,3,3));ed.makePillar(1);
        SectorMap.Sector pit=ed.rectangle(5,2,7,4);assertNotNull("pit inside the room",pit);pit.floor=-1;
        SectorMap.Sector plat=ed.rectangle(6,6,10,8);assertNotNull("platform against the wall",plat);plat.floor=.5f;
        m.playerX=1;m.playerY=6;m.index();return ed;
    }

    @Test public void nestedSectorsTriangulateWithHoles(){
        MapEditor ed=room();SectorMap m=ed.map;
        float room=triArea(m.triangulate(0));
        assertEquals("room floor = 80 - pillar 1 - pit 4 - platform 8",67,room,1e-3f);
        for(int i=1;i<m.sectors.size();i++)assertEquals(Math.abs(m.area(m.sectors.get(i))),triArea(m.triangulate(i)),1e-3f);
        assertEquals("innermost sector wins",2,m.sectorAt(6,3));assertEquals(0,m.sectorAt(1,1));assertEquals(-1,m.sectorAt(11,1));
    }
    @Test public void linesKnowBothSides(){
        MapEditor ed=room();SectorMap m=ed.map;int walls=0,twoSided=0;
        for(SectorMap.Line L:m.lines){if(L.back<0)walls++;else twoSided++;}
        // outer walls: the room's boundary (the platform shares part of it, split at its corners); inside: pillar 4, pit 4, platform 2 open edges
        assertEquals(10,twoSided);
        SectorMap.Line north=null;for(SectorMap.Line L:m.lines){SectorMap.Vertex a=m.vertices.get(L.a),b=m.vertices.get(L.b);if(a.y==8&&b.y==8&&Math.min(a.x,b.x)>=6)north=L;}
        assertNotNull(north);assertEquals("the platform's wall edge is solid",-1,north.back);assertEquals(3,north.front);
        assertTrue(walls>=5);
    }
    @Test public void collisionAndHeights(){
        Level L=new Level(room().map);
        assertFalse("open floor",L.blocked(1,1,0,.3f,1.75f));
        assertTrue("against the outer wall",L.blocked(.1f,4,0,.3f,1.75f));
        assertTrue("the pillar",L.blocked(2.5f,1.85f,0,.3f,1.75f));
        assertFalse("onto the platform (a 0.5 m step)",L.blocked(6,5.85f,0,.3f,1.75f));
        assertEquals(.5f,L.floorUnder(7,7,.3f),1e-4f);
        assertEquals(-1f,L.floorUnder(6,3,0),1e-4f);
        Level.Cell c=L.atF(2.5f,2.5f);assertTrue("pillar cells are solid",c.solid);
    }
    @Test public void raysHitWallsFloorsAndPillars(){
        Level L=new Level(room().map);
        float[] h=L.ray(1,1,1,1,0,0,20);assertNotNull(h);assertEquals("hits the east wall",9,h[6],.06f);assertEquals(-1,h[3],1e-3f);
        h=L.ray(1,2.5f,1,1,0,0,20);assertEquals("hits the pillar",1,h[6],.06f);assertEquals(Tex.PILLAR,(int)h[7]);
        h=L.ray(1,1,1,0,0,-1,20);assertEquals("hits the floor",1,h[6],.06f);assertEquals(1,h[5],1e-3f);
    }
    @Test public void editorRejectsBadShapesAndAllowsNesting(){
        SectorMap m=new SectorMap();MapEditor ed=new MapEditor(m);
        assertNotNull(ed.rectangle(0,0,6,6));
        assertNull("partial overlap",ed.rectangle(4,4,8,8));assertTrue(ed.error,ed.error.equals("Overlaps another sector")||ed.error.equals("Crosses an existing wall"));
        assertNull("bow tie",ed.addSector(poly(10,0,14,3,14,0,10,2)));assertEquals("Sector edges intersect",ed.error);
        assertNull("same shape",ed.rectangle(0,0,6,6));
        assertNotNull("beside it",ed.rectangle(6,0,9,3));
        assertNotNull("inside it",ed.rectangle(1,1,2,2));
        assertNotNull("drawn round it",ed.rectangle(-2,-2,12,10));
        assertEquals("Geometry valid",ed.validateMap().equals("Geometry valid")?"Geometry valid":ed.validateMap());
    }
    @Test public void undoRedoAndSaveRoundTrip(){
        MapEditor ed=room();SectorMap m=ed.map;int n=m.sectors.size();
        assertTrue(ed.deleteSector(n-1));assertEquals(n-1,m.sectors.size());
        assertTrue(ed.undo());assertEquals(n,ed.map.sectors.size());assertTrue(ed.redo());assertEquals(n-1,ed.map.sectors.size());
        ed.placeThing(SectorMap.T_DEMON,8,2,1);
        SectorMap back=SectorMap.load(ed.map.save()).index();
        assertEquals(ed.map.save(),back.save());assertEquals(1,back.things.size());
    }
    @Test public void lineToolCutsASectorInTwo(){
        SectorMap m=new SectorMap();MapEditor ed=new MapEditor(m);ed.rectangle(0,0,8,4);
        assertTrue(ed.error,ed.splitSector(3,0,3,4));
        assertEquals(2,m.sectors.size());assertEquals(12,Math.abs(m.area(m.sectors.get(0)))+0*1,12.001f);
        float a=Math.abs(m.area(m.sectors.get(0))),b=Math.abs(m.area(m.sectors.get(1)));assertEquals(32,a+b,1e-3f);assertEquals(12,Math.min(a,b),1e-3f);
        assertFalse("can't cut along an existing wall",ed.splitSector(0,0,0,4));
        assertTrue(ed.undo());assertEquals(1,ed.map.sectors.size());
    }
    @Test public void stairsRiseAwayFromTheCursor(){
        SectorMap m=new SectorMap();MapEditor ed=new MapEditor(m);ed.rectangle(0,0,4,4);
        assertTrue(ed.makeStairs(0,2,0,.25f,.5f));assertEquals(8,m.sectors.size());
        float lowY=0,highY=0,lo=9,hi=-9;for(SectorMap.Sector s:m.sectors){float[] c=m.centroid(s);if(s.floor<lo){lo=s.floor;lowY=c[1];}if(s.floor>hi){hi=s.floor;highY=c[1];}}
        assertTrue("lowest step at the cursor's (south) side",lowY<highY);assertEquals(2,hi,1e-4f);
    }
    @Test public void extrudeAndDuplicate(){
        SectorMap m=new SectorMap();MapEditor ed=new MapEditor(m);ed.rectangle(0,0,4,4);
        assertTrue(ed.extrude(0,1,3));assertEquals(2,m.sectors.size());assertEquals(12,Math.abs(m.area(m.sectors.get(1))),1e-3f);
        assertFalse("a copy on top overlaps",ed.duplicateSector(0,1,1));
        assertTrue(ed.duplicateSector(0,0,6));
    }
    @Test public void campaignMapsAreValidAndPlayable(){
        for(int i=0;i<Maps.count();i++){SectorMap m=Maps.campaign(i);Level L=new Level(m);
            assertEquals(m.name,"Geometry valid",new MapEditor(m).validateMap());
            for(int s=0;s<m.sectors.size();s++){float want=Math.abs(m.area(m.sectors.get(s)));for(int h:m.holes.get(s))want-=Math.abs(m.area(m.sectors.get(h)));
                assertEquals(m.name+" sector "+s+" floor covers its area",want,triArea(m.triangulate(s)),.01f);}
            Nav nav=new Nav(L);nav.target(L.spawnX,L.spawnY);
            for(float[] d:L.demonSpawns)assertTrue(m.name+": demon at "+d[0]+","+d[1]+" can reach her",nav.distAt(d[0],d[1])<Nav.INF);
            for(Level.Pickup p:L.pickups)assertTrue(m.name+": pickup at "+p.x+","+p.y+" reachable",nav.distAt(p.x,p.y)<Nav.INF);
            boolean exit=false;for(SectorMap.Sector s:m.sectors)if(s.special==SectorMap.SP_EXIT){float[] c=m.centroid(s);exit|=nav.distAt(c[0],c[1])<Nav.INF;}
            assertTrue(m.name+" has a reachable exit",exit);
            assertFalse(m.name+": the start is clear",L.blocked(L.spawnX,L.spawnY,L.floorUnder(L.spawnX,L.spawnY,.3f),Player.RADIUS,Player.HEIGHT));
            assertTrue(m.name+" has demons",L.demonSpawns.size()>=5);
            assertTrue(m.name+" lights",L.lights.size()>=6&&L.lights.size()<=Level.MAX_LIGHTS);}
    }
    @Test public void mapStoreSavesAndLists()throws Exception{
        java.io.File dir=java.nio.file.Files.createTempDirectory("maps").toFile();MapStore st=new MapStore(dir);
        SectorMap m=Maps.blank();m.name="Test level";String id=st.save(null,m);
        assertEquals(1,st.list().size());assertEquals("Test level",st.list().get(0).name);
        assertEquals(m.save(),st.load(id).save());assertTrue(st.delete(id));assertEquals(0,st.list().size());
    }
    @Test public void paintedWallsAndPicking(){
        MapEditor ed=room();SectorMap m=ed.map;Level L=new Level(m.copy());
        float[] hit=L.pick(1,1,1,1,0,0,20);   // east wall from inside the room
        assertNotNull(hit);assertEquals(Level.PICK_WALL,(int)hit[0]);assertEquals(0,(int)hit[1]);
        hit=L.pick(1,1,1,0,0,-1,20);assertEquals(Level.PICK_FLOOR,(int)hit[0]);assertEquals(0,(int)hit[1]);
        hit=L.pick(1,1,1,0,0,1,20);assertEquals(Level.PICK_CEIL,(int)hit[0]);
        float[] wall=L.pick(1,1,1,1,0,0,20);int a=(int)wall[2],b=(int)wall[3];
        assertTrue(ed.setWallTexture(a,b,Tex.METAL_PANEL));
        SectorMap back=SectorMap.load(m.save());assertEquals(Tex.METAL_PANEL,back.wallTexture(a,b,-1));
        java.util.ArrayList<float[]>[] g=new Level(m.copy()).build();assertTrue("the painted wall is drawn in metal",g[Tex.METAL_PANEL].get(0).length>0);
        // splitting the painted wall keeps the paint on both halves
        SectorMap.Vertex va=m.vertices.get(a),vb=m.vertices.get(b);int e=-1;SectorMap.Sector s0=m.sectors.get(0);for(int k=0;k<s0.loop.size();k++){int x=s0.loop.get(k),y=s0.loop.get((k+1)%s0.loop.size());if(x==a&&y==b||x==b&&y==a)e=k;}
        assertTrue(e>=0);assertTrue(ed.error,ed.splitWall(0,e,(va.x+vb.x)/2,Math.round((va.y+vb.y)/2)));
        int painted=0;for(Integer t:m.wallTex.values())if(t==Tex.METAL_PANEL)painted++;assertEquals(2,painted);
        assertTrue(ed.undo());assertEquals(1,ed.map.wallTex.size());
    }
    @Test public void skillLevelsChooseWhichDemonsAppear(){
        int[] counts=new int[5];
        for(int k=0;k<5;k++){Skill.current=k;counts[k]=new Level(Maps.campaign(0)).demonSpawns.size();}
        Skill.current=Skill.MEDIUM;
        assertEquals("the two easy skills share a set",counts[0],counts[1]);assertEquals(counts[3],counts[4]);
        assertTrue("easy < hurt me plenty < ultra-violence: "+java.util.Arrays.toString(counts),counts[1]<counts[2]&&counts[2]<counts[3]);
        SectorMap m=Maps.campaign(0);m.things.get(0).skills=Skill.F_HARD;assertEquals(Skill.F_HARD,SectorMap.load(m.save()).things.get(0).skills);
        // nobody waits in sight of the start
        for(int i=0;i<Maps.count();i++){Skill.current=Skill.HARD;Level L=new Level(Maps.campaign(i));Skill.current=Skill.MEDIUM;
            for(float[] d:L.demonSpawns){float dx=d[0]-L.spawnX,dy=d[1]-L.spawnY;float l=(float)Math.hypot(dx,dy);
                float[] h=L.ray(L.spawnX,L.spawnY,1.6f,dx/l,dy/l,(L.floorUnder(d[0],d[1],0)+1.6f-1.6f)/l,l);
                assertTrue(L.map.name+": demon at "+d[0]+","+d[1]+" is "+l+" m from the start",l>12);}}
    }
    @Test public void doomDamageNumbers(){
        Level L=new Level(Maps.campaign(0));Player p=new Player();p.spawn(L);Enemies E=new Enemies(L,p);
        for(int i=0;i<200;i++){int s=E.shotDamage(),pl=E.plasmaDamage(),saw=E.sawDamage();
            assertTrue(s>=3&&s<=15&&s%3==0);assertTrue(pl>=5&&pl<=40&&pl%5==0);assertTrue(saw>=2&&saw<=20&&saw%2==0);}
        assertEquals(20,Demon.MAX_HEALTH,0);
    }
}
