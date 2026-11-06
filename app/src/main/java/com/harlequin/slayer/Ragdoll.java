package com.harlequin.slayer;

import static com.harlequin.slayer.Hero.*;
import static com.harlequin.slayer.Model.v;

/**
 * An active ragdoll for the warden, with Euphoria-style protective behaviour, and the get-up that follows.
 *
 * PHYSICS: a Verlet particle body (one particle per joint, plus the chainsaw's tip) with masses (the saw arm is
 * heavy), rigid distance constraints for bones and the torso, range constraints as joint limits (knees and elbows
 * bend only so far and only one way, hips and shoulders stay in their cones, the neck can't fold), and collision with
 * the level's floors, steps, walls and ceilings, with friction. It starts from her animated pose and inherits its
 * velocities, so a fall carries her momentum.
 *
 * MUSCLES: while falling she is not limp. Joints are pulled toward a protective pose with a strength that varies:
 * the free hand reaches out to break the fall where she is going to land, the head tucks, the knees bend, and the
 * saw arm is held up and away from the body. Each hard impact knocks the strength down (she goes floppy for a moment)
 * and it recovers; once she is on the ground she relaxes, with a little random movement, and settles.
 *
 * GET-UP: when she has settled, a get-up plays from the pose she landed in. Face up, she first rolls onto her front
 * over her left shoulder, keeping the chainsaw arm up and over. Face down, she plants her left hand and braces on the
 * saw's sleeve (never the bar), pushes up to hands and knees with the saw held off the floor, brings her left foot
 * through to kneel, and stands. The animation blends in from the settled pose, and back into locomotion at the end.
 */
final class Ragdoll {
    static final int N=JOINTS;
    final float[][] p=new float[N][3],q=new float[N][3];final float[] im=new float[N],rad=new float[N];
    // constraints: a, b, min, max, stiffness
    private final java.util.ArrayList<float[]> cons=new java.util.ArrayList<>();
    private final java.util.Random rng=new java.util.Random();
    static final int FALL=0,DOWN=1,GETUP=2,DONE=3;
    int state=DONE;float t;
    float strength;private float settle,downT,lastImpact;
    /** Impact speeds this frame (for sound). */
    float impact;
    static final float G=14f;

    Ragdoll(){
        float[] mass=new float[N];
        mass[J_PELVIS]=12;mass[J_HIP_L]=mass[J_HIP_R]=6;mass[J_NECK]=10;mass[J_SH_L]=mass[J_SH_R]=4;mass[J_HEAD]=5;
        mass[J_KNEE_L]=mass[J_KNEE_R]=4;mass[J_ANK_L]=mass[J_ANK_R]=2;mass[J_TOE_L]=mass[J_TOE_R]=.8f;
        mass[J_ELB_L]=mass[J_ELB_R]=2;mass[J_HAND_L]=1;mass[J_HAND_R]=6;mass[J_SAW]=2.5f;   // the engine sits on her right forearm
        for(int i=0;i<N;i++)im[i]=1/mass[i];
        rad[J_PELVIS]=.12f;rad[J_HIP_L]=rad[J_HIP_R]=.1f;rad[J_NECK]=.11f;rad[J_SH_L]=rad[J_SH_R]=.08f;rad[J_HEAD]=.12f;
        rad[J_KNEE_L]=rad[J_KNEE_R]=.065f;rad[J_ANK_L]=rad[J_ANK_R]=.055f;rad[J_TOE_L]=rad[J_TOE_R]=.035f;
        rad[J_ELB_L]=rad[J_ELB_R]=.055f;rad[J_HAND_L]=.045f;rad[J_HAND_R]=.075f;rad[J_SAW]=.04f;
        float[][] B=BIND_J.clone();B[J_SAW]=sawTipBind();
        int[] torso={J_PELVIS,J_HIP_L,J_HIP_R,J_NECK,J_SH_L,J_SH_R};
        for(int i=0;i<torso.length;i++)for(int j=i+1;j<torso.length;j++)rigid(B,torso[i],torso[j]);
        rigid(B,J_NECK,J_HEAD);range(B,J_SH_L,J_HEAD,.75f,1.15f);range(B,J_SH_R,J_HEAD,.75f,1.15f);range(B,J_PELVIS,J_HEAD,.85f,1.02f);
        for(int s=0;s<2;s++){
            int sh=J_SH_L+s,el=J_ELB_L+s,hd=J_HAND_L+s,hp=J_HIP_L+s,kn=J_KNEE_L+s,an=J_ANK_L+s,to=J_TOE_L+s;
            rigid(B,sh,el);rigid(B,el,hd);
            float arm=dist(B,sh,el)+dist(B,el,hd);cons.add(new float[]{sh,hd,arm*.32f,arm,1});          // elbow bends (never hyper-extends)
            cons.add(new float[]{el,hp,.16f,.82f,.6f});                                                   // shoulder cone
            rigid(B,hp,kn);rigid(B,kn,an);rigid(B,an,to);range(B,kn,to,.93f,1.06f);                       // stiff ankle
            float leg=dist(B,hp,kn)+dist(B,kn,an);cons.add(new float[]{hp,an,leg*.3f,leg,1});            // knee bends
            cons.add(new float[]{kn,J_NECK,.42f,1.03f,.6f});cons.add(new float[]{kn,J_HIP_L+(1-s),.13f,.6f,.5f});   // hip cone, legs don't cross through
        }
        rigid(B,J_HAND_R,J_SAW);rigid(B,J_ELB_R,J_SAW);   // the saw stays in line with her forearm
    }
    private void rigid(float[][] B,int a,int b){float d=dist(B,a,b);cons.add(new float[]{a,b,d,d,1});}
    private void range(float[][] B,int a,int b,float lo,float hi){float d=dist(B,a,b);cons.add(new float[]{a,b,d*lo,d*hi,.7f});}
    private static float dist(float[][] B,int a,int b){return Model.len(Model.sub(B[a],B[b]));}

    /** Go limp-but-alive from the animated pose (velocities from the last frame, plus a push). */
    private final float[][] v0=new float[N][3];private boolean pendingV;
    void start(float[][] J,float[][] Jprev,float dt,float[] push){
        for(int i=0;i<N;i++){float[] a=J[i],b=Jprev[i];
            for(int k=0;k<3;k++){p[i][k]=a[k];float vel=(a[k]-b[k])/Math.max(dt,1e-3f);vel=Math.max(-12,Math.min(12,vel));
                float pk=push[k]*(i==J_NECK||i==J_SH_L||i==J_SH_R||i==J_HEAD?1.25f:i<=J_HIP_R?.8f:.6f);
                v0[i][k]=vel+pk;q[i][k]=a[k];}}
        pendingV=true;state=FALL;t=0;strength=1;settle=0;downT=0;lastImpact=0;
    }

    // ---------------------------------------------------------------- simulation
    void step(float dt,Level L,float time){
        impact=0;t+=dt;
        if(state==GETUP||state==DONE)return;
        int sub=3;float h=Math.min(dt,1/30f)/sub;
        if(pendingV){pendingV=false;for(int i=0;i<N;i++)for(int k=0;k<3;k++)q[i][k]=p[i][k]-v0[i][k]*h;}
        for(int s=0;s<sub;s++){
            muscles(L,h,time);
            for(int i=0;i<N;i++){float[] a=p[i],b=q[i];
                float vx=a[0]-b[0],vy=a[1]-b[1],vz=a[2]-b[2];float damp=.999f;
                b[0]=a[0];b[1]=a[1];b[2]=a[2];
                a[0]+=vx*damp;a[1]+=vy*damp;a[2]+=vz*damp-G*h*h;}
            for(int it=0;it<10;it++){solve();collide(L);}
        }
        // state: on the ground once the torso is low and has hit; settled once things stop moving
        float[] pel=p[J_PELVIS];float fl=L.floorUnder(pel[0],pel[1],.1f);
        boolean low=pel[2]-fl<.32f&&p[J_NECK][2]-fl<.45f;
        float e=0;for(int i=0;i<N;i++){float dx=p[i][0]-q[i][0],dy=p[i][1]-q[i][1],dz=p[i][2]-q[i][2];e+=(dx*dx+dy*dy+dz*dz)/(h*h);}e/=N;
        if(state==FALL&&low&&t>.25f){state=DOWN;downT=0;}
        if(state==DOWN){downT+=dt;settle=e<.35f?settle+dt:Math.max(0,settle-dt*2);
            if((settle>.45f&&downT>.9f)||downT>3.2f)beginGetUp(L);}
        if(state==FALL&&t>4f){state=DOWN;downT=0;}
    }
    private void solve(){
        for(float[] c:cons){int a=(int)c[0],b=(int)c[1];float[] A=p[a],Bp=p[b];
            float dx=Bp[0]-A[0],dy=Bp[1]-A[1],dz=Bp[2]-A[2];float d=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(d<1e-6f)continue;
            float target=d<c[2]?c[2]:d>c[3]?c[3]:d;if(target==d)continue;
            float w=im[a]+im[b];float k=(d-target)/(d*w)*c[4];
            A[0]+=dx*k*im[a];A[1]+=dy*k*im[a];A[2]+=dz*k*im[a];Bp[0]-=dx*k*im[b];Bp[1]-=dy*k*im[b];Bp[2]-=dz*k*im[b];}
        // hinge directions: knees bend forward, elbows bend back (relative to the torso)
        float[] up=Model.norm(Model.sub(p[J_NECK],p[J_PELVIS]));float[] left=Model.norm(Model.sub(p[J_HIP_L],p[J_HIP_R]));float[] fwd=Model.norm(Model.cross(left,up));
        for(int s=0;s<2;s++){hinge(J_HIP_L+s,J_KNEE_L+s,J_ANK_L+s,fwd,.02f);hinge(J_SH_L+s,J_ELB_L+s,J_HAND_L+s,Model.mul(fwd,-1),-.06f);}
    }
    /** Keep the middle joint of a limb on the allowed side of the line through its ends. */
    private void hinge(int a,int m,int b,float[] dir,float slack){
        float[] A=p[a],Mp=p[m],Bp=p[b];float[] mid=Model.mul(Model.add(A,Bp,1),.5f);float[] ax=Model.norm(Model.sub(Bp,A));
        float[] d=Model.sub(dir,Model.mul(ax,Model.dot(dir,ax)));float l=Model.len(d);if(l<1e-4f)return;d=Model.mul(d,1/l);
        float side=Model.dot(Model.sub(Mp,mid),d);if(side>=slack)return;float push=(slack-side)*.5f;
        Mp[0]+=d[0]*push;Mp[1]+=d[1]*push;Mp[2]+=d[2]*push;
    }
    private void collide(Level L){
        for(int i=0;i<N;i++){float[] a=p[i],b=q[i];float r=rad[i];
            // walls (and steps too tall to be floor): stop the axis that went in
            if(wall(L,a[0]+Math.signum(a[0]-b[0])*r,b[1],a[2],r)){a[0]=b[0];}
            if(wall(L,a[0],a[1]+Math.signum(a[1]-b[1])*r,a[2],r)){a[1]=b[1];}
            Level.Cell c=L.atF(a[0],a[1]);if(c.solid){a[0]=b[0];a[1]=b[1];continue;}
            float fl=c.floor;
            if(a[2]-r<fl){float pen=fl-(a[2]-r);float vz=a[2]-b[2];a[2]=fl+r;
                if(-vz/ (1/60f)>impact)impact=-vz*60;
                b[2]=a[2]+Math.max(0,vz)*0+vz*.25f;                         // a little bounce
                float fr=.55f;b[0]=a[0]-(a[0]-b[0])*(1-fr);b[1]=a[1]-(a[1]-b[1])*(1-fr);
                if(i==J_NECK||i==J_PELVIS||i==J_HEAD){float sp=-vz*60;if(sp>2.5f)strength=Math.min(strength,.3f);}}
            if(a[2]+r>c.ceil){a[2]=c.ceil-r;}
        }
    }
    private static boolean wall(Level L,float x,float y,float z,float r){Level.Cell c=L.atF(x,y);return c.solid||c.floor>z-r+.3f||c.ceil<z+r*.5f;}

    // ---------------------------------------------------------------- protective muscles
    private void muscles(Level L,float h,float time){
        if(state==FALL)strength=Math.min(1,strength+h*1.4f);
        else{float relax=downT>.5f?.18f:.45f;strength+=(relax-strength)*Math.min(1,h*3);}
        float k=strength;
        float[] up=Model.norm(Model.sub(p[J_NECK],p[J_PELVIS]));float[] left=Model.sub(p[J_HIP_L],p[J_HIP_R]);left=Model.norm(Model.sub(left,Model.mul(up,Model.dot(left,up))));float[] fwd=Model.cross(left,up);
        float[] o=p[J_PELVIS];
        // the torso frame maps bind offsets (from the pelvis) to world
        // where is she falling? the free hand reaches out to that spot on the floor
        float vx=(p[J_NECK][0]-q[J_NECK][0])/h,vy=(p[J_NECK][1]-q[J_NECK][1])/h;float hv=(float)Math.hypot(vx,vy);
        float[] fall=hv>.3f?v(vx/hv,vy/hv,0):Model.norm(v(fwd[0]+up[0]*.0f,fwd[1],0));
        float[] ch=p[J_NECK];float fl=L.floorUnder(ch[0],ch[1],.1f);
        float[] reachL=v(ch[0]+fall[0]*.45f+left[0]*.15f,ch[1]+fall[1]*.45f+left[1]*.15f,fl+.05f);
        if(state==FALL)pull(J_HAND_L,reachL,.09f*k);
        else pull(J_HAND_L,world(o,fwd,left,up,v(.25f,.3f,1.05f)),.02f*k);
        pull(J_ELB_L,world(o,fwd,left,up,v(.05f,.33f,1.15f)),.03f*k);
        // the saw arm: held up and out, away from her body and head
        pull(J_HAND_R,world(o,fwd,left,up,v(.35f,-.42f,1.3f)),.06f*k);pull(J_ELB_R,world(o,fwd,left,up,v(.05f,-.38f,1.25f)),.04f*k);
        // head tucked, knees soft and a little up
        pull(J_HEAD,world(o,fwd,left,up,v(.07f,0,1.67f)),.12f*k);
        float wr=state==DOWN?(float)Math.sin(time*2.3f)*.04f*(1-Math.min(1,downT)):0;   // a little random writhing as she lands
        pull(J_KNEE_L,world(o,fwd,left,up,v(.22f,.13f,.58f+wr)),.04f*k);pull(J_KNEE_R,world(o,fwd,left,up,v(.15f,-.13f,.55f-wr)),.04f*k);
        pull(J_ANK_L,world(o,fwd,left,up,v(-.05f,.15f,.15f)),.02f*k);pull(J_ANK_R,world(o,fwd,left,up,v(-.08f,-.15f,.15f)),.02f*k);
    }
    private static float[] world(float[] o,float[] f,float[] l,float[] u,float[] bind){float x=bind[0]-PELVIS[0],y=bind[1]-PELVIS[1],z=bind[2]-PELVIS[2];
        return v(o[0]+f[0]*x+l[0]*y+u[0]*z,o[1]+f[1]*x+l[1]*y+u[1]*z,o[2]+f[2]*x+l[2]*y+u[2]*z);}
    private void pull(int i,float[] target,float k){float[] a=p[i],b=q[i];for(int j=0;j<3;j++){float d=(target[j]-a[j])*k;a[j]+=d;b[j]+=d*.85f;}}

    // ---------------------------------------------------------------- get-up
    private float[][] from;private float[] gO=new float[3];private float gYaw;private boolean supine;private float getT,total;private int side;
    /** Where she ends up standing and facing. */
    float[] standAt=new float[3];float standYaw;
    private void beginGetUp(Level L){
        state=GETUP;getT=0;
        float[] up=Model.norm(Model.sub(p[J_NECK],p[J_PELVIS]));float[] left=Model.norm(Model.sub(p[J_HIP_L],p[J_HIP_R]));float[] chestFwd=Model.cross(left,up);
        supine=chestFwd[2]>0;
        float[] axis=v(p[J_NECK][0]-p[J_PELVIS][0],p[J_NECK][1]-p[J_PELVIS][1],0);if(Model.len(axis)<.05f)axis=v(chestFwd[0],chestFwd[1],0);
        gYaw=(float)Math.atan2(axis[1],axis[0]);
        gO[0]=p[J_PELVIS][0];gO[1]=p[J_PELVIS][1];gO[2]=L.floorUnder(gO[0],gO[1],.2f);
        from=new float[N][];for(int i=0;i<N;i++)from[i]=p[i].clone();
        total=(supine?.75f:0)+.35f+KT[KT.length-1];
        float c=(float)Math.cos(gYaw),s=(float)Math.sin(gYaw);float[] st=K[K.length-1][J_PELVIS];
        standAt=v(gO[0]+c*st[0]-s*st[1],gO[1]+s*st[0]+c*st[1],gO[2]);standYaw=gYaw;
        side=rng.nextBoolean()?1:-1;
    }
    /** The get-up pose at the current time (world joint positions); null when finished. */
    float[][] getUpPose(float dt,Level L){
        if(state!=GETUP)return null;
        getT+=dt;
        float c=(float)Math.cos(gYaw),s=(float)Math.sin(gYaw);
        float[][] local;float tt=getT;
        float lead=.35f;   // blending in from the settled pose
        float[][] start=supine?supinePose():K[0];
        if(supine&&tt<lead+.75f){float r=Math.max(0,tt-lead)/.75f;local=roll(supinePose(),PI_F*smooth(0,1,r));}
        else{float kt=tt-(supine?lead+.75f:lead);local=sample(Math.max(0,kt));if(kt>=KT[KT.length-1]){state=DONE;}}
        float[][] J=new float[N][];
        for(int i=0;i<N;i++){float[] a=local[i];J[i]=v(gO[0]+c*a[0]-s*a[1],gO[1]+s*a[0]+c*a[1],gO[2]+a[2]);}
        if(tt<lead){float b=smooth(0,lead,tt);float[][] st=new float[N][];for(int i=0;i<N;i++){float[] a=start[i];st[i]=v(gO[0]+c*a[0]-s*a[1],gO[1]+s*a[0]+c*a[1],gO[2]+a[2]);}
            for(int i=0;i<N;i++)J[i]=Hero.lerpW(from[i],st[i],b);}
        // keep the bones their length and everything above the floor
        for(int i=0;i<N;i++)System.arraycopy(J[i],0,p[i],0,3);
        for(int it=0;it<6;it++){solve();for(int i=0;i<N;i++){float fl=L.floorUnder(p[i][0],p[i][1],.02f);if(p[i][2]-rad[i]*.6f<fl)p[i][2]=fl+rad[i]*.6f;}}
        float[] fa=Model.norm(Model.sub(p[J_HAND_R],p[J_ELB_R]));float l=Model.len(Model.sub(sawTipBind(),handCentre(-1)));
        p[J_SAW]=Model.add(p[J_HAND_R],fa,l);
        float[][] out=new float[N][];for(int i=0;i<N;i++){out[i]=p[i].clone();q[i]=p[i].clone();}
        return out;
    }
    static final float PI_F=(float)Math.PI;
    private float[][] supinePose(){return roll(K[0],PI_F);}   // face up: the prone pose turned over
    private static int mirror(int i){switch(i){case J_HIP_L:return J_HIP_R;case J_HIP_R:return J_HIP_L;case J_SH_L:return J_SH_R;case J_SH_R:return J_SH_L;
        case J_KNEE_L:return J_KNEE_R;case J_KNEE_R:return J_KNEE_L;case J_ANK_L:return J_ANK_R;case J_ANK_R:return J_ANK_L;case J_TOE_L:return J_TOE_R;case J_TOE_R:return J_TOE_L;
        case J_ELB_L:return J_ELB_R;case J_ELB_R:return J_ELB_L;case J_HAND_L:return J_HAND_R;case J_HAND_R:return J_HAND_L;default:return i;}}
    /** Rolls a lying pose about its long axis (over her left shoulder: the saw side goes up and over). */
    private static float[][] roll(float[][] pose,float ang){
        float c=(float)Math.cos(ang),s=(float)Math.sin(ang),cz=.13f,lift=.12f*(float)Math.sin(ang);
        float[][] o=new float[N][];for(int i=0;i<N;i++){float[] a=pose[i];float y=a[1],z=a[2]-cz;o[i]=v(a[0],y*c-z*s,cz+y*s+z*c+lift);}
        return o;
    }
    private static float[][] sample(float t){
        int k=0;while(k<KT.length-2&&t>KT[k+1])k++;
        float u=Math.max(0,Math.min(1,(t-KT[k])/(KT[k+1]-KT[k])));u=u*u*(3-2*u);
        float[][] o=new float[N][];for(int i=0;i<N;i++)o[i]=Hero.lerpW(K[k][i],K[k+1][i],u);return o;
    }
    // key poses for the prone get-up, in a frame on the floor: x toward her head when lying (her facing at the end), y left, z up
    static final float[] KT={0,.5f,1.05f,1.6f,2.15f,2.7f};
    static final float[][][] K={
        {   // 0: lying face down, left hand by the head, saw sleeve flat on the floor
            {0,0,.14f},{0,.095f,.13f},{0,-.095f,.13f},{.55f,0,.15f},{.5f,.2f,.15f},{.5f,-.2f,.15f},{.72f,0,.13f},
            {-.43f,.12f,.08f},{-.43f,-.12f,.08f},{-.85f,.13f,.12f},{-.85f,-.13f,.12f},{-.97f,.15f,.0f},{-.97f,-.15f,.0f},
            {.36f,.38f,.07f},{.3f,-.38f,.07f},{.6f,.32f,.05f},{.56f,-.36f,.08f},{1.2f,-.4f,.05f}},
        {   // 1: push up on the left hand, braced on the saw sleeve
            {0,0,.2f},{0,.095f,.2f},{0,-.095f,.2f},{.5f,0,.43f},{.47f,.2f,.41f},{.47f,-.2f,.41f},{.63f,0,.55f},
            {-.42f,.12f,.08f},{-.42f,-.12f,.08f},{-.84f,.13f,.12f},{-.84f,-.13f,.12f},{-.96f,.15f,.0f},{-.96f,-.15f,.0f},
            {.46f,.36f,.25f},{.33f,-.36f,.1f},{.52f,.28f,.05f},{.6f,-.34f,.1f},{1.25f,-.4f,.12f}},
        {   // 2: hands and knees, the saw held off the floor
            {0,0,.56f},{0,.095f,.55f},{0,-.095f,.55f},{.53f,0,.66f},{.51f,.2f,.64f},{.51f,-.2f,.64f},{.68f,0,.73f},
            {.05f,.12f,.08f},{.05f,-.12f,.08f},{-.36f,.13f,.1f},{-.36f,-.13f,.1f},{-.5f,.14f,.03f},{-.5f,-.14f,.03f},
            {.55f,.3f,.35f},{.45f,-.32f,.42f},{.56f,.26f,.05f},{.7f,-.3f,.55f},{1.35f,-.3f,.75f}},
        {   // 3: kneeling on the right knee, left foot planted, left hand on the knee
            {.05f,0,.52f},{.05f,.095f,.52f},{.05f,-.095f,.52f},{.12f,0,1.07f},{.11f,.2f,1.02f},{.11f,-.2f,1.02f},{.16f,0,1.24f},
            {.48f,.12f,.5f},{.03f,-.12f,.07f},{.46f,.13f,.09f},{-.38f,-.13f,.1f},{.66f,.15f,.03f},{-.5f,-.14f,.03f},
            {.24f,.36f,.78f},{.12f,-.32f,.8f},{.45f,.2f,.56f},{.33f,-.3f,.66f},{.9f,-.32f,.3f}},
        {   // 4: rising, weight over the left foot, saw coming up
            {.22f,0,.78f},{.22f,.095f,.78f},{.22f,-.095f,.78f},{.33f,0,1.32f},{.32f,.2f,1.27f},{.32f,-.2f,1.27f},{.37f,0,1.49f},
            {.56f,.12f,.48f},{.15f,-.12f,.4f},{.46f,.13f,.09f},{-.2f,-.13f,.12f},{.66f,.15f,.03f},{-.32f,-.14f,.03f},
            {.33f,.32f,1.03f},{.3f,-.3f,1.02f},{.47f,.26f,.85f},{.5f,-.25f,.9f},{1.1f,-.28f,.75f}},
        {   // 5: standing (matches her idle stance), saw carried
            {.4f,0,.94f},{.4f,.095f,.94f},{.4f,-.095f,.94f},{.4f,0,1.49f},{.4f,.205f,1.44f},{.4f,-.205f,1.44f},{.41f,0,1.66f},
            {.43f,.12f,.51f},{.43f,-.12f,.51f},{.45f,.14f,.088f},{.35f,-.14f,.088f},{.65f,.155f,.035f},{.55f,-.155f,.035f},
            {.38f,.27f,1.17f},{.38f,-.31f,1.17f},{.4f,.29f,.92f},{.6f,-.25f,1.03f},{1.2f,-.22f,.7f}},
    };
}
