package com.harlequin.slayer;

import static com.harlequin.slayer.Hero.*;
import static com.harlequin.slayer.Model.v;

/**
 * Authored reference poses for getting up (face down and face up), as compact key poses in a floor frame: the
 * pelvis placed and turned, the head nodded, and hands and feet placed by two-bone IK (so bones never stretch).
 * The recovery controller blends between them and hands the result to the muscles as targets: the body still has
 * to be moved there by joint torques, against gravity and contacts.
 */
final class RecoveryPoses {
    private RecoveryPoses(){}
    static float mix(float a,float b,float t){return a+(b-a)*t;}
    /** The key pose at time t of a sequence (smoothstep between keys). */
    static float[] sample(float[][] keys,float[] times,float t){
        t=Math.max(0,Math.min(times[times.length-1],t));int k=0;while(k<times.length-2&&t>times[k+1])k++;
        float u=Math.max(0,Math.min(1,(t-times[k])/(times[k+1]-times[k])));u=u*u*(3-2*u);
        float[] key=new float[KEY];for(int i=0;i<KEY;i++)key[i]=mix(keys[k][i],keys[k+1][i],u);return key;
    }
    // key layout (in the floor frame: x = the way she will face, y = her left, z up)
    static final int KEY=33,PX=0,YAW=3,PITCH=4,ROLL=5,HEADP=6,LANK=7,LFP=10,LKP=11,RANK=14,RFP=17,RKP=18,LHAND=21,LEP=24,RHAND=27,REP=30;
    /** Joint positions for a key pose: the torso placed and turned, head nodded, feet and hands placed by two-bone IK. */
    static float[][] poseJoints(float[] k,float[] o,float gyaw,Level L){
        final int N=JOINTS;
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
