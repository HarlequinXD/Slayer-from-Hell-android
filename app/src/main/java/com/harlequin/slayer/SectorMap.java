package com.harlequin.slayer;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A Doom-style sector map: shared vertices, sectors as closed vertex loops (each with a floor and ceiling height,
 * textures and a special), and placed things. Sectors may sit inside other sectors (a pillar, a pit, a raised dais):
 * the inner one is a hole in the outer one. Walls are derived from the loops: every edge becomes a line with the
 * sector on each side (or nothing, which makes it a solid wall).
 *
 * World units are metres: x east, y north, z up. Pure Java (no Android), so it runs in the unit tests.
 */
final class SectorMap {
    // sector specials
    static final int SP_NONE=0,SP_LAVA=1,SP_EXIT=2,SP_SECRET=3,SP_FLICKER=4;
    static final String[] SPECIAL_NAMES={"None","Lava (hurts)","Exit","Secret","Flickering lights"};
    // thing types
    static final int T_DEMON=0,T_HEALTH=1,T_SHELLS=2,T_LAMP_WARM=3,T_LAMP_RED=4,T_LAMP_BLUE=5,T_LAMP_WHITE=6,T_PLAYER=7;
    static final String[] THING_NAMES={"Demon","Health orb","Shells","Lamp · warm","Lamp · hellfire","Lamp · cold blue","Lamp · white","Player start"};
    static final int[] LAMP_RGB={0,0,0,0xffc890,0xff5020,0x7aa8ff,0xffffff};
    /** Textures a map may use (indices into Tex). */
    static final int[] MAP_TEXTURES={Tex.BRICK,Tex.METAL_PANEL,Tex.FLOOR_TILES,Tex.DIAMOND_PLATE,Tex.CONCRETE,Tex.CRATE,Tex.LIGHT,Tex.PILLAR,Tex.LAVA};

    static final class Vertex {float x,y;Vertex(float x,float y){this.x=x;this.y=y;}}
    static final class Sector {
        ArrayList<Integer> loop=new ArrayList<>();
        float floor=0,ceil=4;int floorTex=Tex.FLOOR_TILES,ceilTex=Tex.CONCRETE,wallTex=Tex.BRICK,special=SP_NONE,tag=0;
        Sector copyProps(Sector o){floor=o.floor;ceil=o.ceil;floorTex=o.floorTex;ceilTex=o.ceilTex;wallTex=o.wallTex;special=o.special;tag=o.tag;return this;}
    }
    static final class Thing {float x,y,angle;int type;Thing(int type,float x,float y,float angle){this.type=type;this.x=x;this.y=y;this.angle=angle;}}
    /** A wall line: vertices a→b, the sector on its left (front) and right (back); -1 = outside the map (solid). */
    static final class Line {int a,b;int front=-1,back=-1;Line(int a,int b){this.a=a;this.b=b;}}

    String name="Untitled";
    ArrayList<Vertex> vertices=new ArrayList<>();
    ArrayList<Sector> sectors=new ArrayList<>();
    ArrayList<Thing> things=new ArrayList<>();
    float playerX=2,playerY=2,facing=0;

    int addVertex(float x,float y){for(int i=0;i<vertices.size();i++){Vertex v=vertices.get(i);if(Math.abs(v.x-x)<.01f&&Math.abs(v.y-y)<.01f)return i;}vertices.add(new Vertex(x,y));return vertices.size()-1;}

    // ------------------------------------------------------------------ text format
    String save(){
        StringBuilder sb=new StringBuilder("SLAYERMAP 1\n");
        sb.append("name ").append(name.replace('\n',' ')).append('\n');
        sb.append("player ").append(f(playerX)).append(' ').append(f(playerY)).append(' ').append(f(facing)).append('\n');
        for(Vertex v:vertices)sb.append("v ").append(f(v.x)).append(' ').append(f(v.y)).append('\n');
        for(Sector s:sectors){sb.append("s ").append(f(s.floor)).append(' ').append(f(s.ceil)).append(' ').append(s.floorTex).append(' ').append(s.ceilTex).append(' ').append(s.wallTex).append(' ').append(s.special).append(' ').append(s.tag).append(' ').append(s.loop.size());
            for(int i:s.loop)sb.append(' ').append(i);sb.append('\n');}
        for(Thing t:things)sb.append("t ").append(t.type).append(' ').append(f(t.x)).append(' ').append(f(t.y)).append(' ').append(f(t.angle)).append('\n');
        return sb.toString();
    }
    private static String f(float v){if(Math.abs(v-Math.round(v))<1e-5f)return String.valueOf(Math.round(v));return String.valueOf(v);}
    static SectorMap load(String text){
        SectorMap m=new SectorMap();
        for(String raw:text.split("\n")){String line=raw.trim();if(line.isEmpty())continue;
            if(line.startsWith("name ")){m.name=line.substring(5);continue;}
            String[] p=line.split("\\s+");
            switch(p[0]){
                case "player":m.playerX=Float.parseFloat(p[1]);m.playerY=Float.parseFloat(p[2]);m.facing=Float.parseFloat(p[3]);break;
                case "v":m.vertices.add(new Vertex(Float.parseFloat(p[1]),Float.parseFloat(p[2])));break;
                case "s":{Sector s=new Sector();s.floor=Float.parseFloat(p[1]);s.ceil=Float.parseFloat(p[2]);s.floorTex=Integer.parseInt(p[3]);s.ceilTex=Integer.parseInt(p[4]);s.wallTex=Integer.parseInt(p[5]);
                    s.special=Integer.parseInt(p[6]);s.tag=Integer.parseInt(p[7]);int n=Integer.parseInt(p[8]);for(int i=0;i<n;i++)s.loop.add(Integer.parseInt(p[9+i]));m.sectors.add(s);break;}
                case "t":m.things.add(new Thing(Integer.parseInt(p[1]),Float.parseFloat(p[2]),Float.parseFloat(p[3]),Float.parseFloat(p[4])));break;
            }}
        return m;
    }
    SectorMap copy(){return load(save());}

    // ------------------------------------------------------------------ polygon helpers
    float area(Sector s){float a=0;int n=s.loop.size();for(int i=0;i<n;i++){Vertex p=vertices.get(s.loop.get(i)),q=vertices.get(s.loop.get((i+1)%n));a+=p.x*q.y-q.x*p.y;}return a*.5f;}
    boolean contains(Sector s,float x,float y){boolean in=false;int n=s.loop.size();
        for(int i=0,j=n-1;i<n;j=i++){Vertex a=vertices.get(s.loop.get(i)),b=vertices.get(s.loop.get(j));
            if((a.y>y)!=(b.y>y)&&x<(b.x-a.x)*(y-a.y)/(b.y-a.y)+a.x)in=!in;}
        return in;}
    float[] centroid(Sector s){float x=0,y=0;for(int id:s.loop){x+=vertices.get(id).x;y+=vertices.get(id).y;}return new float[]{x/s.loop.size(),y/s.loop.size()};}
    static float segDist(float px,float py,float ax,float ay,float bx,float by){
        float dx=bx-ax,dy=by-ay,l2=dx*dx+dy*dy;float t=l2<1e-12f?0:Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/l2));
        float qx=ax+dx*t-px,qy=ay+dy*t-py;return (float)Math.sqrt(qx*qx+qy*qy);}

    // ------------------------------------------------------------------ derived geometry (rebuilt by index())
    private float minX,minY,maxX,maxY;private static final float BUCKET=2f;private int bw,bh;
    private int[][] sectorBuckets,lineBuckets;private float[] absArea;
    ArrayList<Line> lines=new ArrayList<>();
    /** Each sector's innermost enclosing sector (-1 for top-level ones), and the sectors directly inside it. */
    int[] parent;ArrayList<ArrayList<Integer>> holes=new ArrayList<>();
    float minX(){return minX;}float minY(){return minY;}float maxX(){return maxX;}float maxY(){return maxY;}

    /** Recomputes nesting, lines and the lookup grids. Call after editing. */
    SectorMap index(){
        int ns=sectors.size();absArea=new float[ns];
        minX=minY=1e9f;maxX=maxY=-1e9f;
        for(Vertex v:vertices){minX=Math.min(minX,v.x);minY=Math.min(minY,v.y);maxX=Math.max(maxX,v.x);maxY=Math.max(maxY,v.y);}
        if(vertices.isEmpty()){minX=minY=0;maxX=maxY=1;}
        for(int i=0;i<ns;i++)absArea[i]=Math.abs(area(sectors.get(i)));
        bw=Math.max(1,(int)Math.ceil((maxX-minX)/BUCKET)+1);bh=Math.max(1,(int)Math.ceil((maxY-minY)/BUCKET)+1);
        ArrayList<ArrayList<Integer>> sb=new ArrayList<>();for(int i=0;i<bw*bh;i++)sb.add(new ArrayList<>());
        for(int i=0;i<ns;i++){float[] bb=bbox(sectors.get(i));for(int y=by(bb[1]);y<=by(bb[3]);y++)for(int x=bx(bb[0]);x<=bx(bb[2]);x++)sb.get(y*bw+x).add(i);}
        sectorBuckets=toArrays(sb);
        // nesting: the smallest sector that contains every vertex of this one
        parent=new int[ns];holes.clear();for(int i=0;i<ns;i++)holes.add(new ArrayList<>());
        for(int i=0;i<ns;i++){parent[i]=-1;float best=1e30f;Sector s=sectors.get(i);
            for(int j=0;j<ns;j++){if(j==i||absArea[j]<=absArea[i]+1e-6f||absArea[j]>=best)continue;if(insideOrOn(sectors.get(j),s))  {best=absArea[j];parent[i]=j;}}}
        for(int i=0;i<ns;i++)if(parent[i]>=0)holes.get(parent[i]).add(i);
        // lines: every distinct edge; the sectors on each side are found geometrically (innermost sector just off
        // the middle of the edge), which handles shared edges, holes, and platforms touching their room's wall alike
        lines.clear();java.util.HashSet<Long> seen=new java.util.HashSet<>();
        for(int i=0;i<ns;i++){Sector s=sectors.get(i);int n=s.loop.size();
            for(int k=0;k<n;k++){int a=s.loop.get(k),b=s.loop.get((k+1)%n);if(a==b)continue;long key=a<b?((long)a<<32)|b:((long)b<<32)|a;
                if(!seen.add(key))continue;
                Vertex va=vertices.get(a),vb=vertices.get(b);float mx=(va.x+vb.x)*.5f,my=(va.y+vb.y)*.5f,dx=vb.x-va.x,dy=vb.y-va.y,l=(float)Math.hypot(dx,dy);if(l<1e-5f)continue;
                float lx=-dy/l*.01f,ly=dx/l*.01f;
                int left=sectorAt(mx+lx,my+ly),right=sectorAt(mx-lx,my-ly);
                if(left==right)continue;   // no change across it (an edge buried inside one sector)
                Line L=left>=0?new Line(a,b):new Line(b,a);L.front=left>=0?left:right;L.back=left>=0?right:-1;lines.add(L);}}
        ArrayList<ArrayList<Integer>> lb=new ArrayList<>();for(int i=0;i<bw*bh;i++)lb.add(new ArrayList<>());
        for(int i=0;i<lines.size();i++){Line L=lines.get(i);Vertex a=vertices.get(L.a),b=vertices.get(L.b);
            for(int y=by(Math.min(a.y,b.y)-.5f);y<=by(Math.max(a.y,b.y)+.5f);y++)for(int x=bx(Math.min(a.x,b.x)-.5f);x<=bx(Math.max(a.x,b.x)+.5f);x++)lb.get(y*bw+x).add(i);}
        lineBuckets=toArrays(lb);
        return this;
    }
    private static int[][] toArrays(ArrayList<ArrayList<Integer>> l){int[][] r=new int[l.size()][];for(int i=0;i<r.length;i++){ArrayList<Integer> a=l.get(i);r[i]=new int[a.size()];for(int k=0;k<r[i].length;k++)r[i][k]=a.get(k);}return r;}
    private int bx(float x){return Math.max(0,Math.min(bw-1,(int)Math.floor((x-minX)/BUCKET)));}
    private int by(float y){return Math.max(0,Math.min(bh-1,(int)Math.floor((y-minY)/BUCKET)));}
    float[] bbox(Sector s){float[] b={1e9f,1e9f,-1e9f,-1e9f};for(int id:s.loop){Vertex v=vertices.get(id);b[0]=Math.min(b[0],v.x);b[1]=Math.min(b[1],v.y);b[2]=Math.max(b[2],v.x);b[3]=Math.max(b[3],v.y);}return b;}
    /** True if every vertex and edge midpoint of `in` lies inside or on `out`. */
    boolean insideOrOn(Sector out,Sector in){
        int n=in.loop.size();
        for(int k=0;k<n;k++){Vertex a=vertices.get(in.loop.get(k)),b=vertices.get(in.loop.get((k+1)%n));
            if(!containsOrOn(out,a.x,a.y)||!containsOrOn(out,(a.x+b.x)*.5f,(a.y+b.y)*.5f))return false;}
        return true;
    }
    boolean containsOrOn(Sector s,float x,float y){if(contains(s,x,y))return true;int n=s.loop.size();
        for(int k=0;k<n;k++){Vertex a=vertices.get(s.loop.get(k)),b=vertices.get(s.loop.get((k+1)%n));if(segDist(x,y,a.x,a.y,b.x,b.y)<.002f)return true;}return false;}

    /** The innermost sector containing (x,y), or -1 (outside the map: solid). Needs index(). */
    int sectorAt(float x,float y){return sectorAtExcluding(x,y,-1);}
    private int sectorAtExcluding(float x,float y,int skip){
        if(sectorBuckets==null||x<minX-1||y<minY-1||x>maxX+1||y>maxY+1)return -1;
        int best=-1;float ba=1e30f;
        for(int i:sectorBuckets[by(y)*bw+bx(x)]){if(i==skip||absArea[i]>=ba)continue;if(contains(sectors.get(i),x,y)){best=i;ba=absArea[i];}}
        return best;
    }
    /** Indices of lines whose bucket covers (x,y) (candidates for anything within ~0.5 m). */
    int[] linesNear(float x,float y){if(lineBuckets==null||x<minX-1||y<minY-1||x>maxX+1||y>maxY+1)return new int[0];return lineBuckets[by(y)*bw+bx(x)];}

    // ------------------------------------------------------------------ triangulation (ear clipping, holes bridged in)
    /**
     * Triangles {x0,y0,x1,y1,x2,y2,...} covering the sector minus the sectors nested directly inside it. Holes may
     * touch the sector's walls or each other: edges shared between the sector and its holes cancel out, the remaining
     * boundary is traced into loops (region always on the left), holes are bridged into their outer loop, then the
     * result is ear-clipped.
     */
    float[] triangulate(int si){
        Sector s=sectors.get(si);
        java.util.HashMap<Long,int[]> edges=new java.util.HashMap<>();   // directed edges, keyed (a,b)
        addLoop(edges,s.loop,area(s)>0);
        for(int h:holes.get(si)){Sector hs=sectors.get(h);addLoop(edges,hs.loop,area(hs)<0);}   // holes run clockwise
        // trace loops with the region on the left: at a vertex take the outgoing edge turning most to the left
        java.util.HashMap<Integer,ArrayList<Integer>> out=new java.util.HashMap<>();
        for(int[] e:edges.values())out.computeIfAbsent(e[0],k->new ArrayList<>()).add(e[1]);
        java.util.HashSet<Long> used=new java.util.HashSet<>();
        ArrayList<ArrayList<float[]>> outers=new ArrayList<>(),inners=new ArrayList<>();
        for(int[] e0:new ArrayList<>(edges.values())){if(used.contains(key(e0[0],e0[1])))continue;
            ArrayList<Integer> loop=new ArrayList<>();int u=e0[0],v=e0[1];used.add(key(u,v));loop.add(u);
            for(int guard=0;v!=e0[0]&&guard<10000;guard++){loop.add(v);
                ArrayList<Integer> cand=out.get(v);if(cand==null)break;int best=-1;double bd=-1;
                Vertex pv=vertices.get(v),pu=vertices.get(u);double back=Math.atan2(pu.y-pv.y,pu.x-pv.x);
                for(int w:cand){if(used.contains(key(v,w)))continue;Vertex pw=vertices.get(w);double d=Math.atan2(pw.y-pv.y,pw.x-pv.x)-back;
                    while(d<=1e-9)d+=2*Math.PI;while(d>2*Math.PI+1e-9)d-=2*Math.PI;if(d>bd){bd=d;best=w;}}
                if(best<0)break;used.add(key(v,best));u=v;v=best;}
            if(loop.size()<3)continue;
            ArrayList<float[]> p=new ArrayList<>();for(int id:loop){Vertex q=vertices.get(id);p.add(new float[]{q.x,q.y});}
            if(signed(p)>0)outers.add(p);else inners.add(p);}
        // each inner loop belongs to the smallest outer loop around it
        ArrayList<ArrayList<ArrayList<float[]>>> holeOf=new ArrayList<>();for(int i=0;i<outers.size();i++)holeOf.add(new ArrayList<>());
        for(ArrayList<float[]> in:inners){float[] a=in.get(0),b=in.get(1);float mx=(a[0]+b[0])*.5f,my=(a[1]+b[1])*.5f,dx=b[0]-a[0],dy=b[1]-a[1],l=(float)Math.hypot(dx,dy);
            float px=mx-dy/l*.002f,py=my+dx/l*.002f;int best=-1;float ba=1e30f;
            for(int i=0;i<outers.size();i++){ArrayList<float[]> o=outers.get(i);float ar=signed(o);if(ar<ba&&inside(o,px,py)){ba=ar;best=i;}}
            if(best>=0)holeOf.get(best).add(in);}
        Level.FloatList tris=new Level.FloatList();
        for(int i=0;i<outers.size();i++){ArrayList<float[]> poly=outers.get(i);ArrayList<ArrayList<float[]>> hs=holeOf.get(i);
            hs.sort((p,q)->Float.compare(maxXOf(q),maxXOf(p)));
            for(ArrayList<float[]> hole:hs)poly=bridge(poly,hole);
            tris.add(earClip(poly));}
        return tris.toArray();
    }
    private static long key(int a,int b){return ((long)a<<32)|(b&0xffffffffL);}
    private void addLoop(java.util.HashMap<Long,int[]> edges,ArrayList<Integer> loop,boolean forward){
        int n=loop.size();
        for(int k=0;k<n;k++){int a=loop.get(k),b=loop.get((k+1)%n);if(!forward){int t=a;a=b;b=t;}if(a==b)continue;
            if(edges.remove(key(b,a))!=null)continue;   // shared with a neighbour going the other way: not a boundary here
            edges.put(key(a,b),new int[]{a,b});}
    }
    private static float signed(ArrayList<float[]> p){float a=0;for(int i=0;i<p.size();i++){float[] u=p.get(i),w=p.get((i+1)%p.size());a+=u[0]*w[1]-w[0]*u[1];}return a*.5f;}
    private static boolean inside(ArrayList<float[]> poly,float x,float y){boolean r=false;for(int i=0,j=poly.size()-1;i<poly.size();j=i++){float[] a=poly.get(i),b=poly.get(j);
        if((a[1]>y)!=(b[1]>y)&&x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0])r=!r;}return r;}
    private ArrayList<float[]> pts(Sector s,boolean ccw){ArrayList<float[]> p=new ArrayList<>();for(int id:s.loop){Vertex v=vertices.get(id);p.add(new float[]{v.x,v.y});}
        float a=0;for(int i=0;i<p.size();i++){float[] u=p.get(i),w=p.get((i+1)%p.size());a+=u[0]*w[1]-w[0]*u[1];}
        if((a>0)!=ccw)java.util.Collections.reverse(p);return p;}
    private static float maxXOf(ArrayList<float[]> p){float m=-1e9f;for(float[] q:p)m=Math.max(m,q[0]);return m;}
    /** Joins a hole (clockwise) into the outer polygon (counter-clockwise) with a two-way bridge edge. */
    private static ArrayList<float[]> bridge(ArrayList<float[]> outer,ArrayList<float[]> hole){
        int hi=0;for(int i=1;i<hole.size();i++)if(hole.get(i)[0]>hole.get(hi)[0])hi=i;
        float[] h=hole.get(hi);int best=-1;float bd=1e30f;
        for(int i=0;i<outer.size();i++){float[] o=outer.get(i);float d=(o[0]-h[0])*(o[0]-h[0])+(o[1]-h[1])*(o[1]-h[1]);if(d>=bd)continue;
            if(visible(outer,hole,h,o))  {bd=d;best=i;}}
        if(best<0)best=0;
        ArrayList<float[]> r=new ArrayList<>();
        for(int i=0;i<=best;i++)r.add(outer.get(i));
        for(int k=0;k<=hole.size();k++)r.add(hole.get((hi+k)%hole.size()));
        r.add(outer.get(best));
        for(int i=best+1;i<outer.size();i++)r.add(outer.get(i));
        return r;
    }
    private static boolean visible(ArrayList<float[]> outer,ArrayList<float[]> hole,float[] a,float[] b){
        for(ArrayList<float[]> poly:java.util.Arrays.asList(outer,hole))for(int i=0;i<poly.size();i++){float[] p=poly.get(i),q=poly.get((i+1)%poly.size());
            if(same(p,a)||same(p,b)||same(q,a)||same(q,b))continue;if(cross(a,b,p,q))return false;}
        return true;
    }
    private static boolean same(float[] a,float[] b){return Math.abs(a[0]-b[0])<1e-5f&&Math.abs(a[1]-b[1])<1e-5f;}
    private static float orient(float[] a,float[] b,float[] c){return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);}
    private static boolean cross(float[] a,float[] b,float[] c,float[] d){float o1=orient(a,b,c),o2=orient(a,b,d),o3=orient(c,d,a),o4=orient(c,d,b);return o1*o2<0&&o3*o4<0;}
    private static float[] earClip(ArrayList<float[]> poly){
        ArrayList<float[]> rem=new ArrayList<>(poly);float[] out=new float[Math.max(0,(rem.size()-2)*6)];int k=0;int guard=0;
        while(rem.size()>3&&guard++<10000){boolean found=false;int n=rem.size();
            for(int i=0;i<n;i++){float[] a=rem.get((i+n-1)%n),b=rem.get(i),c=rem.get((i+1)%n);
                if(orient(a,b,c)<=1e-7f)continue;
                boolean inside=false;
                for(int j=0;j<n&&!inside;j++){float[] p=rem.get(j);if(p==a||p==b||p==c||same(p,a)||same(p,b)||same(p,c))continue;
                    if(orient(a,b,p)>=-1e-7f&&orient(b,c,p)>=-1e-7f&&orient(c,a,p)>=-1e-7f)inside=true;}
                if(inside)continue;
                if(k+6<=out.length){out[k++]=a[0];out[k++]=a[1];out[k++]=b[0];out[k++]=b[1];out[k++]=c[0];out[k++]=c[1];}
                rem.remove(i);found=true;break;}
            if(!found){   // degenerate leftovers: drop a collinear point and carry on
                for(int i=0;i<rem.size();i++){int m=rem.size();if(Math.abs(orient(rem.get((i+m-1)%m),rem.get(i),rem.get((i+1)%m)))<1e-6f){rem.remove(i);found=true;break;}}
                if(!found)break;}}
        if(rem.size()==3&&orient(rem.get(0),rem.get(1),rem.get(2))>0&&k+6<=out.length){for(float[] p:rem){out[k++]=p[0];out[k++]=p[1];}}
        return java.util.Arrays.copyOf(out,k);
    }
}
