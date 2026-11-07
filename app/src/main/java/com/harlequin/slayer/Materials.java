package com.harlequin.slayer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generated materials, made on demand: with over a hundred to choose from, a level only generates the ones it
 * uses (on all cores, in the background), and materials no longer needed are dropped when a new level starts.
 * Small 64-texel previews for the editor's texture browsers are made separately at low priority. Pure Java.
 */
final class Materials {
    private Materials(){}
    static final int THUMB=64;
    private static final Tex.Mat[] cache=new Tex.Mat[Tex.COUNT];
    private static final boolean[] pending=new boolean[Tex.COUNT],thumbPending=new boolean[Tex.COUNT];
    private static final int[][] thumbs=new int[Tex.COUNT][];
    private static ExecutorService pool,thumbPool;

    private static synchronized ExecutorService pool(){
        if(pool==null)pool=Executors.newFixedThreadPool(Math.max(2,Runtime.getRuntime().availableProcessors()-1),r->{Thread t=new Thread(r,"materials");t.setDaemon(true);return t;});
        return pool;
    }
    private static synchronized ExecutorService thumbPool(){
        if(thumbPool==null)thumbPool=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"thumbnails");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;});
        return thumbPool;
    }
    /** The full-size material if it is ready, else null. */
    static synchronized Tex.Mat get(int id){return id>=0&&id<Tex.COUNT?cache[id]:null;}
    /** Starts making a material (no-op if it is ready or already being made). */
    static synchronized void request(int id){
        if(id<0||id>=Tex.COUNT||cache[id]!=null||pending[id])return;pending[id]=true;
        pool().submit(()->{Tex.Mat m=Tex.make(id);synchronized(Materials.class){cache[id]=m;pending[id]=false;}});
    }
    /** Frees the materials not in `need` (a new level). */
    static synchronized void keepOnly(boolean[] need){for(int i=0;i<Tex.COUNT;i++)if(!need[i])cache[i]=null;}
    /** A 64 x 64 ARGB preview (albedo), or null while it is being made. */
    static synchronized int[] thumb(int id){
        if(id<0||id>=Tex.COUNT)return null;if(thumbs[id]!=null)return thumbs[id];
        Tex.Mat full=cache[id];if(full!=null){thumbs[id]=downscale(full);return thumbs[id];}
        if(!thumbPending[id]){thumbPending[id]=true;thumbPool().submit(()->{Tex.Mat m=Tex.make(id,THUMB);int[] px=new int[THUMB*THUMB];
            for(int k=0;k<px.length;k++)px[k]=shade(m,k);synchronized(Materials.class){thumbs[id]=px;}});}
        return null;
    }
    private static int[] downscale(Tex.Mat m){int s=m.size/THUMB;int[] px=new int[THUMB*THUMB];
        for(int y=0;y<THUMB;y++)for(int x=0;x<THUMB;x++)px[y*THUMB+x]=shade(m,(y*s)*m.size+x*s);return px;}
    /** Albedo lit by a fixed light through the normal map, with emission: how the surface reads in a preview. */
    private static int shade(Tex.Mat m,int k){
        int A=m.albedoAO[k],N=m.normalHeight[k],E=m.rme[k];float nx=((N>>16)&255)/127.5f-1,ny=((N>>8)&255)/127.5f-1,nz=(N&255)/127.5f-1;
        float l=Math.max(0,-nx*.45f-ny*.55f+nz*.7f)*.9f+.25f,ao=(A>>>24)/255f,em=(E&255)/255f,kk=l*(.55f+.45f*ao);
        int r=(int)Math.min(255,((A>>16)&255)*(kk+em)),g=(int)Math.min(255,((A>>8)&255)*(kk+em)),b=(int)Math.min(255,(A&255)*(kk+em));
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
}
