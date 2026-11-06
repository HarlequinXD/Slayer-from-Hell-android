package com.harlequin.slayer;

import static com.harlequin.slayer.Model.v;

/**
 * The side-by-side double-barrel shotgun and the player's hands.
 * The gun itself is the baked low poly (assets/gun.bin: action + stock, barrels + forend, 744 triangles) wearing
 * maps baked from a 5.5-million-triangle sculpt. Hands, sleeves and shells are built here.
 * Fire: right barrel, then left, each with a heavy kick. Reload: the left hand breaks the gun open on its hinge,
 * the extractors throw the spent shells out, the hand fetches fresh shells, thumbs them into the chambers and
 * snaps the barrels shut. Firing interrupts a reload once a barrel is loaded.
 * Pose matrices are in camera space (x forward, y left, z up). Gun space: x along the barrels, y left, z up. Pure Java.
 */
final class Gun {
    static final int P_ACTION=0,P_BARRELS=1,P_RHAND=2,P_LHAND=3,P_SLEEVE=4,P_CUFF=5,P_HULL=6,P_BASE=7,PARTS=8;
    /** Material per part (-1 = the baked gun maps). */
    static final int[] PART_MAT={-1,-1,Tex.LEATHER,Tex.LEATHER,Tex.FABRIC,Tex.LEATHER,Tex.SHELL,Tex.BRASS};
    static final float BX=.0118f,HINGE_X=.128f,HINGE_Z=-.027f,MUZZLE_X=.805f;
    static final float[] FOREND_GRAB={.255f,0,-.012f};
    /** Shell origin (rim plane) when seated in a chamber: hull runs forward along +x. */
    static final float CHAMBER_X=.1095f;

    /** Hands, forearms and shell parts (the gun's own meshes come from the bake). Index = part id; gun parts are null. */
    static float[][] build(){
        float[][] out=new float[PARTS][];
        out[P_RHAND]=rightHand();out[P_LHAND]=leftHand();
        Model m=new Model(3);m.taper(0,.84f,.048f,.044f,.035f,.031f,0,0,0,0);out[P_SLEEVE]=m.data();    // forearm in a sleeve, elbow x=0 → wrist x=1
        m=new Model(6);m.taper(.8f,1.0f,.04f,.036f,.035f,.031f,0,0,0,0);out[P_CUFF]=m.data();             // glove cuff
        m=new Model(30);m.cyl(0,.058f,.0098f,.0098f,0,0,12,false,true);out[P_HULL]=m.data();             // red hull
        m=new Model(30);m.cyl(-.012f,0,.0102f,.0102f,0,0,12,true,false);m.cyl(-.0145f,-.012f,.0113f,.0113f,0,0,12,true,true);out[P_BASE]=m.data();   // brass head and rim
        return out;
    }
    /** Right hand round the straight wrist of the stock, index finger forward on the front trigger, thumb over the top. */
    private static float[] rightHand(){
        Model m=new Model(10);
        float[] o=v(-.098f,0,-.012f),gx=Model.norm(v(-1,0,-.1f)),gy=v(0,1,0),gz=v(0,0,-1);   // gx back along the wrist, gz down round it
        m.frame(o,gx,gy,gz);
        m.box(-.005f,-.046f,-.03f,.075f,-.016f,.024f);                 // back of the hand on the right side
        for(int i=0;i<3;i++){float a=.022f+i*.021f;
            m.box(a,-.04f,.024f,a+.018f,.022f,.042f);                    // fingers wrapped under the wrist
            m.box(a,.016f,-.008f,a+.018f,.03f,.03f);}                   // fingertips up the left side
        m.noFrame();
        m.taper(-.1f,-.032f,.007f,.007f,.0055f,.006f,-.014f,-.035f,-.003f,-.044f);   // index finger to the trigger
        m.taper(-.14f,-.1f,.01f,.009f,.009f,.008f,-.012f,.016f,.006f,.014f);        // thumb over the top
        return m.data();
    }
    /** Left hand cupping the forend from below, fingers up its left side, thumb on the right. Origin = grab point. */
    private static float[] leftHand(){
        Model m=new Model(10);
        m.box(-.045f,-.03f,-.042f,.038f,.022f,-.018f);
        for(int i=0;i<4;i++){float a=-.04f+i*.019f;m.box(a,.019f,-.036f,a+.016f,.033f,.006f);m.box(a,.004f,.0f,a+.016f,.027f,.014f);}
        m.box(-.032f,-.035f,-.032f,.018f,-.021f,.0f);
        m.box(-.085f,-.032f,-.056f,-.04f,.02f,-.02f);
        return m.data();
    }
    static final float[] RIGHT_WRIST={-.175f,-.028f,-.03f},LEFT_WRIST={-.09f,-.006f,-.04f};

    // ------------------------------------------------------------------ state
    int reserve=40;
    /** Per barrel (0 right, 1 left): loaded with a live shell / holding a fired case. */
    final boolean[] live={true,true},spent=new boolean[2];
    int ammo(){return (live[0]?1:0)+(live[1]?1:0);}
    static final int CAPACITY=2;
    static final int IDLE=0,FIRE=1,OPEN=2,LOAD=3,CLOSE=4;
    int state=IDLE;float t;int firedBarrel;private boolean wantFire;private int loading;
    static final int EV_FIRE=1,EV_OPEN=2,EV_EJECT=4,EV_SHELL_IN=8,EV_CLOSE=16,EV_DRY=32;
    int events;private boolean ejected,inserted;
    private float cooldown;

    void update(float dt,boolean fireHeld,boolean reloadTap){
        events=0;t+=dt;cooldown-=dt;
        if(reloadTap&&state==IDLE&&ammo()<2&&reserve>0)begin(OPEN);
        switch(state){
            case IDLE:
                if(fireHeld&&cooldown<=0){if(ammo()>0)shoot();else{events|=EV_DRY;cooldown=.35f;if(reserve>0)begin(OPEN);}}
                break;
            case FIRE:if(t>=.32f){begin(IDLE);if(ammo()==0&&reserve>0)begin(OPEN);}break;
            case OPEN:
                if(t>=.3f&&!ejected){ejected=true;if(spent[0]||spent[1])events|=EV_EJECT;}
                if(t>=.45f){begin(LOAD);loading=Math.min(reserve,(live[0]?0:1)+(live[1]?0:1));}
                break;
            case LOAD:
                if(t>=.62f&&!inserted){inserted=true;for(int b=0;b<2&&loading>0;b++)if(!live[b]){live[b]=true;reserve--;loading--;}events|=EV_SHELL_IN;}
                if(fireHeld&&inserted)wantFire=true;
                if(t>=.95f)begin(CLOSE);
                break;
            case CLOSE:if(t>=.05f&&!inserted){inserted=true;events|=EV_CLOSE;}if(t>=.3f)begin(IDLE);break;
        }
        if(state==IDLE&&wantFire){wantFire=false;if(ammo()>0)shoot();}
    }
    private void shoot(){
        int b=live[0]?0:1;live[b]=false;spent[b]=true;firedBarrel=b;begin(FIRE);events|=EV_FIRE;cooldown=.3f;
    }
    private void begin(int s){state=s;t=0;ejected=inserted=false;if(s==OPEN)events|=EV_OPEN;}
    boolean reloading(){return state==OPEN||state==LOAD||state==CLOSE;}
    /** Called by the game after it has thrown the spent cases out. */
    void clearSpent(){spent[0]=spent[1]=false;}

    // ------------------------------------------------------------------ pose
    final float[][] mat=new float[PARTS][];final boolean[] visible=new boolean[PARTS];
    /** Extra shell instances to draw (chambers and hand): their matrices, and whether each is a fired case. */
    final float[][] shellMats=new float[4][];final boolean[] shellSpent=new boolean[4];int shellCount;
    float[] muzzleCam,rightArm,leftArm;final float[][] chamberCam=new float[2][];float recoilNow;
    private float swayX,swayY;
    private static float ease(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    private static float[] lerp(float[] a,float[] b,float t){return v(lerp(a[0],b[0],t),lerp(a[1],b[1],t),lerp(a[2],b[2],t));}

    void pose(float bobPhase,float bobAmt,float lookDX,float lookDY,float time,float dt){
        swayX+=(-lookDX*.0009f-swayX)*Math.min(1,dt*7);swayY+=(lookDY*.0009f-swayY)*Math.min(1,dt*7);
        float bx=(float)Math.sin(bobPhase)*.011f*bobAmt,bz=-(float)Math.abs(Math.cos(bobPhase))*.009f*bobAmt+(float)Math.sin(time*1.7f)*.0018f;
        float fx=.34f,fy=-.12f,fz=-.135f,pitch=-.03f,yaw=.07f,roll=0,kick=0,drop=0,open=0;float recoil=0;
        float[] lhand=null;int handShells=0;float extract=0;
        switch(state){
            case FIRE:{float r=t<.04f?t/.04f:(float)Math.exp(-(t-.04f)*9);recoil=r;kick=.1f*r;pitch-=.4f*r;roll=(firedBarrel==0?.05f:-.05f)*r;break;}
            case OPEN:{float b=ease(t/.3f);open=.62f*ease((t-.05f)/.22f);pitch+=.22f*b;roll=.22f*b;yaw+=.1f*b;drop=-.05f*b;fy+=.075f*b;fx+=.03f*b;
                extract=ease((t-.24f)/.06f);break;}
            case LOAD:{open=.62f;pitch+=.22f;roll=.22f;yaw+=.1f;drop=-.05f;fy+=.075f;fx+=.03f;
                float[] belt=v(-.08f,-.24f,-.25f),near=v(.05f,0,.06f),in=v(.115f,0,.025f);
                if(t<.25f)lhand=lerp(FOREND_GRAB,belt,ease(t/.25f));
                else if(t<.5f){lhand=lerp(belt,near,ease((t-.25f)/.25f));handShells=loading;}
                else if(t<.62f){lhand=lerp(near,in,ease((t-.5f)/.12f));handShells=loading;}
                else lhand=lerp(in,FOREND_GRAB,ease((t-.62f)/.33f));
                break;}
            case CLOSE:{float b=1-ease(t/.3f);float snap=1-ease(t/.08f);open=.62f*snap;pitch+=.22f*b-.08f*(1-snap)*b;roll=.22f*b;yaw+=.1f*b;drop=-.05f*b;fy+=.075f*b;fx+=.03f*b;break;}
        }
        final float PX=-.1f,PZ=-.012f;   // rotations pivot at the right hand on the wrist, so the stock stays put
        float[] G=M.mul(M.tr(fx+bx*.2f-kick+PX,fy+bx+swayX,fz+bz+swayY-drop+PZ),M.mul(M.rz(yaw+swayX*2),M.mul(M.ry(pitch),M.mul(M.rx(roll),M.tr(-PX,0,-PZ)))));
        // barrels and forend swing down about the hinge pin
        float[] Bm=M.mul(G,M.mul(M.tr(HINGE_X,0,HINGE_Z),M.mul(M.ry(open),M.tr(-HINGE_X,0,-HINGE_Z))));
        for(int i=0;i<PARTS;i++)visible[i]=true;visible[P_HULL]=visible[P_BASE]=false;
        mat[P_ACTION]=G;mat[P_BARRELS]=Bm;mat[P_RHAND]=G;
        float[] L=lhand==null?M.mul(Bm,M.tr(FOREND_GRAB[0],FOREND_GRAB[1],FOREND_GRAB[2])):M.mul(G,M.mul(M.tr(lhand[0],lhand[1],lhand[2]),M.rx(-.4f)));
        mat[P_LHAND]=L;
        // shells: in the chambers (spent cases slide out as the gun opens), or two fresh ones in the left hand
        shellCount=0;
        boolean opened=state==OPEN||state==LOAD||state==CLOSE;
        for(int b=0;b<2;b++){float y=b==0?-BX:BX;chamberCam[b]=M.pt(Bm,v(CHAMBER_X-.02f,y,0));
            if(opened&&(live[b]||spent[b])){shellMats[shellCount]=M.mul(Bm,M.tr(CHAMBER_X-(spent[b]?.016f*extract:0),y,0));shellSpent[shellCount++]=spent[b];}}
        for(int k=0;k<handShells&&k<2;k++){shellMats[shellCount]=M.mul(L,M.tr(-.01f,k==0?-BX:BX,.03f));shellSpent[shellCount++]=false;}
        muzzleCam=M.pt(Bm,v(MUZZLE_X,firedBarrel==0?-BX:BX,0));
        float[] rw=M.pt(G,RIGHT_WRIST),lw=M.pt(L,LEFT_WRIST);
        rightArm=arm(v(-.06f,-.27f,-.33f),rw);leftArm=arm(v(.14f,.1f,-.42f),lw);
        mat[P_SLEEVE]=rightArm;mat[P_CUFF]=rightArm;
        recoilNow=recoil;
    }
    /** How far the barrels hang open on the hinge right now (radians), and how far the extractors have pushed the cases out. */
    float openAngle(){
        switch(state){case OPEN:return .62f*ease((t-.05f)/.22f);case LOAD:return .62f;case CLOSE:return .62f*(1-ease(t/.08f));default:return 0;}
    }
    /**
     * Third-person pose: the gun (action + stock) at world matrix G, held by the character. Fills the same outputs as
     * pose() but in world space (the eye frame is the identity): barrels on the hinge, shells in the chambers (spent
     * cases sliding out as it opens), the muzzle and chamber points, and the recoil amount.
     */
    void poseWorld(float[] G){
        float open=openAngle(),extract=state==OPEN?ease((t-.24f)/.06f):0;
        recoilNow=state==FIRE?(t<.04f?t/.04f:(float)Math.exp(-(t-.04f)*9)):0;
        float[] Bm=M.mul(G,M.mul(M.tr(HINGE_X,0,HINGE_Z),M.mul(M.ry(open),M.tr(-HINGE_X,0,-HINGE_Z))));
        for(int i=0;i<PARTS;i++)visible[i]=false;visible[P_ACTION]=visible[P_BARRELS]=true;
        mat[P_ACTION]=G;mat[P_BARRELS]=Bm;
        shellCount=0;boolean opened=state==OPEN||state==LOAD||state==CLOSE;
        for(int b=0;b<2;b++){float y=b==0?-BX:BX;chamberCam[b]=M.pt(Bm,v(CHAMBER_X-.02f,y,0));
            if(opened&&(live[b]||spent[b])){shellMats[shellCount]=M.mul(Bm,M.tr(CHAMBER_X-(spent[b]?.016f*extract:0),y,0));shellSpent[shellCount++]=spent[b];}}
        muzzleCam=M.pt(Bm,v(MUZZLE_X,firedBarrel==0?-BX:BX,0));
    }
    /** The barrels' frame (for docking the chambers onto shells): G times the hinge at the given angle. */
    static float[] barrelsOf(float[] G,float open){return M.mul(G,M.mul(M.tr(HINGE_X,0,HINGE_Z),M.mul(M.ry(open),M.tr(-HINGE_X,0,-HINGE_Z))));}
    /** Inverse of the hinge transform: G from a barrels frame. */
    static float[] actionOf(float[] Bm,float open){return M.mul(Bm,M.mul(M.tr(HINGE_X,0,HINGE_Z),M.mul(M.ry(-open),M.tr(-HINGE_X,0,-HINGE_Z))));}
    private static float[] arm(float[] elbow,float[] wrist){
        float[] d=Model.sub(wrist,elbow);float len=(float)Math.sqrt(Model.dot(d,d));float[] x=Model.norm(d);
        float[] y=Model.norm(Model.cross(v(0,0,1),x));float[] z=Model.cross(x,y);
        return M.basis(elbow,v(x[0]*len,x[1]*len,x[2]*len),y,z);
    }
}
