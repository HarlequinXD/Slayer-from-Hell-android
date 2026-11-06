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
    void update(float dt,float time,Player pl,float camYaw,float camPitch,boolean firing,Gun gun){
        steps=0;
        // ---------------------------------------------------------------- facing and stance
        if(firing||gun.state!=Gun.IDLE)aimHold=1.4f;else aimHold-=dt;
        boolean aiming=aimHold>0;
        aim=approach(aim,aiming?1:0,aiming?14:4,dt);
        lowReady=1-aim;
        speed=(float)Math.hypot(pl.vx,pl.vy);
        float target=aiming?camYaw:(speed>.4f?(float)Math.atan2(pl.vy,pl.vx):bodyYaw);
        float turn=wrap(target-bodyYaw);float rate=aiming?16:9;bodyYaw=wrap(bodyYaw+turn*Math.min(1,rate*dt));
        float hipOff=-mix(.14f,.32f,aim),twist=-mix(.22f,.3f,aim);
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

        // ---------------------------------------------------------------- the gun in the eye frame, then the arms
        float[] eyeW=M.pt(H,EYE);
        float[] lr=pivot(v(-.12f,-.12f,-.17f),M.mul(M.rz(.42f*lowReady),M.ry(.62f*lowReady)));
        float sway=(float)Math.sin(time*1.3f)*.004f*(1-aim)+(float)Math.sin(phase*4*Math.PI)*.006f*gaitAmp;
        eyeFrame=M.mul(M.tr(eyeW[0],eyeW[1],eyeW[2]+sway),M.mul(M.rz(eyeYaw),M.mul(M.ry(-eyePitch),M.mul(M.tr(-.02f,-.035f,-.055f),M.mul(M.scale(.9f,.9f,.9f),lr)))));
        gun.pose(0,0,0,0,time,dt);
        float[] G=M.mul(eyeFrame,gun.mat[Gun.P_ACTION]),Lh=M.mul(eyeFrame,gun.mat[Gun.P_LHAND]);
        float[] rightGrip=M.pt(G,v(-.1f,-.004f,-.016f)),leftGrip=M.pt(Lh,v(-.005f,0,-.024f));
        arm(-1,T,rightGrip,M.dir(T,v(-.25f,-.75f,-.8f)));
        arm(1,T,leftGrip,M.dir(T,v(-.1f,.55f,-1f)));
        mat[P_GLAIVE_SHAFT]=mat[P_GLAIVE_HEAD]=null;
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
