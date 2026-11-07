package com.harlequin.slayer;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/** Sound effects synthesised at start-up (no audio files): written as WAVs to the cache and played with SoundPool. */
final class Sfx {
    static final int BLAST=0,PUMP_BACK=1,PUMP_FWD=2,SHELL_IN=3,TINK=4,DRY=5,STEP=6,LAND=7,SIZZLE=8,DRONE=9,JUMP=10,ENGINE=11,GRIND=12,
        ROAR=13,CHARGE=14,FIREBALL=15,EXPLODE=16,PAIN=17,DIE=18,HIT=19,PICKUP=20,COUNT=21;
    private static final int RATE=44100;
    private final SoundPool pool;private final int[] ids=new int[COUNT];private final boolean[] ready=new boolean[COUNT];
    private final Random rng=new Random(3);private int droneStream,engineStream;private float engVol=.2f,engRate=.6f;

    Sfx(Context c){
        pool=new SoundPool.Builder().setMaxStreams(28).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((p,id,status)->{for(int i=0;i<COUNT;i++)if(ids[i]==id){ready[i]=status==0;if(i==DRONE&&ready[i])droneStream=p.play(id,.35f,.35f,LOOP_PRI,-1,1);if(i==ENGINE&&ready[i])engineStream=p.play(id,engVol,engVol,LOOP_PRI,-1,engRate);}});
        File dir=c.getCacheDir();
        new Thread(()->{for(int i=0;i<COUNT;i++){try{File f=new File(dir,"sfx"+i+".wav");write(f,make(i));ids[i]=pool.load(f.getPath(),1);}catch(Exception ignored){}}},"sfx").start();
    }
    void play(int s,float vol){play(s,vol,1);}
    void play(int s,float vol,float rate){if(s>=0&&s<COUNT&&ready[s])pool.play(ids[s],vol,vol,1,0,rate*(.96f+rng.nextFloat()*.08f));}
    /** The chainsaw's two-stroke engine (a seamless loop): volume and playback rate follow the throttle. */
    void engine(float vol,float rate){
        rate=Math.max(.5f,Math.min(2f,rate));if(Math.abs(vol-engVol)<.01f&&Math.abs(rate-engRate)<.01f)return;engVol=vol;engRate=rate;
        if(engineStream!=0){pool.setVolume(engineStream,vol,vol);pool.setRate(engineStream,rate);}
    }
    /** Loops play at a high priority so a burst of one-shot effects can never steal their streams (one-shots use 1). */
    private static final int LOOP_PRI=10;
    /** Restarts the drone and engine loops: called when a level starts, so a stream lost to the OS comes back. */
    void restartLoops(){
        if(droneStream!=0)pool.stop(droneStream);if(engineStream!=0)pool.stop(engineStream);droneStream=engineStream=0;
        if(ready[DRONE])droneStream=pool.play(ids[DRONE],.35f,.35f,LOOP_PRI,-1,1);
        if(ready[ENGINE])engineStream=pool.play(ids[ENGINE],engVol,engVol,LOOP_PRI,-1,engRate);
    }
    void pause(){pool.autoPause();}
    void resume(){pool.autoResume();}

    // ------------------------------------------------------------------ synthesis
    private float[] make(int s){
        switch(s){
            case BLAST:{float[] a=new float[(int)(RATE*1.4f)];float lp=0,lp2=0;
                for(int i=0;i<a.length;i++){float t=i/(float)RATE;float n=rng.nextFloat()*2-1;
                    float cut=Math.max(.02f,.9f*(float)Math.exp(-t*18));lp+=(n-lp)*cut;lp2+=(lp-lp2)*Math.max(.03f,cut*.7f);
                    float env=t<.002f?t/.002f:(float)Math.exp(-t*7);float boom=(float)Math.sin(2*Math.PI*(52*t+12*(1-Math.exp(-t*30))))*(float)Math.exp(-t*9)*.9f;
                    a[i]=(lp2*1.8f*env+boom)*.9f;}
                reverb(a,.35f);return a;}
            case PUMP_BACK:return clack(.11f,1500,.9f,2600);
            case PUMP_FWD:return clack(.1f,1900,1f,3300);
            case SHELL_IN:{float[] a=clack(.08f,2400,.6f,4200);float[] b=new float[(int)(RATE*.16f)];System.arraycopy(a,0,b,(int)(RATE*.05f),Math.min(a.length,b.length-(int)(RATE*.05f)));
                for(int i=0;i<(int)(RATE*.05f);i++){float t=i/(float)RATE;b[i]+=(rng.nextFloat()*2-1)*.12f*(float)Math.sin(Math.PI*t/.05f);}return b;}
            case TINK:{float[] a=new float[(int)(RATE*.35f)];for(int i=0;i<a.length;i++){float t=i/(float)RATE;
                a[i]=((float)Math.sin(2*Math.PI*3150*t)*.5f+(float)Math.sin(2*Math.PI*5230*t)*.3f+(float)Math.sin(2*Math.PI*7710*t)*.15f)*(float)Math.exp(-t*18)*.6f;}return a;}
            case DRY:return clack(.05f,2800,.7f,5000);
            case STEP:{float[] a=new float[(int)(RATE*.14f)];float lp=0;for(int i=0;i<a.length;i++){float t=i/(float)RATE;lp+=(rng.nextFloat()*2-1-lp)*.06f;
                a[i]=(lp*2.5f+(float)Math.sin(2*Math.PI*90*t)*.4f)*(float)Math.exp(-t*40)*.6f;}return a;}
            case LAND:{float[] a=new float[(int)(RATE*.3f)];float lp=0;for(int i=0;i<a.length;i++){float t=i/(float)RATE;lp+=(rng.nextFloat()*2-1-lp)*.05f;
                a[i]=(lp*3f+(float)Math.sin(2*Math.PI*60*t)*.8f)*(float)Math.exp(-t*20)*.7f;}return a;}
            case JUMP:{float[] a=new float[(int)(RATE*.12f)];float lp=0;for(int i=0;i<a.length;i++){float t=i/(float)RATE;lp+=(rng.nextFloat()*2-1-lp)*.1f;a[i]=lp*1.6f*(float)Math.exp(-t*30)*.5f;}return a;}
            case SIZZLE:{float[] a=new float[(int)(RATE*.6f)];float hp=0,prev=0;for(int i=0;i<a.length;i++){float t=i/(float)RATE;float n=rng.nextFloat()*2-1;hp=.9f*(hp+n-prev);prev=n;
                a[i]=hp*.25f*(float)Math.sin(Math.PI*t/.6f)*(rng.nextFloat()>.97f?2:1);}return a;}
            case DRONE:{float[] a=new float[RATE*4];float lp=0;for(int i=0;i<a.length;i++){float t=i/(float)RATE;lp+=(rng.nextFloat()*2-1-lp)*.004f;
                a[i]=((float)Math.sin(2*Math.PI*41*t)*.25f+(float)Math.sin(2*Math.PI*61.5f*t)*.12f+lp*4f)*.4f*(.8f+.2f*(float)Math.sin(2*Math.PI*t/2));}
                int fade=RATE/10;for(int i=0;i<fade;i++){float k=i/(float)fade;a[i]*=k;a[a.length-1-i]*=k;}return a;}
            case ENGINE:{   // two-stroke: a buzzy pulse train at 75 Hz (exactly 75 cycles in a second, so it loops cleanly) with exhaust rasp
                float[] a=new float[RATE];float lp=0,hp=0,prev=0;Random r=new Random(11);float[] jit=new float[75];for(int k=0;k<75;k++)jit[k]=.75f+r.nextFloat()*.5f;
                for(int i=0;i<a.length;i++){float t=i/(float)RATE;float ph=(t*75)%1f;int cyc=(int)(t*75)%75;
                    float pulse=(float)Math.exp(-ph*9)*jit[cyc]-.11f;float saw2=(ph-.5f)*.35f;float n=r.nextFloat()*2-1;hp=.85f*(hp+n-prev);prev=n;
                    lp+=((pulse+saw2)-lp)*.35f;a[i]=(lp*.9f+hp*.12f*(float)Math.exp(-ph*5)+(float)Math.sin(2*Math.PI*150*t)*.08f)*.7f;}
                int fade=RATE/200;for(int i=0;i<fade;i++){float k=i/(float)fade;a[a.length-1-i]=a[a.length-1-i]*k+a[i]*(1-k);}return a;}
            case GRIND:{float[] a=new float[(int)(RATE*.22f)];float hp=0,prev=0;for(int i=0;i<a.length;i++){float t=i/(float)RATE;float n=rng.nextFloat()*2-1;hp=.92f*(hp+n-prev);prev=n;
                float env=Math.min(1,t/.01f)*(float)Math.exp(-t*9);a[i]=(hp*.6f+(float)Math.sin(2*Math.PI*1900*t+3*Math.sin(2*Math.PI*37*t))*.35f+(float)Math.sin(2*Math.PI*3100*t)*.2f)*env*.8f;}return a;}
            case ROAR:{   // a demon's roar: a growling, pitch-falling buzz through a throaty formant, with breath noise
                float[] a=new float[(int)(RATE*1.1f)];float ph=0,lp=0,bp=0,bv=0;
                for(int i=0;i<a.length;i++){float t=i/(float)RATE;float f=95-35*t+8*(float)Math.sin(2*Math.PI*7*t);ph+=f/RATE;float buzz=(ph%1f)*2-1;
                    float rough=1+.5f*(rng.nextFloat()*2-1)*(float)Math.sin(2*Math.PI*31*t);float x=buzz*rough+(rng.nextFloat()*2-1)*.35f;
                    float fc=2*(float)Math.sin(Math.PI*(520-140*t)/RATE);bp+=fc*bv;bv+=fc*(x-bp-.35f*bv);lp+=(x-lp)*.12f;
                    float env=Math.min(1,t/.08f)*(t>.75f?Math.max(0,(1.1f-t)/.35f):1);a[i]=(bp*.5f+lp*.6f)*env*.8f;}
                reverb(a,.3f);return a;}
            case CHARGE:{float[] a=new float[(int)(RATE*.65f)];float ph=0;for(int i=0;i<a.length;i++){float t=i/(float)RATE;float f=300+1500*t*t;ph+=f/RATE;
                a[i]=((float)Math.sin(2*Math.PI*ph)*.4f+(float)Math.sin(4*Math.PI*ph)*.15f+(rng.nextFloat()*2-1)*.06f)*Math.min(1,t/.1f)*.55f;}return a;}
            case FIREBALL:{float[] a=new float[(int)(RATE*.7f)];float lp=0,lp2=0;for(int i=0;i<a.length;i++){float t=i/(float)RATE;float n=rng.nextFloat()*2-1;
                float cut=.04f+.25f*(float)Math.exp(-t*6);lp+=(n-lp)*cut;lp2+=(lp-lp2)*cut;float thump=(float)Math.sin(2*Math.PI*(70+90*Math.exp(-t*20))*t)*(float)Math.exp(-t*10);
                a[i]=(lp2*2.2f*(float)Math.exp(-t*3.5f)+thump*.6f+(float)Math.sin(2*Math.PI*(900-600*t)*t)*.12f*(float)Math.exp(-t*8))*Math.min(1,t/.005f)*.8f;}return a;}
            case EXPLODE:{float[] a=new float[(int)(RATE*1.2f)];float lp=0,lp2=0;for(int i=0;i<a.length;i++){float t=i/(float)RATE;float n=rng.nextFloat()*2-1;
                float cut=Math.max(.015f,.6f*(float)Math.exp(-t*10));lp+=(n-lp)*cut;lp2+=(lp-lp2)*cut;float boom=(float)Math.sin(2*Math.PI*(45+40*Math.exp(-t*25))*t)*(float)Math.exp(-t*6);
                float crackle=rng.nextFloat()>.995f-.01f*(float)Math.exp(-t*4)?(rng.nextFloat()*2-1)*.6f:0;
                a[i]=(lp2*2f*(float)Math.exp(-t*4)+boom*.9f+crackle*(float)Math.exp(-t*3))*.85f;}reverb(a,.4f);return a;}
            case PAIN:{float[] a=new float[(int)(RATE*.45f)];float ph=0,lp=0;for(int i=0;i<a.length;i++){float t=i/(float)RATE;float f=150-60*t;ph+=f/RATE;float buzz=(ph%1f)*2-1;
                lp+=(buzz+(rng.nextFloat()*2-1)*.4f-lp)*.2f;a[i]=lp*Math.min(1,t/.02f)*(float)Math.exp(-t*5)*.9f;}return a;}
            case DIE:{float[] a=new float[(int)(RATE*1.6f)];float ph=0,lp=0;for(int i=0;i<a.length;i++){float t=i/(float)RATE;float f=120*(float)Math.exp(-t*.9f)+25;ph+=f/RATE;float buzz=(ph%1f)*2-1;
                lp+=(buzz*(1+.6f*(rng.nextFloat()*2-1))-lp)*.1f;float clank=t>1.0f&&t<1.4f?(float)Math.sin(2*Math.PI*620*t)*(float)Math.exp(-(t-1f)*14)*.5f+(rng.nextFloat()*2-1)*(float)Math.exp(-(t-1f)*30)*.6f:0;
                a[i]=(lp*Math.min(1,t/.05f)*(t<1?1:(float)Math.exp(-(t-1)*6))+clank)*.85f;}reverb(a,.3f);return a;}
            case HIT:{float[] a=clack(.12f,900,.8f,1700);for(int i=0;i<a.length;i++){float t=i/(float)RATE;a[i]+=(float)Math.sin(2*Math.PI*110*t)*(float)Math.exp(-t*30)*.5f;}return a;}
            case PICKUP:{float[] a=new float[(int)(RATE*.45f)];for(int i=0;i<a.length;i++){float t=i/(float)RATE;float f=t<.08f?660:990;
                a[i]=((float)Math.sin(2*Math.PI*f*t)*.5f+(float)Math.sin(2*Math.PI*f*2.01f*t)*.2f)*(float)Math.exp(-(t<.08f?t:t-.08f)*9)*.6f;}reverb(a,.25f);return a;}
            default:return new float[100];
        }
    }
    /** A metallic clack: a noise burst plus two decaying resonances. */
    private float[] clack(float len,float f1,float amp,float f2){
        float[] a=new float[(int)(RATE*len)];float hp=0,prev=0;
        for(int i=0;i<a.length;i++){float t=i/(float)RATE;float n=rng.nextFloat()*2-1;hp=.8f*(hp+n-prev);prev=n;
            a[i]=(hp*(float)Math.exp(-t*120)*.8f+(float)Math.sin(2*Math.PI*f1*t)*(float)Math.exp(-t*45)*.5f+(float)Math.sin(2*Math.PI*f2*t)*(float)Math.exp(-t*70)*.3f)*amp;}
        return a;
    }
    /** A cheap room tail: a few feedback delays. */
    private static void reverb(float[] a,float mix){
        int[] d={1559,2143,2897,3571};float[] out=a.clone();
        for(int k=0;k<d.length;k++){float g=.5f-k*.06f;for(int i=d[k];i<a.length;i++)out[i]+=out[i-d[k]]*g*mix*.5f;}
        float peak=0;for(float v:out)peak=Math.max(peak,Math.abs(v));if(peak>1)for(int i=0;i<out.length;i++)out[i]/=peak;
        System.arraycopy(out,0,a,0,a.length);
    }
    private static void write(File f,float[] s)throws Exception{
        int n=s.length;byte[] b=new byte[44+n*2];java.nio.ByteBuffer bb=java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.put("RIFF".getBytes()).putInt(36+n*2).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1).putInt(RATE).putInt(RATE*2).putShort((short)2).putShort((short)16)
          .put("data".getBytes()).putInt(n*2);
        for(float v:s)bb.putShort((short)Math.max(-32767,Math.min(32767,v*32767)));
        try(FileOutputStream o=new FileOutputStream(f)){o.write(b);}
    }
}
