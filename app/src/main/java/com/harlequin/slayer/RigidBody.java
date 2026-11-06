package com.harlequin.slayer;

/**
 * One rigid segment of the articulated body: position of its centre of mass, orientation, linear and angular
 * velocity, mass and a diagonal inertia tensor (in body space), and capsule colliders.
 *
 * The body's local frame is the character's bind pose frame translated to the segment's centre of mass, so at bind
 * the orientation is the identity and the mesh part attached to it is drawn with T(x)·R(q)·T(-bindCom).
 */
final class RigidBody {
    final String name;final int part;              // the mesh part it drives (Hero part id)
    final float[] x=new float[3],v=new float[3],q=Q.id(),w=new float[3];
    final float[] xp=new float[3],qp=Q.id();       // start-of-substep pose (velocity update and static friction)
    final float[] bindCom;float mass,invMass;final float[] inertia=new float[3],invI=new float[3];
    /** Capsules in body space: {ax,ay,az, bx,by,bz, radius}. */
    float[][] caps=new float[0][];
    /** Accumulated torque / force for this substep (from muscles and root assist). */
    final float[] torque=new float[3],force=new float[3];
    float linDamp,angDamp;boolean kinematic;
    /** Contact this step (for the support polygon and the debug overlay). */
    boolean grounded;final float[] contactPoint=new float[3];

    RigidBody(String name,int part,float[] bindCom,float mass){this.name=name;this.part=part;this.bindCom=bindCom.clone();setMass(mass);System.arraycopy(bindCom,0,x,0,3);}
    void setMass(float m){mass=m;invMass=m>0?1/m:0;}
    /** Inertia of a solid capsule (cylinder plus hemispherical caps) along the body-space axis from a to b. */
    void setCapsuleInertia(float[] a,float[] b,float r){
        float[] d=Model.sub(b,a);float L=Model.len(d);float[] ax=L>1e-6f?Model.mul(d,1/L):Model.v(0,0,1);
        float m=mass;float iAxis=.5f*m*r*r,iPerp=m*(3*r*r+L*L)/12f+m*r*r*.15f;
        // diagonal approximation in body axes: project the principal inertia onto x, y, z
        for(int k=0;k<3;k++){float c=ax[k]*ax[k];inertia[k]=iAxis*c+iPerp*(1-c);invI[k]=1/Math.max(inertia[k],1e-5f);}
    }
    void addCapsule(float[] aWorldBind,float[] bWorldBind,float r){
        float[][] n=new float[caps.length+1][];System.arraycopy(caps,0,n,0,caps.length);
        n[caps.length]=new float[]{aWorldBind[0]-bindCom[0],aWorldBind[1]-bindCom[1],aWorldBind[2]-bindCom[2],bWorldBind[0]-bindCom[0],bWorldBind[1]-bindCom[1],bWorldBind[2]-bindCom[2],r};caps=n;
    }
    // ---- frames
    void toWorld(float[] local,float[] o){Q.rot(q,local,o);o[0]+=x[0];o[1]+=x[1];o[2]+=x[2];}
    /** World point of a bind-pose (model space) point carried by this body. */
    float[] bindToWorld(float[] bindPt){float[] l={bindPt[0]-bindCom[0],bindPt[1]-bindCom[1],bindPt[2]-bindCom[2]};float[] o=new float[3];toWorld(l,o);return o;}
    /** I⁻¹ (world) times v, into o. */
    void invInertiaWorld(float[] v,float[] o){Q.rotInv(q,v,o);o[0]*=invI[0];o[1]*=invI[1];o[2]*=invI[2];Q.rot(q,o,o);}
    /** Generalised inverse mass for a positional correction along unit n at offset r from the centre of mass. */
    float genInvMass(float[] r,float[] n){
        if(kinematic)return 0;
        float cx=r[1]*n[2]-r[2]*n[1],cy=r[2]*n[0]-r[0]*n[2],cz=r[0]*n[1]-r[1]*n[0];
        float[] c={cx,cy,cz},t=new float[3];Q.rotInv(q,c,t);
        return invMass+t[0]*t[0]*invI[0]+t[1]*t[1]*invI[1]+t[2]*t[2]*invI[2];
    }
    float genInvMassAngular(float[] n){if(kinematic)return 0;float[] t=new float[3];Q.rotInv(q,n,t);return t[0]*t[0]*invI[0]+t[1]*t[1]*invI[1]+t[2]*t[2]*invI[2];}
    /** Applies a positional impulse p at offset r (x += p/m, q += ½ I⁻¹(r×p) q). */
    void applyPositional(float[] p,float[] r,float sign){
        if(kinematic)return;
        x[0]+=p[0]*invMass*sign;x[1]+=p[1]*invMass*sign;x[2]+=p[2]*invMass*sign;
        if(r!=null){float[] c={r[1]*p[2]-r[2]*p[1],r[2]*p[0]-r[0]*p[2],r[0]*p[1]-r[1]*p[0]},d=new float[3];invInertiaWorld(c,d);Q.addRot(q,d[0],d[1],d[2],sign);}
    }
    /** Applies an angular positional impulse (q += ½ I⁻¹ p q). */
    void applyAngular(float[] p,float sign){if(kinematic)return;float[] d=new float[3];invInertiaWorld(p,d);Q.addRot(q,d[0],d[1],d[2],sign);}
    /** Velocity impulse at offset r. */
    void applyImpulse(float[] p,float[] r){
        if(kinematic)return;
        v[0]+=p[0]*invMass;v[1]+=p[1]*invMass;v[2]+=p[2]*invMass;
        if(r!=null){float[] c={r[1]*p[2]-r[2]*p[1],r[2]*p[0]-r[0]*p[2],r[0]*p[1]-r[1]*p[0]},d=new float[3];invInertiaWorld(c,d);w[0]+=d[0];w[1]+=d[1];w[2]+=d[2];}
    }
    /** Mesh matrix: the bind-space part placed by this body. */
    float[] partMatrix(){return M.mul(Q.toMatrix(q,x[0],x[1],x[2]),M.tr(-bindCom[0],-bindCom[1],-bindCom[2]));}
    /** Sets the pose from a part matrix (bind→world). */
    void setFromPartMatrix(float[] m){float[] c=M.pt(m,bindCom);x[0]=c[0];x[1]=c[1];x[2]=c[2];float[] r=Q.fromMatrix(m);System.arraycopy(r,0,q,0,4);}
    float kineticEnergy(){float[] lw=new float[3];Q.rotInv(q,w,lw);return .5f*mass*(v[0]*v[0]+v[1]*v[1]+v[2]*v[2])+.5f*(inertia[0]*lw[0]*lw[0]+inertia[1]*lw[1]*lw[1]+inertia[2]*lw[2]*lw[2]);}
}
