package com.harlequin.slayer;

import java.util.ArrayList;

/**
 * The playable level built from a {@link SectorMap}: render geometry (floors and ceilings triangulated per sector,
 * walls, steps and lintels from the lines), collision against the walls and height changes, floor and ceiling
 * heights, ray casts for pellets and sight, a 0.5 m raster of the map (for navigation, lava and effects), and which
 * lights each raster cell can see so light doesn't leak through walls.
 *
 * World space: x east, y north, z up, metres. Pure Java.
 */
final class Level {
    /** One raster cell, sampled at its centre. */
    static final class Cell {boolean solid,lava,shut;float floor,ceil;int floorMat,sector=-1,door=-1;}
    /**
     * A door: a sector with the Door special (as in RPG Pocket Editor). Its ceiling is the door: shut, it rests on the
     * floor; opened, it rises to the height drawn in the editor, waits, and comes back down, going back up if anyone
     * is under it. Walking up to it opens it (a keyed door only for her, carrying its key); demons open plain doors.
     * The walls above it rise with it. Cells of a keyed door are shut to navigation while it is too low to pass.
     */
    static final class Door {final int sector,key;final float floor,top,cx,cy;int state;float wait;boolean hold;
        Door(int s,int k,float f,float t,float x,float y){sector=s;key=k;floor=f;top=t;cx=x;cy=y;}}
    static final int D_CLOSED=0,D_OPENING=1,D_OPEN=2,D_CLOSING=3;
    static final class Light {float x,y,z,r,g,b,radius;boolean flicker;Light(float x,float y,float z,int rgb,float i,float radius,boolean fl){this.x=x;this.y=y;this.z=z;r=((rgb>>16)&255)/255f*i;g=((rgb>>8)&255)/255f*i;b=(rgb&255)/255f*i;this.radius=radius;flicker=fl;}}
    /** A health orb or a box of shells lying in the level. */
    static final class Pickup {final int type;final float x,y,z;boolean taken;Pickup(int t,float x,float y,float z){type=t;this.x=x;this.y=y;this.z=z;}}

    static final float STEP=.55f,CELL=.5f;
    static final int MAX_LIGHTS=13;   // light slots 13-15 are the game's own (fireballs, blasts, muzzle flash)
    /** A map may have up to this many lights; the 13 nearest her are streamed into the slots as she moves. */
    static final int MAX_LEVEL_LIGHTS=64;
    final SectorMap map;
    /** Raster: W x H cells of CELL metres, origin (ox, oy). */
    final int W,H;final float ox,oy;final Cell[] cells;
    final ArrayList<Light> lights=new ArrayList<>();
    final ArrayList<float[]> demonSpawns=new ArrayList<>();
    final ArrayList<Pickup> pickups=new ArrayList<>();
    final ArrayList<Door> doors=new ArrayList<>();
    /** Bumped whenever a door's passability changes (navigation fields rebuild). */
    int navVersion;
    float spawnX,spawnY,spawnAngle;
    /** Per raster cell: which of the streamed light slots it can see (bit i = slot i). */
    final int[] lightMask;
    /** Per raster cell: which level lights it can see (bit i = light i, up to 64). */
    private final long[] lightBits;
    /** The level light in each slot (slot count = slotCount). */
    final int[] slot=new int[MAX_LIGHTS];int slotCount;private float pickX=Float.NaN,pickY;

    /** The first campaign map. */
    Level(){this(Maps.campaign(0));}
    Level(SectorMap m){
        map=m;m.index();
        ox=m.minX()-1;oy=m.minY()-1;W=(int)Math.ceil((m.maxX()-m.minX()+2)/CELL);H=(int)Math.ceil((m.maxY()-m.minY()+2)/CELL);
        cells=new Cell[W*H];
        for(int j=0;j<H;j++)for(int i=0;i<W;i++){Cell c=new Cell();int s=m.sectorAt(cx(i),cy(j));c.sector=s;
            if(s<0){c.solid=true;}else{SectorMap.Sector S=m.sectors.get(s);c.floor=S.floor;c.ceil=S.ceil;c.floorMat=S.floorTex;c.lava=S.special==SectorMap.SP_LAVA||Tex.hurts(S.floorTex);c.solid=S.ceil-S.floor<.05f;
                if(SectorMap.isDoor(S)){c.solid=false;c.ceil=S.ceil-S.floor>.3f?S.ceil:lowestNeighbourCeil(s)-.1f;}}   // a door's cells: as open (the door shuts them while it can't be passed)
            cells[j*W+i]=c;}
        spawnX=m.playerX;spawnY=m.playerY;spawnAngle=m.facing;
        for(SectorMap.Thing t:m.things){
            if(!Skill.present(t.skills))continue;   // not on this difficulty
            if(t.type==SectorMap.T_DEMON)demonSpawns.add(new float[]{t.x,t.y,t.angle});
            else if(t.type==SectorMap.T_HEALTH||t.type==SectorMap.T_SHELLS||SectorMap.isKey(t.type))pickups.add(new Pickup(t.type,t.x,t.y,floorUnder(t.x,t.y,0)));
            else if(t.type>=SectorMap.T_LAMP_WARM&&t.type<=SectorMap.T_LAMP_WHITE&&lights.size()<MAX_LEVEL_LIGHTS){int s=m.sectorAt(t.x,t.y);if(s<0)continue;SectorMap.Sector S=m.sectors.get(s);
                float z=Math.min(S.ceil-.3f,S.floor+3.4f);boolean fl=S.special==SectorMap.SP_FLICKER||t.type==SectorMap.T_LAMP_RED;
                lights.add(new Light(t.x,t.y,z,SectorMap.LAMP_RGB[t.type],t.type==SectorMap.T_LAMP_WHITE?3.6f:3.2f,Math.max(9,Math.min(16,(S.ceil-S.floor)*2.2f)),fl));}}
        glowLights(m);
        lightBits=new long[W*H];lightMask=new int[W*H];bakeLights();pickLights(spawnX,spawnY,true);
        for(int s=0;s<m.sectors.size();s++){SectorMap.Sector S=m.sectors.get(s);if(!SectorMap.isDoor(S))continue;float[] c=m.centroid(S);
            float top=S.ceil-S.floor>.3f?S.ceil:lowestNeighbourCeil(s)-.1f;   // a door drawn shut opens to its neighbours' ceiling
            doors.add(new Door(s,SectorMap.doorKey(S),S.floor,Math.max(top,S.floor+.3f),c[0],c[1]));}
        for(Cell c:cells)if(c.sector>=0)for(int d=0;d<doors.size();d++)if(doors.get(d).sector==c.sector)c.door=d;
        resetDoors();
    }

    // ------------------------------------------------------------------ doors
    static final float DOOR_SPEED=2.5f,DOOR_WAIT=4f,DOOR_REACH=1.5f,DEMON_REACH=1.1f;
    SectorMap.Sector doorSector(Door d){return map.sectors.get(d.sector);}
    float doorHeight(Door d){return doorSector(d).ceil-d.floor;}
    boolean passable(Door d){return doorHeight(d)>=Nav.HEADROOM;}
    /** Every door shut (a new level, or she died and it starts over). */
    void resetDoors(){for(Door d:doors){d.state=D_CLOSED;d.wait=0;d.hold=false;doorSector(d).ceil=d.floor;}shutCells();}
    /** Opens a door at once and keeps it open (tests, the editor's 3D view). */
    void openDoor(Door d){d.state=D_OPEN;d.hold=true;doorSector(d).ceil=d.top;shutCells();}
    private void shutCells(){
        boolean changed=false;
        for(Cell c:cells){if(c.door<0)continue;Door d=doors.get(c.door);boolean shut=d.key>=0&&!passable(d);if(c.shut!=shut){c.shut=shut;changed=true;}}
        if(changed)navVersion++;
    }
    /** Results of one step (bits): a door began to open, began to close, or she is at a door she has no key for. */
    static final int DOOR_OPENED=1,DOOR_CLOSING=2,DOOR_LOCKED=4;int lockedKey=-1;
    /** Where the last door event happened (for its sound). */
    float eventX,eventY,eventZ;
    /**
     * Moves the doors. She opens the doors she walks up to (keys: a bit per key she carries); `others` are the demons
     * ({x, y, z}), who open plain doors and, like her, stop a door closing on them.
     */
    int updateDoors(float dt,float px,float py,float pz,int keys,java.util.List<float[]> others){
        int ev=0;lockedKey=-1;boolean moved=false;
        for(Door d:doors){SectorMap.Sector S=doorSector(d);
            boolean her=Math.abs(pz-d.floor)<1.7f&&distTo(d,px,py)<DOOR_REACH,want=false;
            if(her){if(d.key<0||(keys&(1<<d.key))!=0)want=true;else if(d.state==D_CLOSED||d.state==D_CLOSING){ev|=DOOR_LOCKED;lockedKey=d.key;}}
            boolean someone=her&&want;
            if(d.key<0&&!want&&others!=null)for(float[] o:others)if(Math.abs(o[2]-d.floor)<1.7f&&distTo(d,o[0],o[1])<DEMON_REACH){want=true;someone=true;break;}
            if(want&&(d.state==D_CLOSED||d.state==D_CLOSING)){d.state=D_OPENING;ev|=DOOR_OPENED;event(d);}
            switch(d.state){
                case D_OPENING:S.ceil=Math.min(d.top,S.ceil+DOOR_SPEED*dt);moved=true;if(S.ceil>=d.top){d.state=D_OPEN;d.wait=DOOR_WAIT;}break;
                case D_OPEN:if(d.hold)break;if(someone||occupied(d,px,py,pz,others,S.ceil-.5f))d.wait=DOOR_WAIT;else if((d.wait-=dt)<=0){d.state=D_CLOSING;ev|=DOOR_CLOSING;event(d);}break;
                case D_CLOSING:{float next=Math.max(d.floor,S.ceil-DOOR_SPEED*dt);
                    if(occupied(d,px,py,pz,others,next)){d.state=D_OPENING;ev|=DOOR_OPENED;event(d);break;}   // someone under it: back up
                    S.ceil=next;moved=true;if(next<=d.floor)d.state=D_CLOSED;break;}
                default:break;}}
        if(moved)shutCells();
        return ev;
    }
    private void event(Door d){eventX=d.cx;eventY=d.cy;eventZ=d.floor+1;}
    /** Is anyone (her or a demon) standing in the door, tall enough to reach a ceiling at `ceil`? */
    private boolean occupied(Door d,float px,float py,float pz,java.util.List<float[]> others,float ceil){
        if(distTo(d,px,py)<Player.RADIUS&&pz+Player.HEIGHT>ceil)return true;
        if(others!=null)for(float[] o:others)if(distTo(d,o[0],o[1])<.45f&&o[2]+2.2f>ceil)return true;
        return false;
    }
    /** Distance from (x,y) to the door's sector (0 inside it). */
    float distTo(Door d,float x,float y){
        SectorMap.Sector S=doorSector(d);if(map.sectorAt(x,y)==d.sector)return 0;float best=1e9f;int n=S.loop.size();
        for(int i=0;i<n;i++){SectorMap.Vertex a=map.vertices.get(S.loop.get(i)),b=map.vertices.get(S.loop.get((i+1)%n));best=Math.min(best,SectorMap.segDist(x,y,a.x,a.y,b.x,b.y));}
        return best;
    }
    private boolean isDoorSector(int si){return si>=0&&SectorMap.isDoor(map.sectors.get(si));}
    /**
     * The moving part of the level as it is now: each door's ceiling and the walls above it, pegged to the door's
     * bottom edge so the texture rises with it (one list per material). The static build leaves these out.
     */
    void doorGeometry(FloatList[] out){
        for(Door d:doors){SectorMap.Sector S=doorSector(d);float t=tile(S.ceilTex);
            if(S.ceil-S.floor>.02f){float[] tri=map.triangulate(d.sector);FloatList o=list(out,S.ceilTex);
                for(int k=0;k+5<tri.length;k+=6)for(int v=2;v>=0;v--){float x=tri[k+v*2],y=tri[k+v*2+1];o.add(x,y,S.ceil,0,0,-1,-1,0,0,-x*t,y*t);}}}
        for(SectorMap.Line L:map.lines){if(L.front<0||L.back<0)continue;boolean fd=isDoorSector(L.front),bd=isDoorSector(L.back);if(!fd&&!bd)continue;
            SectorMap.Sector F=map.sectors.get(L.front),B=map.sectors.get(L.back);SectorMap.Vertex a=map.vertices.get(L.a),b=map.vertices.get(L.b);
            int ab=map.wallTexture(L.a,L.b,-1),ba=map.wallTexture(L.b,L.a,-1);
            if(B.ceil<F.ceil-1e-3f){int mat=ab>=0?ab:B.wallTex;wall(out,mat,a.x,a.y,b.x,b.y,Math.max(B.ceil,F.floor),F.ceil,B.ceil);}
            if(F.ceil<B.ceil-1e-3f){int mat=ba>=0?ba:F.wallTex;wall(out,mat,b.x,b.y,a.x,a.y,Math.max(F.ceil,B.floor),B.ceil,F.ceil);}}
    }
    private static FloatList list(FloatList[] out,int mat){if(out[mat]==null)out[mat]=new FloatList();return out[mat];}

    // ------------------------------------------------------------------ streamed lights
    /**
     * Chooses the lights nearest (x,y) for the 13 slots and rewrites the slot masks if the choice changed. Returns
     * true if it did (the caller re-uploads the mask). Lights already in a slot keep it, so the textures stay stable.
     */
    boolean pickLights(float x,float y,boolean force){
        if(!force&&!Float.isNaN(pickX)&&Math.hypot(x-pickX,y-pickY)<1.5f)return false;pickX=x;pickY=y;
        int n=lights.size();if(n<=MAX_LIGHTS&&!force&&slotCount==n)return false;
        Integer[] order=new Integer[n];for(int i=0;i<n;i++)order[i]=i;
        java.util.Arrays.sort(order,(a,b)->Float.compare(lightScore(lights.get(a),x,y),lightScore(lights.get(b),x,y)));
        int want=Math.min(MAX_LIGHTS,n);boolean[] chosen=new boolean[n];for(int i=0;i<want;i++)chosen[order[i]]=true;
        int[] next=new int[MAX_LIGHTS];java.util.Arrays.fill(next,-1);
        for(int s=0;s<slotCount;s++)if(chosen[slot[s]]){next[s]=slot[s];chosen[slot[s]]=false;}   // keep its slot
        for(int i=0;i<n;i++)if(chosen[i])for(int s=0;s<MAX_LIGHTS;s++)if(next[s]<0){next[s]=i;break;}
        boolean changed=force;for(int s=0;s<MAX_LIGHTS;s++)if(s>=slotCount?next[s]>=0:next[s]!=slot[s])changed=true;
        if(!changed)return false;
        slotCount=0;for(int s=0;s<MAX_LIGHTS;s++)if(next[s]>=0)slotCount=s+1;
        for(int s=0;s<MAX_LIGHTS;s++)slot[s]=next[s];
        for(int c=0;c<lightMask.length;c++){long b=lightBits[c];int m=0;if(b!=0)for(int s=0;s<slotCount;s++)if(slot[s]>=0&&(b>>>slot[s]&1)!=0)m|=1<<s;lightMask[c]=m;}
        return true;
    }
    /** Distance to the light's reach (nearer and bigger first). */
    private static float lightScore(Light L,float x,float y){return (float)Math.hypot(L.x-x,L.y-y)-L.radius*.6f;}
    /** The level light in slot s, or null. */
    Light slotLight(int s){return s<slotCount&&slot[s]>=0?lights.get(slot[s]):null;}
    /**
     * Glowing surfaces light their surroundings: lava and every glowing floor, ceiling and wall becomes a light in
     * its own colour (flickering for lava and magma). Placed lamps keep their slots; the glows take what is left,
     * strongest (biggest, brightest) first, spaced at least 3 m apart so one big glowing room doesn't use them all.
     */
    private void glowLights(SectorMap m){
        ArrayList<float[]> cand=new ArrayList<>();   // x, y, z, rgb, intensity, radius, flicker, weight
        for(int s=0;s<m.sectors.size();s++){SectorMap.Sector S=m.sectors.get(s);float area=Math.abs(m.area(S));float[] c=m.centroid(S);
            int ft=S.special==SectorMap.SP_LAVA&&Tex.glowStrength(S.floorTex)<=0?Tex.LAVA:S.floorTex;float fk=Tex.glowStrength(ft);
            if(fk>0&&area>=1.5f){float k=.55f+.45f*fk;cand.add(new float[]{c[0],c[1],S.floor+1.1f,Tex.glowColor(ft),2.3f*k,Math.max(7,Math.min(12,6+(float)Math.sqrt(area)*1.4f)),Tex.flows(ft)||ft==Tex.LAVA?1:0,area*k});}
            float ck=Tex.glowStrength(S.ceilTex);
            if(ck>0&&area>=1&&S.ceil-S.floor>.6f){float k=.55f+.45f*ck;cand.add(new float[]{c[0],c[1],S.ceil-.45f,Tex.glowColor(S.ceilTex),2.6f*k,Math.max(7,Math.min(14,(S.ceil-S.floor)*2.4f+(float)Math.sqrt(area))),Tex.flows(S.ceilTex)?1:0,area*k*1.2f});}}
        walls((mat,ax,ay,bx,by,z0,z1)->{float k0=Tex.glowStrength(mat);if(k0<=0)return;float dx=bx-ax,dy=by-ay,l=(float)Math.hypot(dx,dy),h=z1-z0;if(l<1||h<.4f)return;
            float nx=-dy/l,ny=dx/l,k=.55f+.45f*k0;int n=Math.max(1,Math.round(l/4));
            for(int q=0;q<n;q++){float t=(q+.5f)/n;cand.add(new float[]{ax+dx*t+nx*.6f,ay+dy*t+ny*.6f,z0+Math.min(1.6f,h*.5f),Tex.glowColor(mat),1.9f*k,6.5f,Tex.flows(mat)?1:0,(l/n)*Math.min(h,3)*k*.8f});}});
        cand.sort((a,b)->Float.compare(b[7],a[7]));
        ArrayList<float[]> placed=new ArrayList<>();
        int cap=Math.min(MAX_LEVEL_LIGHTS,Math.max(MAX_LIGHTS,lights.size()+Math.max(4,(int)(Math.sqrt(W*H)*CELL/4))));   // more glows on bigger maps
        for(float[] c:cand){if(lights.size()>=cap)break;boolean near=false;for(float[] p:placed)if(Math.hypot(p[0]-c[0],p[1]-c[1])<3&&Math.abs(p[2]-c[2])<2.5f){near=true;break;}
            if(near||m.sectorAt(c[0],c[1])<0)continue;placed.add(c);lights.add(new Light(c[0],c[1],c[2],(int)c[3],c[4],c[5],c[6]>.5f));}
    }
    float cx(int i){return ox+(i+.5f)*CELL;}
    float cy(int j){return oy+(j+.5f)*CELL;}
    int ix(float x){return (int)Math.floor((x-ox)/CELL);}
    int iy(float y){return (int)Math.floor((y-oy)/CELL);}
    Cell at(int i,int j){if(i<0||j<0||i>=W||j>=H)return SOLID;return cells[j*W+i];}
    private static final Cell SOLID=new Cell();static{SOLID.solid=true;}
    Cell atF(float x,float y){return at(ix(x),iy(y));}
    SectorMap.Sector sectorAt(float x,float y){int s=map.sectorAt(x,y);return s<0?null:map.sectors.get(s);}

    // ------------------------------------------------------------------ geometry
    /** Vertex: x,y,z, normal, tangent, u,v (11 floats). Returns one float list per material. */
    @SuppressWarnings("unchecked")
    ArrayList<float[]>[] build(){
        float[] keep=new float[doors.size()];for(int i=0;i<keep.length;i++){Door d=doors.get(i);SectorMap.Sector S=map.sectors.get(d.sector);keep[i]=S.ceil;S.ceil=d.top;}   // built open; the moving parts are drawn by doorGeometry
        staticOnly=true;
        try{return buildOpen();}finally{staticOnly=false;for(int i=0;i<keep.length;i++)map.sectors.get(doors.get(i).sector).ceil=keep[i];}
    }
    @SuppressWarnings("unchecked")
    private ArrayList<float[]>[] buildOpen(){
        FloatList[] out=new FloatList[Tex.COUNT];for(int i=0;i<out.length;i++)out[i]=new FloatList();
        for(int si=0;si<map.sectors.size();si++){SectorMap.Sector s=map.sectors.get(si);float[] tri=map.triangulate(si);
            float tf=tile(s.floorTex),tc=tile(s.ceilTex);
            for(int k=0;k+5<tri.length;k+=6)for(int v=0;v<3;v++){float x=tri[k+v*2],y=tri[k+v*2+1];out[s.floorTex].add(x,y,s.floor,0,0,1,1,0,0,x*tf,y*tf);}
            if(s.ceil-s.floor>.01f&&!SectorMap.isDoor(s))for(int k=0;k+5<tri.length;k+=6)for(int v=2;v>=0;v--){float x=tri[k+v*2],y=tri[k+v*2+1];out[s.ceilTex].add(x,y,s.ceil,0,0,-1,-1,0,0,-x*tc,y*tc);}}
        walls((mat,ax,ay,bx,by,z0,z1)->wall(out,mat,ax,ay,bx,by,z0,z1));
        ArrayList<float[]>[] res=new ArrayList[Tex.COUNT];
        for(int i=0;i<res.length;i++){res[i]=new ArrayList<>();res[i].add(out[i].toArray());}
        return res;
    }
    interface WallSink{void wall(int mat,float ax,float ay,float bx,float by,float z0,float z1);}
    /** Every wall face of the map (each facing the left side of a→b): one-sided walls, steps and lintels. */
    void walls(WallSink out){
        for(SectorMap.Line L:map.lines){SectorMap.Vertex a=map.vertices.get(L.a),b=map.vertices.get(L.b);
            SectorMap.Sector F=L.front>=0?map.sectors.get(L.front):null,B=L.back>=0?map.sectors.get(L.back):null;
            if(F==null&&B==null)continue;
            int ab=map.wallTexture(L.a,L.b,-1),ba=map.wallTexture(L.b,L.a,-1);   // painted faces (from the 3D editor)
            if(B==null){out.wall(ab>=0?ab:F.wallTex,a.x,a.y,b.x,b.y,F.floor,F.ceil);continue;}
            if(F==null){out.wall(ba>=0?ba:B.wallTex,b.x,b.y,a.x,a.y,B.floor,B.ceil);continue;}
            // steps and ledges below, lintels above; each face looks into the sector it is seen from
            if(B.floor>F.floor+1e-3f)out.wall(ab>=0?ab:B.wallTex,a.x,a.y,b.x,b.y,F.floor,Math.min(B.floor,F.ceil));
            if(F.floor>B.floor+1e-3f)out.wall(ba>=0?ba:F.wallTex,b.x,b.y,a.x,a.y,B.floor,Math.min(F.floor,B.ceil));
            if(staticOnly&&(SectorMap.isDoor(F)||SectorMap.isDoor(B)))continue;   // above a door: it moves (doorGeometry)
            if(B.ceil<F.ceil-1e-3f)out.wall(ab>=0?ab:B.wallTex,a.x,a.y,b.x,b.y,Math.max(B.ceil,F.floor),F.ceil);
            if(F.ceil<B.ceil-1e-3f)out.wall(ba>=0?ba:F.wallTex,b.x,b.y,a.x,a.y,Math.max(F.ceil,B.floor),B.ceil);}
    }
    static final class FloatList{float[] a=new float[1024];int n;void clear(){n=0;}void add(float... v){if(n+v.length>a.length)a=java.util.Arrays.copyOf(a,Math.max(a.length*2,n+v.length));System.arraycopy(v,0,a,n,v.length);n+=v.length;}float[] toArray(){return java.util.Arrays.copyOf(a,n);}}
    /** A wall along a→b from z0 up to z1, facing the left side of a→b. */
    private boolean staticOnly;
    private float lowestNeighbourCeil(int s){float best=1e9f;for(SectorMap.Line L:map.lines){int o=L.front==s?L.back:L.back==s?L.front:-2;if(o>=0)best=Math.min(best,map.sectors.get(o).ceil);}return best>1e8f?map.sectors.get(s).floor+3:best;}
    private static void wall(FloatList[] out,int mat,float ax,float ay,float bx,float by,float z0,float z1){wall(out,mat,ax,ay,bx,by,z0,z1,0);}
    /** A wall from z0 to z1; `peg` is the height its texture is fixed to (a door's wall rises with the door). */
    private static void wall(FloatList[] out,int mat,float ax,float ay,float bx,float by,float z0,float z1,float peg){
        if(z1-z0<1e-3f)return;FloatList o=list(out,mat);float t=tile(mat);
        float dx=bx-ax,dy=by-ay,l=(float)Math.hypot(dx,dy);if(l<1e-4f)return;
        float nx=-dy/l,ny=dx/l;            // left of a→b
        float tx=ny,ty=-nx;               // tangent = cross(B,N) with B = (0,0,-1)
        float ua=(ax*tx+ay*ty)*t,ub=(bx*tx+by*ty)*t,v0=-(z0-peg)*t,v1=-(z1-peg)*t;
        float[][] P={{ax,ay,z0,ua,v0},{bx,by,z0,ub,v0},{bx,by,z1,ub,v1},{ax,ay,z1,ua,v1}};
        int[] idx={0,2,1,0,3,2};
        for(int k:idx)o.add(P[k][0],P[k][1],P[k][2],nx,ny,0,tx,ty,0,P[k][3],P[k][4]);
    }
    static float tile(int mat){if(mat>=Tex.FIRST_LIB)return TexLib.tile(mat-Tex.FIRST_LIB);switch(mat){case Tex.BRICK:return .5f;case Tex.METAL_PANEL:return .5f;case Tex.FLOOR_TILES:return .33f;case Tex.DIAMOND_PLATE:return .5f;case Tex.CONCRETE:return .4f;
        case Tex.CRATE:return 1f;case Tex.LIGHT:return 1f;case Tex.PILLAR:return .5f;case Tex.LAVA:return .3f;default:return 1f;}}

    // ------------------------------------------------------------------ baked light visibility
    private boolean blocksLight(SectorMap.Line L){
        if(L.front<0||L.back<0)return true;SectorMap.Sector F=map.sectors.get(L.front),B=map.sectors.get(L.back);
        return Math.min(F.ceil,B.ceil)-Math.max(F.floor,B.floor)<.25f;}
    private void bakeLights(){
        ArrayList<float[]> walls=new ArrayList<>();
        for(SectorMap.Line L:map.lines)if(blocksLight(L)){SectorMap.Vertex a=map.vertices.get(L.a),b=map.vertices.get(L.b);walls.add(new float[]{a.x,a.y,b.x,b.y});}
        for(int li=0;li<lights.size()&&li<MAX_LEVEL_LIGHTS;li++){Light L=lights.get(li);
            int i0=Math.max(0,ix(L.x-L.radius-1)),i1=Math.min(W-1,ix(L.x+L.radius+1)),j0=Math.max(0,iy(L.y-L.radius-1)),j1=Math.min(H-1,iy(L.y+L.radius+1));
            ArrayList<float[]> near=new ArrayList<>();for(float[] w:walls)if(SectorMap.segDist(L.x,L.y,w[0],w[1],w[2],w[3])<L.radius+1)near.add(w);
            for(int j=j0;j<=j1;j++)for(int i=i0;i<=i1;i++){Cell c=at(i,j);if(c.solid&&!edgeCell(i,j))continue;
                float x=cx(i),y=cy(j);if(Math.hypot(x-L.x,y-L.y)>L.radius+1)continue;
                float q=CELL*.45f;float[][] pts={{x,y},{x-q,y-q},{x+q,y-q},{x-q,y+q},{x+q,y+q}};
                for(float[] p:pts)if(sees(near,L.x,L.y,p[0],p[1])){lightBits[j*W+i]|=1L<<li;break;}}}
    }
    /** Solid cells right next to open ones still need masks (their walls are lit from the open side). */
    private boolean edgeCell(int i,int j){return !at(i+1,j).solid||!at(i-1,j).solid||!at(i,j+1).solid||!at(i,j-1).solid;}
    private static boolean sees(ArrayList<float[]> walls,float x0,float y0,float x1,float y1){
        for(float[] w:walls)if(segCross(x0,y0,x1,y1,w[0],w[1],w[2],w[3]))return false;return true;}
    static boolean segCross(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){
        float d1=(bx-ax)*(cy-ay)-(by-ay)*(cx-ax),d2=(bx-ax)*(dy-ay)-(by-ay)*(dx-ax),d3=(dx-cx)*(ay-cy)-(dy-cy)*(ax-cx),d4=(dx-cx)*(by-cy)-(dy-cy)*(bx-cx);
        return d1*d2<-1e-9f&&d3*d4<-1e-9f;}

    // ------------------------------------------------------------------ collision
    /** True if an upright cylinder (radius r, from z to z+h) at (x,y) overlaps a wall or something taller than a step. */
    boolean blocked(float x,float y,float z,float r,float h){
        SectorMap.Sector s=sectorAt(x,y);if(s==null||s.floor>z+STEP||s.ceil<z+h)return true;
        for(int li:map.linesNear(x,y)){SectorMap.Line L=map.lines.get(li);SectorMap.Vertex a=map.vertices.get(L.a),b=map.vertices.get(L.b);
            if(SectorMap.segDist(x,y,a.x,a.y,b.x,b.y)>=r)continue;
            if(L.front<0||L.back<0)return true;
            for(int si:new int[]{L.front,L.back}){SectorMap.Sector o=map.sectors.get(si);if(o.floor>z+STEP||o.ceil<z+h)return true;}}
        return false;
    }
    float floorUnder(float x,float y,float r){
        float f=-99;SectorMap.Sector s=sectorAt(x,y);if(s!=null)f=s.floor;
        if(r>0)for(int li:map.linesNear(x,y)){SectorMap.Line L=map.lines.get(li);SectorMap.Vertex a=map.vertices.get(L.a),b=map.vertices.get(L.b);
            if(SectorMap.segDist(x,y,a.x,a.y,b.x,b.y)>=r)continue;
            if(L.front>=0)f=Math.max(f,map.sectors.get(L.front).floor);if(L.back>=0)f=Math.max(f,map.sectors.get(L.back).floor);}
        return f;
    }
    float ceilOver(float x,float y,float r){
        float f=99;SectorMap.Sector s=sectorAt(x,y);if(s!=null)f=s.ceil;
        for(int li:map.linesNear(x,y)){SectorMap.Line L=map.lines.get(li);SectorMap.Vertex a=map.vertices.get(L.a),b=map.vertices.get(L.b);
            if(SectorMap.segDist(x,y,a.x,a.y,b.x,b.y)>=r)continue;
            if(L.front>=0)f=Math.min(f,map.sectors.get(L.front).ceil);if(L.back>=0)f=Math.min(f,map.sectors.get(L.back).ceil);}
        return f;
    }
    boolean inside(float x,float y,float z){SectorMap.Sector s=sectorAt(x,y);return s==null||z<s.floor||z>s.ceil;}

    // ------------------------------------------------------------------ picking (the 3D editor)
    static final int PICK_WALL=1,PICK_FLOOR=2,PICK_CEIL=3;
    /**
     * The surface a ray hits: {kind, sector, a, b, x, y, z} where a→b is the wall face (facing the ray), sector is the
     * floor/ceiling's sector or the sector the wall face looks into; null if nothing.
     */
    float[] pick(float ox,float oy,float oz,float dx,float dy,float dz,float max){
        float[] h=ray(ox,oy,oz,dx,dy,dz,max);if(h==null)return null;
        float hx=h[0],hy=h[1],hz=h[2];
        if(h[5]>.5f||h[5]<-.5f){int s=map.sectorAt(hx,hy);return s<0?null:new float[]{h[5]>0?PICK_FLOOR:PICK_CEIL,s,-1,-1,hx,hy,hz};}
        int best=-1;float bd=.08f;
        for(int li:map.linesNear(hx,hy)){SectorMap.Line L=map.lines.get(li);SectorMap.Vertex a=map.vertices.get(L.a),b=map.vertices.get(L.b);float d=SectorMap.segDist(hx,hy,a.x,a.y,b.x,b.y);if(d<bd){bd=d;best=li;}}
        if(best<0)return null;
        SectorMap.Line L=map.lines.get(best);SectorMap.Vertex a=map.vertices.get(L.a),b=map.vertices.get(L.b);
        float lx=-(b.y-a.y),ly=b.x-a.x;   // left of a→b
        boolean leftFaces=lx*(ox-hx)+ly*(oy-hy)>0;
        int fa=leftFaces?L.a:L.b,fb=leftFaces?L.b:L.a,sec=leftFaces?L.front:L.back;
        return new float[]{PICK_WALL,sec,fa,fb,hx,hy,hz};
    }

    // ------------------------------------------------------------------ ray cast
    /** Casts a ray; returns {x,y,z, nx,ny,nz, distance, material} or null. */
    float[] ray(float ox,float oy,float oz,float dx,float dy,float dz,float max){
        float step=.04f;
        for(float t=step;t<max;t+=step){float x=ox+dx*t,y=oy+dy*t,z=oz+dz*t;
            if(!inside(x,y,z))continue;
            float lo=t-step,hi=t;for(int k=0;k<10;k++){float m=(lo+hi)*.5f;if(inside(ox+dx*m,oy+dy*m,oz+dz*m))hi=m;else lo=m;}
            float hx=ox+dx*lo,hy=oy+dy*lo,hz=oz+dz*lo,ex=ox+dx*hi,ey=oy+dy*hi,ez=oz+dz*hi;
            SectorMap.Sector from=sectorAt(hx,hy),to=sectorAt(ex,ey);
            if(from!=null&&to==from){   // through its floor or ceiling
                if(ez<from.floor)return new float[]{hx,hy,hz,0,0,1,lo,from.floorTex};
                return new float[]{hx,hy,hz,0,0,-1,lo,from.ceilTex};}
            if(from!=null){   // a wall, a step riser or a lintel: find the line crossed
                float nx=-dx,ny=-dy;float l=(float)Math.hypot(nx,ny);if(l>1e-5f){nx/=l;ny/=l;}else{nx=1;ny=0;}
                for(int li:map.linesNear(hx,hy)){SectorMap.Line L=map.lines.get(li);SectorMap.Vertex a=map.vertices.get(L.a),b=map.vertices.get(L.b);
                    if(segCross(ox,oy,ex+dx*.01f,ey+dy*.01f,a.x,a.y,b.x,b.y)||SectorMap.segDist(hx,hy,a.x,a.y,b.x,b.y)<.02f){
                        float wx=b.x-a.x,wy=b.y-a.y,wl=(float)Math.hypot(wx,wy);float lx=-wy/wl,ly=wx/wl;
                        if(lx*dx+ly*dy>0){lx=-lx;ly=-ly;}nx=lx;ny=ly;break;}}
                int mat=to!=null?to.wallTex:from.wallTex;
                return new float[]{hx,hy,hz,nx,ny,0,lo,mat};}
            return new float[]{hx,hy,hz,-dx,-dy,-dz,lo,Tex.BRICK};}
        return null;
    }
}
