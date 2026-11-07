package com.harlequin.slayer;

import static com.harlequin.slayer.Tex.*;

/**
 * The level texture library: 100 procedural PBR materials, 50 sci-fi and 50 hell, made from 32 hand-written patterns
 * (16 of each) and a recipe per material that tunes its pattern: palette (base, accent and glow colours), scale,
 * metalness, roughness, wear, rust, emission and variant. Every pattern tiles seamlessly and paints height (for
 * normals and parallax), albedo, roughness, metalness and emission, like the hand-made materials in {@link Tex}.
 * Pure Java.
 */
final class TexLib {
    private TexLib(){}
    // patterns: sci-fi
    static final int PANELS=0,HEX=1,VENT=2,GRATE=3,CIRCUIT=4,TECHFLOOR=5,HAZARD=6,PIPES=7,RIBBED=8,LIGHTSTRIP=9,DATA=10,PERFORATED=11,ARMOR=12,CABLES=13,GLASS=14,TREAD=15;
    // patterns: hell
    static final int FLESH=16,BONE=17,OBSIDIAN=18,BASALT=19,RUNESTONE=20,SCORCHED=21,HELLIRON=22,ASH=23,BLOODSTONE=24,SCALES=25,SINEW=26,SIGIL=27,ROOTS=28,COBBLE=29,BRIMSTONE=30,MAGMA=31;

    /** A material: its pattern and the knobs that make it distinct. Colours are sRGB hex. */
    static final class R {
        final String name;final int pat;final int base,accent,glow;final float metal,rough,emit;final int scale,var;float wear=.5f,rust=0;
        R(String name,int pat,int base,int accent,int glow,float metal,float rough,float emit,int scale,int var){
            this.name=name;this.pat=pat;this.base=base;this.accent=accent;this.glow=glow;this.metal=metal;this.rough=rough;this.emit=emit;this.scale=scale;this.var=var;}
        R wear(float w){wear=w;return this;} R rust(float r){rust=r;return this;}
    }
    private static R r(String n,int pat,int base,int accent,int glow,float metal,float rough,float emit,int scale,int var){return new R(n,pat,base,accent,glow,metal,rough,emit,scale,var);}

    static final R[] RECIPES={
        // ------------------------------------------------------------ sci-fi (50)
        r("Hull panels",PANELS,0x8a9098,0xd8a020,0x40c0ff,1,.35f,0,2,0),
        r("White lab panels",PANELS,0xd6dade,0x3a82c8,0x9ad8ff,.2f,.3f,.6f,2,1),
        r("Gunship plating",PANELS,0x3a4048,0xc23020,0xff5030,1,.4f,0,3,2).wear(.8f),
        r("Rusted bulkhead",PANELS,0x6a6660,0xb08a30,0xff8040,1,.55f,0,2,0).rust(.7f),
        r("Hex plating",HEX,0x7c838c,0x2a2e34,0x60e0ff,1,.3f,0,6,0),
        r("Hex energy shield",HEX,0x1a2a3a,0x0a1420,0x40c8ff,.6f,.15f,1,8,1),
        r("Hex reactor floor",HEX,0x4a4440,0x1a1816,0xff9030,1,.45f,.8f,5,1).wear(.8f),
        r("Black hex armour",HEX,0x3a3c42,0x16181c,0xff3020,1,.25f,0,10,0),
        r("Air vents",VENT,0x8a8e94,0x2a2c30,0x80d0ff,1,.4f,0,1,0),
        r("Heat vents",VENT,0x50504c,0x1e1c1a,0xff6020,1,.5f,1,2,1).wear(.8f),
        r("Coolant vents",VENT,0xb4bac0,0x3c4248,0x40e8ff,.8f,.3f,.7f,2,1),
        r("Floor grate",GRATE,0x6c6e70,0x101010,0x40c0ff,1,.45f,0,10,0),
        r("Glowing sub-grate",GRATE,0x404448,0x0a0a0c,0x30ff90,1,.4f,1,12,1),
        r("Rusted grate",GRATE,0x5a4a3a,0x0e0a08,0xff7020,1,.7f,0,8,0).rust(.8f),
        r("Circuit board",CIRCUIT,0x1e4a2a,0xd0a040,0x60ff80,.3f,.4f,.6f,24,0),
        r("Blue logic board",CIRCUIT,0x14284a,0xc0c8d0,0x40b0ff,.3f,.35f,.8f,32,0),
        r("Red core circuitry",CIRCUIT,0x2a0c0c,0xd0a060,0xff3020,.4f,.35f,1,28,1),
        r("Tech floor",TECHFLOOR,0x5c6064,0x2a2c30,0x60d0ff,1,.4f,.8f,2,0),
        r("Command deck",TECHFLOOR,0x30343a,0x14161a,0xff9a30,1,.3f,.9f,2,1),
        r("Med-bay floor",TECHFLOOR,0xc8ccd0,0x8a9096,0x50ffb0,.2f,.35f,.6f,2,0),
        r("Hazard stripes",HAZARD,0x6a6c70,0xe0b020,0xffffff,1,.45f,0,6,0).wear(.7f),
        r("Red warning stripes",HAZARD,0x5a5c60,0xc02818,0xffffff,1,.45f,0,5,0).wear(.6f),
        r("Hazard blast door",HAZARD,0x44464a,0xe8a818,0xffffff,1,.4f,0,3,1).wear(.9f),
        r("Pipe bundle",PIPES,0x7a7e84,0xa06a30,0xff8030,1,.35f,0,6,0),
        r("Coolant pipes",PIPES,0xa8b0b8,0x2a70b0,0x40c0ff,1,.3f,0,5,1),
        r("Corroded pipework",PIPES,0x6a5a48,0x406a50,0xff8030,1,.6f,0,7,0).rust(.7f),
        r("Ribbed hull",RIBBED,0x8c9096,0x30343a,0xffffff,1,.35f,0,16,0),
        r("Corrugated steel",RIBBED,0x9a9690,0x403a34,0xffffff,1,.5f,0,24,1).rust(.4f),
        r("Dropship skin",RIBBED,0x50565e,0xc0a020,0xffffff,1,.3f,0,12,1).wear(.8f),
        r("Light strips",LIGHTSTRIP,0x24262a,0x484c52,0xbfe8ff,1,.3f,1,4,0),
        r("Amber running lights",LIGHTSTRIP,0x2a2622,0x504840,0xffb040,1,.35f,1,3,1),
        r("Red alert lights",LIGHTSTRIP,0x221a1a,0x4a3434,0xff2a20,1,.35f,1,5,0),
        r("Server wall",DATA,0x2a2e34,0x0a0c10,0x40e080,1,.3f,1,4,0),
        r("Data core",DATA,0x1c2230,0x080a10,0x40a8ff,1,.25f,1,3,1),
        r("Terminal bank",DATA,0x3a3a36,0x0c0c0a,0xffb030,1,.35f,1,4,1),
        r("Perforated steel",PERFORATED,0x8a8e92,0x0e0e10,0x60c8ff,1,.4f,0,12,0),
        r("Speaker mesh",PERFORATED,0x2a2c30,0x060608,0xff4020,1,.45f,0,24,0),
        r("Backlit perforation",PERFORATED,0x40444a,0x08080a,0x60e0ff,1,.35f,1,14,1),
        r("Heavy armour plates",ARMOR,0x6a7078,0x2a2e34,0xffffff,1,.35f,0,2,0).wear(.7f),
        r("Olive drab armour",ARMOR,0x4a5238,0x23271a,0xffffff,.6f,.55f,0,2,1).wear(.8f),
        r("Titan plating",ARMOR,0xb0b4ba,0x50545a,0xffffff,1,.25f,0,3,0),
        r("Cable run",CABLES,0x2a2a2a,0xb02020,0xffffff,0,.55f,0,10,0),
        r("Power conduits",CABLES,0x1e1e20,0xe0a020,0x40c0ff,.2f,.5f,.6f,8,1),
        r("Fibre optics",CABLES,0x14161a,0x3060c0,0x60ffff,0,.4f,1,14,1),
        r("Observation glass",GLASS,0x0a1218,0x6a7078,0x60a0c0,0,.05f,.25f,2,0),
        r("Tinted bulkhead glass",GLASS,0x10080a,0x3a3a3e,0xc04040,0,.05f,.2f,3,1),
        r("Lit window panels",GLASS,0x0c1418,0x8a9096,0x6aa8d0,0,.08f,.45f,2,1),
        r("Tread plate",TREAD,0x8a8c90,0x404246,0xffffff,1,.35f,0,16,0),
        r("Rubber deck",TREAD,0x34343a,0x18181a,0xffffff,0,.85f,0,20,1),
        r("Worn walkway",TREAD,0x6a6660,0x302c28,0xffffff,1,.5f,0,14,0).rust(.5f).wear(.9f),
        // ------------------------------------------------------------ hell (50)
        r("Raw flesh",FLESH,0x7a1810,0x3a0606,0xff3020,0,.35f,0,6,0),
        r("Pulsing flesh wall",FLESH,0x6a1414,0x2a0408,0xff4020,0,.3f,1,5,1),
        r("Rotting meat",FLESH,0x5a3a20,0x2a1a0a,0x90ff40,0,.45f,.6f,7,0),
        r("Charred flesh",FLESH,0x4a2014,0x0e0604,0xff5010,0,.6f,.7f,8,1),
        r("Bone pile",BONE,0xd8ccb0,0x2a2016,0xff5020,0,.6f,0,6,0),
        r("Charred bones",BONE,0x6a5a48,0x140c08,0xff5020,0,.7f,.5f,7,1),
        r("Ossuary wall",BONE,0xc8bc9c,0x3a2c1c,0xff5020,0,.55f,0,5,1),
        r("Obsidian",OBSIDIAN,0x0e0a10,0x2a1a30,0xff4020,0,.08f,0,8,0),
        r("Bleeding obsidian",OBSIDIAN,0x120608,0x3a0a0a,0xff2a10,0,.08f,1,7,1),
        r("Void glass",OBSIDIAN,0x08080e,0x1a1830,0x9a40ff,0,.06f,1,10,1),
        r("Basalt columns",BASALT,0x3a3632,0x161412,0xff6020,0,.8f,0,6,0),
        r("Smouldering basalt",BASALT,0x2e2a26,0x0e0c0a,0xff5010,0,.8f,1,5,1),
        r("Ashen basalt",BASALT,0x6a645e,0x2a2622,0xff6020,0,.9f,0,7,0),
        r("Rune stone",RUNESTONE,0x4a4440,0x1e1a18,0xff4a10,0,.75f,1,4,0),
        r("Blood runes",RUNESTONE,0x3a2a28,0x140a0a,0xff1a10,0,.7f,1,4,1),
        r("Soul runes",RUNESTONE,0x34383c,0x121416,0x40e0ff,0,.75f,1,3,0),
        r("Scorched brick",SCORCHED,0x5a2a1c,0x140a08,0xff5020,0,.85f,0,8,0),
        r("Burnt masonry",SCORCHED,0x5a5450,0x161210,0xff6020,0,.85f,.4f,5,1),
        r("Soot-black stone",SCORCHED,0x2a2624,0x0a0808,0xff4010,0,.9f,0,6,1),
        r("Hell iron",HELLIRON,0x4a3a34,0x1a1210,0xff4020,1,.55f,0,2,0).rust(.6f),
        r("Spiked iron",HELLIRON,0x3a3634,0x141210,0xff3010,1,.45f,0,3,1).rust(.4f),
        r("Molten iron plates",HELLIRON,0x2e2622,0x100c0a,0xff5010,1,.5f,1,2,1).rust(.3f),
        r("Ash floor",ASH,0x5a5652,0x2a2826,0xff5010,0,.95f,.5f,4,0),
        r("Cinder ground",ASH,0x2a2624,0x100e0c,0xff6020,0,.95f,1,6,1),
        r("Pale dust",ASH,0x9a9088,0x5a524a,0xff5010,0,.95f,0,3,0),
        r("Blood-soaked stone",BLOODSTONE,0x4a4440,0x5a0606,0xff2010,0,.8f,0,6,0),
        r("Gore cobbles",BLOODSTONE,0x3a2c28,0x6a0808,0xff2010,0,.7f,0,8,1),
        r("Sacrificial floor",BLOODSTONE,0x5a5450,0x400404,0xff2010,0,.75f,0,5,1),
        r("Demon scales",SCALES,0x6a2a1c,0x140806,0xff4020,.2f,.35f,0,6,0),
        r("Molten scales",SCALES,0x3a2018,0x0e0606,0xff6010,.2f,.3f,1,5,1),
        r("Serpent hide",SCALES,0x4a6234,0x0e140a,0xa0ff40,.1f,.35f,0,8,0),
        r("Sinew",SINEW,0x8a2a22,0xe0c0a0,0xff3020,0,.35f,0,8,0),
        r("Flayed muscle",SINEW,0x6a1410,0xd8b898,0xff3020,0,.3f,.5f,10,1),
        r("Tendon wall",SINEW,0xa04a3a,0xf0dcc0,0xff3020,0,.4f,0,6,1),
        r("Sigil tiles",SIGIL,0x3a3430,0x161210,0xff3010,0,.6f,1,2,0),
        r("Pentagram floor",SIGIL,0x1e1a1a,0x0a0808,0xff2010,0,.5f,1,1,1),
        r("Gilded sigils",SIGIL,0x2a2420,0xc09030,0xffb040,.6f,.4f,.6f,2,0),
        r("Hell roots",ROOTS,0x6a4028,0x120a06,0xff4020,0,.7f,0,4,0),
        r("Thorn tangle",ROOTS,0x584030,0x0a0604,0xff3010,0,.6f,0,6,1),
        r("Glowing tendrils",ROOTS,0x4a2a44,0x0a040a,0xa030e0,0,.5f,.7f,5,1),
        r("Hellstone cobbles",COBBLE,0x5a3a30,0x1a100c,0xff5020,0,.8f,0,8,0),
        r("Ember cobbles",COBBLE,0x3a302c,0x0e0a08,0xff6010,0,.8f,1,7,1),
        r("Bone-white cobbles",COBBLE,0xb0a898,0x3a342c,0xff5020,0,.75f,0,6,0),
        r("Brimstone",BRIMSTONE,0x2a2420,0xd8c030,0xffd040,0,.8f,0,6,0),
        r("Sulphur vents",BRIMSTONE,0x3a3028,0xe8d040,0xffe060,0,.75f,.6f,5,1),
        r("Acid crust",BRIMSTONE,0x1e2618,0x90e030,0xa0ff40,0,.6f,.8f,7,1),
        r("Magma crust",MAGMA,0x1a1210,0x0a0606,0xff6010,0,.9f,1,5,0),
        r("Hellfire pool",MAGMA,0x100a14,0x060408,0x4080ff,0,.85f,1,6,1),
        r("Bile pit",MAGMA,0x101a0c,0x060a04,0x80ff30,0,.6f,1,4,1),
        r("Blood lake",MAGMA,0x1a0606,0x0a0202,0xd01010,0,.15f,.7f,4,1),
    };
    static final int COUNT=RECIPES.length;
    static String name(int i){return RECIPES[i].name;}
    static int category(int i){return RECIPES[i].pat<FLESH?CAT_SCIFI:CAT_HELL;}
    static boolean flows(int i){return RECIPES[i].pat==MAGMA;}

    // ---------------------------------------------------------------- painting
    private static float[] rgb(int c){return new float[]{lin((c>>16)&255),lin((c>>8)&255),lin(c&255)};}
    private static void col(Paint P,int i,float[] c,float k){P.col(i,c[0]*k,c[1]*k,c[2]*k);}
    private static void colMix(Paint P,int i,float[] a,float[] b,float t,float k){P.col(i,mix(a[0],b[0],t)*k,mix(a[1],b[1],t)*k,mix(a[2],b[2],t)*k);}
    private static float fr(float x){return x-(float)Math.floor(x);}
    private static float hh(int a,int b,int s){return hash(a,b,s);}

    /** Voronoi with separate periods in u and v: {F1, F2, id 0..1, dx, dy} (dx,dy from the texel to its cell point, in cells). */
    static float[] vor(float u,float v,int px,int py,int seed,float jitter){
        float x=u*px,y=v*py;int xi=(int)Math.floor(x),yi=(int)Math.floor(y);float f1=9,f2=9,id=0,dx=0,dy=0;
        for(int j=-1;j<=1;j++)for(int i=-1;i<=1;i++){int cx=xi+i,cy=yi+j,wx=Math.floorMod(cx,px),wy=Math.floorMod(cy,py);
            float qx=cx+.5f+(hash(wx,wy,seed)-.5f)*jitter,qy=cy+.5f+(hash(wx,wy,seed+7)-.5f)*jitter;float d=(float)Math.hypot(qx-x,qy-y);
            if(d<f1){f2=f1;f1=d;id=hash(wx,wy,seed+3);dx=qx-x;dy=qy-y;}else if(d<f2)f2=d;}
        return new float[]{f1,f2,id,dx,dy};
    }
    /** Offset-row lattice Voronoi: hexagons (rows must be even to tile). {F1,F2,id,dx,dy}. */
    static float[] hexes(float u,float v,int cols,int rows){
        float x=u*cols,y=v*rows*.8660254f;int rj=(int)Math.floor(v*rows);float f1=9,f2=9,id=0,dx=0,dy=0;
        for(int j=rj-1;j<=rj+1;j++){int wj=Math.floorMod(j,rows);float off=(wj&1)*.5f;int ci=(int)Math.floor(x-off);
            for(int i=ci-1;i<=ci+1;i++){float qx=i+.5f+off,qy=(j+.5f)*.8660254f;float d=(float)Math.hypot(qx-x,qy-y);
                if(d<f1){f2=f1;f1=d;id=hash(Math.floorMod(i,cols),wj,91);dx=qx-x;dy=qy-y;}else if(d<f2)f2=d;}}
        return new float[]{f1,f2,id,dx,dy};
    }
    /** A rectangle subdivided into plates: {local u, local v, width, height, plate id}. Tiles over the unit square. */
    private static float[] plates(float u,float v,int g,int seed){
        float cw=1f/g,ch=1f/g;int cx=(int)(u*g),cy=(int)(v*g);float x0=cx*cw,y0=cy*ch,w=cw,h=ch;int id=cx*31+cy*17+seed;
        for(int lvl=0;lvl<2;lvl++){float k=hh(id,lvl,seed);
            if(k<.35f){float s=.35f+hh(id,9,seed)*.3f;if((u-x0)<w*s){w*=s;id=id*3+1;}else{x0+=w*s;w*=1-s;id=id*3+2;}}
            else if(k<.7f){float s=.35f+hh(id,8,seed)*.3f;if((v-y0)<h*s){h*=s;id=id*5+1;}else{y0+=h*s;h*=1-s;id=id*5+2;}}}
        return new float[]{(u-x0)/w,(v-y0)/h,w,h,id};
    }
    /** Edge wear: exposed bright metal on raised edges, from a bevel factor (0 at the edge) and noise. */
    private static float edgeWear(float bevel,float u,float v,float amount,int seed){return amount*smooth(.55f,.85f,fbm(u,v,16,4,seed))*(1-smooth(.2f,.9f,bevel));}
    /** Rust over metal (sets colour, roughness, metalness); returns the rust amount. */
    private static float rustOver(Paint P,int i,float u,float v,float amount,int seed){
        if(amount<=0)return 0;float k=smooth(.62f-amount*.25f,.8f-amount*.2f,fbm(u,v,8,5,seed));if(k<=0)return 0;
        float f=fbm(u,v,32,3,seed+5);P.r[i]=mix(P.r[i],.12f+f*.06f,k);P.g[i]=mix(P.g[i],.035f+f*.02f,k);P.b[i]=mix(P.b[i],.012f,k);
        P.rough[i]=mix(P.rough[i],.9f,k);P.metal[i]=mix(P.metal[i],.1f,k);P.h[i]+=k*.03f*(f-.5f);return k;
    }
    private static void glowAt(Paint P,int i,float[] g,float k){if(k<=0)return;P.r[i]=mix(P.r[i],g[0],k);P.g[i]=mix(P.g[i],g[1],k);P.b[i]=mix(P.b[i],g[2],k);P.emit[i]=Math.max(P.emit[i],k);P.rough[i]=mix(P.rough[i],.2f,k);}

    /** Paints recipe i; returns {parallax depth, repeats per metre, normal strength, emission strength}. */
    static float[] paint(Paint P,int idx){
        R R=RECIPES[idx];int n=P.n,seed=101+idx*13;float[] A=rgb(R.base),B=rgb(R.accent),G=rgb(R.glow);
        float pom=.04f,tile=.5f,nk=4,emit=R.emit>0?4.5f*R.emit+1.5f:0;
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){int i=y*n+x;float u=(x+.5f)/n,v=(y+.5f)/n;   // texel centres: edges come out symmetric across the wrap
            P.metal[i]=R.metal;P.rough[i]=R.rough;
            switch(R.pat){
                // ======================================================== sci-fi
                case PANELS:{float[] q=plates(u,v,R.scale,seed);float e=Math.min(Math.min(q[0],1-q[0])*q[2],Math.min(q[1],1-q[1])*q[3]);int id=(int)q[4];
                    float seam=smooth(.004f,.012f,e),bev=smooth(.004f,.03f,e);boolean inset=hh(id,4,seed)>.72f;float inner=smooth(.035f,.05f,e);
                    float h=.25f+bev*.45f+(inset?-.12f*inner:0)+fbm(u,v,64,2,seed)*.02f+(hh(id,5,seed)-.5f)*.04f;
                    float bolt=9;if(q[2]>.2f&&q[3]>.2f)for(float cu:new float[]{.03f,1}){for(float cv:new float[]{.03f,1}){float bx=cu<.5f?.03f:q[2]-.03f,by=cv<.5f?.03f:q[3]-.03f;bolt=Math.min(bolt,(float)Math.hypot(q[0]*q[2]-bx,q[1]*q[3]-by));}}
                    float bk=1-smooth(.007f,.011f,bolt);h+=bk*.12f*(float)Math.sqrt(Math.max(0,1-bolt/.011f));P.h[i]=h;
                    float tone=.85f+hh(id,6,seed)*.25f,grime=smooth(.5f,.85f,fbm(u,v,8,4,seed+1));
                    boolean stripe=hh(id,7,seed)>.8f&&q[1]>.08f&&q[1]<.16f&&R.var!=2;
                    if(stripe)col(P,i,B,1);else col(P,i,A,tone*(1-grime*.35f));
                    float w=edgeWear(bev,u,v,R.wear,seed+2);P.r[i]=mix(P.r[i],.55f,w*R.metal);P.g[i]=mix(P.g[i],.55f,w*R.metal);P.b[i]=mix(P.b[i],.56f,w*R.metal);P.rough[i]=R.rough+grime*.2f-w*.1f;
                    if(R.var==2&&hh(id,7,seed)>.6f)col(P,i,B,.8f+.2f*tone);   // painted plates
                    if(seam<.5f){col(P,i,A,.08f);P.rough[i]=.8f;P.ao[i]=.6f;if(R.emit>0&&hh(id,8,seed)>.55f)glowAt(P,i,G,1);}
                    rustOver(P,i,u,v,R.rust,seed+3);break;}
                case HEX:{float[] c=hexes(u,v,R.scale,R.scale%2==0?R.scale+R.scale/6*2:R.scale+1);float e=c[1]-c[0];int id=(int)(c[2]*9999);
                    float bev=smooth(.04f,.14f,e),h=.3f+bev*.4f+(R.var==1?(.5f-c[0])*.15f:0)+fbm(u,v,64,2,seed)*.02f;P.h[i]=h;
                    float tone=.85f+hh(id,1,seed)*.3f;col(P,i,A,tone);
                    float w=edgeWear(bev,u,v,R.wear,seed);P.r[i]=mix(P.r[i],.5f,w*R.metal);P.g[i]=mix(P.g[i],.5f,w*R.metal);P.b[i]=mix(P.b[i],.52f,w*R.metal);
                    if(e<.04f){col(P,i,B,1);P.rough[i]=.7f;P.ao[i]=.7f;if(R.emit>0)glowAt(P,i,G,R.var==1?1:.0f);}
                    if(R.emit>0&&R.var==1&&c[0]<.12f&&hh(id,2,seed)>.5f)glowAt(P,i,G,1-smooth(.06f,.12f,c[0]));
                    if(R.emit>0&&R.var==0)glowAt(P,i,G,(1-smooth(.05f,.09f,c[0]))*(hh(id,3,seed)>.7f?1:0));
                    break;}
                case VENT:{int g=R.scale;float pu=fr(u*g),pv=fr(v*g);float e=Math.min(Math.min(pu,1-pu),Math.min(pv,1-pv));boolean frame=e<.09f;
                    if(frame){float bev=smooth(0,.05f,e);P.h[i]=.4f+bev*.4f;col(P,i,A,.95f);float w=edgeWear(bev,u,v,R.wear,seed);P.r[i]+=w*.2f;P.g[i]+=w*.2f;P.b[i]+=w*.2f;}
                    else{float s=fr(pv*12);float slat=smooth(.05f,.35f,s)*(1-smooth(.62f,.8f,s));P.h[i]=.08f+slat*.55f-(1-slat)*.05f;
                        if(s>.72f){col(P,i,B,.25f);P.ao[i]=.4f;glowAt(P,i,G,R.emit>0?smooth(.75f,.9f,s)*.9f:0);}else col(P,i,A,.6f+slat*.45f);}
                    rustOver(P,i,u,v,R.rust,seed);break;}
                case GRATE:{int g=R.scale;float pu=fr(u*g),pv=fr(v*g);float bu=Math.min(pu,1-pu),bv=Math.min(pv,1-pv);
                    float barU=1-smooth(.1f,.16f,bu),barV=1-smooth(.1f,.16f,bv);float bar=Math.max(barU,barV);
                    float round=Math.max((float)Math.sqrt(Math.max(0,1-bu/.16f)),(float)Math.sqrt(Math.max(0,1-bv/.16f)));
                    P.h[i]=bar>.5f?.55f+round*.25f+(barU>.5f&&barV>.5f?.08f:0):.02f;
                    if(bar>.5f){col(P,i,A,.8f+round*.3f);float w=edgeWear(1-round,u,v,R.wear,seed);P.r[i]+=w*.15f;P.g[i]+=w*.15f;P.b[i]+=w*.15f;}
                    else{float depth=fbm(u,v,8,3,seed);col(P,i,B,1+depth);P.ao[i]=.3f;P.metal[i]=0;P.rough[i]=.9f;if(R.emit>0)glowAt(P,i,G,.55f+.45f*fbm(u,v,4,3,seed+9));}
                    rustOver(P,i,u,v,R.rust,seed+1);break;}
                case CIRCUIT:{int g=R.scale;int cx=(int)(u*g),cy=(int)(v*g);float pu=fr(u*g)-.5f,pv=fr(v*g)-.5f;
                    boolean right=hh(cx,cy,seed)>.45f,down=hh(cx,cy,seed+1)>.5f,left=hh(Math.floorMod(cx-1,g),cy,seed)>.45f,up=hh(cx,Math.floorMod(cy-1,g),seed+1)>.5f;
                    float w=.09f,t=9;if(right&&pu>=0)t=Math.min(t,Math.abs(pv));if(left&&pu<=0)t=Math.min(t,Math.abs(pv));if(down&&pv>=0)t=Math.min(t,Math.abs(pu));if(up&&pv<=0)t=Math.min(t,Math.abs(pu));
                    boolean node=(right?1:0)+(left?1:0)+(down?1:0)+(up?1:0)==1;float pad=(float)Math.hypot(pu,pv);
                    boolean trace=t<w||(node&&pad<.22f);
                    int bx=(int)(u*(g/4)),by=(int)(v*(g/4));float cu=fr(u*(g/4)),cv=fr(v*(g/4));boolean chip=hh(bx,by,seed+5)>.78f&&cu>.15f&&cu<.85f&&cv>.2f&&cv<.8f;
                    boolean pin=hh(bx,by,seed+5)>.78f&&!chip&&cu>.15f&&cu<.85f&&cv>.12f&&cv<.88f&&fr(cu*12)<.5f;
                    float base=fbm(u,v,32,3,seed)*.04f;
                    if(chip){P.h[i]=.75f;P.col(i,.02f,.02f,.025f);P.rough[i]=.35f;P.metal[i]=0;}
                    else if(pin||trace){P.h[i]=.38f;col(P,i,B,node&&pad<.22f?1.1f:.9f);P.metal[i]=1;P.rough[i]=.3f;if(R.emit>0&&hh(cx,cy,seed+9)>(R.var==1?.4f:.7f))glowAt(P,i,G,.9f);}
                    else{P.h[i]=.3f+base;col(P,i,A,.85f+base*4);P.metal[i]=0;P.rough[i]=R.rough+.2f;}
                    break;}
                case TECHFLOOR:{int g=R.scale;int tx=(int)(u*g),ty=(int)(v*g);float pu=fr(u*g),pv=fr(v*g);float e=Math.min(Math.min(pu,1-pu),Math.min(pv,1-pv));
                    float bev=smooth(.01f,.04f,e);boolean strip=((tx+ty)&1)==0?Math.abs(pv-.5f)<.05f&&pu>.12f&&pu<.88f:Math.abs(pu-.5f)<.05f&&pv>.12f&&pv<.88f;
                    float dots=(fr(pu*14)-.5f)*(fr(pu*14)-.5f)+(fr(pv*14)-.5f)*(fr(pv*14)-.5f);float dot=1-smooth(.03f,.06f,dots);
                    P.h[i]=.2f+bev*(.5f+dot*.06f)-(strip?.08f:0);
                    float grime=smooth(.55f,.85f,fbm(u,v,8,4,seed));col(P,i,A,(.9f+hh(tx,ty,seed)*.15f)*(1-grime*.3f));P.rough[i]=R.rough+grime*.2f-dot*.1f;
                    if(e<.01f){col(P,i,B,.5f);P.ao[i]=.6f;}
                    if(strip){col(P,i,B,.6f);if(R.emit>0)glowAt(P,i,G,.95f);}
                    break;}
                case HAZARD:{int g=R.scale;float d=fr((u+v)*g);boolean stripe=d<.5f;float e=Math.min(Math.min(u,1-u),Math.min(v,1-v));
                    float bev=smooth(.0f,.03f,e);float chip=smooth(.5f,.7f,fbm(u,v,16,5,seed))*R.wear;
                    P.h[i]=.4f+bev*.2f+(stripe?.015f:0)+fbm(u,v,64,2,seed)*.02f-chip*.02f;
                    if(R.var==1){float pu=fr(u*2),pv=fr(v*2);float pe=Math.min(Math.min(pu,1-pu),Math.min(pv,1-pv));if(pe<.01f){P.h[i]=.25f;}}
                    if(stripe)col(P,i,B,1);else P.col(i,.03f,.03f,.03f);
                    if(chip>.4f){col(P,i,A,1);P.rough[i]=R.rough-.1f;}else{P.metal[i]=0;P.rough[i]=.55f;}
                    if(e<.008f){col(P,i,A,.3f);}
                    rustOver(P,i,u,v,R.rust,seed);break;}
                case PIPES:{int g=R.scale;float x2=u*g;int slot=(int)x2;float fx=x2-slot;float rad=.36f+hh(slot,1,seed)*.12f;float d=Math.abs(fx-.5f);
                    if(d<rad){float prof=(float)Math.sqrt(1-(d/rad)*(d/rad));int bands=2+(int)(hh(slot,2,seed)*3);float bv=fr(v*bands+hh(slot,3,seed));
                        boolean clamp=bv<.06f;P.h[i]=.25f+prof*.6f+(clamp?.08f:0);
                        boolean painted=hh(slot,4,seed)>.6f;col(P,i,painted?B:A,(.55f+prof*.55f)*(clamp?.8f:1));if(painted)P.metal[i]=.2f;
                        P.rough[i]=R.rough+(1-prof)*.15f;float w=edgeWear(1-prof,u,v,R.wear*.6f,seed);P.r[i]+=w*.12f;P.g[i]+=w*.12f;P.b[i]+=w*.12f;}
                    else{P.h[i]=.05f;col(P,i,A,.1f);P.ao[i]=.4f;}
                    rustOver(P,i,u,v,R.rust,seed);break;}
                case RIBBED:{int g=R.scale;float rib=(float)Math.abs(Math.sin(u*g*Math.PI));float seamV=Math.min(fr(v*2),1-fr(v*2));
                    float seam=smooth(.006f,.02f,seamV);float rivet=0;if(seamV<.05f){float ru=fr(u*g);float rd=(float)Math.hypot(ru-.5f,(seamV-.03f)*g*.5f);rivet=1-smooth(.12f,.18f,rd);}
                    P.h[i]=.3f+(float)Math.pow(rib,.6f)*.35f*seam+rivet*.15f;
                    col(P,i,A,(.7f+rib*.35f)*(seam<.5f?.3f:1));
                    if(R.var==1&&seamV>.4f&&seamV<.46f)col(P,i,B,1);
                    float w=edgeWear(1-rib,u,v,R.wear,seed);P.r[i]+=w*.15f;P.g[i]+=w*.15f;P.b[i]+=w*.15f;rustOver(P,i,u,v,R.rust,seed);break;}
                case LIGHTSTRIP:{int g=R.scale;float pv=fr(v*g);float d=Math.abs(pv-.5f);float e=Math.min(Math.min(fr(u*2),1-fr(u*2)),Math.min(pv,1-pv));
                    boolean housing=d<.12f,lamp=d<.06f;float seam=smooth(.004f,.012f,Math.min(fr(u*2),1-fr(u*2)));
                    P.h[i]=lamp?.5f:housing?.62f:.35f+fbm(u,v,32,3,seed)*.04f;
                    if(lamp){P.col(i,.9f,.9f,.9f);glowAt(P,i,G,1);if(R.var==1&&fr(u*16)<.15f){P.emit[i]=0;col(P,i,B,.3f);}}
                    else if(housing)col(P,i,B,1);else col(P,i,A,.9f+fbm(u,v,16,3,seed)*.3f);
                    if(seam<.5f){col(P,i,A,.2f);P.h[i]=.25f;}
                    break;}
                case DATA:{int g=R.scale;int mx=(int)(u*g),my=(int)(v*g*2);float pu=fr(u*g),pv=fr(v*g*2);float e=Math.min(Math.min(pu,1-pu),Math.min(pv,1-pv));
                    boolean screen=pu>.1f&&pu<.9f&&pv>.15f&&pv<.62f;boolean leds=pv>.72f&&pv<.82f&&pu>.1f&&pu<.9f;
                    P.h[i]=e<.03f?.3f:screen?.35f:.55f;
                    if(screen){col(P,i,B,1);P.rough[i]=.1f;P.metal[i]=0;int gx=(int)((pu-.1f)/.8f*16),gy=(int)((pv-.15f)/.47f*8);
                        boolean lit=hh(gx+mx*16,gy+my*8,seed)>(R.var==1?.45f:.6f)&&fr((pu-.1f)/.8f*16)<.8f&&fr((pv-.15f)/.47f*8)<.7f;if(lit)glowAt(P,i,G,.6f+.4f*hh(gx,gy,seed+mx));}
                    else if(leds){float lu=fr(pu*10);boolean on=lu>.3f&&lu<.6f;if(on){P.h[i]=.6f;if(hh((int)(pu*10)+mx*10,my,seed+3)>.4f)glowAt(P,i,G,1);else col(P,i,B,2);}else col(P,i,A,.8f);}
                    else col(P,i,A,.85f+fbm(u,v,32,3,seed)*.2f);
                    break;}
                case PERFORATED:{int g=R.scale;float row=v*g;int ry=(int)row;float pu=fr(u*g+(ry&1)*.5f),pv=fr(row);float d=(float)Math.hypot(pu-.5f,pv-.5f);
                    float hole=1-smooth(.27f,.31f,d),ring=smooth(.3f,.36f,d);
                    P.h[i]=hole>.5f?.0f:.45f+ring*.05f+fbm(u,v,32,2,seed)*.02f;
                    if(hole>.5f){col(P,i,B,1);P.ao[i]=.3f;P.metal[i]=0;if(R.emit>0)glowAt(P,i,G,.9f);}
                    else{col(P,i,A,.85f+fbm(u,v,8,3,seed)*.25f);float w=edgeWear(ring,u,v,R.wear,seed);P.r[i]+=w*.15f;P.g[i]+=w*.15f;P.b[i]+=w*.15f;}
                    break;}
                case ARMOR:{float[] q=plates(u,v,R.scale,seed+50);float lu=q[0]*q[2],lv=q[1]*q[3];float e=Math.min(Math.min(lu,q[2]-lu),Math.min(lv,q[3]-lv));
                    float cut=.06f;float ch=Math.min(Math.min(lu+lv,(q[2]-lu)+lv),Math.min(lu+(q[3]-lv),(q[2]-lu)+(q[3]-lv)))-cut;e=Math.min(e,ch*.7071f);int id=(int)q[4];
                    float bev=smooth(.004f,.045f,e);P.h[i]=e<.004f?.1f:.3f+bev*.5f+(hh(id,1,seed)-.5f)*.06f+fbm(u,v,32,3,seed)*.03f;
                    float rv=9;for(float t=.12f;t<1;t+=.25f){rv=Math.min(rv,(float)Math.hypot(lu-.022f,lv-q[3]*t));rv=Math.min(rv,(float)Math.hypot(lu-q[2]*t,lv-.022f));}
                    float rk=1-smooth(.008f,.012f,rv);P.h[i]+=rk*.12f;
                    col(P,i,A,(.85f+hh(id,2,seed)*.25f)*(e<.004f?.15f:1));float w=edgeWear(bev,u,v,R.wear,seed);P.r[i]=mix(P.r[i],.5f,w*R.metal);P.g[i]=mix(P.g[i],.5f,w*R.metal);P.b[i]=mix(P.b[i],.5f,w*R.metal);
                    if(R.var==1){float camo=fbm(u,v,4,4,seed+9);if(camo>.55f&&e>.004f)col(P,i,B,.9f);}
                    rustOver(P,i,u,v,R.rust,seed);break;}
                case CABLES:{int g=R.scale;float row=v*g;int k=(int)row;float pv=fr(row);float rad=.32f+hh(k,1,seed)*.16f;float off=(hh(k,2,seed)-.5f)*.1f;float d=Math.abs(pv-.5f-off);
                    if(d<rad){float prof=(float)Math.sqrt(1-(d/rad)*(d/rad));float tie=fr(u*3+hh(k,3,seed));boolean tied=tie<.03f;
                        P.h[i]=.2f+prof*.6f+(tied?.06f:0);float pick=hh(k,4,seed);
                        if(R.var==1&&pick>.75f){P.h[i]=.2f+prof*.4f;col(P,i,A,.6f+prof*.4f);glowAt(P,i,G,smooth(.4f,.9f,prof)*.8f);}
                        else if(R.emit>0&&R.var==1&&pick>.5f){col(P,i,B,.4f+prof*.6f);glowAt(P,i,G,smooth(.75f,1f,prof)*.7f);}
                        else col(P,i,pick>.55f?B:A,(.5f+prof*.6f)*(tied?.5f:1));
                        P.metal[i]=tied?1:R.metal;P.rough[i]=R.rough-prof*.15f;}
                    else{P.h[i]=.05f;col(P,i,A,.2f);P.ao[i]=.4f;}
                    break;}
                case GLASS:{int g=R.scale;float pu=fr(u*g),pv=fr(v*g);float e=Math.min(Math.min(pu,1-pu),Math.min(pv,1-pv));boolean frame=e<.06f;
                    if(frame){float bev=smooth(0,.04f,e);P.h[i]=.4f+bev*.4f;col(P,i,B,.8f+bev*.3f);P.metal[i]=1;P.rough[i]=.35f;}
                    else{P.h[i]=.3f;col(P,i,A,1);float streak=fbm(u,v,8,3,seed)*.5f+(float)Math.max(0,Math.sin((pu+pv*.6f)*9))*.3f;
                        glowAt(P,i,G,R.emit*(R.var==1?.7f+.3f*fbm(u,v,4,3,seed):streak*.6f));P.rough[i]=R.rough+fbm(u,v,32,3,seed)*.05f;}
                    break;}
                case TREAD:{int g=R.scale;int cx=(int)(u*g),cy=(int)(v*g);float pu=fr(u*g)-.5f,pv=fr(v*g)-.5f;boolean alt=((cx+cy)&1)==0;
                    float a=alt?pu:pv,b=alt?pv:pu;float d=R.var==1?(float)Math.hypot(pu,pv)/.3f:Math.abs(a)/.12f+Math.abs(b)/.38f;float bump=1-smooth(.75f,1,d);
                    float dirt=smooth(.5f,.8f,fbm(u,v,8,4,seed));P.h[i]=.3f+bump*.35f+fbm(u,v,64,2,seed)*.02f;
                    col(P,i,A,(.8f+bump*.3f)*(1-dirt*.4f*(1-bump)));float w=edgeWear(1-bump,u,v,R.wear,seed);P.r[i]+=w*.2f*R.metal;P.g[i]+=w*.2f*R.metal;P.b[i]+=w*.2f*R.metal;
                    P.rough[i]=R.rough+dirt*.2f-bump*.1f;rustOver(P,i,u,v,R.rust,seed);break;}
                // ======================================================== hell
                case FLESH:{float[] c=vor(u,v,R.scale,R.scale,seed,.9f);float lump=1-smooth(0,.75f,c[0]);float fold=smooth(0,.15f,c[1]-c[0]);
                    float wu=u+fbm(u,v,4,3,seed)*.1f,wv=v+fbm(u,v,4,3,seed+1)*.1f;float vein=1-Math.abs(fbm(fr(wu),fr(wv),6,4,seed+2)*2-1);vein=smooth(.88f,.97f,vein);
                    P.h[i]=.25f+lump*.4f*fold+vein*.12f+fbm(u,v,32,3,seed)*.05f;
                    float t=.75f+lump*.35f;colMix(P,i,A,B,(1-fold)*.8f,t);if(vein>0){colMix(P,i,new float[]{P.r[i],P.g[i],P.b[i]},B,vein*.8f,1);P.b[i]+=vein*.05f;}
                    P.rough[i]=mix(R.rough,.15f,smooth(.4f,.8f,fbm(u,v,8,3,seed+4)));if(R.emit>0)glowAt(P,i,G,vein*(.5f+.5f*fbm(u,v,4,3,seed+6)));
                    break;}
                case BONE:{float[] c=vor(u,v,R.scale/2,R.scale*2,seed,.8f);float e=c[1]-c[0];float body=smooth(.05f,.35f,e);
                    float knob=1-smooth(.0f,.25f,Math.abs(Math.abs(c[4])-.3f));P.h[i]=e<.05f?.05f:.3f+(float)Math.sqrt(body)*.45f+knob*.06f+fbm(u,v,32,3,seed)*.04f;
                    float stain=fbm(u,v,8,4,seed+1);col(P,i,A,(.75f+c[2]*.3f)*(1-stain*.4f));P.g[i]*=.97f;P.b[i]*=.9f-stain*.2f;
                    if(e<.05f){col(P,i,B,1);P.ao[i]=.35f;if(R.emit>0)glowAt(P,i,G,(1-smooth(0,.05f,e))*.8f);}
                    if(R.var==1){float ch=smooth(.4f,.7f,fbm(u,v,16,4,seed+3));P.r[i]*=1-ch*.7f;P.g[i]*=1-ch*.75f;P.b[i]*=1-ch*.8f;}
                    if(R.var==1&&c[2]>.85f){float hole=(float)Math.hypot(c[3],c[4]*.6f);if(hole<.18f){P.h[i]=.08f;col(P,i,B,1);}}   // eye sockets in the pile
                    break;}
                case OBSIDIAN:{float[] c=vor(u,v,R.scale,R.scale,seed,1);float e=c[1]-c[0];float ha=(hh((int)(c[2]*999),1,seed)-.5f)*.9f,hb=(hh((int)(c[2]*999),2,seed)-.5f)*.9f;
                    P.h[i]=Math.max(.05f,.55f+c[3]*ha+c[4]*hb-(1-smooth(0,.04f,e))*.4f);
                    col(P,i,A,1+c[2]*.6f);float sheen=smooth(.3f,.9f,fbm(u,v,8,3,seed));colMix(P,i,new float[]{P.r[i],P.g[i],P.b[i]},B,sheen*.5f,1);
                    if(e<.04f){col(P,i,B,.5f);P.rough[i]=.6f;if(R.emit>0)glowAt(P,i,G,(1-smooth(0,.04f,e))*(.6f+.4f*fbm(u,v,8,3,seed+2)));}
                    break;}
                case BASALT:{float[] c=hexes(u,v,R.scale,R.scale+R.scale/3*2+(R.scale%2));float e=c[1]-c[0];float top=smooth(.04f,.12f,e);
                    P.h[i]=e<.04f?.05f+e:.4f+top*.3f+fbm(u,v,32,4,seed)*.08f+(c[2]-.5f)*.08f;
                    float lichen=smooth(.6f,.8f,fbm(u,v,16,4,seed+1));col(P,i,A,(.8f+c[2]*.35f)*(1-lichen*.3f));
                    if(e<.04f){col(P,i,B,1);P.ao[i]=.4f;if(R.emit>0)glowAt(P,i,G,(1-smooth(0,.04f,e))*(.4f+.6f*fbm(u,v,8,3,seed+2)));}
                    break;}
                case RUNESTONE:{int rows=R.scale;float row=v*rows;int ry=(int)row;float pv=fr(row);int per=2+(int)(hh(ry,1,seed)*2);float bu=u*per+hh(ry,2,seed);int bx=(int)Math.floor(bu);float pu=fr(bu);
                    float e=Math.min(Math.min(pu/rows*per,(1-pu)/rows*per),Math.min(pv,1-pv)/rows);float bev=smooth(.004f,.02f,e);int id=Math.floorMod(bx,per)*13+ry;
                    float rune=9;float gx=(pu-.5f)*1.6f*per/2,gy=(pv-.5f)*1.6f;for(int sI=0;sI<4;sI++){int a=(int)(hh(id,sI,seed)*9),b=(int)(hh(id,sI+9,seed)*9);if(a==b)continue;
                        float ax=(a%3-1)*.35f,ay=(a/3-1)*.35f,bxx=(b%3-1)*.35f,byy=(b/3-1)*.35f;rune=Math.min(rune,segD(gx,gy,ax,ay,bxx,byy));}
                    boolean carved=hh(id,20,seed)>.35f&&rune<.045f&&Math.abs(gx)<.6f&&Math.abs(gy)<.6f;
                    P.h[i]=e<.004f?.1f:.3f+bev*.4f+fbm(u,v,16,4,seed)*.08f-(carved?.12f:0);
                    col(P,i,A,(.75f+hh(id,3,seed)*.4f)*(1-smooth(.55f,.85f,fbm(u,v,8,4,seed+1))*.4f));
                    if(e<.004f){col(P,i,B,1);P.ao[i]=.5f;}
                    if(carved){col(P,i,B,1);if(R.emit>0)glowAt(P,i,G,1-smooth(.02f,.045f,rune));}
                    break;}
                case SCORCHED:{int rows=R.scale+(R.scale&1);float row=v*rows;int ry=(int)row;float pv=fr(row);float bu=u*(rows/2)+(ry&1)*.5f;int bx=(int)Math.floor(bu);float pu=fr(bu);
                    float e=Math.min(Math.min(pu,1-pu)*.5f,Math.min(pv,1-pv));float mort=R.var==1?.02f:.06f;float bev=smooth(mort,mort+.12f,e);int id=Math.floorMod(bx,rows/2)*7+ry;
                    float chip=fbm(u,v,16,4,seed+id)>.65f?.18f:0;P.h[i]=e<mort?.15f:.3f+bev*.4f-chip*bev+fbm(u,v,32,3,seed)*.06f;
                    float soot=smooth(.3f,.9f,fbm(u,v,4,4,seed+1)*.6f+fbm(u,v,8,3,seed+2)*.5f);
                    col(P,i,A,(.75f+hh(id,1,seed)*.4f)*(1-soot*.75f));if(e<mort)col(P,i,B,1);
                    if(R.emit>0)glowAt(P,i,G,smooth(.68f,.88f,fbm(u,v,8,4,seed+3))*(e<mort?1:.4f)*R.emit);
                    break;}
                case HELLIRON:{float[] q=plates(u,v,R.scale,seed+70);float lu=q[0]*q[2],lv=q[1]*q[3];float e=Math.min(Math.min(lu,q[2]-lu),Math.min(lv,q[3]-lv));int id=(int)q[4];
                    float bev=smooth(.004f,.03f,e);P.h[i]=e<.004f?.08f:.3f+bev*.35f+fbm(u,v,16,4,seed)*.06f;
                    float sp=9;int sg=R.scale*4;float su=fr(u*sg),sv=fr(v*sg);sp=(float)Math.hypot(su-.5f,sv-.5f);boolean spike=R.var==1&&sp<.22f&&(((int)(u*sg)+(int)(v*sg))&1)==0;
                    if(spike){P.h[i]=.65f+(.22f-sp)*2.4f;}
                    col(P,i,A,(.8f+hh(id,1,seed)*.3f)*(e<.004f?.2f:1));float w=edgeWear(bev,u,v,R.wear,seed);P.r[i]+=w*.15f;P.g[i]+=w*.12f;P.b[i]+=w*.1f;
                    if(spike){col(P,i,A,.6f+(.22f-sp)*3);P.rough[i]=.3f;}
                    rustOver(P,i,u,v,R.rust,seed);
                    if(R.emit>0&&e<.006f)glowAt(P,i,G,1);if(R.emit>0)glowAt(P,i,G,smooth(.8f,.9f,fbm(u,v,8,4,seed+5))*.8f);
                    break;}
                case ASH:{float d=fbm(u,v,R.scale,6,seed);float ripple=(float)Math.sin((u*R.scale*4+d*6)*6.283f)*.5f+.5f;
                    P.h[i]=.3f+d*.35f+ripple*.04f;col(P,i,A,.7f+d*.5f);float dark=smooth(.55f,.8f,fbm(u,v,8,4,seed+1));colMix(P,i,new float[]{P.r[i],P.g[i],P.b[i]},B,dark*.7f,1);
                    float speck=hash(x,y,seed)>.993f?1:0;if(R.emit>0)glowAt(P,i,G,Math.max(speck,smooth(.85f,.95f,fbm(u,v,16,4,seed+2))*(R.var==1?1:.5f)));
                    break;}
                case BLOODSTONE:{float[] c=vor(u,v,R.scale,R.scale,seed,.85f);float e=c[1]-c[0];float stone=(float)Math.sqrt(Math.max(0,smooth(.02f,.3f,e)));
                    float pool=smooth(.45f,.6f,fbm(u,v,4,4,seed+1))*(R.var==1?1.4f:1);boolean wet=e<.08f+pool*.25f;
                    P.h[i]=wet?.18f:.2f+stone*.5f+fbm(u,v,32,3,seed)*.06f;
                    col(P,i,A,.7f+c[2]*.4f);if(wet){col(P,i,B,.8f+fbm(u,v,16,3,seed)*.3f);P.rough[i]=.08f;P.ao[i]=.8f;}
                    else{float splat=smooth(.62f,.7f,fbm(u,v,16,4,seed+4));colMix(P,i,new float[]{P.r[i],P.g[i],P.b[i]},B,splat*.8f,1);P.rough[i]=mix(R.rough,.3f,splat);}
                    break;}
                case SCALES:{int g=R.scale;float yy=v*g*2,xx=u*g;int k0=(int)Math.floor(yy);float best=-1,bd=0,bx=0;int bk=0;
                    for(int k=k0+1;k>=k0-1;k--){float cy=k,cx=(float)Math.floor(xx-(Math.floorMod(k,2))*.5f)+.5f+(Math.floorMod(k,2))*.5f;
                        for(int o=-1;o<=1;o++){float dx=xx-(cx+o),dy=(yy-cy)*.5f;float d=(float)Math.hypot(dx,dy*1.15f);if(d<.62f){best=1-d/.62f;bd=dy;bx=dx;bk=k;break;}}
                        if(best>=0)break;}
                    if(best<0){P.h[i]=.05f;col(P,i,B,1);P.ao[i]=.4f;}
                    else{float ridge=1-smooth(0,.06f,Math.abs(bx));float lift=smooth(-.3f,.25f,bd);
                        P.h[i]=.15f+(float)Math.sqrt(best)*.45f+lift*.15f+ridge*.04f;
                        float tone=.65f+hh(bk,(int)Math.floor(xx),seed)*.2f;col(P,i,A,tone*(.6f+best*.7f)+ridge*.1f);
                        float rim=1-smooth(.0f,.18f,best);colMix(P,i,new float[]{P.r[i],P.g[i],P.b[i]},B,rim*.85f,1);
                        if(R.emit>0)glowAt(P,i,G,rim*rim*.95f);P.rough[i]=R.rough+(1-best)*.2f;P.metal[i]=R.metal*(1-rim);}
                    break;}
                case SINEW:{float wu=u+fbm(u,v,4,3,seed)*.05f;float fib=fbm(fr(wu)*1,v,R.scale,1,seed+1);float fibre=(float)Math.abs(Math.sin((wu*R.scale*6+fbm(u,v,2,3,seed+2)*3)*Math.PI));
                    float band=smooth(.95f,.985f,(float)Math.abs(Math.sin((v*2+fbm(u,v,2,2,seed+3)*.5f)*Math.PI)))*(R.var==1?1:.6f);
                    P.h[i]=.3f+fibre*.25f+band*.15f+fib*.05f;colMix(P,i,A,B,band,.6f+fibre*.5f);P.rough[i]=mix(R.rough,.2f,smooth(.5f,.8f,fbm(u,v,8,3,seed+4)));
                    if(R.emit>0)glowAt(P,i,G,smooth(.9f,.98f,1-fibre)*.6f*(1-band));
                    break;}
                case SIGIL:{int g=R.scale;float pu=fr(u*g),pv=fr(v*g);float e=Math.min(Math.min(pu,1-pu),Math.min(pv,1-pv));float bev=smooth(.005f,.03f,e);
                    float px2=pu-.5f,py2=pv-.5f;float rr=(float)Math.hypot(px2,py2);float ring=Math.min(Math.abs(rr-.38f),Math.abs(rr-.33f));
                    float star=9;for(int k=0;k<5;k++){double a0=-Math.PI/2+k*2*Math.PI/5,a1=-Math.PI/2+((k+2)%5)*2*Math.PI/5;star=Math.min(star,segD(px2,py2,(float)Math.cos(a0)*.33f,(float)Math.sin(a0)*.33f,(float)Math.cos(a1)*.33f,(float)Math.sin(a1)*.33f));}
                    float line=Math.min(ring,star);boolean carved=line<.012f;
                    P.h[i]=e<.005f?.1f:.35f+bev*.3f+fbm(u,v,16,4,seed)*.06f-(carved?.12f:0);
                    col(P,i,A,(.8f+fbm(u,v,8,3,seed+1)*.3f)*(e<.005f?.3f:1));
                    if(carved){col(P,i,B,1);P.metal[i]=R.metal;P.rough[i]=.3f;if(R.emit>0)glowAt(P,i,G,(1-smooth(.004f,.012f,line))*R.emit);}
                    break;}
                case ROOTS:{float best=0;float hgt=0;for(int k=0;k<3;k++){float wu=u+fbm(u,v,3,3,seed+k*7)*.25f,wv=v+fbm(u,v,3,3,seed+k*7+1)*.25f;
                        float ridge=1-Math.abs(fbm(fr(wu),fr(wv),R.scale,3,seed+k*11)*2-1);float w=smooth(.86f-k*.03f,.97f,ridge);if(w>best){best=w;hgt=w*(1-k*.2f);}}
                    float thorn=R.var==1&&hash(x/3,y/3,seed)>.985f&&best>.3f?.3f:0;
                    P.h[i]=.15f+hgt*.6f+thorn+fbm(u,v,32,3,seed)*.04f;
                    if(best>.15f){col(P,i,A,.6f+best*.6f);P.rough[i]=R.rough;if(R.emit>0)glowAt(P,i,G,smooth(.95f,1f,best)*.8f);}
                    else{col(P,i,B,1+fbm(u,v,16,3,seed)*.6f);P.ao[i]=.6f;P.rough[i]=.95f;}
                    break;}
                case COBBLE:{float[] c=vor(u,v,R.scale,R.scale,seed,.8f);float e=c[1]-c[0];float stone=(float)Math.sqrt(Math.max(0,smooth(.03f,.35f,e)));
                    P.h[i]=e<.03f?.08f:.2f+stone*.55f+fbm(u,v,32,3,seed)*.06f;col(P,i,A,.7f+c[2]*.45f);
                    float moss=smooth(.6f,.8f,fbm(u,v,8,4,seed+1));P.r[i]*=1-moss*.3f;
                    if(e<.03f){col(P,i,B,1);P.ao[i]=.4f;if(R.emit>0)glowAt(P,i,G,(1-smooth(0,.03f,e))*(.5f+.5f*fbm(u,v,8,3,seed+2)));}
                    break;}
                case BRIMSTONE:{float[] c=vor(u,v,R.scale*2,R.scale*2,seed,1);float crust=smooth(.5f,.62f,fbm(u,v,4,5,seed));float facet=1-smooth(0,.6f,c[0]);
                    P.h[i]=.3f+fbm(u,v,16,4,seed+1)*.2f+crust*(.15f+facet*.2f);
                    if(crust>.5f){col(P,i,B,.6f+facet*.6f);P.rough[i]=.5f;if(R.emit>0)glowAt(P,i,G,smooth(.75f,1f,facet)*.6f*R.emit);}else col(P,i,A,.7f+fbm(u,v,8,3,seed+2)*.5f);
                    if(R.var==1&&crust<.3f&&fbm(u,v,16,3,seed+3)>.7f){P.h[i]-=.1f;col(P,i,A,.2f);}
                    break;}
                case MAGMA:{float wu=u+fbm(u,v,4,3,seed)*.06f,wv=v+fbm(u,v,4,3,seed+1)*.06f;float[] c=vor(fr(wu),fr(wv),R.scale,R.scale,seed,.9f);
                    float gap=1-smooth(0,R.var==1?.22f:.12f,c[1]-c[0]);float f=fbm(u,v,16,4,seed+2);float heat=gap*(.6f+f*.7f);
                    P.h[i]=.65f-gap*.55f+f*.12f+(1-c[0])*.05f;col(P,i,A,.8f+f*.5f);
                    float[] hot={Math.min(1,G[0]*1.2f+.1f),Math.min(1,G[1]*1.2f+.1f),Math.min(1,G[2]*1.2f+.1f)};
                    if(heat>.02f){P.r[i]=mix(P.r[i],mix(G[0],hot[0],heat),heat);P.g[i]=mix(P.g[i],mix(G[1]*.6f,hot[1],heat*heat),heat);P.b[i]=mix(P.b[i],mix(G[2]*.6f,hot[2],heat*heat),heat);P.emit[i]=clamp(heat);P.rough[i]=mix(R.rough,.4f,heat);}
                    break;}
            }
        }
        float[] pp=params(R.pat);pom=pp[0];tile=pp[1];nk=pp[2];
        return new float[]{pom,tile,nk,emit};
    }
    /**
     * Parallax depth, repeats per metre and normal strength for a pattern. The repeat sizes put each pattern's
     * features at real-world scale next to the classic textures (e.g. brick courses ~30 cm, floor tiles ~1 m).
     */
    static float[] params(int pat){float[] pp={.04f,.5f,4};
        switch(pat){
            case PANELS:case HEX:case ARMOR:case HELLIRON:pp[0]=.035f;pp[1]=.5f;pp[2]=4;break;
            case VENT:case GRATE:case PERFORATED:pp[0]=.05f;pp[1]=pat==VENT?.5f:.6f;pp[2]=5;break;
            case CIRCUIT:case DATA:case LIGHTSTRIP:pp[0]=.02f;pp[1]=.5f;pp[2]=3;break;
            case TECHFLOOR:case HAZARD:case TREAD:pp[0]=.03f;pp[1]=.5f;pp[2]=4;break;
            case PIPES:case CABLES:case RIBBED:pp[0]=.06f;pp[1]=.5f;pp[2]=5;break;
            case GLASS:pp[0]=.01f;pp[1]=.5f;pp[2]=2;break;
            case FLESH:case SINEW:case SCALES:pp[0]=.05f;pp[1]=.45f;pp[2]=5;break;
            case BONE:case ROOTS:pp[0]=.07f;pp[1]=.45f;pp[2]=6;break;
            case OBSIDIAN:pp[0]=.03f;pp[1]=.4f;pp[2]=5;break;
            case BASALT:case COBBLE:case BLOODSTONE:case RUNESTONE:case SCORCHED:case SIGIL:pp[0]=.06f;pp[1]=.4f;pp[2]=5;break;
            case ASH:case BRIMSTONE:pp[0]=.04f;pp[1]=.35f;pp[2]=4;break;
            case MAGMA:pp[0]=.05f;pp[1]=.35f;pp[2]=5;break;
        }
        return pp;
    }
    /** Repeats per metre of library material i (what the level builder uses for UVs). */
    static float tile(int i){return params(RECIPES[i].pat)[1];}
    /** The colour its glow casts (sRGB hex) and how strongly (0..1). */
    static int glowColor(int i){return RECIPES[i].glow;}
    static float glowStrength(int i){return RECIPES[i].emit;}
    /** The magma family burns like lava. */
    static boolean hurts(int i){return RECIPES[i].pat==MAGMA;}
    /** Distance from (px,py) to the segment a-b. */
    static float segD(float px,float py,float ax,float ay,float bx,float by){
        float dx=bx-ax,dy=by-ay,l=dx*dx+dy*dy;float t=l<1e-6f?0:Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/l));return (float)Math.hypot(px-ax-dx*t,py-ay-dy*t);
    }
}
