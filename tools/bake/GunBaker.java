import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/**
 * Offline high-to-low bake for the double-barrel shotgun (run on a desktop, results go into the app's assets).
 *
 * 1. HIGH POLY: a detailed sculpt defined as a signed distance field with sub-millimetre detail: hollow bores
 *    and chambers, a serrated sighting rib and brass bead, case-hardened action with fences, scroll engraving,
 *    screws, hinge pin, top lever, safety, two triggers and guard, chequered walnut wrist and forend, ribbed rubber
 *    butt pad. Every region has its own material (blued steel, case hardening, walnut, brass, rubber). It is meshed
 *    with surface nets at 0.4 mm for the high-poly mesh (exported as OBJ, millions of triangles).
 * 2. LOW POLY: two game meshes (action + stock, barrels + forend that hinge open), well under 1000 triangles,
 *    hand-built from lofts and boxes and UV-unwrapped into charts packed into one atlas.
 * 3. BAKE: for every texel of the low poly, a ray is cast along the low poly's normal through a cage to the high
 *    poly surface, which gives: tangent-space normal map, height map (surface offset, for parallax), ambient
 *    occlusion, and the high poly's material (albedo, roughness, metalness). Charts are dilated against seams.
 *
 * Output: gun.bin (low poly meshes), gun_a.png (albedo+AO), gun_b.png (normal+height), gun_c.png (roughness,
 * metalness), plus preview images.
 *
 *   javac GunBaker.java && java -Xmx4g GunBaker <assets dir> <preview dir> [mesh]
 */
public class GunBaker {
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

    // ================================================================== the high poly sculpt
    static final int M_BLUED=0,M_CASE=1,M_WALNUT=2,M_BRASS=3,M_RUBBER=4,M_BORE=5;
    static final float BX=.0118f;          // barrel centre offset left/right
    static final float HINGE_X=.128f,HINGE_Z=-.027f;

    /** Barrels + rib + bead + lumps + forend, in the closed position. mat[0] receives the material id. */
    static float barrels(float x,float y,float z,int[] mat){
        float d=1e9f;int m=M_BLUED;
        if(x<.07f||x>.83f||Math.abs(y)>.05f||z<-.05f||z>.03f)return Math.max(.0f,boundDist(x,y,z,.07f,.83f,-.05f,.05f,-.05f,.03f))+.002f;
        for(int s=-1;s<=1;s+=2){float cy=s*BX;
            float r=x<.17f?mix(.0128f,.0122f,(x-.095f)/.075f):mix(.0122f,.0103f,(x-.17f)/.635f);
            float tube=cylX(x,y,z,cy,0,r,.095f,.805f);
            float bore=cylX(x,y,z,cy,0,x<.165f?.0102f:.00925f,.085f,.83f);   // chamber then bore
            tube=Math.max(tube,-bore);
            if(tube<d){d=tube;m=bore>-.0006f&&bore<.0011f?M_BORE:M_BLUED;}}
        // top rib with fine transverse serrations and a brass bead
        float serr=((x*1100f)%1f)<.5f?.00014f:0;
        float rib=box(x,y,z,.465f,0,.0098f,.335f,.0042f,.0036f-serr,.0006f);
        if(rib<d){d=rib;m=M_BLUED;}
        float bead=sphere(x,y,z,.796f,0,.0142f,.0019f);if(bead<d){d=bead;m=M_BRASS;}
        float brib=box(x,y,z,.49f,0,-.0098f,.29f,.003f,.0022f,.0006f);if(brib<d){d=brib;m=M_BLUED;}
        // breech monoblock, lumps hooking over the hinge pin, extractor
        float mono=box(x,y,z,.115f,0,-.004f,.02f,.0245f,.0168f,.003f);
        for(int s=-1;s<=1;s+=2)mono=Math.max(mono,-cylX(x,y,z,s*BX,0,.0102f,.09f,.2f));
        float lump=box(x,y,z,.112f,0,-.024f,.017f,.0085f,.008f,.0015f);
        lump=Math.max(lump,-cylY(x,y,z,HINGE_X,HINGE_Z,.0042f,-.02f,.02f));
        mono=Math.min(mono,lump);
        float ext=box(x,y,z,.0965f,0,-.002f,.0015f,.0075f,.006f,.0005f);mono=Math.min(mono,ext);
        if(mono<d){d=mono;m=M_BLUED;}
        // walnut splinter forend with chequered panels and a steel iron and latch
        float t=clamp((x-.14f)/.22f,0,1);float hw=mix(.0195f,.0165f,t);
        float fe=box(x,y,z,.25f,0,-.0185f,.11f,hw,.0125f,.0075f);
        fe=Math.max(fe,-(cylX(x,y,z,BX,0,.0124f,.1f,.4f)));fe=Math.max(fe,-(cylX(x,y,z,-BX,0,.0124f,.1f,.4f)));
        if(x>.17f&&x<.33f&&z<-.011f&&z>-.029f){float c=chequer(x,z*1.6f+y*.3f);fe+=c*.00042f;}
        if(fe<d){d=fe;m=M_WALNUT;}
        float iron=box(x,y,z,.145f,0,-.028f,.012f,.0105f,.0045f,.002f);float latch=box(x,y,z,.352f,0,-.0305f,.007f,.0045f,.0018f,.001f);
        float ir=Math.min(iron,latch);if(ir<d){d=ir;m=M_BLUED;}
        mat[0]=m;return d;
    }
    /** Action (receiver), top tang and lever, safety, triggers and guard, stock and butt pad. */
    static float action(float x,float y,float z,int[] mat){
        float d=1e9f;int m=M_CASE;
        if(x<-.47f||x>.15f||Math.abs(y)>.05f||z<-.14f||z>.04f)return Math.max(0,boundDist(x,y,z,-.47f,.15f,-.05f,.05f,-.14f,.04f))+.002f;
        // receiver body with rounded fences around the breech
        float body=box(x,y,z,.025f,0,-.0085f,.07f,.0215f,.0215f,.0035f);
        for(int s=-1;s<=1;s+=2){float f=sphere(x,y,z,.084f,s*BX*1.05f,.0f,.0142f);f=Math.max(f,x-.095f);f=Math.max(f,-(z+.004f));body=smin(body,f,.004f);}
        // breech face: firing pin bushings
        for(int s=-1;s<=1;s+=2){float bush=cylX(x,y,z,s*BX,0,.0021f,.093f,.0953f);body=Math.min(body,bush);}
        // scroll engraving on the action sides
        if(Math.abs(y)>.017f&&x>-.035f&&x<.085f&&z>-.027f&&z<.009f){body+=engrave(x,z)*.00026f;}
        // action bar and hinge pin
        float bar=box(x,y,z,.11f,0,-.027f,.025f,.0215f,.0035f,.0015f);body=Math.min(body,bar);
        float pin=cylY(x,y,z,HINGE_X,HINGE_Z,.0036f,-.0222f,.0222f);
        // screws (pins with slots) on the sides
        float screws=1e9f;
        for(float[] sc:new float[][]{{.058f,-.012f},{-.002f,-.019f},{.03f,.002f}})for(int s=-1;s<=1;s+=2){
            float c=cylY(x,y,z,sc[0],sc[1],.0024f,s>0?.0205f:-.0221f,s>0?.0221f:-.0205f);
            float slot=box(x,y,z,sc[0],s*.0221f,sc[1],.0024f,.0004f,.00035f,0);c=Math.max(c,-slot);screws=Math.min(screws,c);}
        if(body<d){d=body;m=M_CASE;}
        if(pin<d){d=pin;m=M_BLUED;}if(screws<d){d=screws;m=M_BLUED;}
        // top tang, top lever (swung slightly right) with chequered thumb piece, safety slide
        float tang=box(x,y,z,-.06f,0,.0108f,.055f,.0075f,.0022f,.0015f);
        float a=.25f,lx=x+.03f,ly=y;float rx=lx*(float)Math.cos(a)-ly*(float)Math.sin(a),ry=lx*(float)Math.sin(a)+ly*(float)Math.cos(a);
        float lever=box(rx,ry,z,-.024f,0,.0145f,.024f,.0055f,.0018f,.0012f);
        if(rx<-.035f)lever+=chequer(rx*2,ry*2)*.0002f;
        float safety=box(x,y,z,-.083f,0,.0138f,.0055f,.0034f,.0016f,.0008f);
        if(Math.abs((x*1500)%1f)<.35f)safety+=.00012f;
        float top=Math.min(tang,Math.min(lever,safety));if(top<d){d=top;m=M_BLUED;}
        // trigger plate, guard bow, two triggers
        float plate=box(x,y,z,-.085f,0,-.031f,.04f,.007f,.0018f,.001f);
        float[][] g={{-.004f,0,-.03f},{-.012f,0,-.054f},{-.04f,0,-.061f},{-.066f,0,-.054f},{-.08f,0,-.04f},{-.105f,0,-.033f}};
        float guard=1e9f;for(int i=0;i+1<g.length;i++)guard=Math.min(guard,capsule(x,y*1.6f,z,g[i],g[i+1],.0028f));
        float trig=1e9f;   // two curved trigger blades (front edge bowed forward), filling the low poly's trigger boxes
        for(float tx:new float[]{-.025f,-.043f}){float bow=(z+.04f)*(z+.04f)*30f;trig=Math.min(trig,box(x+bow,y,z,tx,0,-.04f,.0045f,.0014f,.0098f,.0012f));}
        float tg=Math.min(plate,Math.min(guard,trig));if(tg<d){d=tg;m=M_BLUED;}
        // walnut stock: straight-hand wrist flaring to the butt, superelliptic sections, chequered wrist
        float st=stock(x,y,z);
        if(x<-.065f&&x>-.155f){float c=chequer(x*1.0f,(z+.03f)*1.4f+Math.abs(y)*.6f);st+=c*.0004f;}
        if(st<d){d=st;m=M_WALNUT;}
        // rubber butt pad with horizontal ribs
        float pad=Math.max(stockSection(x,y,z,-.44f),Math.max(x-(-.44f),-.456f-x));
        if(pad<1e8f){pad+=((Math.abs(z)*900)%1f)<.3f?.0003f:0;if(pad<d){d=pad;m=M_RUBBER;}}
        mat[0]=m;return d;
    }
    static float boundDist(float x,float y,float z,float x0,float x1,float y0,float y1,float z0,float z1){
        float dx=Math.max(x0-x,x-x1),dy=Math.max(y0-y,y-y1),dz=Math.max(z0-z,z-z1);
        return (float)Math.sqrt(Math.max(dx,0)*Math.max(dx,0)+Math.max(dy,0)*Math.max(dy,0)+Math.max(dz,0)*Math.max(dz,0));
    }
    /** Chequering: two families of V-grooves at ±32 degrees (1.6 mm pitch); 0 on the diamonds' tops, 1 in a groove. */
    static float chequer(float u,float v){
        float a=(u*.848f+v*.53f)/.0016f,b=(u*.848f-v*.53f)/.0016f;
        float ga=Math.abs(a-(float)Math.floor(a)-.5f)*2,gb=Math.abs(b-(float)Math.floor(b)-.5f)*2;   // 1 at groove centre
        return Math.max(smooth(.62f,.95f,ga),smooth(.62f,.95f,gb));
    }
    /** Scroll engraving: curling lines from a warped spiral field; 1 inside a cut. */
    static float engrave(float x,float z){
        float u=x*180,w=z*180;float wx=u+fbm(u*.35f,w*.35f,3,3)*3,wz=w+fbm(u*.35f+7,w*.35f,5,3)*3;
        float cx=(float)Math.floor(wx/3)*3+1.5f,cz=(float)Math.floor(wz/3)*3+1.5f;
        float dx=wx-cx,dz=wz-cz;float r=(float)Math.hypot(dx,dz),an=(float)Math.atan2(dz,dx);
        float spiral=(float)Math.sin(r*4.2f-an*1.0f+hash((int)cx,(int)cz,1)*6);
        float line=1-smooth(.12f,.32f,Math.abs(spiral));
        float border=Math.min(Math.min(Math.abs(z+.026f),Math.abs(z-.008f)),Math.min(Math.abs(x+.034f),Math.abs(x-.084f)));
        float frame=1-smooth(.0006f,.0011f,Math.abs(border-.0018f));
        return Math.max(line*(r<1.45f?1:0),frame);
    }
    // stock profile (x from the action back to the butt)
    static float stockTop(float x){return x>-.17f?mix(.0112f,.0045f,(-.045f-x)/.125f):mix(.0045f,.001f,(-.17f-x)/.285f);}
    static float stockBot(float x){return x>-.17f?mix(-.03f,-.036f,(-.045f-x)/.125f):mix(-.036f,-.124f,smooth(0,1,(-.17f-x)/.285f)*.4f+(-.17f-x)/.285f*.6f);}
    static float stockHalfW(float x){if(x>-.12f)return mix(.0213f,.0155f,(-.045f-x)/.075f);if(x>-.2f)return mix(.0155f,.0175f,(-.12f-x)/.08f);return mix(.0175f,.0208f,(-.2f-x)/.255f);}
    static float stockSection(float x,float y,float z,float xc){
        float top=stockTop(xc),bot=stockBot(xc),cz=(top+bot)*.5f,hz=(top-bot)*.5f,hy=stockHalfW(xc);
        float ay=Math.abs(y)/hy,az=Math.abs(z-cz)/hz;float se=(float)Math.pow(Math.pow(ay,3)+Math.pow(az,3),1/3.0);
        return (se-1)*Math.min(hy,hz)*.85f;
    }
    static float stock(float x,float y,float z){
        float xc=clamp(x,-.44f,-.045f);float d=stockSection(x,y,z,xc);
        return Math.max(d,Math.max(x-(-.04f),-.44f-x));
    }
    static float sdf(int group,float x,float y,float z,int[] mat){return group==0?action(x,y,z,mat):barrels(x,y,z,mat);}

    // ================================================================== materials of the high poly
    /** Albedo (linear RGB), roughness, metalness at a point on the high poly surface; lap = curvature (edge wear). */
    static float[] material(int m,float x,float y,float z,float lap,float cav){
        float r,g,b,rough,metal;
        switch(m){
            case M_BLUED:{float n=fbm(x*400,y*400,z*400,3);float wear=smooth(.55f,.9f,lap*.0009f+fbm(x*90,y*90,z*90,4)*.5f)*(1-cav);
                r=mix(.035f,.38f,wear);g=mix(.04f,.38f,wear);b=mix(.055f,.4f,wear);r+=n*.01f;g+=n*.01f;b+=n*.015f;rough=mix(.28f,.2f,wear)+n*.08f;metal=1;break;}
            case M_CASE:{   // case hardening: mottled blues, purples, straw and grey
                float n1=fbm(x*60+3,y*60,z*60,5),n2=fbm(x*90,y*90+5,z*90,4);
                float[][] pal={{.55f,.55f,.56f},{.12f,.18f,.42f},{.32f,.16f,.38f},{.62f,.44f,.18f},{.2f,.3f,.5f},{.45f,.45f,.48f}};
                float t=clamp(n1*1.4f-.2f,0,.999f)*(pal.length-1);int i=(int)t;float f=t-i;
                r=mix(pal[i][0],pal[i+1][0],f);g=mix(pal[i][1],pal[i+1][1],f);b=mix(pal[i][2],pal[i+1][2],f);
                float gr=(r+g+b)/3;r=mix(gr,r,.55f)*.8f;g=mix(gr,g,.55f)*.8f;b=mix(gr,b,.55f)*.8f;   // case colours are subtle over grey steel
                float k=.8f+n2*.4f;r*=k;g*=k;b*=k;float wear=smooth(.6f,.9f,lap*.0009f);r=mix(r,.6f,wear);g=mix(g,.6f,wear);b=mix(b,.62f,wear);
                rough=.32f+n2*.1f;metal=1;break;}
            case M_WALNUT:{   // walnut: rings around the stock's long axis, dark figure, oil finish
                float rr=(float)Math.hypot(y*1.3f,z-.02f)*260+fbm(x*14,y*30,z*30,4)*6;float ring=.5f+.5f*(float)Math.sin(rr*6.283f);
                float fig=fbm(x*30,y*80,z*80,5);float dark=smooth(.35f,.75f,fig);
                r=mix(.16f,.07f,dark)*(.75f+ring*.35f);g=mix(.075f,.03f,dark)*(.75f+ring*.3f);b=mix(.03f,.012f,dark)*(.8f+ring*.2f);
                rough=.42f+fig*.12f+cav*.3f;metal=0;break;}
            case M_BRASS:{float n=fbm(x*500,y*500,z*500,3);r=.85f-n*.1f;g=.62f-n*.1f;b=.25f;rough=.22f+n*.1f;metal=1;break;}
            case M_RUBBER:{float n=fbm(x*300,y*300,z*300,3);r=g=b=.025f+n*.012f;rough=.88f;metal=0;break;}
            default:{r=.02f;g=.02f;b=.022f;rough=.5f;metal=1;}
        }
        return new float[]{r,g,b,rough,metal};
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

    // the low poly gun ------------------------------------------------------------------------------------------------
    static float[][] oct(float cx,float cy,float cz,float hy,float hz,float ch){   // octagonal section (chamfer ch) in the yz plane at x=cx
        return new float[][]{v(cx,cy-hy+ch,cz-hz),v(cx,cy+hy-ch,cz-hz),v(cx,cy+hy,cz-hz+ch),v(cx,cy+hy,cz+hz-ch),v(cx,cy+hy-ch,cz+hz),v(cx,cy-hy+ch,cz+hz),v(cx,cy-hy,cz+hz-ch),v(cx,cy-hy,cz-hz+ch)};
    }
    static float[][] circ(float cx,float cy,float cz,float r,int k){float[][] R=new float[k][];for(int j=0;j<k;j++){double a=j*2*Math.PI/k;R[j]=v(cx,cy+(float)Math.cos(a)*r,cz+(float)Math.sin(a)*r);}return R;}
    static float[][] stockRing(float x,int k){
        float top=stockTop(x),bot=stockBot(x),cz=(top+bot)*.5f,hz=(top-bot)*.5f,hy=stockHalfW(x);float[][] R=new float[k][];
        for(int j=0;j<k;j++){double a=j*2*Math.PI/k+Math.PI/k;float c=(float)Math.cos(a),s=(float)Math.sin(a);float e=(float)Math.pow(Math.pow(Math.abs(c),3)+Math.pow(Math.abs(s),3),-1/3.0);
            R[j]=v(x,c*e*hy,cz+s*e*hz);}
        return R;
    }
    static void buildLow(){
        // ---------------- group 0: action + stock
        loft(0,new float[][][]{oct(-.046f,0,-.0085f,.0215f,.0215f,.005f),oct(.0f,0,-.0085f,.0216f,.0215f,.005f),oct(.07f,0,-.0085f,.0222f,.0222f,.004f),oct(.096f,0,-.0085f,.0222f,.0222f,.004f)},false,true);
        box(0,.095f,-.0216f,-.031f,.137f,.0216f,-.022f);                      // action bar + hinge
        box(0,-.116f,-.0092f,.0086f,-.004f,.0092f,.0175f);                    // tang, top lever, safety
        box(0,-.126f,-.0075f,-.0335f,-.044f,.0075f,-.029f);                   // trigger plate
        float[][] g={{-.004f,-.03f},{-.012f,-.054f},{-.04f,-.061f},{-.066f,-.054f},{-.08f,-.04f},{-.105f,-.033f}};
        loftPath(0,g,.0025f,.0032f);
        box(0,-.031f,-.0016f,-.05f,-.019f,.0016f,-.03f);box(0,-.049f,-.0016f,-.049f,-.037f,.0016f,-.03f);   // triggers
        int K=8;float[] xs={-.04f,-.07f,-.105f,-.14f,-.18f,-.24f,-.3f,-.36f,-.41f,-.44f};
        float[][][] st=new float[xs.length][][];for(int i=0;i<xs.length;i++)st[i]=stockRing(xs[i],K);
        loft(0,st,false,false);
        float[][] padA=stockRing(-.44f,K),padB=stockRing(-.4565f,K);for(float[] p:padB)p[0]=-.4565f;
        loft(0,new float[][][]{padA,padB},false,true);                      // butt pad
        // ---------------- group 1: barrels + forend (closed position)
        int B=16;
        for(int s=-1;s<=1;s+=2){float cy=s*BX;
            loft(1,new float[][][]{circ(.095f,cy,0,.0128f,B),circ(.17f,cy,0,.0122f,B),circ(.805f,cy,0,.0103f,B)},false,false);
            // muzzle: crown (annulus) and the first few cm of bore
            Chart c=chart(1);float[][] o=circ(.805f,cy,0,.0103f,B),in=circ(.805f,cy,0,.00925f,B);int[] io=new int[B],ii=new int[B];
            for(int j=0;j<B;j++){io[j]=vert(c,o[j],v(1,0,0),v(0,1,0),o[j][1],o[j][2]);ii[j]=vert(c,in[j],v(1,0,0),v(0,1,0),in[j][1],in[j][2]);}
            for(int j=0;j<B;j++){int j2=(j+1)%B;c.tris.add(new int[]{io[j],io[j2],ii[j2]});c.tris.add(new int[]{io[j],ii[j2],ii[j]});}orient(c);
            loft(1,new float[][][]{circ(.805f,cy,0,.00925f,B),circ(.76f,cy,0,.00925f,B)},false,true);
        }
        box(1,.095f,-.0245f,-.0208f,.135f,.0245f,.0128f);                     // breech monoblock (its front = breech face)
        box(1,.095f,-.0085f,-.032f,.129f,.0085f,-.016f);                       // lumps
        box(1,.13f,-.0042f,.006f,.8f,.0042f,.0134f);                           // top rib
        box(1,.2f,-.003f,-.012f,.78f,.003f,-.0076f);                           // bottom rib
        box(1,.793f,-.0019f,.0125f,.799f,.0019f,.0162f);                       // bead
        loft(1,new float[][][]{oct(.138f,0,-.0185f,.0195f,.0125f,.007f),oct(.18f,0,-.0185f,.0192f,.0125f,.007f),oct(.3f,0,-.0185f,.0172f,.0125f,.007f),oct(.362f,0,-.0185f,.0165f,.0125f,.007f)},true,true);
    }
    /** Square tube along a polyline in the xz plane (trigger guard). */
    static void loftPath(int group,float[][] pts,float hy,float hr){
        int n=pts.length;float[][][] R=new float[n][][];
        for(int i=0;i<n;i++){float[] a=pts[Math.max(0,i-1)],b=pts[Math.min(n-1,i+1)];float dx=b[0]-a[0],dz=b[1]-a[1],l=(float)Math.hypot(dx,dz);float nx=-dz/l,nz=dx/l;
            float x=pts[i][0],z=pts[i][1];R[i]=new float[][]{v(x-nx*hr,-hy,z-nz*hr),v(x-nx*hr,hy,z-nz*hr),v(x+nx*hr,hy,z+nz*hr),v(x+nx*hr,-hy,z+nz*hr)};}
        loft(group,R,true,true);
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
    static final float CAGE=.0045f,RANGE=.003f;   // ray search distance out/in, height map range (± metres)
    static float[] A_R,A_G,A_B,AO,NX,NY,NZ,HT,RO,ME;static boolean[] COV;
    static void bake()throws Exception{
        int N=AW*AH;A_R=new float[N];A_G=new float[N];A_B=new float[N];AO=new float[N];NX=new float[N];NY=new float[N];NZ=new float[N];HT=new float[N];RO=new float[N];ME=new float[N];COV=new boolean[N];
        ExecutorService ex=Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());ArrayList<Future<?>> fs=new ArrayList<>();
        for(Chart c:charts)fs.add(ex.submit(()->{bakeChart(c);return null;}));
        int done=0;for(Future<?> f:fs){f.get();done++;if(done%10==0)System.out.println("  charts "+done+"/"+fs.size());}
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
        if(Float.isNaN(t)){   // looking into an opening (the chambers): dark bore
            A_R[i]=A_G[i]=A_B[i]=.015f;RO[i]=.5f;ME[i]=1;AO[i]=.12f;NX[i]=NY[i]=0;NZ[i]=1;HT[i]=0;COV[i]=true;return;}
        float[] H=add(P,N,t);
        float e=.00012f;float d0;
        float nx=sdf(g,H[0]+e,H[1],H[2],mat)-sdf(g,H[0]-e,H[1],H[2],mat),ny=sdf(g,H[0],H[1]+e,H[2],mat)-sdf(g,H[0],H[1]-e,H[2],mat),nz=sdf(g,H[0],H[1],H[2]+e,mat)-sdf(g,H[0],H[1],H[2]-e,mat);
        float[] Nh=norm(v(nx,ny,nz));if(Float.isNaN(Nh[0]))Nh=N;
        d0=sdf(g,H[0],H[1],H[2],mat);int m=mat[0];
        // curvature (for edge wear) and SDF ambient occlusion (several scales, plus the high normal)
        float le=.0006f;float lap=(sdf(g,H[0]+le,H[1],H[2],mat)+sdf(g,H[0]-le,H[1],H[2],mat)+sdf(g,H[0],H[1]+le,H[2],mat)+sdf(g,H[0],H[1]-le,H[2],mat)+sdf(g,H[0],H[1],H[2]+le,mat)+sdf(g,H[0],H[1],H[2]-le,mat)-6*d0)/(le*le);
        float occ=0,wsum=0;float[] hs={.0006f,.0012f,.0025f,.005f,.01f,.02f};
        for(int k=0;k<hs.length;k++){float h=hs[k];float s=sdf(g,H[0]+Nh[0]*h,H[1]+Nh[1]*h,H[2]+Nh[2]*h,mat);float w=1f/(k+1);occ+=w*clamp((h-s)/h,0,1);wsum+=w;}
        float ao=clamp(1-occ/wsum*1.3f,0,1);float cav=clamp(-lap*.002f,0,1);
        float[] mt=material(m,H[0],H[1],H[2],Math.max(0,lap),1-ao);
        A_R[i]=mt[0];A_G[i]=mt[1];A_B[i]=mt[2];RO[i]=mt[3];ME[i]=mt[4];AO[i]=ao;
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
        float[][] ch={A_R,A_G,A_B,AO,NX,NY,NZ,HT,RO,ME};
        for(int p=0;p<passes;p++){boolean[] nc=COV.clone();
            for(int y=0;y<AH;y++)for(int x=0;x<AW;x++){int i=y*AW+x;if(COV[i])continue;int n=0;float[] s=new float[ch.length];
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int xx=x+dx,yy=y+dy;if(xx<0||yy<0||xx>=AW||yy>=AH)continue;int j=yy*AW+xx;if(!COV[j])continue;n++;for(int k=0;k<ch.length;k++)s[k]+=ch[k][j];}
                if(n>0){for(int k=0;k<ch.length;k++)ch[k][i]=s[k]/n;nc[i]=true;}}
            COV=nc;}
        for(int i=0;i<AW*AH;i++)if(!COV[i]){A_R[i]=A_G[i]=A_B[i]=.05f;AO[i]=1;NX[i]=NY[i]=0;NZ[i]=1;HT[i]=.5f;RO[i]=.5f;ME[i]=0;}
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
            c.setRGB(x,y,(b8(RO[i])<<16)|(b8(ME[i])<<8));}
        ImageIO.write(a,"png",new File(dir,"gun_a.png"));ImageIO.write(b,"png",new File(dir,"gun_b.png"));ImageIO.write(c,"png",new File(dir,"gun_c.png"));
    }
    /** gun.bin: magic, pom scale, groups; per group: vertex count then 11 floats per vertex (pos, normal, tangent, uv). */
    static int[] writeMesh(File f)throws Exception{
        int[] tris=new int[2];ByteArrayOutputStream bo=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(bo);
        ArrayList<float[]>[] g=new ArrayList[]{new ArrayList<>(),new ArrayList<>()};
        for(Chart c:charts)for(int[] t:c.tris)for(int k:t){g[c.group].add(c.verts.get(k));}
        ByteBuffer bb=ByteBuffer.allocate(16+(g[0].size()+g[1].size())*44+8).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(0x4e554753);bb.putFloat(2*RANGE*texelsPerMetre/AW);bb.putInt(2);
        for(int k=0;k<2;k++){bb.putInt(g[k].size());tris[k]=g[k].size()/3;for(float[] q:g[k])for(int j=0;j<11;j++)bb.putFloat(q[j]);}
        try(FileOutputStream out=new FileOutputStream(f)){out.write(bb.array(),0,bb.position());}
        return tris;
    }

    // ================================================================== high poly mesh (surface nets, narrow band)
    static long meshHigh(int g,float h,File obj)throws Exception{
        float x0,x1,y0,y1,z0,z1;if(g==0){x0=-.462f;x1=.142f;y0=-.03f;y1=.03f;z0=-.132f;z1=.026f;}else{x0=.085f;x1=.82f;y0=-.03f;y1=.03f;z0=-.042f;z1=.022f;}
        int nx=(int)((x1-x0)/h)+2,ny=(int)((y1-y0)/h)+2,nz=(int)((z1-z0)/h)+2;int B=8;int[] mat=new int[1];
        HashMap<Long,float[]> blocks=new HashMap<>();
        // coarse pass: only blocks near the surface get fine samples
        for(int bz=0;bz*B<nz;bz++)for(int by=0;by*B<ny;by++)for(int bx=0;bx*B<nx;bx++){
            float cx=x0+(bx*B+B/2f)*h,cy=y0+(by*B+B/2f)*h,cz=z0+(bz*B+B/2f)*h;float d=sdf(g,cx,cy,cz,mat);
            if(Math.abs(d)>B*h*.9f+.0008f)continue;
            float[] s=new float[(B+1)*(B+1)*(B+1)];
            for(int k=0;k<=B;k++)for(int j=0;j<=B;j++)for(int i=0;i<=B;i++)s[(k*(B+1)+j)*(B+1)+i]=sdf(g,x0+(bx*B+i)*h,y0+(by*B+j)*h,z0+(bz*B+k)*h,mat);
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
        System.out.println("  high poly group "+g+": "+verts.size()+" vertices, "+faces+" triangles");
        return faces;
    }
    static long key(int x,int y,int z){return (x&0xffffL)|((y&0xffffL)<<16)|((z&0xffffL)<<32);}
    static float sample(HashMap<Long,float[]> blocks,int gx,int gy,int gz,int B){
        int bx=gx/B,by=gy/B,bz=gz/B;int i=gx-bx*B,j=gy-by*B,k=gz-bz*B;float[] s=blocks.get(key(bx,by,bz));
        if(s==null){if(i==0&&bx>0){s=blocks.get(key(bx-1,by,bz));i=B;bx--;}}
        if(s==null)return Float.NaN;return s[(k*(B+1)+j)*(B+1)+i];
    }

    // ================================================================== previews
    /** Sphere-traced render of the high poly (left) and the baked low poly with its maps (right). */
    static void preview(File dir,float yaw,float pitch,String name)throws Exception{preview(dir,yaw,pitch,name,v(.17f,0,-.03f),1.32f);}
    static void preview(File dir,float yaw,float pitch,String name,float[] centre,float span)throws Exception{
        int W=1200,H=420;BufferedImage im=new BufferedImage(W*2,H,BufferedImage.TYPE_INT_RGB);int[] mat=new int[1];
        float cy=(float)Math.cos(yaw),sy=(float)Math.sin(yaw),cp=(float)Math.cos(pitch),sp=(float)Math.sin(pitch);
        float[] fwd=v(-cy*cp,-sy*cp,-sp),right=norm(cross(fwd,v(0,0,1))),up=cross(right,fwd);float dist=.9f,scale=span/W;
        float[] L=norm(v(.3f,.5f,.8f)),L2=norm(v(-.6f,-.3f,.2f));
        // high poly
        for(int py=0;py<H;py++)for(int px=0;px<W;px++){float sx=(px-W/2f)*scale,syy=(H/2f-py)*scale;
            float[] o=add(add(add(centre,fwd,-dist),right,sx),up,syy);float t=0;int g=-1;float[] p=null;
            for(int k=0;k<300;k++){p=add(o,fwd,t);float d0=action(p[0],p[1],p[2],mat),d1=barrels(p[0],p[1],p[2],mat);float d=Math.min(d0,d1);if(d<.00005f){g=d0<d1?0:1;break;}t+=d*.8f;if(t>2)break;}
            int col=0x1c1a1e;
            if(g>=0){float e=.0001f;float[] n=norm(v(sdf(g,p[0]+e,p[1],p[2],mat)-sdf(g,p[0]-e,p[1],p[2],mat),sdf(g,p[0],p[1]+e,p[2],mat)-sdf(g,p[0],p[1]-e,p[2],mat),sdf(g,p[0],p[1],p[2]+e,mat)-sdf(g,p[0],p[1],p[2]-e,mat)));
                sdf(g,p[0],p[1],p[2],mat);float[] mt=material(mat[0],p[0],p[1],p[2],0,0);col=shade(mt,n,fwd,L,L2,1);}
            im.setRGB(px,py,col);}
        // low poly with the baked maps (software raster + normal mapping)
        float[] zb=new float[W*H];Arrays.fill(zb,1e9f);for(int i=0;i<W*H;i++)im.setRGB(W+i%W,i/W,0x1c1a1e);
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
                im.setRGB(W+x,y,shade(new float[]{A_R[ti],A_G[ti],A_B[ti],RO[ti],ME[ti]},n,fwd,L,L2,AO[ti]));}}
        ImageIO.write(im,"png",new File(dir,name));
    }
    static int shade(float[] mt,float[] n,float[] fwd,float[] L,float[] L2,float ao){
        float[] V=mul(fwd,-1);if(dot(n,V)<0)n=mul(n,-1);float r=0,g=0,b=0;
        for(int li=0;li<2;li++){float[] l=li==0?L:L2;float k=li==0?1.6f:.6f;float nl=Math.max(0,dot(n,l));float[] h=norm(add(l,V,1));float nh=Math.max(0,dot(n,h));
            float sp=(float)Math.pow(nh,2+(1-mt[3])*120)*(1-mt[3])*1.5f;float[] f0=mt[4]>.5f?v(mt[0],mt[1],mt[2]):v(.04f,.04f,.04f);
            float kd=1-mt[4];r+=(mt[0]*kd*nl+f0[0]*sp*nl)*k;g+=(mt[1]*kd*nl+f0[1]*sp*nl)*k;b+=(mt[2]*kd*nl+f0[2]*sp*nl)*k;}
        float amb=.12f*ao;r+=mt[0]*amb+(mt[4]>.5f?mt[0]*.25f*ao:0);g+=mt[1]*amb+(mt[4]>.5f?mt[1]*.25f*ao:0);b+=mt[2]*amb+(mt[4]>.5f?mt[2]*.25f*ao:0);
        return (b8(srgb(r*ao))<<16)|(b8(srgb(g*ao))<<8)|b8(srgb(b*ao));
    }
    /** The UV layout with chart outlines over the albedo (for the README). */
    static void uvPreview(File dir)throws Exception{
        BufferedImage im=new BufferedImage(AW/2,AH/2,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<AH/2;y++)for(int x=0;x<AW/2;x++){int i=(y*2)*AW+x*2;im.setRGB(x,y,(b8(srgb(A_R[i]*AO[i]))<<16)|(b8(srgb(A_G[i]*AO[i]))<<8)|b8(srgb(A_B[i]*AO[i])));}
        java.awt.Graphics2D g=im.createGraphics();g.setColor(new java.awt.Color(0,255,160,120));
        for(Chart c:charts)for(int[] t:c.tris)for(int k=0;k<3;k++){float[] a=c.verts.get(t[k]),b=c.verts.get(t[(k+1)%3]);g.drawLine((int)(a[9]*AW/2),(int)(a[10]*AH/2),(int)(b[9]*AW/2),(int)(b[10]*AH/2));}
        ImageIO.write(im,"png",new File(dir,"gun_uv.png"));
    }

    public static void main(String[] a)throws Exception{
        File assets=new File(a[0]),prev=new File(a[1]);assets.mkdirs();prev.mkdirs();boolean mesh=a.length>2;
        long t0=System.currentTimeMillis();
        buildLow();int lowTris=0;for(Chart c:charts)lowTris+=c.tris.size();
        System.out.println("low poly: "+lowTris+" triangles in "+charts.size()+" UV charts");
        pack();System.out.printf(Locale.ROOT,"atlas %dx%d at %.0f texels per metre (%.2f mm per texel)%n",AW,AH,texelsPerMetre,1000/texelsPerMetre);
        if(mesh){long hp=meshHigh(0,.0004f,new File(prev,"gun_high_action.obj"))+meshHigh(1,.0004f,new File(prev,"gun_high_barrels.obj"));System.out.println("high poly total: "+hp+" triangles");}
        System.out.println("baking…");bake();dilate(10);
        writeMaps(assets);int[] tr=writeMesh(new File(assets,"gun.bin"));System.out.println("mesh written: action "+tr[0]+" + barrels "+tr[1]+" triangles");
        uvPreview(prev);preview(prev,-.55f,.22f,"gun_bake.png");preview(prev,1.35f,.12f,"gun_bake_action.png",v(-.02f,0,-.02f),.26f);
        preview(prev,-.4f,.35f,"gun_bake_muzzle.png",v(.78f,0,0),.09f);preview(prev,1.2f,-.15f,"gun_bake_stock.png",v(-.1f,0,-.03f),.22f);
        System.out.println("done in "+(System.currentTimeMillis()-t0)/1000+" s");
    }
}
