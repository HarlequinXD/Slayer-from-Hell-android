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
    static final float G=14f,SPIN_DAMP=.035f;
    private final boolean[] contact=new boolean[N];

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
        float[] mean=new float[3];float msum=0;
        for(int i=0;i<N;i++){float m=1/im[i];msum+=m;for(int k=0;k<3;k++)mean[k]+=(J[i][k]-Jprev[i][k])/Math.max(dt,1e-3f)*m;}
        for(int k=0;k<3;k++)mean[k]=Math.max(-7,Math.min(7,mean[k]/msum));
        for(int i=0;i<N;i++){float[] a=J[i],b=Jprev[i];
            boolean upper=i==J_NECK||i==J_SH_L||i==J_SH_R||i==J_HEAD||i==J_ELB_L||i==J_ELB_R||i==J_HAND_L||i==J_HAND_R||i==J_SAW;
            boolean feet=i==J_ANK_L||i==J_ANK_R||i==J_TOE_L||i==J_TOE_R||i==J_KNEE_L||i==J_KNEE_R;
            for(int k=0;k<3;k++){p[i][k]=a[k];float own=(a[k]-b[k])/Math.max(dt,1e-3f);own=Math.max(-5,Math.min(5,own));
                float vel=mean[k]*.8f+own*.2f;   // mostly the body's own momentum, a little of each limb's motion
                float pk=k==2?push[2]:push[k]*(upper?1f:feet?-.25f:.4f);   // the upper body goes, the feet catch: she tips over them
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
            float mx=0,my=0,mz=0,ms=0;for(int i=0;i<N;i++){float m=1/im[i];mx+=(p[i][0]-q[i][0])*m;my+=(p[i][1]-q[i][1])*m;mz+=(p[i][2]-q[i][2])*m;ms+=m;}mx/=ms;my/=ms;mz/=ms;
            float vmax=9*h;
            for(int i=0;i<N;i++){float[] a=p[i],b=q[i];
                float vx=a[0]-b[0],vy=a[1]-b[1],vz=a[2]-b[2];
                vx+=(mx-vx)*SPIN_DAMP;vy+=(my-vy)*SPIN_DAMP;vz+=(mz-vz)*SPIN_DAMP;   // tames tumbling: limbs drift toward the body's motion
                float l=(float)Math.sqrt(vx*vx+vy*vy+vz*vz);if(l>vmax){vx*=vmax/l;vy*=vmax/l;vz*=vmax/l;}
                b[0]=a[0];b[1]=a[1];b[2]=a[2];
                a[0]+=vx*.998f;a[1]+=vy*.998f;a[2]+=vz*.998f-G*h*h;}
            java.util.Arrays.fill(contact,false);
            for(int it=0;it<10;it++){solve();collide(L);}
            for(int i=0;i<N;i++)if(contact[i]){float[] a=p[i],b=q[i];float fr=.35f;b[0]=a[0]-(a[0]-b[0])*(1-fr);b[1]=a[1]-(a[1]-b[1])*(1-fr);}
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
                b[2]=a[2]+vz*.08f;contact[i]=true;                          // barely any bounce: armour is heavy
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
        pull(J_HEAD,world(o,fwd,left,up,v(.06f,0,1.67f)),.05f*k);
        float wr=state==DOWN?(float)Math.sin(time*2.3f)*.04f*(1-Math.min(1,downT)):0;   // a little random writhing as she lands
        pull(J_KNEE_L,world(o,fwd,left,up,v(.22f,.13f,.58f+wr)),.04f*k);pull(J_KNEE_R,world(o,fwd,left,up,v(.15f,-.13f,.55f-wr)),.04f*k);
        pull(J_ANK_L,world(o,fwd,left,up,v(-.05f,.15f,.15f)),.02f*k);pull(J_ANK_R,world(o,fwd,left,up,v(-.08f,-.15f,.15f)),.02f*k);
    }
    private static float[] world(float[] o,float[] f,float[] l,float[] u,float[] bind){float x=bind[0]-PELVIS[0],y=bind[1]-PELVIS[1],z=bind[2]-PELVIS[2];
        return v(o[0]+f[0]*x+l[0]*y+u[0]*z,o[1]+f[1]*x+l[1]*y+u[1]*z,o[2]+f[2]*x+l[2]*y+u[2]*z);}
    private void pull(int i,float[] target,float k){float[] a=p[i],b=q[i];for(int j=0;j<3;j++){float d=(target[j]-a[j])*k;a[j]+=d;b[j]+=d;}}

    // ---------------------------------------------------------------- get-up (key poses solved with IK, so bones never stretch)
    private float[][] from;private final float[] gO=new float[3];private float gYaw;private boolean supine;private float getT;
    /** Where she ends up standing and facing. */
    float[] standAt=new float[3];float standYaw;
    private void beginGetUp(Level L){
        state=GETUP;getT=0;
        float[] up=Model.norm(Model.sub(p[J_NECK],p[J_PELVIS]));float[] left=Model.norm(Model.sub(p[J_HIP_L],p[J_HIP_R]));float[] chestFwd=Model.cross(left,up);
        supine=chestFwd[2]>0;
        // the floor frame: face down she gets up facing where her head was; face up, facing her feet
        float[] axis=v(p[J_NECK][0]-p[J_PELVIS][0],p[J_NECK][1]-p[J_PELVIS][1],0);if(supine)axis=Model.mul(axis,-1);
        if(Model.len(axis)<.05f)axis=v(chestFwd[0],chestFwd[1],0);
        gYaw=(float)Math.atan2(axis[1],axis[0]);
        gO[0]=p[J_PELVIS][0];gO[1]=p[J_PELVIS][1];gO[2]=L.floorUnder(gO[0],gO[1],.2f);
        from=new float[N][];for(int i=0;i<N;i++)from[i]=p[i].clone();
        float[][] keys=supine?BACK:FRONT;float[] last=keys[keys.length-1];
        float c=(float)Math.cos(gYaw),s=(float)Math.sin(gYaw);
        standAt=v(gO[0]+c*last[0]-s*last[1],gO[1]+s*last[0]+c*last[1],gO[2]);standYaw=gYaw;
    }
    /** The get-up pose at the current time (world joint positions); state becomes DONE on its last frame. */
    float[][] getUpPose(float dt,Level L){
        if(state!=GETUP)return null;
        getT+=dt;
        float[][] keys=supine?BACK:FRONT;float[] times=supine?BACK_T:FRONT_T;
        float lead=.45f;   // settling from where the ragdoll landed into the first key pose
        float kt=Math.max(0,getT-lead);if(kt>=times[times.length-1]){kt=times[times.length-1];state=DONE;}
        int k=0;while(k<times.length-2&&kt>times[k+1])k++;
        float u=Math.max(0,Math.min(1,(kt-times[k])/(times[k+1]-times[k])));u=u*u*(3-2*u);
        float[] key=new float[KEY];for(int i=0;i<KEY;i++)key[i]=mix(keys[k][i],keys[k+1][i],u);
        float[][] J=poseJoints(key,gO,gYaw,L);
        if(getT<lead){float b=smooth(0,lead,getT);for(int i=0;i<N;i++)J[i]=Hero.lerpW(from[i],J[i],b);}
        for(int i=0;i<N;i++){System.arraycopy(J[i],0,p[i],0,3);System.arraycopy(J[i],0,q[i],0,3);}
        return J;
    }
    // key layout (in the floor frame: x = the way she will face, y = her left, z up)
    static final int KEY=33,PX=0,YAW=3,PITCH=4,ROLL=5,HEADP=6,LANK=7,LFP=10,LKP=11,RANK=14,RFP=17,RKP=18,LHAND=21,LEP=24,RHAND=27,REP=30;
    /** Joint positions for a key pose: the torso placed and turned, head nodded, feet and hands placed by two-bone IK. */
    static float[][] poseJoints(float[] k,float[] o,float gyaw,Level L){
        float[] Gm=M.mul(M.tr(o[0],o[1],o[2]),M.rz(gyaw));
        float[] T=M.mul(Gm,M.mul(M.tr(k[PX],k[PX+1],k[PX+2]),M.mul(M.rz(k[YAW]),M.mul(M.ry(k[PITCH]),M.mul(M.rx(k[ROLL]),M.tr(-PELVIS[0],-PELVIS[1],-PELVIS[2]))))));
        float[][] J=new float[N][];
        J[J_PELVIS]=M.pt(T,PELVIS);J[J_HIP_L]=M.pt(T,hip(1));J[J_HIP_R]=M.pt(T,hip(-1));J[J_NECK]=M.pt(T,NECK);J[J_SH_L]=M.pt(T,shoulder(1));J[J_SH_R]=M.pt(T,shoulder(-1));
        J[J_HEAD]=M.pt(M.mul(T,pivot(NECK,M.ry(k[HEADP]))),HEAD);
        for(int s=-1;s<=1;s+=2){int o2=s>0?0:1;int A=s>0?LANK:RANK,FP=s>0?LFP:RFP,KP=s>0?LKP:RKP,HA=s>0?LHAND:RHAND,EP=s>0?LEP:REP;
            float[] hipW=J[J_HIP_L+o2];float[] at=M.pt(Gm,v(k[A],k[A+1],k[A+2]));float[] kp=Model.norm(M.dir(Gm,v(k[KP],k[KP+1],k[KP+2])));
            float l1=Model.len(Model.sub(knee(s),hip(s))),l2=Model.len(Model.sub(ankle(s),knee(s)));
            float[][] ik=ik(hipW,at,kp,l1,l2);J[J_KNEE_L+o2]=ik[0];J[J_ANK_L+o2]=ik[1];
            float fp=k[FP];float[] fd=M.dir(Gm,v((float)Math.cos(fp),s*.08f,-(float)Math.sin(fp)));
            J[J_TOE_L+o2]=Model.add(ik[1],Model.norm(fd),Model.len(Model.sub(toe(s),ankle(s))));
            float[] shW=J[J_SH_L+o2];float[] ht=M.pt(Gm,v(k[HA],k[HA+1],k[HA+2]));float[] ep=Model.norm(M.dir(Gm,v(k[EP],k[EP+1],k[EP+2])));
            float a1=Model.len(Model.sub(elbow(s),shoulder(s))),a2=Model.len(Model.sub(handCentre(s),elbow(s)));
            float[][] ia=ik(shW,ht,ep,a1,a2);J[J_ELB_L+o2]=ia[0];J[J_HAND_L+o2]=ia[1];}
        float[] fa=Model.norm(Model.sub(J[J_HAND_R],J[J_ELB_R]));J[J_SAW]=Model.add(J[J_HAND_R],fa,Model.len(Model.sub(sawTipBind(),handCentre(-1))));
        // nothing below the floor (the saw's tip, a toe)
        for(int i=0;i<N;i++){float fl=L.floorUnder(J[i][0],J[i][1],.02f);if(fl>-50&&J[i][2]<fl+.02f)J[i][2]=fl+.02f;}
        return J;
    }
    static final float H=1.5708f;
    //                     pelvis          yaw  pitch roll head  L ankle          Lfoot L knee pole   R ankle           Rfoot R knee pole  L hand           L elbow pole      R hand (saw)      R elbow pole
    static final float[] FRONT_T={0,.55f,1.1f,1.65f,2.25f,2.85f};
    static final float[][] FRONT={
        {0,0,.14f,       0, H,    0, -.2f,  -.85f,.14f,.16f,  2f, 0,0,-1,       -.85f,-.14f,.16f,  2f, 0,0,-1,      .55f,.32f,.05f,   -.5f,.5f,.3f,     .55f,-.34f,.08f,  -.5f,-.5f,.2f},    // lying face down
        {0,0,.2f,        0,1.15f, 0, -.3f,  -.84f,.14f,.15f,  2f, 0,0,-1,       -.84f,-.14f,.15f,  2f, 0,0,-1,      .5f,.28f,.04f,    -.6f,.5f,.3f,     .6f,-.32f,.1f,    0,-.3f,-1},        // push up on the left hand, braced on the saw sleeve
        {0,0,.52f,       0,1.45f, 0, -.35f, -.4f,.14f,.1f,    2.3f,.4f,0,-1,    -.4f,-.14f,.1f,    2.3f,.4f,0,-1,   .52f,.25f,.04f,   -.4f,.6f,.3f,     .62f,-.3f,.42f,   -.4f,-.6f,-.4f},   // hands and knees, saw off the floor
        {.05f,0,.52f,    0,.12f,  0, .1f,   .48f,.14f,.09f,   0, 1,0,0,         -.36f,-.14f,.11f,  2.3f,.3f,0,-1,   .43f,.2f,.58f,    -.5f,.6f,-.4f,    .4f,-.3f,.62f,    -.4f,-.6f,-.6f},   // kneel on the right knee, left foot forward
        {.22f,0,.78f,    0,.3f,   0, .05f,  .48f,.14f,.09f,   0, 1,0,0,         -.12f,-.14f,.12f,  .9f,1,0,-.2f,    .45f,.26f,.85f,   -.6f,.6f,-.4f,    .48f,-.28f,.9f,   -.4f,-.6f,-.6f},   // rising over the left foot
        {.33f,0,.935f,   0,.02f,  0, 0,     .39f,.14f,.088f,  0, 1,0,0,         .28f,-.14f,.088f,  0, 1,0,0,        .34f,.27f,.95f,   -1,.3f,-.3f,      .53f,-.25f,1.03f, -.4f,-.6f,-.7f}};  // standing
    static final float[] BACK_T={0,.6f,1.2f,1.75f,2.3f,2.9f};
    static final float[][] BACK={
        {0,0,.14f,       0,-H,    0, .25f,  .85f,.14f,.1f,   -1.2f,0,0,1,       .85f,-.14f,.1f,   -1.2f,0,0,1,      -.05f,.33f,.05f,  0,1,0,            -.02f,-.36f,.08f, 0,-1,0},           // lying face up
        {0,0,.14f,       0,-.75f, 0, .2f,   .55f,.14f,.09f,   0,.3f,0,1,        .55f,-.14f,.09f,   0,.3f,0,1,       -.3f,.3f,.04f,    -1,.4f,.3f,       .2f,-.3f,.5f,     -.3f,-.7f,-.5f},   // sit up, propped on the left hand, saw lifted over the knees
        {.08f,.05f,.3f,  .5f,-.15f,.25f,0,  -.25f,.25f,.1f,   2.3f,.5f,.3f,-.6f, .48f,-.12f,.09f,  0,1,0,.3f,       .05f,.42f,.04f,   -.4f,.7f,.2f,     .35f,-.25f,.55f,  -.4f,-.6f,-.6f},   // turn onto the left hand and tuck the left leg
        {.08f,0,.52f,    0,.12f,  0, .1f,   -.36f,.14f,.11f,  2.3f,.3f,0,-1,    .48f,-.14f,.09f,   0,1,0,0,         .25f,.26f,.6f,    -.5f,.7f,-.3f,    .42f,-.3f,.62f,   -.4f,-.6f,-.6f},   // kneel on the left knee, right foot forward
        {.24f,0,.78f,    0,.3f,   0, .05f,  -.12f,.14f,.12f,  .9f,1,0,-.2f,     .48f,-.14f,.09f,   0,1,0,0,         .42f,.27f,.85f,   -.6f,.6f,-.4f,    .5f,-.28f,.9f,    -.4f,-.6f,-.6f},   // rising over the right foot
        {.33f,0,.935f,   0,.02f,  0, 0,     .39f,.14f,.088f,  0,1,0,0,          .28f,-.14f,.088f,  0,1,0,0,         .34f,.27f,.95f,   -1,.3f,-.3f,      .53f,-.25f,1.03f, -.4f,-.6f,-.7f}};  // standing
}
