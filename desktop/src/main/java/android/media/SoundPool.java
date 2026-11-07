package android.media;
import javax.sound.sampled.*;
import java.io.*;
import java.nio.*;
import java.util.*;
/**
 * SoundPool on the desktop: a small software mixer (44.1 kHz stereo) on its own thread. Sounds are 16-bit PCM WAVs
 * loaded into memory; streams play at any rate (linear interpolation), with volume, looping and priority-based
 * voice stealing like Android's.
 */
public class SoundPool {
    public interface OnLoadCompleteListener {void onLoadComplete(SoundPool p,int sampleId,int status);}
    public static class Builder {
        int max=8;public Builder setMaxStreams(int n){max=n;return this;}
        public Builder setAudioAttributes(AudioAttributes a){return this;}
        public SoundPool build(){return new SoundPool(max);}
    }
    private static final int OUT=44100;
    private static final class Sample {float[] d;int rate;}
    private static final class Voice {int id;Sample s;double pos;float l,r,rate;int loop,pri;boolean paused;}
    private final int max;private final Map<Integer,Sample> samples=new HashMap<>();private final List<Voice> voices=new ArrayList<>();
    private int nextSample=1,nextStream=1;private OnLoadCompleteListener listener;private volatile boolean autoPaused;private Thread thread;
    private SoundPool(int max){this.max=max;start();}
    public void setOnLoadCompleteListener(OnLoadCompleteListener l){listener=l;}
    public int load(String path,int priority){
        int id;synchronized(this){id=nextSample++;}
        int status=0;try{Sample s=read(new File(path));synchronized(this){samples.put(id,s);}}catch(Exception e){status=1;}
        if(listener!=null){final int st=status,fid=id;android.os.Handler h=new android.os.Handler();h.post(()->listener.onLoadComplete(this,fid,st));}
        return id;
    }
    private static Sample read(File f)throws IOException{
        byte[] b=java.nio.file.Files.readAllBytes(f.toPath());ByteBuffer bb=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        int rate=44100,ch=1,bits=16,dataOff=-1,dataLen=0;int p=12;
        while(p+8<=b.length){String tag=new String(b,p,4,"US-ASCII");int len=bb.getInt(p+4);
            if(tag.equals("fmt ")){ch=bb.getShort(p+10);rate=bb.getInt(p+12);bits=bb.getShort(p+22);}
            if(tag.equals("data")){dataOff=p+8;dataLen=Math.min(len,b.length-dataOff);break;}p+=8+len+(len&1);}
        if(dataOff<0||bits!=16)throw new IOException("unsupported wav");
        int n=dataLen/2/ch;float[] d=new float[n];for(int i=0;i<n;i++){float s=0;for(int c=0;c<ch;c++)s+=bb.getShort(dataOff+(i*ch+c)*2)/32768f;d[i]=s/ch;}
        Sample s=new Sample();s.d=d;s.rate=rate;return s;
    }
    public synchronized int play(int sampleId,float l,float r,int priority,int loop,float rate){
        Sample s=samples.get(sampleId);if(s==null)return 0;
        if(voices.size()>=max){Voice low=null;for(Voice v:voices)if(low==null||v.pri<low.pri)low=v;if(low==null||low.pri>priority)return 0;voices.remove(low);}
        Voice v=new Voice();v.id=nextStream++;v.s=s;v.l=l;v.r=r;v.rate=rate;v.loop=loop;v.pri=priority;voices.add(v);return v.id;
    }
    private Voice find(int id){for(Voice v:voices)if(v.id==id)return v;return null;}
    public synchronized void setVolume(int id,float l,float r){Voice v=find(id);if(v!=null){v.l=l;v.r=r;}}
    public synchronized void setRate(int id,float rate){Voice v=find(id);if(v!=null)v.rate=rate;}
    public synchronized void stop(int id){Voice v=find(id);if(v!=null)voices.remove(v);}
    public synchronized void pause(int id){Voice v=find(id);if(v!=null)v.paused=true;}
    public synchronized void resume(int id){Voice v=find(id);if(v!=null)v.paused=false;}
    public void autoPause(){autoPaused=true;} public void autoResume(){autoPaused=false;}
    public void release(){Thread t=thread;thread=null;if(t!=null)t.interrupt();}

    private void start(){
        thread=new Thread(()->{
            try{AudioFormat fmt=new AudioFormat(OUT,16,2,true,false);SourceDataLine line=AudioSystem.getSourceDataLine(fmt);line.open(fmt,4096*2);line.start();
                int frames=512;float[] mix=new float[frames*2];byte[] out=new byte[frames*4];
                while(thread!=null){
                    Arrays.fill(mix,0);
                    if(!autoPaused)synchronized(this){
                        for(Iterator<Voice> it=voices.iterator();it.hasNext();){Voice v=it.next();if(v.paused)continue;float[] d=v.s.d;double step=v.rate*v.s.rate/(double)OUT;boolean done=false;
                            for(int i=0;i<frames;i++){int k=(int)v.pos;if(k>=d.length-1){if(v.loop!=0){if(v.loop>0)v.loop--;v.pos-=d.length-1;k=(int)v.pos;}else{done=true;break;}}
                                float f=(float)(v.pos-k);float s=d[k]+(d[Math.min(d.length-1,k+1)]-d[k])*f;mix[i*2]+=s*v.l;mix[i*2+1]+=s*v.r;v.pos+=step;}
                            if(done)it.remove();}}
                    for(int i=0;i<frames*2;i++){float s=mix[i];s=s>1?1:s<-1?-1:s;s=s/(1+.15f*Math.abs(s))*1.15f;int v=(int)(Math.max(-1,Math.min(1,s))*32767);out[i*2]=(byte)v;out[i*2+1]=(byte)(v>>8);}
                    line.write(out,0,out.length);}
                line.drain();line.close();
            }catch(Exception e){System.err.println("audio: "+e);}
        },"mixer");thread.setDaemon(true);thread.start();
    }
}
