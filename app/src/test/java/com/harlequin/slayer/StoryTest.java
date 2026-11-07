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
}
