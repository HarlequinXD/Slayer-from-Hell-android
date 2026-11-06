package com.harlequin.slayer;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/** Sound effects synthesised at start-up (no audio files): written as WAVs to the cache and played with SoundPool. */
final class Sfx {
    static final int BLAST=0,PUMP_BACK=1,PUMP_FWD=2,SHELL_IN=3,TINK=4,DRY=5,STEP=6,LAND=7,SIZZLE=8,DRONE=9,JUMP=10,COUNT=11;
    private static final int RATE=44100;
    private final SoundPool pool;private final int[] ids=new int[COUNT];private final boolean[] ready=new boolean[COUNT];
    private final Random rng=new Random(3);private int droneStream;

    Sfx(Context c){
        pool=new SoundPool.Builder().setMaxStreams(16).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((p,id,status)->{for(int i=0;i<COUNT;i++)if(ids[i]==id){ready[i]=status==0;if(i==DRONE&&ready[i])droneStream=p.play(id,.35f,.35f,0,-1,1);}});
        File dir=c.getCacheDir();
        new Thread(()->{for(int i=0;i<COUNT;i++){try{File f=new File(dir,"sfx"+i+".wav");write(f,make(i));ids[i]=pool.load(f.getPath(),1);}catch(Exception ignored){}}},"sfx").start();
    }
    void play(int s,float vol){play(s,vol,1);}
    void play(int s,float vol,float rate){if(s>=0&&s<COUNT&&ready[s])pool.play(ids[s],vol,vol,1,0,rate*(.96f+rng.nextFloat()*.08f));}
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
