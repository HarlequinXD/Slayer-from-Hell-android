package android.opengl;
import org.lwjgl.opengl.*;
import java.nio.*;
/**
 * The OpenGL ES 3.0 calls the game uses, on desktop OpenGL 3.3 core (LWJGL). The constants are the same numbers;
 * shaders written for "#version 300 es" are recompiled as "#version 330 core".
 */
public final class GLES30 {
    private GLES30(){}
    public static final int GL_ARRAY_BUFFER=0x8892,GL_BLEND=0x0BE2,GL_CLAMP_TO_EDGE=0x812F,GL_COLOR_ATTACHMENT0=0x8CE0,GL_COLOR_BUFFER_BIT=0x4000,GL_COMPILE_STATUS=0x8B81,
        GL_CULL_FACE=0x0B44,GL_DEPTH_ATTACHMENT=0x8D00,GL_DEPTH_BUFFER_BIT=0x0100,GL_DEPTH_COMPONENT24=0x81A6,GL_DEPTH_TEST=0x0B71,GL_DRAW_FRAMEBUFFER=0x8CA9,
        GL_DST_COLOR=0x0306,GL_FLOAT=0x1406,GL_FRAGMENT_SHADER=0x8B30,GL_FRAMEBUFFER=0x8D40,GL_LEQUAL=0x0203,GL_LINK_STATUS=0x8B82,GL_NEAREST=0x2600,
        GL_NEAREST_MIPMAP_NEAREST=0x2700,GL_ONE=1,GL_ONE_MINUS_SRC_ALPHA=0x0303,GL_POLYGON_OFFSET_FILL=0x8037,GL_READ_FRAMEBUFFER=0x8CA8,GL_RENDERBUFFER=0x8D41,
        GL_REPEAT=0x2901,GL_RG=0x8227,GL_RG8=0x822B,GL_RGBA=0x1908,GL_RGBA8=0x8058,GL_SRGB8_ALPHA8=0x8C43,GL_STATIC_DRAW=0x88E4,GL_STREAM_DRAW=0x88E0,
        GL_TEXTURE0=0x84C0,GL_TEXTURE3=0x84C3,GL_TEXTURE_2D=0x0DE1,GL_TEXTURE_MAG_FILTER=0x2800,GL_TEXTURE_MIN_FILTER=0x2801,GL_TEXTURE_WRAP_S=0x2802,
        GL_TEXTURE_WRAP_T=0x2803,GL_TRIANGLES=4,GL_UNPACK_ALIGNMENT=0x0CF5,GL_UNSIGNED_BYTE=0x1401,GL_VERTEX_SHADER=0x8B31,GL_ZERO=0,GL_LINEAR=0x2601,
        GL_SRC_ALPHA=0x0302,GL_TRIANGLE_STRIP=5,GL_BGRA=0x80E1,GL_UNSIGNED_INT_8_8_8_8_REV=0x8367;

    public static void glActiveTexture(int t){GL13C.glActiveTexture(t);}
    public static void glAttachShader(int p,int s){GL20C.glAttachShader(p,s);}
    public static void glBindBuffer(int t,int b){GL15C.glBindBuffer(t,b);}
    public static void glBindFramebuffer(int t,int f){GL30C.glBindFramebuffer(t,f==0?defaultFramebuffer:f);}
    /** The window's framebuffer (0 normally). */
    public static int defaultFramebuffer=0;
    public static void glBindRenderbuffer(int t,int r){GL30C.glBindRenderbuffer(t,r);}
    public static void glBindTexture(int t,int x){GL11C.glBindTexture(t,x);}
    public static void glBindVertexArray(int a){GL30C.glBindVertexArray(a);}
    public static void glBlendFunc(int s,int d){GL11C.glBlendFunc(s,d);}
    public static void glBlitFramebuffer(int a,int b,int c,int d,int e,int f,int g,int h,int m,int fl){GL30C.glBlitFramebuffer(a,b,c,d,e,f,g,h,m,fl);}
    public static void glBufferData(int t,int size,Buffer data,int usage){
        if(data==null){GL15C.glBufferData(t,(long)size,usage);return;}
        if(data instanceof FloatBuffer){FloatBuffer f=((FloatBuffer)data).duplicate();f.position(0).limit(Math.min(f.capacity(),size/4));GL15C.glBufferData(t,f,usage);}
        else if(data instanceof ByteBuffer){ByteBuffer b=((ByteBuffer)data).duplicate();b.position(0).limit(Math.min(b.capacity(),size));GL15C.glBufferData(t,b,usage);}
        else if(data instanceof IntBuffer){IntBuffer b=((IntBuffer)data).duplicate();b.position(0).limit(Math.min(b.capacity(),size/4));GL15C.glBufferData(t,b,usage);}
        else if(data instanceof ShortBuffer){ShortBuffer b=((ShortBuffer)data).duplicate();b.position(0).limit(Math.min(b.capacity(),size/2));GL15C.glBufferData(t,b,usage);}
    }
    public static void glClear(int m){GL11C.glClear(m);}
    public static void glClearColor(float r,float g,float b,float a){GL11C.glClearColor(r,g,b,a);}
    public static void glCompileShader(int s){GL20C.glCompileShader(s);}
    public static int glCreateProgram(){return GL20C.glCreateProgram();}
    public static int glCreateShader(int t){return GL20C.glCreateShader(t);}
    public static void glDeleteBuffers(int n,int[] a,int o){for(int i=0;i<n;i++)GL15C.glDeleteBuffers(a[o+i]);}
    public static void glDeleteFramebuffers(int n,int[] a,int o){for(int i=0;i<n;i++)GL30C.glDeleteFramebuffers(a[o+i]);}
    public static void glDeleteRenderbuffers(int n,int[] a,int o){for(int i=0;i<n;i++)GL30C.glDeleteRenderbuffers(a[o+i]);}
    public static void glDeleteTextures(int n,int[] a,int o){for(int i=0;i<n;i++)GL11C.glDeleteTextures(a[o+i]);}
    public static void glDeleteVertexArrays(int n,int[] a,int o){for(int i=0;i<n;i++)GL30C.glDeleteVertexArrays(a[o+i]);}
    public static void glDeleteProgram(int p){GL20C.glDeleteProgram(p);}
    public static void glDepthFunc(int f){GL11C.glDepthFunc(f);}
    public static void glDepthMask(boolean m){GL11C.glDepthMask(m);}
    public static void glDisable(int c){GL11C.glDisable(c);}
    public static void glDrawArrays(int m,int f,int n){GL11C.glDrawArrays(m,f,n);}
    public static void glEnable(int c){GL11C.glEnable(c);}
    public static void glEnableVertexAttribArray(int i){GL20C.glEnableVertexAttribArray(i);}
    public static void glFramebufferRenderbuffer(int t,int a,int rt,int r){GL30C.glFramebufferRenderbuffer(t,a,rt,r);}
    public static void glGenBuffers(int n,int[] a,int o){for(int i=0;i<n;i++)a[o+i]=GL15C.glGenBuffers();}
    public static void glGenFramebuffers(int n,int[] a,int o){for(int i=0;i<n;i++)a[o+i]=GL30C.glGenFramebuffers();}
    public static void glGenRenderbuffers(int n,int[] a,int o){for(int i=0;i<n;i++)a[o+i]=GL30C.glGenRenderbuffers();}
    public static void glGenTextures(int n,int[] a,int o){for(int i=0;i<n;i++)a[o+i]=GL11C.glGenTextures();}
    public static void glGenVertexArrays(int n,int[] a,int o){for(int i=0;i<n;i++)a[o+i]=GL30C.glGenVertexArrays();}
    public static void glGenerateMipmap(int t){GL30C.glGenerateMipmap(t);}
    public static String glGetProgramInfoLog(int p){return GL20C.glGetProgramInfoLog(p);}
    public static void glGetProgramiv(int p,int n,int[] a,int o){a[o]=GL20C.glGetProgrami(p,n);}
    public static String glGetShaderInfoLog(int s){return GL20C.glGetShaderInfoLog(s);}
    public static void glGetShaderiv(int s,int n,int[] a,int o){a[o]=GL20C.glGetShaderi(s,n);}
    public static int glGetUniformLocation(int p,String n){return GL20C.glGetUniformLocation(p,n);}
    public static void glLinkProgram(int p){GL20C.glLinkProgram(p);}
    public static void glPixelStorei(int n,int v){GL11C.glPixelStorei(n,v);}
    public static void glPolygonOffset(float f,float u){GL11C.glPolygonOffset(f,u);}
    public static void glRenderbufferStorage(int t,int f,int w,int h){GL30C.glRenderbufferStorage(t,f,w,h);}
    public static void glShaderSource(int s,String src){GL20C.glShaderSource(s,src.replace("#version 300 es","#version 330 core"));}
    public static void glTexImage2D(int t,int l,int ifmt,int w,int h,int b,int fmt,int type,Buffer data){
        if(data==null)GL11C.glTexImage2D(t,l,ifmt,w,h,b,fmt,type,(ByteBuffer)null);
        else if(data instanceof ByteBuffer)GL11C.glTexImage2D(t,l,ifmt,w,h,b,fmt,type,(ByteBuffer)data);
        else if(data instanceof IntBuffer)GL11C.glTexImage2D(t,l,ifmt,w,h,b,fmt,type,(IntBuffer)data);
    }
    public static void glTexSubImage2D(int t,int l,int x,int y,int w,int h,int fmt,int type,IntBuffer data){GL11C.glTexSubImage2D(t,l,x,y,w,h,fmt,type,data);}
    public static void glTexParameteri(int t,int n,int v){GL11C.glTexParameteri(t,n,v);}
    public static void glUniform1f(int l,float a){GL20C.glUniform1f(l,a);}
    public static void glUniform1i(int l,int a){GL20C.glUniform1i(l,a);}
    public static void glUniform2f(int l,float a,float b){GL20C.glUniform2f(l,a,b);}
    public static void glUniform2i(int l,int a,int b){GL20C.glUniform2i(l,a,b);}
    public static void glUniform3f(int l,float a,float b,float c){GL20C.glUniform3f(l,a,b,c);}
    public static void glUniform4f(int l,float a,float b,float c,float d){GL20C.glUniform4f(l,a,b,c,d);}
    private static float[] slice(float[] v,int o,int n){if(o==0&&v.length==n)return v;float[] r=new float[n];System.arraycopy(v,o,r,0,n);return r;}
    public static void glUniform3fv(int l,int n,float[] v,int o){GL20C.glUniform3fv(l,slice(v,o,n*3));}
    public static void glUniform4fv(int l,int n,float[] v,int o){GL20C.glUniform4fv(l,slice(v,o,n*4));}
    public static void glUniformMatrix3fv(int l,int n,boolean t,float[] v,int o){GL20C.glUniformMatrix3fv(l,t,slice(v,o,n*9));}
    public static void glUniformMatrix4fv(int l,int n,boolean t,float[] v,int o){GL20C.glUniformMatrix4fv(l,t,slice(v,o,n*16));}
    public static void glUseProgram(int p){GL20C.glUseProgram(p);}
    public static void glVertexAttribPointer(int i,int n,int t,boolean norm,int stride,int off){GL20C.glVertexAttribPointer(i,n,t,norm,stride,(long)off);}
    public static void glViewport(int x,int y,int w,int h){GL11C.glViewport(x,y,w,h);}
}
