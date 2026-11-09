package com.harlequin.slayer;

import org.junit.Test;
import static org.junit.Assert.*;

public class StoryTest {
    @Test public void aChapterBeforeEveryMapAndAnEpilogue(){
        assertEquals(Maps.count()+1,Story.CHAPTERS.length);
        for(int i=0;i<Maps.count();i++){Story.Chapter c=Story.before(i);assertNotNull(c);assertTrue(c.text.length()>600);}
        assertTrue(Story.epilogue().text.contains("Slayer"));
        assertEquals(Maps.count(),Maps.BLURBS.length);
    }
    @Test public void musicRendersCleanLoops(){
        for(int th=0;th<2;th++){MusicSynth s=new MusicSynth(th);
            MusicSynth.Track c=s.combat(),a=s.ambient(),i=s.impact();
            assertEquals(MusicSynth.BAR*MusicSynth.BARS,c.length());
            for(MusicSynth.Track t:new MusicSynth.Track[]{c,a,i}){float pk=0;double sum=0;
                for(int k=0;k<t.length();k++){assertFalse(Float.isNaN(t.l[k])||Float.isNaN(t.r[k]));pk=Math.max(pk,Math.abs(t.l[k]));sum+=t.l[k]*t.l[k];}
                assertTrue("not clipping",pk<=1f);assertTrue("audible",Math.sqrt(sum/t.length())>.05f);}
            // the loop seam: the last and first samples are close (no click)
            assertEquals(a.l[a.length()-1],a.l[0],.08f);
        }
    }

    @Test public void narrationReadsEveryParagraph(){
        for(Story.Chapter ch:Story.CHAPTERS){java.util.ArrayList<int[]> sp=Narrator.spans(ch.text);assertTrue(sp.size()>=3);
            StringBuilder all=new StringBuilder();
            for(int[] s:sp){String p=ch.text.substring(s[0],s[1]);assertFalse(p.trim().isEmpty());assertFalse(p.contains("\n"));all.append(p);}
            assertEquals(ch.text.replace("\n",""),all.toString());}
    }
    @Test public void trailerVoiceAndBraams() throws Exception {
        // a WAV as a speech engine writes it (16-bit mono, 22.05 kHz, with silence either side) goes in ...
        int rate=22050,n=rate*2;java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();java.io.DataOutputStream o=new java.io.DataOutputStream(b);
        o.writeBytes("RIFF");o.writeInt(Integer.reverseBytes(36+n*2));o.writeBytes("WAVEfmt ");o.writeInt(Integer.reverseBytes(16));o.writeShort(Short.reverseBytes((short)1));
        o.writeShort(Short.reverseBytes((short)1));o.writeInt(Integer.reverseBytes(rate));o.writeInt(Integer.reverseBytes(rate*2));o.writeShort(Short.reverseBytes((short)2));o.writeShort(Short.reverseBytes((short)16));
        o.writeBytes("data");o.writeInt(Integer.reverseBytes(n*2));
        for(int i=0;i<n;i++){double t=i/(double)rate;double env=t>.3&&t<1.6?1:0;o.writeShort(Short.reverseBytes((short)(env*8000*Math.sin(2*Math.PI*180*t)*(1+.5*Math.sin(2*Math.PI*900*t)))));}
        float[][] w=Narrator.readWav(new java.io.ByteArrayInputStream(b.toByteArray()));
        assertEquals(n,w[0].length);assertEquals(rate,(int)w[1][0]);
        // ... and comes out stereo at 44.1 kHz, silence trimmed, a reverb tail added, loud but not clipping
        short[] v=Narrator.trailer(w[0],rate);int frames=v.length/2;
        assertTrue("trimmed",frames<Narrator.RATE*3.2f);assertTrue("kept the speech",frames>Narrator.RATE*2.5f);
        int pk=0;for(short s:v)pk=Math.max(pk,Math.abs(s));assertTrue(pk>20000&&pk<32767);
        short[] br=Narrator.braam(5f,new java.util.Random(1));assertEquals(Narrator.RATE*5*2,br.length);
        double sum=0;pk=0;for(short s:br){sum+=s*(double)s;pk=Math.max(pk,Math.abs(s));}
        assertTrue("audible",Math.sqrt(sum/br.length)>2000);assertTrue("not clipping",pk<32767);
        assertTrue("fades out",Math.abs(br[br.length-2])<200);
    }
}
