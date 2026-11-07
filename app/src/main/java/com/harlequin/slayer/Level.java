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
    static final class Cell {boolean solid,lava;float floor,ceil;int floorMat,sector=-1;}
    static final class Light {float x,y,z,r,g,b,radius;boolean flicker;Light(float x,float y,float z,int rgb,float i,float radius,boolean fl){this.x=x;this.y=y;this.z=z;r=((rgb>>16)&255)/255f*i;g=((rgb>>8)&255)/255f*i;b=(rgb&255)/255f*i;this.radius=radius;flicker=fl;}}
    /** A health orb or a box of shells lying in the level. */
    static final class Pickup {final int type;final float x,y,z;boolean taken;Pickup(int t,float x,float y,float z){type=t;this.x=x;this.y=y;this.z=z;}}

    static final float STEP=.55f,CELL=.5f;
    static final int MAX_LIGHTS=13;   // light slots 13-15 are the game's own (fireballs, blasts, muzzle flash)
    final SectorMap map;
    /** Raster: W x H cells of CELL metres, origin (ox, oy). */
    final int W,H;final float ox,oy;final Cell[] cells;
    final ArrayList<Light> lights=new ArrayList<>();
    final ArrayList<float[]> demonSpawns=new ArrayList<>();
    final ArrayList<Pickup> pickups=new ArrayList<>();
    float spawnX,spawnY,spawnAngle;
    /** Per raster cell: which level lights it can see (bit i = light i). */
    final int[] lightMask;

    /** The first campaign map. */
    Level(){this(Maps.campaign(0));}
    Level(SectorMap m){
        map=m;m.index();
        ox=m.minX()-1;oy=m.minY()-1;W=(int)Math.ceil((m.maxX()-m.minX()+2)/CELL);H=(int)Math.ceil((m.maxY()-m.minY()+2)/CELL);
        cells=new Cell[W*H];
        for(int j=0;j<H;j++)for(int i=0;i<W;i++){Cell c=new Cell();int s=m.sectorAt(cx(i),cy(j));c.sector=s;
            if(s<0){c.solid=true;}else{SectorMap.Sector S=m.sectors.get(s);c.floor=S.floor;c.ceil=S.ceil;c.floorMat=S.floorTex;c.lava=S.special==SectorMap.SP_LAVA||S.floorTex==Tex.LAVA;c.solid=S.ceil-S.floor<.05f;}
            cells[j*W+i]=c;}
        spawnX=m.playerX;spawnY=m.playerY;spawnAngle=m.facing;
        for(SectorMap.Thing t:m.things){
            if(t.type==SectorMap.T_DEMON)demonSpawns.add(new float[]{t.x,t.y,t.angle});
            else if(t.type==SectorMap.T_HEALTH||t.type==SectorMap.T_SHELLS)pickups.add(new Pickup(t.type,t.x,t.y,floorUnder(t.x,t.y,0)));
            else if(t.type>=SectorMap.T_LAMP_WARM&&t.type<=SectorMap.T_LAMP_WHITE&&lights.size()<MAX_LIGHTS){int s=m.sectorAt(t.x,t.y);if(s<0)continue;SectorMap.Sector S=m.sectors.get(s);
                float z=Math.min(S.ceil-.3f,S.floor+3.4f);boolean fl=S.special==SectorMap.SP_FLICKER||t.type==SectorMap.T_LAMP_RED;
                lights.add(new Light(t.x,t.y,z,SectorMap.LAMP_RGB[t.type],t.type==SectorMap.T_LAMP_WHITE?3.6f:3.2f,Math.max(9,Math.min(16,(S.ceil-S.floor)*2.2f)),fl));}}
        // lava glows: one light over every sizeable lava pool, while slots remain
        for(int s=0;s<m.sectors.size()&&lights.size()<MAX_LIGHTS;s++){SectorMap.Sector S=m.sectors.get(s);if(!(S.special==SectorMap.SP_LAVA||S.floorTex==Tex.LAVA))continue;
            if(Math.abs(m.area(S))<4)continue;float[] c=m.centroid(S);lights.add(new Light(c[0],c[1],S.floor+1.1f,0xff5a14,2.4f,8f,true));}
        lightMask=new int[W*H];bakeLights();
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
        FloatList[] out=new FloatList[Tex.COUNT];for(int i=0;i<out.length;i++)out[i]=new FloatList();
        for(int si=0;si<map.sectors.size();si++){SectorMap.Sector s=map.sectors.get(si);float[] tri=map.triangulate(si);
            float tf=tile(s.floorTex),tc=tile(s.ceilTex);
            for(int k=0;k+5<tri.length;k+=6)for(int v=0;v<3;v++){float x=tri[k+v*2],y=tri[k+v*2+1];out[s.floorTex].add(x,y,s.floor,0,0,1,1,0,0,x*tf,y*tf);}
            if(s.ceil-s.floor>.01f)for(int k=0;k+5<tri.length;k+=6)for(int v=2;v>=0;v--){float x=tri[k+v*2],y=tri[k+v*2+1];out[s.ceilTex].add(x,y,s.ceil,0,0,-1,-1,0,0,-x*tc,y*tc);}}
        for(SectorMap.Line L:map.lines){SectorMap.Vertex a=map.vertices.get(L.a),b=map.vertices.get(L.b);
            SectorMap.Sector F=L.front>=0?map.sectors.get(L.front):null,B=L.back>=0?map.sectors.get(L.back):null;
            if(F==null&&B==null)continue;
            int ab=map.wallTexture(L.a,L.b,-1),ba=map.wallTexture(L.b,L.a,-1);   // painted faces (from the 3D editor)
            if(B==null){wall(out,ab>=0?ab:F.wallTex,a.x,a.y,b.x,b.y,F.floor,F.ceil);continue;}
            if(F==null){wall(out,ba>=0?ba:B.wallTex,b.x,b.y,a.x,a.y,B.floor,B.ceil);continue;}
            // steps and ledges below, lintels above; each face looks into the sector it is seen from
            if(B.floor>F.floor+1e-3f)wall(out,ab>=0?ab:B.wallTex,a.x,a.y,b.x,b.y,F.floor,Math.min(B.floor,F.ceil));
            if(F.floor>B.floor+1e-3f)wall(out,ba>=0?ba:F.wallTex,b.x,b.y,a.x,a.y,B.floor,Math.min(F.floor,B.ceil));
            if(B.ceil<F.ceil-1e-3f)wall(out,ab>=0?ab:B.wallTex,a.x,a.y,b.x,b.y,Math.max(B.ceil,F.floor),F.ceil);
            if(F.ceil<B.ceil-1e-3f)wall(out,ba>=0?ba:F.wallTex,b.x,b.y,a.x,a.y,Math.max(F.ceil,B.floor),B.ceil);}
        ArrayList<float[]>[] res=new ArrayList[Tex.COUNT];
        for(int i=0;i<res.length;i++){res[i]=new ArrayList<>();res[i].add(out[i].toArray());}
        return res;
    }
    static final class FloatList{float[] a=new float[1024];int n;void add(float... v){if(n+v.length>a.length)a=java.util.Arrays.copyOf(a,Math.max(a.length*2,n+v.length));System.arraycopy(v,0,a,n,v.length);n+=v.length;}float[] toArray(){return java.util.Arrays.copyOf(a,n);}}
    /** A wall along a→b from z0 up to z1, facing the left side of a→b. */
    private static void wall(FloatList[] out,int mat,float ax,float ay,float bx,float by,float z0,float z1){
        if(z1-z0<1e-3f)return;FloatList o=out[mat];float t=tile(mat);
        float dx=bx-ax,dy=by-ay,l=(float)Math.hypot(dx,dy);if(l<1e-4f)return;
        float nx=-dy/l,ny=dx/l;            // left of a→b
        float tx=ny,ty=-nx;               // tangent = cross(B,N) with B = (0,0,-1)
        float ua=(ax*tx+ay*ty)*t,ub=(bx*tx+by*ty)*t,v0=-z0*t,v1=-z1*t;
        float[][] P={{ax,ay,z0,ua,v0},{bx,by,z0,ub,v0},{bx,by,z1,ub,v1},{ax,ay,z1,ua,v1}};
        int[] idx={0,2,1,0,3,2};
        for(int k:idx)o.add(P[k][0],P[k][1],P[k][2],nx,ny,0,tx,ty,0,P[k][3],P[k][4]);
    }
    static float tile(int mat){switch(mat){case Tex.BRICK:return .5f;case Tex.METAL_PANEL:return .5f;case Tex.FLOOR_TILES:return .33f;case Tex.DIAMOND_PLATE:return .5f;case Tex.CONCRETE:return .4f;
        case Tex.CRATE:return 1f;case Tex.LIGHT:return 1f;case Tex.PILLAR:return .5f;case Tex.LAVA:return .3f;default:return 1f;}}

    // ------------------------------------------------------------------ baked light visibility
    private boolean blocksLight(SectorMap.Line L){
        if(L.front<0||L.back<0)return true;SectorMap.Sector F=map.sectors.get(L.front),B=map.sectors.get(L.back);
        return Math.min(F.ceil,B.ceil)-Math.max(F.floor,B.floor)<.25f;}
    private void bakeLights(){
        ArrayList<float[]> walls=new ArrayList<>();
        for(SectorMap.Line L:map.lines)if(blocksLight(L)){SectorMap.Vertex a=map.vertices.get(L.a),b=map.vertices.get(L.b);walls.add(new float[]{a.x,a.y,b.x,b.y});}
        for(int li=0;li<lights.size()&&li<MAX_LIGHTS;li++){Light L=lights.get(li);
            int i0=Math.max(0,ix(L.x-L.radius-1)),i1=Math.min(W-1,ix(L.x+L.radius+1)),j0=Math.max(0,iy(L.y-L.radius-1)),j1=Math.min(H-1,iy(L.y+L.radius+1));
            ArrayList<float[]> near=new ArrayList<>();for(float[] w:walls)if(SectorMap.segDist(L.x,L.y,w[0],w[1],w[2],w[3])<L.radius+1)near.add(w);
            for(int j=j0;j<=j1;j++)for(int i=i0;i<=i1;i++){Cell c=at(i,j);if(c.solid&&!edgeCell(i,j))continue;
                float x=cx(i),y=cy(j);if(Math.hypot(x-L.x,y-L.y)>L.radius+1)continue;
                float q=CELL*.45f;float[][] pts={{x,y},{x-q,y-q},{x+q,y-q},{x-q,y+q},{x+q,y+q}};
                for(float[] p:pts)if(sees(near,L.x,L.y,p[0],p[1])){lightMask[j*W+i]|=1<<li;break;}}}
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
