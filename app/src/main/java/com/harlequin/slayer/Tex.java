package com.harlequin.slayer;

/**
 * Procedurally generated, seamlessly tiling PBR materials. Every material is painted from a height field plus colour
 * rules, then packed into three textures the shader samples:
 * <ul>
 *   <li>A: albedo RGB (sRGB) + ambient occlusion in alpha</li>
 *   <li>B: tangent-space normal RGB + height in alpha (for parallax occlusion mapping)</li>
 *   <li>C: roughness R, metalness G, emission B</li>
 * </ul>
 * Pure Java (no Android), so the generator can be previewed on a desktop.
 */
final class Tex {
    private Tex(){}
    static final int BRICK=0,METAL_PANEL=1,FLOOR_TILES=2,DIAMOND_PLATE=3,CONCRETE=4,CRATE=5,LIGHT=6,PILLAR=7,LAVA=8,
            GUNMETAL=9,WOOD=10,LEATHER=11,FABRIC=12,BRASS=13,SHELL=14,COUNT=15;
    static final String[] NAMES={"Hell brick","Metal panel","Floor tiles","Diamond plate","Concrete","Crate","Light panel","Carved pillar","Lava",
            "Gunmetal","Walnut","Leather","Fabric","Brass","Shell hull"};

    /** One generated material. */
    static final class Mat {
        final int size;final int[] albedoAO,normalHeight,rme;
        /** Parallax depth in texture units (0 = flat), texture repeats per metre, emission strength. */
        float pom,tile,emit;
        Mat(int s){size=s;albedoAO=new int[s*s];normalHeight=new int[s*s];rme=new int[s*s];}
    }

    // ------------------------------------------------------------------ tileable noise
    private static int hashI(int x,int y,int s){int h=x*374761393+y*668265263+s*1442695041;h=(h^(h>>>13))*1274126177;return h^(h>>>16);}
    static float hash(int x,int y,int s){return (hashI(x,y,s)&0xffffff)/16777215f;}
    private static float fade(float t){return t*t*t*(t*(t*6-15)+10);}
    /** Value noise with period p (in lattice cells) at (x,y) in lattice units. */
    static float noise(float x,float y,int p,int seed){
        int xi=(int)Math.floor(x),yi=(int)Math.floor(y);float fx=x-xi,fy=y-yi;
        int x0=Math.floorMod(xi,p),y0=Math.floorMod(yi,p),x1=(x0+1)%p,y1=(y0+1)%p;
        float a=hash(x0,y0,seed),b=hash(x1,y0,seed),c=hash(x0,y1,seed),d=hash(x1,y1,seed);float u=fade(fx),v=fade(fy);
        return a+(b-a)*u+(c-a)*v+(a-b-c+d)*u*v;
    }
    /** Fractal noise, tiling over the unit square: u,v in 0..1, base period p cells. */
    static float fbm(float u,float v,int p,int oct,int seed){
        float s=0,amp=.5f,norm=0;int per=p;
        for(int i=0;i<oct;i++){s+=noise(u*per,v*per,per,seed+i*31)*amp;norm+=amp;amp*=.5f;per*=2;}
        return s/norm;
    }
    /** Worley cells, tiling: returns {F1, F2, cell id hash}. */
    static float[] cells(float u,float v,int p,int seed){
        float x=u*p,y=v*p;int xi=(int)Math.floor(x),yi=(int)Math.floor(y);float f1=9,f2=9;int id=0;
        for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){int cx=xi+i,cy=yi+j,wx=Math.floorMod(cx,p),wy=Math.floorMod(cy,p);
            float px=cx+hash(wx,wy,seed),py=cy+hash(wx,wy,seed+7);float d=(float)Math.hypot(px-x,py-y);
            if(d<f1){f2=f1;f1=d;id=hashI(wx,wy,seed+3);}else if(d<f2)f2=d;}
        return new float[]{f1,f2,(id&0xffff)/65535f};
    }
    private static float clamp(float x){return x<0?0:x>1?1:x;}
    private static float smooth(float a,float b,float x){float t=clamp((x-a)/(b-a));return t*t*(3-2*t);}
    private static float mix(float a,float b,float t){return a+(b-a)*t;}

    // ------------------------------------------------------------------ painting
    /** Working buffers for one material: height, albedo, ao, roughness, metal, emission. */
    private static final class Paint {
        final int n;final float[] h,r,g,b,ao,rough,metal,emit;
        Paint(int n){this.n=n;int s=n*n;h=new float[s];r=new float[s];g=new float[s];b=new float[s];ao=new float[s];rough=new float[s];metal=new float[s];emit=new float[s];java.util.Arrays.fill(ao,1);}
        void col(int i,float rr,float gg,float bb){r[i]=rr;g[i]=gg;b[i]=bb;}
    }

    static Mat make(int id){
        int n=256;Paint P=new Paint(n);float pom=.04f,tile=.5f,normalK=4,emit=0;
        switch(id){
            case BRICK:brick(P);pom=.06f;tile=.5f;normalK=5;break;
            case METAL_PANEL:metalPanel(P);pom=.035f;tile=.5f;normalK=4;break;
            case FLOOR_TILES:floorTiles(P);pom=.045f;tile=.33f;normalK=4;break;
            case DIAMOND_PLATE:diamond(P);pom=.03f;tile=.75f;normalK=6;break;
            case CONCRETE:concrete(P);pom=.025f;tile=.4f;normalK=3;break;
            case CRATE:crate(P);pom=.04f;tile=1f;normalK=5;break;
            case LIGHT:light(P);pom=.02f;tile=1f;normalK=3;emit=6;break;
            case PILLAR:pillar(P);pom=.07f;tile=.5f;normalK=6;break;
            case LAVA:lava(P);pom=.05f;tile=.35f;normalK=5;emit=4;break;
            case GUNMETAL:gunmetal(P);pom=0;tile=4;normalK=1.5f;break;
            case WOOD:wood(P);pom=0;tile=3;normalK=2;break;
            case LEATHER:leather(P);pom=0;tile=6;normalK=3;break;
            case FABRIC:fabric(P);pom=0;tile=6;normalK=3;break;
            case BRASS:brass(P);pom=0;tile=6;normalK=1;break;
            default:shell(P);pom=0;tile=6;normalK=2;break;
        }
        cavity(P,id>=GUNMETAL?.5f:1f);
        Mat m=new Mat(n);m.pom=pom;m.tile=tile;m.emit=emit;pack(P,m,normalK);return m;
    }
    /** Darkens crevices: ambient occlusion from how far each texel sits below its blurred surroundings. */
    private static void cavity(Paint P,float k){
        int n=P.n;float[] bl=blur(P.h,n,n/64+2);
        for(int i=0;i<n*n;i++){float c=clamp(1-Math.max(0,bl[i]-P.h[i])*6f*k);P.ao[i]*=c;}
    }
    private static float[] blur(float[] a,int n,int rad){
        float[] t=new float[n*n],o=new float[n*n];float inv=1f/(2*rad+1);
        for(int y=0;y<n;y++){float s=0;for(int k=-rad;k<=rad;k++)s+=a[y*n+Math.floorMod(k,n)];
            for(int x=0;x<n;x++){t[y*n+x]=s*inv;s+=a[y*n+Math.floorMod(x+rad+1,n)]-a[y*n+Math.floorMod(x-rad,n)];}}
        for(int x=0;x<n;x++){float s=0;for(int k=-rad;k<=rad;k++)s+=t[Math.floorMod(k,n)*n+x];
            for(int y=0;y<n;y++){o[y*n+x]=s*inv;s+=t[Math.floorMod(y+rad+1,n)*n+x]-t[Math.floorMod(y-rad,n)*n+x];}}
        return o;
    }
    private static void pack(Paint P,Mat m,float normalK){
        int n=P.n;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;
            float dx=P.h[y*n+(x+1)%n]-P.h[y*n+(x+n-1)%n],dy=P.h[((y+1)%n)*n+x]-P.h[((y+n-1)%n)*n+x];
            float nx=-dx*normalK*n/256f,ny=-dy*normalK*n/256f,nz=1,l=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);nx/=l;ny/=l;nz/=l;
            m.albedoAO[i]=(b8(P.ao[i])<<24)|(b8(srgb(P.r[i]))<<16)|(b8(srgb(P.g[i]))<<8)|b8(srgb(P.b[i]));
            m.normalHeight[i]=(b8(P.h[i])<<24)|(b8(nx*.5f+.5f)<<16)|(b8(ny*.5f+.5f)<<8)|b8(nz*.5f+.5f);
            m.rme[i]=0xff000000|(b8(P.rough[i])<<16)|(b8(P.metal[i])<<8)|b8(P.emit[i]);}
    }
    private static float srgb(float lin){lin=clamp(lin);return lin<=.0031308f?lin*12.92f:(float)(1.055*Math.pow(lin,1/2.4)-.055);}
    private static int b8(float v){return Math.max(0,Math.min(255,Math.round(v*255)));}
    private static float lin(int c){float s=c/255f;return s<=.04045f?s/12.92f:(float)Math.pow((s+.055)/1.055,2.4);}

    // ------------------------------------------------------------------ materials
    private static void brick(Paint P){
        int n=P.n,rows=8,per=4;float mortar=.035f;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=x/(float)n,v=y/(float)n;
            int row=(int)(v*rows);float bu=u*per+(row%2)*.5f;int col=(int)Math.floor(bu);float fu=bu-col,fv=v*rows-row;
            int bid=Math.floorMod(col,per)*31+row;
            float edge=Math.min(Math.min(fu,1-fu)/per*rows,Math.min(fv,1-fv));   // distance to the brick's edge (in row heights)
            float bevel=smooth(mortar,mortar+.09f,edge);
            float chip=fbm(u,v,16,4,9+bid)>.68f?.25f:0;
            float rough=fbm(u,v,32,4,3)*.18f;
            float hb=.62f+hash(bid,1,5)*.12f+rough-chip*bevel;
            float hm=.18f+fbm(u,v,64,3,11)*.08f;
            float h=edge<mortar?hm:mix(hm,hb,bevel);P.h[i]=h;
            float tone=.75f+hash(bid,2,5)*.45f,grime=fbm(u,v,8,4,21);
            if(edge<mortar+.02f){P.col(i,.11f,.1f,.095f);P.rough[i]=.95f;P.ao[i]=.75f;}
            else{float burn=smooth(.55f,.8f,grime);P.col(i,(.32f*tone)*(1-burn*.6f),(.085f*tone)*(1-burn*.7f),(.06f*tone)*(1-burn*.7f));P.rough[i]=.78f+rough;}
            P.metal[i]=0;}
    }
    private static void metalPanel(Paint P){
        int n=P.n,pan=2;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=x/(float)n,v=y/(float)n;
            float pu=u*pan%1,pv=v*pan%1;float e=Math.min(Math.min(pu,1-pu),Math.min(pv,1-pv));
            float seam=smooth(.008f,.022f,e);
            // rivets near each panel corner
            float rv=1e9f;for(float cu:new float[]{.06f,.94f})for(float cv:new float[]{.06f,.94f}){float d=(float)Math.hypot(pu-cu,pv-cv);rv=Math.min(rv,d);}
            float rivet=1-smooth(.012f,.022f,rv);
            float brushed=fbm(u*.02f+v,v,64,3,4)*.04f;
            float dent=fbm(u,v,8,4,17)*.06f;
            P.h[i]=.25f+seam*.45f+rivet*.18f*(float)Math.sqrt(Math.max(0,1-rv/.022f))+brushed+dent;
            float rust=smooth(.62f,.8f,fbm(u,v*.6f,8,5,31)+Math.max(0,.3f-e*4)*(pv>.5f?.6f:0));
            float base=.55f+fbm(u,v,16,3,5)*.1f;
            P.col(i,mix(base*.9f,.28f,rust),mix(base*.92f,.12f,rust),mix(base,.05f,rust));
            P.metal[i]=mix(1,.15f,rust);P.rough[i]=mix(.32f+brushed*3+dent,.85f,rust);
            if(seam<.5f){P.col(i,.08f,.08f,.085f);P.rough[i]=.7f;P.ao[i]=.6f;}}
    }
    private static void floorTiles(Paint P){
        int n=P.n,t=2;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=x/(float)n,v=y/(float)n;
            int tx=(int)(u*t),ty=(int)(v*t);float pu=u*t-tx,pv=v*t-ty;float e=Math.min(Math.min(pu,1-pu),Math.min(pv,1-pv));
            int tid=tx*7+ty*13;
            float bevel=smooth(.012f,.05f,e);
            float tilt=(pu-.5f)*(hash(tid,1,3)-.5f)*.06f+(pv-.5f)*(hash(tid,2,3)-.5f)*.06f;
            float[] c=cells(u,v,6,40);float crack=1-smooth(.0f,.03f,c[1]-c[0]);crack*=smooth(.55f,.7f,fbm(u,v,4,3,8));
            float wear=fbm(u,v,32,4,12);
            P.h[i]=.2f+bevel*(.55f+tilt+wear*.12f-crack*.25f);
            float tone=.8f+hash(tid,3,3)*.35f;
            P.col(i,.17f*tone*(1-crack*.5f),.16f*tone*(1-crack*.5f),.15f*tone*(1-crack*.5f));
            P.rough[i]=.6f+wear*.25f+crack*.2f;P.metal[i]=0;
            if(e<.012f){P.col(i,.05f,.045f,.04f);P.rough[i]=.95f;P.ao[i]=.6f;}}
    }
    private static void diamond(Paint P){
        int n=P.n,k=8;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=x/(float)n,v=y/(float)n;
            float gu=u*k,gv=v*k;int cx=(int)gu,cy=(int)gv;float fu=gu-cx-.5f,fv=gv-cy-.5f;
            boolean flip=((cx+cy)&1)==0;float a=flip?fu+fv:fu-fv,b=flip?fu-fv:fu+fv;   // elongated diamonds alternating direction
            float d=Math.abs(a)/.42f+Math.abs(b)/.12f;float bump=1-smooth(.75f,1f,d);
            float dirt=smooth(.5f,.75f,fbm(u,v,8,4,6));
            P.h[i]=.3f+bump*.45f+fbm(u,v,64,2,2)*.03f;
            float base=.5f+fbm(u,v,16,3,7)*.1f;
            P.col(i,mix(base,.12f,dirt*(1-bump)),mix(base,.1f,dirt*(1-bump)),mix(base*1.03f,.08f,dirt*(1-bump)));
            P.metal[i]=mix(1,.3f,dirt*(1-bump));P.rough[i]=bump>.5f?.22f:.45f+dirt*.4f;}
    }
    private static void concrete(Paint P){
        int n=P.n;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=x/(float)n,v=y/(float)n;
            float f=fbm(u,v,8,6,1);float pits=hash(x,y,9)>.985f?.15f:0;
            float[] c=cells(u,v,4,77);float crack=(1-smooth(0,.02f,c[1]-c[0]))*smooth(.6f,.72f,fbm(u,v,4,3,19));
            float stain=smooth(.55f,.85f,fbm(u,v*.5f,4,4,23));
            P.h[i]=.4f+f*.35f-pits-crack*.3f;
            float t=.32f+f*.12f;P.col(i,t*(1-stain*.35f),t*(1-stain*.4f),t*.97f*(1-stain*.45f));
            P.rough[i]=.88f+f*.1f;P.metal[i]=0;}
    }
    private static void crate(Paint P){
        int n=P.n;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=x/(float)n,v=y/(float)n;
            float e=Math.min(Math.min(u,1-u),Math.min(v,1-v));boolean frame=e<.1f;
            boolean diag=Math.abs(u-v)<.07f&&!frame;
            int plank=(int)(v*5);float pv=v*5-plank;float seam=frame||diag?1:smooth(.0f,.04f,Math.min(pv,1-pv));
            float grain=(float)Math.sin((u*40+fbm(u,v,4,4,plank+3)*12)*6.283f)*.5f+.5f;
            float k=frame||diag?.1f:.0f;
            P.h[i]=.3f+k*2.4f+seam*.25f+grain*.05f;
            float tone=.75f+hash(plank,1,4)*.3f+(frame?-.12f:0);
            P.col(i,.42f*tone*(.85f+grain*.15f),.25f*tone*(.85f+grain*.15f),.11f*tone);P.rough[i]=.7f+grain*.15f;P.metal[i]=0;
            if(seam<.5f){P.col(i,.06f,.035f,.02f);P.ao[i]=.6f;}
            // metal corner brackets
            if((u<.16f||u>.84f)&&(v<.16f||v>.84f)){P.h[i]=.82f;float s=fbm(u,v,32,3,2);P.col(i,.3f+s*.1f,.3f+s*.1f,.32f);P.metal[i]=1;P.rough[i]=.45f+s*.2f;
                float bu=(u<.5f?u:1-u),bv=(v<.5f?v:1-v);if(Math.hypot(bu-.08f,bv-.08f)<.02f)P.h[i]=.95f;}}
    }
    private static void light(Paint P){
        int n=P.n;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=x/(float)n,v=y/(float)n;
            float e=Math.min(Math.min(u,1-u),Math.min(v,1-v));
            boolean frame=e<.12f;float gu=u*6%1,gv=v*6%1;boolean grid=!frame&&(Math.min(gu,1-gu)<.06f||Math.min(gv,1-gv)<.06f);
            if(frame){P.h[i]=.7f;P.col(i,.25f,.25f,.27f);P.metal[i]=1;P.rough[i]=.4f;}
            else if(grid){P.h[i]=.55f;P.col(i,.1f,.1f,.1f);P.metal[i]=1;P.rough[i]=.5f;}
            else{P.h[i]=.4f;P.col(i,1f,.82f,.6f);P.emit[i]=1;P.rough[i]=.2f;}}
    }
    private static void pillar(Paint P){
        int n=P.n;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=x/(float)n,v=y/(float)n;
            float flute=(float)Math.abs(Math.sin(u*8*Math.PI));float band=Math.min(Math.abs(v-.08f),Math.abs(v-.92f));boolean isBand=band<.05f;
            float wear=fbm(u,v,16,5,44);
            float h=isBand?.85f-(float)Math.abs(Math.sin(u*48*Math.PI))*.12f:.35f+(float)Math.pow(flute,.5)*.45f;
            P.h[i]=h+wear*.1f;
            float t=.38f+wear*.15f;P.col(i,t*.95f,t*.85f,t*.75f);P.rough[i]=.75f+wear*.2f;P.metal[i]=0;}
    }
    private static void lava(Paint P){
        int n=P.n;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=x/(float)n,v=y/(float)n;
            float wu=u+fbm(u,v,4,3,5)*.08f,wv=v+fbm(u,v,4,3,6)*.08f;
            float[] c=cells(wu-(float)Math.floor(wu),wv-(float)Math.floor(wv),6,13);
            float crack=1-smooth(0,.09f,c[1]-c[0]);float f=fbm(u,v,16,4,7);
            P.h[i]=.75f-crack*.6f+f*.15f;
            float glow=crack*(.7f+f*.6f);
            P.col(i,mix(.05f,1f,glow),mix(.03f,.32f,glow*glow),mix(.025f,.04f,glow));P.emit[i]=clamp(glow);
            P.rough[i]=mix(.9f,.4f,glow);P.metal[i]=0;}
    }
    private static void gunmetal(Paint P){
        int n=P.n;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=x/(float)n,v=y/(float)n;
            float s=fbm(u*.03f+v*.97f,u,64,3,3);float scratch=hash(x/2,y*7/8,5)>.997f?1:0;float wear=smooth(.6f,.8f,fbm(u,v,8,4,9));
            P.h[i]=.5f+s*.05f-scratch*.1f;
            float b=.06f+s*.03f;P.col(i,mix(b,.42f,wear*.5f+scratch*.6f),mix(b,.42f,wear*.5f+scratch*.6f),mix(b*1.25f,.44f,wear*.5f+scratch*.6f));
            P.metal[i]=1;P.rough[i]=.28f+s*.2f+wear*.1f;}
    }
    private static void wood(Paint P){
        int n=P.n;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=x/(float)n,v=y/(float)n;
            float ring=(float)Math.sin((v*18+fbm(u,v,4,5,2)*5)*6.283f)*.5f+.5f;float pore=hash(x,y/3,4)>.9f?.06f:0;
            P.h[i]=.5f+ring*.06f-pore;
            float t=.6f+ring*.4f;P.col(i,.2f*t,.085f*t,.035f*t);P.rough[i]=.42f+ring*.15f+pore*2;P.metal[i]=0;}
    }
    private static void leather(Paint P){
        int n=P.n;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=x/(float)n,v=y/(float)n;
            float[] c=cells(u,v,24,3);float gr=smooth(0,.15f,c[1]-c[0]);float f=fbm(u,v,8,4,8);
            P.h[i]=.4f+gr*.3f+f*.1f;float t=.6f+f*.5f;P.col(i,.06f*t,.05f*t,.045f*t);P.rough[i]=.65f+(1-gr)*.2f;P.metal[i]=0;}
    }
    private static void fabric(Paint P){
        int n=P.n;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=x/(float)n,v=y/(float)n;
            boolean warp=(((x>>2)+(y>>2))&1)==0;float th=warp?(float)Math.sin((y&3)*.785f):(float)Math.sin((x&3)*.785f);float f=fbm(u,v,8,4,4);
            P.h[i]=.4f+th*.3f;float t=.7f+f*.5f+th*.1f;P.col(i,.06f*t,.07f*t,.05f*t);P.rough[i]=.9f;P.metal[i]=0;}
    }
    private static void brass(Paint P){
        int n=P.n;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=x/(float)n,v=y/(float)n;float f=fbm(u,v,16,4,6);
            P.h[i]=.5f+f*.03f;P.col(i,.8f-f*.15f,.58f-f*.15f,.25f-f*.1f);P.metal[i]=1;P.rough[i]=.25f+f*.25f;}
    }
    private static void shell(Paint P){
        int n=P.n;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=x/(float)n,v=y/(float)n;
            float rib=(float)Math.abs(Math.sin(u*40*Math.PI));float f=fbm(u,v,8,3,5);
            P.h[i]=.4f+rib*.25f;P.col(i,.45f+f*.1f,.03f,.02f);P.rough[i]=.35f+rib*.2f;P.metal[i]=0;}
    }
}
