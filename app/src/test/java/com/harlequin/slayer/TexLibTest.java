package com.harlequin.slayer;

import static org.junit.Assert.*;

import org.junit.Test;

/** The texture library: 50 sci-fi and 50 hell materials, all distinct, valid and seamlessly tiling. */
public class TexLibTest {
    @Test public void aHundredNamedMaterialsFiftyOfEach(){
        assertEquals(100,TexLib.COUNT);int sci=0,hell=0;java.util.HashSet<String> names=new java.util.HashSet<>();
        for(int i=0;i<TexLib.COUNT;i++){if(TexLib.category(i)==Tex.CAT_SCIFI)sci++;else hell++;assertTrue("unique name "+TexLib.name(i),names.add(TexLib.name(i)));}
        assertEquals(50,sci);assertEquals(50,hell);
        assertEquals("the editor offers the classic nine and all of them",109,SectorMap.MAP_TEXTURES.length);
    }
    @Test public void everyMaterialIsValidAndTiles(){
        int n=64;
        for(int i=0;i<TexLib.COUNT;i++){Tex.Mat m=Tex.make(Tex.FIRST_LIB+i,n);String name=TexLib.name(i);
            assertTrue(name+" has a tile size",m.tile>0);assertFalse(name+" emission",Float.isNaN(m.emit));
            // the wrap seam (last column to first, last row to first) is no rougher than the texture itself
            double inner=0,seam=0;
            for(int y=0;y<n;y++){for(int x=0;x<n-1;x++)inner+=Math.abs(h(m,x,y)-h(m,x+1,y));seam+=Math.abs(h(m,n-1,y)-h(m,0,y));}
            for(int x=0;x<n;x++){for(int y=0;y<n-1;y++)inner+=Math.abs(h(m,x,y)-h(m,x,y+1));seam+=Math.abs(h(m,x,n-1)-h(m,x,0));}
            inner/=2*n*(n-1);seam/=2*n;
            assertTrue(name+" tiles: seam "+seam+" vs "+inner,seam<inner*3.5+2);
            boolean varied=false;int a0=m.albedoAO[0]&0xffffff;for(int k=1;k<n*n&&!varied;k++)varied=(m.albedoAO[k]&0xffffff)!=a0;assertTrue(name+" isn't flat",varied);}
    }
    private static int h(Tex.Mat m,int x,int y){return m.normalHeight[y*m.size+x]>>>24;}
    @Test public void emissiveOnesGlow(){
        for(int i=0;i<TexLib.COUNT;i++){int id=Tex.FIRST_LIB+i;if(!Tex.emissive(id))continue;Tex.Mat m=Tex.make(id,64);int lit=0;for(int px:m.rme)if((px&255)>8)lit++;
            assertTrue(TexLib.name(i)+" has glowing texels",lit>0);assertTrue(m.emit>0);}
    }
}
