package com.harlequin.slayer;

/** GLSL ES 3.00 shaders. */
final class Shaders {
    private Shaders(){}
    static final int MAX_LIGHTS=16;

    /** Lit surfaces (level and 3D models): position, normal, tangent, uv. */
    static final String VS_PBR=
        "#version 300 es\n"+
        "layout(location=0) in vec3 aPos;layout(location=1) in vec3 aNrm;layout(location=2) in vec3 aTan;layout(location=3) in vec2 aUv;\n"+
        "uniform mat4 uVP;uniform mat4 uModel;uniform mat3 uNrmM;uniform vec2 uUvScroll;\n"+
        "out vec3 vPos;out vec3 vN;out vec3 vT;out vec2 vUv;\n"+
        "void main(){vec4 w=uModel*vec4(aPos,1.0);vPos=w.xyz;vN=uNrmM*aNrm;vT=uNrmM*aTan;vUv=aUv+uUvScroll;gl_Position=uVP*w;}\n";

    /**
     * Physically based shading (GGX / Smith / Schlick) of albedo, normal, height, ambient occlusion, roughness,
     * metalness and emission maps. Parallax occlusion mapping: a view-angle-adaptive ray march through the height
     * map with a binary-search refinement, then soft self-shadows traced toward the two strongest lights, so bricks
     * and plates cast shadows into their own grooves. Lights are masked per cell by the level's baked visibility.
     * ACES tone mapping, exponential fog, gamma.
     */
    static final String FS_PBR=
        "#version 300 es\n"+
        "precision highp float;precision highp int;\n"+
        "uniform sampler2D uA;uniform sampler2D uB;uniform sampler2D uC;uniform sampler2D uMask;\n"+
        "uniform vec3 uCam;uniform float uPom;uniform float uEmit;uniform float uEnvK;uniform float uTime;uniform float uExposure;\n"+
        "uniform vec4 uL[16];uniform vec3 uLC[16];uniform int uLCount;uniform ivec2 uGrid;\n"+
        "uniform vec3 uSky;uniform vec3 uGround;uniform vec4 uFog;uniform float uPulse;uniform float uShadowK;\n"+
        "in vec3 vPos;in vec3 vN;in vec3 vT;in vec2 vUv;out vec4 fragColor;\n"+
        "const float PI=3.14159265;\n"+
        "vec3 aces(vec3 x){return clamp((x*(2.51*x+0.03))/(x*(2.43*x+0.59)+0.14),0.0,1.0);}\n"+
        "void main(){\n"+
        " vec3 N=normalize(vN);vec3 T=normalize(vT-N*dot(vT,N));vec3 B=cross(N,T);\n"+
        " vec3 toCam=uCam-vPos;float dist=length(toCam);vec3 V=toCam/dist;\n"+
        " vec3 Vt=vec3(dot(V,T),dot(V,B),dot(V,N));\n"+
        " vec2 uv=vUv;vec2 gx=dFdx(vUv),gy=dFdy(vUv);\n"+
        " float pomAmt=uPom*clamp(1.6-dist/9.0,0.0,1.0);float surfDepth=0.0;\n"+
        // ---- parallax occlusion: march down the height field along the view ray (more steps at grazing angles)
        " if(pomAmt>0.001&&Vt.z>0.02){\n"+
        "  float steps=mix(28.0,8.0,clamp(Vt.z,0.0,1.0))*clamp(1.3-dist/12.0,0.35,1.0);\n"+
        "  float layer=1.0/steps;vec2 dUV=Vt.xy/max(Vt.z,0.12)*pomAmt*layer;\n"+
        "  float cur=0.0;vec2 c=uv;float h=1.0-textureGrad(uB,c,gx,gy).a;\n"+
        "  for(int i=0;i<36;i++){if(float(i)>=steps||cur>=h)break;c-=dUV;h=1.0-textureGrad(uB,c,gx,gy).a;cur+=layer;}\n"+
        "  vec2 a=c+dUV;vec2 b=c;float ca=cur-layer,cb=cur;\n"+
        "  for(int k=0;k<5;k++){vec2 m=(a+b)*0.5;float cm=(ca+cb)*0.5;float hm=1.0-textureGrad(uB,m,gx,gy).a;if(cm>=hm){b=m;cb=cm;}else{a=m;ca=cm;}}\n"+
        "  uv=b;surfDepth=cb;}\n"+
        " vec4 A=textureGrad(uA,uv,gx,gy);vec3 albedo=A.rgb;float ao=A.a;\n"+
        " vec4 Bt=textureGrad(uB,uv,gx,gy);vec3 nm=Bt.rgb*2.0-1.0;float h0=Bt.a;\n"+
        " vec3 Nw=normalize(T*nm.x+B*nm.y+N*nm.z);\n"+
        " vec3 rme=textureGrad(uC,uv,gx,gy).rgb;float rough=clamp(rme.r,0.05,1.0);float metal=rme.g;float emis=rme.b;\n"+
        " vec3 F0=mix(vec3(0.04),albedo,metal);float NdV=max(dot(Nw,V),1e-3);\n"+
        " float a2=rough*rough;a2*=a2;float kG=(rough+1.0)*(rough+1.0)/8.0;\n"+
        // ---- which lights this cell can see (baked visibility)
        " ivec2 cell=clamp(ivec2(floor(vPos.xy+N.xy*0.06)),ivec2(0),uGrid-1);vec2 mk=texelFetch(uMask,cell,0).rg;\n"+
        " int bits=int(mk.r*255.0+0.5)|(int(mk.g*255.0+0.5)<<8);bits|=1<<15;\n"+
        " vec3 Lo=vec3(0.0);int shadowBudget=2;\n"+
        " for(int i=0;i<16;i++){if(i>=uLCount)break;if(((bits>>i)&1)==0)continue;\n"+
        "  vec3 Ld=uL[i].xyz-vPos;float d=length(Ld);float r=uL[i].w;if(d>r)continue;Ld/=d;\n"+
        "  float fall=clamp(1.0-pow(d/r,4.0),0.0,1.0);fall=fall*fall/(d*d*0.6+0.4);\n"+
        "  float NdL=dot(Nw,Ld);if(NdL<=0.0||dot(N,Ld)<-0.2)continue;\n"+
        "  float sh=1.0;\n"+
        // soft self-shadow inside the parallax surface toward this light
        "  if(pomAmt>0.001&&shadowBudget>0&&fall>0.02){shadowBudget--;vec3 Lt=vec3(dot(Ld,T),dot(Ld,B),dot(Ld,N));\n"+
        "   if(Lt.z>0.02){vec2 dir=Lt.xy/max(Lt.z,0.15)*pomAmt;float occ=0.0;\n"+
        "    float hs=1.0-surfDepth;float dh=max(1.0-hs,0.06)/6.0;\n"+
        "    for(int j=1;j<=6;j++){float hr=hs+dh*float(j);float hh=textureGrad(uB,uv+dir*dh*float(j),gx,gy).a;occ=max(occ,(hh-hr)*(1.0-float(j)/7.0));}\n"+
        "    sh=1.0-clamp(occ*uShadowK,0.0,0.85);}else sh=0.4;}\n"+
        "  vec3 H=normalize(Ld+V);float NdH=max(dot(Nw,H),0.0);float VdH=max(dot(V,H),0.0);\n"+
        "  float D=a2/(PI*pow(NdH*NdH*(a2-1.0)+1.0,2.0));\n"+
        "  float G=(NdV/(NdV*(1.0-kG)+kG))*(NdL/(NdL*(1.0-kG)+kG));\n"+
        "  vec3 F=F0+(1.0-F0)*pow(1.0-VdH,5.0);\n"+
        "  vec3 spec=D*G*F/(4.0*NdV*NdL+1e-4);vec3 kd=(1.0-F)*(1.0-metal);\n"+
        "  Lo+=(kd*albedo/PI+spec)*uLC[i]*fall*NdL*sh;}\n"+
        // ---- ambient: sky/ground hemisphere, rough reflection of it, occlusion
        " vec3 hemi=mix(uGround,uSky,Nw.z*0.5+0.5);vec3 R=reflect(-V,Nw);vec3 env=mix(uGround,uSky,R.z*0.5+0.5)*uEnvK+(uEnvK-1.0)*(vec3(0.06,0.03,0.018)*pow(1.0-abs(R.z),6.0)+vec3(0.05,0.022,0.01)*max(-R.z,0.0)+vec3(0.02,0.021,0.024)*max(R.z,0.0));\n"+
        " vec3 Fr=F0+(max(vec3(1.0-rough),F0)-F0)*pow(1.0-NdV,5.0);\n"+
        " vec3 amb=hemi*albedo*(1.0-metal)+env*Fr*(1.0-rough*0.7);\n"+
        " float cav=mix(1.0,0.55,surfDepth);\n"+
        " vec3 col=Lo*mix(1.0,ao,0.5)+amb*ao*cav;\n"+
        " col+=albedo*emis*uEmit*(0.75+0.25*sin(uTime*1.7+vPos.x*1.3+vPos.y*0.9))*uPulse+albedo*emis*uEmit*(1.0-uPulse);\n"+
        " col=mix(col,uFog.rgb,1.0-exp(-dist*uFog.a));\n"+
        " col=aces(col*uExposure);fragColor=vec4(pow(col,vec3(1.0/2.2)),1.0);}\n";

    /** Bullet holes: a darkened, scorched disc multiplied over the wall. */
    static final String VS_DECAL=
        "#version 300 es\nlayout(location=0) in vec3 aPos;layout(location=1) in vec2 aUv;layout(location=2) in float aSeed;\n"+
        "uniform mat4 uVP;out vec2 vUv;out float vSeed;out vec3 vPos;\n"+
        "void main(){vUv=aUv;vSeed=aSeed;vPos=aPos;gl_Position=uVP*vec4(aPos,1.0);}\n";
    static final String FS_DECAL=
        "#version 300 es\nprecision mediump float;in vec2 vUv;in float vSeed;in vec3 vPos;uniform vec3 uCam;uniform vec4 uFog;out vec4 fragColor;\n"+
        "float h(float n){return fract(sin(n)*43758.5453);}\n"+
        "void main(){vec2 p=vUv*2.0-1.0;float a=atan(p.y,p.x);float r=length(p);\n"+
        " float edge=0.55+0.25*sin(a*5.0+vSeed*6.0)*h(vSeed+1.0)+0.1*sin(a*11.0+vSeed*9.0);\n"+
        " float hole=1.0-smoothstep(0.12,0.2,r);float scorch=(1.0-smoothstep(edge*0.6,edge,r));\n"+
        " if(scorch<0.01)discard;float k=mix(1.0,0.35,scorch)*mix(1.0,0.08,hole);\n"+
        " float f=1.0-exp(-length(uCam-vPos)*uFog.a);k=mix(k,1.0,f);fragColor=vec4(vec3(k),1.0);}\n";

    /** Particles: camera-facing quads, colour per vertex, a soft round or star-shaped sprite. */
    static final String VS_PART=
        "#version 300 es\nlayout(location=0) in vec3 aPos;layout(location=1) in vec2 aUv;layout(location=2) in vec4 aCol;\n"+
        "uniform mat4 uVP;out vec2 vUv;out vec4 vCol;\n"+
        "void main(){vUv=aUv;vCol=aCol;gl_Position=uVP*vec4(aPos,1.0);}\n";
    static final String FS_PART=
        "#version 300 es\nprecision mediump float;in vec2 vUv;in vec4 vCol;out vec4 fragColor;\n"+
        "void main(){vec2 p=vUv*2.0-1.0;float r=length(p);float k;\n"+
        " if(vUv.x>1.5){p=(vUv-vec2(2.0,0.0))*2.0-1.0;float a=atan(p.y,p.x);r=length(p);k=clamp(1.0-r/(0.35+0.65*pow(abs(cos(a*3.0)),8.0)),0.0,1.0);k=k*k+clamp(1.0-r*2.2,0.0,1.0);}\n"+
        " else k=clamp(1.0-r,0.0,1.0);k=k*k;\n"+
        " fragColor=vec4(vCol.rgb*k*vCol.a,k*vCol.a);}\n";
}
