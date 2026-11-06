import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/**
 * Offline high-to-low bake for the warden, an original armoured heroine (run on a desktop; results go into assets).
 *
 * 1. HIGH POLY: a part-based signed distance field with millimetre detail: a charcoal undersuit; bone-white ceramic
 *    plates (shaped breastplate, segmented abdomen, back plate with spine ridge, tassets, layered pauldrons, rerebraces,
 *    ribbed vambraces, cuisses, greaves, sabatons) with panel grooves and rivets; dark steel trim (gorget, elbow and
 *    knee cops, plate edges); a crested helmet with cheek guards and a glowing amber visor slit; a long braid; leather
 *    belt, pouches and gauntlets; a crimson sash; and a glaive with a wrapped grip and an obsidian blade split by
 *    molten cracks. Meshed with surface nets (millions of triangles) only to report the count / export an OBJ.
 * 2. LOW POLY: about 2,500 triangles. Each part is lofted through rings fitted to the sculpt by casting rays at it,
 *    then UV-unwrapped into charts packed into one atlas.
 * 3. BAKE: for every texel a ray goes from a cage along the low poly normal to the part's high poly surface:
 *    tangent-space normal map, height, ambient occlusion (from the whole figure), albedo, roughness, metalness and
 *    emission (visor and blade cracks). Charts are dilated against seams.
 *
 * Output: hero.bin (one mesh), hero_a.png (albedo+AO), hero_b.png (normal+height), hero_c.png (roughness, metalness,
 * emission), plus previews.
 *
 *   javac HeroBaker.java && java -Xmx6g HeroBaker <assets dir> <preview dir> [obj]
 */
public class HeroBaker {
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

    // ================================================================== the high poly sculpt: an armoured warden
    // x forward (she faces +x), y to her left, z up, metres; feet on z = 0, about 1.8 m tall (2.25 m with the glaive).
    static final int M_SUIT=0,M_ARMOR=1,M_TRIM=2,M_CLOTH=3,M_LEATHER=4,M_VISOR=5,M_HAIR=6,M_OBSIDIAN=7,M_RUBBER=8;
    static float ell(float x,float y,float z,float cx,float cy,float cz,float rx,float ry,float rz){
        float px=(x-cx)/rx,py=(y-cy)/ry,pz=(z-cz)/rz;float k0=(float)Math.sqrt(px*px+py*py+pz*pz);
        float k1=(float)Math.sqrt(px*px/(rx*rx)+py*py/(ry*ry)+pz*pz/(rz*rz));return k1<1e-9f?-Math.min(rx,Math.min(ry,rz)):k0*(k0-1)/k1;
    }
    /** Rounded cone between spheres at a (radius ra) and b (radius rb). */
    static float cone(float x,float y,float z,float[] a,float[] b,float ra,float rb){
        float bx=b[0]-a[0],by=b[1]-a[1],bz=b[2]-a[2],l2=bx*bx+by*by+bz*bz,qx=x-a[0],qy=y-a[1],qz=z-a[2];
        float h=clamp((qx*bx+qy*by+qz*bz)/l2,0,1);float dx=qx-bx*h,dy=qy-by*h,dz=qz-bz*h;
        return (float)Math.sqrt(dx*dx+dy*dy+dz*dz)-mix(ra,rb,h);
    }
    /** A carved V-groove: pushes the surface in by `depth` where `dist` (distance to the line) is under `w`. */
    static float groove(float d,float dist,float w,float depth){return d+depth*(1-smooth(0,w,Math.abs(dist)));}
    static float rivets(float d,float u,float v,float spacing,float r,float h){   // little domes on a (u,v) grid line
        float cu=Math.round(u/spacing)*spacing;float dd=(float)Math.hypot(u-cu,v);return dd<r?d-h*(float)Math.sqrt(1-(dd/r)*(dd/r)):d;
    }
    // skeleton landmarks (s = +1 left, -1 right)
    static float[] hip(int s){return v(0,s*.095f,.95f);}
    static float[] knee(int s){return v(.025f,s*.12f,.52f);}
    static float[] ankle(int s){return v(-.005f,s*.14f,.088f);}
    static float[] toe(int s){return v(.2f,s*.155f,.035f);}
    static float[] shoulder(int s){return v(0,s*.205f,1.45f);}
    static float[] elbow(int s){return s<0?v(-.03f,-.27f,1.19f):v(-.04f,.265f,1.18f);}
    static float[] wrist(int s){return s<0?v(.06f,-.3f,.985f):v(-.01f,.29f,.95f);}
    static final float GX=.085f,GY=-.315f;   // glaive shaft axis

    static final int P_TORSO=0,P_HEAD=1,P_BRAID=2,P_GLAIVE_SHAFT=3,P_GLAIVE_HEAD=4,P_FIRST_SIDE=5;
    // per side: pauldron, upper arm, forearm, hand, thigh, shin, foot  (left = 5..11, right = 12..18)
    static final int S_PAULDRON=0,S_UPPER=1,S_FORE=2,S_HAND=3,S_THIGH=4,S_SHIN=5,S_FOOT=6,PER_SIDE=7,PARTS=P_FIRST_SIDE+2*PER_SIDE;
    static int sidePart(int s,int k){return P_FIRST_SIDE+(s>0?0:PER_SIDE)+k;}

    static float sdfAll(float x,float y,float z,int[] mat){float d=1e9f;int m=0;for(int p=0;p<PARTS;p++){float e=part(p,x,y,z,mat);if(e<d){d=e;m=mat[0];}}mat[0]=m;return d;}
    static float sdf(int g,float x,float y,float z,int[] mat){return part(g,x,y,z,mat);}
    static float part(int p,float x,float y,float z,int[] mat){
        switch(p){
            case P_TORSO:return torso(x,y,z,mat);
            case P_HEAD:return head(x,y,z,mat);
            case P_BRAID:return braid(x,y,z,mat);
            case P_GLAIVE_SHAFT:return shaft(x,y,z,mat);
            case P_GLAIVE_HEAD:return blade(x,y,z,mat);
        }
        int q=p-P_FIRST_SIDE;int s=q<PER_SIDE?1:-1;int k=q%PER_SIDE;
        switch(k){case S_PAULDRON:return pauldron(s,x,y,z,mat);case S_UPPER:return upperArm(s,x,y,z,mat);case S_FORE:return forearm(s,x,y,z,mat);
            case S_HAND:return hand(s,x,y,z,mat);case S_THIGH:return thigh(s,x,y,z,mat);case S_SHIN:return shin(s,x,y,z,mat);default:return foot(s,x,y,z,mat);}
    }
    static float bound(float x,float y,float z,float x0,float x1,float y0,float y1,float z0,float z1){
        float dx=Math.max(x0-x,x-x1),dy=Math.max(y0-y,y-y1),dz=Math.max(z0-z,z-z1);
        return (float)Math.sqrt(Math.max(dx,0)*Math.max(dx,0)+Math.max(dy,0)*Math.max(dy,0)+Math.max(dz,0)*Math.max(dz,0));
    }

    static float torso(float x,float y,float z,int[] mat){
        float bd=bound(x,y,z,-.2f,.2f,-.26f,.26f,.78f,1.58f);if(bd>.02f){mat[0]=M_ARMOR;return bd;}
        // the body underneath (charcoal suit)
        float body=ell(x,y,z,0,0,1.0f,.115f,.165f,.115f);
        body=smin(body,ell(x,y,z,0,0,1.13f,.092f,.118f,.09f),.05f);
        body=smin(body,ell(x,y,z,0,0,1.3f,.115f,.16f,.135f),.06f);
        body=smin(body,ell(x,y,z,-.01f,0,1.42f,.1f,.195f,.075f),.05f);
        float d=body;int m=M_SUIT;
        // breastplate: the chest shell with a shaped bust, panel lines, a centre ridge
        float chest=smin(ell(x,y,z,.0f,0,1.335f,.112f,.168f,.15f),Math.min(ell(x,y,z,.058f,.062f,1.29f,.062f,.058f,.05f),ell(x,y,z,.058f,-.062f,1.29f,.062f,.058f,.05f)),.04f)-.01f;
        chest=Math.max(chest,1.215f-z);chest=Math.max(chest,z-1.46f);
        chest=groove(chest,y,.004f,.003f*(x>0?1:0));chest=groove(chest,z-1.24f,.003f,.0025f);chest=groove(chest,z-1.43f,.003f,.0025f);
        chest=groove(chest,(float)Math.hypot(y,0)-.12f,.003f,.002f);
        if(chest<d){d=chest;m=M_ARMOR;}
        // segmented abdomen plates
        float ab=ell(x,y,z,0,0,1.135f,.098f,.124f,.095f)-.009f;ab=Math.max(ab,Math.max(1.06f-z,z-1.218f));
        float band=((z-1.06f)/.039f)%1f;ab=groove(ab,(band-.5f)*.039f,.004f,-.0f);ab+=(Math.abs(band-.5f)>.42f)?.004f:0;
        if(ab<d){d=ab;m=M_ARMOR;}
        // back plate with a spine ridge
        float back=ell(x,y,z,-.01f,0,1.3f,.118f,.16f,.15f)-.012f;back=Math.max(back,Math.max(1.12f-z,z-1.45f));back=Math.max(back,x+.02f);
        back=groove(back,y,.005f,-.004f);if(back<d){d=back;m=M_ARMOR;}
        // collar / gorget base, steel trim at the plate edges
        float collar=ell(x,y,z,-.005f,0,1.455f,.095f,.13f,.03f)-.004f;if(collar<d){d=collar;m=M_TRIM;}
        // belt with buckle and side pouches, tassets over the hips, crimson sash at the front
        float belt=ell(x,y,z,0,0,1.0f,.123f,.172f,.03f);belt=Math.max(belt,Math.abs(z-1.005f)-.024f);if(belt<d){d=belt;m=M_LEATHER;}
        float buckle=box(x,y,z,.122f,0,1.005f,.008f,.026f,.022f,.003f);if(buckle<d){d=buckle;m=M_TRIM;}
        for(int s=-1;s<=1;s+=2){float pouch=box(x,y,z,.03f,s*.16f,.975f,.035f,.022f,.03f,.006f);if(pouch<d){d=pouch;m=M_LEATHER;}
            float tas=box(x,y,z,.045f,s*.115f,.918f,.06f,.062f,.05f,.008f);float cut=ell(x,y,z,0,s*.095f,.83f,.1f,.08f,.1f);tas=Math.max(tas,-cut+.004f);
            tas=groove(tas,z-.92f,.003f,.002f);tas=rivets(tas,y,z-.95f,.03f,.004f,.0025f);if(tas<d){d=tas;m=M_ARMOR;}}
        float sash=box(x,y,z,.118f,0,.935f,.006f,.055f,.068f,.004f);sash+=(float)Math.sin(z*90)*.0015f;if(sash<d){d=sash;m=M_CLOTH;}
        mat[0]=m;return d;
    }
    static float head(float x,float y,float z,int[] mat){
        float bd=bound(x,y,z,-.16f,.17f,-.14f,.14f,1.45f,1.86f);if(bd>.02f){mat[0]=M_ARMOR;return bd;}
        float neck=cone(x,y,z,v(0,0,1.47f),v(0,0,1.6f),.047f,.044f);float d=neck;int m=M_SUIT;
        float gorget=ell(x,y,z,-.005f,0,1.515f,.075f,.085f,.035f);gorget=groove(gorget,z-1.515f,.003f,.002f);if(gorget<d){d=gorget;m=M_TRIM;}
        // helmet: a smooth shell, jaw guard, swept crest, visor slit
        float helm=smin(ell(x,y,z,.01f,0,1.675f,.124f,.104f,.13f),ell(x,y,z,.06f,0,1.592f,.075f,.078f,.055f),.03f);
        float crest=box(x,y,z,-.005f,0,1.775f,.115f,.006f,.03f,.004f);crest=Math.max(crest,ell(x,y,z,.01f,0,1.675f,.13f,.2f,.145f));
        helm=smin(helm,crest,.008f);
        helm=groove(helm,Math.abs(y)-.058f,.003f,.0025f*(x>.04f&&z<1.66f?1:0));          // cheek guards
        helm=groove(helm,z-1.62f+x*.25f,.003f,.002f*(x>0?1:0));
        helm=rivets(helm,x,Math.abs(y)-.098f,.04f,.005f,.003f);
        boolean visorZone=x>.06f&&Math.abs(z-1.676f)<.0135f&&Math.abs(y)<.085f;
        if(visorZone)helm+=.006f;
        if(helm<d){d=helm;m=visorZone&&helm>-.004f?M_VISOR:M_ARMOR;}
        mat[0]=m;return d;
    }
    static float[] braidPt(float t){return v(-.115f-.07f*t-.02f*t*t,0.012f*(float)Math.sin(t*3),1.63f-.42f*t);}
    static float braid(float x,float y,float z,int[] mat){
        float bd=bound(x,y,z,-.25f,-.06f,-.07f,.07f,1.15f,1.7f);if(bd>.02f){mat[0]=M_HAIR;return bd;}
        float d=1e9f;
        for(int i=0;i<=26;i++){float t=i/26f;float[] c=braidPt(t);float r=mix(.03f,.013f,t);float off=(i%2==0?1:-1)*r*.35f;
            d=smin(d,ell(x,y,z,c[0],c[1]+off,c[2],r*.9f,r*.75f,r*1.15f),.006f);}
        float tie=sphere(x,y,z,braidPt(.97f)[0],0,braidPt(.97f)[2],.016f);mat[0]=M_HAIR;if(tie<d){mat[0]=M_LEATHER;d=tie;}
        // fine strands
        if(mat[0]==M_HAIR)d+=(float)Math.sin((y*1.2f+z)*1400)*.0004f;
        return d;
    }
    static float pauldron(int s,float x,float y,float z,int[] mat){
        float cy=s*.235f;float bd=bound(x,y,z,-.15f,.15f,cy-.14f,cy+.14f,1.3f,1.6f);if(bd>.02f){mat[0]=M_ARMOR;return bd;}
        float top=ell(x,y,z,-.005f,cy,1.48f,.105f,.085f,.075f);top=Math.max(top,1.42f-z-(Math.abs(y-cy))*.4f);
        float mid=ell(x,y,z,-.005f,cy+s*.01f,1.435f,.1f,.083f,.065f);mid=Math.max(mid,1.37f-z);
        float low=ell(x,y,z,-.005f,cy+s*.016f,1.395f,.092f,.077f,.052f);low=Math.max(low,1.355f-z);
        float d=Math.min(top,Math.min(mid,low));
        d=groove(d,(float)Math.hypot(x+.005f,y-cy)-.06f,.003f,.002f*(z>1.5f?1:0));
        d=rivets(d,x,z-1.405f,.035f,.005f,.003f);
        float edge=Math.min(Math.abs(z-1.42f),Math.min(Math.abs(z-1.37f),Math.abs(z-1.355f)));
        mat[0]=edge<.006f?M_TRIM:M_ARMOR;return d;
    }
    static float upperArm(int s,float x,float y,float z,int[] mat){
        float[] a=shoulder(s),b=elbow(s);float bd=bound(x,y,z,Math.min(a[0],b[0])-.09f,Math.max(a[0],b[0])+.09f,Math.min(a[1],b[1])-.09f,Math.max(a[1],b[1])+.09f,b[2]-.09f,a[2]+.05f);
        if(bd>.02f){mat[0]=M_SUIT;return bd;}
        float d=cone(x,y,z,a,b,.05f,.043f);int m=M_SUIT;
        float plate=cone(x,y,z,add(a,v(0,s*.008f,-.05f),1),b,.056f,.048f);plate=Math.max(plate,-(s*(y-mix(a[1],b[1],.5f)))+.005f);
        plate=groove(plate,z-(a[2]+b[2])*.5f,.003f,.002f);if(plate<d){d=plate;m=M_ARMOR;}
        mat[0]=m;return d;
    }
    static float forearm(int s,float x,float y,float z,int[] mat){
        float[] a=elbow(s),b=wrist(s);float bd=bound(x,y,z,Math.min(a[0],b[0])-.09f,Math.max(a[0],b[0])+.09f,Math.min(a[1],b[1])-.09f,Math.max(a[1],b[1])+.09f,b[2]-.07f,a[2]+.07f);
        if(bd>.02f){mat[0]=M_SUIT;return bd;}
        float d=cone(x,y,z,a,b,.044f,.034f);int m=M_SUIT;
        float vb=cone(x,y,z,add(a,sub(b,a),.2f),add(b,sub(a,b),.06f),.053f,.043f);
        float[] ax=norm(sub(b,a));float along=dot(sub(v(x,y,z),a),ax);float ring=(along/.032f)%1f;vb+=Math.abs(ring-.5f)>.44f?.003f:0;
        if(vb<d){d=vb;m=M_ARMOR;}
        float cop=sphere(x,y,z,a[0]-.012f,a[1]+s*.006f,a[2],.05f);cop=groove(cop,dot(sub(v(x,y,z),a),ax),.003f,.002f);if(cop<d){d=cop;m=M_TRIM;}
        mat[0]=m;return d;
    }
    static float hand(int s,float x,float y,float z,int[] mat){
        float[] w=wrist(s),e=elbow(s);float[] ax=norm(sub(w,e));float[] c=add(w,ax,.055f);
        float bd=bound(x,y,z,c[0]-.1f,c[0]+.1f,c[1]-.1f,c[1]+.1f,c[2]-.1f,c[2]+.1f);if(bd>.02f){mat[0]=M_LEATHER;return bd;}
        // a gauntleted fist: knuckle plates and finger segments
        float fist=ell(x,y,z,c[0],c[1],c[2],.045f,.04f,.055f);
        float knuck=ell(x,y,z,c[0]+.022f,c[1],c[2]-.01f,.03f,.045f,.04f);fist=smin(fist,knuck,.015f);
        float seg=((z-c[2])/.018f)%1f;fist+=Math.abs(seg-.5f)>.42f?.0025f:0;
        float thumb=cone(x,y,z,add(c,v(.02f,-s*.03f,.02f),1),add(c,v(.045f,-s*.01f,.005f),1),.014f,.011f);fist=smin(fist,thumb,.008f);
        mat[0]=Math.abs(x-c[0]-.03f)<.02f&&z>c[2]?M_TRIM:M_LEATHER;return fist;
    }
    static float thigh(int s,float x,float y,float z,int[] mat){
        float[] a=hip(s),b=knee(s);float bd=bound(x,y,z,-.12f,.14f,Math.min(a[1],b[1])-.12f,Math.max(a[1],b[1])+.12f,b[2]-.07f,a[2]+.08f);if(bd>.02f){mat[0]=M_SUIT;return bd;}
        float d=cone(x,y,z,a,b,.088f,.06f);int m=M_SUIT;
        float cuisse=cone(x,y,z,add(a,v(.012f,0,-.06f),1),add(b,v(.006f,0,.05f),1),.092f,.066f);cuisse=Math.max(cuisse,-(x-.0f)+.005f);
        cuisse=groove(cuisse,y-mix(a[1],b[1],(a[2]-z)/(a[2]-b[2])),.003f,.002f);if(cuisse<d){d=cuisse;m=M_ARMOR;}
        float kn=ell(x,y,z,b[0]+.04f,b[1],b[2],.04f,.06f,.06f);kn=groove(kn,z-b[2],.003f,.002f);kn=rivets(kn,y-b[1],z-b[2]+.03f,.04f,.005f,.003f);if(kn<d){d=kn;m=M_TRIM;}
        mat[0]=m;return d;
    }
    static float shin(int s,float x,float y,float z,int[] mat){
        float[] a=knee(s),b=ankle(s);float bd=bound(x,y,z,-.1f,.12f,Math.min(a[1],b[1])-.1f,Math.max(a[1],b[1])+.1f,b[2]-.05f,a[2]+.03f);if(bd>.02f){mat[0]=M_SUIT;return bd;}
        float d=cone(x,y,z,a,b,.058f,.043f);int m=M_SUIT;
        float greave=cone(x,y,z,add(a,v(.008f,0,-.06f),1),add(b,v(.004f,0,.04f),1),.064f,.05f);greave=Math.max(greave,-(x+.005f));
        float ridge=Math.abs(y-mix(a[1],b[1],(a[2]-z)/(a[2]-b[2])));greave-=(1-smooth(0,.012f,ridge))*.004f;
        if(greave<d){d=greave;m=M_ARMOR;}
        mat[0]=m;return d;
    }
    static float foot(int s,float x,float y,float z,int[] mat){
        float[] a=ankle(s),t=toe(s);float bd=bound(x,y,z,-.09f,.26f,a[1]-.09f,a[1]+.09f,-.01f,.16f);if(bd>.02f){mat[0]=M_ARMOR;return bd;}
        float u=clamp((x-a[0]+.06f)/(t[0]-a[0]+.06f),0,1);float hw=mix(.05f,.04f,u),hh=mix(.065f,.03f,u*u);
        float boot=box(x,y,z,(a[0]-.06f+t[0])*.5f,mix(a[1],t[1],u),hh+.0f,(t[0]-a[0]+.06f)*.5f,hw,hh,.018f);
        boot=smin(boot,cone(x,y,z,a,add(a,v(0,0,.07f),1),.05f,.048f),.02f);
        float plates=((x-a[0])/.035f)%1f;if(u>.35f)boot+=Math.abs(plates-.5f)>.43f?.0025f:0;
        int m=z<.016f?M_RUBBER:(u>.35f&&z>.03f?M_ARMOR:M_TRIM);
        mat[0]=m;return boot;
    }
    static float shaft(float x,float y,float z,int[] mat){
        float bd=bound(x,y,z,GX-.05f,GX+.05f,GY-.05f,GY+.05f,-.02f,1.82f);if(bd>.02f){mat[0]=M_TRIM;return bd;}
        float d=cylZ(x,y,z,GX,GY,.016f,.04f,1.78f);int m=M_TRIM;
        if(z>.82f&&z<1.08f){float sp=(float)Math.sin((Math.atan2(y-GY,x-GX)*1+z*220));d+=sp>.6f?.0012f:-.001f;m=M_LEATHER;}
        float butt=cone(x,y,z,v(GX,GY,.0f),v(GX,GY,.06f),.006f,.022f);if(butt<d){d=butt;m=M_TRIM;}
        mat[0]=m;return d;
    }
    static float blade(float x,float y,float z,int[] mat){
        float bd=bound(x,y,z,GX-.16f,GX+.14f,GY-.06f,GY+.06f,1.7f,2.3f);if(bd>.02f){mat[0]=M_OBSIDIAN;return bd;}
        float collar=cylZ(x,y,z,GX,GY,.028f,1.74f,1.81f);collar=groove(collar,z-1.775f,.003f,.002f);
        float t=clamp((z-1.8f)/.45f,0,1);float w=.075f*(float)Math.pow(1-t,.65f)+.004f;float cxb=GX+.012f+.02f*t;
        float bl=box(x,y,z,cxb,GY,1.8f+.225f,w,.011f*(1-t*.6f),.225f,.004f);
        bl=Math.max(bl,Math.abs(x-cxb)-w);
        float edge=Math.abs(Math.abs(x-cxb)-w);bl+=(1-smooth(0,.006f,edge))*.0f;
        float hook=cone(x,y,z,v(GX-.01f,GY,1.86f),v(GX-.13f,GY,1.96f),.016f,.003f);hook=Math.max(hook,Math.abs(y-GY)-.008f);
        float d=Math.min(bl,hook);int m=M_OBSIDIAN;
        if(collar<d){d=collar;m=M_TRIM;}
        mat[0]=m;return d;
    }

    // ================================================================== materials of the high poly
    /** Albedo (linear), roughness, metalness, emission at a point; lap = curvature (edge wear), cav = cavity. */
    static float[] material(int m,float x,float y,float z,float lap,float cav){
        float r,g,b,rough,metal,emit=0;
        switch(m){
            case M_SUIT:{float n=fbm(x*300,y*300,z*300,3);float weave=((int)(x*900+z*900)&1)==0?.03f:0;r=.035f+n*.02f+weave*.3f;g=.036f+n*.02f+weave*.3f;b=.04f+n*.022f+weave*.3f;rough=.72f;metal=0;break;}
            case M_ARMOR:{   // bone-white ceramic, grime in the seams, soot, chipped edges show dark under-layer
                float n=fbm(x*40,y*40,z*40,4),soot=smooth(.58f,.82f,fbm(x*9+2,y*9,z*9,5)),chip=smooth(.6f,.95f,lap*.0012f+fbm(x*120,y*120,z*120,3)*.5f);
                r=.56f-n*.06f;g=.52f-n*.06f;b=.44f-n*.05f;
                r=mix(r,.12f,cav*.8f);g=mix(g,.1f,cav*.8f);b=mix(b,.08f,cav*.8f);
                r=mix(r,.05f,soot*.7f);g=mix(g,.045f,soot*.7f);b=mix(b,.04f,soot*.7f);
                r=mix(r,.07f,chip);g=mix(g,.07f,chip);b=mix(b,.075f,chip);
                rough=.42f+n*.15f+soot*.25f;metal=chip*.6f;break;}
            case M_TRIM:{float n=fbm(x*200,y*200,z*200,3);float wear=smooth(.55f,.9f,lap*.0012f+n*.4f);r=mix(.06f,.4f,wear);g=mix(.06f,.38f,wear);b=mix(.065f,.36f,wear);rough=.35f+n*.15f;metal=1;break;}
            case M_CLOTH:{float n=fbm(x*80,y*80,z*120,4);float th=((int)(y*1200)&1)==0?.04f:0;r=.32f+n*.08f+th;g=.03f+n*.015f;b=.03f+n*.015f;rough=.85f;metal=0;break;}
            case M_LEATHER:{float n=fbm(x*150,y*150,z*150,4);r=.11f+n*.05f;g=.065f+n*.03f;b=.04f+n*.02f;rough=.62f+n*.15f;metal=0;break;}
            case M_VISOR:{r=1f;g=.55f;b=.12f;rough=.15f;metal=0;emit=1;break;}
            case M_HAIR:{float st=.5f+.5f*(float)Math.sin((y*1.2f+z)*1400);float n=fbm(x*60,y*60,z*60,3);r=.2f*(.7f+st*.4f)+n*.04f;g=.055f*(.7f+st*.4f);b=.025f;rough=.55f;metal=0;break;}
            case M_OBSIDIAN:{   // black volcanic glass with molten cracks that glow
                float[] c=cells(y*60+x*20,z*28,x*30);float crack=1-smooth(0,.12f,c[1]-c[0]);float n=fbm(x*90,y*90,z*90,3);
                r=mix(.02f,1f,crack);g=mix(.018f,.4f,crack);b=mix(.022f,.06f,crack);rough=mix(.12f+n*.1f,.5f,crack);metal=0;emit=crack;break;}
            case M_RUBBER:{float n=fbm(x*200,y*200,z*200,3);r=g=b=.02f+n*.01f;rough=.9f;metal=0;break;}
            default:{r=g=b=.5f;rough=.5f;metal=0;}
        }
        return new float[]{r,g,b,rough,metal,emit};
    }
    /** 3D Worley cells: {F1, F2}. */
    static float[] cells(float x,float y,float z){
        int xi=(int)Math.floor(x),yi=(int)Math.floor(y),zi=(int)Math.floor(z);float f1=9,f2=9;
        for(int k=-1;k<=1;k++)for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){int cx=xi+i,cy=yi+j,cz=zi+k;
            float px=cx+hash(cx,cy,cz),py=cy+hash(cx+7,cy,cz),pz=cz+hash(cx,cy+3,cz+5);float d=(float)Math.sqrt((px-x)*(px-x)+(py-y)*(py-y)+(pz-z)*(pz-z));
            if(d<f1){f2=f1;f1=d;}else if(d<f2)f2=d;}
        return new float[]{f1,f2};
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

    // ================================================================== the low poly: rings fitted to the sculpt
    /**
     * A part is lofted through rings placed at stations along its axis (a curve for the braid). Every ring vertex is
     * found by casting a ray at the part's SDF from outside: from a point on a big ellipse in the ring plane, along the
     * ellipse's inward normal (a circle for round parts, a flat ellipse for the glaive's blade so the vertices spread
     * across its faces), falling back to a ray aimed at the axis. The hit is pushed out a little so the low poly sits
     * just over the high poly. Ends are closed with a fan to a tip found by a ray along the axis.
     */
    static final float LIFT=.003f;
    static float[] castIn(int part,float[] o,float[] d,float tmax){
        int[] mat=new int[1];float t=0;
        for(int k=0;k<400&&t<tmax;k++){float[] p=add(o,d,t);float s=part(part,p[0],p[1],p[2],mat);if(s<.0003f)return add(o,d,t-LIFT);t+=Math.max(s*.9f,.0004f);}
        return null;
    }
    static float[][] fitRing(int part,float[] c,float[] e1,float[] e2,int k,float A,float B){
        float[][] R=new float[k][];
        for(int j=0;j<k;j++){double th=Math.PI+j*2*Math.PI/k;float ca=(float)Math.cos(th),sa=(float)Math.sin(th);
            float[] start=add(add(c,e1,A*ca),e2,B*sa);float[] n=norm(add(mul(e1,B*ca),e2,A*sa));
            float[] h=castIn(part,start,mul(n,-1),2.2f*Math.max(A,B));
            if(h==null){float[] radial=norm(add(mul(e1,ca),e2,sa));start=add(c,radial,Math.max(A,B));h=castIn(part,start,mul(radial,-1),Math.max(A,B));}
            if(h==null)h=add(add(c,e1,.004f*ca),e2,.004f*sa);
            R[j]=h;}
        return R;
    }
    static float[][] frame(float[] axis){float[] fw=v(1,0,0);if(Math.abs(axis[0])>.9f)fw=v(0,0,1);
        float[] e1=norm(sub(fw,mul(axis,dot(fw,axis))));return new float[][]{e1,cross(axis,e1)};}
    static float[] tip(int part,float[] end,float[] out,float reach){float[] h=castIn(part,add(end,out,reach),mul(out,-1),reach*2);return h==null?end:h;}

    /** Builds one part's surface (side chart + optional end fans); vertex normals are averaged over the part. */
    static int loftPart(int part,float[][] centres,float[][] axes,int k,float A,float B,boolean cap0,boolean cap1,float reach0,float reach1){
        int n=centres.length;float[][][] R=new float[n][][];float[][] E1=new float[n][];
        for(int i=0;i<n;i++){float[][] f=frame(axes[i]);E1[i]=f[0];R[i]=fitRing(part,centres[i],f[0],f[1],k,A,B);}
        float[] t0=cap0?tip(part,centres[0],mul(axes[0],-1),reach0):null,t1=cap1?tip(part,centres[n-1],axes[n-1],reach1):null;
        // smooth normals: area-weighted face normals gathered on the shared ring points
        IdentityHashMap<float[],float[]> nrm=new IdentityHashMap<>();
        ArrayList<float[][]> faces=new ArrayList<>();
        for(int i=0;i+1<n;i++)for(int j=0;j<k;j++){float[] a=R[i][j],b=R[i][(j+1)%k],c=R[i+1][(j+1)%k],d=R[i+1][j];faces.add(new float[][]{a,b,c});faces.add(new float[][]{a,c,d});}
        if(t0!=null)for(int j=0;j<k;j++)faces.add(new float[][]{R[0][(j+1)%k],R[0][j],t0});
        if(t1!=null)for(int j=0;j<k;j++)faces.add(new float[][]{R[n-1][j],R[n-1][(j+1)%k],t1});
        for(float[][] f:faces){float[] fn=cross(sub(f[1],f[0]),sub(f[2],f[0]));for(float[] p:f){float[] acc=nrm.computeIfAbsent(p,q->new float[3]);acc[0]+=fn[0];acc[1]+=fn[1];acc[2]+=fn[2];}}
        // side chart: u = arc length around the ring (centred), v = distance along the axis
        Chart c=chart(part);float[] vAcc=new float[n];for(int i=1;i<n;i++)vAcc[i]=vAcc[i-1]+len(sub(centroid(R[i]),centroid(R[i-1])));
        int[][] id=new int[n][k+1];
        for(int i=0;i<n;i++){float[] acc=new float[k+1];for(int j=0;j<k;j++)acc[j+1]=acc[j]+len(sub(R[i][(j+1)%k],R[i][j]));
            for(int j=0;j<=k;j++){float[] p=R[i][j%k];float[] N=norm(nrm.get(p));float[] T=sub(R[i][(j+1)%k],R[i][(j-1+k)%k]);T=norm(sub(T,mul(N,dot(T,N))));
                id[i][j]=vert(c,p,N,T,acc[j]-acc[k]*.5f,vAcc[i]);}}
        for(int i=0;i+1<n;i++)for(int j=0;j<k;j++){int a=id[i][j],b=id[i][j+1],cc=id[i+1][j+1],d=id[i+1][j];c.tris.add(new int[]{a,b,cc});c.tris.add(new int[]{a,cc,d});}
        orient(c);int tris=c.tris.size();
        if(t0!=null)tris+=fan(part,R[0],t0,nrm,E1[0],true);
        if(t1!=null)tris+=fan(part,R[n-1],t1,nrm,E1[n-1],false);
        return tris;
    }
    static int fan(int part,float[][] R,float[] tip,IdentityHashMap<float[],float[]> nrm,float[] e1,boolean flip){
        Chart c=chart(part);int k=R.length;float[] axis=norm(nrm.get(tip));float[] t=norm(sub(e1,mul(axis,dot(e1,axis))));float[] bt=cross(axis,t);
        int ti=vert(c,tip,axis,t,0,0);int[] ids=new int[k];
        for(int j=0;j<k;j++){float[] N=norm(nrm.get(R[j]));float[] T=norm(sub(t,mul(N,dot(t,N))));float[] d=sub(R[j],tip);ids[j]=vert(c,R[j],N,T,dot(d,t),dot(d,bt));}
        for(int j=0;j<k;j++)c.tris.add(flip?new int[]{ids[(j+1)%k],ids[j],ti}:new int[]{ids[j],ids[(j+1)%k],ti});
        orient(c);return k;
    }
    /** Stations along the straight segment a..b at fractions ts (may run past 0..1). */
    static float[][][] line(float[] a,float[] b,float... ts){float[][] C=new float[ts.length][],X=new float[ts.length][];float[] ax=norm(sub(b,a));
        for(int i=0;i<ts.length;i++){C[i]=add(a,sub(b,a),ts[i]);X[i]=ax;}return new float[][][]{C,X};}
    static final int[] partTris=new int[PARTS];
    static void piece(int part,float[][][] st,int k,float A,float B,boolean c0,boolean c1,float r0,float r1){partTris[part]+=loftPart(part,st[0],st[1],k,A,B,c0,c1,r0,r1);}
    static void piece(int part,float[][][] st,int k,float R,boolean c0,boolean c1){piece(part,st,k,R,R,c0,c1,.15f,.15f);}

    static void buildLow(){
        // torso: hips to collar, a vertical axis
        piece(P_TORSO,line(v(0,0,.866f),v(0,0,1.49f),0,.07f,.15f,.23f,.31f,.37f,.45f,.53f,.62f,.7f,.79f,.87f,.95f,1),16,.36f,true,true);
        // helmet and neck
        piece(P_HEAD,line(v(0,0,1.475f),v(0,0,1.805f),0,.13f,.23f,.33f,.43f,.53f,.63f,.73f,.83f,.92f,1),14,.3f,false,true);
        // braid: stations along its curve
        {int n=8;float[][] C=new float[n][],X=new float[n][];for(int i=0;i<n;i++){float t=.08f+.9f*i/(n-1);C[i]=braidPt(t);X[i]=norm(sub(braidPt(Math.min(1,t+.01f)),braidPt(Math.max(0,t-.01f))));}
            piece(P_BRAID,new float[][][]{C,X},6,.09f,.09f,true,true,.06f,.06f);}
        // glaive: shaft and blade (the blade's rings cast from a flat ellipse so vertices spread over its faces)
        piece(P_GLAIVE_SHAFT,line(v(GX,GY,.0f),v(GX,GY,1.745f),.004f,.035f,.47f,.53f,.62f,1),6,.1f,true,false);
        piece(P_GLAIVE_HEAD,line(v(GX,GY,1.74f),v(GX,GY,2.25f),0,.07f,.16f,.24f,.3f,.38f,.46f,.58f,.72f,.86f,.97f),8,.22f,.05f,true,true,.1f,.1f);
        for(int s=-1;s<=1;s+=2){
            float cy=s*.235f;
            piece(sidePart(s,S_PAULDRON),line(v(-.005f,cy,1.355f),v(-.005f,cy,1.555f),.01f,.15f,.3f,.45f,.6f,.75f,.88f),12,.25f,true,true);
            float[] sh=shoulder(s),el=elbow(s),wr=wrist(s),hp=hip(s),kn=knee(s),an=ankle(s),to=toe(s);
            piece(sidePart(s,S_UPPER),line(sh,el,.06f,.4f,.7f,.97f),8,.16f,true,true);
            float[] fa=norm(sub(wr,el));
            piece(sidePart(s,S_FORE),line(el,wr,-.25f,0,.2f,.45f,.7f,.92f,1),9,.16f,true,false);
            float[] hc=add(wr,fa,.055f);
            piece(sidePart(s,S_HAND),line(add(hc,fa,-.06f),add(hc,fa,.06f),0,.35f,.7f,1),8,.15f,true,true);
            piece(sidePart(s,S_THIGH),line(hp,kn,0,.25f,.5f,.75f,.92f,1.04f),10,.2f,false,true);
            piece(sidePart(s,S_SHIN),line(kn,an,-.04f,.12f,.35f,.6f,.85f,1.05f),9,.18f,true,false);
            float[] heel=v(an[0]-.075f,an[1],.06f),tipF=v(to[0]+.03f,to[1],.04f);
            piece(sidePart(s,S_FOOT),line(heel,tipF,0,.15f,.35f,.55f,.75f,.95f),8,.22f,true,true);
        }
    }
    // ================================================================== packing
    static int AW=2048,AH=2048;static float texelsPerMetre;
    static void pack(){
        // lay every chart with its long side horizontal (better shelf packing); the tangent turns with the uvs so
        // the normal map and parallax stay aligned: (u,v) -> (v,-u) and T -> cross(N,T)
        for(Chart c:charts)if(c.maxV-c.minV>c.maxU-c.minU){
            for(float[] q:c.verts){float u=q[9],vv=q[10];q[9]=vv;q[10]=-u;float[] N=v(q[3],q[4],q[5]),T=v(q[6],q[7],q[8]);float[] B=cross(N,T);q[6]=B[0];q[7]=B[1];q[8]=B[2];}
            float a=c.minU,b=c.maxU;c.minU=c.minV;c.maxU=c.maxV;c.minV=-b;c.maxV=-a;}
        float density=5000;   // texels per metre, shrunk until everything fits
        for(int tries=0;tries<60;tries++){if(tryPack(density))break;density*=.95f;}
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
    static final float CAGE=.028f,RANGE=.012f;   // ray search distance out/in, height map range (± metres)
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
        float occ=0,wsum=0;float[] hs={.0015f,.003f,.006f,.012f,.025f,.05f,.1f};
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
        ImageIO.write(a,"png",new File(dir,"hero_a.png"));ImageIO.write(b,"png",new File(dir,"hero_b.png"));ImageIO.write(c,"png",new File(dir,"hero_c.png"));
    }
    /**
     * hero.bin: same layout as gun.bin: magic, pom scale, group count, then per group a vertex count and 11 floats per
     * vertex. One group per body part (part ids as above) so the game can pose them as a rigid-segment rig; the glaive's
     * parts are written empty (she carries the shotgun in the game).
     */
    static boolean SKIP_GLAIVE=true;
    static int writeMesh(File f)throws Exception{
        ArrayList<float[]>[] g=new ArrayList[PARTS];for(int i=0;i<PARTS;i++)g[i]=new ArrayList<>();int total=0;
        for(Chart c:charts){if(SKIP_GLAIVE&&(c.group==P_GLAIVE_SHAFT||c.group==P_GLAIVE_HEAD))continue;for(int[] t:c.tris)for(int k:t)g[c.group].add(c.verts.get(k));}
        int n=0;for(ArrayList<float[]> l:g)n+=l.size();
        ByteBuffer bb=ByteBuffer.allocate(12+PARTS*4+n*44).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(0x4e554753);bb.putFloat(2*RANGE*texelsPerMetre/AW);bb.putInt(PARTS);
        for(ArrayList<float[]> l:g){bb.putInt(l.size());total+=l.size()/3;for(float[] q:l)for(int j=0;j<11;j++)bb.putFloat(q[j]);}
        try(FileOutputStream out=new FileOutputStream(f)){out.write(bb.array(),0,bb.position());}
        return total;
    }
    // ================================================================== high poly mesh (surface nets, narrow band)
    static long meshHigh(int g,float h,File obj)throws Exception{
        float x0=-.3f,x1=.3f,y0=-.4f,y1=.4f,z0=-.01f,z1=2.27f;
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

    // ================================================================== previews
    /** Orthographic sphere-traced render of the high poly (left) and the baked low poly with its maps (right). */
    static void preview(File dir,float yaw,float pitch,String name,float[] centre,float span,int W,int H)throws Exception{
        BufferedImage im=new BufferedImage(W*2,H,BufferedImage.TYPE_INT_RGB);
        float cy=(float)Math.cos(yaw),sy=(float)Math.sin(yaw),cp=(float)Math.cos(pitch),sp=(float)Math.sin(pitch);
        float[] fwd=v(-cy*cp,-sy*cp,-sp),right=norm(cross(fwd,v(0,0,1))),up=cross(right,fwd);float dist=1.6f,scale=span/H;
        float[] L=norm(v(.5f,.35f,.8f)),L2=norm(v(-.6f,-.4f,.25f));
        int rows=H;ExecutorService ex=Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());ArrayList<Future<?>> fs=new ArrayList<>();
        for(int py0=0;py0<rows;py0++){final int py=py0;fs.add(ex.submit(()->{int[] mat=new int[1];
            for(int px=0;px<W;px++){float sx=(px-W/2f)*scale,syy=(H/2f-py)*scale;
                float[] o=add(add(add(centre,fwd,-dist),right,sx),up,syy);float t=0;boolean hitp=false;float[] p=null;
                for(int k=0;k<400;k++){p=add(o,fwd,t);float d=sdfAll(p[0],p[1],p[2],mat);if(d<.0002f){hitp=true;break;}t+=d*.85f;if(t>3.4f)break;}
                int col=0x1c1a1e;
                if(hitp){float e=.0003f;float[] n=norm(v(sdfAll(p[0]+e,p[1],p[2],mat)-sdfAll(p[0]-e,p[1],p[2],mat),sdfAll(p[0],p[1]+e,p[2],mat)-sdfAll(p[0],p[1]-e,p[2],mat),sdfAll(p[0],p[1],p[2]+e,mat)-sdfAll(p[0],p[1],p[2]-e,mat)));
                    sdfAll(p[0],p[1],p[2],mat);float[] mt=material(mat[0],p[0],p[1],p[2],0,0);col=shade(mt,n,fwd,L,L2,1);}
                synchronized(im){im.setRGB(px,py,col);}}
            return null;}));}
        for(Future<?> f:fs)f.get();ex.shutdown();
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
                im.setRGB(W+x,y,shade(new float[]{A_R[ti],A_G[ti],A_B[ti],RO[ti],ME[ti],EM[ti]},n,fwd,L,L2,AO[ti]));}}
        ImageIO.write(im,"png",new File(dir,name));
    }
    /** Flat-shaded low poly wireframe (for checking the topology). */
    static void wirePreview(File dir,float yaw,String name)throws Exception{
        int W=700,H=1100;BufferedImage im=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g=im.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(28,26,30));g.fillRect(0,0,W,H);
        float cy=(float)Math.cos(yaw),sy=(float)Math.sin(yaw);float[] fwd=v(-cy,-sy,0),right=norm(cross(fwd,v(0,0,1))),up=v(0,0,1);float scale=2.35f/H;float[] centre=v(0,0,1.13f);
        ArrayList<float[][]> tl=new ArrayList<>();for(Chart c:charts)for(int[] t:c.tris){float[][] S=new float[3][];float[] nn=v(0,0,0);
            for(int k=0;k<3;k++){float[] q=c.verts.get(t[k]);float[] rel=sub(v(q[0],q[1],q[2]),centre);S[k]=new float[]{W/2f+dot(rel,right)/scale,H/2f-dot(rel,up)/scale,dot(rel,fwd)};}
            float[] a=c.verts.get(t[0]),b=c.verts.get(t[1]),d=c.verts.get(t[2]);float[] fn=norm(cross(sub(v(b[0],b[1],b[2]),v(a[0],a[1],a[2])),sub(v(d[0],d[1],d[2]),v(a[0],a[1],a[2]))));
            if(dot(fn,fwd)>0)continue;tl.add(new float[][]{S[0],S[1],S[2],fn});}
        tl.sort((p,q)->Float.compare(q[0][2]+q[1][2]+q[2][2],p[0][2]+p[1][2]+p[2][2]));
        float[] Lt=norm(v(.5f,.35f,.8f));
        for(float[][] t:tl){int s=(int)(60+120*Math.max(0,dot(t[3],Lt)));java.awt.Polygon pg=new java.awt.Polygon(new int[]{(int)t[0][0],(int)t[1][0],(int)t[2][0]},new int[]{(int)t[0][1],(int)t[1][1],(int)t[2][1]},3);
            g.setColor(new java.awt.Color(s,s,(int)(s*1.05f)>255?255:(int)(s*1.05f)));g.fillPolygon(pg);g.setColor(new java.awt.Color(0,220,150,150));g.drawPolygon(pg);}
        ImageIO.write(im,"png",new File(dir,name));
    }
    static int shade(float[] mt,float[] n,float[] fwd,float[] L,float[] L2,float ao){
        float[] V=mul(fwd,-1);if(dot(n,V)<0)n=mul(n,-1);float r=0,g=0,b=0;
        for(int li=0;li<2;li++){float[] l=li==0?L:L2;float k=li==0?1.6f:.6f;float nl=Math.max(0,dot(n,l));float[] h=norm(add(l,V,1));float nh=Math.max(0,dot(n,h));
            float sp=(float)Math.pow(nh,2+(1-mt[3])*120)*(1-mt[3])*1.5f;float[] f0=mt[4]>.5f?v(mt[0],mt[1],mt[2]):v(.04f,.04f,.04f);
            float kd=1-mt[4];r+=(mt[0]*kd*nl+f0[0]*sp*nl)*k;g+=(mt[1]*kd*nl+f0[1]*sp*nl)*k;b+=(mt[2]*kd*nl+f0[2]*sp*nl)*k;}
        float amb=.12f*ao;r+=mt[0]*amb+(mt[4]>.5f?mt[0]*.25f*ao:0);g+=mt[1]*amb+(mt[4]>.5f?mt[1]*.25f*ao:0);b+=mt[2]*amb+(mt[4]>.5f?mt[2]*.25f*ao:0);
        r*=ao;g*=ao;b*=ao;float e=mt.length>5?mt[5]*1.4f:0;r+=mt[0]*e;g+=mt[1]*e;b+=mt[2]*e;
        return (b8(srgb(r))<<16)|(b8(srgb(g))<<8)|b8(srgb(b));
    }
    static void uvPreview(File dir)throws Exception{
        BufferedImage im=new BufferedImage(AW/2,AH/2,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<AH/2;y++)for(int x=0;x<AW/2;x++){int i=(y*2)*AW+x*2;im.setRGB(x,y,(b8(srgb(A_R[i]*AO[i]+EM[i]*A_R[i]))<<16)|(b8(srgb(A_G[i]*AO[i]+EM[i]*A_G[i]))<<8)|b8(srgb(A_B[i]*AO[i]+EM[i]*A_B[i])));}
        java.awt.Graphics2D g=im.createGraphics();g.setColor(new java.awt.Color(0,255,160,90));
        for(Chart c:charts)for(int[] t:c.tris)for(int k=0;k<3;k++){float[] a=c.verts.get(t[k]),b=c.verts.get(t[(k+1)%3]);g.drawLine((int)(a[9]*AW/2),(int)(a[10]*AH/2),(int)(b[9]*AW/2),(int)(b[10]*AH/2));}
        ImageIO.write(im,"png",new File(dir,"hero_uv.png"));
    }

    public static void main(String[] a)throws Exception{
        File assets=new File(a[0]),prev=new File(a[1]);assets.mkdirs();prev.mkdirs();String mode=a.length>2?a[2]:"";
        long t0=System.currentTimeMillis();
        buildLow();int lowTris=0;for(Chart c:charts)lowTris+=c.tris.size();
        System.out.println("low poly: "+lowTris+" triangles in "+charts.size()+" UV charts; per part "+Arrays.toString(partTris));
        if(mode.equals("low")){AW=AH=64;A_R=A_G=A_B=AO=NX=NY=HT=RO=ME=EM=new float[AW*AH];wirePreview(prev,-.6f,"hero_low_front.png");wirePreview(prev,2.4f,"hero_low_back.png");return;}
        pack();System.out.printf(Locale.ROOT,"atlas %dx%d at %.0f texels per metre (%.2f mm per texel)%n",AW,AH,texelsPerMetre,1000/texelsPerMetre);
        if(mode.equals("high")){long hp=meshHigh(0,.0015f,null);System.out.println("high poly total: "+hp+" triangles");}
        if(mode.equals("obj")){long hp=meshHigh(0,.0015f,new File(prev,"hero_high.obj"));System.out.println("high poly total: "+hp+" triangles");}
        if(mode.equals("prev")){A_R=A_G=A_B=AO=NX=NY=NZ=HT=RO=ME=EM=new float[AW*AH];preview(prev,-.5f,.08f,"hero_high_only.png",v(0,0,1.13f),2.35f,560,1000);return;}
        System.out.println("baking…");bake();dilate(10);
        writeMaps(assets);int tr=writeMesh(new File(assets,"hero.bin"));System.out.println("mesh written: "+tr+" triangles");
        uvPreview(prev);wirePreview(prev,-.6f,"hero_low_front.png");wirePreview(prev,2.4f,"hero_low_back.png");
        preview(prev,-.5f,.08f,"hero_bake.png",v(0,0,1.13f),2.35f,600,1060);
        preview(prev,2.5f,.1f,"hero_bake_back.png",v(0,0,1.13f),2.35f,600,1060);
        preview(prev,-.3f,.05f,"hero_bake_head.png",v(.0f,0,1.6f),.5f,520,520);
        preview(prev,-.9f,.15f,"hero_bake_blade.png",v(GX,GY,2.0f),.6f,420,560);
        System.out.println("done in "+(System.currentTimeMillis()-t0)/1000+" s");
    }
}
