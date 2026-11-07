package com.harlequin.slayer;

import java.util.ArrayList;

/**
 * The built-in campaign, built sector by sector through the same validated operations the level editor uses
 * (so a map that would be broken can't be built). Pure Java.
 */
final class Maps {
    static final String[] CAMPAIGN={"Hell's Gate","The Molten Keep"};
    static int count(){return CAMPAIGN.length;}
    static SectorMap campaign(int i){return i==1?moltenKeep():hellsGate();}

    // ------------------------------------------------------------------ a tiny builder on top of MapEditor
    static final class B {
        final MapEditor ed;final SectorMap m;
        B(String name){m=new SectorMap();m.name=name;ed=new MapEditor(m);}
        SectorMap.Sector poly(float floor,float ceil,int fl,int ce,int wa,float... xy){
            ArrayList<float[]> p=new ArrayList<>();for(int i=0;i+1<xy.length;i+=2)p.add(new float[]{xy[i],xy[i+1]});
            SectorMap.Sector s=ed.addSector(p);if(s==null)throw new IllegalStateException(m.name+": "+ed.error+" at "+java.util.Arrays.toString(xy));
            s.floor=floor;s.ceil=ceil;s.floorTex=fl;s.ceilTex=ce;s.wallTex=wa;m.index();return s;}
        SectorMap.Sector rect(float x0,float y0,float x1,float y1,float floor,float ceil,int fl,int ce,int wa){return poly(floor,ceil,fl,ce,wa,x0,y0,x1,y0,x1,y1,x0,y1);}
        /** A square pillar (a sector raised to its room's ceiling). */
        void pillar(float x0,float y0,float x1,float y1){SectorMap.Sector s=rect(x0,y0,x1,y1,0,0,Tex.METAL_PANEL,Tex.METAL_PANEL,Tex.PILLAR);int i=m.sectors.indexOf(s);
            int host=-1;m.index();host=m.parent[i];SectorMap.Sector h=host>=0?m.sectors.get(host):null;s.floor=s.ceil=h!=null?h.ceil:4;m.index();}
        SectorMap.Sector lava(float floor,float... xy){SectorMap.Sector s=poly(floor,0,Tex.LAVA,Tex.CONCRETE,Tex.BRICK,xy);int host=m.parent[m.sectors.indexOf(s)];
            s.ceil=host>=0?m.sectors.get(host).ceil:6;s.special=SectorMap.SP_LAVA;m.index();return s;}
        /** A rectangle turned into stairs rising away from (fx,fy), `rise` per step, steps about `depth` deep. */
        void stairs(float x0,float y0,float x1,float y1,float base,float ceil,float fx,float fy,float rise,float depth,int fl,int wa){
            SectorMap.Sector s=rect(x0,y0,x1,y1,base,ceil,fl,Tex.CONCRETE,wa);
            if(!ed.makeStairs(m.sectors.indexOf(s),fx,fy,rise,depth))throw new IllegalStateException(m.name+" stairs: "+ed.error);m.index();}
        void thing(int type,float x,float y,float angle){m.things.add(new SectorMap.Thing(type,x,y,angle));}
        void demon(float x,float y,float angle){thing(SectorMap.T_DEMON,x,y,angle);}
        void lamp(int type,float x,float y){thing(type,x,y,0);}
        void start(float x,float y,float facing){m.playerX=x;m.playerY=y;m.facing=facing;}
        SectorMap done(){m.index();String v=ed.validateMap();if(!v.equals("Geometry valid"))throw new IllegalStateException(m.name+": "+v);return m;}
    }
    static final float N=(float)Math.PI/2,S=-N,E=0,W=(float)Math.PI;
    static final int BR=Tex.BRICK,MP=Tex.METAL_PANEL,FT=Tex.FLOOR_TILES,DP=Tex.DIAMOND_PLATE,CO=Tex.CONCRETE,CR=Tex.CRATE,LT=Tex.LIGHT,PI=Tex.PILLAR;

    /**
     * Map 1. A chamfered entry hall with twin pillars; a metal doorway into the great hall (7 m high: a stepped dais
     * with an altar, four pillars, a lava trench); a window and a door into the armoury (crates to hide behind); stairs
     * up to the upper gallery with its lava pool; and the exit shrine beyond.
     */
    static SectorMap hellsGate(){
        B b=new B("Hell's Gate");
        // entry hall
        b.poly(0,4.5f,FT,CO,BR,0,2,2,0,6,0,8,2,8,10,0,10);
        b.pillar(2,5,3,6);b.pillar(5,5,6,6);
        b.lamp(SectorMap.T_LAMP_WARM,1.5f,8.5f);b.lamp(SectorMap.T_LAMP_WARM,6.5f,8.5f);
        b.start(4,1.5f,N);b.thing(SectorMap.T_SHELLS,1.5f,3.5f,0);
        // doorway
        b.rect(3,10,5,13,0,3,DP,MP,MP);
        // great hall
        b.poly(0,7,FT,MP,BR,-4,15,-2,13,10,13,12,15,12,25,10,27,-2,27,-4,25);
        b.lava(-.8f,-3.5f,16,-1.5f,16,-1.5f,24,-3.5f,24);
        b.pillar(0,15,1,16);b.pillar(7,15,8,16);b.pillar(0,24,1,25);b.pillar(7,24,8,25);
        SectorMap.Sector dais=b.poly(.5f,7,MP,MP,PI,2,18,6,18,6,22,2,22);
        SectorMap.Sector altar=b.poly(1,7,LT,MP,PI,3,19,5,19,5,21,3,21);
        b.lamp(SectorMap.T_LAMP_RED,4,20);b.lamp(SectorMap.T_LAMP_WARM,-1,14.5f);b.lamp(SectorMap.T_LAMP_WARM,9,14.5f);
        b.demon(4,20,S);b.demon(1,20,E);b.demon(9,22,W);b.demon(10,17,W);
        b.thing(SectorMap.T_HEALTH,-.5f,25.5f,0);
        // the armoury: a window and a door from the hall
        b.rect(12,19,13,21,1.2f,2.6f,MP,MP,MP);
        b.rect(12,22,13,24,0,3,DP,MP,MP);
        b.rect(13,15,21,26,0,4,DP,CO,MP);
        b.rect(15,17,16,18,1,4,CR,CO,CR);b.rect(16,17,17,18,2,4,CR,CO,CR);b.rect(18,22,19,23,1,4,CR,CO,CR);
        b.lamp(SectorMap.T_LAMP_BLUE,17,25);
        b.demon(19,19,W);b.demon(15,24,S);
        b.thing(SectorMap.T_SHELLS,20,16,0);b.thing(SectorMap.T_SHELLS,14,16,0);b.thing(SectorMap.T_HEALTH,20,25,0);
        // stairs up to the gallery
        b.stairs(2,27,6,31,0,6,4,27,.25f,.667f,DP,MP);
        b.poly(1.5f,6,FT,MP,BR,-2,31,10,31,10,35,8,37,0,37,-2,35);
        b.lava(.8f,6.5f,32,8.5f,32,8.5f,35,6.5f,35);
        b.pillar(-1,32,0,33);
        b.lamp(SectorMap.T_LAMP_WARM,3,35.5f);
        b.demon(1,34,S);b.demon(7,36,S);b.demon(-1,35.5f,E);
        b.thing(SectorMap.T_HEALTH,9,33,0);
        // exit shrine
        SectorMap.Sector ex=b.rect(2,37,6,40,1.5f,4.2f,LT,MP,PI);ex.special=SectorMap.SP_EXIT;
        b.lamp(SectorMap.T_LAMP_WHITE,4,39);
        return b.done();
    }

    /**
     * Map 2. A gatehouse opens onto an octagonal courtyard: an island altar ringed by four lava pools with bridges
     * between them, stairs up to terraces on both sides leading into the side wings, and a north passage to the
     * pillared temple (lava gutters along its walls) and the exit.
     */
    static SectorMap moltenKeep(){
        B b=new B("The Molten Keep");
        // gatehouse and gate passage
        b.poly(0,4,FT,CO,BR,-3,0,3,0,3,6,-3,6);
        b.lamp(SectorMap.T_LAMP_WARM,-2,5);b.lamp(SectorMap.T_LAMP_WARM,2,5);
        b.start(0,1.5f,N);b.thing(SectorMap.T_SHELLS,2,1,0);b.thing(SectorMap.T_HEALTH,-2,1,0);
        b.rect(-1.5f,6,1.5f,9,0,3.2f,DP,MP,MP);
        // courtyard
        b.poly(0,10,FT,MP,BR,-5,9,5,9,11,15,11,25,5,31,-5,31,-11,25,-11,15);
        // island and altar, with four lava pools around it (bridges between them)
        b.poly(.5f,10,MP,MP,PI,-3,17,3,17,3,23,-3,23);
        b.poly(1,10,LT,MP,PI,-1,19,1,19,1,21,-1,21);
        b.lava(-1.2f,1,23,1,27,7,27,7,21,3,21,3,23);
        b.lava(-1.2f,-1,23,-3,23,-3,21,-7,21,-7,27,-1,27);
        b.lava(-1.2f,1,17,3,17,3,19,7,19,7,13,1,13);
        b.lava(-1.2f,-1,17,-1,13,-7,13,-7,19,-3,19,-3,17);
        // terraces with stairs (the east and west bridges lead onto them)
        b.rect(9,15,11,25,1.5f,10,DP,MP,MP);b.rect(-11,15,-9,25,1.5f,10,DP,MP,MP);
        b.stairs(7,19,9,21,0,10,7,20,.25f,.333f,DP,MP);b.stairs(-9,19,-7,21,0,10,-7,20,.25f,.333f,DP,MP);
        b.pillar(-4,10.5f,-3,11.5f);b.pillar(3,10.5f,4,11.5f);b.pillar(-4,28.5f,-3,29.5f);b.pillar(3,28.5f,4,29.5f);
        b.lamp(SectorMap.T_LAMP_RED,0,20);b.lamp(SectorMap.T_LAMP_WARM,0,11);b.lamp(SectorMap.T_LAMP_WARM,0,29.5f);
        b.demon(0,20,S);b.demon(10,23,W);b.demon(-10,17,E);b.demon(-6,11,N);b.demon(6,29,S);b.demon(-8,26,S);
        // east wing (from the east terrace)
        b.rect(11,19,12,21,1.5f,4,DP,MP,MP);
        b.rect(12,16,18,24,1.5f,4.5f,DP,CO,MP);
        b.rect(14,18,15,19,2.5f,4.5f,CR,CO,CR);b.rect(16,21,17,22,2.5f,4.5f,CR,CO,CR);
        b.lamp(SectorMap.T_LAMP_BLUE,17,23);b.demon(16,18,W);b.demon(13,23,S);
        b.thing(SectorMap.T_SHELLS,17.2f,16.8f,0);b.thing(SectorMap.T_SHELLS,13,17,0);
        // west wing (from the west terrace)
        b.rect(-12,19,-11,21,1.5f,4,DP,MP,MP);
        b.rect(-18,16,-12,24,1.5f,4.5f,DP,CO,MP);
        b.rect(-15,18,-14,19,2.5f,4.5f,CR,CO,CR);
        b.lamp(SectorMap.T_LAMP_BLUE,-17,23);b.demon(-16,20,E);b.thing(SectorMap.T_HEALTH,-17,17,0);b.thing(SectorMap.T_SHELLS,-13,23,0);
        // north passage and the temple
        b.rect(-1.5f,31,1.5f,34,0,4,DP,MP,MP);
        b.poly(0,8,FT,CO,BR,-7,36,-5,34,5,34,7,36,7,44,5,46,-5,46,-7,44);
        b.lava(-.8f,-6.5f,37,-5.5f,37,-5.5f,43,-6.5f,43);b.lava(-.8f,5.5f,37,6.5f,37,6.5f,43,5.5f,43);
        b.pillar(-4,37,-3,38);b.pillar(3,37,4,38);b.pillar(-4,42,-3,43);b.pillar(3,42,4,43);
        b.lamp(SectorMap.T_LAMP_RED,-4.5f,40);b.lamp(SectorMap.T_LAMP_RED,4.5f,40);
        b.demon(-2,44,S);b.demon(2,44,S);b.demon(0,39,S);b.demon(-5,45,E);
        b.thing(SectorMap.T_HEALTH,0,35,0);
        SectorMap.Sector ex=b.rect(-2,46,2,49,.5f,4f,LT,MP,PI);ex.special=SectorMap.SP_EXIT;
        b.lamp(SectorMap.T_LAMP_WHITE,0,48);
        return b.done();
    }

    /** A small blank map for a new level in the editor: one room and the player start. */
    static SectorMap blank(){
        B b=new B("New map");b.rect(0,0,10,8,0,4,FT,CO,BR);b.start(2,2,N);b.lamp(SectorMap.T_LAMP_WARM,5,4);return b.done();
    }
}
