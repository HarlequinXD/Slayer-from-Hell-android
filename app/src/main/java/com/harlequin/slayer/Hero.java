package com.harlequin.slayer;

import static com.harlequin.slayer.Model.v;

/**
 * The warden as the player character: a rigid-segment rig over the baked low poly (assets/hero.bin, one mesh group
 * per body part) and a procedural animation system.
 *
 * Locomotion is driven by the real velocity, not canned clips: each foot follows a gait cycle (planted while in
 * stance so it doesn't slide, then lifted on an arc through the swing) whose stride and cadence scale with speed and
 * blend from walk to run; two-bone IK places hips, knees and ankles, with heel-strike and toe-off foot roll. The pelvis
 * bobs, shifts over the stance foot, rolls and twists with the stride; the torso counter-rotates, leans into the run
 * and bends with the aim. Jumping tucks the legs and reaches for the ground on the way down; landing compresses the
 * whole body by how hard she hit. The braid swings on a spring.
 *
 * The shotgun is held with the stock in the right shoulder and the cheek on the comb: its pose (fire, break-open
 * reload) comes from Gun.pose in an "eye frame" at her helmet; both arms reach it by IK (right hand on the grip,
 * left hand on the forend, or fetching shells from the belt). Out of combat she carries it at low ready and turns
 * to face where she is going; when shooting or reloading she squares up to the camera and shoulders it.
 *
 * Model space: x forward, y to her left, z up, feet at z = 0 (the same landmarks the baker used). Pure Java.
 */
final class Hero {
    // part ids: identical to tools/bake/HeroBaker
    static final int P_TORSO=0,P_HEAD=1,P_BRAID=2,P_GLAIVE_SHAFT=3,P_GLAIVE_HEAD=4,P_FIRST_SIDE=5;
    static final int S_PAULDRON=0,S_UPPER=1,S_FORE=2,S_HAND=3,S_THIGH=4,S_SHIN=5,S_FOOT=6,PER_SIDE=7,PARTS=P_FIRST_SIDE+2*PER_SIDE;
    static int sidePart(int s,int k){return P_FIRST_SIDE+(s>0?0:PER_SIDE)+k;}
    // bind-pose landmarks (s = +1 left, -1 right)
    static float[] hip(int s){return v(0,s*.095f,.95f);}
    static float[] knee(int s){return v(.025f,s*.12f,.52f);}
    static float[] ankle(int s){return v(-.005f,s*.14f,.088f);}
    static float[] shoulder(int s){return v(0,s*.205f,1.45f);}
    static float[] elbow(int s){return s<0?v(-.03f,-.27f,1.19f):v(-.04f,.265f,1.18f);}
    static float[] wrist(int s){return s<0?v(.06f,-.3f,.985f):v(-.01f,.29f,.95f);}
    static float[] handCentre(int s){float[] e=elbow(s),w=wrist(s);return Model.add(w,Model.norm(Model.sub(w,e)),.055f);}
    static final float[] WAIST=v(0,0,1.0f),NECK=v(0,0,1.5f),EYE=v(.06f,0,1.676f),BRAID_ROOT=v(-.115f,0,1.63f),PELVIS=v(0,0,.95f);

    /** World matrix of every part (null = not drawn), and the gun's eye frame (gun-space pose matrices go after it). */
    final float[][] mat=new float[PARTS][];float[] eyeFrame;
    /** Facing, blends and timers. */
    float bodyYaw;float aim;float run;float air;float phase;float speed;
    private float aimHold,landT=9,landK,jumpT=9,braidA,braidAV,braidB,braidBV,pvx,pvy,lowReady=1;
    private boolean wasGround=true;
    /** Foot plants this frame (for footstep sounds). */
    int steps;
    private float lastPhase;

    static float wrap(float a){while(a>Math.PI)a-=2*Math.PI;while(a<-Math.PI)a+=2*Math.PI;return a;}
    static float smooth(float a,float b,float x){float t=Math.max(0,Math.min(1,(x-a)/(b-a)));return t*t*(3-2*t);}
    static float mix(float a,float b,float t){return a+(b-a)*t;}
    static float approach(float cur,float target,float rate,float dt){return cur+(target-cur)*Math.min(1,rate*dt);}

    /**
     * Advances the animation and poses every part.
     * @param camYaw camera heading; @param camPitch camera pitch (up positive); @param firing fire held.
     */
    void update(float dt,float time,Player pl,float camYaw,float camPitch,boolean firing,boolean sawHeld,Gun gun){
        steps=0;
        // ---------------------------------------------------------------- facing and stance
        if(firing||sawHeld||gun.state!=Gun.IDLE)aimHold=1.4f;else aimHold-=dt;
        boolean aiming=aimHold>0;
        aim=approach(aim,aiming?1:0,aiming?14:4,dt);
        lowReady=1-aim;
        speed=(float)Math.hypot(pl.vx,pl.vy);
        float target=aiming?camYaw:(speed>.4f?(float)Math.atan2(pl.vy,pl.vx):bodyYaw);
        float turn=wrap(target-bodyYaw);float rate=aiming?16:9;bodyYaw=wrap(bodyYaw+turn*Math.min(1,rate*dt));
        float hipOff=mix(.08f,.18f,aim),twist=mix(.04f,.12f,aim);   // the saw side leads, the shotgun side is tucked back
        float hipsYaw=bodyYaw+hipOff;
        // gait blends
        boolean ground=pl.onGround;
        air=approach(air,ground?0:1,ground?20:9,dt);
        run=approach(run,smooth(1.9f,3.6f,speed),6,dt);
        float gaitAmp=smooth(.08f,.7f,speed)*(1-air);
        float stance=mix(.62f,.35f,run);
        float exc=mix(.33f,.42f,run)*Math.max(.35f,Math.min(1,speed/1.2f));
        float stride=2*exc/stance;
        if(ground&&speed>.05f)phase+=dt*speed/stride;
        if(ground&&speed<.08f){float near=Math.round(phase*2)/2f;phase=approach(phase,near,3,dt);}   // settle into a neutral pose
        phase-=(float)Math.floor(phase);
        for(int f=0;f<2;f++){float off=f*.5f;float a=lastPhase-off,b=phase-off;a-=(float)Math.floor(a);b-=(float)Math.floor(b);if(b<a&&gaitAmp>.3f)steps++;}
        lastPhase=phase;
        // jump and landing
        if(pl.justJumped)jumpT=0;jumpT+=dt;
        if(pl.onGround&&!wasGround){landT=0;landK=Math.min(1,Math.max(.25f,pl.landSpeed/10f));}
        wasGround=pl.onGround;landT+=dt;
        float land=landT<.07f?landT/.07f:(float)Math.exp(-(landT-.07f)*7);land*=landK;
        // velocity in the hips frame
        float ch=(float)Math.cos(hipsYaw),sh=(float)Math.sin(hipsYaw);
        float lvx=ch*pl.vx+sh*pl.vy,lvy=-sh*pl.vx+ch*pl.vy;float ll=(float)Math.hypot(lvx,lvy);float dx=ll>1e-3f?lvx/ll:1,dy=ll>1e-3f?lvy/ll:0;

        // ---------------------------------------------------------------- pelvis
        float midStance=stance*.5f;
        float bob=mix(.02f*(float)Math.cos(4*Math.PI*(phase-midStance)),.04f*(float)Math.cos(4*Math.PI*(phase-midStance-.25f)),run)*gaitAmp;
        float dz=-.012f-.01f*aim-.05f*run*gaitAmp+bob-.17f*land+(air>0?.02f*air:0);
        float lat=-.022f*(float)Math.cos(2*Math.PI*(phase-midStance))*gaitAmp*(1-run);
        float pRoll=.035f*(float)Math.cos(2*Math.PI*(phase-midStance))*gaitAmp;
        float pTwist=mix(.09f,.16f,run)*(float)Math.cos(2*Math.PI*phase)*gaitAmp*(dx>=0?1:-1);
        float[] root=v(pl.x,pl.y,pl.z+pl.stepSmooth);
        float[] Hf=M.mul(M.tr(root[0],root[1],root[2]),M.rz(hipsYaw));      // ground frame under the hips
        float[] P=M.mul(Hf,M.mul(M.tr(0,lat,dz),pivot(PELVIS,M.mul(M.rz(pTwist),M.rx(pRoll)))));

        // ---------------------------------------------------------------- torso, head, braid
        float aimPitch=camPitch*aim;
        float lean=(.05f+.13f*run)*gaitAmp+.06f*land;
        float kick=gun.recoilNow;
        float torsoTwist=twist-pTwist*.75f;
        float[] Tr=M.mul(M.rz(torsoTwist),M.mul(M.ry(lean*dx-aimPitch*.35f-kick*.05f),M.rx(-lean*dy*.6f)));
        float[] T=M.mul(P,pivot(WAIST,Tr));
        mat[P_TORSO]=T;
        mat[sidePart(1,S_PAULDRON)]=T;mat[sidePart(-1,S_PAULDRON)]=T;
        float reload=gun.reloading()?1:0;
        float eyeYaw=bodyYaw+aim*wrap(camYaw-bodyYaw);
        float eyePitch=mix(aimPitch,-.32f,reload*.7f)+(gun.state==Gun.FIRE?-.06f*kick:0);
        float[] neckW=M.pt(T,NECK);
        float[] H=M.mul(M.tr(neckW[0],neckW[1],neckW[2]),M.mul(M.rz(eyeYaw+(1-aim)*.0f),M.mul(M.ry(-eyePitch*.8f),M.mul(M.rx(.12f*aim),M.tr(-NECK[0],-NECK[1],-NECK[2])))));
        mat[P_HEAD]=H;
        // braid: a damped pendulum pushed by her acceleration
        float ax=(pl.vx-pvx)/Math.max(dt,1e-3f),ay=(pl.vy-pvy)/Math.max(dt,1e-3f);pvx=pl.vx;pvy=pl.vy;
        float ce=(float)Math.cos(eyeYaw),se=(float)Math.sin(eyeYaw);float fa=ce*ax+se*ay,sa=-se*ax+ce*ay;
        braidAV+=(-braidA*40-braidAV*5+fa*.03f+(pl.vz<-1?-pl.vz*.05f:0))*dt;braidA+=braidAV*dt;
        braidBV+=(-braidB*40-braidBV*5-sa*.03f)*dt;braidB+=braidBV*dt;
        braidA=Math.max(-.6f,Math.min(.9f,braidA+(float)Math.sin(phase*4*Math.PI)*.002f*gaitAmp));braidB=Math.max(-.6f,Math.min(.6f,braidB));
        mat[P_BRAID]=M.mul(H,pivot(BRAID_ROOT,M.mul(M.ry(-braidA),M.rx(braidB))));

        // ---------------------------------------------------------------- legs (gait + IK)
        for(int s=-1;s<=1;s+=2){
            float p=phase+(s>0?.5f:0);p-=(float)Math.floor(p);
            float off,lift=0,pitch;
            if(p<stance){float q=p/stance;off=exc*(1-2*q);
                pitch=q<.15f?-.25f*(1-q/.15f):(q>.68f?.55f*(float)Math.pow((q-.68f)/.32f,2):0);
                lift=pitch>0?.17f*(float)Math.sin(pitch):0;}
            else{float q=(p-stance)/(1-stance);float e=smooth(0,1,q);off=-exc+2*exc*e;
                float h=mix(.08f,.2f,run)*Math.min(1,speed/1.4f+.3f);
                lift=h*(float)Math.sin(Math.PI*q)+run*.16f*(float)Math.sin(Math.PI*Math.min(1,q*1.5f));
                off-=run*.12f*(float)Math.sin(Math.PI*Math.min(1,q*1.4f));     // heel kicks up behind
                pitch=mix(.55f,-.18f,smooth(0,.85f,q));}
            off*=gaitAmp;lift*=gaitAmp;pitch*=gaitAmp;
            // airborne: tuck while rising, reach down while falling
            float rising=pl.vz>0?1:0;
            float aOff=(s>0?.13f:-.1f),aLift=rising>0?.3f:.1f;
            off=mix(off,aOff,air);lift=mix(lift,aLift,air);pitch=mix(pitch,rising>0?.35f:.15f,air);
            // stance width: the left foot a little forward when standing bladed
            float[] an=ankle(s);float idleFwd=(s>0?.06f:-.05f)*(1-gaitAmp)*(1-air);
            float[] targetL=v(an[0]+dx*off+idleFwd,an[1]+dy*off*.8f,an[2]+lift);
            targetL[1]=s>0?Math.max(targetL[1],.05f):Math.min(targetL[1],-.05f);   // side-steps never cross the feet
            float[] tgt=M.pt(Hf,targetL);
            float[] hipW=M.pt(P,hip(s));
            float[] fwd=M.dir(Hf,v(1,0,s*.12f*0));float[] pole=Model.norm(Model.add(fwd,M.dir(Hf,v(0,s,0)),.08f));
            float l1=Model.len(Model.sub(knee(s),hip(s))),l2=Model.len(Model.sub(ankle(s),knee(s)));
            float[][] ik=ik(hipW,tgt,pole,l1,l2);
            mat[sidePart(s,S_THIGH)]=bone(hip(s),knee(s),v(1,0,0),hipW,ik[0],pole);
            mat[sidePart(s,S_SHIN)]=bone(knee(s),an,v(1,0,0),ik[0],ik[1],pole);
            float[] aw=ik[1];
            mat[sidePart(s,S_FOOT)]=M.mul(M.tr(aw[0],aw[1],aw[2]),M.mul(M.rz(hipsYaw+s*.07f),M.mul(M.ry(pitch),M.tr(-an[0],-an[1],-an[2]))));
        }

        // ---------------------------------------------------------------- right arm: the chainsaw
        saw=approach(saw,sawHeld?1:0,sawHeld?10:5,dt);
        float cross=0;
        if(gun.state==Gun.OPEN)cross=smooth(.15f,.45f,gun.t);else if(gun.state==Gun.LOAD)cross=1;else if(gun.state==Gun.CLOSE)cross=1-smooth(0,.3f,gun.t);
        cutPhase+=dt*7*saw;
        float thrust=(float)Math.sin(cutPhase)*.05f*saw;
        float[] idleH=v(.2f,-.25f,1.03f),cutH=v(.43f+thrust,-.13f,1.22f+.02f*(float)Math.sin(cutPhase*.5f)),crossH=v(.3f,.02f,1.14f);
        float[] idleP=v(-.4f,-.6f,-.7f),cutP=v(-.2f,-.8f,-.6f),crossP=v(.25f,-1f,-.75f);
        float[] hT=lerp3(lerp3(idleH,cutH,saw),crossH,cross),pT=lerp3(lerp3(idleP,cutP,saw),crossP,cross);
        hT[2]+=(float)Math.sin(time*1.6f)*.006f+(float)Math.sin(phase*4*Math.PI)*.012f*gaitAmp;
        arm(-1,T,M.pt(T,hT),Model.norm(M.dir(T,pT)));
        mat[sidePart(-1,S_HAND)]=null;                 // her right hand is inside the sleeve
        float shake=saw>.05f?.0015f*(float)Math.sin(time*170)*(.4f+saw):.0006f*(float)Math.sin(time*110);
        sawMat=M.mul(M.mul(mat[sidePart(-1,S_FORE)],SAW_BIND),M.tr(0,shake,shake*.6f));
        float[] caddy=M.mul(sawMat,CADDY_FRAME);
        caddyCount=Math.min(2,gun.reserve);
        for(int k=0;k<2;k++)caddyMats[k]=M.mul(caddy,M.tr(0,k==0?-Gun.BX:Gun.BX,0));

        // ---------------------------------------------------------------- left hand: the shotgun, one-handed, stock under the arm
        float[] armpit=M.pt(T,v(-.035f,.165f,1.3f));
        float gy=eyeYaw-.04f*aim,gp=eyePitch;
        float[] R=M.mul(M.rz(gy+(1-aim)*.18f),M.ry(-gp+(1-aim)*.55f));
        float[] hold=M.mul(M.tr(armpit[0],armpit[1],armpit[2]),M.mul(R,M.tr(.43f,0,.06f)));
        float[] grip=v(-.1f,0,-.02f);
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
        eyeFrame=M.id();
        mat[P_GLAIVE_SHAFT]=mat[P_GLAIVE_HEAD]=null;
    }
    /** The chainsaw: world matrix, how much it is raised and running (0..1), and the shells waiting in its caddy. */
    float[] sawMat;float saw;private float cutPhase;
    final float[][] caddyMats=new float[2][];int caddyCount;
    /** Saw space -> her bind pose: the grip bar (saw x = .25) in her right fist, the engine on the side away from the elbow's point. */
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
