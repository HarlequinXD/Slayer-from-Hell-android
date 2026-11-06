package com.harlequin.slayer;

/**
 * Builds triangle meshes for 3D models (the shotgun, hands, shells) in the level's vertex format
 * (position, normal, tangent, uv). Tangents come from the uv layout, so normal maps line up on every face.
 * Pure Java. Units: metres; uvs are scaled by `tile` repeats per metre.
 */
final class Model {
    final Level.FloatList f=new Level.FloatList();
    float tile=6;
    Model(float tile){this.tile=tile;}

    static float[] v(float x,float y,float z){return new float[]{x,y,z};}
    static float[] sub(float[] a,float[] b){return v(a[0]-b[0],a[1]-b[1],a[2]-b[2]);}
    static float[] add(float[] a,float[] b,float s){return v(a[0]+b[0]*s,a[1]+b[1]*s,a[2]+b[2]*s);}
    static float[] cross(float[] a,float[] b){return v(a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]);}
    static float dot(float[] a,float[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    static float[] norm(float[] a){float l=(float)Math.sqrt(dot(a,a));return l<1e-9f?v(0,0,1):v(a[0]/l,a[1]/l,a[2]/l);}

    /** Triangle with explicit normals (null = flat) and uvs; the tangent follows the uv's u direction. */
    void tri(float[] a,float[] b,float[] c,float[] ua,float[] ub,float[] uc,float[] na,float[] nb,float[] nc){
        float[] e1=sub(b,a),e2=sub(c,a);float du1=ub[0]-ua[0],dv1=ub[1]-ua[1],du2=uc[0]-ua[0],dv2=uc[1]-ua[1];
        float r=du1*dv2-du2*dv1;float[] t;if(Math.abs(r)<1e-12f)t=norm(e1);else{float k=1/r;t=norm(v((e1[0]*dv2-e2[0]*dv1)*k,(e1[1]*dv2-e2[1]*dv1)*k,(e1[2]*dv2-e2[2]*dv1)*k));}
        float[] fn=norm(cross(e1,e2));if(na==null){na=nb=nc=fn;}
        put(a,na,t,ua);put(b,nb,t,ub);put(c,nc,t,uc);
    }
    /** Optional local frame (origin + axes) applied to everything added; null = identity. */
    private float[] fo,fx,fy,fz;
    void frame(float[] o,float[] x,float[] y,float[] z){fo=o;fx=x;fy=y;fz=z;}
    void noFrame(){fo=null;}
    private float[] tp(float[] p){return fo==null?p:v(fo[0]+fx[0]*p[0]+fy[0]*p[1]+fz[0]*p[2],fo[1]+fx[1]*p[0]+fy[1]*p[1]+fz[1]*p[2],fo[2]+fx[2]*p[0]+fy[2]*p[1]+fz[2]*p[2]);}
    private float[] td(float[] d){return fo==null?d:norm(v(fx[0]*d[0]+fy[0]*d[1]+fz[0]*d[2],fx[1]*d[0]+fy[1]*d[1]+fz[1]*d[2],fx[2]*d[0]+fy[2]*d[1]+fz[2]*d[2]));}
    private void put(float[] p,float[] n,float[] t,float[] uv){p=tp(p);n=td(n);t=td(t);f.add(p[0],p[1],p[2],n[0],n[1],n[2],t[0],t[1],t[2],uv[0],uv[1]);}
    /** Flat quad a-b-c-d (counter-clockwise from the front), uv from the edge lengths. */
    void quad(float[] a,float[] b,float[] c,float[] d){
        float w=(float)Math.sqrt(dot(sub(b,a),sub(b,a)))*tile,h=(float)Math.sqrt(dot(sub(d,a),sub(d,a)))*tile;
        float[] u0={0,0},u1={w,0},u2={w,h},u3={0,h};tri(a,b,c,u0,u1,u2,null,null,null);tri(a,c,d,u0,u2,u3,null,null,null);
    }
    /**
     * A box along x from x0 to x1 whose cross-section (half-width hy, half-height hz) and centre (cy, cz) can change
     * from one end to the other: barrels' shrouds, stocks, forearms.
     */
    void taper(float x0,float x1,float hy0,float hz0,float hy1,float hz1,float cy0,float cz0,float cy1,float cz1){
        float[][] A=ring(x0,hy0,hz0,cy0,cz0),B=ring(x1,hy1,hz1,cy1,cz1);
        for(int i=0;i<4;i++){int j=(i+1)%4;quad(A[i],A[j],B[j],B[i]);}
        quad(A[3],A[2],A[1],A[0]);quad(B[0],B[1],B[2],B[3]);
    }
    private static float[][] ring(float x,float hy,float hz,float cy,float cz){return new float[][]{v(x,cy-hy,cz-hz),v(x,cy+hy,cz-hz),v(x,cy+hy,cz+hz),v(x,cy-hy,cz+hz)};}
    /** Axis-aligned box. */
    void box(float x0,float y0,float z0,float x1,float y1,float z1){taper(x0,x1,(y1-y0)/2,(z1-z0)/2,(y1-y0)/2,(z1-z0)/2,(y0+y1)/2,(z0+z1)/2,(y0+y1)/2,(z0+z1)/2);}
    /** Smooth n-sided cylinder (or cone) along x, centre (cy,cz), optional flat caps. */
    void cyl(float x0,float x1,float r0,float r1,float cy,float cz,int n,boolean cap0,boolean cap1){
        float circ=(float)(2*Math.PI*Math.max(r0,r1))*tile,len=(x1-x0)*tile;
        for(int i=0;i<n;i++){double a0=i*2*Math.PI/n,a1=(i+1)*2*Math.PI/n;
            float c0=(float)Math.cos(a0),s0=(float)Math.sin(a0),c1=(float)Math.cos(a1),s1=(float)Math.sin(a1);
            float[] p00=v(x0,cy+c0*r0,cz+s0*r0),p01=v(x0,cy+c1*r0,cz+s1*r0),p10=v(x1,cy+c0*r1,cz+s0*r1),p11=v(x1,cy+c1*r1,cz+s1*r1);
            float[] n0=v(0,c0,s0),n1=v(0,c1,s1);float u0=i*circ/n,u1=(i+1)*circ/n;
            tri(p00,p10,p11,new float[]{0,u0},new float[]{len,u0},new float[]{len,u1},n0,n0,n1);
            tri(p00,p11,p01,new float[]{0,u0},new float[]{len,u1},new float[]{0,u1},n0,n1,n1);
            if(cap0)tri(v(x0,cy,cz),p01,p00,new float[]{0,0},new float[]{c1*r0*tile,s1*r0*tile},new float[]{c0*r0*tile,s0*r0*tile},null,null,null);
            if(cap1)tri(v(x1,cy,cz),p10,p11,new float[]{0,0},new float[]{c0*r1*tile,s0*r1*tile},new float[]{c1*r1*tile,s1*r1*tile},null,null,null);}
    }
    float[] data(){return f.toArray();}
}
