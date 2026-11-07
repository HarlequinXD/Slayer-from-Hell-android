package com.harlequin.slayer;

import java.util.ArrayList;

/**
 * The level ("The Molten Keep"): a grid of 1 m cells, each with a floor and ceiling height and materials (like Doom sectors on a
 * grid, rendered in full 3D). Walls, stair risers, ledges and lintels are generated wherever neighbouring cells
 * differ. Also does collision for the player, ray casts for pellets, and works out which lights each cell can see
 * so light doesn't leak through walls (a cheap baked shadow).
 *
 * World space: x east, y north, z up, metres.
 */
final class Level {
    static final class Cell {boolean solid;float floor,ceil;int floorMat,ceilMat,wallMat,sideMat;boolean crate;}
    static final class Light {float x,y,z,r,g,b,radius;boolean flicker;Light(float x,float y,float z,int rgb,float i,float radius,boolean fl){this.x=x;this.y=y;this.z=z;r=((rgb>>16)&255)/255f*i;g=((rgb>>8)&255)/255f*i;b=(rgb&255)/255f*i;this.radius=radius;flicker=fl;}}

    // "The Molten Keep". Legend:
    //  start chamber   '.' tiled floor, 4 m ceiling, brick   'L' its lights   'S' where she starts   '=' corridor
    //  courtyard       ',' cracked tiles, 10 m ceiling        'l' its lights   '_' lava channel   'O' pillar   'c'/'k' crates
    //                  't' terraces (1.5 m) with stairs '1'-'6'
    //  temple hall     'h' (7 m)   'H' lights   'a' the altar dais (1 m) with steps 'w','x','y'
    //  store room      'g' (3.5 m)   'G' light   'e' crate        gate room  'v' (6 m)   'V' lights   '~' lava pool
    //  demons          '@' courtyard   '*' terrace   '&' temple   'A' altar   '$' store   '%' gate room
    static final String[] MAP={
        "############################################################",
        "############################################################",
        "##################vvvvvvvvvvvvvvvv##########################",
        "##################vvvv~~~~~~~~vvvv##########################",
        "##################vv%v~~~~~~~~v%vv##########################",
        "##################vvvv~~~~~~~~vvvv##########################",
        "##################vvvvvvvvvvvvvvvv##########################",
        "##################vVvvvvvvvvvvvvVv##########################",
        "##################vvvvvvvvvvvvvvvv##########################",
        "#########################vv#################################",
        "#########################vv#################################",
        "#########################vv#################################",
        "########ttttt,,,,,,,,,,,,,,,,,,,,,,,,,,ttttt################",
        "########ttttt,,,,,,,O,,,,,,,,,,O,,,,,,,ttttt##hhhhhhhhhhhh##",
        "#gggggg#tt*tt,,,,,,,,,,,,,,@,,,,,,,,,,,tt*tt##hhhhhhhhhhhh##",
        "#geggeg#ttttt654321,,,,,,,,,,,,,,123456ttttt##hhOhhhOhhhOh##",
        "#gggggg#ttttt654321,,,,,,l,,,,,,,123456ttttt##hhhhhhhhhhhh##",
        "#gggggg#ttttt,,,,,,@,,,,,,,,,,,,,,,,,,,ttttt##hhhhHhhhhhhh##",
        "#gggggg#ttttt,,,,,,,,,,,,,,,,,,@,,,,,,,ttttt##hhhhhhaaaaah##",
        "#ggGggg#,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,##hhhhhhaaaaah##",
        "#gggggg#,______,,,_______,,_______,,,______,##hhhwxyaaAaah##",
        "#gggggg#,______,,,_______,,_______,,,______,##hhhwxyaaaaah##",
        "#gg$ggg#,______,,,_______,,_______,,,______,##h&hhhhaaaaah##",
        "#ggggggg,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,##hhhhhhaaaaah##",
        "#geggggg,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,hhhhhhhhhhhhhh##",
        "#gggggg#,,,,O,,,,,,,O,,,@,,,,,,O,,,,,,,O,,,,hhhhhhHhhhhhhh##",
        "########,,,,,,,,,,,,,,,,,l,,,,,,,,,,,@,,,,,,##hhOhhhOhhhOh##",
        "########,,,,,,@,l,,,,,,,,,,,,,,,,,,l,,,,,,,,##hhhhhhhh&hhh##",
        "########,,ccO,,,,,,,,,,,,,,,,,,,,,,,,,,O,,,,##hhhhhhhhhhhh##",
        "########,,k,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,ck,################",
        "########,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,################",
        "#########################==#################################",
        "#########################==#################################",
        "#########################==#################################",
        "#########################==#################################",
        "#########################==#################################",
        "#####################..........#############################",
        "#####################..........#############################",
        "#####################.L......L.#############################",
        "#####################..........#############################",
        "#####################....S.....#############################",
        "#####################..........#############################",
        "############################################################",
        "############################################################",
    };
    final int W,H;final Cell[] cells;final ArrayList<Light> lights=new ArrayList<>();
    float spawnX=3.5f,spawnY=8.5f,spawnAngle=(float)Math.PI/2;
    /** Demon spawn points: x, y, facing. */
    final ArrayList<float[]> demonSpawns=new ArrayList<>();
    static final float STEP=.55f;
    /** Per cell: which of the level lights (at most 13; slots 13-15 are the game's dynamic lights) it can see (bit i = light i). */
    final int[] lightMask;

    Level(){
        H=MAP.length;W=MAP[0].length();cells=new Cell[W*H];
        for(int my=0;my<H;my++){String row=MAP[my];for(int x=0;x<W;x++){char ch=x<row.length()?row.charAt(x):'#';int y=H-1-my;cells[y*W+x]=cell(ch,x,y);}}
        // lava glow: a light every few cells over the pit
        for(int y=0;y<H;y+=4)for(int x=0;x<W;x+=4){int lava=0;float sx=0,sy=0;for(int j=0;j<4;j++)for(int i=0;i<4;i++){Cell c=at(x+i,y+j);if(!c.solid&&c.floorMat==Tex.LAVA){lava++;sx+=x+i+.5f;sy+=y+j+.5f;}}
            if(lava>=8&&lights.size()<13)lights.add(new Light(sx/lava,sy/lava,-.3f,0xff5a14,2.2f,7f,true));}
        lightMask=new int[W*H];bakeLights();
    }
    private Cell cell(char ch,int x,int y){
        Cell c=new Cell();c.wallMat=Tex.BRICK;c.sideMat=Tex.BRICK;c.floorMat=Tex.FLOOR_TILES;c.ceilMat=Tex.CONCRETE;c.floor=0;c.ceil=4;
        switch(ch){
            case '#':c.solid=true;break;
            case 'M':c.solid=true;c.wallMat=c.sideMat=Tex.METAL_PANEL;break;
            case 'O':c.solid=true;c.wallMat=c.sideMat=Tex.PILLAR;break;
            case 'S':spawnX=x+.5f;spawnY=y+.5f;break;
            case '.':break;
            case 'L':c.ceilMat=Tex.LIGHT;light(x,y,3.7f,0xffd8a8,2.6f,9f);break;
            case '=':c.ceil=3.2f;c.floorMat=Tex.DIAMOND_PLATE;c.ceilMat=Tex.METAL_PANEL;c.wallMat=c.sideMat=Tex.METAL_PANEL;break;
            case ',':yard(c);break;
            case '@':yard(c);demon(x,y);break;
            case 'l':yard(c);c.ceilMat=Tex.LIGHT;light(x,y,9.2f,0xffc8a0,4.6f,17f);break;
            case '_':yard(c);c.floor=-1.3f;c.floorMat=Tex.LAVA;c.sideMat=Tex.BRICK;break;
            case 't':yard(c);c.floor=1.5f;c.floorMat=Tex.DIAMOND_PLATE;c.sideMat=Tex.METAL_PANEL;break;
            case '*':yard(c);c.floor=1.5f;c.floorMat=Tex.DIAMOND_PLATE;c.sideMat=Tex.METAL_PANEL;demon(x,y);break;
            case 'c':yard(c);c.crate=true;c.floor=1f;c.floorMat=Tex.CRATE;c.sideMat=Tex.CRATE;break;
            case 'k':yard(c);c.crate=true;c.floor=2f;c.floorMat=Tex.CRATE;c.sideMat=Tex.CRATE;break;
            case 'h':temple(c);break;
            case '&':temple(c);demon(x,y);break;
            case 'H':temple(c);c.ceilMat=Tex.LIGHT;light(x,y,6.7f,0xff7a48,3.6f,13f);break;
            case 'a':temple(c);c.floor=1f;c.floorMat=Tex.METAL_PANEL;c.sideMat=Tex.PILLAR;break;
            case 'A':temple(c);c.floor=1f;c.floorMat=Tex.METAL_PANEL;c.sideMat=Tex.PILLAR;demon(x,y);break;
            case 'w':temple(c);c.floor=.25f;c.sideMat=Tex.PILLAR;break;
            case 'x':temple(c);c.floor=.5f;c.sideMat=Tex.PILLAR;break;
            case 'y':temple(c);c.floor=.75f;c.sideMat=Tex.PILLAR;break;
            case 'g':store(c);break;
            case '$':store(c);demon(x,y);break;
            case 'G':store(c);c.ceilMat=Tex.LIGHT;light(x,y,3.2f,0xc8d8ff,2.2f,8f);break;
            case 'e':store(c);c.crate=true;c.floor=1f;c.floorMat=Tex.CRATE;c.sideMat=Tex.CRATE;break;
            case 'v':gate(c);break;
            case '%':gate(c);demon(x,y);break;
            case 'V':gate(c);c.ceilMat=Tex.LIGHT;light(x,y,5.7f,0xffb070,3f,11f);break;
            case '~':gate(c);c.floor=-1f;c.floorMat=Tex.LAVA;c.sideMat=Tex.BRICK;break;
            default:
                if(ch>='1'&&ch<='8'){yard(c);c.floor=(ch-'0')*.25f;c.floorMat=Tex.DIAMOND_PLATE;c.sideMat=Tex.METAL_PANEL;}
        }
        if(c.solid){c.floor=0;c.ceil=0;}
        return c;
    }
    private void yard(Cell c){c.floor=0;c.ceil=10;c.floorMat=Tex.FLOOR_TILES;c.ceilMat=Tex.METAL_PANEL;c.wallMat=Tex.BRICK;c.sideMat=Tex.BRICK;}
    private void temple(Cell c){c.floor=0;c.ceil=7;c.floorMat=Tex.FLOOR_TILES;c.ceilMat=Tex.CONCRETE;c.wallMat=Tex.BRICK;c.sideMat=Tex.BRICK;}
    private void gate(Cell c){c.floor=0;c.ceil=6;c.floorMat=Tex.DIAMOND_PLATE;c.ceilMat=Tex.METAL_PANEL;c.wallMat=Tex.METAL_PANEL;c.sideMat=Tex.BRICK;}
    private void store(Cell c){c.floor=0;c.ceil=3.5f;c.floorMat=Tex.DIAMOND_PLATE;c.ceilMat=Tex.CONCRETE;c.wallMat=Tex.CONCRETE;c.sideMat=Tex.CONCRETE;}
    private void demon(int x,int y){demonSpawns.add(new float[]{x+.5f,y+.5f,(float)(((x*7+y*13)%12)*Math.PI/6)});}
    private void light(int x,int y,float z,int rgb,float i,float r){if(lights.size()<13)lights.add(new Light(x+.5f,y+.5f,z,rgb,i,r,false));}

    Cell at(int x,int y){if(x<0||y<0||x>=W||y>=H)return SOLID;return cells[y*W+x];}
    private static final Cell SOLID=new Cell();static{SOLID.solid=true;}
    Cell atF(float x,float y){return at((int)Math.floor(x),(int)Math.floor(y));}

    // ------------------------------------------------------------------ geometry
    /** Vertex: x,y,z, normal, tangent, u,v  (11 floats). Returns one float list per material. */
    @SuppressWarnings("unchecked")
    ArrayList<float[]>[] build(){
        FloatList[] out=new FloatList[Tex.COUNT];for(int i=0;i<out.length;i++)out[i]=new FloatList();
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){Cell c=at(x,y);if(c.solid)continue;
            quad(out[c.floorMat],x,y,c.floor,1,1,0,0,1,c.floorMat);                       // floor
            quad(out[c.ceilMat],x,y,c.ceil,1,1,0,0,-1,c.ceilMat);                        // ceiling
            int[][] dirs={{1,0},{-1,0},{0,1},{0,-1}};
            for(int[] d:dirs){Cell n=at(x+d[0],y+d[1]);
                if(n.solid){wall(out[n.wallMat],x,y,d,c.floor,c.ceil,n.wallMat);continue;}
                if(n.floor>c.floor+1e-3f){int m=n.sideMat;wall(out[m],x,y,d,c.floor,Math.min(n.floor,c.ceil),m);}
                if(n.ceil<c.ceil-1e-3f){int m=c.wallMat;wall(out[m],x,y,d,Math.max(n.ceil,c.floor),c.ceil,m);}}
        }
        ArrayList<float[]>[] res=new ArrayList[Tex.COUNT];
        for(int i=0;i<res.length;i++){res[i]=new ArrayList<>();res[i].add(out[i].toArray());}
        return res;
    }
    static final class FloatList{float[] a=new float[1024];int n;void add(float... v){if(n+v.length>a.length)a=java.util.Arrays.copyOf(a,Math.max(a.length*2,n+v.length));System.arraycopy(v,0,a,n,v.length);n+=v.length;}float[] toArray(){return java.util.Arrays.copyOf(a,n);}}
    /** A horizontal 1x1 quad at height z facing up (nz=1) or down (nz=-1). */
    private void quad(FloatList o,int x,int y,float z,float w,float h,float nx,float ny,float nz,int mat){
        float t=tile(mat);float[][] P={{x,y},{x+1,y},{x+1,y+1},{x,y+1}};
        float tx=nz>0?1:-1;   // tangent = cross(B,N) with B = +y: floor (1,0,0), ceiling (-1,0,0)
        int[] idx=nz>0?new int[]{0,1,2,0,2,3}:new int[]{0,2,1,0,3,2};
        for(int k:idx){float px=P[k][0],py=P[k][1];o.add(px,py,z,0,0,nz,tx,0,0,px*tx*t,py*t);}
    }
    /** A wall on the side of cell (x,y) in direction d, from z0 up to z1, facing back into the cell. */
    private void wall(FloatList o,int x,int y,int[] d,float z0,float z1,int mat){
        if(z1-z0<1e-3f)return;float t=tile(mat);
        float nx=-d[0],ny=-d[1];   // normal points into the open cell
        float ax,ay,bx,by;         // the edge's two ends
        if(d[0]==1){ax=x+1;ay=y;bx=x+1;by=y+1;}else if(d[0]==-1){ax=x;ay=y+1;bx=x;by=y;}else if(d[1]==1){ax=x+1;ay=y+1;bx=x;by=y+1;}else{ax=x;ay=y;bx=x+1;by=y;}
        // tangent = cross(B,N) with B = (0,0,-1)  →  (-ny, nx, 0)... computed directly:
        float tx=ny,ty=-nx;   // tangent = cross(B,N) with B = (0,0,-1): (ny,-nx,0)
        float ua=(ax*tx+ay*ty)*t,ub=(bx*tx+by*ty)*t,v0=-z0*t,v1=-z1*t;
        float[][] P={{ax,ay,z0,ua,v0},{bx,by,z0,ub,v0},{bx,by,z1,ub,v1},{ax,ay,z1,ua,v1}};
        // winding: counter-clockwise seen from the open side
        int[] idx={0,2,1,0,3,2};
        for(int k:idx)o.add(P[k][0],P[k][1],P[k][2],nx,ny,0,tx,ty,0,P[k][3],P[k][4]);
    }
    static float tile(int mat){switch(mat){case Tex.BRICK:return .5f;case Tex.METAL_PANEL:return .5f;case Tex.FLOOR_TILES:return .33f;case Tex.DIAMOND_PLATE:return .5f;case Tex.CONCRETE:return .4f;
        case Tex.CRATE:return 1f;case Tex.LIGHT:return 1f;case Tex.PILLAR:return 1f;case Tex.LAVA:return .3f;default:return 1f;}}

    // ------------------------------------------------------------------ baked light visibility
    private void bakeLights(){
        for(int li=0;li<lights.size()&&li<16;li++){Light L=lights.get(li);
            for(int y=0;y<H;y++)for(int x=0;x<W;x++){Cell c=at(x,y);if(c.solid)continue;
                float[][] pts={{x+.5f,y+.5f},{x+.08f,y+.08f},{x+.92f,y+.08f},{x+.08f,y+.92f},{x+.92f,y+.92f}};
                if(Math.hypot(x+.5f-L.x,y+.5f-L.y)>L.radius+1)continue;
                for(float[] p:pts)if(sees(L.x,L.y,p[0],p[1])){lightMask[y*W+x]|=1<<li;break;}}}
    }
    private boolean sees(float x0,float y0,float x1,float y1){
        float d=(float)Math.hypot(x1-x0,y1-y0);int n=Math.max(1,(int)(d/.08f));
        for(int i=1;i<n;i++){float t=i/(float)n;Cell c=atF(x0+(x1-x0)*t,y0+(y1-y0)*t);if(c.solid)return false;}
        return true;
    }

    // ------------------------------------------------------------------ collision
    /** True if an upright box (half-width r, from z to z+h) at (x,y) overlaps a wall or something taller than a step. */
    boolean blocked(float x,float y,float z,float r,float h){
        int x0=(int)Math.floor(x-r),x1=(int)Math.floor(x+r),y0=(int)Math.floor(y-r),y1=(int)Math.floor(y+r);
        for(int cy=y0;cy<=y1;cy++)for(int cx=x0;cx<=x1;cx++){Cell c=at(cx,cy);if(c.solid||c.floor>z+STEP||c.ceil<z+h)return true;}
        return false;
    }
    float floorUnder(float x,float y,float r){
        int x0=(int)Math.floor(x-r),x1=(int)Math.floor(x+r),y0=(int)Math.floor(y-r),y1=(int)Math.floor(y+r);float f=-99;
        for(int cy=y0;cy<=y1;cy++)for(int cx=x0;cx<=x1;cx++){Cell c=at(cx,cy);if(!c.solid)f=Math.max(f,c.floor);}
        return f;
    }
    float ceilOver(float x,float y,float r){
        int x0=(int)Math.floor(x-r),x1=(int)Math.floor(x+r),y0=(int)Math.floor(y-r),y1=(int)Math.floor(y+r);float f=99;
        for(int cy=y0;cy<=y1;cy++)for(int cx=x0;cx<=x1;cx++){Cell c=at(cx,cy);if(!c.solid)f=Math.min(f,c.ceil);}
        return f;
    }
    boolean inside(float x,float y,float z){Cell c=atF(x,y);return c.solid||z<c.floor||z>c.ceil;}

    // ------------------------------------------------------------------ ray cast
    /** Casts a ray; returns {x,y,z, nx,ny,nz, distance, material} or null. */
    float[] ray(float ox,float oy,float oz,float dx,float dy,float dz,float max){
        float step=.04f;float px=ox,py=oy,pz=oz;
        for(float t=step;t<max;t+=step){float x=ox+dx*t,y=oy+dy*t,z=oz+dz*t;
            if(inside(x,y,z)){
                float lo=t-step,hi=t;for(int k=0;k<10;k++){float m=(lo+hi)*.5f;if(inside(ox+dx*m,oy+dy*m,oz+dz*m))hi=m;else lo=m;}
                float hx=ox+dx*lo,hy=oy+dy*lo,hz=oz+dz*lo;Cell from=atF(hx,hy),to=atF(ox+dx*hi,oy+dy*hi);
                float nx=0,ny=0,nz=0;int mat;
                if(!from.solid&&oz+dz*hi<from.floor){nz=1;mat=from.floorMat;}
                else if(!from.solid&&oz+dz*hi>from.ceil){nz=-1;mat=from.ceilMat;}
                else{int fx=(int)Math.floor(hx),fy=(int)Math.floor(hy),tx2=(int)Math.floor(ox+dx*hi),ty2=(int)Math.floor(oy+dy*hi);
                    if(tx2!=fx){nx=tx2>fx?-1:1;}else if(ty2!=fy){ny=ty2>fy?-1:1;}else{nx=-Math.signum(dx);}
                    mat=to.solid?to.wallMat:to.sideMat;}
                return new float[]{hx,hy,hz,nx,ny,nz,lo,mat};}
            px=x;py=y;pz=z;}
        return null;
    }
}
