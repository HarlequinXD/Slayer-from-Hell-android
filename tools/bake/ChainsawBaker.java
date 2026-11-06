import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/**
 * Offline high-to-low bake for the warden's chainsaw arm: a forearm-mounted chainsaw whose sleeve she slides her right
 * arm into (padded opening, grip bar inside, riveted bands, leather straps and buckles), with a two-stroke engine on
 * top (fuel tank, finned cylinder, head cover, plug and lead, muffler and pipes, recoil starter, air filter), a front
 * housing with the chain-brake guard and bucking spikes, a 53 cm guide bar, a chain of 136 links, and a caddy on
 * the sleeve that holds two spare shotgun shells for her one-handed reloads. Paint: "hazard" amber with black chevrons.
 *
 * 1. HIGH POLY: signed distance field with sub-millimetre detail (and a straight run of chain, every link modelled).
 * 2. LOW POLY: box modelled, under 1000 triangles: the major forms are geometry; the small detail goes in the bake.
 * 3. BAKE: normal, height, AO, albedo, roughness, metalness. The body has its own atlas; the chain bakes onto a
 *    tiling strip (8 links) so the loop around the bar can scroll its texture when the chain runs.
 *
 * Output: chainsaw.bin (group 0 body, group 1 chain loop), chainsaw_a/b/c.png, chain_a/b/c.png, previews.
 *
 *   javac ChainsawBaker.java && java -Xmx4g ChainsawBaker <assets dir> <preview dir> [high]
 */
public class ChainsawBaker {
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

    // ================================================================== the high poly sculpt: the chainsaw arm
    // Saw space: x along her forearm (0 = the sleeve's opening at the elbow end, the guide bar's nose at x = .97),
    // y to the left, z up (the engine sits on top). Metres.
    static final int M_PAINT=0,M_STEEL=1,M_ALU=2,M_CHROME=3,M_GUN=4,M_LEATHER=5,M_RUBBER=6,M_BRASS=7,M_CERAMIC=8,M_HOLE=9;
    static int MODE=0;   // 0 = the saw body, 1 = a straight run of chain (for the tiling chain strip)
    static float sdfAll(float x,float y,float z,int[] mat){return MODE==0?body(x,y,z,mat):chain(x,y,z,mat);}
    static float sdf(int g,float x,float y,float z,int[] mat){return sdfAll(x,y,z,mat);}
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

    // ---------------- the parts
    static final float SY=.94f;   // the sleeve is a little flattened side to side
    static final float[] SLEEVE={0,.058f, .004f,.066f, .04f,.068f, .15f,.067f, .28f,.064f, .33f,.06f, .36f,.046f, .372f,.024f, .375f,0, .3f,0, .3f,.045f, .02f,.049f, 0,.05f};
    static final float CA=(float)Math.cos(.35),SA=(float)Math.sin(.35);
    static final float[] CB={.235f,0,.145f};   // base of the tilted cylinder
    static float[] cylLocal(float x,float y,float z){float dx=x-CB[0],dz=z-CB[2];return v(dx*CA-dz*SA,y,dx*SA+dz*CA);}   // (across, y, along the cylinder)
    static float[] cylWorld(float lx,float ly,float lz){return v(CB[0]+lx*CA+lz*SA,ly,CB[2]-lx*SA+lz*CA);}
    static final float[] HD=cylWorld(0,0,.122f);
    static final float[] SP=add(HD,v(.048f,0,-.012f),1);
    static final float[] PLUG_AX=norm(v(.62f,0,.78f));
    static final float[][] LEAD=spline(new float[][]{add(SP,PLUG_AX,.062f),v(.36f,.006f,.315f),v(.33f,.03f,.302f),v(.285f,.045f,.293f),v(.235f,.05f,.27f),v(.19f,.055f,.235f),v(.16f,.056f,.2f),v(.15f,.056f,.175f)},10);
    static final float[] LEAD_B=tubeBox(LEAD,.0045f);
    static final float[][] PIPE0=spline(new float[][]{v(.165f,-.08f,.1f),v(.13f,-.095f,.1f),v(.105f,-.11f,.11f)},10),PIPE1=spline(new float[][]{v(.165f,-.08f,.12f),v(.13f,-.107f,.12f),v(.105f,-.122f,.13f)},10);
    static final float[][] FUEL=spline(new float[][]{v(.11f,-.04f,.14f),v(.14f,-.06f,.165f),v(.17f,-.05f,.15f)},10);
    static final float[][] CORD=spline(new float[][]{v(.19f,.07f,.07f),v(.17f,.08f,.045f),v(.12f,.082f,.048f),v(.095f,.082f,.06f)},10);
    static final float BX0=.40f,BX1=.93f,BR=.034f;
    static final float[] DOGS={-.045f,-.052f, 0,-.052f, .026f,-.04f, 0,-.035f, .026f,-.023f, 0,-.018f, .026f,-.006f, 0,-.001f, -.045f,-.001f};
    static final float[] CADDY={.2f,-.084f,-.01f};   // where the two spare shells stand (rim plane), hull pointing up

    static float body(float x,float y,float z,int[] mat){
        float bd=bound(x,y,z,-.02f,.98f,-.135f,.1f,-.08f,.33f);if(bd>.01f){mat[0]=M_GUN;return bd;}
        float d=1e9f;int m=M_GUN;float e;
        // sleeve shell, padded rim, riveted bands, leather straps and buckles, the grip bar inside
        if(!(x<.4f&&Math.abs(z)<.085f&&Math.abs(y)<.085f))d=Math.min(d,bound(x,y,z,-1,.4f,-.085f,.085f,-.085f,.085f)+.001f);
        else{
            float r=(float)Math.hypot(y/SY,z);
            e=poly(x,r,SLEEVE)*.97f;
            float sc=Math.abs(z)<.003f&&y<0?.0012f:0;e+=sc;               // the clamshell seam along the outer side
            if(e<d){d=e;m=M_GUN;}
            float dr=r-.058f;e=(float)Math.sqrt(x*x+dr*dr)-.009f;e+=.0004f*(float)Math.sin(Math.atan2(z,y)*90);if(e<d){d=e;m=M_LEATHER;}
            if(x>.02f&&x<.3f&&r<.051f&&poly(x,r,SLEEVE)<.002f){m=M_LEATHER;}
            for(float bx:new float[]{.105f,.185f,.265f}){e=Math.max(Math.abs(x-bx)-.007f,r-.0705f)-.002f;e=Math.max(e,-(r-.06f));if(e<d){d=e;m=M_GUN;}
                float a=(float)Math.atan2(z,y/SY);float ak=Math.round(a/(2*PI/18))*(2*PI/18);float ry=(float)Math.cos(ak)*.0725f*SY,rz=(float)Math.sin(ak)*.0725f;
                e=sphere(x,y,z,bx,ry,rz,.0028f);if(e<d){d=e;m=M_BRASS;}}
            for(float sx:new float[]{.06f,.225f}){e=Math.max(Math.abs(x-sx)-.011f,r-.0722f)-.001f;e=Math.max(e,-(r-.06f));
                float st=Math.abs(Math.abs(x-sx)-.008f);if(st<.0007f&&((int)(Math.atan2(z,y)*80)&1)==0)e+=.0005f;
                if(e<d){d=e;m=M_LEATHER;}
                e=box(x,y,z,sx,-.071f,-.012f,.016f,.005f,.015f,.003f);if(e<d){d=e;m=M_CHROME;}
                e=box(x,y,z,sx,-.0765f,-.012f,.012f,.003f,.003f,.0015f);if(e<d){d=e;m=M_BRASS;}}
            e=cylY(x,y,z,.25f,0,.012f,-.035f,.035f);if(e<d){d=e;m=M_RUBBER;}
        }
        // shell caddy on the outer side of the sleeve: a cup that holds two spare shells upright
        e=box(x,y,z,CADDY[0],CADDY[1]+.002f,CADDY[2]-.022f,.03f,.014f,.011f,.004f);
        e=Math.max(e,-Math.min(cylZ(x,y,z,CADDY[0]-.0118f,CADDY[1],.0105f,CADDY[2]-.024f,.1f),cylZ(x,y,z,CADDY[0]+.0118f,CADDY[1],.0105f,CADDY[2]-.024f,.1f)));
        if(e<d){d=e;m=M_GUN;}
        e=box(x,y,z,CADDY[0],-.07f,CADDY[2]-.022f,.022f,.006f,.008f,.002f);if(e<d){d=e;m=M_GUN;}
        // engine: mount plate, fuel tank and cap, crankcase
        e=box(x,y,z,.2f,0,.07f,.135f,.045f,.008f,.005f);if(e<d){d=e;m=M_GUN;}
        e=box(x,y,z,.07f,0,.124f,.06f,.056f,.046f,.026f);if(e<d){d=e;m=M_PAINT;}
        e=cylZ(x,y,z,.055f,.022f,.019f,.155f,.178f);float ka=(float)Math.atan2(y-.022f,x-.055f);if(z>.163f)e-=.0012f*smooth(.3f,.7f,.5f+.5f*(float)Math.sin(ka*24));if(e<d){d=e;m=M_ALU;}
        e=box(x,y,z,.205f,0,.112f,.0825f,.0475f,.0375f,.014f);e=groove(e,x-.205f,.0012f,.0012f);if(e<d){d=e;m=M_ALU;}
        // cylinder and cooling fins (tilted forward), head cover with vents, spark plug and boot, the lead and its clips
        {float[] L=cylLocal(x,y,z);
            if(!(L[2]>-.01f&&L[2]<.15f&&Math.abs(L[0])<.06f&&Math.abs(y)<.06f))d=Math.min(d,bound(L[0],L[1],L[2],-.06f,.06f,-.06f,.06f,-.01f,.15f)+.001f);
            else{
                e=cylZ(L[0],L[1],L[2],0,0,.025f,0,.11f);if(e<d){d=e;m=M_ALU;}
                float k=Math.round((L[2]-.012f)/.0082f);if(k>=0&&k<12){e=box(L[0],L[1],L[2],0,0,.012f+k*.0082f,.04f,.042f,.0017f,.0015f);if(e<d){d=e;m=M_ALU;}}
                e=box(L[0],L[1],L[2],0,0,.122f,.05f,.05f,.02f,.016f);
                if(L[2]>.14f){float vk=Math.round((L[0]+.032f)/.013f);if(vk>=0&&vk<6&&Math.abs(L[0]-(-.032f+vk*.013f))<.003f&&Math.abs(y)<.035f)e+=.003f;}
                if(e<d){d=e;m=M_PAINT;}}}
        {float[] q=sub(v(x,y,z),SP);float t=dot(q,PLUG_AX);float[] rr=sub(q,mul(PLUG_AX,t));float rad=len(rr);
            if(!(t>-.01f&&t<.1f&&rad<.02f))d=Math.min(d,Math.max(Math.max(-.01f-t,t-.1f),rad-.02f)+.001f);
            else{
                float pr=t<0?.0f:t<.012f?.008f:t<.03f?.006f:t<.034f?.0045f:t<.045f?.0035f:0;
                if(t>=-.004f&&t<=.008f){float hx=(float)Math.max(Math.abs(rr[1]),Math.abs(rr[1])*.5f+Math.abs(dot(rr,norm(cross(PLUG_AX,v(0,1,0)))))*.866f);e=Math.max(hx-.0095f,Math.abs(t-.002f)-.006f);if(e<d){d=e;m=M_STEEL;}}
                e=Math.max(rad-pr,Math.max(-t,t-.05f));if(e<d){d=e;m=M_CERAMIC;}
                float br=t<.03f?0:t<.034f?mix(.006f,.0085f,(t-.03f)/.004f):t<.06f?.0085f:t<.064f?.0045f:0;
                e=Math.max(rad-br,Math.max(.03f-t,t-.064f));if(br>0&&e<d){d=e;m=M_RUBBER;}}}
        e=tubeB(x,y,z,LEAD,LEAD_B,.0042f);if(e<d){d=e;m=M_RUBBER;}
        for(float[] c:new float[][]{{.285f,.045f,.289f},{.2f,.054f,.239f}}){e=box(x,y,z,c[0],c[1],c[2],.006f,.008f,.004f,.003f);if(e<d){d=e;m=M_CHROME;}}
        // muffler with a perforated heat shield and twin exhaust pipes
        e=box(x,y,z,.225f,-.075f,.125f,.06f,.017f,.034f,.012f);
        if(y<-.085f){float hi=Math.round((x-.18f)/.014f),hj=Math.round((z-.105f)/.018f);if(hi>=0&&hi<7&&hj>=0&&hj<3){float h=(float)Math.hypot(x-(.18f+hi*.014f),z-(.105f+hj*.018f));if(h<.0034f){e=Math.max(e,-(y+.0905f));if(e<d){d=e;m=M_HOLE;}}}}
        if(e<d){d=e;m=M_CHROME;}
        for(float[][] pp:new float[][][]{PIPE0,PIPE1}){e=Math.max(tube(x,y,z,pp,.0065f),-tube(x,y,z,pp,.0048f));
            float[] end=pp[pp.length-1];if(sphere(x,y,z,end[0],end[1],end[2],.009f)<0&&x<end[0]+.003f){e=Math.max(tube(x,y,z,pp,.0065f),-(tube(x,y,z,pp,.0048f)));}
            if(e<d){d=e;m=dot(sub(v(x,y,z),end),v(1,0,0))<-.001f&&tube(x,y,z,pp,.0052f)<0?M_HOLE:M_CHROME;}}
        // recoil starter (on the left side), its vent slots and hub, the cord and T-handle
        {float ly=y-.058f,rr=(float)Math.hypot(x-.19f,z-.1f);
            if(!(Math.abs(ly)<.02f&&rr<.055f))d=Math.min(d,Math.max(Math.abs(ly)-.02f,rr-.055f)+.001f);
            else{e=poly(ly,rr,new float[]{-.011f,0,-.011f,.042f,-.008f,.048f,.007f,.05f,.011f,.046f,.011f,0});
                float a=(float)Math.atan2(z-.1f,x-.19f);float ak=Math.round(a/(2*PI/14))*(2*PI/14);float sx=(float)Math.cos(ak)*.03f,sz=(float)Math.sin(ak)*.03f;
                if(ly>.006f){float[] tdir=v(-(float)Math.sin(ak),0,(float)Math.cos(ak));float[] rdir=v((float)Math.cos(ak),0,(float)Math.sin(ak));float[] q=v(x-.19f-sx,0,z-.1f-sz);
                    if(Math.abs(dot(q,rdir))<.008f&&Math.abs(dot(q,tdir))<.002f)e+=.004f;}
                if(e<d){d=e;m=M_PAINT;}
                e=cylY(x,y,z,.19f,.1f,.012f,.065f,.073f);if(e<d){d=e;m=M_BRASS;}}}
        e=tube(x,y,z,CORD,.0018f);if(e<d){d=e;m=M_RUBBER;}
        {float c=(float)Math.cos(.25f),s=(float)Math.sin(.25f);float dx=x-.088f,dz=z-.062f;e=box(dx*c-dz*s,y,dx*s+dz*c,0,.084f,0,.006f,.006f,.025f,.005f);if(e<d){d=e;m=M_RUBBER;}}
        // air filter box and knob, fuel line
        e=box(x,y,z,.14f,.034f,.165f,.035f,.025f,.02f,.012f);if(e<d){d=e;m=M_PAINT;}
        e=cylZ(x,y,z,.14f,.034f,.01f,.183f,.193f);if(e<d){d=e;m=M_BRASS;}
        e=tube(x,y,z,FUEL,.0035f);if(e<d){d=e;m=M_RUBBER;}
        // front housing over the drive sprocket, bar nuts, vents; chain-brake guard on its hinge; bucking spikes
        e=box(x,y,z,.405f,-.0145f,0,.065f,.0445f,.0575f,.022f);
        e=Math.max(e,-box(x,y,z,.47f,0,0,.03f,.0062f,.045f,.002f));                    // slot for the bar and chain
        if(y<-.056f){float vk=Math.round((x-.37f)/.018f);if(vk>=0&&vk<5&&Math.abs(x-(.37f+vk*.018f))<.0035f&&Math.abs(z-.035f)<.014f)e+=.003f;}
        if(e<d){d=e;m=M_GUN;}
        for(float nx:new float[]{.385f,.425f}){float ny=-.061f,nzz=.022f;float hx=Math.max(Math.abs(x-nx)*.866f+Math.abs(z-nzz)*.5f,Math.abs(z-nzz));e=Math.max(hx-.008f,Math.abs(y-ny)-.006f);if(e<d){d=e;m=M_BRASS;}}
        e=cylY(x,y,z,.43f,.062f,.006f,-.06f,.04f);if(e<d){d=e;m=M_STEEL;}
        {float c=(float)Math.cos(-.35f),s=(float)Math.sin(-.35f);
            for(float by:new float[]{-.05f,.03f}){float dx=x-.432f,dz=z-.06f;e=box(dx*c-dz*s,y,dx*s+dz*c,0,by,0,.015f,.004f,.011f,.003f);if(e<d){d=e;m=M_GUN;}}
            float dx=x-.442f,dz=z-.098f;float px=dx*c-dz*s,pz=dx*s+dz*c;e=box(px,y,pz,0,-.01f,0,.007f,.0475f,.0375f,.006f);e=groove(e,pz,.0015f,.0012f);if(e<d){d=e;m=M_PAINT;}}
        if(!(y<-.055f&&y>-.068f&&x>.41f&&x<.5f))d=Math.min(d,bound(x,y,z,.41f,.5f,-.068f,-.055f,-1,1)+.001f);
        else{e=Math.max(poly(x-.468f,z,DOGS)-.0006f,Math.abs(y+.061f)-.002f);if(e<d){d=e;m=M_STEEL;}
            for(float bz:new float[]{-.012f,-.04f}){e=cylY(x,y,z,.44f,bz,.0045f,-.066f,-.06f);if(e<d){d=e;m=M_BRASS;}}}
        // guide bar: plate, polished rails, nose sprocket rivets
        if(!(x>BX0-.045f&&Math.abs(y)<.006f&&Math.abs(z)<.045f))d=Math.min(d,bound(x,y,z,BX0-.045f,2,-.006f,.006f,-.045f,.045f)+.001f);
        else{
            float px=clamp(x,BX0,BX1);float sd=(float)Math.hypot(x-px,z)-BR;
            e=Math.max(sd,Math.abs(y)-.0025f);if(e<d){d=e;m=M_GUN;}
            e=Math.max(Math.max(sd-.0004f,-(sd+.0035f)),Math.abs(y)-.0028f);if(e<d){d=e;m=M_STEEL;}
            float a=(float)Math.atan2(z,x-BX1);float ak=Math.round(a/(PI/4))*(PI/4);e=cylY(x,y,z,BX1+(float)Math.cos(ak)*.016f,(float)Math.sin(ak)*.016f,.0025f,-.0031f,.0031f);if(e<d){d=e;m=M_CHROME;}
            if(Math.abs(z)<.012f&&x>BX0+.08f&&x<BX0+.3f&&Math.abs(y)>.002f){e+=0;}}
        mat[0]=m;return d;
    }

    // ---------------- a straight run of chain along x (tiles every 8 links), centred on the chain's path line z = 0
    static final int CHAIN_N=136;static float PITCH;   // set from the loop length in main
    static float chain(float x,float y,float z,int[] mat){
        float d=1e9f;int m=M_STEEL;float e;
        int i0=(int)Math.floor(x/PITCH)-1;
        for(int i=i0;i<=i0+3;i++){float cx=i*PITCH,u=x-cx;int im=((i%8)+8)%8;
            if((im&1)==0){   // drive link in the middle plane, tang down into the bar groove
                float pl=Math.max(box(u,0,z,0,0,0,.0072f,1,.004f,.0034f),-1e9f);float tang=poly(u,z,new float[]{-.004f,-.002f,.004f,-.002f,0,-.0105f});
                e=Math.max(Math.min(pl,tang),Math.abs(y)-.0006f);if(e<d){d=e;m=M_STEEL;}}
            else{boolean cut=(im&3)==1;int side=(im&7)==1?1:-1;
                for(int sd=-1;sd<=1;sd+=2){e=Math.max(box(u,0,z,0,0,0,.0082f,1,.0042f,.0038f),Math.abs(y-sd*.0029f)-.00055f);if(e<d){d=e;m=M_STEEL;}
                    if(cut&&sd==side){float t=box(u,y,z,.001f,sd*.0018f,.0056f,.0047f,.0036f,.0008f,.0005f);
                        float chisel=Math.max(t,-(u-.0055f)*.0f-1);t=Math.max(t,(u-.0058f)-(z-.0048f)*.6f);
                        if(t<d){d=t;m=M_CHROME;}
                        float tooth=box(u,y,z,-.0005f,sd*.0029f,.0046f,.005f,.00055f,.0015f,.0005f);if(tooth<d){d=tooth;m=M_CHROME;}
                        float g=box(u,y,z,-.0055f,sd*.0029f,.0047f,.0015f,.0008f,.001f,.0005f);if(g<d){d=g;m=M_CHROME;}}}}
            float rx=x-(i+.5f)*PITCH;e=cylY(rx,y,z,0,0,.0016f,-.0046f,.0046f);e=Math.min(e,Math.min(sphere(rx,y,z,0,.0044f,0,.0019f),sphere(rx,y,z,0,-.0044f,0,.0019f)));if(e<d){d=e;m=M_CHROME;}}
        mat[0]=m;return d;
    }
    static float groove(float d,float dist,float w,float depth){return d+depth*(1-smooth(0,w,Math.abs(dist)));}

    // ================================================================== materials of the high poly
    /** Albedo (linear), roughness, metalness, emission; lap = curvature (edge wear), cav = cavity. The paint is "hazard": amber with black chevrons. */
    static float[] material(int m,float x,float y,float z,float lap,float cav){
        float r,g,b,rough,metal;float grime=smooth(.55f,.85f,fbm(x*9+3,y*9,z*9,4))*.5f;float n=fbm(x*45,y*45,z*45,4);
        switch(m){
            case M_PAINT:{float pat=((x+Math.abs(y)*.9f+z*.4f)*16)%1f;pat=pat<0?pat+1:pat;boolean blk=pat>.5f;
                r=blk?.018f:.72f;g=blk?.018f:.46f;b=blk?.02f:.02f;
                float wear=smooth(.62f,.8f,fbm(x*70,y*70,z*70,4)*.6f+lap*.0011f+fbm(x*9+7,y*9,z*9,3)*.35f);
                r=mix(r,.6f,wear);g=mix(g,.6f,wear);b=mix(b,.62f,wear);float k=1-grime*.6f-cav*.5f;r*=k;g*=k;b*=k;
                rough=mix(.3f+n*.12f,.36f,wear)+grime*.3f;metal=wear;break;}
            case M_STEEL:{float st=noise(x*7,y*1100,z*1100);r=g=b=.62f*(.88f+st*.18f);rough=.18f+st*.14f+cav*.3f;metal=1;float k=1-cav*.6f-grime*.3f;r*=k;g*=k;b*=k;break;}
            case M_ALU:{float k=(.82f+.2f*n)*(1-grime)*(1-cav*.6f);r=.58f*k;g=.59f*k;b=.6f*k;rough=.5f+.15f*n;metal=1;break;}
            case M_CHROME:{float k=1-cav*.5f;r=g=.86f*k;b=.88f*k;rough=.06f+.06f*n;metal=1;break;}
            case M_GUN:{float sc=noise(x*600,y*30,z*30)>.93f?1:0;float wear=smooth(.6f,.9f,lap*.0011f+n*.3f);float k=(.85f+.25f*n)*(1-grime*.5f)*(1-cav*.5f);
                r=mix(.11f*k,.5f,Math.max(sc,wear));g=mix(.115f*k,.5f,Math.max(sc,wear));b=mix(.13f*k,.52f,Math.max(sc,wear));rough=.36f+.12f*n;metal=.9f;break;}
            case M_LEATHER:{float k=.7f+.6f*fbm(x*350,y*350,z*350,3);r=.13f*k;g=.075f*k;b=.045f*k;rough=.68f+.1f*n;metal=0;break;}
            case M_RUBBER:{r=g=b=.028f*(.8f+.4f*n);rough=.85f;metal=0;break;}
            case M_BRASS:{float k=.9f+.15f*n;r=.78f*k;g=.56f*k;b=.24f*k;rough=.25f+.1f*n;metal=1;break;}
            case M_CERAMIC:{r=.85f;g=.83f;b=.78f;rough=.22f;metal=0;break;}
            default:{r=g=b=.01f;rough=.8f;metal=0;}
        }
        return new float[]{r,g,b,rough,metal,0};
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

    static void buildLow(){
        int K=10;
        // sleeve: outer envelope (over bands and straps) closing to a nose, the padded opening, and the bore inside
        float[][] xs={{0,.067f},{.05f,.0725f},{.235f,.0725f},{.285f,.0665f},{.33f,.061f},{.36f,.047f},{.372f,.026f}};
        float[][][] rs=new float[xs.length][][];for(int i=0;i<xs.length;i++)rs[i]=sleeveRing(xs[i][0],xs[i][1],K);
        loft(0,rs,false,true);
        annulus(0,sleeveRing(0,.067f,K),sleeveRing(0,.05f,K));
        loft(0,new float[][][]{sleeveRing(0,.05f,K),sleeveRing(.29f,.046f,K)},false,true);
        for(int k=1;k<=2;k++){Chart bc=charts.get(charts.size()-k);for(float[] q:bc.verts){q[3]=-q[3];q[4]=-q[4];q[5]=-q[5];}orient(bc);}   // the bore and its end wall face inward
        // shell caddy, mount plate
        box(0,CADDY[0]-.03f,CADDY[1]-.012f,CADDY[2]-.033f,CADDY[0]+.03f,-.062f,CADDY[2]-.011f);
        box(0,.065f,-.045f,.062f,.335f,.045f,.078f);
        // fuel tank and cap, crankcase
        prismX(.01f,.13f,0,.124f,oct(.056f,.046f,.02f));
        prism(v(.055f,.022f,.155f),v(.055f,.022f,.178f),X,Y,ellipse(.019f,.019f,6,0),false,true);
        prismX(.1225f,.2875f,0,.112f,oct(.0475f,.0375f,.012f));
        // cylinder fin block and head cover (tilted), spark plug + boot, the lead
        float[] cax=norm(sub(cylWorld(0,0,1),cylWorld(0,0,0))),ce1=norm(sub(cylWorld(1,0,0),cylWorld(0,0,0)));float[] ce2=cross(cax,ce1);
        prism(cylWorld(0,0,.005f),cylWorld(0,0,.104f),ce1,ce2,oct(.042f,.044f,.014f),false,false);
        prism(cylWorld(0,0,.102f),cylWorld(0,0,.142f),ce1,ce2,oct(.05f,.05f,.016f),true,true);
        {float[] pe1=norm(cross(PLUG_AX,Y)),pe2=cross(PLUG_AX,pe1);
            loft(0,new float[][][]{ring(add(SP,PLUG_AX,-.004f),pe1,pe2,ellipse(.0098f,.0098f,6,0)),ring(add(SP,PLUG_AX,.03f),pe1,pe2,ellipse(.0072f,.0072f,6,0)),ring(add(SP,PLUG_AX,.034f),pe1,pe2,ellipse(.009f,.009f,6,0)),ring(add(SP,PLUG_AX,.062f),pe1,pe2,ellipse(.009f,.009f,6,0))},false,false);}
        tubeLow(LEAD,.0052f,4,6,false);
        // muffler and pipes
        prismX(.165f,.285f,-.075f,.125f,oct(.017f,.034f,.009f));
        tubeLow(PIPE0,.0072f,4,2,false);tubeLow(PIPE1,.0072f,4,2,false);
        // recoil starter disc (axis +y), cord and T-handle
        prism(v(.19f,.047f,.1f),v(.19f,.071f,.1f),Z,X,ellipse(.051f,.051f,8,PI/8),false,true);
        tubeLow(CORD,.0028f,4,3,false);
        {float c=(float)Math.cos(.25f),s=(float)Math.sin(.25f);float[] e1=v(c,0,-s),ez=v(s,0,c);prism(add(v(.088f,.084f,.062f),ez,-.026f),add(v(.088f,.084f,.062f),ez,.026f),e1,Y,oct(.0065f,.0065f,.002f),true,true);}
        // air filter box, fuel line
        prismX(.103f,.177f,.034f,.165f,oct(.026f,.021f,.008f));
        tubeLow(FUEL,.0045f,4,3,false);
        // front housing; chain-brake guard on its hinge block; bucking spikes
        prismX(.338f,.472f,-.0145f,0,oct(.0455f,.0585f,.016f));
        {float c=(float)Math.cos(-.35f),s=(float)Math.sin(-.35f);float[] e1=v(c,0,-s),ez=v(s,0,c);
            float[] g=v(.442f,-.01f,.098f);prism(add(g,Y,-.0575f),add(g,Y,.0375f),ez,e1,oct(.038f,.0075f,.003f),true,true);
            float[] h=v(.432f,-.01f,.06f);prism(add(h,Y,-.056f),add(h,Y,.036f),ez,e1,oct(.012f,.016f,.004f),true,true);}
        box(0,.423f,-.0635f,-.052f,.469f,-.0585f,-.001f);
        for(int k=0;k<3;k++){float z0=-.052f+k*.017f;prism(v(0,-.063f,0),v(0,-.059f,0),X,Z,new float[][]{{.468f,z0+.0005f},{.495f,z0+.006f},{.468f,z0+.0165f}},true,true);}
        // guide bar: a stadium plate (rails, rivets and the groove are baked)
        {int A=6;float[][] st=new float[2*(A+1)][];int n=0;
            for(int i=0;i<=A;i++){double a=-Math.PI/2+i*Math.PI/A;st[n++]=new float[]{BX1+(float)Math.cos(a)*(BR+.0004f),(float)Math.sin(a)*(BR+.0004f)};}
            for(int i=0;i<=A;i++){double a=Math.PI/2+i*Math.PI/A;st[n++]=new float[]{BX0-.03f+(float)Math.cos(a)*(BR+.0004f)*.3f,(float)Math.sin(a)*(BR+.0004f)};}
            float[][] rev=new float[st.length][];for(int i=0;i<st.length;i++)rev[i]=st[st.length-1-i];
            prism(v(0,-.0029f,0),v(0,.0029f,0),X,Z,rev,true,true);}
    }

    // ---------------- the chain: a straight strip to bake (8 links, tiling), and the loop around the bar for the game
    static final float CH_OUT=.0076f,CH_IN=-.0046f,CH_W=.0047f;
    static float CR=BR+.0042f,LOOP_L=BX1-BX0,PER;
    static void buildChainStrip(){
        float L=8*PITCH;
        face(new float[][]{{0,-CH_W,CH_OUT},{L,-CH_W,CH_OUT},{L,CH_W,CH_OUT},{0,CH_W,CH_OUT}},Z,new float[]{0,0,1,0,1,.28f,0,.28f});
        face(new float[][]{{0,CH_W,CH_OUT},{L,CH_W,CH_OUT},{L,CH_W,CH_IN},{0,CH_W,CH_IN}},Y,new float[]{0,.36f,1,.36f,1,.64f,0,.64f});
        face(new float[][]{{0,-CH_W,CH_IN},{L,-CH_W,CH_IN},{L,-CH_W,CH_OUT},{0,-CH_W,CH_OUT}},v(0,-1,0),new float[]{0,.72f,1,.72f,1,1,0,1});
    }
    static void face(float[][] p,float[] n,float[] uv){Chart c=chart(0);int[] id=new int[4];for(int i=0;i<4;i++)id[i]=vert(c,p[i],n,X,uv[i*2],uv[i*2+1]);c.tris.add(new int[]{id[0],id[1],id[2]});c.tris.add(new int[]{id[0],id[2],id[3]});}
    /** Path around the bar: {x, z, tangent x, tangent z, normal x, normal z} at arc length s. */
    static float[] path(float s){
        s=((s%PER)+PER)%PER;float th;
        if(s<LOOP_L)return new float[]{BX0+s,CR,1,0,0,1};s-=LOOP_L;
        if(s<PI*CR){th=PI/2-s/CR;return new float[]{BX1+CR*(float)Math.cos(th),CR*(float)Math.sin(th),(float)Math.sin(th),-(float)Math.cos(th),(float)Math.cos(th),(float)Math.sin(th)};}s-=PI*CR;
        if(s<LOOP_L)return new float[]{BX1-s,-CR,-1,0,0,-1};s-=LOOP_L;
        th=-PI/2-s/CR;return new float[]{BX0+CR*(float)Math.cos(th),CR*(float)Math.sin(th),(float)Math.sin(th),-(float)Math.cos(th),(float)Math.cos(th),(float)Math.sin(th)};
    }
    /** The chain loop for the game (group 1): outer face and both sides, u = links / 8 (the texture repeats), scrolls along u. */
    static ArrayList<float[]> chainLoop(){
        ArrayList<Float> ss=new ArrayList<>();int A=6;
        ss.add(0f);ss.add(LOOP_L);for(int i=1;i<A;i++)ss.add(LOOP_L+PI*CR*i/A);ss.add(LOOP_L+PI*CR);ss.add(2*LOOP_L+PI*CR);for(int i=1;i<A;i++)ss.add(2*LOOP_L+PI*CR+PI*CR*i/A);ss.add(PER);
        ArrayList<float[]> out=new ArrayList<>();float uS=1/(8*PITCH);
        for(int i=0;i+1<ss.size();i++){float s0=ss.get(i),s1=ss.get(i+1);float[] a=path(s0),b=path(s1==PER?PER-1e-6f:s1);if(s1==PER)b=path(0);
            float u0=s0*uS,u1=s1*uS;
            // per face: corner positions as (radial, lateral) offsets from the path line
            float[][][] f={{{CH_OUT,-CH_W},{CH_OUT,CH_W}},{{CH_OUT,CH_W},{CH_IN,CH_W}},{{CH_IN,-CH_W},{CH_OUT,-CH_W}}};
            float[][] vr={{0,.28f},{.36f,.64f},{.72f,1}};
            for(int k=0;k<3;k++){float[] p00=cp(a,f[k][0]),p01=cp(a,f[k][1]),p10=cp(b,f[k][0]),p11=cp(b,f[k][1]);
                float[] na=k==0?v(a[4],0,a[5]):k==1?Y:v(0,-1,0),nb=k==0?v(b[4],0,b[5]):na;float[] ta=v(a[2],0,a[3]),tb=v(b[2],0,b[3]);
                float[] A0=vx(p00,na,ta,u0,vr[k][0]),A1=vx(p01,na,ta,u0,vr[k][1]),B0=vx(p10,nb,tb,u1,vr[k][0]),B1=vx(p11,nb,tb,u1,vr[k][1]);
                out.add(A0);out.add(B0);out.add(B1);out.add(A0);out.add(B1);out.add(A1);}}
        return out;
    }
    static float[] cp(float[] a,float[] o){return v(a[0]+a[4]*o[0],o[1],a[1]+a[5]*o[0]);}
    static float[] vx(float[] p,float[] n,float[] t,float u,float vv){return new float[]{p[0],p[1],p[2],n[0],n[1],n[2],t[0],t[1],t[2],u,vv};}
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
        float x0=-.02f,x1=.98f,y0=-.135f,y1=.1f,z0=-.08f,z1=.33f;
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
    /** chainsaw.bin: magic, pom scale (body), group count (2), then per group a vertex count and 11 floats per vertex. */
    static int[] writeMesh(File f,ArrayList<Chart> body,float pom,ArrayList<float[]> loop)throws Exception{
        ArrayList<float[]> g0=new ArrayList<>();for(Chart c:body)for(int[] t:c.tris)for(int k:t)g0.add(c.verts.get(k));
        ByteBuffer bb=ByteBuffer.allocate(20+(g0.size()+loop.size())*44).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(0x4e554753);bb.putFloat(pom);bb.putInt(2);
        bb.putInt(g0.size());for(float[] q:g0)for(int j=0;j<11;j++)bb.putFloat(q[j]);
        bb.putInt(loop.size());for(float[] q:loop)for(int j=0;j<11;j++)bb.putFloat(q[j]);
        try(FileOutputStream out=new FileOutputStream(f)){out.write(bb.array(),0,bb.position());}
        return new int[]{g0.size()/3,loop.size()/3};
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
        PER=2*LOOP_L+2*PI*CR;PITCH=PER/CHAIN_N;
        // ---------------- body
        MODE=0;CAGE=.012f;RANGE=.006f;NAME="chainsaw";
        buildLow();int lowTris=0;for(Chart c:charts)lowTris+=c.tris.size();ArrayList<float[]> loop=chainLoop();
        System.out.println("low poly: body "+lowTris+" + chain loop "+loop.size()/3+" = "+(lowTris+loop.size()/3)+" triangles; "+charts.size()+" UV charts");
        if(mode.equals("low")){AW=AH=64;A_R=A_G=A_B=AO=NX=NY=NZ=HT=RO=ME=EM=new float[AW*AH];preview(prev,-.7f,.35f,"saw_low.png",v(.47f,0,.08f),1.05f,900,500,true);preview(prev,2.3f,.3f,"saw_low_b.png",v(.47f,0,.08f),1.05f,900,500,true);return;}
        pack();System.out.printf(Locale.ROOT,"atlas %dx%d at %.0f texels per metre (%.2f mm per texel)%n",AW,AH,texelsPerMetre,1000/texelsPerMetre);
        if(mode.equals("high")){long hp=meshHigh(0,.0004f,null);System.out.println("high poly (body): "+hp+" triangles");}
        System.out.println("baking body…");bake();dilate(10);writeMaps(assets);
        float pom=2*RANGE*texelsPerMetre/AW;
        preview(prev,-.7f,.35f,"saw_bake.png",v(.47f,0,.08f),1.05f,900,500,false);
        preview(prev,2.3f,.3f,"saw_bake_b.png",v(.47f,0,.08f),1.05f,900,500,false);
        preview(prev,-.9f,.25f,"saw_bake_engine.png",v(.2f,0,.15f),.42f,700,560,false);
        preview(prev,-.7f,.35f,"saw_wire.png",v(.47f,0,.08f),1.05f,900,500,true);
        ArrayList<Chart> body=new ArrayList<>(charts);
        // ---------------- chain strip
        charts.clear();MODE=1;CAGE=.006f;RANGE=.003f;AO_HS=new float[]{.0004f,.0008f,.0015f,.003f,.006f};NAME="chain";AW=512;AH=256;
        buildChainStrip();System.out.println("baking chain strip…");bake();dilate(6);writeMaps(assets);
        int[] tr=writeMesh(new File(assets,"chainsaw.bin"),body,pom,loop);System.out.println("mesh written: body "+tr[0]+" + chain "+tr[1]+" triangles");
        System.out.println("done in "+(System.currentTimeMillis()-t0)/1000+" s");
    }
}
