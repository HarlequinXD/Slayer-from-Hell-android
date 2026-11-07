import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/**
 * Offline high-to-low bake for the demon's hand cannon: an armoured sleeve over his right forearm (padded opening,
 * riveted bands, a dorsal row of spikes) that ends in a heavy barrel with cooling rings, glowing blue vents and a
 * slotted muzzle crown. A caged plasma core rides on top and feeds the barrel through two conduits. Paint: the same
 * blood red as his armour; the core, vents and bore glow blue (emission).
 *
 * 1. HIGH POLY: signed distance field with sub-millimetre detail.
 * 2. LOW POLY: box modelled, under 1000 triangles; the small detail goes in the bake.
 * 3. BAKE: normal, height, AO, albedo, roughness, metalness, emission on a 512 atlas.
 *
 * Cannon space is the chainsaw's: x along the forearm (0 = the sleeve's opening at the elbow end), y left, z up.
 * The muzzle is at x = .675 on the axis. Output: cannon.bin (one group), cannon_a/b/c.png, previews.
 *
 *   javac CannonBaker.java && java -Xmx4g CannonBaker <assets dir> <preview dir> [low|high]
 */
public class CannonBaker {
    // ================================================================== tiny vector helpers
    static float[] v(float x,float y,float z){return new float[]{x,y,z};}
    static float[] sub(float[] a,float[] b){return v(a[0]-b[0],a[1]-b[1],a[2]-b[2]);}
    static float[] add(float[] a,float[] b,float s){return v(a[0]+b[0]*s,a[1]+b[1]*s,a[2]+b[2]*s);}
    static float[] mul(float[] a,float s){return v(a[0]*s,a[1]*s,a[2]*s);}
    static float dot(float[] a,float[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    static float[] cross(float[] a,float[] b){return v(a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]);}
    static float len(float[] a){return (float)Math.sqrt(dot(a,a));}
    static float[] norm(float[] a){float l=len(a);return l<1e-12f?v(0,0,1):mul(a,1/l);}
    static float clamp(float x,float a,float b){return x<a?a:x>b?b:x;}
    static float smooth(float a,float b,float x){float t=clamp((x-a)/(b-a),0,1);return t*t*(3-2*t);}
    static float mix(float a,float b,float t){return a+(b-a)*t;}

    // ================================================================== noise (for materials and engraving)
    static float hash(int x,int y,int z){int h=x*374761393+y*668265263+z*1274126177;h=(h^(h>>>13))*1274126177;return ((h^(h>>>16))&0xffffff)/16777215f;}
    static float noise(float x,float y,float z){
        int xi=(int)Math.floor(x),yi=(int)Math.floor(y),zi=(int)Math.floor(z);float fx=x-xi,fy=y-yi,fz=z-zi;
        fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);fz=fz*fz*(3-2*fz);
        float a=mix(mix(hash(xi,yi,zi),hash(xi+1,yi,zi),fx),mix(hash(xi,yi+1,zi),hash(xi+1,yi+1,zi),fx),fy);
        float b=mix(mix(hash(xi,yi,zi+1),hash(xi+1,yi,zi+1),fx),mix(hash(xi,yi+1,zi+1),hash(xi+1,yi+1,zi+1),fx),fy);
        return mix(a,b,fz);
    }
    static float fbm(float x,float y,float z,int o){float s=0,a=.5f,n=0;for(int i=0;i<o;i++){s+=noise(x,y,z)*a;n+=a;a*=.5f;x*=2.03f;y*=2.03f;z*=2.03f;}return s/n;}

    // ================================================================== SDF primitives
    static float box(float px,float py,float pz,float cx,float cy,float cz,float hx,float hy,float hz,float r){
        float qx=Math.abs(px-cx)-hx+r,qy=Math.abs(py-cy)-hy+r,qz=Math.abs(pz-cz)-hz+r;
        float ox=Math.max(qx,0),oy=Math.max(qy,0),oz=Math.max(qz,0);
        return (float)Math.sqrt(ox*ox+oy*oy+oz*oz)+Math.min(Math.max(qx,Math.max(qy,qz)),0)-r;
    }
    /** Cylinder along x from x0 to x1, radius r, centre (cy,cz). */
    static float cylX(float px,float py,float pz,float cy,float cz,float r,float x0,float x1){
        float d=(float)Math.hypot(py-cy,pz-cz)-r;float e=Math.max(x0-px,px-x1);
        float ox=Math.max(d,0),oy=Math.max(e,0);return Math.min(Math.max(d,e),0)+(float)Math.sqrt(ox*ox+oy*oy);
    }
    static float cylY(float px,float py,float pz,float cx,float cz,float r,float y0,float y1){return cylX(py,px,pz,cx,cz,r,y0,y1);}
    static float cylZ(float px,float py,float pz,float cx,float cy,float r,float z0,float z1){return cylX(pz,px,py,cx,cy,r,z0,z1);}
    static float sphere(float px,float py,float pz,float cx,float cy,float cz,float r){return (float)Math.sqrt((px-cx)*(px-cx)+(py-cy)*(py-cy)+(pz-cz)*(pz-cz))-r;}
    static float capsule(float px,float py,float pz,float[] a,float[] b,float r){
        float bx=b[0]-a[0],by=b[1]-a[1],bz=b[2]-a[2],qx=px-a[0],qy=py-a[1],qz=pz-a[2];
        float h=clamp((qx*bx+qy*by+qz*bz)/(bx*bx+by*by+bz*bz),0,1);
        float dx=qx-bx*h,dy=qy-by*h,dz=qz-bz*h;return (float)Math.sqrt(dx*dx+dy*dy+dz*dz)-r;
    }
    static float smin(float a,float b,float k){float h=clamp(.5f+.5f*(b-a)/k,0,1);return mix(b,a,h)-k*h*(1-h);}


    // ================================================================== the high poly sculpt: the hand cannon
    static final int M_PAINT=0,M_STEEL=1,M_GUN=2,M_LEATHER=3,M_RUBBER=4,M_BRASS=5,M_GLOW=6,M_HOLE=7;
    static float sdfAll(float x,float y,float z,int[] mat){return body(x,y,z,mat);}
    static float sdf(int g,float x,float y,float z,int[] mat){return body(x,y,z,mat);}
    static float bound(float x,float y,float z,float x0,float x1,float y0,float y1,float z0,float z1){
        float dx=Math.max(x0-x,x-x1),dy=Math.max(y0-y,y-y1),dz=Math.max(z0-z,z-z1);
        return (float)Math.sqrt(Math.max(dx,0)*Math.max(dx,0)+Math.max(dy,0)*Math.max(dy,0)+Math.max(dz,0)*Math.max(dz,0));
    }
    static float cone(float x,float y,float z,float[] a,float[] b,float ra,float rb){
        float bx=b[0]-a[0],by=b[1]-a[1],bz=b[2]-a[2],l2=bx*bx+by*by+bz*bz,qx=x-a[0],qy=y-a[1],qz=z-a[2];
        float h=clamp((qx*bx+qy*by+qz*bz)/l2,0,1);float dx=qx-bx*h,dy=qy-by*h,dz=qz-bz*h;
        return (float)Math.sqrt(dx*dx+dy*dy+dz*dz)-mix(ra,rb,h);
    }
    /** Signed distance to a closed 2D polygon (pts = x0,y0,x1,y1,...). */
    static float poly(float px,float py,float[] p){
        int n=p.length/2;float d=1e9f;boolean in=false;
        for(int i=0,j=n-1;i<n;j=i++){float ax=p[j*2],ay=p[j*2+1],bx=p[i*2],by=p[i*2+1];float ex=bx-ax,ey=by-ay,wx=px-ax,wy=py-ay;
            float t=clamp((wx*ex+wy*ey)/(ex*ex+ey*ey),0,1);float dx=wx-ex*t,dy=wy-ey*t;d=Math.min(d,dx*dx+dy*dy);
            if((ay>py)!=(by>py)&&px<ax+(py-ay)*ex/ey)in=!in;}
        return (in?-1:1)*(float)Math.sqrt(d);
    }
    /** Catmull-Rom polyline (pts as {x,y,z}...) sampled into a tube. */
    static float[][] spline(float[][] c,int per){
        ArrayList<float[]> out=new ArrayList<>();
        for(int i=0;i+1<c.length;i++){float[] p0=c[Math.max(0,i-1)],p1=c[i],p2=c[i+1],p3=c[Math.min(c.length-1,i+2)];
            for(int k=0;k<per;k++){float t=k/(float)per,t2=t*t,t3=t2*t;float[] r=new float[3];
                for(int j=0;j<3;j++)r[j]=.5f*(2*p1[j]+(-p0[j]+p2[j])*t+(2*p0[j]-5*p1[j]+4*p2[j]-p3[j])*t2+(-p0[j]+3*p1[j]-3*p2[j]+p3[j])*t3);out.add(r);}}
        out.add(c[c.length-1]);return out.toArray(new float[0][]);
    }
    static float tube(float x,float y,float z,float[][] pts,float r){float d=1e9f;for(int i=0;i+1<pts.length;i++)d=Math.min(d,cone(x,y,z,pts[i],pts[i+1],r,r));return d;}
    static float[] tubeBox(float[][] pts,float r){float[] b={1e9f,-1e9f,1e9f,-1e9f,1e9f,-1e9f};for(float[] p:pts)for(int k=0;k<3;k++){b[k*2]=Math.min(b[k*2],p[k]-r);b[k*2+1]=Math.max(b[k*2+1],p[k]+r);}return b;}
    static float tubeB(float x,float y,float z,float[][] pts,float[] bb,float r){float d=bound(x,y,z,bb[0],bb[1],bb[2],bb[3],bb[4],bb[5]);return d>.01f?d:tube(x,y,z,pts,r);}
    static final float SY=.94f;
    static final float[] SLEEVE={0,.062f, .004f,.074f, .04f,.078f, .3f,.078f, .335f,.071f, .36f,.056f, .362f,0, .3f,0, .3f,.05f, .02f,.054f, 0,.055f};
    static final float BX0=.33f,BX1=.6f,BR=.052f,BORE=.03f,MZ0=.598f,MZ1=.675f,MR=.064f;   // barrel and muzzle crown
    static final float[] CORE0={.1f,0,.118f},CORE1={.285f,0,.118f};static final float CR=.036f;
    static final float[][] COND0=spline(new float[][]{{.285f,.02f,.118f},{.33f,.024f,.11f},{.37f,.024f,.08f},{.4f,.02f,.05f}},10),COND1=spline(new float[][]{{.285f,-.02f,.118f},{.33f,-.024f,.11f},{.37f,-.024f,.08f},{.4f,-.02f,.05f}},10);
    static final float[] COND_B=tubeBox(COND0,.008f),COND_B1=tubeBox(COND1,.008f);
    static final float[] RINGS={.4f,.445f,.49f,.535f,.575f};

    static float body(float x,float y,float z,int[] mat){
        float bd=bound(x,y,z,-.02f,.7f,-.1f,.1f,-.1f,.18f);if(bd>.01f){mat[0]=M_PAINT;return bd;}
        float d=1e9f;int m=M_PAINT;float e;
        float r=(float)Math.hypot(y/SY,z);
        // sleeve: shell, padded rim, riveted bands, dorsal spikes
        if(x<.38f){
            e=poly(x,r,SLEEVE)*.97f;if(Math.abs(z)<.003f&&y<0)e+=.0012f;
            float pl=((x-.02f)/.045f)%1f;if(x>.02f&&x<.3f&&r>.07f&&Math.abs(pl-.5f)>.44f)e+=.0025f;   // overlapping plate edges
            if(e<d){d=e;m=M_PAINT;}
            float dr=r-.064f;e=(float)Math.sqrt(x*x+dr*dr)-.01f;e+=.0004f*(float)Math.sin(Math.atan2(z,y)*90);if(e<d){d=e;m=M_LEATHER;}
            if(x>.02f&&x<.3f&&r<.055f&&poly(x,r,SLEEVE)<.002f)m=M_LEATHER;
            for(float bx:new float[]{.09f,.19f,.29f}){e=Math.max(Math.abs(x-bx)-.008f,r-.0815f)-.002f;e=Math.max(e,-(r-.066f));if(e<d){d=e;m=M_GUN;}
                float a=(float)Math.atan2(z,y/SY);float ak=Math.round(a/(2*PI/16))*(2*PI/16);e=sphere(x,y,z,bx,(float)Math.cos(ak)*.083f*SY,(float)Math.sin(ak)*.083f,.003f);if(e<d){d=e;m=M_BRASS;}}
            for(int k=0;k<3;k++){float sx=.05f+k*.07f;if(sx>.09f-.02f&&sx<.09f+.02f)sx+=.0f;
                e=cone(x,y,z,v(sx+.01f,-.035f,.07f),v(sx-.03f,-.07f,.12f),.016f,.002f);if(e<d){d=e;m=M_GUN;}}   // spikes along the outer top edge
        }else d=Math.min(d,bound(x,y,z,-1,.38f,-.09f,.09f,-.09f,.09f)+.001f);
        // barrel: collar, tube with cooling rings and glowing side vents, slotted muzzle crown, glowing bore
        if(x>BX0-.01f){
            e=cylX(x,y,z,0,0,BR,BX0,MZ0+.002f);
            boolean vent=x>.385f&&x<.59f&&Math.abs(z)<.009f;boolean between=true;for(float rx:RINGS)if(Math.abs(x-rx)<.012f)between=false;
            if(vent&&between)e+=.004f;
            if(e<d){d=e;m=vent&&between&&e>-.002f?M_GLOW:M_GUN;}
            for(float rx:RINGS){e=cylX(x,y,z,0,0,BR+.007f,rx-.004f,rx+.004f)-.001f;if(e<d){d=e;m=M_STEEL;}}
            e=cylX(x,y,z,0,0,BR+.01f,BX0,BX0+.025f);e=groove(e,x-(BX0+.0125f),.002f,.0015f);if(e<d){d=e;m=M_GUN;}
            e=cylX(x,y,z,0,0,MR,MZ0,MZ1);
            float a=(float)Math.atan2(z,y);float ak=Math.round(a/(2*PI/6))*(2*PI/6);float ang=Math.abs(a-ak);
            if(x>MZ0+.025f&&ang*r<.008f)e=Math.max(e,-(r-.04f));                 // six vent slots through the crown
            if(e<d){d=e;m=M_GUN;}
            e=-(r-BORE);e=Math.max(e,-(MZ1-x));                                     // the bore (carved out of everything)
            if(-e>d){}else{d=Math.max(d,e);m=x<.52f?M_GLOW:M_HOLE;}
            if(x<.52f&&r<BORE+.001f&&d>-.002f)m=M_GLOW;
        }
        // plasma core on top: mount, glass cylinder, cage bands and rails, end caps, the two conduits into the barrel
        e=box(x,y,z,.195f,0,.084f,.1f,.04f,.009f,.004f);if(e<d){d=e;m=M_GUN;}
        e=cylX(x,y,z,0,.118f,CR,CORE0[0],CORE1[0]);if(e<d){d=e;m=M_GLOW;}
        for(float cx:new float[]{.105f,.28f}){e=cylX(x,y,z,0,.118f,CR+.007f,cx-.012f,cx+.012f)-.002f;if(e<d){d=e;m=M_GUN;}}
        for(float cx:new float[]{.15f,.195f,.24f}){e=Math.max(cylX(x,y,z,0,.118f,CR+.005f,cx-.004f,cx+.004f),-(float)(Math.hypot(y,z-.118f)-CR-.001f));if(e<d){d=e;m=M_STEEL;}}
        for(float ry:new float[]{-.027f,.027f}){e=box(x,y,z,.195f,ry,.118f+.026f,.09f,.004f,.004f,.002f);if(e<d){d=e;m=M_STEEL;}}
        e=tubeB(x,y,z,COND0,COND_B,.0075f);e=Math.min(e,tubeB(x,y,z,COND1,COND_B1,.0075f));
        if(e<d){d=e;m=((int)((x+z)*160)&1)==0?M_RUBBER:M_STEEL;}
        mat[0]=m;return d;
    }
    static float groove(float d,float dist,float w,float depth){return d+depth*(1-smooth(0,w,Math.abs(dist)));}

    // ================================================================== materials of the high poly
    static float[] material(int m,float x,float y,float z,float lap,float cav){
        float r,g,b,rough,metal,emit=0;float grime=smooth(.55f,.85f,fbm(x*9+3,y*9,z*9,4))*.5f;float n=fbm(x*45,y*45,z*45,4);
        switch(m){
            case M_PAINT:{float mott=fbm(x*14+5,y*14,z*14,4);r=.24f*(.85f+mott*.3f);g=.024f*(.85f+mott*.3f);b=.018f*(.85f+mott*.3f);
                float wear=smooth(.62f,.85f,fbm(x*70,y*70,z*70,4)*.6f+lap*.0011f+fbm(x*9+7,y*9,z*9,3)*.35f);
                r=mix(r,.6f,wear);g=mix(g,.6f,wear);b=mix(b,.6f,wear);float k=1-grime*.6f-cav*.5f;r*=k;g*=k;b*=k;
                rough=mix(.42f+n*.12f,.26f,wear)+grime*.25f;metal=wear;break;}
            case M_STEEL:{float st=noise(x*7,y*1100,z*1100);r=g=b=.6f*(.88f+st*.18f);rough=.2f+st*.14f+cav*.3f;metal=1;float k=1-cav*.6f-grime*.3f;r*=k;g*=k;b*=k;break;}
            case M_GUN:{float wear=smooth(.6f,.9f,lap*.0011f+n*.3f);float k=(.85f+.25f*n)*(1-grime*.5f)*(1-cav*.5f);
                r=mix(.07f*k,.45f,wear);g=mix(.068f*k,.44f,wear);b=mix(.075f*k,.46f,wear);rough=.38f+.12f*n;metal=.9f;break;}
            case M_LEATHER:{float k=.7f+.6f*fbm(x*350,y*350,z*350,3);r=.09f*k;g=.05f*k;b=.035f*k;rough=.7f+.1f*n;metal=0;break;}
            case M_RUBBER:{r=g=b=.025f*(.8f+.4f*n);rough=.85f;metal=0;break;}
            case M_BRASS:{float k=.9f+.15f*n;r=.7f*k;g=.5f*k;b=.22f*k;rough=.3f+.1f*n;metal=1;break;}
            case M_GLOW:{float f=.75f+.25f*fbm(x*120,y*120,z*120,3);r=.2f*f;g=.55f*f;b=1f*f;rough=.08f;metal=0;emit=1;break;}   // blue plasma light
            case M_HOLE:{r=g=b=.01f;rough=.9f;metal=0;break;}
            default:{r=g=b=.01f;rough=.8f;metal=0;}
        }
        return new float[]{r,g,b,rough,metal,emit};
    }

    // ================================================================== low poly + UV charts
    static final class Chart {final ArrayList<float[]> verts=new ArrayList<>();final ArrayList<int[]> tris=new ArrayList<>();int group;float minU=1e9f,minV=1e9f,maxU=-1e9f,maxV=-1e9f;int px,py,pw,ph;}
    // vertex: x,y,z, nx,ny,nz, tx,ty,tz, u,v (chart space metres), then atlas uv filled after packing
    static final ArrayList<Chart> charts=new ArrayList<>();
    static Chart chart(int group){Chart c=new Chart();c.group=group;charts.add(c);return c;}
    static int vert(Chart c,float[] p,float[] n,float[] t,float u,float vv){c.verts.add(new float[]{p[0],p[1],p[2],n[0],n[1],n[2],t[0],t[1],t[2],u,vv});c.minU=Math.min(c.minU,u);c.maxU=Math.max(c.maxU,u);c.minV=Math.min(c.minV,vv);c.maxV=Math.max(c.maxV,vv);return c.verts.size()-1;}
    /**
     * Loft through rings of k points (each ring closed); smooth normals around the ring. The side becomes one chart,
     * unrolled: u = distance around the ring, v = distance along the loft. Optional flat caps (each its own chart).
     */
    static void loft(int group,float[][][] rings,boolean cap0,boolean cap1){
        int n=rings.length,k=rings[0].length;Chart c=chart(group);
        float[] vAcc=new float[n];for(int i=1;i<n;i++)vAcc[i]=vAcc[i-1]+len(sub(centroid(rings[i]),centroid(rings[i-1])));
        int[][] id=new int[n][k+1];
        for(int i=0;i<n;i++){float[][] R=rings[i];float per=0;float[] acc=new float[k+1];for(int j=0;j<k;j++){acc[j+1]=acc[j]+len(sub(R[(j+1)%k],R[j]));}per=acc[k];
            float[] axis=norm(sub(centroid(rings[Math.min(n-1,i+1)]),centroid(rings[Math.max(0,i-1)])));
            for(int j=0;j<=k;j++){float[] p=R[j%k];float[] prev=R[(j-1+k)%k],next=R[(j+1)%k];
                float[] tan=norm(sub(next,prev));float[] nrm=norm(cross(tan,axis));
                if(dot(nrm,sub(p,centroid(R)))<0)nrm=mul(nrm,-1);
                id[i][j]=vert(c,p,nrm,tan,acc[j]-per*.5f,vAcc[i]);}}
        for(int i=0;i+1<n;i++)for(int j=0;j<k;j++){int a=id[i][j],b=id[i][j+1],cc=id[i+1][j+1],d=id[i+1][j];c.tris.add(new int[]{a,b,cc});c.tris.add(new int[]{a,cc,d});}
        orient(c);
        float[] dir=norm(sub(centroid(rings[n-1]),centroid(rings[0])));
        if(cap0)cap(group,rings[0],mul(dir,-1));if(cap1)cap(group,rings[n-1],dir);
    }
    static float[] centroid(float[][] R){float[] c=v(0,0,0);for(float[] p:R)c=add(c,p,1f/R.length);return c;}
    /** Flat polygon cap (fan), its own chart, facing `out` (away from the loft). */
    static void cap(int group,float[][] R,float[] out){
        Chart c=chart(group);float[] cen=centroid(R);float[] n=norm(out);
        float[] t=sub(R[0],cen);t=norm(sub(t,mul(n,dot(t,n))));float[] bt=cross(n,t);int k=R.length;
        int ci=vert(c,cen,n,t,0,0);int[] ids=new int[k];for(int j=0;j<k;j++){float[] d=sub(R[j],cen);ids[j]=vert(c,R[j],n,t,dot(d,t),dot(d,bt));}
        for(int j=0;j<k;j++)c.tris.add(new int[]{ci,ids[j],ids[(j+1)%k]});
    }
    /** A box as 6 charts (x0..x1 etc). */
    static void box(int group,float x0,float y0,float z0,float x1,float y1,float z1){
        float[][] r0={v(x0,y0,z0),v(x0,y1,z0),v(x0,y1,z1),v(x0,y0,z1)},r1={v(x1,y0,z0),v(x1,y1,z0),v(x1,y1,z1),v(x1,y0,z1)};
        for(int f=0;f<4;f++){int g=(f+1)%4;quadChart(group,r0[f],r0[g],r1[g],r1[f]);}
        quadChart(group,r0[3],r0[2],r0[1],r0[0]);quadChart(group,r1[0],r1[1],r1[2],r1[3]);
    }
    /** One flat quad (a,b,c,d counter-clockwise seen from outside) as a chart. */
    static void quadChart(int group,float[] a,float[] b,float[] c2,float[] d){
        Chart c=chart(group);float[] n=norm(cross(sub(b,a),sub(d,a)));float[] t=norm(sub(b,a));float[] bt=cross(n,t);
        int[] ids=new int[4];float[][] P={a,b,c2,d};for(int i=0;i<4;i++){float[] q=sub(P[i],a);ids[i]=vert(c,P[i],n,t,dot(q,t),dot(q,bt));}
        c.tris.add(new int[]{ids[0],ids[1],ids[2]});c.tris.add(new int[]{ids[0],ids[2],ids[3]});
    }
    /** Make each chart's v axis follow cross(N,T) (the shader's bitangent) so parallax offsets go the right way. */
    static void orient(Chart c){
        float[] t0=c.verts.get(c.tris.get(0)[0]),t1=c.verts.get(c.tris.get(0)[1]),t2=c.verts.get(c.tris.get(0)[2]);
        float[] e1=sub(v(t1[0],t1[1],t1[2]),v(t0[0],t0[1],t0[2])),e2=sub(v(t2[0],t2[1],t2[2]),v(t0[0],t0[1],t0[2]));
        float du1=t1[9]-t0[9],dv1=t1[10]-t0[10],du2=t2[9]-t0[9],dv2=t2[10]-t0[10];float r=du1*dv2-du2*dv1;if(Math.abs(r)<1e-12f)return;
        float[] dPdv=mul(sub(mul(e2,du1),mul(e1,du2)),1/r);float[] n=v(t0[3],t0[4],t0[5]),t=v(t0[6],t0[7],t0[8]);
        if(dot(cross(n,t),dPdv)<0){for(float[] q:c.verts)q[10]=-q[10];float tmp=c.minV;c.minV=-c.maxV;c.maxV=-tmp;}
    }


    // ================================================================== the low poly: box modelled
    // Major forms get geometry (sleeve, tank, crankcase, fin block, head, plug, lead, muffler, pipes, starter, filter,
    // housing, guard, spikes, guide bar); everything smaller (rivets, straps, buckles, fins, vents, holes, nuts, the
    // caddy cups, knurling, seams) lives in the baked maps.
    static final float PI=(float)Math.PI;
    /** Ring of points c + e1*a + e2*b for the 2D outline pts (counter-clockwise in a,b). */
    static float[][] ring(float[] c,float[] e1,float[] e2,float[][] pts){float[][] R=new float[pts.length][];for(int i=0;i<pts.length;i++)R[i]=add(add(c,e1,pts[i][0]),e2,pts[i][1]);return R;}
    static float[][] ellipse(float ra,float rb,int k,float phase){float[][] p=new float[k][];for(int j=0;j<k;j++){double a=phase+j*2*Math.PI/k;p[j]=new float[]{(float)Math.cos(a)*ra,(float)Math.sin(a)*rb};}return p;}
    static float[][] oct(float ha,float hb,float ch){return new float[][]{{ha,-hb+ch},{ha,hb-ch},{ha-ch,hb},{-ha+ch,hb},{-ha,hb-ch},{-ha,-hb+ch},{-ha+ch,-hb},{ha-ch,-hb}};}
    static final float[] X=v(1,0,0),Y=v(0,1,0),Z=v(0,0,1);
    /** A prism along axis (unit) from c0 to c1 with the outline pts in the frame (e1,e2), e1 x e2 = axis. */
    static void prism(float[] c0,float[] c1,float[] e1,float[] e2,float[][] pts,boolean cap0,boolean cap1){loft(0,new float[][][]{ring(c0,e1,e2,pts),ring(c1,e1,e2,pts)},cap0,cap1);}
    static void prismX(float x0,float x1,float cy,float cz,float[][] pts){prism(v(x0,cy,cz),v(x1,cy,cz),Y,Z,pts,true,true);}
    static void tubeLow(float[][] path,float r,int sides,int segs,boolean caps){
        float[][][] R=new float[segs+1][][];
        for(int i=0;i<=segs;i++){float t=i/(float)segs;int k=Math.min(path.length-1,Math.round(t*(path.length-1)));float[] p=path[k];
            float[] a=path[Math.max(0,k-1)],b=path[Math.min(path.length-1,k+1)];float[] ax=norm(sub(b,a));float[] e1=norm(cross(ax,Math.abs(ax[2])>.9f?X:Z));float[] e2=cross(ax,e1);
            R[i]=ring(p,e1,e2,ellipse(r,r,sides,PI/sides));}
        loft(0,R,caps,caps);
    }
    static void annulus(float x,float[][] outer,float[][] inner){
        Chart c=chart(0);int k=outer.length;int[] io=new int[k],ii=new int[k];
        for(int j=0;j<k;j++){io[j]=vert(c,outer[j],v(-1,0,0),v(0,1,0),outer[j][1],outer[j][2]);ii[j]=vert(c,inner[j],v(-1,0,0),v(0,1,0),inner[j][1],inner[j][2]);}
        for(int j=0;j<k;j++){int j2=(j+1)%k;c.tris.add(new int[]{io[j],io[j2],ii[j2]});c.tris.add(new int[]{io[j],ii[j2],ii[j]});}orient(c);
    }
    static void flipLast(){Chart c=charts.get(charts.size()-1);for(float[] q:c.verts){q[3]=-q[3];q[4]=-q[4];q[5]=-q[5];}orient(c);}
    static float[][] sleeveRing(float x,float r,int k){return ring(v(x,0,0),Y,Z,ellipse(r*SY,r,k,PI));}
    static float[][] barrelRing(float x,float r,int k){return ring(v(x,0,0),Y,Z,ellipse(r,r,k,PI/k));}
    static void buildLow(){
        int K=10;
        // sleeve: outer envelope (over the bands), the padded opening, and the bore the forearm goes into
        float[][] xs={{0,.078f},{.05f,.0835f},{.3f,.0835f},{.335f,.074f},{.358f,.06f}};
        float[][][] rs=new float[xs.length][][];for(int i=0;i<xs.length;i++)rs[i]=sleeveRing(xs[i][0],xs[i][1],K);
        loft(0,rs,false,false);
        annulus(0,sleeveRing(0,.078f,K),sleeveRing(0,.055f,K));
        loft(0,new float[][][]{sleeveRing(0,.055f,K),sleeveRing(.3f,.05f,K)},false,true);
        for(int k=1;k<=2;k++){Chart bc=charts.get(charts.size()-k);for(float[] q:bc.verts){q[3]=-q[3];q[4]=-q[4];q[5]=-q[5];}orient(bc);}   // the bore and its end wall face inward
        // barrel from the sleeve's nose (closing the sleeve), collar, cooling rings in the bake, muzzle crown, the bore
        loft(0,new float[][][]{sleeveRing(.358f,.06f,K),barrelRing(.358f,BR+.011f,K),barrelRing(BX0+.025f,BR+.011f,K),barrelRing(BX0+.03f,BR+.008f,K),barrelRing(MZ0,BR+.008f,K),barrelRing(MZ0,MR+.001f,K),barrelRing(MZ1,MR+.001f,K)},false,false);
        annulus(MZ1,barrelRing(MZ1,MR+.001f,K),barrelRing(MZ1,BORE,K));flipLast();
        loft(0,new float[][][]{barrelRing(MZ1,BORE,K),barrelRing(.5f,BORE,K)},false,true);
        for(int k=1;k<=2;k++){Chart bc=charts.get(charts.size()-k);for(float[] q:bc.verts){q[3]=-q[3];q[4]=-q[4];q[5]=-q[5];}orient(bc);}
        // core: mount block, the caged cylinder (end caps included), rails in the bake; conduits
        box(0,.095f,-.04f,.075f,.295f,.04f,.093f);
        prism(v(.093f,0,.118f),v(.292f,0,.118f),Y,Z,ellipse(CR+.009f,CR+.009f,10,PI/10),true,true);
        tubeLow(COND0,.009f,4,4,false);tubeLow(COND1,.009f,4,4,false);
        // spikes along the outer top edge: three-sided pyramids
        for(int k=0;k<3;k++){float sx=.05f+k*.07f;float[] base=v(sx+.01f,-.035f,.07f),tip=v(sx-.03f,-.07f,.12f);float[] ax=norm(sub(tip,base));float[] e1=norm(cross(ax,X)),e2=cross(ax,e1);
            float[][] tri=new float[3][];for(int j=0;j<3;j++){double a=j*2*Math.PI/3;tri[j]=add(add(add(base,ax,-.008f),e1,(float)Math.cos(a)*.019f),e2,(float)Math.sin(a)*.019f);}
            loft(0,new float[][][]{tri,new float[][]{tip,tip,tip}},false,false);}
    }

    // ================================================================== packing
    static int AW=512,AH=512;static float texelsPerMetre;
    static void pack(){
        // lay every chart with its long side horizontal (better shelf packing); the tangent turns with the uvs so
        // the normal map and parallax stay aligned: (u,v) -> (v,-u) and T -> cross(N,T)
        for(Chart c:charts)if(c.maxV-c.minV>c.maxU-c.minU){
            for(float[] q:c.verts){float u=q[9],vv=q[10];q[9]=vv;q[10]=-u;float[] N=v(q[3],q[4],q[5]),T=v(q[6],q[7],q[8]);float[] B=cross(N,T);q[6]=B[0];q[7]=B[1];q[8]=B[2];}
            float a=c.minU,b=c.maxU;c.minU=c.minV;c.maxU=c.maxV;c.minV=-b;c.maxV=-a;}
        float density=5000;   // texels per metre, shrunk until everything fits
        for(int tries=0;tries<160;tries++){if(tryPack(density))break;density*=.95f;}
        texelsPerMetre=densityUsed;
        for(Chart c:charts)for(float[] q:c.verts){float u=(c.px+4+(q[9]-c.minU)*densityUsed)/AW,vv=(c.py+4+(q[10]-c.minV)*densityUsed)/AH;q[9]=u;q[10]=vv;}
    }
    static float densityUsed;
    static boolean tryPack(float density){
        ArrayList<Chart> sorted=new ArrayList<>(charts);
        for(Chart c:sorted){c.pw=(int)Math.ceil((c.maxU-c.minU)*density)+8;c.ph=(int)Math.ceil((c.maxV-c.minV)*density)+8;
            if(c.pw>c.ph&&c.ph<AH&&false){}}
        sorted.sort((a,b)->b.ph-a.ph);
        int x=0,y=0,rowH=0;
        for(Chart c:sorted){if(c.pw>AW)return false;if(x+c.pw>AW){x=0;y+=rowH;rowH=0;}c.px=x;c.py=y;x+=c.pw;rowH=Math.max(rowH,c.ph);if(y+rowH>AH)return false;}
        densityUsed=density;return true;
    }

    // ================================================================== baking
    static float CAGE=.012f,RANGE=.006f;static float[] AO_HS={.0008f,.0016f,.003f,.006f,.012f,.025f,.05f};static String NAME="chainsaw";   // ray search distance out/in, height map range (± metres)
    static float[] A_R,A_G,A_B,AO,NX,NY,NZ,HT,RO,ME,EM;static boolean[] COV;
    static void bake()throws Exception{
        int N=AW*AH;A_R=new float[N];A_G=new float[N];A_B=new float[N];AO=new float[N];NX=new float[N];NY=new float[N];NZ=new float[N];HT=new float[N];RO=new float[N];ME=new float[N];EM=new float[N];COV=new boolean[N];
        ExecutorService ex=Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());ArrayList<Future<?>> fs=new ArrayList<>();
        for(Chart c:charts)fs.add(ex.submit(()->{bakeChart(c);return null;}));
        int done=0;for(Future<?> f:fs){f.get();done++;if(done%20==0)System.out.println("  charts "+done+"/"+fs.size());}
        ex.shutdown();
    }
    static void bakeChart(Chart c){
        int[] mat=new int[1];
        for(int[] t:c.tris){float[] a=c.verts.get(t[0]),b=c.verts.get(t[1]),d=c.verts.get(t[2]);
            float ax=a[9]*AW,ay=a[10]*AH,bx=b[9]*AW,by=b[10]*AH,dx=d[9]*AW,dy=d[10]*AH;
            float area=(bx-ax)*(dy-ay)-(dx-ax)*(by-ay);if(Math.abs(area)<1e-9f)continue;
            int x0=(int)Math.floor(Math.min(ax,Math.min(bx,dx)))-1,x1=(int)Math.ceil(Math.max(ax,Math.max(bx,dx)))+1,y0=(int)Math.floor(Math.min(ay,Math.min(by,dy)))-1,y1=(int)Math.ceil(Math.max(ay,Math.max(by,dy)))+1;
            for(int py=Math.max(0,y0);py<=Math.min(AH-1,y1);py++)for(int px=Math.max(0,x0);px<=Math.min(AW-1,x1);px++){
                float qx=px+.5f,qy=py+.5f;
                float w0=((bx-qx)*(dy-qy)-(dx-qx)*(by-qy))/area,w1=((dx-qx)*(ay-qy)-(ax-qx)*(dy-qy))/area,w2=1-w0-w1;
                float tol=-.02f;   // a little conservative rasterisation so chart borders get covered
                if(w0<tol||w1<tol||w2<tol)continue;int i=py*AW+px;if(COV[i])continue;
                float[] P=v(a[0]*w0+b[0]*w1+d[0]*w2,a[1]*w0+b[1]*w1+d[1]*w2,a[2]*w0+b[2]*w1+d[2]*w2);
                float[] N=norm(v(a[3]*w0+b[3]*w1+d[3]*w2,a[4]*w0+b[4]*w1+d[4]*w2,a[5]*w0+b[5]*w1+d[5]*w2));
                float[] T=v(a[6]*w0+b[6]*w1+d[6]*w2,a[7]*w0+b[7]*w1+d[7]*w2,a[8]*w0+b[8]*w1+d[8]*w2);T=norm(sub(T,mul(N,dot(T,N))));float[] B=cross(N,T);
                texel(c.group,i,P,N,T,B,mat);}}
    }
    /** Casts from the cage toward the low poly surface and on through it to find the high poly; fills one texel. */
    static void texel(int g,int i,float[] P,float[] N,float[] T,float[] B,int[] mat){
        float t=hit(g,P,N,mat);
        boolean miss=Float.isNaN(t);if(miss)t=0;
        float[] H=add(P,N,t);
        float e=.00012f;float d0;
        float nx=sdf(g,H[0]+e,H[1],H[2],mat)-sdf(g,H[0]-e,H[1],H[2],mat),ny=sdf(g,H[0],H[1]+e,H[2],mat)-sdf(g,H[0],H[1]-e,H[2],mat),nz=sdf(g,H[0],H[1],H[2]+e,mat)-sdf(g,H[0],H[1],H[2]-e,mat);
        float[] Nh=miss?N:norm(v(nx,ny,nz));if(Float.isNaN(Nh[0]))Nh=N;
        d0=sdf(g,H[0],H[1],H[2],mat);int m=mat[0];
        // curvature (for edge wear) and SDF ambient occlusion (several scales, plus the high normal)
        float le=.0006f;float lap=(sdf(g,H[0]+le,H[1],H[2],mat)+sdf(g,H[0]-le,H[1],H[2],mat)+sdf(g,H[0],H[1]+le,H[2],mat)+sdf(g,H[0],H[1]-le,H[2],mat)+sdf(g,H[0],H[1],H[2]+le,mat)+sdf(g,H[0],H[1],H[2]-le,mat)-6*d0)/(le*le);
        float occ=0,wsum=0;float[] hs=AO_HS;
        for(int k=0;k<hs.length;k++){float h=hs[k];float s=sdfAll(H[0]+Nh[0]*h,H[1]+Nh[1]*h,H[2]+Nh[2]*h,mat);float w=1f/(k+1);occ+=w*clamp((h-s)/h,0,1);wsum+=w;}
        float ao=clamp(1-occ/wsum*1.2f,0,1);float cav=clamp(-lap*.002f,0,1);
        float[] mt=material(m,H[0],H[1],H[2],Math.max(0,lap),1-ao);
        A_R[i]=mt[0];A_G[i]=mt[1];A_B[i]=mt[2];RO[i]=mt[3];ME[i]=mt[4];EM[i]=mt[5];AO[i]=ao;
        NX[i]=dot(Nh,T);NY[i]=dot(Nh,B);NZ[i]=Math.max(.05f,dot(Nh,N));
        HT[i]=clamp(.5f+t/(2*RANGE),0,1);COV[i]=true;
    }
    /** Signed distance along N from the low poly point to the high poly surface (0 if none found within the cage). */
    static float hit(int g,float[] P,float[] N,int[] mat){
        float t=CAGE;float d=sdf(g,P[0]+N[0]*t,P[1]+N[1]*t,P[2]+N[2]*t,mat);
        if(d<0){   // the cage start is inside the high poly (it bulges out further): march outward to its surface
            float lo=t;for(int k=0;k<60&&d<0;k++){lo=t;t+=Math.max(-d*.8f,.00005f);if(t>CAGE*2.5f)return CAGE;d=sdf(g,P[0]+N[0]*t,P[1]+N[1]*t,P[2]+N[2]*t,mat);}
            return refine(g,P,N,lo,t,mat);
        }
        for(int k=0;k<120;k++){float nt=t-Math.max(d*.7f,.00004f);if(nt<-CAGE*1.6f)return Float.NaN;float nd=sdf(g,P[0]+N[0]*nt,P[1]+N[1]*nt,P[2]+N[2]*nt,mat);
            if(nd<0)return refine(g,P,N,nt,t,mat);t=nt;d=nd;}
        return Float.NaN;
    }
    static float refine(int g,float[] P,float[] N,float in,float out,int[] mat){
        for(int k=0;k<14;k++){float m=(in+out)*.5f;float d=sdf(g,P[0]+N[0]*m,P[1]+N[1]*m,P[2]+N[2]*m,mat);if(d<0)in=m;else out=m;}
        return (in+out)*.5f;
    }
    /** Grows every chart's border outward so mipmaps and filtering never pull in empty texels. */
    static void dilate(int passes){
        float[][] ch={A_R,A_G,A_B,AO,NX,NY,NZ,HT,RO,ME,EM};
        for(int p=0;p<passes;p++){boolean[] nc=COV.clone();
            for(int y=0;y<AH;y++)for(int x=0;x<AW;x++){int i=y*AW+x;if(COV[i])continue;int n=0;float[] s=new float[ch.length];
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int xx=x+dx,yy=y+dy;if(xx<0||yy<0||xx>=AW||yy>=AH)continue;int j=yy*AW+xx;if(!COV[j])continue;n++;for(int k=0;k<ch.length;k++)s[k]+=ch[k][j];}
                if(n>0){for(int k=0;k<ch.length;k++)ch[k][i]=s[k]/n;nc[i]=true;}}
            COV=nc;}
        for(int i=0;i<AW*AH;i++)if(!COV[i]){A_R[i]=A_G[i]=A_B[i]=.05f;AO[i]=1;NX[i]=NY[i]=0;NZ[i]=1;HT[i]=.5f;RO[i]=.5f;ME[i]=0;EM[i]=0;}
    }

    // ================================================================== output
    static int b8(float v){return Math.max(0,Math.min(255,Math.round(v*255)));}
    static float srgb(float l){l=clamp(l,0,1);return l<=.0031308f?l*12.92f:(float)(1.055*Math.pow(l,1/2.4)-.055);}
    static void writeMaps(File dir)throws Exception{
        BufferedImage a=new BufferedImage(AW,AH,BufferedImage.TYPE_INT_ARGB),b=new BufferedImage(AW,AH,BufferedImage.TYPE_INT_ARGB),c=new BufferedImage(AW,AH,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<AH;y++)for(int x=0;x<AW;x++){int i=y*AW+x;
            float l=(float)Math.sqrt(NX[i]*NX[i]+NY[i]*NY[i]+NZ[i]*NZ[i]);
            a.setRGB(x,y,(b8(AO[i])<<24)|(b8(srgb(A_R[i]))<<16)|(b8(srgb(A_G[i]))<<8)|b8(srgb(A_B[i])));
            b.setRGB(x,y,(b8(HT[i])<<24)|(b8(NX[i]/l*.5f+.5f)<<16)|(b8(NY[i]/l*.5f+.5f)<<8)|b8(NZ[i]/l*.5f+.5f));
            c.setRGB(x,y,(b8(RO[i])<<16)|(b8(ME[i])<<8)|b8(EM[i]));}
        ImageIO.write(a,"png",new File(dir,NAME+"_a.png"));ImageIO.write(b,"png",new File(dir,NAME+"_b.png"));ImageIO.write(c,"png",new File(dir,NAME+"_c.png"));
    }
    // ================================================================== high poly mesh (surface nets, narrow band)
    static long meshHigh(int g,float h,File obj)throws Exception{
        float x0=-.02f,x1=.7f,y0=-.1f,y1=.1f,z0=-.1f,z1=.18f;
        int nx=(int)((x1-x0)/h)+2,ny=(int)((y1-y0)/h)+2,nz=(int)((z1-z0)/h)+2;int B=8;int[] mat=new int[1];
        HashMap<Long,float[]> blocks=new HashMap<>();
        // coarse pass: only blocks near the surface get fine samples
        for(int bz=0;bz*B<nz;bz++)for(int by=0;by*B<ny;by++)for(int bx=0;bx*B<nx;bx++){
            float cx=x0+(bx*B+B/2f)*h,cy=y0+(by*B+B/2f)*h,cz=z0+(bz*B+B/2f)*h;float d=sdfAll(cx,cy,cz,mat);
            if(Math.abs(d)>B*h*.9f+.0008f)continue;
            float[] s=new float[(B+1)*(B+1)*(B+1)];
            for(int k=0;k<=B;k++)for(int j=0;j<=B;j++)for(int i=0;i<=B;i++)s[(k*(B+1)+j)*(B+1)+i]=sdfAll(x0+(bx*B+i)*h,y0+(by*B+j)*h,z0+(bz*B+k)*h,mat);
            blocks.put(key(bx,by,bz),s);}
        // vertices: one per cell with a sign change, at the average of its edge crossings
        HashMap<Long,Integer> vid=new HashMap<>();ArrayList<float[]> verts=new ArrayList<>();long faces=0;
        PrintWriter w=obj==null?null:new PrintWriter(new BufferedWriter(new FileWriter(obj),1<<20));
        int[][] E={{0,1},{2,3},{4,5},{6,7},{0,2},{1,3},{4,6},{5,7},{0,4},{1,5},{2,6},{3,7}};
        for(Map.Entry<Long,float[]> en:blocks.entrySet()){long kk=en.getKey();int bx=(int)(kk&0xffff),by=(int)((kk>>16)&0xffff),bz=(int)((kk>>32)&0xffff);float[] s=en.getValue();
            for(int k=0;k<B;k++)for(int j=0;j<B;j++)for(int i=0;i<B;i++){float[] c=new float[8];int neg=0;
                for(int q=0;q<8;q++){c[q]=s[((k+((q>>2)&1))*(B+1)+j+((q>>1)&1))*(B+1)+i+(q&1)];if(c[q]<0)neg++;}
                if(neg==0||neg==8)continue;float px=0,py=0,pz=0;int cnt=0;
                for(int[] e:E){float a=c[e[0]],b=c[e[1]];if((a<0)==(b<0))continue;float t=a/(a-b);
                    px+=((e[0]&1)+(((e[1]&1)-(e[0]&1))*t));py+=(((e[0]>>1)&1)+((((e[1]>>1)&1)-((e[0]>>1)&1))*t));pz+=(((e[0]>>2)&1)+((((e[1]>>2)&1)-((e[0]>>2)&1))*t));cnt++;}
                int gx=bx*B+i,gy=by*B+j,gz=bz*B+k;
                vid.put(key(gx,gy,gz),verts.size());verts.add(new float[]{x0+(gx+px/cnt)*h,y0+(gy+py/cnt)*h,z0+(gz+pz/cnt)*h});}}
        if(w!=null)for(float[] p:verts)w.printf(Locale.ROOT,"v %.5f %.5f %.5f%n",p[0],p[1],p[2]);
        // faces: a quad around every grid edge whose ends differ in sign
        for(Map.Entry<Long,Integer> en:vid.entrySet()){long kk=en.getKey();int gx=(int)(kk&0xffff),gy=(int)((kk>>16)&0xffff),gz=(int)((kk>>32)&0xffff);
            for(int ax=0;ax<3;ax++){
                float a=sample(blocks,gx,gy,gz,B),b=sample(blocks,gx+(ax==0?1:0),gy+(ax==1?1:0),gz+(ax==2?1:0),B);
                if(Float.isNaN(a)||Float.isNaN(b)||(a<0)==(b<0))continue;
                // the four cells around this edge
                int[][] o=ax==0?new int[][]{{0,0,0},{0,-1,0},{0,-1,-1},{0,0,-1}}:ax==1?new int[][]{{0,0,0},{0,0,-1},{-1,0,-1},{-1,0,0}}:new int[][]{{0,0,0},{-1,0,0},{-1,-1,0},{0,-1,0}};
                int[] q=new int[4];boolean ok=true;for(int m=0;m<4;m++){Integer id=vid.get(key(gx+o[m][0],gy+o[m][1],gz+o[m][2]));if(id==null){ok=false;break;}q[m]=id+1;}
                if(!ok)continue;faces+=2;
                if(w!=null){if(a<0)w.println("f "+q[0]+" "+q[1]+" "+q[2]+" "+q[3]);else w.println("f "+q[3]+" "+q[2]+" "+q[1]+" "+q[0]);}}}
        if(w!=null)w.close();
        System.out.println("  high poly: "+verts.size()+" vertices, "+faces+" triangles");
        return faces;
    }
    static long key(int x,int y,int z){return (x&0xffffL)|((y&0xffffL)<<16)|((z&0xffffL)<<32);}
    static float sample(HashMap<Long,float[]> blocks,int gx,int gy,int gz,int B){
        int bx=gx/B,by=gy/B,bz=gz/B;int i=gx-bx*B,j=gy-by*B,k=gz-bz*B;float[] s=blocks.get(key(bx,by,bz));
        if(s==null){if(i==0&&bx>0){s=blocks.get(key(bx-1,by,bz));i=B;bx--;}}
        if(s==null)return Float.NaN;return s[(k*(B+1)+j)*(B+1)+i];
    }


    // ================================================================== mesh output
    /** cannon.bin: magic, pom scale, group count (1), then a vertex count and 11 floats per vertex. */
    static int writeMesh(File f,float pom)throws Exception{
        ArrayList<float[]> g0=new ArrayList<>();for(Chart c:charts)for(int[] t:c.tris)for(int k:t)g0.add(c.verts.get(k));
        ByteBuffer bb=ByteBuffer.allocate(16+g0.size()*44).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(0x4e554753);bb.putFloat(pom);bb.putInt(1);
        bb.putInt(g0.size());for(float[] q:g0)for(int j=0;j<11;j++)bb.putFloat(q[j]);
        try(FileOutputStream out=new FileOutputStream(f)){out.write(bb.array(),0,bb.position());}
        return g0.size()/3;
    }
    // ================================================================== previews
    /** Orthographic sphere-traced render of the high poly (left) and the baked low poly with its maps (right). */
    static void preview(File dir,float yaw,float pitch,String name,float[] centre,float span,int W,int H,boolean lowWire)throws Exception{
        BufferedImage im=new BufferedImage(W*2,H,BufferedImage.TYPE_INT_RGB);
        float cy=(float)Math.cos(yaw),sy=(float)Math.sin(yaw),cp=(float)Math.cos(pitch),sp=(float)Math.sin(pitch);
        float[] fwd=v(-cy*cp,-sy*cp,-sp),right=norm(cross(fwd,v(0,0,1))),up=cross(right,fwd);float dist=1.2f,scale=span/W;
        float[] L=norm(v(.5f,.35f,.8f)),L2=norm(v(-.6f,-.4f,.25f));
        ExecutorService ex=Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());ArrayList<Future<?>> fs=new ArrayList<>();
        for(int py0=0;py0<H;py0++){final int py=py0;fs.add(ex.submit(()->{int[] mat=new int[1];
            for(int px=0;px<W;px++){float sx=(px-W/2f)*scale,syy=(H/2f-py)*scale;
                float[] o=add(add(add(centre,fwd,-dist),right,sx),up,syy);float t=0;boolean hitp=false;float[] p=null;
                for(int k=0;k<500;k++){p=add(o,fwd,t);float d=sdfAll(p[0],p[1],p[2],mat);if(d<.00008f){hitp=true;break;}t+=d*.85f;if(t>2.6f)break;}
                int col=0x1c1a1e;
                if(hitp){float e=.0001f;float[] n=norm(v(sdfAll(p[0]+e,p[1],p[2],mat)-sdfAll(p[0]-e,p[1],p[2],mat),sdfAll(p[0],p[1]+e,p[2],mat)-sdfAll(p[0],p[1]-e,p[2],mat),sdfAll(p[0],p[1],p[2]+e,mat)-sdfAll(p[0],p[1],p[2]-e,mat)));
                    sdfAll(p[0],p[1],p[2],mat);float[] mt=material(mat[0],p[0],p[1],p[2],0,0);col=shade(mt,n,fwd,L,L2,1);}
                synchronized(im){im.setRGB(px,py,col);}}
            return null;}));}
        for(Future<?> f:fs)f.get();ex.shutdown();
        float[] zb=new float[W*H];Arrays.fill(zb,1e9f);for(int i=0;i<W*H;i++)im.setRGB(W+i%W,i/W,0x1c1a1e);
        java.awt.Graphics2D gw=im.createGraphics();gw.setColor(new java.awt.Color(0,220,150,140));
        for(Chart c:charts)for(int[] t:c.tris){float[][] P=new float[3][],S=new float[3][];
            for(int k=0;k<3;k++){float[] q=c.verts.get(t[k]);P[k]=q;float[] rel=sub(v(q[0],q[1],q[2]),centre);S[k]=new float[]{W/2f+dot(rel,right)/scale,H/2f-dot(rel,up)/scale,dot(rel,fwd)};}
            float area=(S[1][0]-S[0][0])*(S[2][1]-S[0][1])-(S[2][0]-S[0][0])*(S[1][1]-S[0][1]);if(Math.abs(area)<1e-6f)continue;
            int xa=(int)Math.max(0,Math.min(S[0][0],Math.min(S[1][0],S[2][0]))),xb=(int)Math.min(W-1,Math.max(S[0][0],Math.max(S[1][0],S[2][0])));
            int ya=(int)Math.max(0,Math.min(S[0][1],Math.min(S[1][1],S[2][1]))),yb=(int)Math.min(H-1,Math.max(S[0][1],Math.max(S[1][1],S[2][1])));
            for(int y=ya;y<=yb;y++)for(int x=xa;x<=xb;x++){float qx=x+.5f,qy=y+.5f;
                float w0=((S[1][0]-qx)*(S[2][1]-qy)-(S[2][0]-qx)*(S[1][1]-qy))/area,w1=((S[2][0]-qx)*(S[0][1]-qy)-(S[0][0]-qx)*(S[2][1]-qy))/area,w2=1-w0-w1;
                if(w0<0||w1<0||w2<0)continue;float z=w0*S[0][2]+w1*S[1][2]+w2*S[2][2];if(z>=zb[y*W+x])continue;zb[y*W+x]=z;
                float u=w0*P[0][9]+w1*P[1][9]+w2*P[2][9],vv=w0*P[0][10]+w1*P[1][10]+w2*P[2][10];int ti=Math.min(AH-1,Math.max(0,(int)(vv*AH)))*AW+Math.min(AW-1,Math.max(0,(int)(u*AW)));
                float[] N=norm(v(w0*P[0][3]+w1*P[1][3]+w2*P[2][3],w0*P[0][4]+w1*P[1][4]+w2*P[2][4],w0*P[0][5]+w1*P[1][5]+w2*P[2][5]));
                float[] T=v(w0*P[0][6]+w1*P[1][6]+w2*P[2][6],w0*P[0][7]+w1*P[1][7]+w2*P[2][7],w0*P[0][8]+w1*P[1][8]+w2*P[2][8]);T=norm(sub(T,mul(N,dot(T,N))));float[] Bt=cross(N,T);
                float[] n=norm(add(add(mul(T,NX[ti]),Bt,NY[ti]),N,NZ[ti]));
                im.setRGB(W+x,y,lowWire?flat(N,fwd):shade(new float[]{A_R[ti],A_G[ti],A_B[ti],RO[ti],ME[ti],EM[ti]},n,fwd,L,L2,AO[ti]));}
            if(lowWire){java.awt.Polygon pg=new java.awt.Polygon(new int[]{W+(int)S[0][0],W+(int)S[1][0],W+(int)S[2][0]},new int[]{(int)S[0][1],(int)S[1][1],(int)S[2][1]},3);gw.drawPolygon(pg);}}
        ImageIO.write(im,"png",new File(dir,name));
    }
    static int flat(float[] n,float[] fwd){float s=Math.abs(dot(n,norm(v(.5f,.35f,.8f))))*.7f+.15f;int c=(int)(s*255);return (c<<16)|(c<<8)|Math.min(255,(int)(c*1.06f));}
    static int shade(float[] mt,float[] n,float[] fwd,float[] L,float[] L2,float ao){
        float[] V=mul(fwd,-1);if(dot(n,V)<0)n=mul(n,-1);float r=0,g=0,b=0;
        for(int li=0;li<2;li++){float[] l=li==0?L:L2;float k=li==0?1.6f:.6f;float nl=Math.max(0,dot(n,l));float[] h=norm(add(l,V,1));float nh=Math.max(0,dot(n,h));
            float sp=(float)Math.pow(nh,2+(1-mt[3])*120)*(1-mt[3])*1.5f;float[] f0=mt[4]>.5f?v(mt[0],mt[1],mt[2]):v(.04f,.04f,.04f);
            float kd=1-mt[4];r+=(mt[0]*kd*nl+f0[0]*sp*nl)*k;g+=(mt[1]*kd*nl+f0[1]*sp*nl)*k;b+=(mt[2]*kd*nl+f0[2]*sp*nl)*k;}
        float amb=.14f*ao;r+=mt[0]*amb+(mt[4]>.5f?mt[0]*.3f*ao:0);g+=mt[1]*amb+(mt[4]>.5f?mt[1]*.3f*ao:0);b+=mt[2]*amb+(mt[4]>.5f?mt[2]*.3f*ao:0);
        r*=ao;g*=ao;b*=ao;
        return (b8(srgb(r))<<16)|(b8(srgb(g))<<8)|b8(srgb(b));
    }

    public static void main(String[] a)throws Exception{
        File assets=new File(a[0]),prev=new File(a[1]);assets.mkdirs();prev.mkdirs();String mode=a.length>2?a[2]:"";
        long t0=System.currentTimeMillis();
        CAGE=.012f;RANGE=.006f;NAME="cannon";
        buildLow();int lowTris=0;for(Chart c:charts)lowTris+=c.tris.size();
        System.out.println("low poly: "+lowTris+" triangles; "+charts.size()+" UV charts");
        if(mode.equals("low")){AW=AH=64;A_R=A_G=A_B=AO=NX=NY=NZ=HT=RO=ME=EM=new float[AW*AH];preview(prev,-.7f,.35f,"cannon_low.png",v(.33f,0,.03f),.85f,900,500,true);return;}
        pack();System.out.printf(Locale.ROOT,"atlas %dx%d at %.0f texels per metre (%.2f mm per texel)%n",AW,AH,texelsPerMetre,1000/texelsPerMetre);
        if(mode.equals("high")){long hp=meshHigh(0,.0005f,null);System.out.println("high poly: "+hp+" triangles");}
        System.out.println("baking…");bake();dilate(10);writeMaps(assets);
        float pom=2*RANGE*texelsPerMetre/AW;
        preview(prev,-.7f,.35f,"cannon_bake.png",v(.33f,0,.03f),.85f,900,500,false);
        preview(prev,2.4f,.3f,"cannon_bake_b.png",v(.33f,0,.03f),.85f,900,500,false);
        preview(prev,-.7f,.35f,"cannon_wire.png",v(.33f,0,.03f),.85f,900,500,true);
        int tr=writeMesh(new File(assets,"cannon.bin"),pom);System.out.println("mesh written: "+tr+" triangles");
        System.out.println("done in "+(System.currentTimeMillis()-t0)/1000+" s");
    }
}