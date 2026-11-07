package android.media;
import javax.sound.sampled.*;
/**
 * AudioTrack on the desktop: a streaming 16-bit PCM line (Java Sound). Without an audio device it stays
 * uninitialized and the game plays on silently.
 */
public class AudioTrack {
    public static final int MODE_STREAM=1,STATE_UNINITIALIZED=0,STATE_INITIALIZED=1,PLAYSTATE_STOPPED=1,PLAYSTATE_PAUSED=2,PLAYSTATE_PLAYING=3;
    private SourceDataLine line;private int state=STATE_UNINITIALIZED,play=PLAYSTATE_STOPPED;private final int ch;private byte[] bytes=new byte[0];
    public static int getMinBufferSize(int rate,int channels,int enc){return 8192;}
    public AudioTrack(int stream,int rate,int channels,int enc,int bufferBytes,int mode){
        ch=channels==AudioFormat.CHANNEL_OUT_MONO?1:2;
        try{javax.sound.sampled.AudioFormat f=new javax.sound.sampled.AudioFormat(rate,16,ch,true,false);
            line=AudioSystem.getSourceDataLine(f);line.open(f,Math.max(bufferBytes,rate/10*ch*2));state=STATE_INITIALIZED;}
        catch(Throwable e){line=null;state=STATE_UNINITIALIZED;}
    }
    public int getState(){return state;}
    public int getPlayState(){return play;}
    public void play(){if(line!=null)line.start();play=PLAYSTATE_PLAYING;}
    public void pause(){if(line!=null)line.stop();play=PLAYSTATE_PAUSED;}
    public void stop(){if(line!=null){line.stop();line.flush();}play=PLAYSTATE_STOPPED;}
    public void flush(){if(line!=null)line.flush();}
    public int write(short[] d,int off,int n){
        if(line==null)return n;if(bytes.length<n*2)bytes=new byte[n*2];
        for(int i=0;i<n;i++){short s=d[off+i];bytes[i*2]=(byte)s;bytes[i*2+1]=(byte)(s>>8);}
        line.write(bytes,0,n*2);return n;
    }
    public void release(){if(line!=null){line.close();line=null;}state=STATE_UNINITIALIZED;}
}
