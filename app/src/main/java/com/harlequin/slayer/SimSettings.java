package com.harlequin.slayer;

import java.util.Properties;

/**
 * Data-driven settings for the character simulation, loaded from assets/sim/warden.properties (any key can be
 * left out: the defaults below apply). Designers tune these without touching code; the debug overlay shows the live
 * values. Units: metres, kilograms, seconds, newton-metres, radians.
 *
 * Muscles are specified as a natural frequency (stiffness, rad/s) and a damping ratio rather than raw gains: the
 * controller turns them into PD gains using each joint's effective inertia (Kp = I·ω², Kd = 2ζ·I·ω), which keeps the
 * same "feel" for light and heavy limbs and lets the solver clamp the gains to what it can integrate stably.
 */
final class SimSettings {
    // ---- body
    float height=1.80f,totalMass=75f;         // with armour
    float sawMass=6.5f,gunMass=3.4f;           // carried weight on the right forearm / left hand
    // ---- solver
    float fixedDt=1/60f;int substeps=8,maxStepsPerFrame=3;float gravity=9.81f;
    float linearDamping=.05f,angularDamping=.35f,jointDamping=.8f;
    float friction=.85f,restitution=.05f,contactSlop=.002f;
    // ---- muscles
    float stiffness=14f,dampingRatio=.9f;      // rad/s, ratio (critical = 1)
    float maxTorqueScale=1f;                   // multiplies the per-joint anatomical torque limits
    float gravityCompensation=.85f;            // feedforward fraction of each limb's gravity torque
    float fatigueRate=.25f,fatigueRecovery=.35f;
    // ---- reactions / balance
    float rootAssistLinear=9f,rootAssistAngular=10f;   // natural frequencies (rad/s) of the "hand of god" that keeps a powered body upright
    float balanceMargin=.12f;                  // how far (m) the capture point may leave the support polygon before balance is lost
    float pushTestImpulse=55f;                 // N·s for the PUSH debug button (heavy push = ×2)
    float fallStrength=.6f,impactStrength=.25f,groundStrength=.18f,recoverStrength=1f;
    float settleTime=.6f;
    // ---- quality (0 low .. 3 ultra): substeps and solver iterations
    int quality=2;

    static SimSettings load(Properties p){
        SimSettings s=new SimSettings();if(p==null)return s;
        for(java.lang.reflect.Field f:SimSettings.class.getDeclaredFields()){
            if(java.lang.reflect.Modifier.isStatic(f.getModifiers()))continue;String v=p.getProperty(f.getName());if(v==null)continue;
            try{if(f.getType()==float.class)f.setFloat(s,Float.parseFloat(v.trim()));else if(f.getType()==int.class)f.setInt(s,Integer.parseInt(v.trim()));}catch(Exception ignored){}}
        s.applyQuality();return s;
    }
    void applyQuality(){substeps=quality<=0?3:quality==1?5:quality==2?8:12;}
    /** The current values as text (for the debug overlay). */
    String describe(){return String.format(java.util.Locale.ROOT,"mass %.0f kg  substeps %d  muscle %.0f rad/s ζ%.2f  gravComp %.2f  balance %.2f m",totalMass,substeps,stiffness,dampingRatio,gravityCompensation,balanceMargin);}
}
