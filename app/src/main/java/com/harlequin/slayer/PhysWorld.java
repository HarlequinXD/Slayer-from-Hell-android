package com.harlequin.slayer;

import java.util.ArrayList;

/**
 * Articulated rigid-body physics for the character, written for this engine (there is no third-party physics
 * backend). It follows the substepped XPBD scheme for rigid bodies (Müller et al., "Detailed Rigid Body Simulation
 * with Extended Position Based Dynamics", 2020): each fixed step is split into substeps; in each one forces and
 * muscle torques are integrated explicitly, then joint anchors, joint limits and contacts are solved as positional
 * constraints, velocities are derived from the change in pose, and finally a velocity pass applies restitution,
 * dynamic friction and joint damping. Small substeps make stiff joints and stiff muscles stable without big
 * iteration counts.
 *
 * Contacts are against the level's grid: per-cell floors (steps and stairs), walls (solid cells, or floors too high
 * to be a step) and ceilings, tested with spheres along each body's capsules. Static friction is positional (a
 * planted foot stays planted); dynamic friction and restitution act on velocities.
 *
 * Muscles (see PhysJoint) are PD torques. Their gains come from the settings as a natural frequency and damping ratio
 * times each joint's effective inertia, and are clamped to what the substep can integrate stably (ω·h < 0.45,
 * Kd·h < 0.9·I), so a designer can't dial in a setting that explodes.
 *
 * A "root assist" (the classic powered-ragdoll helper) can pull the pelvis toward a target pose with a PD force and
 * torque and partly carry the body's weight. Hybrid mode uses it to keep a reacting character upright; a fall turns
 * it off entirely; a get-up ramps it in. Fully physical locomotion would have to replace it with balance torques
 * through the legs and contacts. That is not implemented yet.
 */
final class PhysWorld {
    final ArrayList<RigidBody> bodies=new ArrayList<>();final ArrayList<PhysJoint> joints=new ArrayList<>();
    final SimSettings S;Level level;
    // root assist
    RigidBody root;float rootAssist,weightSupport;final float[] rootX=new float[3],rootQ=Q.id(),rootV=new float[3],rootW=new float[3];
    /** Per-body assist targets (positions, velocities, orientations) from the animation; null until set. */
    float[][] tgtX,tgtV,tgtQ;
    // contacts (pooled)
    private static final class Contact {RigidBody b;final float[] local=new float[3],n=new float[3];float radius,lambda;boolean floor;}
    private final Contact[] pool=new Contact[256];private int nc;
    // diagnostics
    float lastStepMs,avgStepMs,maxAnchorError,maxLimitError;int steps,divergences,contactCount;
    private float acc;
    private int[][] subtree;   // per joint: indices of bodies at and below the child

    PhysWorld(SimSettings s,Level l){S=s;level=l;for(int i=0;i<pool.length;i++)pool[i]=new Contact();}

    /** Finish setup once bodies and joints are added: subtree lists, effective inertia per joint. */
    void finish(){
        subtree=new int[joints.size()][];
        for(int j=0;j<joints.size();j++){ArrayList<Integer> list=new ArrayList<>();collect(joints.get(j).child,list);subtree[j]=new int[list.size()];for(int k=0;k<list.size();k++)subtree[j][k]=list.get(k);
            PhysJoint J=joints.get(j);float[] a=J.parent.bindToWorld(Model.add(J.anchorP,J.parent.bindCom,1));float I=0;
            for(int bi:subtree[j]){RigidBody b=bodies.get(bi);float[] d=Model.sub(b.bindCom,Model.add(J.anchorP,J.parent.bindCom,1));I+=b.mass*Model.dot(d,d)+(b.inertia[0]+b.inertia[1]+b.inertia[2])/3f;}
            J.effInertia=Math.max(I,.002f);}
    }
    private void collect(RigidBody b,ArrayList<Integer> out){out.add(bodies.indexOf(b));for(PhysJoint j:joints)if(j.parent==b)collect(j.child,out);}

    /** Advances the simulation in fixed steps (frame time accumulates; at most maxStepsPerFrame steps per call). */
    int update(float frameDt){
        acc+=Math.min(frameDt,.25f);int n=0;
        while(acc>=S.fixedDt&&n<S.maxStepsPerFrame){step(S.fixedDt);acc-=S.fixedDt;n++;}
        if(n==S.maxStepsPerFrame&&acc>S.fixedDt)acc=0;   // running slow: drop time rather than spiral
        return n;
    }
    void step(float dt){
        long t0=System.nanoTime();
        int sub=Math.max(1,S.substeps);float h=dt/sub;
        updateSubtrees();
        maxAnchorError=maxLimitError=0;
        for(int s=0;s<sub;s++)substep(h);
        boolean ok=true;for(RigidBody b:bodies)if(!Q.finite(b.x)||!Q.finite(b.q)||!Q.finite(b.v)||!Q.finite(b.w))ok=false;
        if(!ok){divergences++;android_log("physics diverged (non-finite state), resetting velocities");for(RigidBody b:bodies){if(!Q.finite(b.x))System.arraycopy(b.xp,0,b.x,0,3);if(!Q.finite(b.q))System.arraycopy(b.qp,0,b.q,0,4);java.util.Arrays.fill(b.v,0);java.util.Arrays.fill(b.w,0);}}
        lastStepMs=(System.nanoTime()-t0)/1e6f;avgStepMs=steps==0?lastStepMs:avgStepMs*.95f+lastStepMs*.05f;steps++;
    }
    static void android_log(String m){try{Class.forName("android.util.Log").getMethod("w",String.class,String.class).invoke(null,"SlayerSim",m);}catch(Throwable t){System.err.println("[sim] "+m);}}

    private final float[] tmp=new float[3],tmp2=new float[3],tmp3=new float[3];
    private void substep(float h){
        // ---- 1. forces: muscles, root assist, gravity; integrate
        for(RigidBody b:bodies){java.util.Arrays.fill(b.torque,0);java.util.Arrays.fill(b.force,0);}
        for(PhysJoint j:joints)muscle(j,h);
        if(root!=null&&rootAssist>0)assist(h);
        for(RigidBody b:bodies){
            System.arraycopy(b.x,0,b.xp,0,3);System.arraycopy(b.q,0,b.qp,0,4);
            if(b.kinematic)continue;
            b.v[0]+=h*b.force[0]*b.invMass;b.v[1]+=h*b.force[1]*b.invMass;b.v[2]+=h*(b.force[2]*b.invMass-S.gravity);
            b.invInertiaWorld(b.torque,tmp);b.w[0]+=h*tmp[0];b.w[1]+=h*tmp[1];b.w[2]+=h*tmp[2];
            float ld=Math.max(0,1-b.linDamp*h),ad=Math.max(0,1-b.angDamp*h);
            for(int k=0;k<3;k++){b.v[k]*=ld;b.w[k]*=ad;b.x[k]+=h*b.v[k];}
            Q.addRot(b.q,b.w[0],b.w[1],b.w[2],h);
        }
        // ---- 2. positions: joints, limits, contacts
        detectContacts();
        for(PhysJoint j:joints){solveAnchor(j);solveLimits(j);}
        for(int i=0;i<nc;i++)solveContact(pool[i]);
        for(PhysJoint j:joints)solveAnchor(j);   // re-tighten anchors after contacts
        // ---- 3. velocities from the pose change, then the velocity pass
        for(RigidBody b:bodies){if(b.kinematic)continue;
            for(int k=0;k<3;k++)b.v[k]=(b.x[k]-b.xp[k])/h;
            float[] dq=Q.mul(b.q,Q.conj(b.qp));float s=dq[3]<0?-2/h:2/h;b.w[0]=dq[0]*s;b.w[1]=dq[1]*s;b.w[2]=dq[2]*s;}
        for(int i=0;i<nc;i++)velocityContact(pool[i],h);
        for(PhysJoint j:joints)damp(j,h);
    }

    // ---------------------------------------------------------------- muscles (PD, quaternion error, saturation, fatigue)
    private final float[] qa=new float[4],qb=new float[4],e=new float[3];
    private void muscle(PhysJoint j,float h){
        java.util.Arrays.fill(j.lastTorque,0);
        float str=j.strength*(1-j.fatigue);
        Q.mul(j.parent.q,j.targetRel,qa);Q.mul(qa,Q.conj(j.child.q),qb);Q.toRotVec(qb,e);
        j.lastError=Model.len(e);
        if(!j.motorOn||str<=1e-3f)return;
        float I=j.effInertia;float wn=Math.min(S.stiffness*j.stiffnessScale,.45f/h);
        float kp=I*wn*wn,kd=Math.min(2*S.dampingRatio*I*wn,.9f*I/h);
        float rx=j.child.w[0]-j.parent.w[0],ry=j.child.w[1]-j.parent.w[1],rz=j.child.w[2]-j.parent.w[2];
        float tx=(kp*e[0]+kd*(j.targetW[0]-rx))*str,ty=(kp*e[1]+kd*(j.targetW[1]-ry))*str,tz=(kp*e[2]+kd*(j.targetW[2]-rz))*str;
        // gravity compensation for the limb below the joint: cancels r × (−M g) about the anchor
        if(S.gravityCompensation>0){float[] a=new float[3];j.parent.toWorld(j.anchorP,a);
            float gx=j.subtreeCom[0]-a[0],gy=j.subtreeCom[1]-a[1];float Mg=j.subtreeMass*S.gravity*S.gravityCompensation*str;
            tx+=gy*Mg;ty+=-gx*Mg;}
        float tl=(float)Math.sqrt(tx*tx+ty*ty+tz*tz),max=j.maxTorque*S.maxTorqueScale*str;
        j.saturation=max>0?Math.min(1,tl/max):1;
        if(tl>max&&tl>0){float k=max/tl;tx*=k;ty*=k;tz*=k;}
        j.fatigue=Math.max(0,Math.min(.7f,j.fatigue+h*(j.saturation>.95f?S.fatigueRate:-S.fatigueRecovery)));
        j.child.torque[0]+=tx;j.child.torque[1]+=ty;j.child.torque[2]+=tz;j.parent.torque[0]-=tx;j.parent.torque[1]-=ty;j.parent.torque[2]-=tz;
        j.lastTorque[0]=tx;j.lastTorque[1]=ty;j.lastTorque[2]=tz;
    }
    private void updateSubtrees(){
        for(int j=0;j<joints.size();j++){PhysJoint J=joints.get(j);float m=0,cx=0,cy=0,cz=0;
            for(int bi:subtree[j]){RigidBody b=bodies.get(bi);m+=b.mass;cx+=b.x[0]*b.mass;cy+=b.x[1]*b.mass;cz+=b.x[2]*b.mass;}
            J.subtreeMass=m;J.subtreeCom[0]=cx/m;J.subtreeCom[1]=cy/m;J.subtreeCom[2]=cz/m;}
    }
    /**
     * Assist: a PD force on every body toward its animated position (scaled by its own mass, so the joints are not
     * asked to drag the whole body around by the pelvis) plus a share of its weight, and a PD torque that keeps the
     * pelvis and chest oriented. This is the "hand of god" of a powered ragdoll; rootAssist = 0 switches it off.
     */
    private void assist(float h){
        float wl=Math.min(S.rootAssistLinear,.45f/h),wa=Math.min(S.rootAssistAngular,.45f/h);float k=rootAssist;
        for(int bi=0;bi<bodies.size();bi++){RigidBody b=bodies.get(bi);if(b.kinematic)continue;
            float[] tx=tgtX!=null&&tgtX[bi]!=null?tgtX[bi]:null;
            if(tx!=null)for(int i=0;i<3;i++)b.force[i]+=k*b.mass*(wl*wl*(tx[i]-b.x[i])+2*wl*(tgtV[bi][i]-b.v[i]));
            b.force[2]+=k*weightSupport*b.mass*S.gravity;
            if((b==root||b.part==Hero.P_TORSO)&&tgtQ!=null&&tgtQ[bi]!=null){Q.mul(tgtQ[bi],Q.conj(b.q),qb);Q.toRotVec(qb,e);float Ie=(b.inertia[0]+b.inertia[1]+b.inertia[2])/3f*4;
                for(int i=0;i<3;i++)b.torque[i]+=k*Ie*(wa*wa*e[i]-2*wa*b.w[i]);}
        }
    }

    // ---------------------------------------------------------------- joints
    private void solveAnchor(PhysJoint j){
        float[] a1=new float[3],a2=new float[3];j.parent.toWorld(j.anchorP,a1);j.child.toWorld(j.anchorC,a2);
        float dx=a1[0]-a2[0],dy=a1[1]-a2[1],dz=a1[2]-a2[2];float c=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(c<1e-7f)return;
        maxAnchorError=Math.max(maxAnchorError,c);
        float[] n={dx/c,dy/c,dz/c};float[] r1=Model.sub(a1,j.parent.x),r2=Model.sub(a2,j.child.x);
        float w=j.parent.genInvMass(r1,n)+j.child.genInvMass(r2,n);if(w<1e-9f)return;
        float l=-c/w;float[] p={n[0]*l,n[1]*l,n[2]*l};
        j.parent.applyPositional(p,r1,1);j.child.applyPositional(p,r2,-1);
    }
    /** Rotates the child by dphi and the parent by −dphi, weighted by their inverse inertias. */
    private void angularPair(PhysJoint j,float[] dphi){
        float th=Model.len(dphi);if(th<1e-7f)return;float[] n={dphi[0]/th,dphi[1]/th,dphi[2]/th};
        float w=j.parent.genInvMassAngular(n)+j.child.genInvMassAngular(n);if(w<1e-9f)return;
        float l=th/w;float[] p={n[0]*l,n[1]*l,n[2]*l};j.child.applyAngular(p,1);j.parent.applyAngular(p,-1);
        maxLimitError=Math.max(maxLimitError,th);
    }
    private void solveLimits(PhysJoint j){
        float[] A1=Q.rot(j.parent.q,j.axisP),A2=Q.rot(j.child.q,j.axisC);
        if(j.type==PhysJoint.HINGE){
            angularPair(j,Model.cross(A2,A1));                                    // keep the hinge axes aligned
            float ang=j.angle();
            if(ang<j.twistMin)angularPair(j,Model.mul(Q.rot(j.parent.q,j.axisP),j.twistMin-ang));
            else if(ang>j.twistMax)angularPair(j,Model.mul(Q.rot(j.parent.q,j.axisP),j.twistMax-ang));
        }else{
            float d=Math.max(-1,Math.min(1,Model.dot(A1,A2)));float phi=(float)Math.acos(d);
            if(phi>j.swingMax){float[] c=Model.cross(A2,A1);float l=Model.len(c);if(l>1e-6f)angularPair(j,Model.mul(c,(phi-j.swingMax)/l));}
            float tw=twist(j);
            if(tw<j.twistMin)angularPair(j,Model.mul(Q.rot(j.child.q,j.axisC),j.twistMin-tw));
            else if(tw>j.twistMax)angularPair(j,Model.mul(Q.rot(j.child.q,j.axisC),j.twistMax-tw));
        }
    }
    /** Twist of the child about its own axis relative to the parent (swing removed). */
    static float twist(PhysJoint j){
        float[] rel=j.relRotation();float[] ax=j.axisP;
        float d=rel[0]*ax[0]+rel[1]*ax[1]+rel[2]*ax[2];float[] tw={ax[0]*d,ax[1]*d,ax[2]*d,rel[3]};
        float l=(float)Math.sqrt(tw[0]*tw[0]+tw[1]*tw[1]+tw[2]*tw[2]+tw[3]*tw[3]);if(l<1e-7f)return 0;
        float s=d/l,c=rel[3]/l;float a=2*(float)Math.atan2(s,c);if(a>Math.PI)a-=2*Math.PI;if(a<-Math.PI)a+=2*Math.PI;return a;
    }
    private void damp(PhysJoint j,float h){
        float k=Math.min(1,S.jointDamping*h*(1.5f-j.strength*.5f));
        float[] wr=Model.sub(j.child.w,j.parent.w);float l=Model.len(wr);if(l<1e-6f)return;float[] n=Model.mul(wr,1/l);
        float w=j.parent.genInvMassAngular(n)+j.child.genInvMassAngular(n);if(w<1e-9f)return;
        float p=l*k/w;float[] dc=new float[3],dp=new float[3];j.child.invInertiaWorld(Model.mul(n,p),dc);j.parent.invInertiaWorld(Model.mul(n,p),dp);
        if(!j.child.kinematic)for(int i=0;i<3;i++)j.child.w[i]-=dc[i];
        if(!j.parent.kinematic)for(int i=0;i<3;i++)j.parent.w[i]+=dp[i];
    }

    // ---------------------------------------------------------------- contacts with the level
    private void detectContacts(){
        nc=0;for(RigidBody b:bodies)b.grounded=false;
        for(RigidBody b:bodies){if(b.kinematic)continue;
            for(float[] cp:b.caps){int samples=Model.len(Model.sub(Model.v(cp[3],cp[4],cp[5]),Model.v(cp[0],cp[1],cp[2])))>cp[6]*2.5f?3:2;
                for(int s=0;s<samples;s++){float t=samples==1?.5f:s/(float)(samples-1);
                    float[] l={cp[0]+(cp[3]-cp[0])*t,cp[1]+(cp[4]-cp[1])*t,cp[2]+(cp[5]-cp[2])*t};sphere(b,l,cp[6]);}}}
        contactCount=nc;
    }
    private void sphere(RigidBody b,float[] local,float r){
        float[] c=new float[3];b.toWorld(local,c);
        Level.Cell cell=level.atF(c[0],c[1]);
        if(cell.solid){   // deep inside a wall: push out through the nearest cell face
            float fx=c[0]-(float)Math.floor(c[0]),fy=c[1]-(float)Math.floor(c[1]);float[] d={fx,1-fx,fy,1-fy};int k=0;for(int i=1;i<4;i++)if(d[i]<d[k])k=i;
            add(b,local,r,k==0?-1:k==1?1:0,k==2?-1:k==3?1:0,0,d[k]+r,false);return;}
        float pen=cell.floor-(c[2]-r);
        if(pen>0&&pen<.4f)add(b,local,r,0,0,1,pen,true);
        if(c[2]+r>cell.ceil)add(b,local,r,0,0,-1,c[2]+r-cell.ceil,false);
        // walls (solid cells or floors too tall to be a step) within the sphere's reach
        int cx=(int)Math.floor(c[0]),cy=(int)Math.floor(c[1]);
        if(c[0]+r>cx+1&&wall(level.at(cx+1,cy),c[2],r))add(b,local,r,-1,0,0,c[0]+r-(cx+1),false);
        if(c[0]-r<cx&&wall(level.at(cx-1,cy),c[2],r))add(b,local,r,1,0,0,cx-(c[0]-r),false);
        if(c[1]+r>cy+1&&wall(level.at(cx,cy+1),c[2],r))add(b,local,r,0,-1,0,c[1]+r-(cy+1),false);
        if(c[1]-r<cy&&wall(level.at(cx,cy-1),c[2],r))add(b,local,r,0,1,0,cy-(c[1]-r),false);
    }
    private static boolean wall(Level.Cell n,float z,float r){return n.solid||n.floor>z-r+.4f||n.ceil<z+r*.3f;}
    private void add(RigidBody b,float[] local,float r,float nx,float ny,float nz,float depth,boolean floor){
        if(nc>=pool.length||depth<=0)return;Contact c=pool[nc++];c.b=b;System.arraycopy(local,0,c.local,0,3);c.radius=r;c.n[0]=nx;c.n[1]=ny;c.n[2]=nz;c.lambda=depth;c.floor=floor;
        if(floor){b.grounded=true;float[] w=new float[3];b.toWorld(local,w);b.contactPoint[0]=w[0];b.contactPoint[1]=w[1];b.contactPoint[2]=w[2]-r;}
    }
    private void solveContact(Contact c){
        RigidBody b=c.b;float[] cw=new float[3];b.toWorld(c.local,cw);
        // recompute penetration for the current pose
        float depth;
        if(c.floor){Level.Cell cell=level.atF(cw[0],cw[1]);depth=cell.floor-(cw[2]-c.radius);if(cell.solid)depth=0;}
        else depth=c.lambda;   // walls/ceilings: one-shot correction per substep
        if(depth<=S.contactSlop){c.lambda=0;return;}
        depth-=S.contactSlop;
        float[] pt={cw[0]-c.n[0]*c.radius,cw[1]-c.n[1]*c.radius,cw[2]-c.n[2]*c.radius};float[] r=Model.sub(pt,b.x);
        float w=b.genInvMass(r,c.n);if(w<1e-9f)return;float l=depth/w;
        b.applyPositional(new float[]{c.n[0]*l,c.n[1]*l,c.n[2]*l},r,1);c.lambda=depth;
        // static friction: cancel the contact point's sliding this substep if it is within the friction cone
        if(c.floor){float[] prev=new float[3];Q.rot(b.qp,c.local,prev);prev[0]+=b.xp[0];prev[1]+=b.xp[1];prev[2]+=b.xp[2];
            float[] now=new float[3];b.toWorld(c.local,now);float[] d=Model.sub(now,prev);float dn=Model.dot(d,c.n);float[] dt={d[0]-c.n[0]*dn,d[1]-c.n[1]*dn,d[2]-c.n[2]*dn};
            float tl=Model.len(dt);if(tl>1e-7f&&tl<S.friction*depth*1.5f+.0004f){float[] t=Model.mul(dt,1/tl);float[] r2=Model.sub(now,b.x);float wt=b.genInvMass(r2,t);
                if(wt>1e-9f){float lt=-tl/wt;b.applyPositional(Model.mul(t,lt),r2,1);}}}
    }
    private void velocityContact(Contact c,float h){
        if(c.lambda<=0)return;RigidBody b=c.b;float[] cw=new float[3];b.toWorld(c.local,cw);
        float[] pt={cw[0]-c.n[0]*c.radius,cw[1]-c.n[1]*c.radius,cw[2]-c.n[2]*c.radius};float[] r=Model.sub(pt,b.x);
        float[] vp=Model.add(b.v,Model.cross(b.w,r),1);float vn=Model.dot(vp,c.n);float[] vt=Model.sub(vp,Model.mul(c.n,vn));float vtl=Model.len(vt);
        if(vtl>1e-6f){float dv=Math.min(S.friction*c.lambda/h,vtl);float[] t=Model.mul(vt,1/vtl);   // Coulomb: tangential change bounded by μ × the normal correctionfloat[] t=Model.mul(vt,1/vtl);
            float wt=b.genInvMass(r,t);if(wt>1e-9f)b.applyImpulse(Model.mul(t,-dv/wt),r);}
        if(vn<0){float dvn=-vn*(1+(vn<-1f?S.restitution:0));float wn=b.genInvMass(r,c.n);if(wn>1e-9f)b.applyImpulse(Model.mul(c.n,dvn/wn),r);}
    }

    // ---------------------------------------------------------------- queries
    float totalMass(){float m=0;for(RigidBody b:bodies)m+=b.mass;return m;}
    float[] centerOfMass(){float m=0;float[] c=new float[3];for(RigidBody b:bodies){m+=b.mass;for(int k=0;k<3;k++)c[k]+=b.x[k]*b.mass;}for(int k=0;k<3;k++)c[k]/=m;return c;}
    float[] comVelocity(){float m=0;float[] c=new float[3];for(RigidBody b:bodies){m+=b.mass;for(int k=0;k<3;k++)c[k]+=b.v[k]*b.mass;}for(int k=0;k<3;k++)c[k]/=m;return c;}
    float kineticEnergy(){float e=0;for(RigidBody b:bodies)e+=b.kineticEnergy();return e;}
}
