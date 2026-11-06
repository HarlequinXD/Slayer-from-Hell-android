package com.harlequin.slayer;

import static com.harlequin.slayer.Model.v;

/**
 * The warden as the player character: a rigid-segment rig over the baked low poly (assets/hero.bin, one mesh group
 * per body part) driven by a layered procedural animation system, in the spirit of GTA V's locomotion (clean start /
 * cycle / stop / pivot behaviour with foot IK and stride matching) with Euphoria-style physics layered on top.
 *
 * Locomotion is driven by the real velocity: each foot is planted while in stance (no sliding) and lifted on an arc
 * through the swing; stride and cadence scale with speed and blend from walk to run, with heel strike and toe off.
 * Foot IK finds the floor under each foot, so on stairs each foot lands on its own step and the pelvis drops to let
 * the lower leg reach. The body leans into starts, rocks back on stops and banks into turns; turning on the spot
 * makes her shuffle her feet round instead of skating. Walking into a wall makes her recoil (a hard run into one can
 * knock her down); pushing against it, she puts her free hand on it. When idle she fidgets at random (looks around,
 * shifts her weight, checks the saw, rolls her shoulders), and every step varies a little.
 *
 * The shotgun is held in her left hand with the stock under the arm (or she has dropped it, and her left arm swings
 * free or pumps when she runs, and reaches down to pick the gun back up). The chainsaw is strapped to her right arm.
 *
 * Every pose can also be expressed as joint positions (pelvis, hips, neck, shoulders, head, knees, ankles, toes,
 * elbows, hands, the saw's tip): the ragdoll and the get-up animations drive the rig through fromJoints().
 *
 * Model space: x forward, y to her left, z up, feet at z = 0 (the same landmarks the baker used). Pure Java.
 */
final class Hero {
    // part ids: identical to tools/bake/HeroBaker
    static final int P_TORSO=0,P_HEAD=1,P_BRAID=2,P_GLAIVE_SHAFT=3,P_GLAIVE_HEAD=4,P_FIRST_SIDE=5;
    static final int S_PAULDRON=0,S_UPPER=1,S_FORE=2,S_HAND=3,S_THIGH=4,S_SHIN=5,S_FOOT=6,PER_SIDE=7,P_PELVIS=P_FIRST_SIDE+2*PER_SIDE,PARTS=P_PELVIS+1;
    static int sidePart(int s,int k){return P_FIRST_SIDE+(s>0?0:PER_SIDE)+k;}
    // bind-pose landmarks (s = +1 left, -1 right)
    static float[] hip(int s){return v(0,s*.095f,.95f);}
    static float[] knee(int s){return v(.025f,s*.12f,.52f);}
    static float[] ankle(int s){return v(-.005f,s*.14f,.088f);}
    static float[] toe(int s){return v(.2f,s*.155f,.035f);}
    static float[] shoulder(int s){return v(0,s*.205f,1.45f);}
    static float[] elbow(int s){return s<0?v(-.03f,-.27f,1.19f):v(-.04f,.265f,1.18f);}
    static float[] wrist(int s){return s<0?v(.06f,-.3f,.985f):v(-.01f,.29f,.95f);}
    static float[] handCentre(int s){float[] e=elbow(s),w=wrist(s);return Model.add(w,Model.norm(Model.sub(w,e)),.055f);}
    static final float[] WAIST=v(0,0,1.12f),NECK=v(0,0,1.5f),EYE=v(.06f,0,1.676f),BRAID_ROOT=v(-.115f,0,1.63f),PELVIS=v(0,0,.95f),HEAD=v(.01f,0,1.675f);

    // ---------------------------------------------------------------- joints (shared with the ragdoll and the get-up)
    static final int J_PELVIS=0,J_HIP_L=1,J_HIP_R=2,J_NECK=3,J_SH_L=4,J_SH_R=5,J_HEAD=6,J_KNEE_L=7,J_KNEE_R=8,J_ANK_L=9,J_ANK_R=10,
        J_TOE_L=11,J_TOE_R=12,J_ELB_L=13,J_ELB_R=14,J_HAND_L=15,J_HAND_R=16,J_SAW=17,JOINTS=18;
    /** Bind positions of the joints. */
    static final float[][] BIND_J=new float[JOINTS][];
    static{BIND_J[J_PELVIS]=PELVIS;BIND_J[J_HIP_L]=hip(1);BIND_J[J_HIP_R]=hip(-1);BIND_J[J_NECK]=NECK;BIND_J[J_SH_L]=shoulder(1);BIND_J[J_SH_R]=shoulder(-1);
        BIND_J[J_HEAD]=HEAD;BIND_J[J_KNEE_L]=knee(1);BIND_J[J_KNEE_R]=knee(-1);BIND_J[J_ANK_L]=ankle(1);BIND_J[J_ANK_R]=ankle(-1);BIND_J[J_TOE_L]=toe(1);BIND_J[J_TOE_R]=toe(-1);
        BIND_J[J_ELB_L]=elbow(1);BIND_J[J_ELB_R]=elbow(-1);BIND_J[J_HAND_L]=handCentre(1);BIND_J[J_HAND_R]=handCentre(-1);}
    static float[] sawTipBind(){return M.pt(SAW_BIND,v(.97f,0,0));}

    /** World matrix of every part (null = not drawn), and the gun's eye frame (the identity: gun poses are in world space). */
    final float[][] mat=new float[PARTS][];float[] eyeFrame=M.id();
    /** Facing, blends and timers. */
    float bodyYaw;float aim;float run;float air;float phase;float speed;
    private float aimHold,landT=9,landK,jumpT=9,braidA,braidAV,braidB,braidBV,pvx,pvy;
    private boolean wasGround=true;
    /** Foot plants this frame (for footstep sounds). */
    int steps;
    private float lastPhase;
    // start/stop/turn dynamics
    private float accLean,bank,prevLvx,prevLvy,prevYaw;
    // per-foot state: smoothed floor height, random step variation, stance yaw for turning on the spot
    private final float[] footFloor={Float.NaN,Float.NaN},stepVar={1,1},stepYaw={0,0};private float stanceYaw,shuffle,shufflePhase;private boolean stanceInit;
    private float pelvisDrop;
    // wall bumps and leaning on walls
    private float bumpT=9,bumpK,bumpNx,bumpNy,wallLean,wallNx,wallNy;
    // idle fidgets
    private final java.util.Random rng=new java.util.Random(7);private float fidgetWait=4,fidgetT=9,fidgetDur=1;private int fidget=-1;
    /** True during the "check the saw" fidget (the game revs the engine). */
    boolean fidgetRev;
    // picking the shotgun up
    private float pickT=9;private float[] pickAt;
    /** The left hand has the shotgun. */
    boolean hasGun=true;
    /** Joint positions last frame (for handing velocities to the ragdoll). */
    final float[][] joints=new float[JOINTS][3],prevJoints=new float[JOINTS][3];

    static float wrap(float a){while(a>Math.PI)a-=2*Math.PI;while(a<-Math.PI)a+=2*Math.PI;return a;}
    static float smooth(float a,float b,float x){float t=Math.max(0,Math.min(1,(x-a)/(b-a)));return t*t*(3-2*t);}
    static float mix(float a,float b,float t){return a+(b-a)*t;}
    static float approach(float cur,float target,float rate,float dt){return cur+(target-cur)*Math.min(1,rate*dt);}

    /** A wall hit at this speed, with the wall's normal (pointing back at her). */
    void bump(float nx,float ny,float sp){bumpT=0;bumpK=Math.min(1,sp/3.5f);bumpNx=nx;bumpNy=ny;}
    /** Begin reaching for the dropped shotgun at p. */
    void pickUp(float[] p){pickT=0;pickAt=p.clone();}
    boolean picking(){return pickT<.55f;}
    /** True once the hand has closed on the gun during a pick-up. */
    boolean pickGrabbed(){return pickT>=.28f&&pickT<.55f;}

    /**
     * Advances the animation and poses every part.
     * @param camYaw camera heading; @param camPitch camera pitch (up positive); @param firing fire held.
     */
    void update(float dt,float time,Player pl,Level level,float camYaw,float camPitch,boolean firing,boolean sawHeld,Gun gun){
        steps=0;fidgetRev=false;
        // ---------------------------------------------------------------- facing and stance
        if((hasGun&&(firing||gun.state!=Gun.IDLE))||sawHeld)aimHold=1.4f;else aimHold-=dt;
        boolean aiming=aimHold>0;
        aim=approach(aim,aiming?1:0,aiming?14:4,dt);
        speed=(float)Math.hypot(pl.vx,pl.vy);
        float target=aiming?camYaw:(speed>.4f?(float)Math.atan2(pl.vy,pl.vx):bodyYaw);
        float turn=wrap(target-bodyYaw);float rate=aiming?16:9;bodyYaw=wrap(bodyYaw+turn*Math.min(1,rate*dt));
        float yawRate=wrap(bodyYaw-prevYaw)/Math.max(dt,1e-3f);prevYaw=bodyYaw;
        float g=hasGun?1:0;
        float hipOff=mix(.08f,.18f,aim)*mix(.4f,1,g),twist=mix(.04f,.12f,aim)*mix(.3f,1,g);   // the saw side leads, the shotgun side is tucked back
        float hipsYaw=bodyYaw+hipOff;
        // gait blends
        boolean ground=pl.onGround;
        air=approach(air,ground?0:1,ground?20:9,dt);
        run=approach(run,smooth(1.9f,3.6f,speed),6,dt);
        float gaitAmp=smooth(.08f,.7f,speed)*(1-air);
        // turning on the spot: once the feet are too far behind the body, a quick two-step shuffle brings them round
        if(!stanceInit){stanceYaw=hipsYaw;stanceInit=true;}
        if(gaitAmp>.3f||air>.5f)stanceYaw=hipsYaw;
        else if(shuffle<=0&&Math.abs(wrap(hipsYaw-stanceYaw))>.55f){shuffle=1;shufflePhase=0;}
        if(shuffle>0){shufflePhase+=dt*2.6f;stanceYaw=wrap(stanceYaw+wrap(hipsYaw-stanceYaw)*Math.min(1,dt*6));if(shufflePhase>=1){shuffle=0;}}
        float shuffleAmp=shuffle>0?(float)Math.sin(Math.PI*shufflePhase):0;
        float stance=mix(.62f,.35f,run);
        float exc=mix(.33f,.42f,run)*Math.max(.35f,Math.min(1,speed/1.2f));
        float stride=2*exc/stance;
        if(ground&&speed>.05f)phase+=dt*speed/stride;
        else if(shuffle>0)phase+=dt*2.6f;
        else if(ground&&speed<.08f){float near=Math.round(phase*2)/2f;phase=approach(phase,near,3,dt);}   // finish the step and settle
        phase-=(float)Math.floor(phase);
        for(int f=0;f<2;f++){float off=f*.5f;float a=lastPhase-off,b=phase-off;a-=(float)Math.floor(a);b-=(float)Math.floor(b);
            if(b<a&&(gaitAmp>.3f||shuffle>0)){steps++;stepVar[f]=.85f+rng.nextFloat()*.3f;stepYaw[f]=(rng.nextFloat()-.5f)*.12f;}}
        lastPhase=phase;
        // jump and landing
        if(pl.justJumped)jumpT=0;jumpT+=dt;
        if(pl.onGround&&!wasGround){landT=0;landK=Math.min(1,Math.max(.25f,pl.landSpeed/10f));}
        wasGround=pl.onGround;landT+=dt;
        float land=landT<.07f?landT/.07f:(float)Math.exp(-(landT-.07f)*7);land*=landK;
        // velocity and acceleration in the hips frame: lean into starts, rock back on stops, bank into turns
        float ch=(float)Math.cos(hipsYaw),sh=(float)Math.sin(hipsYaw);
        float lvx=ch*pl.vx+sh*pl.vy,lvy=-sh*pl.vx+ch*pl.vy;float ll=(float)Math.hypot(lvx,lvy);float dx=ll>1e-3f?lvx/ll:1,dy=ll>1e-3f?lvy/ll:0;
        float alx=(lvx-prevLvx)/Math.max(dt,1e-3f),aly=(lvy-prevLvy)/Math.max(dt,1e-3f);prevLvx=lvx;prevLvy=lvy;
        if(!ground){alx=aly=0;}
        accLean=approach(accLean,Math.max(-.16f,Math.min(.2f,alx*.022f)),8,dt);
        bank=approach(bank,Math.max(-.2f,Math.min(.2f,-(yawRate*speed*.035f)+aly*.01f)),6,dt);
        // wall bumps and leaning on the wall
        bumpT+=dt;float bump=bumpK*(bumpT<.07f?bumpT/.07f:(float)Math.exp(-(bumpT-.07f)*5.5f));
        boolean pushing=pl.pushingWall&&ground;if(pushing){wallNx=pl.wallNx;wallNy=pl.wallNy;}
        wallLean=approach(wallLean,pushing?1:0,pushing?6:4,dt);
        float bnx=ch*bumpNx+sh*bumpNy,bny=-sh*bumpNx+ch*bumpNy;   // bump normal in the hips frame
        // idle fidgets (random)
        boolean idle=speed<.1f&&aim<.1f&&ground&&!picking()&&wallLean<.1f&&shuffle<=0;
        fidgetT+=dt;
        if(idle){fidgetWait-=dt;if(fidgetWait<=0&&fidgetT>fidgetDur){fidget=rng.nextInt(4);fidgetT=0;fidgetDur=2.2f+rng.nextFloat()*1.4f;fidgetWait=5+rng.nextFloat()*6;}}
        else{if(fidgetT<fidgetDur)fidgetT=Math.max(fidgetT,fidgetDur-.35f);}
        float fk=fidgetT<fidgetDur?smooth(0,.35f,fidgetT)*smooth(0,.35f,fidgetDur-fidgetT):0;float fu=fidgetT/Math.max(fidgetDur,1e-3f);
        float fLook=fidget==0?.7f*(float)Math.sin(2*Math.PI*fu)*fk:0,fShift=fidget==1?fk:0,fSaw=fidget==2?fk:0,fRoll=fidget==3?fk*(float)Math.sin(4*Math.PI*fu):0;
        if(fidget==2&&fk>.5f&&fu>.35f&&fu<.6f)fidgetRev=true;
        // picking the shotgun up
        pickT+=dt;float pick=pickT<.55f?(float)Math.sin(Math.PI*pickT/.55f):0;

        // ---------------------------------------------------------------- feet first (foot IK finds each foot's floor)
        float[] root=v(pl.x,pl.y,pl.z+pl.stepSmooth);
        float[] Hf=M.mul(M.tr(root[0],root[1],root[2]),M.rz(hipsYaw));      // ground frame under the hips
        float feetYaw=wrap(hipsYaw+wrap(stanceYaw-hipsYaw)*(1-gaitAmp));
        float[] Ff=M.mul(M.tr(root[0],root[1],root[2]),M.rz(feetYaw));      // where idle feet stand (turns with the shuffle)
        float[][] tgt=new float[2][],pole=new float[2][];float[] pitchF=new float[2];
        for(int si=0;si<2;si++){int s=si==0?1:-1;
            float p=phase+(s>0?.5f:0);p-=(float)Math.floor(p);
            float off,lift=0,pitch;boolean swing;
            if(p<stance){float q=p/stance;off=exc*(1-2*q);swing=false;
                pitch=q<.15f?-.25f*(1-q/.15f):(q>.68f?.55f*(float)Math.pow((q-.68f)/.32f,2):0);
                lift=pitch>0?.17f*(float)Math.sin(pitch):0;}
            else{float q=(p-stance)/(1-stance);float e=smooth(0,1,q);off=-exc+2*exc*e;swing=true;
                float h=mix(.08f,.2f,run)*Math.min(1,speed/1.4f+.3f)*stepVar[si];
                lift=h*(float)Math.sin(Math.PI*q)+run*.16f*(float)Math.sin(Math.PI*Math.min(1,q*1.5f));
                off-=run*.12f*(float)Math.sin(Math.PI*Math.min(1,q*1.4f));     // heel kicks up behind
                pitch=mix(.55f,-.18f,smooth(0,.85f,q));}
            off*=gaitAmp;lift*=gaitAmp;pitch*=gaitAmp;
            if(shuffle>0){float sp=shufflePhase*2-(s>0?0:1);float k=sp>0&&sp<1?(float)Math.sin(Math.PI*sp):0;lift+=.07f*k;}
            // airborne: tuck while rising, reach down while falling
            float rising=pl.vz>0?1:0;
            float aOff=(s>0?.13f:-.1f),aLift=rising>0?.3f:.1f;
            off=mix(off,aOff,air);lift=mix(lift,aLift,air);pitch=mix(pitch,rising>0?.35f:.15f,air);
            // stance width: the left foot a little forward when standing bladed; the pick-up steps the left foot in
            float[] an=ankle(s);float idleFwd=(s>0?.06f:-.05f)*(1-gaitAmp)*(1-air)*mix(.5f,1,g)+(s>0?.1f*pick:0);
            float[] tl=v(an[0]+dx*off,an[1]+dy*off*.8f,an[2]);
            tl[1]=s>0?Math.max(tl[1],.05f):Math.min(tl[1],-.05f);   // side-steps never cross the feet
            float[] tw=M.pt(Hf,tl);float[] iw=M.pt(Ff,v(an[0]+idleFwd,an[1]+(fShift*.03f*s),an[2]));
            float w=1-gaitAmp;tw=v(mix(tw[0],iw[0],w*(1-air)),mix(tw[1],iw[1],w*(1-air)),tw[2]);
            // the floor under this foot: planted feet keep theirs; swinging feet ease to the next one
            float fl=level.floorUnder(tw[0],tw[1],.06f);if(fl<-50)fl=pl.z;
            if(Float.isNaN(footFloor[si]))footFloor[si]=fl;
            if(fl>footFloor[si])footFloor[si]=swing||air>.5f?approach(footFloor[si],fl,30,dt):fl;   // never through a step: rise at once when planted, quickly when swinging
            if(fl>footFloor[si]+.03f&&swing)footFloor[si]=fl-.03f;
            else if(fl<footFloor[si])footFloor[si]=approach(footFloor[si],fl,air>.5f?4:16,dt);   // ease down onto lower steps
            float floorRel=footFloor[si]-root[2];
            if(swing&&fl>footFloor[si]+.02f)lift+=Math.min(.3f,fl-footFloor[si])*1.1f;     // lift the swinging foot over a step up
            tw[2]=root[2]+an[2]+lift+(air>.5f?0:floorRel)*(1-air);
            tgt[si]=tw;pitchF[si]=pitch+fShift*0;
            float[] fwd=M.dir(Hf,v(1,0,0));pole[si]=Model.norm(Model.add(fwd,M.dir(Hf,v(0,s,0)),.08f));
        }

        // ---------------------------------------------------------------- pelvis
        float midStance=stance*.5f;
        float bob=mix(.02f*(float)Math.cos(4*Math.PI*(phase-midStance)),.04f*(float)Math.cos(4*Math.PI*(phase-midStance-.25f)),run)*gaitAmp;
        float dz=-.012f-.01f*aim-.05f*run*gaitAmp+bob-.17f*land+(air>0?.02f*air:0)-.012f*shuffleAmp-.4f*pick*pick;
        float lat=-.022f*(float)Math.cos(2*Math.PI*(phase-midStance))*gaitAmp*(1-run)+.03f*fShift*(float)Math.sin(Math.PI*fu);
        float pRoll=.035f*(float)Math.cos(2*Math.PI*(phase-midStance))*gaitAmp+bank*.4f-fShift*.05f*(float)Math.sin(Math.PI*fu);
        float pTwist=mix(.09f,.16f,run)*(float)Math.cos(2*Math.PI*phase)*gaitAmp*(dx>=0?1:-1);
        float back=.06f*bump;   // the bump shoves her hips back off the wall
        float[] P0=M.mul(Hf,M.mul(M.tr(back*bnx-.04f*accLean,lat+back*bny,dz),pivot(PELVIS,M.mul(M.rz(pTwist),M.rx(pRoll)))));
        // drop the pelvis so both legs reach their floors (stairs, slopes, a foot on a step)
        float need=0;
        for(int si=0;si<2;si++){int s=si==0?1:-1;float[] hw=M.pt(P0,hip(s));float L=(Model.len(Model.sub(knee(s),hip(s)))+Model.len(Model.sub(ankle(s),knee(s))))*.985f;
            float ddx=tgt[si][0]-hw[0],ddy=tgt[si][1]-hw[1],ddz=tgt[si][2]-hw[2];float h2=ddx*ddx+ddy*ddy;
            if(h2+ddz*ddz>L*L&&ddz<0){float z=-(float)Math.sqrt(Math.max(0,L*L-h2));need=Math.max(need,z-ddz);}}
        pelvisDrop=approach(pelvisDrop,Math.min(.35f,need),need>pelvisDrop?25:8,dt);
        float[] P=M.mul(M.tr(0,0,-pelvisDrop),P0);

        // ---------------------------------------------------------------- torso, head, braid
        float aimPitch=camPitch*aim;
        float lean=(.05f+.13f*run)*gaitAmp+.06f*land+accLean+.55f*pick+.08f*wallLean;
        float kick=gun.recoilNow;
        float torsoTwist=twist-pTwist*.75f+Math.max(-.25f,Math.min(.25f,turn*.25f))*(1-aim);
        float bl=-.32f*bump;   // the bump rocks her shoulders back (away from the wall)
        float wl=wallLean*.06f;
        float[] Tr=M.mul(M.rz(torsoTwist),M.mul(M.ry(lean*dx-aimPitch*.35f-kick*.05f+bl*(-bnx)*0-(bl*bnx)-wl*0),M.rx(-lean*dy*.6f+bank*.6f+bl*bny+fRoll*.07f)));
        Tr=M.mul(M.rz(.0f),Tr);
        float[] T=M.mul(P,pivot(WAIST,Tr));
        mat[P_TORSO]=T;mat[P_PELVIS]=P;
        mat[sidePart(1,S_PAULDRON)]=T;mat[sidePart(-1,S_PAULDRON)]=T;
        float reload=hasGun&&gun.reloading()?1:0;
        float eyeYaw=bodyYaw+aim*wrap(camYaw-bodyYaw)+fLook-.45f*fSaw;
        float eyePitch=mix(aimPitch,-.32f,reload*.7f)+(gun.state==Gun.FIRE?-.06f*kick:0)-.25f*fSaw-.5f*pick-.25f*bump;
        float[] neckW=M.pt(T,NECK);
        float[] H=M.mul(M.tr(neckW[0],neckW[1],neckW[2]),M.mul(M.rz(eyeYaw),M.mul(M.ry(-eyePitch*.8f),M.mul(M.rx(.12f*aim*g),M.tr(-NECK[0],-NECK[1],-NECK[2])))));
        mat[P_HEAD]=H;
        braid(dt,pl.vx,pl.vy,pl.vz,eyeYaw,gaitAmp);

        // ---------------------------------------------------------------- legs (IK to the planted / swinging targets)
        for(int si=0;si<2;si++){int s=si==0?1:-1;float[] an=ankle(s);
            float[] hipW=M.pt(P,hip(s));
            float l1=Model.len(Model.sub(knee(s),hip(s))),l2=Model.len(Model.sub(ankle(s),knee(s)));
            float[][] ik=ik(hipW,tgt[si],pole[si],l1,l2);
            mat[sidePart(s,S_THIGH)]=bone(hip(s),knee(s),v(1,0,0),hipW,ik[0],pole[si]);
            mat[sidePart(s,S_SHIN)]=bone(knee(s),an,v(1,0,0),ik[0],ik[1],pole[si]);
            float[] aw=ik[1];float fy=wrap(feetYaw+(hipsYaw-feetYaw)*gaitAmp)+s*.07f+stepYaw[si]*gaitAmp;
            mat[sidePart(s,S_FOOT)]=M.mul(M.tr(aw[0],aw[1],aw[2]),M.mul(M.rz(fy),M.mul(M.ry(pitchF[si]),M.tr(-an[0],-an[1],-an[2]))));
        }

        // ---------------------------------------------------------------- right arm: the chainsaw
        saw=approach(saw,sawHeld?1:0,sawHeld?10:5,dt);
        float cross=0;
        if(hasGun){if(gun.state==Gun.OPEN)cross=smooth(.15f,.45f,gun.t);else if(gun.state==Gun.LOAD)cross=1;else if(gun.state==Gun.CLOSE)cross=1-smooth(0,.3f,gun.t);}
        cutPhase+=dt*7*saw;
        float thrust=(float)Math.sin(cutPhase)*.05f*saw;
        float[] idleH=v(.2f,-.25f,1.03f),cutH=v(.43f+thrust,-.13f,1.22f+.02f*(float)Math.sin(cutPhase*.5f)),crossH=v(.3f,.02f,1.14f),lookH=v(.3f,-.12f,1.32f);
        float[] idleP=v(-.4f,-.6f,-.7f),cutP=v(-.2f,-.8f,-.6f),crossP=v(.25f,-1f,-.75f),lookP=v(0,-.6f,-1f);
        // with no gun to balance, the saw arm swings with her stride; running, it pumps
        float sw=(float)Math.cos(2*Math.PI*phase)*gaitAmp*(1-g);
        idleH=v(idleH[0]-.1f*sw*(1-run)-.06f*sw*run,idleH[1],idleH[2]+.06f*run*(1-g));
        float[] hT=lerp3(lerp3(lerp3(lerp3(idleH,cutH,saw),crossH,cross),lookH,fSaw),cutH,Math.min(1,bump*.8f));
        float[] pT=lerp3(lerp3(lerp3(idleP,cutP,saw),crossP,cross),lookP,fSaw);
        hT[2]+=(float)Math.sin(time*1.6f)*.006f+(float)Math.sin(phase*4*Math.PI)*.012f*gaitAmp;
        arm(-1,T,M.pt(T,hT),Model.norm(M.dir(T,pT)));
        mat[sidePart(-1,S_HAND)]=null;                 // her right hand is inside the sleeve
        float shake=saw>.05f?.0015f*(float)Math.sin(time*170)*(.4f+saw):.0006f*(float)Math.sin(time*110);
        sawMat=M.mul(M.mul(mat[sidePart(-1,S_FORE)],SAW_BIND),M.tr(0,shake,shake*.6f));
        float[] caddy=M.mul(sawMat,CADDY_FRAME);
        caddyCount=Math.min(2,gun.reserve);
        for(int k=0;k<2;k++)caddyMats[k]=M.mul(caddy,M.tr(0,k==0?-Gun.BX:Gun.BX,0));

        // ---------------------------------------------------------------- left arm: the shotgun (one-handed, stock under the arm) or free
        float[] grip=v(-.1f,0,-.02f);
        float[] wallHand=wallLean>.01f||bump>.01f?v(pl.x-(wallLean>bump?wallNx:bumpNx)*.4f+(float)Math.cos(hipsYaw+1.57f)*.12f,pl.y-(wallLean>bump?wallNy:bumpNy)*.4f+(float)Math.sin(hipsYaw+1.57f)*.12f,pl.z+1.32f):null;
        if(hasGun){
            float[] armpit=M.pt(T,v(-.035f,.165f,1.3f));
            float gy=eyeYaw-.04f*aim-fLook,gp=eyePitch+.5f*pick;
            float[] R=M.mul(M.rz(gy+(1-aim)*.18f),M.ry(-gp+(1-aim)*.55f));
            float[] hold=M.mul(M.tr(armpit[0],armpit[1],armpit[2]),M.mul(R,M.tr(.43f,0,.06f)));
            float rec=gun.state==Gun.FIRE?(gun.t<.04f?gun.t/.04f:(float)Math.exp(-(gun.t-.04f)*9)):0;
            hold=M.mul(hold,M.mul(M.tr(-.05f*rec,0,0),pivot(grip,M.ry(-.38f*rec))));
            float[] G=hold;
            float[] low=M.mul(hold,pivot(grip,M.mul(M.ry(.42f),M.rx(.35f))));      // muzzle down and rolled for the flick
            if(gun.state==Gun.OPEN){float b=smooth(0,.25f,gun.t);G=mlerp(hold,low,b);}
            else if(gun.state==Gun.LOAD){
                float open=gun.openAngle();
                float[] seat=Gun.actionOf(M.mul(caddy,M.tr(-Gun.CHAMBER_X,0,0)),open),appr=Gun.actionOf(M.mul(caddy,M.tr(-Gun.CHAMBER_X+.085f,0,0)),open);
                float t=gun.t;
                if(t<.32f)G=mlerp(low,appr,smooth(0,.32f,t));else if(t<.62f)G=mlerp(appr,seat,smooth(.32f,.62f,t));
                else if(t<.76f)G=mlerp(seat,appr,smooth(.62f,.76f,t));else G=mlerp(appr,low,smooth(.76f,.95f,t));}
            else if(gun.state==Gun.CLOSE){float b=smooth(0,.3f,gun.t);float[] flick=M.mul(hold,pivot(grip,M.ry(-.25f)));G=b<.4f?mlerp(low,flick,b/.4f):mlerp(flick,hold,(b-.4f)/.6f);}
            gun.poseWorld(G);
            arm(1,T,M.pt(G,grip),Model.norm(M.dir(T,v(-.3f,.7f,-.6f))));
        }else{
            // free left arm: swings opposite the left leg walking, pumps bent running, guards up when the saw is out
            float swing=(float)Math.cos(2*Math.PI*phase)*gaitAmp;
            float[] hand=v(.02f+.2f*swing*(1-run),.25f-.02f*swing,.97f+.04f*Math.abs(swing)*(1-run)),pl2=v(-1,.3f,-.3f);
            float[] runH=v(.12f+.24f*swing,.2f,1.1f+.06f*Math.max(0,swing)),runP=v(-1,.4f,-.2f);
            float[] guard=v(.28f,.2f,1.28f),guardP=v(-.4f,.6f,-.8f);
            float[] h=lerp3(lerp3(hand,runH,run),guard,aim*(1-run*.5f));float[] pp=lerp3(lerp3(pl2,runP,run),guardP,aim);
            h[2]+=(float)Math.sin(time*1.6f+1)*.005f;
            float[] hw=M.pt(T,h);
            if(pickAt!=null&&pick>0){hw=Hero.lerpW(hw,v(pickAt[0],pickAt[1],pickAt[2]+.04f),smooth(0,.6f,pick));}
            if(wallHand!=null){float k=Math.max(wallLean,Math.min(1,bump*1.3f));hw=Hero.lerpW(hw,wallHand,k);}
            arm(1,T,hw,Model.norm(M.dir(T,pp)));
        }
        eyeFrame=M.id();
        mat[P_GLAIVE_SHAFT]=mat[P_GLAIVE_HEAD]=null;
        storeJoints();
    }
    static float[] lerpW(float[] a,float[] b,float t){return v(mix(a[0],b[0],t),mix(a[1],b[1],t),mix(a[2],b[2],t));}

    /** The braid: a damped pendulum pushed by her acceleration, hung from the head. */
    private void braid(float dt,float vx,float vy,float vz,float yaw,float gaitAmp){
        float ax=(vx-pvx)/Math.max(dt,1e-3f),ay=(vy-pvy)/Math.max(dt,1e-3f);pvx=vx;pvy=vy;
        float ce=(float)Math.cos(yaw),se=(float)Math.sin(yaw);float fa=ce*ax+se*ay,sa=-se*ax+ce*ay;
        braidAV+=(-braidA*40-braidAV*5+fa*.03f+(vz<-1?-vz*.05f:0))*dt;braidA+=braidAV*dt;
        braidBV+=(-braidB*40-braidBV*5-sa*.03f)*dt;braidB+=braidBV*dt;
        braidA=Math.max(-.6f,Math.min(.9f,braidA+(float)Math.sin(phase*4*Math.PI)*.002f*gaitAmp));braidB=Math.max(-.6f,Math.min(.6f,braidB));
        mat[P_BRAID]=M.mul(mat[P_HEAD],pivot(BRAID_ROOT,M.mul(M.ry(-braidA),M.rx(braidB))));
    }

    // ---------------------------------------------------------------- joints <-> pose
    /** Joint positions of the current pose (and last frame's, for velocities). */
    private void storeJoints(){
        for(int j=0;j<JOINTS;j++)System.arraycopy(joints[j],0,prevJoints[j],0,3);
        float[][] J=currentJoints();for(int j=0;j<JOINTS;j++)System.arraycopy(J[j],0,joints[j],0,3);
    }
    float[][] currentJoints(){
        float[][] J=new float[JOINTS][];float[] T=mat[P_TORSO],Pm=mat[P_PELVIS];
        J[J_PELVIS]=M.pt(Pm,PELVIS);J[J_HIP_L]=M.pt(Pm,hip(1));J[J_HIP_R]=M.pt(Pm,hip(-1));J[J_NECK]=M.pt(T,NECK);J[J_SH_L]=M.pt(T,shoulder(1));J[J_SH_R]=M.pt(T,shoulder(-1));
        J[J_HEAD]=M.pt(mat[P_HEAD],HEAD);
        for(int s=-1;s<=1;s+=2){int o=s>0?0:1;
            J[J_KNEE_L+o]=M.pt(mat[sidePart(s,S_THIGH)],knee(s));J[J_ANK_L+o]=M.pt(mat[sidePart(s,S_SHIN)],ankle(s));J[J_TOE_L+o]=M.pt(mat[sidePart(s,S_FOOT)],toe(s));
            J[J_ELB_L+o]=M.pt(mat[sidePart(s,S_UPPER)],elbow(s));J[J_HAND_L+o]=M.pt(mat[sidePart(s,S_FORE)],handCentre(s));}
        J[J_SAW]=M.pt(sawMat,v(.97f,0,0));
        return J;
    }
    /** Poses every part from joint positions (the ragdoll, the get-up, and blends between them and locomotion). */
    void fromJoints(float[][] J,float dt){
        float[] up=Model.norm(Model.sub(J[J_NECK],J[J_PELVIS]));
        float[] left=Model.add(Model.sub(J[J_HIP_L],J[J_HIP_R]),Model.sub(J[J_SH_L],J[J_SH_R]),1);left=Model.norm(Model.sub(left,Model.mul(up,Model.dot(left,up))));
        float[] fwd=Model.cross(left,up);
        float[] T=M.mul(M.basis(J[J_PELVIS],fwd,left,up),M.tr(-PELVIS[0],-PELVIS[1],-PELVIS[2]));
        mat[P_TORSO]=T;mat[P_PELVIS]=T;mat[sidePart(1,S_PAULDRON)]=T;mat[sidePart(-1,S_PAULDRON)]=T;
        float[] hu=Model.norm(Model.sub(J[J_HEAD],J[J_NECK]));float[] hf=Model.norm(Model.sub(fwd,Model.mul(hu,Model.dot(fwd,hu))));float[] hl=Model.cross(hu,hf);
        float[] hb=Model.norm(Model.sub(HEAD,NECK));   // bind neck->head axis leans a touch forward
        float[] H=M.mul(M.basis(J[J_NECK],hf,hl,hu),M.mul(rotTo(hb,v(0,0,1)),M.tr(-NECK[0],-NECK[1],-NECK[2])));
        mat[P_HEAD]=H;
        braid(dt,0,0,0,(float)Math.atan2(fwd[1],fwd[0]),0);
        for(int s=-1;s<=1;s+=2){int o=s>0?0:1;
            float[] sh=J[J_SH_L+o],el=J[J_ELB_L+o],hd=J[J_HAND_L+o];
            float[] ePt=Model.sub(el,Model.mul(Model.add(sh,hd,1),.5f));if(Model.len(ePt)<.02f)ePt=Model.mul(fwd,-1);
            mat[sidePart(s,S_UPPER)]=bone(shoulder(s),elbow(s),v(-1,0,0),sh,el,ePt);
            float[] fore=bone(elbow(s),handCentre(s),v(-1,0,0),el,hd,ePt);mat[sidePart(s,S_FORE)]=fore;mat[sidePart(s,S_HAND)]=s<0?null:fore;
            float[] hp=J[J_HIP_L+o],kn=J[J_KNEE_L+o],an=J[J_ANK_L+o],to=J[J_TOE_L+o];
            float[] kPt=Model.sub(kn,Model.mul(Model.add(hp,an,1),.5f));if(Model.len(kPt)<.02f)kPt=fwd;
            mat[sidePart(s,S_THIGH)]=bone(hip(s),knee(s),v(1,0,0),hp,kn,kPt);
            mat[sidePart(s,S_SHIN)]=bone(knee(s),ankle(s),v(1,0,0),kn,an,kPt);
            mat[sidePart(s,S_FOOT)]=bone(ankle(s),toe(s),Model.sub(knee(s),ankle(s)),an,to,Model.sub(kn,an));
        }
        sawMat=M.mul(mat[sidePart(-1,S_FORE)],SAW_BIND);
        float[] caddy=M.mul(sawMat,CADDY_FRAME);for(int k=0;k<2;k++)caddyMats[k]=M.mul(caddy,M.tr(0,k==0?-Gun.BX:Gun.BX,0));
        mat[P_GLAIVE_SHAFT]=mat[P_GLAIVE_HEAD]=null;
        for(int j=0;j<JOINTS;j++){System.arraycopy(joints[j],0,prevJoints[j],0,3);System.arraycopy(J[j],0,joints[j],0,3);}
    }
    /** Rotation taking unit vector a to unit vector b. */
    static float[] rotTo(float[] a,float[] b){
        float[] c=Model.cross(a,b);float s=Model.len(c),d=Model.dot(a,b);if(s<1e-6f)return M.id();
        float[] k=Model.mul(c,1/s);float ang=(float)Math.atan2(s,d);float co=(float)Math.cos(ang),si=(float)Math.sin(ang),t=1-co;
        float x=k[0],y=k[1],z=k[2];
        return new float[]{t*x*x+co,t*x*y+si*z,t*x*z-si*y,0, t*x*y-si*z,t*y*y+co,t*y*z+si*x,0, t*x*z+si*y,t*y*z-si*x,t*z*z+co,0, 0,0,0,1};
    }
    /** Hand the body over to the locomotion facing (after a get-up). */
    void resetFacing(float yaw){bodyYaw=yaw;prevYaw=yaw;stanceYaw=yaw+mix(.08f,.18f,aim)*mix(.4f,1,hasGun?1:0);footFloor[0]=footFloor[1]=Float.NaN;pelvisDrop=0;phase=0;lastPhase=0;}
    /** The chainsaw: world matrix, how much it is raised and running (0..1), and the shells waiting in its caddy. */
    float[] sawMat;float saw;private float cutPhase;
    final float[][] caddyMats=new float[2][];int caddyCount;
    static final float[] SAW_BIND;
    /** Caddy frame in saw space: origin between the two shells' rims, x = up the shells, y = back along the forearm, z = away from her. */
    static final float[] CADDY_FRAME=M.basis(v(.2f,-.084f,-.01f),v(0,0,1),v(-1,0,0),v(0,-1,0));
    static{float[] e=elbow(-1),hc=handCentre(-1);float[] ax=Model.norm(Model.sub(hc,e));
        float[] z=Model.norm(Model.sub(v(1,0,0),Model.mul(ax,ax[0])));float[] y=Model.cross(z,ax);
        SAW_BIND=M.basis(Model.add(hc,ax,-.25f),ax,y,z);}
    private static float[] lerp3(float[] a,float[] b,float t){return v(mix(a[0],b[0],t),mix(a[1],b[1],t),mix(a[2],b[2],t));}
    /** Blend two rigid transforms (position lerp, rotation slerp). */
    static float[] mlerp(float[] A,float[] B,float t){
        if(t<=0)return A;if(t>=1)return B;
        float[] qa=quat(A),qb=quat(B);float d=qa[0]*qb[0]+qa[1]*qb[1]+qa[2]*qb[2]+qa[3]*qb[3];if(d<0){for(int i=0;i<4;i++)qb[i]=-qb[i];d=-d;}
        float[] q=new float[4];if(d>.9995f){for(int i=0;i<4;i++)q[i]=mix(qa[i],qb[i],t);}
        else{float th=(float)Math.acos(d),sn=(float)Math.sin(th),wa=(float)Math.sin((1-t)*th)/sn,wb=(float)Math.sin(t*th)/sn;for(int i=0;i<4;i++)q[i]=qa[i]*wa+qb[i]*wb;}
        float l=(float)Math.sqrt(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]+q[3]*q[3]);for(int i=0;i<4;i++)q[i]/=l;
        float x=q[0],y=q[1],z=q[2],w=q[3];
        return new float[]{1-2*(y*y+z*z),2*(x*y+z*w),2*(x*z-y*w),0, 2*(x*y-z*w),1-2*(x*x+z*z),2*(y*z+x*w),0, 2*(x*z+y*w),2*(y*z-x*w),1-2*(x*x+y*y),0,
            mix(A[12],B[12],t),mix(A[13],B[13],t),mix(A[14],B[14],t),1};
    }
    private static float[] quat(float[] m){
        float m00=m[0],m11=m[5],m22=m[10],tr=m00+m11+m22;float x,y,z,w;
        if(tr>0){float s=(float)Math.sqrt(tr+1)*2;w=.25f*s;x=(m[6]-m[9])/s;y=(m[8]-m[2])/s;z=(m[1]-m[4])/s;}
        else if(m00>m11&&m00>m22){float s=(float)Math.sqrt(1+m00-m11-m22)*2;w=(m[6]-m[9])/s;x=.25f*s;y=(m[4]+m[1])/s;z=(m[8]+m[2])/s;}
        else if(m11>m22){float s=(float)Math.sqrt(1+m11-m00-m22)*2;w=(m[8]-m[2])/s;x=(m[4]+m[1])/s;y=.25f*s;z=(m[9]+m[6])/s;}
        else{float s=(float)Math.sqrt(1+m22-m00-m11)*2;w=(m[1]-m[4])/s;x=(m[8]+m[2])/s;y=(m[9]+m[6])/s;z=.25f*s;}
        return new float[]{x,y,z,w};
    }
    private void arm(int s,float[] T,float[] target,float[] pole){
        float[] sh=shoulder(s),el=elbow(s),hc=handCentre(s);float[] shW=M.pt(T,sh);
        float l1=Model.len(Model.sub(el,sh)),l2=Model.len(Model.sub(hc,el));
        float[][] ik=ik(shW,target,Model.norm(pole),l1,l2);
        float[] back=v(-1,0,0);
        mat[sidePart(s,S_UPPER)]=bone(sh,el,back,shW,ik[0],pole);
        float[] fore=bone(el,hc,back,ik[0],ik[1],pole);
        mat[sidePart(s,S_FORE)]=fore;mat[sidePart(s,S_HAND)]=fore;
    }
    /** Rotation R about point c: T(c) R T(-c). */
    static float[] pivot(float[] c,float[] R){return M.mul(M.tr(c[0],c[1],c[2]),M.mul(R,M.tr(-c[0],-c[1],-c[2])));}
    /** Two-bone IK: {joint, end} reaching from a toward t with the joint bending toward pole. */
    static float[][] ik(float[] a,float[] t,float[] pole,float l1,float l2){
        float[] d=Model.sub(t,a);float dist=Model.len(d);float[] dir=dist>1e-5f?Model.norm(d):v(0,0,-1);
        dist=Math.max(Math.abs(l1-l2)+.002f,Math.min(l1+l2-.001f,dist));
        float c=(l1*l1+dist*dist-l2*l2)/(2*l1*dist);c=Math.max(-1,Math.min(1,c));float sn=(float)Math.sqrt(1-c*c);
        float[] n=Model.sub(pole,Model.mul(dir,Model.dot(pole,dir)));if(Model.len(n)<1e-5f)n=v(0,0,1);n=Model.norm(n);
        float[] j=Model.add(Model.add(a,dir,l1*c),n,l1*sn);
        return new float[][]{j,Model.add(a,dir,dist)};
    }
    /** Rigid transform taking a bone from bind (p0→p1, reference direction rb) to pose (q0→q1, reference rn). */
    static float[] bone(float[] p0,float[] p1,float[] rb,float[] q0,float[] q1,float[] rn){
        float[][] fb=frame(Model.sub(p1,p0),rb),fn=frame(Model.sub(q1,q0),rn);
        float[] m=M.id();
        for(int c=0;c<3;c++)for(int r=0;r<3;r++){float s=0;for(int k=0;k<3;k++)s+=fn[k][r]*fb[k][c];m[c*4+r]=s;}
        float[] rp=M.dir(m,p0);m[12]=q0[0]-rp[0];m[13]=q0[1]-rp[1];m[14]=q0[2]-rp[2];
        return m;
    }
    private static float[][] frame(float[] axis,float[] ref){
        float[] a=Model.norm(axis);float[] r=Model.sub(ref,Model.mul(a,Model.dot(ref,a)));
        if(Model.len(r)<1e-5f){r=Math.abs(a[2])<.9f?v(0,0,1):v(1,0,0);r=Model.sub(r,Model.mul(a,Model.dot(r,a)));}
        r=Model.norm(r);return new float[][]{a,r,Model.cross(a,r)};
    }
}
