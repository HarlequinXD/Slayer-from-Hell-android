package com.harlequin.slayer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Random;

/**
 * Slayer reads the story aloud, cut like a movie trailer. Each paragraph of a chapter is spoken by the platform's own
 * text-to-speech engine ({@link Voice}: Android's TextToSpeech, Windows' System.Speech), then given the trailer
 * treatment in code (a deeper sub layer, chest and presence EQ, heavy compression, a little grit, a hall) and played
 * with procedural BRAAMs: one to open, one after every short "punchline" paragraph, and a big one to close. The game
 * music ducks while she speaks. {@link StoryView} types the text out in step with her voice.
 */
final class Narrator {
    /** A platform speech engine: renders text to a 16-bit PCM WAV file, blocking until done (or throws). */
    interface Voice {void render(String text,File wav) throws Exception;}
    /** Set by the platform at start-up; null when there is no speech engine (then the story is read silently). */
    static volatile Voice voice;
    /** The Narration setting. */
    static volatile boolean enabled=true;
    static final int RATE=44100,PREPARING=0,SPEAKING=1,DONE=2,FAILED=3;
    /** Paragraphs at most this long are punchlines: a BRAAM lands after them. */
    static final int PUNCH=90;

    private static Narrator current;
    /** Starts reading a chapter (stopping any other reading), or returns null if narration is off or impossible. */
    static synchronized Narrator start(Story.Chapter ch,File tmpDir){
        if(current!=null){current.stop();current=null;}
        if(!enabled||voice==null)return null;
        current=new Narrator(ch.text,tmpDir,voice);return current;
    }
    static synchronized void pauseAll(){if(current!=null)current.paused=true;}
    static synchronized void resumeAll(){if(current!=null)current.paused=false;}

    final String[] paras;final int[] paraStart;
    private final short[][] voiced;private final long[] vStart;private final int[] vLen;
    private final File dir;private final Voice engine;
    private volatile int state=PREPARING;private volatile boolean stopping,paused;private volatile long played;

    Narrator(String text,File dir,Voice engine){
        this.dir=dir;this.engine=engine;
        ArrayList<int[]> sp=spans(text);paras=new String[sp.size()];paraStart=new int[sp.size()];
        for(int k=0;k<paras.length;k++){paraStart[k]=sp.get(k)[0];paras[k]=text.substring(sp.get(k)[0],sp.get(k)[1]);}
        voiced=new short[paras.length][];vStart=new long[paras.length];vLen=new int[paras.length];java.util.Arrays.fill(vStart,-1);
        Thread s=new Thread(this::synthesise,"narrator-voice");s.setDaemon(true);s.start();
        Thread p=new Thread(this::play,"narrator-play");p.setDaemon(true);p.start();
    }
    /** The paragraphs she reads, as [start, end) character spans of the chapter text. */
    static ArrayList<int[]> spans(String text){
        ArrayList<int[]> out=new ArrayList<>();int i=0;
        for(String p:text.split("\n",-1)){if(!p.trim().isEmpty())out.add(new int[]{i,i+p.length()});i+=p.length()+1;}
        return out;
    }
    int state(){return state;}
    /** Fades her out (Skip / Continue / leaving the screen). */
    void stop(){stopping=true;}
    /** How far into the chapter's text she has read, in characters (for typing the text in step with her). */
    float position(){
        long p=played-RATE/6;   // what has reached the speaker, roughly
        for(int i=paras.length-1;i>=0;i--)if(vStart[i]>=0&&p>=vStart[i])return paraStart[i]+Math.min(1f,(p-vStart[i])/(float)vLen[i])*paras[i].length();
        return 0;
    }

    // ---------------------------------------------------------------- speech, paragraph by paragraph
    private void synthesise(){
        try{
            for(int i=0;i<paras.length&&!stopping;i++){
                File wav=new File(dir,"narration-"+i+"-"+System.nanoTime()+".wav");
                try{engine.render(paras[i],wav);float[][] pcm=readWav(new FileInputStream(wav));
                    voiced[i]=trailer(pcm[0],(int)pcm[1][0]);}
                finally{wav.delete();}
            }
        }catch(Throwable e){android.util.Log.e("Slayer","narration",e);state=FAILED;stopping=true;}
    }

    // ---------------------------------------------------------------- playback: her voice on a timeline with the BRAAMs
    private static final class Clip {final long at;final short[] pcm;final float gain;Clip(long a,short[] p,float g){at=a;pcm=p;gain=g;}}
    private void play(){
        AudioTrack track=null;
        try{
            int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
            track=new AudioTrack(AudioManager.STREAM_MUSIC,RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,8192),AudioTrack.MODE_STREAM);
            if(track.getState()!=AudioTrack.STATE_INITIALIZED){state=FAILED;return;}
            Random rng=new Random(666);
            short[] braam=braam(5f,rng),big=braam(7.5f,rng);
            ArrayList<Clip> clips=new ArrayList<>();clips.add(new Clip(0,big,.85f));
            long cursor=(long)(1.6f*RATE),end=Long.MAX_VALUE;int next=0;float fade=1;
            final int frames=1024;float[] mix=new float[frames*2];short[] out=new short[frames*2];
            Music.duck=1;track.play();
            while(true){
                if(paused){if(track.getPlayState()==AudioTrack.PLAYSTATE_PLAYING)track.pause();Thread.sleep(50);continue;}
                if(track.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING)track.play();
                // place each paragraph on the timeline as soon as it is ready
                while(next<paras.length&&voiced[next]!=null){
                    short[] v=voiced[next];long at=Math.max(cursor,played+RATE/10);
                    vLen[next]=v.length/2;vStart[next]=at;clips.add(new Clip(at,v,1f));state=SPEAKING;
                    long done=at+v.length/2-(long)(1.2f*RATE);   // her reverb tail overlaps what follows
                    if(next==paras.length-1){clips.add(new Clip(done+RATE/3,big,1f));end=done+RATE/3+big.length/2;}
                    else if(paras[next].length()<=PUNCH){clips.add(new Clip(done+RATE/4,braam,.8f));cursor=done+(long)(2.7f*RATE);}
                    else cursor=done+(long)(.55f*RATE);
                    next++;
                }
                if(stopping){fade-=frames/(RATE*.5f);if(fade<=0)break;}
                if(played>=end)break;
                java.util.Arrays.fill(mix,0);
                for(int k=clips.size()-1;k>=0;k--){Clip c=clips.get(k);long off=played-c.at;
                    if(off>=c.pcm.length/2){clips.remove(k);continue;}
                    for(int f=0;f<frames;f++){long s=off+f;if(s<0)continue;if(s>=c.pcm.length/2)break;
                        mix[f*2]+=c.pcm[(int)s*2]*c.gain;mix[f*2+1]+=c.pcm[(int)s*2+1]*c.gain;}}
                for(int f=0;f<frames*2;f++){float x=mix[f]/32767f*fade;out[f]=(short)(32767*Math.tanh(x*1.1f)/Math.tanh(1.1f));}
                track.write(out,0,out.length);played+=frames;
            }
            if(state!=FAILED)state=DONE;
        }catch(InterruptedException e){state=DONE;}
        catch(Throwable e){android.util.Log.e("Slayer","narration",e);state=FAILED;}
        finally{if(track!=null){try{track.stop();}catch(Throwable ignored){}track.release();}
            synchronized(Narrator.class){if(current==this)current=null;if(current==null)Music.duck=0;}}   // a newer reading keeps the duck
    }

    // ---------------------------------------------------------------- WAV in
    /** Reads a PCM WAV as mono floats; returns {samples, {sampleRate}}. */
    static float[][] readWav(InputStream in0) throws IOException {
        byte[] b;try(InputStream in=in0){ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] buf=new byte[1<<16];int n;while((n=in.read(buf))>0)o.write(buf,0,n);b=o.toByteArray();}
        if(b.length<12||b[0]!='R'||b[1]!='I'||b[8]!='W')throw new IOException("not a WAV file");
        int ch=1,rate=22050,bits=16,p=12,dataAt=-1,dataLen=0;
        while(p+8<=b.length){String id=new String(b,p,4,"US-ASCII");int len=le32(b,p+4);
            if(id.equals("fmt ")){ch=le16(b,p+10);rate=le32(b,p+12);bits=le16(b,p+22);}
            if(id.equals("data")){dataAt=p+8;dataLen=(len<=0||dataAt+len>b.length)?b.length-dataAt:len;break;}
            p+=8+len+(len&1);if(len<0)break;}
        if(dataAt<0)throw new IOException("WAV without data");
        int bps=bits/8,frames=dataLen/(bps*ch);float[] x=new float[frames];
        for(int i=0;i<frames;i++){float s=0;for(int c=0;c<ch;c++){int at=dataAt+(i*ch+c)*bps;
            s+=bits==8?((b[at]&255)-128)/128f:(short)((b[at]&255)|(b[at+1]<<8))/32768f;}x[i]=s/ch;}
        return new float[][]{x,{rate}};
    }
    private static int le16(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8);}
    private static int le32(byte[] b,int p){return le16(b,p)|(le16(b,p+2)<<16);}

    // ---------------------------------------------------------------- the trailer voice
    /** Her voice as it comes from the engine, made into a trailer voice-over: stereo 44.1 kHz, with a reverb tail. */
    static short[] trailer(float[] in,int rate){
        // to 44.1 kHz, without the engine's leading and trailing silence
        int a=0,z=in.length-1;while(a<z&&Math.abs(in[a])<.01f)a++;while(z>a&&Math.abs(in[z])<.01f)z--;
        a=Math.max(0,a-rate/50);z=Math.min(in.length-1,z+rate/20);
        int n=Math.max(1,(int)((long)(z-a+1)*RATE/rate));float[] x=new float[n];
        for(int i=0;i<n;i++){double s=a+(double)i*rate/RATE;int k=(int)s;float f=(float)(s-k);x[i]=in[Math.min(z,k)]*(1-f)+in[Math.min(z,k+1)]*f;}
        // a sub voice an octave down, felt more than heard
        float[] sub=lowpass(lowpass(octaveDown(x),700),700);
        float pk=peak(x);if(pk<1e-4f)pk=1;
        float[] y=new float[n];float hp=0,prev=0,body=0,pres1=0,pres2=0;
        float aHp=1-(float)Math.exp(-2*Math.PI*80/RATE),aBody=1-(float)Math.exp(-2*Math.PI*220/RATE),
            aP1=1-(float)Math.exp(-2*Math.PI*1800/RATE),aP2=1-(float)Math.exp(-2*Math.PI*5500/RATE);
        for(int i=0;i<n;i++){float s=x[i]/pk+.22f*sub[i]/Math.max(1e-4f,pk);
            hp+=aHp*(s-hp);s-=hp;                                  // rumble out
            body+=aBody*(s-body);pres1+=aP1*(s-pres1);pres2+=aP2*(s-pres2);
            y[i]=s+.55f*body+.35f*(pres2-pres1);}                  // chest + presence
        // heavy compression (3:1 above a low threshold), then a little grit
        float env=0,att=1-(float)Math.exp(-1/(.004*RATE)),rel=1-(float)Math.exp(-1/(.12*RATE)),ep=0;
        float[] e=new float[n];for(int i=0;i<n;i++){float v=Math.abs(y[i]);env+=(v>env?att:rel)*(v-env);e[i]=env;ep=Math.max(ep,env);}
        float thr=ep*.22f;for(int i=0;i<n;i++)if(e[i]>thr)y[i]*=(float)Math.pow(e[i]/thr,-2/3.0);
        float g=1/Math.max(1e-6f,peak(y));for(int i=0;i<n;i++)y[i]=(float)(Math.tanh(1.5*y[i]*g)/Math.tanh(1.5));
        // a stereo hall with a short pre-delay, and a tail
        int tail=(int)(1.4f*RATE),pre=(int)(.025f*RATE);float[][] wet=hall(y,n+tail,pre);
        short[] o=new short[(n+tail)*2];float mx=1e-6f;
        for(int i=0;i<n+tail;i++){float d=i<n?y[i]:0;mx=Math.max(mx,Math.max(Math.abs(d+1.1f*wet[0][i]),Math.abs(d+1.1f*wet[1][i])));}
        float s=.88f/mx;
        for(int i=0;i<n+tail;i++){float d=i<n?y[i]:0;o[i*2]=(short)(32767*s*(d+1.1f*wet[0][i]));o[i*2+1]=(short)(32767*s*(d+1.1f*wet[1][i]));}
        return o;
    }
    /** Pitch an octave down at the same length: read at half speed, overlap-added in windowed grains. */
    static float[] octaveDown(float[] x){
        int n=x.length,G=2048,H=512;float[] y=new float[n],w=new float[n];float[] win=new float[G];
        for(int i=0;i<G;i++)win[i]=(float)(.5-.5*Math.cos(2*Math.PI*i/G));
        for(int o=0;o<n;o+=H){int src=o-G/4;   // grains read from around where they are written, at half speed
            for(int i=0;i<G&&o+i<n;i++){double s=src+i*.5;if(s<0)s=0;int k=(int)s;if(k+1>=n)break;float f=(float)(s-k);
                y[o+i]+=(x[k]*(1-f)+x[k+1]*f)*win[i];w[o+i]+=win[i];}}
        for(int i=0;i<n;i++)if(w[i]>1e-3f)y[i]/=w[i];
        return y;
    }
    static float[] lowpass(float[] x,float hz){float a=1-(float)Math.exp(-2*Math.PI*hz/RATE),s=0;float[] y=new float[x.length];for(int i=0;i<x.length;i++){s+=a*(x[i]-s);y[i]=s;}return y;}
    static float peak(float[] x){float p=0;for(float v:x)p=Math.max(p,Math.abs(v));return p;}
    /** A small Freeverb-style hall (eight damped combs and two allpasses per side). */
    static float[][] hall(float[] x,int len,int pre){
        int[] combs={1116,1188,1277,1356,1422,1491,1557,1617},aps={556,441};float[][] out=new float[2][len];
        for(int side=0;side<2;side++){int sp=side*23;float[][] cb=new float[combs.length][];int[] ci=new int[combs.length];float[] lp=new float[combs.length];
            for(int k=0;k<combs.length;k++)cb[k]=new float[combs[k]+sp];
            float[][] ab=new float[2][];int[] ai=new int[2];for(int k=0;k<2;k++)ab[k]=new float[aps[k]+sp];
            for(int i=0;i<len;i++){int src=i-pre;float in=src>=0&&src<x.length?x[src]*.015f:0,acc=0;
                for(int k=0;k<combs.length;k++){float[] buf=cb[k];float o=buf[ci[k]];lp[k]=o*.7f+lp[k]*.3f;buf[ci[k]]=in+lp[k]*.84f;if(++ci[k]>=buf.length)ci[k]=0;acc+=o;}
                for(int k=0;k<2;k++){float[] buf=ab[k];float b=buf[ai[k]];float o=-acc+b;buf[ai[k]]=acc+b*.5f;if(++ai[k]>=buf.length)ai[k]=0;acc=o;}
                out[side][i]=acc;}}
        return out;
    }

    // ---------------------------------------------------------------- BRAAM
    /** A trailer BRAAM: detuned, distorted low saws whose filter blasts open and closes, a sub drop and an impact. */
    static short[] braam(float seconds,Random rng){
        int n=(int)(seconds*RATE);short[] o=new short[n*2];float[][] side=new float[2][n];
        float[] freqs={36.71f,73.42f,110f,146.83f},det={-.006f,-.002f,.002f,.006f};
        for(int s=0;s<2;s++){float[] y=side[s];
            for(float f:freqs)for(float d:det){double ph=rng.nextDouble(),inc=f*(1+d+(s==0?-.0015:.0015))/RATE;
                for(int i=0;i<n;i++){ph+=inc;if(ph>=1)ph-=1;y[i]+=(float)(2*ph-1);}}
            float pk=peak(y);for(int i=0;i<n;i++)y[i]=(float)Math.tanh(2.5*y[i]/pk);
            float[] dark=lowpass(lowpass(y,180),180),bright=lowpass(lowpass(y,2200),2200);float pd=peak(dark),pb=peak(bright);
            float lp=0,aLp=1-(float)Math.exp(-2*Math.PI*300/RATE);double subPh=0;
            for(int i=0;i<n;i++){float t=i/(float)RATE;
                float open=Math.min(1,t/.06f)*(float)Math.exp(-1.4*t);
                float b=(dark[i]/pd*(1-open)*.8f+bright[i]/pb*open)*Math.min(1,t/.03f)*(float)Math.exp(-.55*t);
                subPh+=(30+35*Math.exp(-2.5*t))/RATE;float sub=(float)(Math.sin(2*Math.PI*subPh)*Math.exp(-.6*t));
                lp+=aLp*((rng.nextFloat()*2-1)-lp);float hit=lp*6*(float)Math.exp(-9*t);
                y[i]=.75f*b+.9f*sub+.6f*hit;}}
        float pk=Math.max(peak(side[0]),peak(side[1]));
        for(int i=0;i<n;i++){float f=i>n-RATE/4?(n-i)/(RATE/4f):1;o[i*2]=(short)(32767*.9f*f*side[0][i]/pk);o[i*2+1]=(short)(32767*.9f*f*side[1][i]/pk);}
        return o;
    }
}
