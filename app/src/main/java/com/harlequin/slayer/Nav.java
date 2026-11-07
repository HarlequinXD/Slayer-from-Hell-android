package com.harlequin.slayer;

import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * Grid navigation over the level's 0.5 m raster cells: a flow field (Dijkstra distances to the target cell) that every
 * walker follows downhill. A move between neighbouring cells is allowed if the step up is no more than a stair,
 * the drop is no more than 2 m, there is headroom, and the cell isn't lava; diagonals need both sides open.
 * Rebuilt only when the target moves to another cell. Pure Java.
 */
final class Nav {
    final Level L;final int W,H;
    /** Path distance (metres) from every cell to the target; infinity where unreachable. */
    final float[] dist;
    private int target=-1;
    static final float INF=1e9f,CLIMB=Level.STEP,DROP=2.05f,HEADROOM=2.1f;
    private static final int[][] DIRS={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};

    Nav(Level l){L=l;W=l.W;H=l.H;dist=new float[W*H];Arrays.fill(dist,INF);}

    static boolean standable(Level.Cell c){return !c.solid&&!c.lava&&c.ceil-c.floor>=HEADROOM;}
    /** Can a walker go from cell a to the neighbouring cell b? */
    boolean step(int ax,int ay,int bx,int by){
        Level.Cell a=L.at(ax,ay),b=L.at(bx,by);if(!standable(a)||!standable(b))return false;
        float d=b.floor-a.floor;if(d>CLIMB+1e-3f||d<-DROP)return false;
        if(ax!=bx&&ay!=by){Level.Cell c1=L.at(bx,ay),c2=L.at(ax,by);   // no corner cutting
            if(!standable(c1)||!standable(c2)||Math.abs(c1.floor-a.floor)>CLIMB||Math.abs(c2.floor-a.floor)>CLIMB)return false;}
        return true;
    }

    /** Recomputes the field toward (x,y) if that is a different cell from last time. */
    void target(float x,float y){
        int tx=L.ix(x),ty=L.iy(y);if(tx<0||ty<0||tx>=W||ty>=H)return;int t=ty*W+tx;if(t==target)return;target=t;
        Arrays.fill(dist,INF);dist[t]=0;
        PriorityQueue<long[]> q=new PriorityQueue<>((p,r)->Long.compare(p[0],r[0]));q.add(new long[]{0,t});
        while(!q.isEmpty()){long[] e=q.poll();int c=(int)e[1];float dc=e[0]/1000f;if(dc>dist[c]+1e-4f)continue;int cx=c%W,cy=c/W;
            for(int[] d:DIRS){int nx=cx+d[0],ny=cy+d[1];if(nx<0||ny<0||nx>=W||ny>=H)continue;
                if(!step(nx,ny,cx,cy))continue;   // walkers move from the neighbour toward the target
                // cost in metres, with a little extra next to walls so walkers keep off them
                float nd=dc+(d[0]!=0&&d[1]!=0?1.4142f:1f)*Level.CELL+(nearWall(nx,ny)?.35f:0);int n=ny*W+nx;
                if(nd<dist[n]-1e-4f){dist[n]=nd;q.add(new long[]{(long)(nd*1000),n});}}}
    }
    private boolean nearWall(int x,int y){return !standable(L.at(x+1,y))||!standable(L.at(x-1,y))||!standable(L.at(x,y+1))||!standable(L.at(x,y-1));}
    float distAt(float x,float y){int cx=L.ix(x),cy=L.iy(y);if(cx<0||cy<0||cx>=W||cy>=H)return INF;return dist[cy*W+cx];}

    /** The centre of the best neighbouring cell to head for from (x,y), or null if there is none (unreachable / arrived). */
    float[] next(float x,float y){
        int cx=L.ix(x),cy=L.iy(y);if(cx<0||cy<0||cx>=W||cy>=H)return null;
        float best=dist[cy*W+cx];int bx=-1,by=-1;
        for(int[] d:DIRS){int nx=cx+d[0],ny=cy+d[1];if(nx<0||ny<0||nx>=W||ny>=H)continue;float nd=dist[ny*W+nx];
            if(nd<best-1e-4f&&step(cx,cy,nx,ny)){best=nd;bx=nx;by=ny;}}
        return bx<0?null:new float[]{L.cx(bx),L.cy(by)};
    }
    /** True if a walker can go in a straight line from a to b (every cell crossed is a legal step from the last). */
    boolean straight(float ax,float ay,float bx,float by){
        float d=(float)Math.hypot(bx-ax,by-ay);int n=Math.max(1,(int)(d/.2f));int px=L.ix(ax),py=L.iy(ay);
        for(int i=1;i<=n;i++){float t=i/(float)n;
            for(float off:new float[]{-.3f,.3f}){   // both shoulders (her radius) stay on walkable cells
                float nx=-(by-ay)/Math.max(d,1e-3f)*off,ny=(bx-ax)/Math.max(d,1e-3f)*off;
                int cx=L.ix(ax+(bx-ax)*t+nx),cy=L.iy(ay+(by-ay)*t+ny);Level.Cell c=L.at(cx,cy);if(!standable(c))return false;}
            int cx=L.ix(ax+(bx-ax)*t),cy=L.iy(ay+(by-ay)*t);
            if(cx!=px||cy!=py){if(Math.abs(cx-px)>1||Math.abs(cy-py)>1||!step(px,py,cx,cy))return false;px=cx;py=cy;}}
        return true;
    }
}
