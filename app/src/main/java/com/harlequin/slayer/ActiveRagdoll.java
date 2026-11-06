package com.harlequin.slayer;

import static com.harlequin.slayer.Hero.*;
import static com.harlequin.slayer.Model.v;

/**
 * Character simulation manager for the warden: owns her articulated physical body (PhysWorld), decides who has
 * authority over the pose each frame, and runs the physical reactions.
 *
 * Update order (one owner per bone at a time, so controllers never fight):
 *   1. Hero (procedural animation) computes the animated pose: the part matrices.
 *   2. This class turns that pose into muscle targets (joint-space orientations and angular velocities) and pelvis
 *      targets for the root assist, or into protective / recovery targets when falling or getting up.
 *   3. PhysWorld steps at a fixed rate (muscle torques, joints, limits, contacts).
 *   4. The rendered pose comes from the animation (KINEMATIC), from the bodies (POWERED, FALL, DOWN, RECOVER), or
 *      a blend of the two (BLEND_OUT).
 *
 * Modes:
 *   KINEMATIC  hybrid default: the animation drives; the bodies follow it kinematically (keeping velocities), so the
 *              hand-over to physics is continuous.
 *   POWERED    a physical reaction (push, impact, wall bump): full-strength muscles track the animation and the root
 *              assist holds her upright while the impulse plays out through the joints. Balance is evaluated from
 *              the centre of mass, its velocity and the support polygon of the planted feet (capture point): a
 *              small loss of balance becomes recovery steps (the locomotion takes them); a large one becomes a fall.
 *   FALL       balance lost: the root assist lets go and muscles drive a protective pose (free hand reaching to
 *              where she will land, head tucked, saw held away, knees soft). Hard impacts cut muscle strength.
 *   DOWN       on the ground: muscles relax and she settles.
 *   RECOVER    getting up: authored reference poses (RecoveryPoses, IK-solved) become muscle targets and the root
 *              assist ramps in to lift her. Face down and face up have their own sequences.
 *   BLEND_OUT  hand back to animation with a short pose blend.
 */
final class ActiveRagdoll {
    static final int KINEMATIC=0,POWERED=1,FALL=2,DOWN=3,RECOVER=4,BLEND_OUT=5;
    static final String[] MODE_NAMES={"animated","reacting","falling","down","getting up","blending"};
    /** Gameplay events (OnFootstep, OnLand and OnJump come from the locomotion; see Game). */
    interface Events {
        void onImpact(float speed);void onStumble();void onFall();void onBalanceLost();void onBalanceRestored();void onRecover();
        void onMuscleFatigue(String joint);void onMovementStateChanged(String from,String to);
    }
    final SimSettings S;final PhysWorld world;final Hero hero;private final Hero poser=new Hero();
    Events events;
    int mode=KINEMATIC;float modeT;
    // bodies
    RigidBody pelvis,chest,head;final RigidBody[] upper=new RigidBody[2],fore=new RigidBody[2],thigh=new RigidBody[2],shin=new RigidBody[2],foot=new RigidBody[2];
    private final float[][] prevTargetQ;private boolean havePrev;
    // balance
    final float[] com=new float[3],comVel=new float[3],capture=new float[3];float stability,supportArea;float[][] support=new float[0][];
    private boolean wasBalanced=true;
    // recovery
    private boolean supine;private final float[] gO=new float[3];private float gYaw,recoverT;
    float[] standAt=new float[3];float standYaw;
    private float[][] blendFrom;private float blendT;
    float strength=1;private float lastImpactT;
    /** Requested by POWERED when balance needs a step: horizontal push for the player controller. */
    final float[] stepRequest=new float[2];

    ActiveRagdoll(SimSettings s,Level level,Hero hero){
        S=s;this.hero=hero;world=new PhysWorld(s,level);build();prevTargetQ=new float[world.bodies.size()][4];
    }

    // ---------------------------------------------------------------- the biomechanical skeleton
    /**
     * Segments, masses and inertias from body height and mass. Mass fractions follow published anthropometric
     * tables for women (de Leva 1996, adjusted for the armour: trunk split at the lumbar joint into pelvis and
     * chest); inertia comes from each segment's capsule. The chainsaw (and the shotgun while held) add their
     * mass to the forearms. Joint limits are anatomical ranges in radians.
     */
    private void build(){
        float Mt=S.totalMass,hs=S.height/1.8f;
        pelvis=body("pelvis",P_PELVIS,v(0,0,1.0f),.19f*Mt);pelvis.addCapsule(v(0,.09f,.98f),v(0,-.09f,.98f),.13f);pelvis.setCapsuleInertia(v(0,.09f,.98f),v(0,-.09f,.98f),.13f);
        chest=body("chest",P_TORSO,v(0,0,1.3f),.235f*Mt);chest.addCapsule(v(0,.12f,1.34f),v(0,-.12f,1.34f),.14f);chest.addCapsule(v(0,0,1.18f),v(0,0,1.42f),.12f);chest.setCapsuleInertia(v(0,0,1.15f),v(0,0,1.45f),.15f);
        head=body("head",P_HEAD,v(.01f,0,1.66f),.067f*Mt);head.addCapsule(v(.01f,0,1.6f),v(.02f,0,1.72f),.115f);head.setCapsuleInertia(v(.01f,0,1.6f),v(.02f,0,1.72f),.115f);
        joint("lumbar",pelvis,chest,PhysJoint.BALL,v(0,0,1.12f),v(0,0,1),v(1,0,0)).limits(.55f,-.5f,.5f).torque(320);
        joint("neck",chest,head,PhysJoint.BALL,NECK,v(0,0,1),v(1,0,0)).limits(.7f,-1f,1f).torque(70);
        for(int si=0;si<2;si++){int s=si==0?1:-1;
            float[] sh=shoulder(s),el=elbow(s),hc=handCentre(s),hp=hip(s),kn=knee(s),an=ankle(s),to=toe(s);
            upper[si]=body((s>0?"L":"R")+" upper arm",sidePart(s,S_UPPER),lerp(sh,el,.45f),.026f*Mt);upper[si].addCapsule(sh,el,.055f);upper[si].setCapsuleInertia(sh,el,.055f);
            fore[si]=body((s>0?"L":"R")+" forearm",sidePart(s,S_FORE),lerp(el,hc,.45f),.02f*Mt);fore[si].addCapsule(el,hc,.05f);fore[si].setCapsuleInertia(el,hc,.05f);
            thigh[si]=body((s>0?"L":"R")+" thigh",sidePart(s,S_THIGH),lerp(hp,kn,.43f),.148f*Mt);thigh[si].addCapsule(hp,kn,.075f);thigh[si].setCapsuleInertia(hp,kn,.075f);
            shin[si]=body((s>0?"L":"R")+" shin",sidePart(s,S_SHIN),lerp(kn,an,.44f),.048f*Mt);shin[si].addCapsule(kn,an,.055f);shin[si].setCapsuleInertia(kn,an,.055f);
            float[] heel=v(an[0]-.06f,an[1],.035f),tip=v(to[0],to[1],.03f);
            foot[si]=body((s>0?"L":"R")+" foot",sidePart(s,S_FOOT),lerp(heel,tip,.45f),.013f*Mt);foot[si].addCapsule(heel,tip,.04f);foot[si].setCapsuleInertia(heel,tip,.04f);
            joint((s>0?"L":"R")+" shoulder",chest,upper[si],PhysJoint.BALL,sh,Model.sub(el,sh),v(1,0,0)).limits(2.5f,-1.2f,1.2f).torque(s<0?170:95);   // the saw arm is trained for the weight
            joint((s>0?"L":"R")+" elbow",upper[si],fore[si],PhysJoint.BALL,el,Model.sub(hc,el),v(1,0,0)).limits(2.5f,-1.4f,1.4f).torque(s<0?130:75);   // the arm IK places the elbow plane freely, so the elbow is a limited ball rather than a strict hinge
            joint((s>0?"L":"R")+" hip",pelvis,thigh[si],PhysJoint.BALL,hp,Model.sub(kn,hp),v(1,0,0)).limits(1.9f,-.7f,.7f).torque(260);
            joint((s>0?"L":"R")+" knee",thigh[si],shin[si],PhysJoint.HINGE,kn,v(0,1,0),v(0,0,-1)).limits(0,-.05f,2.4f).torque(230);
            joint((s>0?"L":"R")+" ankle",shin[si],foot[si],PhysJoint.BALL,an,Model.sub(to,an),v(0,0,1)).limits(.75f,-.35f,.35f).torque(130);
        }
        // the chainsaw on the right forearm: its mass, and colliders for the engine and the bar
        RigidBody rf=fore[1];float addM=S.sawMass;float[] sawCom=M.pt(SAW_BIND,v(.4f,0,.06f));
        float[] c=v((rf.bindCom[0]*rf.mass+sawCom[0]*addM)/(rf.mass+addM),(rf.bindCom[1]*rf.mass+sawCom[1]*addM)/(rf.mass+addM),(rf.bindCom[2]*rf.mass+sawCom[2]*addM)/(rf.mass+addM));
        RigidBody saw=new RigidBody("R forearm + chainsaw",rf.part,c,rf.mass+addM);
        saw.addCapsule(elbow(-1),handCentre(-1),.05f);saw.addCapsule(M.pt(SAW_BIND,v(.08f,0,.1f)),M.pt(SAW_BIND,v(.3f,0,.1f)),.07f);saw.addCapsule(M.pt(SAW_BIND,v(.42f,0,0)),M.pt(SAW_BIND,v(.97f,0,0)),.035f);
        saw.setCapsuleInertia(elbow(-1),M.pt(SAW_BIND,v(.97f,0,0)),.07f);
        replace(rf,saw);fore[1]=saw;
        for(RigidBody b:world.bodies){b.linDamp=S.linearDamping;b.angDamp=S.angularDamping;}
        world.root=pelvis;world.finish();
    }
    private static float[] lerp(float[] a,float[] b,float t){return v(a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t,a[2]+(b[2]-a[2])*t);}
    private RigidBody body(String n,int part,float[] com,float m){RigidBody b=new RigidBody(n,part,com,m);world.bodies.add(b);return b;}
    private PhysJoint joint(String n,RigidBody p,RigidBody c,int type,float[] anchor,float[] axis,float[] ref){PhysJoint j=new PhysJoint(n,p,c,type,anchor,axis,ref);world.joints.add(j);return j;}
    private void replace(RigidBody old,RigidBody nb){int i=world.bodies.indexOf(old);world.bodies.set(i,nb);
        for(int k=0;k<world.joints.size();k++){PhysJoint j=world.joints.get(k);if(j.child==old||j.parent==old){
            PhysJoint r=new PhysJoint(j.name,j.parent==old?nb:j.parent,j.child==old?nb:j.child,j.type,Model.add(j.anchorP,j.parent.bindCom,1),j.axisP,j.refP);
            r.limits(j.swingMax,j.twistMin,j.twistMax).torque(j.maxTorque);world.joints.set(k,r);}}}

    // ---------------------------------------------------------------- per frame
    /**
     * Called after Hero.update. Returns true when the physics owns the rendered pose (the hero's part matrices have
     * been overwritten with it).
     */
    boolean update(float dt,float time,Player pl){
        modeT+=dt;
        if(mode==KINEMATIC){followAnimation(dt);updateBalance();return false;}
        float[][] anim=snapshot();   // the animation's pose this frame (targets in POWERED, destination in BLEND_OUT)
        switch(mode){
            case POWERED:{setTargets(anim,dt);world.rootAssist=1;world.weightSupport=1;setRoot(anim[P_PELVIS],dt);strengthTo(1,dt,4);
                world.update(dt);updateBalance();
                // balance: capture point vs support polygon (margin grows with available strength)
                float margin=S.balanceMargin*strength;
                if(stability<-margin*2.2f&&modeT>.05f){fall();break;}
                if(stability<-margin){stepRequest[0]=capture[0]-com[0];stepRequest[1]=capture[1]-com[1];if(wasBalanced&&events!=null)events.onStumble();wasBalanced=false;}
                else{if(!wasBalanced&&events!=null)events.onBalanceRestored();wasBalanced=true;stepRequest[0]=stepRequest[1]=0;}
                if(modeT>.45f&&maxError()<.18f&&Model.len(world.comVelocity())<2.5f)beginBlendOut(pl,false);
                break;}
            case FALL:case DOWN:{protectiveTargets(dt);world.rootAssist=0;world.update(dt);updateBalance();impacts(time);
                float target=mode==FALL?S.fallStrength:(modeT>.5f?S.groundStrength:.4f);strengthTo(target,dt,mode==FALL?1.5f:3);
                float ke=world.kineticEnergy()/world.totalMass();
                boolean low=pelvis.x[2]-floorAt(pelvis.x)<.35f&&chest.x[2]-floorAt(chest.x)<.4f;
                if(mode==FALL&&low&&modeT>.3f)setMode(DOWN);
                if(mode==FALL&&modeT>3.5f)setMode(DOWN);
                if(mode==DOWN){settle=ke<.05f?settle+dt:Math.max(0,settle-dt);if((settle>S.settleTime&&modeT>1f)||modeT>4f)beginRecover();}
                break;}
            case RECOVER:{recoverTargets(dt);world.update(dt);updateBalance();impacts(time);strengthTo(S.recoverStrength,dt,3);
                float[] keyT=mode==RECOVER?(supine?RecoveryPoses.BACK_T:RecoveryPoses.FRONT_T):null;
                if(recoverT>keyT[keyT.length-1]+.15f&&(maxError()<.35f||recoverT>keyT[keyT.length-1]+1.2f))beginBlendOut(pl,true);
                break;}
            case BLEND_OUT:{blendT+=dt;float b=smooth(0,.35f,blendT);
                for(int p=0;p<PARTS;p++)if(anim[p]!=null&&blendFrom[p]!=null)hero.mat[p]=Hero.mlerp(blendFrom[p],anim[p],b);
                hero.sawMat=M.mul(hero.mat[sidePart(-1,S_FORE)],SAW_BIND);
                followAnimation(dt);
                if(blendT>=.35f){setMode(KINEMATIC);}
                return true;}
        }
        writePose();
        return true;
    }
    private float settle;
    private float floorAt(float[] p){return world.level.floorUnder(p[0],p[1],.05f);}
    private void strengthTo(float t,float dt,float rate){strength+=(t-strength)*Math.min(1,dt*rate);for(PhysJoint j:world.joints)j.strength=strength;}
    private float maxError(){float e=0;for(PhysJoint j:world.joints)e=Math.max(e,j.lastError);return e;}
    private void setMode(int m){if(m==mode)return;String from=MODE_NAMES[mode];mode=m;modeT=0;if(events!=null)events.onMovementStateChanged(from,MODE_NAMES[m]);}

    /** The animation's part matrices this frame (copied; hero.mat is overwritten when physics owns the pose). */
    private float[][] snapshot(){float[][] a=new float[PARTS][];for(int p=0;p<PARTS;p++)a[p]=hero.mat[p]==null?null:hero.mat[p].clone();
        if(a[P_PELVIS]==null)a[P_PELVIS]=a[P_TORSO];return a;}
    /** KINEMATIC: bodies take the animated pose (with velocities) so the next reaction starts from real motion. */
    private void followAnimation(float dt){
        float[][] a=snapshot();
        for(RigidBody b:world.bodies){float[] m=a[b.part];if(m==null)continue;
            float[] ox=b.x.clone(),oq=b.q.clone();b.setFromPartMatrix(m);
            if(havePrev&&dt>0){for(int k=0;k<3;k++)b.v[k]=(b.x[k]-ox[k])/dt;float[] dq=Q.mul(b.q,Q.conj(oq));float s=dq[3]<0?-2/dt:2/dt;b.w[0]=dq[0]*s;b.w[1]=dq[1]*s;b.w[2]=dq[2]*s;}
            System.arraycopy(b.x,0,b.xp,0,3);System.arraycopy(b.q,0,b.qp,0,4);}
        for(int i=0;i<world.bodies.size();i++)System.arraycopy(world.bodies.get(i).q,0,prevTargetQ[i],0,4);
        havePrev=true;
    }
    /** Muscle targets from part matrices: each joint's child orientation relative to its parent, and its rate of change. */
    private void setTargets(float[][] parts,float dt){
        float[][] tq=new float[world.bodies.size()][];
        for(int i=0;i<world.bodies.size();i++){RigidBody b=world.bodies.get(i);float[] m=parts[b.part];tq[i]=m==null?b.q.clone():Q.fromMatrix(m);}
        for(PhysJoint j:world.joints){int pi=world.bodies.indexOf(j.parent),ci=world.bodies.indexOf(j.child);
            float[] rel=Q.mul(Q.conj(tq[pi]),tq[ci]);System.arraycopy(rel,0,j.targetRel,0,4);
            if(havePrev&&dt>0){float[] wc=angVel(prevTargetQ[ci],tq[ci],dt),wp=angVel(prevTargetQ[pi],tq[pi],dt);for(int k=0;k<3;k++)j.targetW[k]=Math.max(-12,Math.min(12,wc[k]-wp[k]));}
            else java.util.Arrays.fill(j.targetW,0);}
        for(int i=0;i<tq.length;i++)System.arraycopy(tq[i],0,prevTargetQ[i],0,4);havePrev=true;
        // per-body assist targets
        int n=world.bodies.size();if(world.tgtX==null){world.tgtX=new float[n][];world.tgtV=new float[n][];world.tgtQ=new float[n][];}
        for(int i=0;i<n;i++){RigidBody b=world.bodies.get(i);float[] m=parts[b.part];if(m==null){world.tgtX[i]=null;continue;}
            float[] x=M.pt(m,b.bindCom);float[] v=new float[3];if(world.tgtX[i]!=null&&dt>0)for(int k=0;k<3;k++)v[k]=Math.max(-8,Math.min(8,(x[k]-world.tgtX[i][k])/dt));
            world.tgtX[i]=x;world.tgtV[i]=v;world.tgtQ[i]=tq[i];}
    }
    /** Forget assist targets (their velocities would be wrong after a jump in targets). */
    private void resetAssistTargets(){world.tgtX=null;}
    private static float[] angVel(float[] q0,float[] q1,float dt){float[] dq=Q.mul(q1,Q.conj(q0));float[] r=new float[3];Q.toRotVec(dq,r);return Model.mul(r,1/dt);}
    private final float[] prevRootX=new float[3];
    private void setRoot(float[] pelvisPart,float dt){
        float[] x=M.pt(pelvisPart,pelvis.bindCom);float[] q=Q.fromMatrix(pelvisPart);
        for(int k=0;k<3;k++){world.rootV[k]=dt>0?(x[k]-prevRootX[k])/dt:0;prevRootX[k]=x[k];world.rootX[k]=x[k];}
        if(Model.len(world.rootV)>15)java.util.Arrays.fill(world.rootV,0);
        System.arraycopy(q,0,world.rootQ,0,4);java.util.Arrays.fill(world.rootW,0);
    }
    /** Renders from the bodies: part matrices, the saw and the caddy follow the right forearm. */
    private void writePose(){
        float[] headOld=hero.mat[P_HEAD],braidOld=hero.mat[P_BRAID];
        for(RigidBody b:world.bodies)hero.mat[b.part]=b.partMatrix();
        hero.mat[sidePart(1,S_PAULDRON)]=hero.mat[P_TORSO];hero.mat[sidePart(-1,S_PAULDRON)]=hero.mat[P_TORSO];
        hero.mat[sidePart(1,S_HAND)]=hero.mat[sidePart(1,S_FORE)];hero.mat[sidePart(-1,S_HAND)]=null;
        if(headOld!=null&&braidOld!=null)hero.mat[P_BRAID]=M.mul(hero.mat[P_HEAD],M.mul(rigidInverse(headOld),braidOld));else hero.mat[P_BRAID]=hero.mat[P_HEAD];
        hero.sawMat=M.mul(hero.mat[sidePart(-1,S_FORE)],SAW_BIND);
        float[] caddy=M.mul(hero.sawMat,CADDY_FRAME);for(int k=0;k<2;k++)hero.caddyMats[k]=M.mul(caddy,M.tr(0,k==0?-Gun.BX:Gun.BX,0));
        float[][] J=hero.currentJoints();for(int j=0;j<JOINTS;j++){System.arraycopy(hero.joints[j],0,hero.prevJoints[j],0,3);System.arraycopy(J[j],0,hero.joints[j],0,3);}
    }
    static float[] rigidInverse(float[] m){float[] r={m[0],m[4],m[8],0,m[1],m[5],m[9],0,m[2],m[6],m[10],0,0,0,0,1};
        r[12]=-(r[0]*m[12]+r[4]*m[13]+r[8]*m[14]);r[13]=-(r[1]*m[12]+r[5]*m[13]+r[9]*m[14]);r[14]=-(r[2]*m[12]+r[6]*m[13]+r[10]*m[14]);return r;}

    // ---------------------------------------------------------------- balance
    /** Centre of mass, its velocity, the support polygon of grounded feet (and knees/hands when down), the capture point and the stability margin. */
    private void updateBalance(){
        float[] c=world.centerOfMass(),cv=world.comVelocity();System.arraycopy(c,0,com,0,3);System.arraycopy(cv,0,comVel,0,3);
        java.util.ArrayList<float[]> pts=new java.util.ArrayList<>();
        for(int si=0;si<2;si++){RigidBody f=foot[si];float fl=floorAt(f.x);
            float[][] cp={f.bindToWorld(v(ankle(si==0?1:-1)[0]-.07f,ankle(si==0?1:-1)[1],.0f)),f.bindToWorld(v(toe(si==0?1:-1)[0]+.02f,toe(si==0?1:-1)[1],.0f))};
            for(float[] p:cp)if(p[2]-fl<.06f||mode==KINEMATIC&&f.x[2]-fl<.12f)pts.add(p);}
        support=hull(pts);
        float zc=Math.max(.2f,com[2]-floorAt(com));float w0=(float)Math.sqrt(S.gravity/zc);
        capture[0]=com[0]+comVel[0]/w0;capture[1]=com[1]+comVel[1]/w0;capture[2]=floorAt(com);
        stability=support.length>=3?-distOutside(capture,support):support.length>0?-(float)Math.hypot(capture[0]-support[0][0],capture[1]-support[0][1])+.08f:-1;
        supportArea=0;for(int i=0;i<support.length;i++){float[] a=support[i],b=support[(i+1)%support.length];supportArea+=a[0]*b[1]-b[0]*a[1];}supportArea=Math.abs(supportArea)*.5f;
    }
    /** Convex hull (xy) of contact points. */
    private static float[][] hull(java.util.ArrayList<float[]> pts){
        if(pts.size()<3)return pts.toArray(new float[0][]);
        pts.sort((a,b)->a[0]!=b[0]?Float.compare(a[0],b[0]):Float.compare(a[1],b[1]));
        float[][] h=new float[pts.size()*2][];int k=0;
        for(float[] p:pts){while(k>=2&&cr(h[k-2],h[k-1],p)<=0)k--;h[k++]=p;}
        for(int i=pts.size()-2,t=k+1;i>=0;i--){float[] p=pts.get(i);while(k>=t&&cr(h[k-2],h[k-1],p)<=0)k--;h[k++]=p;}
        float[][] o=new float[Math.max(0,k-1)][];System.arraycopy(h,0,o,0,o.length);return o;
    }
    private static float cr(float[] o,float[] a,float[] b){return (a[0]-o[0])*(b[1]-o[1])-(a[1]-o[1])*(b[0]-o[0]);}
    /** Signed distance of p outside a convex polygon (negative inside). */
    private static float distOutside(float[] p,float[][] poly){
        float worst=-1e9f;boolean inside=true;float best=1e9f;
        for(int i=0;i<poly.length;i++){float[] a=poly[i],b=poly[(i+1)%poly.length];float ex=b[0]-a[0],ey=b[1]-a[1];float l=(float)Math.hypot(ex,ey);if(l<1e-6f)continue;
            float side=((p[0]-a[0])*ey-(p[1]-a[1])*ex)/l;   // > 0 outside for a counter-clockwise polygon
            worst=Math.max(worst,side);if(side>0)inside=false;
            float t=Math.max(0,Math.min(1,((p[0]-a[0])*ex+(p[1]-a[1])*ey)/(l*l)));best=Math.min(best,(float)Math.hypot(p[0]-a[0]-ex*t,p[1]-a[1]-ey*t));}
        return inside?worst:best;
    }

    // ---------------------------------------------------------------- reactions
    /** An impulse (N·s) on a body at a world point; the body becomes a powered ragdoll and balance decides the rest. */
    void applyImpact(RigidBody b,float[] point,float[] impulse){
        if(mode==KINEMATIC||mode==BLEND_OUT){setMode(POWERED);for(RigidBody r:world.bodies)r.kinematic=false;strength=1;settle=0;wasBalanced=true;System.arraycopy(pelvis.x,0,prevRootX,0,3);resetAssistTargets();}
        b.applyImpulse(impulse,Model.sub(point,b.x));
        strength=Math.max(.35f,strength-Model.len(impulse)/220f);for(PhysJoint j:world.joints)j.strength=strength;   // a hard hit momentarily overwhelms the muscles
        if(events!=null)events.onImpact(Model.len(impulse)/b.mass);
    }
    /** Push the chest from a direction (horizontal unit vector) with an impulse in N·s. */
    void push(float dx,float dy,float impulse){float[] p=chest.bindToWorld(v(0,0,1.32f));applyImpact(chest,p,v(dx*impulse,dy*impulse,impulse*.08f));}
    /** Drop straight into a fall (the RAGDOLL button, a big drop), with an extra shove. */
    void fallNow(float[] shove){
        if(mode==KINEMATIC||mode==BLEND_OUT)for(RigidBody r:world.bodies)r.kinematic=false;
        for(RigidBody r:world.bodies){float k=r==chest||r==head||r==upper[0]||r==upper[1]?1f:r==pelvis?.6f:.25f;for(int i=0;i<3;i++)r.v[i]+=shove[i]*k;}
        fall();
    }
    private void fall(){setMode(FALL);strength=Math.min(strength,S.fallStrength);wasBalanced=false;if(events!=null){events.onBalanceLost();events.onFall();}}
    private float lastKE;
    private void impacts(float time){
        // a hard landing of the head, chest or pelvis knocks the strength out for a moment
        for(RigidBody b:new RigidBody[]{pelvis,chest,head}){if(!b.grounded)continue;float vz=-b.v[2];if(vz>2.2f&&time-lastImpactT>.15f){lastImpactT=time;strength=Math.min(strength,S.impactStrength);if(events!=null)events.onImpact(vz);}}
        for(PhysJoint j:world.joints)if(j.fatigue>.6f&&events!=null&&time-lastImpactT>.5f){events.onMuscleFatigue(j.name);lastImpactT=time;}
    }
    /** FALL / DOWN targets: protect the head and body, keep the saw away. Built as joint positions, solved to part matrices. */
    private void protectiveTargets(float dt){
        float[][] J=new float[JOINTS][];
        J[J_PELVIS]=pelvis.bindToWorld(PELVIS);J[J_HIP_L]=pelvis.bindToWorld(hip(1));J[J_HIP_R]=pelvis.bindToWorld(hip(-1));
        J[J_NECK]=chest.bindToWorld(NECK);J[J_SH_L]=chest.bindToWorld(shoulder(1));J[J_SH_R]=chest.bindToWorld(shoulder(-1));J[J_HEAD]=head.bindToWorld(HEAD);
        for(int si=0;si<2;si++){int s=si==0?1:-1;
            J[J_KNEE_L+si]=thigh[si].bindToWorld(knee(s));J[J_ANK_L+si]=shin[si].bindToWorld(ankle(s));J[J_TOE_L+si]=foot[si].bindToWorld(toe(s));
            J[J_ELB_L+si]=upper[si].bindToWorld(elbow(s));J[J_HAND_L+si]=fore[si].bindToWorld(handCentre(s));}
        J[J_SAW]=fore[1].bindToWorld(sawTipBind());
        float[] up=Model.norm(Model.sub(J[J_NECK],J[J_PELVIS]));float[] left=Model.norm(Model.sub(J[J_SH_L],J[J_SH_R]));float[] fwd=Model.norm(Model.cross(left,up));
        if(mode==FALL){
            // free hand: reach for where the chest is going to land
            float[] cv=chest.v;float hv=(float)Math.hypot(cv[0],cv[1]);float[] dir=hv>.3f?v(cv[0]/hv,cv[1]/hv,0):Model.norm(v(fwd[0],fwd[1],0));
            float fl=floorAt(chest.x);float[] reach=v(J[J_NECK][0]+dir[0]*.35f+left[0]*.15f,J[J_NECK][1]+dir[1]*.35f+left[1]*.15f,Math.max(fl+.05f,J[J_NECK][2]-.55f));
            float[][] ia=ik(J[J_SH_L],reach,Model.mul(fwd,-1),.27f,.28f);J[J_ELB_L]=ia[0];J[J_HAND_L]=ia[1];
            // saw arm held up and out, away from the head
            float[] sawH=Model.add(Model.add(J[J_SH_R],Model.mul(left,-1),.3f),Model.add(fwd,up,1),.18f);float[][] ir=ik(J[J_SH_R],sawH,Model.add(Model.mul(left,-1),up,-.5f),.27f,.28f);J[J_ELB_R]=ir[0];J[J_HAND_R]=ir[1];
            // head tucked, knees soft
            J[J_HEAD]=Model.add(J[J_NECK],Model.norm(Model.add(up,fwd,.35f)),.175f);
        }
        poser.fromJoints(J,dt);
        float[][] parts=new float[PARTS][];for(int p=0;p<PARTS;p++)parts[p]=poser.mat[p];parts[P_PELVIS]=poser.mat[P_TORSO];
        // the pelvis/chest split: keep the lumbar target as it is (no bending the spine on the way down)
        parts[P_PELVIS]=pelvis.partMatrix();parts[P_TORSO]=chest.partMatrix();
        if(mode==FALL){for(int si=0;si<2;si++){float[] kneeBend=M.mul(thigh[si].partMatrix(),M.id());parts[sidePart(si==0?1:-1,S_THIGH)]=kneeBend;}}
        setTargets(parts,dt);
        for(PhysJoint j:world.joints)if(j.name.endsWith("knee")&&mode==FALL){float[] r=Q.mul(Q.conj(j.parent.q),j.child.q);float[] bend=Q.fromMatrix(M.ry(.5f));System.arraycopy(Q.slerp(r,bend,.5f),0,j.targetRel,0,4);}
    }

    // ---------------------------------------------------------------- recovery (get-up)
    private void beginRecover(){
        setMode(RECOVER);recoverT=0;resetAssistTargets();
        float[] up=Model.norm(Model.sub(chest.bindToWorld(NECK),pelvis.bindToWorld(PELVIS)));float[] left=Model.norm(Model.sub(pelvis.bindToWorld(hip(1)),pelvis.bindToWorld(hip(-1))));float[] chestFwd=Model.cross(left,up);
        supine=chestFwd[2]>0;
        float[] axis=v(up[0],up[1],0);if(supine)axis=Model.mul(axis,-1);if(Model.len(axis)<.05f)axis=v(chestFwd[0],chestFwd[1],0);
        gYaw=(float)Math.atan2(axis[1],axis[0]);
        float[] pc=pelvis.bindToWorld(PELVIS);gO[0]=pc[0];gO[1]=pc[1];gO[2]=floorAt(pc);
        float[][] keys=supine?RecoveryPoses.BACK:RecoveryPoses.FRONT;float[] last=keys[keys.length-1];
        float c=(float)Math.cos(gYaw),s=(float)Math.sin(gYaw);standAt=v(gO[0]+c*last[0]-s*last[1],gO[1]+s*last[0]+c*last[1],gO[2]);standYaw=gYaw;
        if(events!=null)events.onRecover();
    }
    private void recoverTargets(float dt){
        recoverT+=dt;float lead=.35f;
        float[][] keys=supine?RecoveryPoses.BACK:RecoveryPoses.FRONT;float[] times=supine?RecoveryPoses.BACK_T:RecoveryPoses.FRONT_T;
        float[] key=RecoveryPoses.sample(keys,times,Math.max(0,recoverT-lead));
        float[][] J=RecoveryPoses.poseJoints(key,gO,gYaw,world.level);
        poser.fromJoints(J,dt);float[][] parts=new float[PARTS][];for(int p=0;p<PARTS;p++)parts[p]=poser.mat[p];parts[P_PELVIS]=poser.mat[P_TORSO];
        setTargets(parts,dt);
        // the root assist ramps in as she starts to push herself up; it carries part of her weight
        float ramp=smooth(lead,lead+.8f,recoverT);world.rootAssist=ramp;world.weightSupport=ramp*.9f;setRoot(parts[P_PELVIS],dt);
    }
    private void beginBlendOut(Player pl,boolean fromGround){
        blendFrom=new float[PARTS][];for(RigidBody b:world.bodies)blendFrom[b.part]=b.partMatrix();
        blendFrom[sidePart(1,S_PAULDRON)]=blendFrom[P_TORSO];blendFrom[sidePart(-1,S_PAULDRON)]=blendFrom[P_TORSO];blendFrom[sidePart(1,S_HAND)]=blendFrom[sidePart(1,S_FORE)];
        blendFrom[P_BRAID]=hero.mat[P_BRAID];
        if(fromGround){pl.x=standAt[0];pl.y=standAt[1];pl.z=world.level.floorUnder(pl.x,pl.y,Player.RADIUS);pl.vx=pl.vy=pl.vz=0;pl.onGround=true;hero.resetFacing(standYaw);}
        for(RigidBody r:world.bodies)r.kinematic=true;
        blendT=0;setMode(BLEND_OUT);world.rootAssist=0;
    }
    /** True while the player controller should not move her (falling, down, getting up). */
    boolean grounded(){return mode==FALL||mode==DOWN||mode==RECOVER;}
    void setKinematic(){for(RigidBody r:world.bodies)r.kinematic=true;}
}
