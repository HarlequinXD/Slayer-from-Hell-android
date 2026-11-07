package com.harlequin.slayer;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * Validated, undoable sector and entity operations (after RPG Pocket Editor's SectorEditor). Every change is
 * checked first (simple polygons, no crossing walls, no partial overlaps: a sector is either beside another or
 * wholly inside it, which makes it a pillar, pit or platform) and recorded for undo. Coordinates are metres.
 * Pure Java.
 */
final class MapEditor {
    SectorMap map;
    private final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private static final float EPS=.0001f;
    String error="";

    MapEditor(SectorMap m){map=m;map.index();}
    private void push(){undo.push(map.save());if(undo.size()>80)undo.removeLast();redo.clear();}
    boolean canUndo(){return !undo.isEmpty();}boolean canRedo(){return !redo.isEmpty();}
    boolean undo(){if(undo.isEmpty())return false;redo.push(map.save());restore(undo.pop());return true;}
    boolean redo(){if(redo.isEmpty())return false;undo.push(map.save());restore(redo.pop());return true;}
    private void restore(String s){SectorMap m=SectorMap.load(s);map.vertices=m.vertices;map.sectors=m.sectors;map.things=m.things;map.playerX=m.playerX;map.playerY=m.playerY;map.facing=m.facing;map.name=m.name;map.wallTex=m.wallTex;map.index();}
    private void done(){map.index();}

    static float snap(float n,float step){return Math.round(n/step)*step;}
    /** Snaps to the grid, or onto a nearby existing vertex that sits on the grid. */
    float[] snapPoint(float x,float y,float grid){
        float sx=snap(x,grid),sy=snap(y,grid),best=.42f*grid;
        for(SectorMap.Vertex v:map.vertices){float d=(float)Math.hypot(v.x-x,v.y-y);if(d<best){best=d;sx=v.x;sy=v.y;}}
        return new float[]{sx,sy};
    }

    // ------------------------------------------------------------------ geometry tests
    private static float cross(float ax,float ay,float bx,float by,float cx,float cy){return (bx-ax)*(cy-ay)-(by-ay)*(cx-ax);}
    private static boolean onSegment(float ax,float ay,float bx,float by,float px,float py){return Math.abs(cross(ax,ay,bx,by,px,py))<EPS*Math.max(1,(float)Math.hypot(bx-ax,by-ay))&&px>=Math.min(ax,bx)-EPS&&px<=Math.max(ax,bx)+EPS&&py>=Math.min(ay,by)-EPS&&py<=Math.max(ay,by)+EPS;}
    private static boolean intersects(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){
        if(properCross(ax,ay,bx,by,cx,cy,dx,dy))return true;
        return onSegment(ax,ay,bx,by,cx,cy)||onSegment(ax,ay,bx,by,dx,dy)||onSegment(cx,cy,dx,dy,ax,ay)||onSegment(cx,cy,dx,dy,bx,by);
    }
    private static boolean properCross(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy){
        float a=cross(ax,ay,bx,by,cx,cy),b=cross(ax,ay,bx,by,dx,dy),c=cross(cx,cy,dx,dy,ax,ay),d=cross(cx,cy,dx,dy,bx,by);
        return (a>EPS&&b<-EPS||a<-EPS&&b>EPS)&&(c>EPS&&d<-EPS||c<-EPS&&d>EPS);
    }
    static boolean inPoly(ArrayList<float[]> poly,float x,float y){boolean r=false;for(int i=0,j=poly.size()-1;i<poly.size();j=i++){float[] a=poly.get(i),b=poly.get(j);
        if((a[1]>y)!=(b[1]>y)&&x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0])r=!r;}return r;}
    private static boolean onPoly(ArrayList<float[]> poly,float x,float y){for(int i=0;i<poly.size();i++){float[] a=poly.get(i),b=poly.get((i+1)%poly.size());if(onSegment(a[0],a[1],b[0],b[1],x,y))return true;}return false;}
    /** Every vertex and edge midpoint of `in` inside or on `out`. */
    private static boolean within(ArrayList<float[]> out,ArrayList<float[]> in){
        for(int i=0;i<in.size();i++){float[] a=in.get(i),b=in.get((i+1)%in.size());float mx=(a[0]+b[0])*.5f,my=(a[1]+b[1])*.5f;
            if(!(onPoly(out,a[0],a[1])||inPoly(out,a[0],a[1])))return false;if(!(onPoly(out,mx,my)||inPoly(out,mx,my)))return false;}
        return true;
    }
    /** True if any vertex or edge midpoint of `p` is strictly inside `q`. */
    private static boolean pokesInto(ArrayList<float[]> p,ArrayList<float[]> q){
        for(int i=0;i<p.size();i++){float[] a=p.get(i),b=p.get((i+1)%p.size());float mx=(a[0]+b[0])*.5f,my=(a[1]+b[1])*.5f;
            if(inPoly(q,a[0],a[1])&&!onPoly(q,a[0],a[1]))return true;if(inPoly(q,mx,my)&&!onPoly(q,mx,my))return true;}
        return false;
    }
    ArrayList<float[]> coords(SectorMap.Sector s){ArrayList<float[]> a=new ArrayList<>();for(int id:s.loop){if(id<0||id>=map.vertices.size())return new ArrayList<>();SectorMap.Vertex v=map.vertices.get(id);a.add(new float[]{v.x,v.y});}return a;}
    private static float area(ArrayList<float[]> p){float a=0;for(int i=0;i<p.size();i++){float[] u=p.get(i),w=p.get((i+1)%p.size());a+=u[0]*w[1]-w[0]*u[1];}return a*.5f;}

    /** Checks a polygon against itself and every other sector (skip: one to ignore, e.g. the one being changed). */
    boolean validate(ArrayList<float[]> poly,int skip){return validate(poly,skip,-1);}
    private boolean validate(ArrayList<float[]> poly,int skip,int skip2){
        error="";int n=poly.size();if(n<3){error="Need at least three corners";return false;}
        for(int i=0;i<n;i++){float[] a=poly.get(i),b=poly.get((i+1)%n);if(!Float.isFinite(a[0])||!Float.isFinite(a[1])){error="Invalid coordinates";return false;}
            if(Math.hypot(a[0]-b[0],a[1]-b[1])<.08){error="Zero-length edge";return false;}}
        if(Math.abs(area(poly))<.02){error="Sector has no area";return false;}
        for(int i=0;i<n;i++)for(int j=i+1;j<n;j++){if(j==i+1||(i==0&&j==n-1))continue;
            float[] a=poly.get(i),b=poly.get((i+1)%n),c=poly.get(j),d=poly.get((j+1)%n);
            if(intersects(a[0],a[1],b[0],b[1],c[0],c[1],d[0],d[1])){error="Sector edges intersect";return false;}}
        for(int k=0;k<map.sectors.size();k++){if(k==skip||k==skip2)continue;ArrayList<float[]> o=coords(map.sectors.get(k));if(o.size()<3)continue;
            for(int i=0;i<n;i++)for(int j=0;j<o.size();j++){float[] a=poly.get(i),b=poly.get((i+1)%n),c=o.get(j),d=o.get((j+1)%o.size());
                if(properCross(a[0],a[1],b[0],b[1],c[0],c[1],d[0],d[1])){error="Crosses an existing wall";return false;}}
            boolean inO=within(o,poly),oIn=within(poly,o);
            if(inO&&oIn){error="Same shape as an existing sector";return false;}
            if(inO||oIn)continue;   // nested: a pillar, pit or platform (or a room drawn round one)
            if(pokesInto(poly,o)||pokesInto(o,poly)){error="Overlaps another sector";return false;}}
        return true;
    }

    // ------------------------------------------------------------------ sector operations
    SectorMap.Sector addSector(ArrayList<float[]> pts){
        if(!validate(pts,-1))return null;push();
        SectorMap.Sector s=new SectorMap.Sector();
        int host=hostOf(pts);if(host>=0){SectorMap.Sector h=map.sectors.get(host);s.copyProps(h);if(s.special==SectorMap.SP_EXIT)s.special=0;}   // drawn inside a room: start from its heights and textures
        for(float[] p:pts)s.loop.add(map.addVertex(p[0],p[1]));map.sectors.add(s);normalizeEdges();done();return s;
    }
    /** The innermost existing sector that wholly contains the polygon, or -1. */
    private int hostOf(ArrayList<float[]> pts){int best=-1;float ba=1e30f;for(int k=0;k<map.sectors.size();k++){ArrayList<float[]> o=coords(map.sectors.get(k));float a=Math.abs(area(o));if(a<ba&&within(o,pts)){ba=a;best=k;}}return best;}
    SectorMap.Sector rectangle(float x1,float y1,float x2,float y2){
        if(Math.abs(x1-x2)<.01f||Math.abs(y1-y2)<.01f){error="Rectangle needs width and height";return null;}
        ArrayList<float[]> p=new ArrayList<>();float l=Math.min(x1,x2),r=Math.max(x1,x2),b=Math.min(y1,y2),t=Math.max(y1,y2);
        p.add(new float[]{l,b});p.add(new float[]{r,b});p.add(new float[]{r,t});p.add(new float[]{l,t});
        return addSector(p);
    }
    boolean deleteSector(int i){if(i<0||i>=map.sectors.size())return false;push();map.sectors.remove(i);done();return true;}
    boolean changeHeight(int i,float df,float dc){if(i<0||i>=map.sectors.size())return false;SectorMap.Sector s=map.sectors.get(i);
        if(s.ceil+dc-s.floor-df<0){error="The ceiling can't go below the floor";return false;}push();s.floor+=df;s.ceil+=dc;done();return true;}
    boolean setHeights(int i,float f,float c){if(i<0||i>=map.sectors.size()||c<f){error="Invalid floor/ceiling";return false;}push();SectorMap.Sector s=map.sectors.get(i);s.floor=f;s.ceil=c;done();return true;}
    /** surface: 0 walls, 1 floor, 2 ceiling. */
    boolean setTexture(int i,int surface,int tex){if(i<0||i>=map.sectors.size())return false;push();SectorMap.Sector s=map.sectors.get(i);
        if(surface==0)s.wallTex=tex;else if(surface==1)s.floorTex=tex;else s.ceilTex=tex;done();return true;}
    /** Paints one wall face (along a→b, facing its left side); tex < 0 goes back to the sector's wall texture. */
    boolean setWallTexture(int a,int b,int tex){push();if(tex<0)map.wallTex.remove(SectorMap.wallKey(a,b));else map.wallTex.put(SectorMap.wallKey(a,b),tex);done();return true;}
    boolean setSpecial(int i,int sp,int tag){if(i<0||i>=map.sectors.size())return false;push();SectorMap.Sector s=map.sectors.get(i);s.special=sp;s.tag=tag;
        if(sp==SectorMap.SP_LAVA)s.floorTex=Tex.LAVA;done();return true;}
    /** A solid column: the sector rises to its ceiling (Doom-style pillar). */
    boolean makePillar(int i){if(i<0||i>=map.sectors.size())return false;push();SectorMap.Sector s=map.sectors.get(i);s.floor=s.ceil;if(s.wallTex==Tex.BRICK)s.wallTex=Tex.PILLAR;done();return true;}

    boolean moveVertices(int[] ids,float dx,float dy){
        error="";if(ids.length==0||(dx==0&&dy==0))return false;
        float[] old=new float[ids.length*2];
        for(int i=0;i<ids.length;i++){if(ids[i]<0||ids[i]>=map.vertices.size())return false;SectorMap.Vertex v=map.vertices.get(ids[i]);old[i*2]=v.x;old[i*2+1]=v.y;v.x+=dx;v.y+=dy;}
        boolean valid=true;
        for(int i=0;i<map.sectors.size()&&valid;i++){SectorMap.Sector s=map.sectors.get(i);boolean touched=false;for(int id:ids)if(s.loop.contains(id)){touched=true;break;}
            if(touched&&!validateTouched(i,ids))valid=false;}
        for(int i=0;i<ids.length;i++){SectorMap.Vertex v=map.vertices.get(ids[i]);v.x=old[i*2];v.y=old[i*2+1];}
        if(!valid)return false;
        push();for(int id:ids){SectorMap.Vertex v=map.vertices.get(id);v.x+=dx;v.y+=dy;}normalizeEdges();done();return true;
    }
    /** Validates sector i against the others, ignoring sectors that move along with it (they share the moved vertices). */
    private boolean validateTouched(int i,int[] ids){
        ArrayList<float[]> poly=coords(map.sectors.get(i));
        // sectors sharing every moved vertex move with it; checking against them is still right (they move too)
        return validate(poly,i);
    }
    boolean splitWall(int sector,int edge,float x,float y){
        if(sector<0||sector>=map.sectors.size())return false;SectorMap.Sector s=map.sectors.get(sector);if(edge<0||edge>=s.loop.size())return false;
        SectorMap.Vertex a=map.vertices.get(s.loop.get(edge)),b=map.vertices.get(s.loop.get((edge+1)%s.loop.size()));
        if(!onSegment(a.x,a.y,b.x,b.y,x,y)||Math.hypot(x-a.x,y-a.y)<.08||Math.hypot(x-b.x,y-b.y)<.08){error="Point must lie on the wall";return false;}
        push();map.addVertex(x,y);normalizeEdges();done();return true;
    }
    boolean duplicateSector(int i,float dx,float dy){
        if(i<0||i>=map.sectors.size())return false;SectorMap.Sector s=map.sectors.get(i);ArrayList<float[]> poly=coords(s);for(float[] p:poly){p[0]+=dx;p[1]+=dy;}
        if(!validate(poly,-1))return false;
        push();SectorMap.Sector c=new SectorMap.Sector().copyProps(s);for(float[] p:poly)c.loop.add(map.addVertex(p[0],p[1]));map.sectors.add(c);normalizeEdges();done();return true;
    }
    boolean extrude(int sector,int edge,float distance){
        if(sector<0||sector>=map.sectors.size()||distance==0)return false;
        SectorMap.Sector s=map.sectors.get(sector);if(edge<0||edge>=s.loop.size())return false;
        SectorMap.Vertex a=map.vertices.get(s.loop.get(edge)),b=map.vertices.get(s.loop.get((edge+1)%s.loop.size()));
        float dx=b.x-a.x,dy=b.y-a.y,len=(float)Math.hypot(dx,dy);if(len<.001)return false;
        float sign=map.area(s)>0?1:-1,nx=sign*dy/len*distance,ny=-sign*dx/len*distance;
        ArrayList<float[]> poly=new ArrayList<>();
        poly.add(new float[]{b.x,b.y});poly.add(new float[]{a.x,a.y});poly.add(new float[]{a.x+nx,a.y+ny});poly.add(new float[]{b.x+nx,b.y+ny});
        SectorMap.Sector c=addSector(poly);if(c==null)return false;c.copyProps(s);if(c.special==SectorMap.SP_EXIT)c.special=0;done();return true;
    }
    /** Splits an axis-aligned rectangular sector into steps rising away from (fromX,fromY). */
    boolean makeStairs(int index,float fromX,float fromY,float rise,float depth){
        error="";if(index<0||index>=map.sectors.size())return false;
        SectorMap.Sector s=map.sectors.get(index);float[] bb=map.bbox(s);float minX=bb[0],minY=bb[1],maxX=bb[2],maxY=bb[3];
        if(Math.abs(Math.abs(map.area(s))-(maxX-minX)*(maxY-minY))>.01f){error="Stairs need a rectangular sector";return false;}
        float cx=(minX+maxX)*.5f,cy=(minY+maxY)*.5f;   // rise along the axis the cursor sits off-centre on (the longer one if it's central)
        float ox=Math.abs(fromX-cx)/Math.max(.01f,maxX-minX),oy=Math.abs(fromY-cy)/Math.max(.01f,maxY-minY);
        boolean alongX=Math.abs(ox-oy)<.05f?maxX-minX>=maxY-minY:ox>oy;float span=alongX?maxX-minX:maxY-minY;int n=Math.max(1,Math.round(span/depth));
        if(n<2){error="Sector is too small for stairs";return false;}
        boolean reverse=alongX?Math.abs(fromX-maxX)<Math.abs(fromX-minX):Math.abs(fromY-maxY)<Math.abs(fromY-minY);
        push();SectorMap.Sector t=s;map.sectors.remove(index);float d=span/n;
        for(int i=0;i<n;i++){float a=(alongX?minX:minY)+i*d,b=i==n-1?(alongX?maxX:maxY):a+d;
            float[][] q=alongX?new float[][]{{a,minY},{b,minY},{b,maxY},{a,maxY}}:new float[][]{{minX,a},{maxX,a},{maxX,b},{minX,b}};
            SectorMap.Sector st=new SectorMap.Sector().copyProps(t);for(float[] p:q)st.loop.add(map.addVertex(p[0],p[1]));
            int k=reverse?n-1-i:i;st.floor=t.floor+rise*(k+1);st.ceil=Math.max(t.ceil,st.floor+2.2f);if(st.special==SectorMap.SP_EXIT)st.special=0;map.sectors.add(st);}
        normalizeEdges();done();return true;
    }
    /**
     * The Line tool: cuts a sector in two along the line p→q (both on its boundary, the line inside it).
     * Corners are added where the ends fall in the middle of walls.
     */
    boolean splitSector(float px,float py,float qx,float qy){
        error="";if(Math.hypot(px-qx,py-qy)<.08){error="The line needs length";return false;}
        float mx=(px+qx)*.5f,my=(py+qy)*.5f;int si=map.sectorAt(mx,my);if(si<0){error="The line must run across a sector";return false;}
        ArrayList<float[]> poly=coords(map.sectors.get(si));
        if(!onPoly(poly,px,py)||!onPoly(poly,qx,qy)){error="Both ends must be on the sector's walls";return false;}
        for(int i=0;i<poly.size();i++){float[] a=poly.get(i),b=poly.get((i+1)%poly.size());if(properCross(px,py,qx,qy,a[0],a[1],b[0],b[1])){error="The line crosses a wall";return false;}}
        for(int h:map.holes.get(si)){ArrayList<float[]> hp=coords(map.sectors.get(h));for(int i=0;i<hp.size();i++){float[] a=hp.get(i),b=hp.get((i+1)%hp.size());
            if(intersects(px,py,qx,qy,a[0],a[1],b[0],b[1])){error="The line crosses a sector inside this one";return false;}}}
        String before=map.save();
        int pa=map.addVertex(px,py),qa=map.addVertex(qx,qy);normalizeEdges();
        SectorMap.Sector s=map.sectors.get(si);int i=s.loop.indexOf(pa),j=s.loop.indexOf(qa);
        if(i<0||j<0||Math.abs(i-j)==1||Math.abs(i-j)==s.loop.size()-1){restore(before);error=i<0||j<0?"Couldn't find the ends":"That line is already a wall";return false;}
        ArrayList<Integer> A=new ArrayList<>(),B=new ArrayList<>();
        for(int k=i;;k=(k+1)%s.loop.size()){A.add(s.loop.get(k));if(k==j)break;}
        for(int k=j;;k=(k+1)%s.loop.size()){B.add(s.loop.get(k));if(k==i)break;}
        SectorMap.Sector b=new SectorMap.Sector().copyProps(s);b.loop=B;s.loop=A;map.sectors.add(b);
        if(!validate(coords(s),si,map.sectors.size()-1)||!validate(coords(b),map.sectors.size()-1,si)){String why=error;restore(before);error=why;return false;}
        undo.push(before);if(undo.size()>80)undo.removeLast();redo.clear();done();return true;
    }
    /** Removes vertices no sector uses (keeping sector loops pointing at the right ones). */
    int cleanOrphans(){
        java.util.HashSet<Integer> used=new java.util.HashSet<>();for(SectorMap.Sector s:map.sectors)used.addAll(s.loop);
        int removed=map.vertices.size()-used.size();if(removed<=0)return 0;
        push();ArrayList<SectorMap.Vertex> vs=new ArrayList<>();int[] remap=new int[map.vertices.size()];
        for(int i=0;i<map.vertices.size();i++)if(used.contains(i)){remap[i]=vs.size();vs.add(map.vertices.get(i));}
        for(SectorMap.Sector s:map.sectors)for(int i=0;i<s.loop.size();i++)s.loop.set(i,remap[s.loop.get(i)]);
        java.util.HashMap<Long,Integer> wt=new java.util.HashMap<>();
        for(java.util.Map.Entry<Long,Integer> e:map.wallTex.entrySet()){int a=(int)(e.getKey()>>32),b=(int)(long)e.getKey();if(used.contains(a)&&used.contains(b))wt.put(SectorMap.wallKey(remap[a],remap[b]),e.getValue());}
        map.wallTex=wt;map.vertices=vs;done();return removed;
    }
    /** "Geometry valid" or the first problem. */
    String validateMap(){
        for(int i=0;i<map.sectors.size();i++)if(!validate(coords(map.sectors.get(i)),i))return "Sector "+(i+1)+": "+error;
        if(map.sectorAt(map.playerX,map.playerY)<0)return "The player start is outside every sector";
        return "Geometry valid";
    }
    /** Inserts vertices lying on other edges into those edges, so neighbouring sectors share whole wall segments. */
    void normalizeEdges(){
        for(SectorMap.Sector sec:map.sectors){
            ArrayList<Integer> loop=new ArrayList<>();int n=sec.loop.size();
            for(int i=0;i<n;i++){int ia=sec.loop.get(i),ib=sec.loop.get((i+1)%n);SectorMap.Vertex a=map.vertices.get(ia),b=map.vertices.get(ib);loop.add(ia);
                ArrayList<Integer> mids=new ArrayList<>();
                for(int k=0;k<map.vertices.size();k++){if(k==ia||k==ib)continue;SectorMap.Vertex v=map.vertices.get(k);
                    if(onSegment(a.x,a.y,b.x,b.y,v.x,v.y)&&Math.hypot(v.x-a.x,v.y-a.y)>.001&&Math.hypot(v.x-b.x,v.y-b.y)>.001)mids.add(k);}
                final float dx=b.x-a.x,dy=b.y-a.y;
                mids.sort((u,w)->{SectorMap.Vertex x=map.vertices.get(u),y=map.vertices.get(w);return Float.compare((x.x-a.x)*dx+(x.y-a.y)*dy,(y.x-a.x)*dx+(y.y-a.y)*dy);});
                // a painted wall that gets split keeps its paint on every piece
                if(!mids.isEmpty())for(int dir=0;dir<2;dir++){int fa=dir==0?ia:ib,fb=dir==0?ib:ia;Integer t=map.wallTex.remove(SectorMap.wallKey(fa,fb));if(t==null)continue;
                    ArrayList<Integer> chain=new ArrayList<>();chain.add(ia);chain.addAll(mids);chain.add(ib);
                    for(int k=0;k+1<chain.size();k++){int x=chain.get(k),y=chain.get(k+1);map.wallTex.put(dir==0?SectorMap.wallKey(x,y):SectorMap.wallKey(y,x),t);}}
                loop.addAll(mids);}
            sec.loop=loop;}
    }

    // ------------------------------------------------------------------ things
    boolean placeThing(int type,float x,float y,float angle){
        if(map.sectorAt(x,y)<0){error="Place things inside a sector";return false;}
        if(type==SectorMap.T_PLAYER){push();map.playerX=x;map.playerY=y;done();return true;}
        for(SectorMap.Thing t:map.things)if(Math.hypot(t.x-x,t.y-y)<.3f){error="Something is already here";return false;}
        push();map.things.add(new SectorMap.Thing(type,x,y,angle));done();return true;
    }
    /** Nearest thing within max: its index, -2 for the player start, -1 for none. */
    int thingNear(float x,float y,float max){int best=-1;float bd=max;for(int i=0;i<map.things.size();i++){SectorMap.Thing t=map.things.get(i);float d=(float)Math.hypot(t.x-x,t.y-y);if(d<bd){bd=d;best=i;}}
        if(Math.hypot(map.playerX-x,map.playerY-y)<Math.min(bd,max))return -2;return best;}
    boolean moveThing(int i,float x,float y){if(map.sectorAt(x,y)<0){error="Things must be inside a sector";return false;}push();
        if(i==-2){map.playerX=x;map.playerY=y;}else if(i>=0&&i<map.things.size()){map.things.get(i).x=x;map.things.get(i).y=y;}done();return true;}
    boolean rotateThing(int i,float by){push();if(i==-2)map.facing+=by;else if(i>=0&&i<map.things.size())map.things.get(i).angle+=by;else return false;done();return true;}
    /** Which difficulties a thing appears on (Skill.F_* bits, at least one). */
    boolean setThingSkills(int i,int mask){if(i<0||i>=map.things.size()||mask==0){error=mask==0?"A thing must appear on at least one difficulty":"Nothing selected";return false;}push();map.things.get(i).skills=mask;done();return true;}
    boolean deleteThing(int i){if(i<0||i>=map.things.size()){error=i==-2?"The player start can't be deleted":"Nothing to delete";return false;}push();map.things.remove(i);done();return true;}
}
