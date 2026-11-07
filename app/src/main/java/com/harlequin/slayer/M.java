package com.harlequin.slayer;

/** Column-major 4x4 matrix helpers (same layout as OpenGL), pure Java. */
final class M {
    private M(){}
    /** Inverse of a rigid transform (rotation and translation only). */
    static float[] inv(float[] m){float[] r=id();
        for(int c=0;c<3;c++)for(int k=0;k<3;k++)r[c*4+k]=m[k*4+c];
        for(int k=0;k<3;k++)r[12+k]=-(r[k]*m[12]+r[4+k]*m[13]+r[8+k]*m[14]);return r;}
    static float[] id(){float[] m=new float[16];m[0]=m[5]=m[10]=m[15]=1;return m;}
    static float[] mul(float[] a,float[] b){float[] r=new float[16];
        for(int c=0;c<4;c++)for(int rr=0;rr<4;rr++){float s=0;for(int k=0;k<4;k++)s+=a[k*4+rr]*b[c*4+k];r[c*4+rr]=s;}return r;}
    static float[] tr(float x,float y,float z){float[] m=id();m[12]=x;m[13]=y;m[14]=z;return m;}
    static float[] scale(float x,float y,float z){float[] m=id();m[0]=x;m[5]=y;m[10]=z;return m;}
    /** Rotation about an axis (radians). */
    static float[] rx(float a){float c=(float)Math.cos(a),s=(float)Math.sin(a);float[] m=id();m[5]=c;m[6]=s;m[9]=-s;m[10]=c;return m;}
    static float[] ry(float a){float c=(float)Math.cos(a),s=(float)Math.sin(a);float[] m=id();m[0]=c;m[2]=-s;m[8]=s;m[10]=c;return m;}
    static float[] rz(float a){float c=(float)Math.cos(a),s=(float)Math.sin(a);float[] m=id();m[0]=c;m[1]=s;m[4]=-s;m[5]=c;return m;}
    /** Matrix whose columns are the axes X, Y, Z and origin O. */
    static float[] basis(float[] o,float[] x,float[] y,float[] z){return new float[]{x[0],x[1],x[2],0,y[0],y[1],y[2],0,z[0],z[1],z[2],0,o[0],o[1],o[2],1};}
    static float[] pt(float[] m,float[] p){return new float[]{m[0]*p[0]+m[4]*p[1]+m[8]*p[2]+m[12],m[1]*p[0]+m[5]*p[1]+m[9]*p[2]+m[13],m[2]*p[0]+m[6]*p[1]+m[10]*p[2]+m[14]};}
    static float[] dir(float[] m,float[] p){return new float[]{m[0]*p[0]+m[4]*p[1]+m[8]*p[2],m[1]*p[0]+m[5]*p[1]+m[9]*p[2],m[2]*p[0]+m[6]*p[1]+m[10]*p[2]};}
    /** Normal matrix: inverse transpose of the upper 3x3 (column-major 3x3). */
    static float[] nrm(float[] m){
        float a=m[0],b=m[4],c=m[8],d=m[1],e=m[5],f=m[9],g=m[2],h=m[6],i=m[10];
        float A=e*i-f*h,B=-(d*i-f*g),C=d*h-e*g,det=a*A+b*B+c*C;if(Math.abs(det)<1e-12f)det=1e-12f;float k=1/det;
        // inverse transpose = cofactor matrix / det
        float C10=-(b*i-c*h),C11=a*i-c*g,C12=-(a*h-b*g),C20=b*f-c*e,C21=-(a*f-c*d),C22=a*e-b*d;
        return new float[]{A*k,C10*k,C20*k,B*k,C11*k,C21*k,C*k,C12*k,C22*k};   // columns of the cofactor matrix / det
    }
    static float[] perspective(float fovy,float aspect,float n,float f){float t=1/(float)Math.tan(fovy/2);float[] m=new float[16];
        m[0]=t/aspect;m[5]=t;m[10]=(f+n)/(n-f);m[11]=-1;m[14]=2*f*n/(n-f);return m;}
    static float[] lookAt(float[] eye,float[] fwd,float[] up){
        float[] f=Model.norm(fwd),s=Model.norm(Model.cross(f,up)),u=Model.cross(s,f);
        return new float[]{s[0],u[0],-f[0],0,s[1],u[1],-f[1],0,s[2],u[2],-f[2],0,-Model.dot(s,eye),-Model.dot(u,eye),Model.dot(f,eye),1};
    }
}
