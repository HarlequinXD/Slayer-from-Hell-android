package com.harlequin.slayer;

import java.util.ArrayList;

/**
 * The built-in campaign, built sector by sector through the same validated operations the level editor uses
 * (so a map that would be broken can't be built). Pure Java.
 */
final class Maps {
    /** The campaign, in story order (see {@link Story}): Hell, the human station Hell is swallowing, and back. */
    static final String[] CAMPAIGN={"Hell's Gate","The Molten Keep","The Ossuary Road","Threshold Station","The Foundry","The Grafted Halls","The Breach Core","The Last Gate"};
    static final String[] BLURBS={"The gate of hell: the great hall, the armoury, the Pit of Chains",
        "An octagonal keep over lava, the reliquary and the red key, the summit",
        "Bone tunnels, the lake of blood, the charnel nave and the bone organ",
        "The human station: labs, the reactor ring, cryo and the armoury",
        "Where the weapons were made: the furnaces, the crucible, two keys",
        "Flesh grown over steel: the ring, the womb, the choir of mouths",
        "The machine that opened Hell, and the lab where they kept her",
        "Up the road to the court of the Wardens, and the Gate"};
    static int count(){return CAMPAIGN.length;}
    static SectorMap campaign(int i){
        switch(i){case 1:return moltenKeep();case 2:return ossuaryRoad();case 3:return threshold();case 4:return foundry();case 5:return graftedHalls();case 6:return breachCore();case 7:return lastGate();default:return hellsGate();}
    }
    /** A library texture by name. */
    static int L(String name){for(int i=0;i<TexLib.COUNT;i++)if(TexLib.name(i).equals(name))return Tex.FIRST_LIB+i;throw new IllegalArgumentException(name);}

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
        /** A pillar in any material, up to its room's ceiling. */
        void pillar(float x0,float y0,float x1,float y1,int tex){SectorMap.Sector s=rect(x0,y0,x1,y1,0,0,tex,tex,tex);int i=m.sectors.indexOf(s);
            m.index();int host=m.parent[i];SectorMap.Sector h=host>=0?m.sectors.get(host):null;s.floor=s.ceil=h!=null?h.ceil:4;m.index();}
        /** A sunken pool of a material (magma-family ones burn) inside its room. */
        SectorMap.Sector pool(int tex,float floor,float... xy){SectorMap.Sector s=poly(floor,0,tex,Tex.CONCRETE,tex,xy);int host=m.parent[m.sectors.indexOf(s)];
            s.ceil=host>=0?m.sectors.get(host).ceil:6;m.index();return s;}
        /** A raised block (a console, a crate) of `h` metres in its room. */
        void block(float x0,float y0,float x1,float y1,float h,int top,int side){SectorMap.Sector s=rect(x0,y0,x1,y1,h,0,top,top,side);int host=m.parent[m.sectors.indexOf(s)];
            s.ceil=host>=0?m.sectors.get(host).ceil:4;s.floor=(host>=0?m.sectors.get(host).floor:0)+h;m.index();}
        /** A door across a passage: it rises into its lintel (top) when opened; key -1 (none), 0 red, 1 blue, 2 yellow. */
        SectorMap.Sector door(float x0,float y0,float x1,float y1,float floor,float top,int key,int slab,int frame){
            SectorMap.Sector s=rect(x0,y0,x1,y1,floor,top,frame,slab,frame);s.special=key<0?SectorMap.SP_DOOR:SectorMap.SP_DOOR_RED+key;m.index();return s;}
        /** A key lying on the floor (0 red, 1 blue, 2 yellow). */
        void key(int k,float x,float y){thing(SectorMap.T_KEY_RED+k,x,y,0);}
        void thing(int type,float x,float y,float angle){m.things.add(new SectorMap.Thing(type,x,y,angle));}
        void demon(float x,float y,float angle){thing(SectorMap.T_DEMON,x,y,angle);}
        /** A demon on some skills only: E (easy), M (Hurt Me Plenty), H (Ultra-Violence/Nightmare), as in Doom's flags. */
        void demon(float x,float y,float angle,int skills){thing(SectorMap.T_DEMON,x,y,angle);m.things.get(m.things.size()-1).skills=skills;}
        void lamp(int type,float x,float y){thing(type,x,y,0);}
        void start(float x,float y,float facing){m.playerX=x;m.playerY=y;m.facing=facing;}
        SectorMap done(){m.index();String v=ed.validateMap();if(!v.equals("Geometry valid"))throw new IllegalStateException(m.name+": "+v);return m;}
    }
    static final float N=(float)Math.PI/2,S=-N,E=0,W=(float)Math.PI;
    static final int ALL=Skill.F_ALL,MH=Skill.F_NORMAL|Skill.F_HARD,H=Skill.F_HARD;
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
        // the great hall: the altar's guard; more on harder skills, spread to the far corners
        b.demon(4,20,S,ALL);b.demon(10,24.5f,W,MH);b.demon(-2.5f,25.5f,E,H);
        b.thing(SectorMap.T_HEALTH,-.5f,25.5f,0);
        // the armoury: a window and a door from the hall
        b.rect(12,19,13,21,1.2f,2.6f,MP,MP,MP);
        b.rect(12,22,13,24,0,3,DP,MP,MP);
        b.rect(13,15,21,26,0,4,DP,CO,MP);
        b.rect(15,17,16,18,1,4,CR,CO,CR);b.rect(16,17,17,18,2,4,CR,CO,CR);b.rect(18,22,19,23,1,4,CR,CO,CR);
        b.lamp(SectorMap.T_LAMP_BLUE,17,25);
        b.demon(19,19,W,ALL);b.demon(15,24.5f,S,MH);
        b.thing(SectorMap.T_SHELLS,20,16,0);b.thing(SectorMap.T_SHELLS,14,16,0);b.thing(SectorMap.T_HEALTH,20,25,0);
        // stairs up to the gallery
        b.stairs(2,27,6,31,0,6,4,27,.25f,.667f,DP,MP);
        b.poly(1.5f,6,FT,MP,BR,-2,31,10,31,10,35,8,37,0,37,-2,35);
        b.lava(.8f,6.5f,32,8.5f,32,8.5f,35,6.5f,35);
        b.pillar(-1,32,0,33);
        b.lamp(SectorMap.T_LAMP_WARM,3,35.5f);
        b.demon(7,36,S,ALL);b.demon(1,34,S,MH);b.demon(-1,35.5f,E,H);
        b.thing(SectorMap.T_HEALTH,9,33,0);
        // the shrine passage and the first door
        b.rect(2,37,6,40,1.5f,4.2f,DP,MP,PI);
        b.door(2,40,6,41,1.5f,3.8f,-1,MP,PI);
        b.rect(2,41,6,46,1.5f,4,DP,MP,BR);b.lamp(WARM,4,43.5f);
        // the Pit of Chains: an octagon around a sunken pit of lava, four pillars at its corners
        b.poly(1.5f,9,FT,MP,BR,-4,46,12,46,16,50,16,62,12,66,-4,66,-8,62,-8,50);
        b.lava(.7f,1,52,7,52,7,60,1,60);
        b.pillar(-1,51,0,52,PI);b.pillar(8,51,9,52,PI);b.pillar(-1,60,0,61,PI);b.pillar(8,60,9,61,PI);
        b.lamp(RED,4,56);b.lamp(WARM,-6,56);b.lamp(WARM,14,56);
        b.demon(-6,63,E,ALL);b.demon(14,63,W,ALL);b.demon(4,64.5f,S,ALL);b.demon(-6,49,N,MH);b.demon(14,49,W,H);
        b.thing(HP,-7,56,0);b.thing(CELLS,11,48,0);
        // the chain hall, east through a door: crates, cells, an ambush
        b.door(16,54,17,58,1.5f,3.8f,-1,MP,PI);
        b.rect(17,52,30,60,1.5f,5,DP,CO,MP);
        b.block(20,53,21,54,1,CR,CR);b.block(24,57,25,58,1,CR,CR);b.block(27,53,28,55,1,CR,CR);
        b.lamp(BLUE,28,58);b.demon(28,56,W,ALL);b.demon(22,59,S,MH);b.demon(29,53,W,H);
        b.thing(CELLS,29,59,0);b.thing(HP,18,53,0);b.thing(CELLS,18,59,0);
        // the stair to the shrine of the gate, and the exit
        b.stairs(2,66,6,70,1.5f,9,4,66,.25f,.667f,DP,MP);
        b.rect(0,70,8,76,3,8,FT,MP,BR);b.pillar(0.5f,74.5f,1.5f,75.5f,PI);b.pillar(6.5f,74.5f,7.5f,75.5f,PI);
        b.lamp(WARM,1,71);b.lamp(WARM,7,71);b.demon(4,75,S,MH);b.demon(1.5f,73,E,H);
        SectorMap.Sector ex=b.rect(2,76,6,79,3,6,LT,MP,PI);ex.special=SectorMap.SP_EXIT;
        b.lamp(SectorMap.T_LAMP_WHITE,4,78);
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
        // the courtyard: the altar's guard; others far from the gate on harder skills (none in sight of the start)
        b.demon(0,20,S,ALL);b.demon(6,29,S,MH);b.demon(10,23,W,H);b.demon(-8,26,S,H);
        // east wing (from the east terrace)
        b.rect(11,19,12,21,1.5f,4,DP,MP,MP);
        b.rect(12,16,18,24,1.5f,4.5f,DP,CO,MP);
        b.rect(14,18,15,19,2.5f,4.5f,CR,CO,CR);b.rect(16,21,17,22,2.5f,4.5f,CR,CO,CR);
        b.lamp(SectorMap.T_LAMP_BLUE,17,23);b.demon(16,18,W,ALL);b.demon(13,23,S,H);
        b.thing(SectorMap.T_SHELLS,17.2f,16.8f,0);b.thing(SectorMap.T_SHELLS,13,17,0);
        // west wing (from the west terrace)
        b.rect(-12,19,-11,21,1.5f,4,DP,MP,MP);
        b.rect(-18,16,-12,24,1.5f,4.5f,DP,CO,MP);
        b.rect(-15,18,-14,19,2.5f,4.5f,CR,CO,CR);
        b.lamp(SectorMap.T_LAMP_BLUE,-17,23);b.demon(-16,20,E,MH);b.thing(SectorMap.T_HEALTH,-17,17,0);b.thing(SectorMap.T_SHELLS,-13,23,0);
        // north passage and the temple
        b.rect(-1.5f,31,1.5f,34,0,4,DP,MP,MP);
        b.poly(0,8,FT,CO,BR,-7,36,-5,34,5,34,7,36,7,44,5,46,-5,46,-7,44);
        b.lava(-.8f,-6.5f,37,-5.5f,37,-5.5f,43,-6.5f,43);b.lava(-.8f,5.5f,37,6.5f,37,6.5f,43,5.5f,43);
        b.pillar(-4,37,-3,38);b.pillar(3,37,4,38);b.pillar(-4,42,-3,43);b.pillar(3,42,4,43);
        b.lamp(SectorMap.T_LAMP_RED,-4.5f,40);b.lamp(SectorMap.T_LAMP_RED,4.5f,40);
        b.demon(0,44.5f,S,ALL);b.demon(-2,44,S,MH);b.demon(0,39,S,MH);b.demon(2,44,S,H);
        b.thing(SectorMap.T_HEALTH,0,35,0);
        // beyond the temple: the brazier hall
        int iron=L("Hell iron"),spiked=L("Spiked iron"),obs=L("Obsidian");
        b.rect(-2,46,2,49,.5f,4f,DP,MP,PI);
        b.door(-2,49,2,50,.5f,3.6f,-1,iron,obs);
        b.rect(-10,50,10,64,.5f,8,FT,MP,BR);
        b.lava(-.3f,-9.5f,51,-8.5f,51,-8.5f,63,-9.5f,63);b.lava(-.3f,8.5f,51,9.5f,51,9.5f,63,8.5f,63);
        b.pillar(-6,53,-5,54,PI);b.pillar(5,53,6,54,PI);b.pillar(-6,60,-5,61,PI);b.pillar(5,60,6,61,PI);
        b.lamp(RED,-7,57);b.lamp(RED,7,57);
        b.demon(-4,62,S,ALL);b.demon(4,62,S,ALL);b.demon(0,58,S,MH);b.demon(7,52,W,H);
        b.thing(HP,7,63,0);
        // the reliquary, west: the red key on its dais
        b.rect(-14,55,-10,59,.5f,4,DP,MP,obs);
        b.rect(-26,50,-14,64,.5f,6,L("Hellstone cobbles"),obs,L("Burnt masonry"));
        b.block(-22,55.5f,-18,58.5f,.5f,L("Sigil tiles"),obs);b.key(0,-20,57);
        b.pillar(-25,51,-24,52,obs);b.pillar(-25,62,-24,63,obs);
        b.lamp(RED,-20,62);b.lamp(WARM,-15,51);
        b.demon(-24,57,E,ALL);b.demon(-17,62,S,ALL);b.demon(-17,52,N,MH);b.demon(-24,62,E,H);b.thing(CELLS,-25,63,0);b.thing(HP,-25,53.5f,0);
        // the red door, and the summit of the keep
        b.door(-2,64,2,65,.5f,4,0,spiked,obs);
        b.rect(-2,65,2,70,.5f,4,DP,MP,BR);
        b.poly(.5f,10,FT,MP,BR,-8,70,8,70,12,74,12,84,8,88,-8,88,-12,84,-12,74);
        b.lava(-.3f,-11,75,-8,75,-8,78,-11,78);b.lava(-.3f,8,75,11,75,11,78,8,78);b.lava(-.3f,-11,80,-8,80,-8,83,-11,83);b.lava(-.3f,8,80,11,80,11,83,8,83);
        b.block(-2,77,2,81,.5f,LT,PI);
        b.lamp(RED,0,79);b.lamp(WARM,-9,86);b.lamp(WARM,9,86);
        b.demon(0,86,S,ALL);b.demon(-9,73,E,ALL);b.demon(9,73,W,ALL);b.demon(-6,85,S,MH);b.demon(6,85,S,MH);b.demon(0,72,S,H);
        b.thing(HP,-11,79,0);b.thing(CELLS,11,79,0);
        SectorMap.Sector ex=b.rect(-2,88,2,91,1,4f,LT,MP,PI);ex.special=SectorMap.SP_EXIT;
        b.lamp(SectorMap.T_LAMP_WHITE,0,90);
        return b.done();
    }

    static final int WARM=SectorMap.T_LAMP_WARM,RED=SectorMap.T_LAMP_RED,BLUE=SectorMap.T_LAMP_BLUE,WHITE=SectorMap.T_LAMP_WHITE,HP=SectorMap.T_HEALTH,CELLS=SectorMap.T_SHELLS;

    /** Map 3. Bone tunnels north to a bend chamber, west into the ossuary hall around a lake of blood, up the stair to the crypt, and east through the root tunnel to the rune shrine. */
    static SectorMap ossuaryRoad(){
        B b=new B("The Ossuary Road");int bone=L("Bone-white cobbles"),oss=L("Ossuary wall"),bas=L("Ashen basalt"),pile=L("Bone pile"),blood=L("Blood-soaked stone"),charred=L("Charred bones"),roots=L("Hell roots"),ash=L("Ash floor"),sig=L("Sigil tiles"),soul=L("Soul runes");
        b.rect(-3,0,3,6,0,4,bone,bas,oss);b.lamp(WARM,-2,5);b.lamp(WARM,2,5);b.start(0,1.5f,N);b.thing(CELLS,2,1,0);
        b.rect(-1.5f,6,1.5f,14,0,3.2f,pile,bas,pile);
        // the bend chamber
        b.rect(-6,14,4,20,0,5,bone,bas,oss);b.pillar(-1,16.5f,0,17.5f,pile);b.lamp(RED,3,19);
        b.demon(-5,19,E,ALL);b.demon(3,15,W,MH);b.thing(HP,-5,15,0);
        // the ossuary hall around a lake of blood
        b.rect(-14,16,-6,18,0,3.5f,pile,bas,pile);
        b.rect(-26,8,-14,30,0,8,blood,bas,oss);
        b.pool(L("Blood lake"),-.6f,-23,13,-17,13,-17,25,-23,25);
        b.pillar(-25,9,-24,10,pile);b.pillar(-16,9,-15,10,pile);b.pillar(-25,28,-24,29,pile);b.pillar(-16,28,-15,29,pile);
        b.lamp(RED,-15,14);b.lamp(WARM,-25,20);
        b.demon(-24.5f,26,E,ALL);b.demon(-15.5f,24,W,ALL);b.demon(-20,10,N,MH);b.demon(-15.5f,11,W,H);
        b.thing(CELLS,-25,11,0);b.thing(HP,-15,27,0);
        // the stair up to the crypt
        b.stairs(-22,30,-18,34,0,8,-20,30,.25f,.667f,charred,oss);
        b.rect(-24,34,-16,42,1.5f,5.5f,charred,bas,oss);b.lamp(WARM,-20,41);
        b.demon(-23,40,E,ALL);b.demon(-17,41,S,H);b.thing(HP,-23,35,0);
        // the root tunnel and the shrine
        b.rect(-16,38,-8,40,1.5f,4.5f,ash,bas,roots);
        b.rect(-8,34,4,46,1.5f,6,ash,bas,roots);
        b.pillar(-5,37,-4,38,roots);b.pillar(0,42,1,43,roots);
        b.lamp(WARM,-6,45);
        b.demon(2,44,W,ALL);b.demon(-6,35,N,MH);b.demon(3,35,W,H);b.thing(CELLS,-7,44,0);
        // north: the charnel nave
        int iron=L("Hell iron"),spiked=L("Spiked iron");
        b.rect(-2,46,2,49,1.5f,4.5f,pile,bas,oss);
        b.door(-2,49,2,50,1.5f,4,-1,iron,bas);
        b.rect(-2,50,2,54,1.5f,4,bone,bas,pile);
        b.rect(-10,54,10,76,1.5f,9,bone,bas,oss);
        b.pool(L("Blood lake"),.9f,-2,57,2,57,2,73,-2,73);
        for(int k=0;k<4;k++){float y=57+k*5;b.pillar(-6,y,-5,y+1,pile);b.pillar(5,y,6,y+1,pile);}
        b.lamp(RED,0,56);b.lamp(RED,0,74);b.lamp(WARM,-8,65);b.lamp(WARM,8,65);
        b.demon(-8,74,E,ALL);b.demon(8,74,W,ALL);b.demon(-4,70,S,ALL);b.demon(4,60,S,MH);b.demon(-8,56,N,H);b.demon(8,56,N,H);
        b.thing(HP,-9,55,0);b.thing(CELLS,9,55,0);
        // the bone organ, east: the blue key in its pipes
        b.door(10,62,11,66,1.5f,4,-1,iron,bas);
        b.rect(11,58,23,70,1.5f,7,charred,bas,pile);
        b.block(15,62,19,66,.5f,L("Pentagram floor"),pile);b.key(1,17,64);
        b.pillar(12,59,13,60,pile);b.pillar(21,59,22,60,pile);b.pillar(12,68,13,69,pile);b.pillar(21,68,22,69,pile);
        b.lamp(BLUE,17,68.5f);b.lamp(RED,17,59.5f);
        b.demon(22,64,W,ALL);b.demon(17,69,S,ALL);b.demon(14,59,N,MH);b.demon(22,69,W,H);b.thing(HP,22,59,0);b.thing(CELLS,12,64,0);
        // the blue door and the sigil shrine
        b.door(-2,76,2,77,1.5f,4,1,spiked,bas);
        b.rect(-2,77,2,81,1.5f,4,bone,bas,pile);
        b.poly(1.5f,8,sig,bas,soul,-6,81,6,81,9,84,9,90,6,93,-6,93,-9,90,-9,84);
        b.lamp(RED,-7,87);b.lamp(RED,7,87);b.demon(-6,91,E,ALL);b.demon(6,91,W,MH);b.demon(0,90,S,H);b.thing(HP,0,83,0);
        SectorMap.Sector ex=b.rect(-2,93,2,96,2,5,sig,bas,soul);ex.special=SectorMap.SP_EXIT;b.lamp(WHITE,0,95);
        return b.done();
    }

    /** Map 4. Through the scorched airlock into the atrium, the medical lab east, the control room west, and north through the hangar to the lift. */
    static SectorMap threshold(){
        B b=new B("Threshold Station");int lab=L("White lab panels"),tech=L("Tech floor"),strips=L("Light strips"),med=L("Med-bay floor"),cmd=L("Command deck"),server=L("Server wall"),
            term=L("Terminal bank"),hull=L("Ribbed hull"),tread=L("Tread plate"),haz=L("Hazard stripes"),walk=L("Worn walkway"),corr=L("Corrugated steel"),titan=L("Titan plating");
        // the breach: hell bleeding into the airlock
        b.rect(-4,0,4,6,0,4,L("Ember cobbles"),hull,L("Rusted bulkhead"));b.lamp(RED,-3,5);b.lamp(WARM,3,5);b.start(0,1.5f,N);b.thing(CELLS,3,1,0);
        b.rect(-1.5f,6,1.5f,12,0,3.2f,tread,hull,haz);
        // the atrium
        b.rect(-10,12,10,26,0,6,tech,strips,lab);b.pillar(-1,18,1,20,L("Lit window panels"));
        b.lamp(WHITE,-8,24);b.lamp(WHITE,8,24);
        b.demon(-8,24.5f,S,ALL);b.demon(8,14,W,MH);b.thing(HP,9,25,0);
        // the medical lab
        b.rect(10,17,14,20,0,3.2f,tread,hull,lab);
        b.rect(14,12,24,26,0,4,med,strips,lab);b.block(16,14,18,15,1,term,lab);b.block(20,22,22,23,1,term,lab);
        b.demon(22,15,W,ALL);b.demon(17,24,S,H);b.thing(CELLS,23,25,0);b.lamp(BLUE,23,13);
        // the control room
        b.rect(-14,17,-10,20,0,3.2f,tread,hull,lab);
        b.rect(-24,12,-14,26,0,4,cmd,hull,server);b.block(-22,16,-21,22,1,term,server);b.block(-17,14,-16,15,1,term,server);
        b.demon(-19,24,E,ALL);b.demon(-23,13,N,MH);b.thing(HP,-15,25,0);b.lamp(BLUE,-15,13);
        // the hangar and the lift
        b.rect(-1.5f,26,1.5f,30,0,3.5f,tread,hull,haz);
        b.rect(-12,30,12,44,0,9,walk,titan,corr);
        b.block(-9,33,-7,35,1,Tex.CRATE,Tex.CRATE);b.block(6,37,8,39,1,Tex.CRATE,Tex.CRATE);b.block(-4,39,-2,41,1,Tex.CRATE,Tex.CRATE);
        b.lamp(WARM,-11,43);b.lamp(WARM,11,43);
        b.demon(10,42,W,ALL);b.demon(-10,42,E,ALL);b.demon(0,36,S,MH);b.demon(9,31,N,H);b.thing(CELLS,-11,31,0);b.thing(HP,11,31,0);
        // the lift was dead: north, through the reactor ring
        int blast=L("Hazard blast door"),hexb=L("Black hex armour"),hexr=L("Hex reactor floor"),shield=L("Hex energy shield");
        b.rect(-2,44,2,47,.5f,4,tread,hull,haz);
        b.door(-2,47,2,48,.5f,3.5f,-1,blast,hexb);
        b.rect(-2,48,2,52,.5f,3.5f,tread,hull,haz);
        b.rect(-14,52,14,72,.5f,10,hexr,titan,corr);
        b.pillar(-3,59,3,65,shield);
        b.block(-12,54,-10,56,1,Tex.CRATE,Tex.CRATE);b.block(10,68,12,70,1,Tex.CRATE,Tex.CRATE);b.block(9,54,10,57,1.2f,term,lab);
        b.lamp(BLUE,0,57.5f);b.lamp(BLUE,0,66.5f);b.lamp(WHITE,-13,71);b.lamp(WHITE,13,53);
        b.demon(-12,70,E,ALL);b.demon(12,62,W,ALL);b.demon(0,70,S,MH);b.demon(-8,58,E,MH);b.demon(8,66,W,H);
        b.thing(HP,13,71,0);b.thing(CELLS,-13,53,0);
        // the cryo bay, west: the red key in the warden's empty pod
        b.door(-15,60,-14,64,.5f,3.5f,-1,blast,hexb);
        b.rect(-27,56,-15,68,.5f,4.5f,med,strips,lab);
        for(int k=0;k<3;k++){float y=57.5f+k*4;b.block(-26.5f,y,-25,y+1.5f,1.5f,L("Tinted bulkhead glass"),lab);}
        b.block(-21,61,-19,63,.4f,hexr,lab);b.key(0,-20,62);
        b.lamp(BLUE,-20,67);b.demon(-16,67,S,ALL);b.demon(-24,62,E,ALL);b.demon(-17,57,N,H);b.thing(HP,-16,57,0);
        // the armoury, east, behind the red door: the blue key
        b.door(14,60,15,64,.5f,3.5f,0,blast,hexb);
        b.rect(15,56,27,68,.5f,4.5f,L("Olive drab armour"),hull,L("Heavy armour plates"));
        b.block(17,57,25,58,1.2f,L("Heavy armour plates"),L("Heavy armour plates"));b.block(17,66,25,67,1.2f,L("Heavy armour plates"),L("Heavy armour plates"));
        b.block(20,61,22,63,.4f,hexr,lab);b.key(1,21,62);
        b.lamp(RED,26,62);b.demon(26,66,W,ALL);b.demon(26,58,W,ALL);b.demon(16,62,E,MH);b.thing(CELLS,26,62,0);b.thing(HP,16,57,0);
        // the blue door and the maintenance lift
        b.door(-2,72,2,73,.5f,3.5f,1,blast,hexb);
        b.rect(-2,73,2,78,.5f,3.5f,tread,hull,haz);
        b.rect(-7,78,7,88,.5f,12,walk,titan,corr);b.lamp(WHITE,-6,87);b.lamp(WHITE,6,87);
        b.demon(-5,86,S,ALL);b.demon(5,86,S,MH);b.demon(0,84,S,H);b.thing(HP,-6,79,0);b.thing(CELLS,6,79,0);
        SectorMap.Sector ex=b.rect(-2,88,2,91,.5f,4,hexr,hull,blast);ex.special=SectorMap.SP_EXIT;b.lamp(WHITE,0,90);
        return b.done();
    }

    /** Map 5. The loading dock, the furnace hall between two pools of molten metal, the pipe gallery, the stair to the vault where Subject W was kept, and the exit. */
    static SectorMap foundry(){
        B b=new B("The Foundry");int grate=L("Floor grate"),vents=L("Heat vents"),rib=L("Ribbed hull"),pipes=L("Pipe bundle"),cpipes=L("Corroded pipework"),rgrate=L("Rusted grate"),
            corr=L("Corrugated steel"),armour=L("Heavy armour plates"),hexb=L("Black hex armour"),tread=L("Tread plate"),haz=L("Hazard stripes");
        b.rect(-4,0,4,6,0,4,tread,pipes,corr);b.lamp(WARM,-3,5);b.lamp(WARM,3,5);b.start(0,1.5f,N);b.thing(CELLS,-3,1,0);
        b.rect(-1.5f,6,1.5f,10,0,3.2f,tread,rib,haz);
        // the furnace hall: a walkway between two pools of molten metal
        b.rect(-14,10,14,30,0,10,grate,rib,vents);
        int magma=L("Magma crust");b.pool(magma,-.8f,-12,14,-4,14,-4,26,-12,26);b.pool(magma,-.8f,4,14,12,14,12,26,4,26);
        b.pillar(-1,13,1,14,pipes);b.pillar(-1,26,1,27,pipes);
        b.lamp(WARM,-13,29);b.lamp(WARM,13,29);
        b.demon(-13,28,E,ALL);b.demon(13,12,W,ALL);b.demon(2,28,S,MH);b.demon(-13,11,N,H);b.thing(HP,13,29,0);b.thing(CELLS,-13,29,0);
        // the pipe gallery
        b.rect(-1.5f,30,1.5f,34,0,3.5f,rgrate,rib,haz);
        b.rect(-10,34,10,40,0,4,rgrate,pipes,cpipes);
        b.demon(-9,39,E,ALL);b.demon(8,35,W,H);b.thing(HP,-9,35,0);b.lamp(BLUE,-9,37);
        // the stair to the vault
        b.stairs(10,35,14,39,0,4,10,37,.25f,.667f,tread,cpipes);
        b.rect(14,32,24,42,1.5f,6,hexb,armour,armour);b.pillar(18,36,20,38,L("Tinted bulkhead glass"));
        b.lamp(RED,23,33);b.demon(23,41,S,ALL);b.demon(15,33,N,MH);b.demon(23,33,W,H);b.thing(CELLS,15,41,0);
        // up from the vault: the crucible
        int blast=L("Hazard blast door");
        b.rect(17,42,21,45,1.5f,4,tread,rib,haz);
        b.door(17,45,21,46,1.5f,3.6f,-1,blast,hexb);
        b.rect(17,46,21,50,1.5f,3.6f,tread,rib,haz);
        b.rect(5,50,33,72,1.5f,12,grate,rib,vents);
        b.pool(magma,.7f,13,56,25,56,25,66,13,66);
        b.pillar(9,54,10,55,pipes);b.pillar(28,54,29,55,pipes);b.pillar(9,67,10,68,pipes);b.pillar(28,67,29,68,pipes);
        b.lamp(WARM,6,71);b.lamp(WARM,32,71);b.lamp(RED,19,61);
        b.demon(6,70,E,ALL);b.demon(32,52,W,ALL);b.demon(19,70,S,ALL);b.demon(31,70,W,MH);b.demon(7,52,N,H);
        b.thing(HP,32,71,0);b.thing(CELLS,6,51,0);
        // the casting pits, west: the yellow key
        b.door(4,59,5,63,1.5f,3.6f,-1,blast,hexb);
        b.rect(-8,54,4,68,1.5f,6,rgrate,pipes,cpipes);
        b.pool(magma,.9f,-7,56,-5,56,-5,66,-7,66);b.pool(magma,.9f,1,56,3,56,3,66,1,66);
        b.block(-3,60,-1,62,.5f,armour,armour);b.key(2,-2,61);
        b.lamp(RED,-2,67);b.demon(-2,66,S,ALL);b.demon(-2,55,N,MH);b.demon(-4,64,E,H);b.thing(CELLS,-4,55,0);
        // the ordnance store, east, behind the yellow door: the red key
        b.door(33,59,34,63,1.5f,3.6f,2,blast,hexb);
        b.rect(34,54,46,68,1.5f,5,L("Olive drab armour"),armour,armour);
        b.block(36,56,38,58,1,Tex.CRATE,Tex.CRATE);b.block(42,63,44,65,1,Tex.CRATE,Tex.CRATE);b.block(41,57,43,58,1.6f,Tex.CRATE,Tex.CRATE);
        b.block(39,60,41,62,.5f,armour,armour);b.key(0,40,61);
        b.lamp(BLUE,45,67);b.lamp(BLUE,35,55);b.demon(45,61,W,ALL);b.demon(36,66,S,ALL);b.demon(45,55,N,MH);b.thing(HP,45,67,0);b.thing(CELLS,35,67,0);
        // the red door and the proving range
        b.door(17,72,21,73,1.5f,3.6f,0,blast,hexb);
        b.rect(17,73,21,77,1.5f,3.6f,tread,rib,haz);
        b.rect(11,77,27,87,1.5f,7,hexb,armour,armour);
        b.block(13,80,15,82,1,Tex.CRATE,Tex.CRATE);b.block(23,82,25,84,1,Tex.CRATE,Tex.CRATE);
        b.lamp(WHITE,12,86);b.lamp(WHITE,26,86);b.demon(19,85,S,ALL);b.demon(12,85,E,MH);b.demon(26,85,W,MH);b.demon(19,80,S,H);b.thing(HP,26,78,0);
        SectorMap.Sector ex=b.rect(17,87,21,90,2,5,L("Hex energy shield"),rib,blast);ex.special=SectorMap.SP_EXIT;b.lamp(WHITE,19,89);
        return b.done();
    }

    /** Map 6. Flesh over the station: the ring hall around a pillar of living flesh, the server sanctum east, the bile pit west, and the rune shaft north. */
    static SectorMap graftedHalls(){
        B b=new B("The Grafted Halls");int med=L("Med-bay floor"),lab=L("White lab panels"),raw=L("Raw flesh"),sinew=L("Sinew"),sac=L("Sacrificial floor"),flayed=L("Flayed muscle"),
            server=L("Server wall"),cmd=L("Command deck"),rot=L("Rotting meat"),gore=L("Gore cobbles"),pent=L("Pentagram floor"),bruns=L("Blood runes"),hull=L("Hull panels");
        b.rect(-3,0,3,6,0,4,med,lab,raw);b.lamp(WHITE,-2,5);b.lamp(RED,2,5);b.start(0,1.5f,N);b.thing(HP,2,1,0);
        b.rect(-1.5f,6,1.5f,12,0,3.2f,med,lab,sinew);
        // the ring hall around a pillar of living flesh
        b.rect(-14,12,14,36,0,6,sac,hull,flayed);b.pillar(-4,20,4,28,L("Pulsing flesh wall"));
        b.lamp(RED,-13,13);b.lamp(RED,13,35);
        b.demon(-12,33,E,ALL);b.demon(12,15,W,ALL);b.demon(0,34,S,MH);b.demon(-12,18,N,H);b.thing(CELLS,13,13,0);b.thing(HP,-13,35,0);
        // the server sanctum
        b.rect(14,22,18,26,0,3.5f,cmd,hull,server);
        b.rect(18,16,28,32,0,4.5f,L("Sigil tiles"),hull,server);b.block(20,18,21,30,1,L("Terminal bank"),server);b.block(25,18,26,30,1,L("Terminal bank"),server);
        b.demon(23,31,S,ALL);b.demon(27,17,N,H);b.thing(CELLS,27,31,0);b.lamp(BLUE,23,17);
        // the bile pit
        b.rect(-18,22,-14,26,0,3.5f,gore,hull,rot);
        b.rect(-28,16,-18,32,0,5,gore,hull,rot);b.pool(L("Bile pit"),-.6f,-25,19,-21,19,-21,29,-25,29);
        b.demon(-27,31,E,ALL);b.demon(-19,17,W,MH);b.thing(HP,-27,17,0);
        // the rune shaft and the exit
        b.rect(-1.5f,36,1.5f,40,0,3.5f,sac,hull,flayed);
        b.rect(-6,40,6,48,0,8,pent,hull,bruns);b.demon(-5,47,E,ALL);b.demon(5,47,W,H);
        // north: the womb
        int blast=L("Hazard blast door"),tend=L("Tendon wall"),spiked=L("Spiked iron");
        b.rect(-2,48,2,51,0,4,sac,hull,flayed);
        b.door(-2,51,2,52,0,3.6f,-1,blast,tend);
        b.rect(-2,52,2,56,0,3.6f,gore,hull,sinew);
        b.poly(0,9,raw,hull,L("Pulsing flesh wall"),-8,56,8,56,14,62,14,74,8,80,-8,80,-14,74,-14,62);
        b.pool(L("Bile pit"),-.6f,-3,63,3,63,3,73,-3,73);
        b.pillar(-8,62,-7,63,flayed);b.pillar(7,62,8,63,flayed);b.pillar(-8,73,-7,74,flayed);b.pillar(7,73,8,74,flayed);
        b.lamp(RED,0,60);b.lamp(RED,0,77);b.lamp(WARM,-12,68);b.lamp(WARM,12,68);
        b.demon(-6,78,E,ALL);b.demon(6,78,W,ALL);b.demon(-12,64,E,MH);b.demon(12,72,W,MH);b.demon(0,79,S,H);
        b.thing(HP,13,63,0);b.thing(CELLS,-13,63,0);
        // the nerve archive, east: the blue key
        b.door(14,66,15,70,0,3.6f,-1,blast,tend);
        b.rect(15,62,27,74,0,4.5f,cmd,hull,server);
        b.block(17,64,18,72,1,L("Terminal bank"),server);b.block(24,64,25,72,1,L("Terminal bank"),server);
        b.block(20,67,22,69,.4f,L("Sigil tiles"),server);b.key(1,21,68);
        b.lamp(BLUE,21,73);b.demon(26,73,S,ALL);b.demon(21,63,N,ALL);b.demon(26,63,W,H);b.thing(CELLS,16,73,0);
        // the choir of mouths, west, behind the blue door: the yellow key
        b.door(-15,66,-14,70,0,3.6f,1,spiked,tend);
        b.rect(-27,62,-15,74,0,7,pent,hull,L("Glowing tendrils"));
        b.pillar(-26,63,-25,64,tend);b.pillar(-17,63,-16,64,tend);b.pillar(-26,72,-25,73,tend);b.pillar(-17,72,-16,73,tend);
        b.block(-22,67,-20,69,.5f,L("Blood runes"),tend);b.key(2,-21,68);
        b.lamp(RED,-21,72.5f);b.demon(-26,68,E,ALL);b.demon(-21,73,S,ALL);b.demon(-21,63,N,MH);b.demon(-16,68,W,H);b.thing(HP,-26,73,0);
        // the yellow door and the heart
        b.door(-2,80,2,81,0,3.6f,2,spiked,tend);
        b.rect(-2,81,2,86,0,3.6f,gore,hull,sinew);
        b.rect(-8,86,8,96,0,10,raw,hull,L("Pulsing flesh wall"));b.pillar(-1,89,1,91,L("Pulsing flesh wall"));
        b.lamp(RED,-7,95);b.lamp(RED,7,95);b.demon(-6,94,E,ALL);b.demon(6,94,W,ALL);b.demon(0,94,S,MH);b.demon(-6,88,N,H);b.thing(HP,7,87,0);
        SectorMap.Sector ex=b.rect(-2,96,2,99,.5f,4,L("Hex energy shield"),hull,bruns);ex.special=SectorMap.SP_EXIT;b.lamp(WHITE,0,98);
        return b.done();
    }

    /** Map 7. Across the bridge into the core chamber: the Threshold on its island in a pit of blue fire, with bridges between the pools, observation wings east and west, and the way out north. */
    static SectorMap breachCore(){
        B b=new B("The Breach Core");int tech=L("Tech floor"),dcore=L("Data core"),hexb=L("Black hex armour"),hexr=L("Hex reactor floor"),obs=L("Observation glass"),hull=L("Hull panels"),titan=L("Titan plating"),fire=L("Hellfire pool");
        b.rect(-3,0,3,6,0,4,tech,hull,dcore);b.lamp(BLUE,-2,5);b.lamp(BLUE,2,5);b.start(0,1.5f,N);b.thing(CELLS,2,1,0);b.thing(HP,-2,1,0);
        b.rect(-1.5f,6,1.5f,10,0,3.2f,L("Tread plate"),hull,L("Hazard stripes"));
        // the chamber, the island, four pools of blue fire with bridges between them
        b.poly(0,14,hexb,titan,dcore,-8,10,8,10,14,16,14,32,8,38,-8,38,-14,32,-14,16);
        b.poly(.5f,14,hexr,titan,hull,-3,21,3,21,3,27,-3,27);b.pillar(-1,23,1,25,L("Hex energy shield"));
        b.pool(fire,-1.2f,1,27,1,31,7,31,7,25,3,25,3,27);
        b.pool(fire,-1.2f,-1,27,-3,27,-3,25,-7,25,-7,31,-1,31);
        b.pool(fire,-1.2f,1,21,3,21,3,23,7,23,7,17,1,17);
        b.pool(fire,-1.2f,-1,21,-1,17,-7,17,-7,23,-3,23,-3,21);
        b.lamp(WHITE,-12,17);b.lamp(WHITE,12,17);b.lamp(WHITE,0,36);
        b.demon(0,26,S,ALL);b.demon(-12,31,E,ALL);b.demon(12,31,W,ALL);b.demon(-9,36,S,MH);b.demon(9,36,S,MH);b.demon(12,22,W,H);b.demon(-12,22,E,H);
        b.thing(HP,-10.5f,33,0);b.thing(CELLS,10.5f,33,0);
        // observation wings
        b.rect(14,22,16,26,0,3.5f,tech,hull,obs);b.rect(16,18,24,30,0,4,tech,hull,obs);b.demon(23,29,S,MH);b.thing(HP,23,19,0);b.lamp(BLUE,23,24);
        b.rect(-16,22,-14,26,0,3.5f,tech,hull,obs);b.rect(-24,18,-16,30,0,4,tech,hull,obs);b.demon(-23,29,S,H);b.thing(CELLS,-23,19,0);b.lamp(BLUE,-23,24);
        // out
        b.rect(-1.5f,38,1.5f,42,0,3.5f,L("Tread plate"),hull,L("Hazard stripes"));
        // the spindle: north of the core
        int blast=L("Hazard blast door"),shield=L("Hex energy shield"),obsid=L("Obsidian"),spiked=L("Spiked iron");
        b.rect(-2,42,2,45,0,4,L("Tread plate"),hull,L("Hazard stripes"));
        b.door(-2,45,2,46,0,3.6f,-1,blast,hexb);
        b.rect(-2,46,2,50,0,3.6f,L("Tread plate"),hull,L("Hazard stripes"));
        b.rect(-16,50,16,70,0,12,hexb,titan,dcore);
        b.pool(fire,-1.2f,-12,54,-4,54,-4,66,-12,66);b.pool(fire,-1.2f,4,54,12,54,12,66,4,66);
        b.pillar(-1,58,1,62,shield);
        b.lamp(BLUE,-8,60);b.lamp(BLUE,8,60);b.lamp(WHITE,0,68);
        b.demon(-14,68,E,ALL);b.demon(14,68,W,ALL);b.demon(0,66,S,ALL);b.demon(-14,52,N,MH);b.demon(14,52,N,MH);b.demon(0,54,S,H);
        b.thing(HP,-15,69,0);b.thing(CELLS,15,69,0);
        // containment, west: the red key
        b.door(-17,58,-16,62,0,3.6f,-1,blast,hexb);
        b.rect(-29,54,-17,66,0,5,tech,hull,obs);
        for(int k=0;k<3;k++){float y=55+k*4;b.block(-28.5f,y,-27,y+2,2,obs,obs);}
        b.block(-23,59,-21,61,.4f,hexr,hull);b.key(0,-22,60);
        b.lamp(BLUE,-22,65);b.demon(-18,65,S,ALL);b.demon(-26,60,E,ALL);b.demon(-18,55,N,H);b.thing(HP,-28,65,0);
        // the anchor lab, east, behind the red door: the blue key where they kept her
        b.door(16,58,17,62,0,3.6f,0,blast,hexb);
        b.rect(17,54,29,66,0,5,L("Med-bay floor"),hull,L("White lab panels"));
        b.block(21,58,25,62,.6f,shield,L("White lab panels"));b.key(1,23,60);
        b.lamp(RED,28,65);b.lamp(WHITE,28,55);b.demon(28,60,W,ALL);b.demon(22,65,S,ALL);b.demon(22,55,N,MH);b.thing(CELLS,18,65,0);
        // the blue door, the coil gallery and the yellow key
        b.door(-2,70,2,71,0,3.6f,1,blast,hexb);
        b.rect(-2,71,2,76,0,3.6f,L("Tread plate"),hull,L("Hazard stripes"));
        b.rect(-12,76,12,86,0,7,hexr,titan,L("Power conduits"));
        b.pillar(-8,79,-7,80,dcore);b.pillar(7,79,8,80,dcore);b.pillar(-8,83,-7,84,dcore);b.pillar(7,83,8,84,dcore);
        b.block(-11,80,-9,82,.4f,shield,hull);b.key(2,-10,81);
        b.lamp(BLUE,-11,85);b.lamp(BLUE,11,85);b.demon(10,85,W,ALL);b.demon(-10,77,N,ALL);b.demon(10,77,N,MH);b.demon(0,84,S,H);b.thing(HP,11,81,0);
        // the yellow door and the way out
        b.door(-2,86,2,87,0,3.6f,2,spiked,obsid);
        b.rect(-2,87,2,90,0,3.6f,L("Tread plate"),hull,L("Hazard stripes"));
        b.rect(-6,90,6,98,0,8,hexb,titan,dcore);b.lamp(WHITE,-5,97);b.lamp(WHITE,5,97);b.demon(-4,96,S,ALL);b.demon(4,96,S,MH);b.thing(CELLS,5,91,0);
        SectorMap.Sector ex=b.rect(-2,98,2,101,.5f,4,L("Soul runes"),hull,blast);ex.special=SectorMap.SP_EXIT;b.lamp(WHITE,0,100);
        return b.done();
    }

    /** Map 8. Out of the ruins and up the road between channels of magma, the great stair, and the plaza before the Gate where the last Wardens wait. */
    static SectorMap lastGate(){
        B b=new B("The Last Gate");int ash=L("Ash floor"),soot=L("Soot-black stone"),obsid=L("Obsidian"),basalt=L("Basalt columns"),cinder=L("Cinder ground"),rune=L("Rune stone"),
            sig=L("Sigil tiles"),bobs=L("Bleeding obsidian"),bruns=L("Blood runes"),magma=L("Magma crust");
        b.rect(-3,0,3,6,0,4,ash,obsid,soot);b.lamp(WARM,-2,5);b.lamp(WARM,2,5);b.start(0,1.5f,N);b.thing(HP,-2,1,0);b.thing(CELLS,2,1,0);
        b.rect(-1.5f,6,1.5f,12,0,3.5f,cinder,obsid,basalt);
        // the road between magma channels
        b.rect(-6,12,6,28,0,7,cinder,obsid,rune);b.pool(magma,-.8f,-5.5f,13,-4.5f,13,-4.5f,27,-5.5f,27);b.pool(magma,-.8f,4.5f,13,5.5f,13,5.5f,27,4.5f,27);
        b.demon(-3.5f,27,S,ALL);b.demon(3.5f,24,S,MH);b.thing(HP,3.5f,13,0);
        // the great stair
        b.stairs(-4,28,4,34,0,7,0,28,.25f,.667f,rune,rune);
        // the plaza before the Gate
        b.poly(2.25f,16,sig,obsid,bobs,-10,34,10,34,14,38,14,48,10,52,-10,52,-14,48,-14,38);
        b.pillar(-9,38,-8,39,bobs);b.pillar(8,38,9,39,bobs);b.pillar(-9,47,-8,48,bobs);b.pillar(8,47,9,48,bobs);
        b.lamp(RED,-12,43);b.lamp(RED,12,43);
        b.demon(0,50,S,ALL);b.demon(-11,49,E,ALL);b.demon(11,49,W,ALL);b.demon(-12,37,E,MH);b.demon(12,37,W,MH);b.demon(-4,46,S,H);b.demon(4,46,S,H);
        b.thing(HP,-13,43,0);b.thing(HP,13,43,0);b.thing(CELLS,0,36,0);
        // through the plaza: the court of the Wardens, eight statues, eight empty plinths
        int iron=L("Hell iron"),spiked=L("Spiked iron"),bones=L("Charred bones");
        b.rect(-3,52,3,55,2.25f,6,cinder,obsid,basalt);
        b.door(-3,55,3,56,2.25f,5.5f,-1,iron,obsid);
        b.rect(-3,56,3,60,2.25f,6,cinder,obsid,basalt);
        b.rect(-18,60,18,80,2.25f,14,sig,obsid,bobs);
        for(int k=0;k<4;k++){float y=62.5f+k*4.5f;b.pillar(-13,y,-12,y+1,basalt);b.pillar(12,y,13,y+1,basalt);}
        b.pool(magma,1.45f,-3,63,3,63,3,77,-3,77);
        b.lamp(RED,-15,70);b.lamp(RED,15,70);b.lamp(WARM,0,62);b.lamp(WARM,0,78);
        b.demon(-16,78,E,ALL);b.demon(16,78,W,ALL);b.demon(-8,78,S,ALL);b.demon(8,78,S,ALL);b.demon(-16,62,N,MH);b.demon(16,62,N,MH);b.demon(0,79,S,H);
        b.thing(HP,-17,61,0);b.thing(CELLS,17,61,0);b.thing(HP,17,79,0);
        // the hall of chains, west: the red key
        b.door(-19,68,-18,72,2.25f,5.5f,-1,iron,obsid);
        b.rect(-31,62,-19,78,2.25f,8,cinder,obsid,L("Burnt masonry"));
        b.pillar(-28,65,-27,66,iron);b.pillar(-23,65,-22,66,iron);b.pillar(-28,74,-27,75,iron);b.pillar(-23,74,-22,75,iron);
        b.block(-26,69,-24,71,.5f,rune,obsid);b.key(0,-25,70);
        b.lamp(RED,-25,77);b.demon(-30,70,E,ALL);b.demon(-25,77,S,ALL);b.demon(-25,63,N,MH);b.demon(-20,76,W,H);b.thing(CELLS,-30,63,0);
        // the ossuary of the Wardens, east, behind the red door: the blue key
        b.door(18,68,19,72,2.25f,5.5f,0,spiked,obsid);
        b.rect(19,62,31,78,2.25f,8,L("Bone-white cobbles"),obsid,L("Ossuary wall"));
        for(int k=0;k<4;k++){float y=63.5f+k*3.5f;b.block(29,y,30.5f,y+2,1.2f,bones,bones);}
        b.block(23,69,25,71,.5f,rune,obsid);b.key(1,24,70);
        b.lamp(BLUE,24,77);b.demon(30,77,W,ALL);b.demon(20,77,S,ALL);b.demon(24,63,N,MH);b.demon(30,63,W,H);b.thing(HP,20,63,0);
        // the blue door, the stair to the Gate's terrace, the yellow key on the last altar
        b.door(-3,80,3,81,2.25f,5.5f,1,spiked,obsid);
        b.stairs(-3,81,3,87,2.25f,10,0,81,.25f,.667f,rune,rune);
        b.rect(-10,87,10,97,4.5f,12,sig,obsid,bobs);
        b.block(-1,90,1,92,.5f,L("Pentagram floor"),bobs);b.key(2,0,91);
        b.pillar(-9,88,-8,89,bobs);b.pillar(8,88,9,89,bobs);
        b.lamp(RED,-9,95);b.lamp(RED,9,95);
        b.demon(-8,95,E,ALL);b.demon(8,95,W,ALL);b.demon(0,96,S,ALL);b.demon(-8,90,E,MH);b.demon(8,90,W,MH);b.demon(-4,96,S,H);b.demon(4,96,S,H);
        b.thing(HP,-9,96,0);b.thing(HP,9,96,0);b.thing(CELLS,0,88,0);
        // the Gate
        b.door(-3,97,3,98,4.5f,7.5f,2,spiked,obsid);
        SectorMap.Sector ex=b.rect(-4,98,4,102,4.5f,9,L("Pentagram floor"),obsid,bruns);ex.special=SectorMap.SP_EXIT;b.lamp(WHITE,0,100);
        return b.done();
    }

    /** A small blank map for a new level in the editor: one room and the player start. */
    static SectorMap blank(){
        B b=new B("New map");b.rect(0,0,10,8,0,4,FT,CO,BR);b.start(2,2,N);b.lamp(SectorMap.T_LAMP_WARM,5,4);return b.done();
    }
}
