package com.harlequin.slayer;

/**
 * A joint between a parent and a child body: a shared anchor (ball socket), anatomical limits (a swing cone plus a
 * twist range, or a hinge with a flexion range), passive joint damping, and a muscle: a PD actuator that pulls the
 * child's orientation (relative to the parent) toward a target from the animation.
 *
 * Muscle torque (joint space, quaternion error):   τ = Kp·e + Kd·(ω_target − ω_rel) + τ_ff,
 * where e is the rotation vector of q_target·q_childᵀ, ω_rel the child's angular velocity relative to its parent,
 * and τ_ff a gravity-compensation feedforward for the limb below the joint. τ is saturated at the joint's maximum
 * torque × strength × (1 − fatigue) and applied as equal and opposite torques on child and parent.
 */
final class PhysJoint {
    static final int BALL=0,HINGE=1;
    final String name;final RigidBody parent,child;final int type;
    final float[] anchorP,anchorC;           // body-space anchors
    final float[] axisP,axisC;               // twist axis (ball) or hinge axis (hinge), body space
    final float[] refP,refC;                 // perpendicular reference for twist / hinge angle
    float swingMax,twistMin,twistMax;        // ball limits; hinge uses twistMin/twistMax as flexion range
    // ---- muscle
    final float[] targetRel=Q.id();           // target child orientation relative to the parent (from the animation)
    final float[] targetW=new float[3];       // target relative angular velocity (world)
    float maxTorque,stiffnessScale=1,strength=1,fatigue;float effInertia;   // effective inertia of the limb below the joint
    float subtreeMass;final float[] subtreeCom=new float[3];
    boolean motorOn=true;
    // ---- diagnostics
    final float[] lastTorque=new float[3];float lastError,saturation;

    PhysJoint(String name,RigidBody parent,RigidBody child,int type,float[] anchorBind,float[] axisBind,float[] refBind){
        this.name=name;this.parent=parent;this.child=child;this.type=type;
        anchorP=Model.sub(anchorBind,parent.bindCom);anchorC=Model.sub(anchorBind,child.bindCom);
        axisP=Model.norm(axisBind).clone();axisC=Model.norm(axisBind).clone();   // bodies are in bind orientation at bind
        float[] r=Model.sub(refBind,Model.mul(axisP,Model.dot(refBind,axisP)));r=Model.norm(r);refP=r.clone();refC=r.clone();
    }
    PhysJoint limits(float swing,float tMin,float tMax){swingMax=swing;twistMin=tMin;twistMax=tMax;return this;}
    PhysJoint torque(float t){maxTorque=t;return this;}

    /** Current child orientation relative to the parent. */
    float[] relRotation(){return Q.mul(Q.conj(parent.q),child.q);}
    /** Twist / hinge angle about the joint axis (radians, 0 at bind). */
    float angle(){
        float[] a=new float[3],b1=new float[3],b2=new float[3];Q.rot(parent.q,axisP,a);Q.rot(parent.q,refP,b1);Q.rot(child.q,refC,b2);
        b2=Model.sub(b2,Model.mul(a,Model.dot(b2,a)));float l=Model.len(b2);if(l<1e-6f)return 0;b2=Model.mul(b2,1/l);
        return (float)Math.atan2(Model.dot(a,Model.cross(b1,b2)),Model.dot(b1,b2));
    }
}
