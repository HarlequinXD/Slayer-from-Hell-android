package com.harlequin.slayer;

import android.opengl.GLES30;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Small OpenGL ES 3 helpers: programs, textures, static meshes. */
final class Gfx {
    private Gfx(){}
    static final String TAG="Slayer";

    static int program(String vs,String fs){
        int v=shader(GLES30.GL_VERTEX_SHADER,vs),f=shader(GLES30.GL_FRAGMENT_SHADER,fs);
        int p=GLES30.glCreateProgram();GLES30.glAttachShader(p,v);GLES30.glAttachShader(p,f);GLES30.glLinkProgram(p);
        int[] ok=new int[1];GLES30.glGetProgramiv(p,GLES30.GL_LINK_STATUS,ok,0);
        if(ok[0]==0)throw new RuntimeException("link: "+GLES30.glGetProgramInfoLog(p));
        return p;
    }
    private static int shader(int type,String src){
        int s=GLES30.glCreateShader(type);GLES30.glShaderSource(s,src);GLES30.glCompileShader(s);
        int[] ok=new int[1];GLES30.glGetShaderiv(s,GLES30.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0){String log=GLES30.glGetShaderInfoLog(s);Log.e(TAG,log);throw new RuntimeException("shader: "+log);}
        return s;
    }
    static FloatBuffer floats(float[] a,int n){FloatBuffer b=ByteBuffer.allocateDirect(Math.max(4,n*4)).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(a,0,n).position(0);return b;}

    /** Uploads an ARGB image as RGBA with mipmaps (sRGB for colour maps), point filtering, repeating. */
    static int texture(int[] argb,int w,int h,boolean srgb,boolean repeat){
        ByteBuffer b=ByteBuffer.allocateDirect(w*h*4).order(ByteOrder.nativeOrder());
        for(int c:argb){b.put((byte)(c>>16));b.put((byte)(c>>8));b.put((byte)c);b.put((byte)(c>>>24));}
        b.position(0);
        int[] t=new int[1];GLES30.glGenTextures(1,t,0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,t[0]);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT,1);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,srgb?GLES30.GL_SRGB8_ALPHA8:GLES30.GL_RGBA8,w,h,0,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,b);
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
        // point sampling everywhere (nearest texel, nearest mip level): crisp retro texels
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST_MIPMAP_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST);
        int wrap=repeat?GLES30.GL_REPEAT:GLES30.GL_CLAMP_TO_EDGE;
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,wrap);GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,wrap);
        return t[0];
    }
    /** Nearest-filtered RG8 texture (the level's light visibility masks). */
    static int maskTexture(int[] mask,int w,int h){
        ByteBuffer b=ByteBuffer.allocateDirect(w*h*2).order(ByteOrder.nativeOrder());
        for(int m:mask){b.put((byte)(m&255));b.put((byte)((m>>8)&255));}b.position(0);
        int[] t=new int[1];GLES30.glGenTextures(1,t,0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,t[0]);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT,1);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D,0,GLES30.GL_RG8,w,h,0,GLES30.GL_RG,GLES30.GL_UNSIGNED_BYTE,b);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MIN_FILTER,GLES30.GL_NEAREST);GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_MAG_FILTER,GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_S,GLES30.GL_CLAMP_TO_EDGE);GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,GLES30.GL_TEXTURE_WRAP_T,GLES30.GL_CLAMP_TO_EDGE);
        return t[0];
    }

    /** A static triangle mesh in a VAO: position, normal, tangent, uv (11 floats per vertex). */
    static final class Mesh {
        int vao,vbo,count;
        Mesh(float[] v){
            count=v.length/11;if(count==0)return;
            int[] a=new int[1];GLES30.glGenVertexArrays(1,a,0);vao=a[0];GLES30.glBindVertexArray(vao);
            GLES30.glGenBuffers(1,a,0);vbo=a[0];GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,v.length*4,floats(v,v.length),GLES30.GL_STATIC_DRAW);
            int st=11*4;
            GLES30.glEnableVertexAttribArray(0);GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,st,0);
            GLES30.glEnableVertexAttribArray(1);GLES30.glVertexAttribPointer(1,3,GLES30.GL_FLOAT,false,st,12);
            GLES30.glEnableVertexAttribArray(2);GLES30.glVertexAttribPointer(2,3,GLES30.GL_FLOAT,false,st,24);
            GLES30.glEnableVertexAttribArray(3);GLES30.glVertexAttribPointer(3,2,GLES30.GL_FLOAT,false,st,36);
            GLES30.glBindVertexArray(0);
        }
        void draw(){if(count==0)return;GLES30.glBindVertexArray(vao);GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,count);}
    }

    /** A dynamic vertex stream (particles, decals) with a given attribute layout. */
    static final class Stream {
        final int vao,vbo,stride;final int[] sizes;FloatBuffer buf;int cap;int mode=GLES30.GL_TRIANGLES;
        Stream(int... sizes){this.sizes=sizes;int s=0;for(int z:sizes)s+=z;stride=s;
            int[] a=new int[1];GLES30.glGenVertexArrays(1,a,0);vao=a[0];GLES30.glGenBuffers(1,a,0);vbo=a[0];}
        void draw(float[] v,int floats){
            if(floats<=0)return;int n=floats/stride;
            if(buf==null||buf.capacity()<floats){cap=Math.max(floats,4096);buf=ByteBuffer.allocateDirect(cap*4).order(ByteOrder.nativeOrder()).asFloatBuffer();}
            buf.clear();buf.put(v,0,floats).position(0);
            GLES30.glBindVertexArray(vao);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,floats*4,buf,GLES30.GL_STREAM_DRAW);
            int off=0;for(int i=0;i<sizes.length;i++){GLES30.glEnableVertexAttribArray(i);GLES30.glVertexAttribPointer(i,sizes[i],GLES30.GL_FLOAT,false,stride*4,off*4);off+=sizes[i];}
            GLES30.glDrawArrays(mode,0,n);
        }
    }
}
