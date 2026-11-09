package com.harlequin.slayer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * The music player: an ambient bed while she explores, and when the fighting starts the drop — an impact and the
 * dubstep loop from its first bar, the ambience ducking under it. When the fighting stops (nothing hunting her for a
 * few seconds) the loop fades out over a few seconds and the ambience comes back. The tracks are synthesized by
 * {@link MusicSynth} on a background thread (once per theme, then kept), and mixed here into a streaming AudioTrack.
 */
final class Music {
    /** Music volume (a setting), 0..1. */
    static volatile float volume=.8f;
    /** 1 while Slayer narrates the story ({@link Narrator}): the music sinks under her voice. */
    static volatile float duck;
    static final int RATE=MusicSynth.RATE;

    private static final class Set {short[] amb,combat,impact;}
    private final Set[] sets=new Set[2];
    private volatile int theme=-1;private volatile boolean on,combat,paused,alive=true;
    private Thread mixer;private AudioTrack track;
    // mixer state (mixer thread only)
    private int ia,ic,ii=-1;private float ga,gc,gi;private boolean wasCombat;private int playing=-1;private float master,dk;

    Music(){
        mixer=new Thread(this::run,"music");mixer.setDaemon(true);mixer.start();
    }
    /** Starts (or switches to) a theme's ambience; the tracks render first if they haven't yet. */
    void play(int theme){this.theme=theme;on=true;combat=false;prepare(theme);}
    /** Fades the music out (menus, the editor). */
    void stop(){on=false;combat=false;}
    /** The fight has begun / is over. */
    void combat(boolean c){combat=c;}
    void pause(){paused=true;}
    void resume(){paused=false;}
    /** Renders a theme's tracks in the background (no-op if they exist or are rendering). */
    private final boolean[] rendering=new boolean[2];
    void prepare(int th){
        synchronized(this){if(sets[th]!=null||rendering[th])return;rendering[th]=true;}
        Thread t=new Thread(()->{
            long t0=System.nanoTime();
            MusicSynth s=new MusicSynth(th);Set set=new Set();
            set.amb=pcm(s.ambient());set.impact=pcm(s.impact());set.combat=pcm(s.combat());
            synchronized(this){sets[th]=set;rendering[th]=false;}
            android.util.Log.i("Slayer","music theme "+th+" rendered in "+(System.nanoTime()-t0)/1000000+" ms");
        },"music-render");
        t.setPriority(Thread.MIN_PRIORITY);t.setDaemon(true);t.start();
    }
    /** Which theme a map sounds like: the station's textures are TECH, everything else HELL. */
    static int themeOf(SectorMap m){
        int tech=0,hell=0;for(SectorMap.Sector s:m.sectors){for(int id:new int[]{s.floorTex,s.wallTex}){int c=Tex.category(id);if(c==Tex.CAT_SCIFI)tech++;else if(c==Tex.CAT_HELL)hell++;}}
        return tech>hell?MusicSynth.TECH:MusicSynth.HELL;
    }
    static short[] pcm(MusicSynth.Track t){short[] o=new short[t.length()*2];for(int i=0;i<t.length();i++){o[i*2]=(short)Math.max(-32767,Math.min(32767,t.l[i]*32767));o[i*2+1]=(short)Math.max(-32767,Math.min(32767,t.r[i]*32767));}return o;}

    private void run(){
        final int frames=1024;short[] buf=new short[frames*2];
        while(alive){
            try{
                if(track==null){if(!on){Thread.sleep(60);continue;}open();if(track==null){Thread.sleep(500);continue;}}
                int th=theme;Set set=th>=0?sets[th]:null;
                if(paused||(!on&&master<.001f)||set==null){
                    if(track.getPlayState()==AudioTrack.PLAYSTATE_PLAYING){track.pause();track.flush();}
                    if(!on&&master<.001f){ga=gc=gi=0;ii=-1;wasCombat=false;}
                    Thread.sleep(40);continue;}
                if(track.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING)track.play();
                if(playing!=th){playing=th;ia=0;ic=0;ii=-1;gc=0;gi=0;wasCombat=false;}
                mix(set,buf,frames);
                track.write(buf,0,buf.length);
            }catch(InterruptedException e){return;}catch(Throwable e){android.util.Log.e("Slayer","music",e);try{Thread.sleep(500);}catch(InterruptedException x){return;}}
        }
    }
    private void open(){
        try{int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
            track=new AudioTrack(AudioManager.STREAM_MUSIC,RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,4096*4),AudioTrack.MODE_STREAM);
            if(track.getState()!=AudioTrack.STATE_INITIALIZED){track.release();track=null;}}
        catch(Throwable e){track=null;}
    }
    /** One block: the gains glide toward their targets; the drop restarts the loop and fires the impact. */
    private void mix(Set s,short[] out,int frames){
        boolean c=combat&&on;
        if(c&&!wasCombat&&gc<.2f){ic=0;ii=0;gi=1;}   // the drop: from the top of the loop, with the impact
        wasCombat=c;
        float tA=on?(c?.18f:1f):0,tC=c&&on?1f:0,tM=on?volume:0;
        // per-sample glide rates: the drop is quick, the fade-out slow
        float rA=1f/(RATE*(c?.6f:3f)),rC=1f/(RATE*(c?.15f:3.5f)),rM=1f/(RATE*1.2f),rD=1f/(RATE*.8f),tD=duck;
        short[] A=s.amb,C=s.combat,I=s.impact;
        for(int f=0;f<frames;f++){
            ga+=Math.max(-rA,Math.min(rA,tA-ga));gc+=Math.max(-rC,Math.min(rC,tC-gc));master+=Math.max(-rM,Math.min(rM,tM-master));dk+=Math.max(-rD,Math.min(rD,tD-dk));
            float l=0,r=0;
            if(ga>1e-4f){l+=A[ia]*ga*.85f;r+=A[ia+1]*ga*.85f;}
            ia+=2;if(ia>=A.length)ia=0;
            if(gc>1e-4f){l+=C[ic]*gc;r+=C[ic+1]*gc;ic+=2;if(ic>=C.length)ic=0;}else ic=0;
            if(ii>=0){l+=I[ii]*gi;r+=I[ii+1]*gi;ii+=2;if(ii>=I.length)ii=-1;}
            float m=master*.8f*(1-.65f*dk);l*=m;r*=m;
            out[f*2]=(short)Math.max(-32767,Math.min(32767,l));out[f*2+1]=(short)Math.max(-32767,Math.min(32767,r));
        }
    }
    void release(){alive=false;if(mixer!=null)mixer.interrupt();if(track!=null){try{track.stop();}catch(Throwable e){}track.release();track=null;}}
}
