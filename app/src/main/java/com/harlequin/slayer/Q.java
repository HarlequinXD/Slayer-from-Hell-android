package com.harlequin.slayer;

/** Quaternion and small-vector helpers for the physics (quaternions are {x, y, z, w}). Allocation-free variants write into out. */
final class Q {
    private Q(){}
    static float[] id(){return new float[]{0,0,0,1};}
    static void mul(float[] a,float[] b,float[] o){
        float x=a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1],y=a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0],z=a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3],w=a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2];
        o[0]=x;o[1]=y;o[2]=z;o[3]=w;}
    static float[] mul(float[] a,float[] b){float[] o=new float[4];mul(a,b,o);return o;}
    static float[] conj(float[] q){return new float[]{-q[0],-q[1],-q[2],q[3]};}
    static void normalize(float[] q){float l=(float)Math.sqrt(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]+q[3]*q[3]);if(l<1e-12f){q[0]=q[1]=q[2]=0;q[3]=1;return;}q[0]/=l;q[1]/=l;q[2]/=l;q[3]/=l;}
    /** v rotated by q, into o (o may alias v). */
    static void rot(float[] q,float[] v,float[] o){
        float x=q[0],y=q[1],z=q[2],w=q[3];float tx=2*(y*v[2]-z*v[1]),ty=2*(z*v[0]-x*v[2]),tz=2*(x*v[1]-y*v[0]);
        float ox=v[0]+w*tx+(y*tz-z*ty),oy=v[1]+w*ty+(z*tx-x*tz),oz=v[2]+w*tz+(x*ty-y*tx);o[0]=ox;o[1]=oy;o[2]=oz;}
    static float[] rot(float[] q,float[] v){float[] o=new float[3];rot(q,v,o);return o;}
    static void rotInv(float[] q,float[] v,float[] o){float[] c={-q[0],-q[1],-q[2],q[3]};rot(c,v,o);}
    /** Adds the rotation vector dθ (small) to q: q += ½ [dθ,0] q, then normalises. */
    static void addRot(float[] q,float dx,float dy,float dz,float s){
        dx*=s;dy*=s;dz*=s;float x=q[0],y=q[1],z=q[2],w=q[3];
        q[0]+=.5f*(dx*w+dy*z-dz*y);q[1]+=.5f*(dy*w+dz*x-dx*z);q[2]+=.5f*(dz*w+dx*y-dy*x);q[3]+=.5f*(-dx*x-dy*y-dz*z);normalize(q);}
    /** Rotation vector (axis × angle, angle in [0, π]) of q. */
    static void toRotVec(float[] q,float[] o){
        float x=q[0],y=q[1],z=q[2],w=q[3];if(w<0){x=-x;y=-y;z=-z;w=-w;}
        float s=(float)Math.sqrt(x*x+y*y+z*z);if(s<1e-7f){o[0]=2*x;o[1]=2*y;o[2]=2*z;return;}
        float ang=2*(float)Math.atan2(s,w);o[0]=x/s*ang;o[1]=y/s*ang;o[2]=z/s*ang;}
    /** Rotation part of a column-major 4×4 matrix (assumed orthonormal). */
    static float[] fromMatrix(float[] m){
        float m00=m[0],m11=m[5],m22=m[10],tr=m00+m11+m22;float x,y,z,w;
        if(tr>0){float s=(float)Math.sqrt(tr+1)*2;w=.25f*s;x=(m[6]-m[9])/s;y=(m[8]-m[2])/s;z=(m[1]-m[4])/s;}
        else if(m00>m11&&m00>m22){float s=(float)Math.sqrt(1+m00-m11-m22)*2;w=(m[6]-m[9])/s;x=.25f*s;y=(m[4]+m[1])/s;z=(m[8]+m[2])/s;}
        else if(m11>m22){float s=(float)Math.sqrt(1+m11-m00-m22)*2;w=(m[8]-m[2])/s;x=(m[4]+m[1])/s;y=.25f*s;z=(m[9]+m[6])/s;}
        else{float s=(float)Math.sqrt(1+m22-m00-m11)*2;w=(m[1]-m[4])/s;x=(m[8]+m[2])/s;y=(m[9]+m[6])/s;z=.25f*s;}
        float[] q={x,y,z,w};normalize(q);return q;}
    /** Column-major 4×4 from a rotation and a translation. */
    static float[] toMatrix(float[] q,float tx,float ty,float tz){
        float x=q[0],y=q[1],z=q[2],w=q[3];
        return new float[]{1-2*(y*y+z*z),2*(x*y+z*w),2*(x*z-y*w),0, 2*(x*y-z*w),1-2*(x*x+z*z),2*(y*z+x*w),0, 2*(x*z+y*w),2*(y*z-x*w),1-2*(x*x+y*y),0, tx,ty,tz,1};}
    static float[] slerp(float[] a,float[] b,float t){
        float d=a[0]*b[0]+a[1]*b[1]+a[2]*b[2]+a[3]*b[3];float s=1;if(d<0){d=-d;s=-1;}
        float wa,wb;if(d>.9995f){wa=1-t;wb=t;}else{float th=(float)Math.acos(d),sn=(float)Math.sin(th);wa=(float)Math.sin((1-t)*th)/sn;wb=(float)Math.sin(t*th)/sn;}
        float[] o={a[0]*wa+b[0]*wb*s,a[1]*wa+b[1]*wb*s,a[2]*wa+b[2]*wb*s,a[3]*wa+b[3]*wb*s};normalize(o);return o;}
    static boolean finite(float[] a){for(float f:a)if(Float.isNaN(f)||Float.isInfinite(f))return false;return true;}
}
