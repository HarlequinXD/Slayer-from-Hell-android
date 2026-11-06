package com.harlequin.slayer;

import static com.harlequin.slayer.Model.v;

/**
 * The pump-action shotgun and the player's hands: 3D parts, ammunition and the animation state machine.
 * Fire: recoil kick, then the left hand racks the pump back (the spent shell flies out of the port) and forward.
 * Reload: the gun rolls to show its loading port and the left hand fetches shells one by one and thumbs them in;
 * firing interrupts a reload. Pose matrices are in camera space (x forward, y left, z up).
 * Gun space: x along the barrel, y left, z up, metres. Pure Java.
 */
final class Gun {
    // ------------------------------------------------------------------ parts
    static final int P_BODY=0,P_BEAD=1,P_STOCK=2,P_PAD=3,P_PUMP=4,P_BARS=5,P_RHAND=6,P_LHAND=7,P_SLEEVE=8,P_CUFF=9,P_HULL=10,P_BASE=11,PARTS=12;
    static final int[] PART_MAT={Tex.GUNMETAL,Tex.BRASS,Tex.WOOD,Tex.LEATHER,Tex.WOOD,Tex.GUNMETAL,Tex.LEATHER,Tex.LEATHER,Tex.FABRIC,Tex.LEATHER,Tex.SHELL,Tex.BRASS};
    static final float[] MUZZLE={.645f,0,.012f},PORT={.075f,-.024f,.016f},LOAD_PORT={.06f,0,-.04f};
    static final float[] PUMP_GRAB={.29f,0,-.02f};
    /** Where the left hand holds a shell, relative to the hand's grab point. */
    static final float[] SHELL_IN_HAND={-.01f,.0f,.02f};

    static float[][] build(){
        float[][] out=new float[PARTS][];
        Model m=new Model(8);   // body: receiver, barrel, magazine tube, rib, port, trigger
        m.box(-.06f,-.022f,-.03f,.17f,.022f,.038f);                    // receiver
        m.box(.02f,-.0245f,.0f,.115f,-.021f,.03f);                     // ejection port frame (proud of the right side)
        m.cyl(.17f,.645f,.0118f,.0112f,0,.012f,16,true,true);          // barrel
        m.cyl(.615f,.645f,.0128f,.0128f,0,.012f,16,true,true);         // muzzle crown
        m.box(.17f,-.0035f,.022f,.635f,.0035f,.027f);                  // vent rib
        m.cyl(.15f,.565f,.0125f,.0125f,0,-.02f,14,true,false);         // magazine tube
        m.cyl(.565f,.585f,.0135f,.013f,0,-.02f,14,true,true);          // tube cap
        m.box(.535f,-.013f,-.034f,.553f,.013f,.024f);                  // barrel band
        m.box(.0f,-.006f,-.065f,.08f,.006f,-.058f);                    // trigger guard
        m.box(.07f,-.006f,-.065f,.08f,.006f,-.03f);m.box(-.003f,-.006f,-.065f,.007f,.006f,-.03f);
        m.taper(.035f,.045f,.003f,.012f,.003f,.01f,0,-.042f,0,-.044f); // trigger
        out[P_BODY]=m.data();
        m=new Model(10);m.cyl(.62f,.628f,.0028f,.0022f,0,.031f,8,true,true);out[P_BEAD]=m.data();
        m=new Model(4);                                                // walnut stock and pistol grip
        m.taper(-.06f,-.4f,.019f,.03f,.022f,.062f,0,.002f,0,-.055f);
        m.taper(-.055f,-.15f,.018f,.026f,.019f,.028f,0,-.04f,0,-.115f);
        out[P_STOCK]=m.data();
        m=new Model(10);m.box(-.425f,-.0225f,-.12f,-.4f,.0225f,.01f);out[P_PAD]=m.data();
        m=new Model(5);                                                // the sliding pump (ribbed walnut)
        m.cyl(.22f,.37f,.0225f,.0225f,0,-.02f,14,true,true);
        for(int i=0;i<6;i++){float x=.235f+i*.022f;m.cyl(x,x+.006f,.0242f,.0242f,0,-.02f,14,false,false);}
        out[P_PUMP]=m.data();
        m=new Model(8);m.box(.12f,.019f,-.026f,.24f,.022f,-.016f);m.box(.12f,-.022f,-.026f,.24f,-.019f,-.016f);out[P_BARS]=m.data();
        out[P_RHAND]=rightHand();out[P_LHAND]=leftHand();
        m=new Model(3);m.taper(0,.84f,.048f,.044f,.035f,.031f,0,0,0,0);out[P_SLEEVE]=m.data();    // forearm in a sleeve, elbow at x=0, wrist at x=1
        m=new Model(6);m.taper(.8f,1.0f,.04f,.036f,.035f,.031f,0,0,0,0);out[P_CUFF]=m.data();     // glove cuff
        m=new Model(30);m.cyl(0,.058f,.0098f,.0098f,0,0,12,false,true);out[P_HULL]=m.data();     // shell: red hull
        m=new Model(30);m.cyl(-.012f,0,.0102f,.0102f,0,0,12,true,false);m.cyl(-.0145f,-.012f,.0113f,.0113f,0,0,12,true,true);out[P_BASE]=m.data();
        return out;
    }
    /** Right hand wrapped round the pistol grip, index finger on the trigger. Built in a frame along the grip. */
    private static float[] rightHand(){
        Model m=new Model(10);
        float[] o=v(-.07f,0,-.05f),gx=Model.norm(v(-.075f,0,-.075f)),gy=v(0,1,0),gz=Model.cross(gx,gy);   // gx down the grip, gz toward the front of the grip
        m.frame(o,gx,gy,gz);
        m.box(-.005f,-.047f,-.042f,.075f,-.018f,.02f);                 // back of the hand (right side of the grip)
        for(int i=0;i<3;i++){float a=.022f+i*.021f;
            m.box(a,-.042f,.02f,a+.018f,.024f,.039f);                    // fingers across the front of the grip
            m.box(a,.018f,-.012f,a+.018f,.031f,.03f);}                  // fingertips on the left side
        m.box(-.026f,-.034f,.026f,-.006f,-.012f,.045f);                // index finger base
        m.noFrame();
        m.taper(.006f,.04f,.007f,.007f,.006f,.006f,-.012f,-.034f,-.004f,-.044f);   // index finger to the trigger
        m.taper(-.07f,-.025f,.01f,.009f,.009f,.008f,.016f,.016f,.022f,.004f);      // thumb over the top of the grip
        return m.data();
    }
    /** Left hand cupping the pump from below, fingers curled up its left side, thumb on the right. Origin = grab point. */
    private static float[] leftHand(){
        Model m=new Model(10);
        m.box(-.045f,-.03f,-.052f,.038f,.022f,-.024f);                 // palm under the pump
        for(int i=0;i<4;i++){float a=-.04f+i*.019f;
            m.box(a,.02f,-.046f,a+.016f,.034f,.004f);                    // fingers up the left side
            m.box(a,.004f,.0f,a+.016f,.028f,.016f);}                    // fingertips over the top
        m.box(-.032f,-.036f,-.04f,.018f,-.022f,-.004f);                // thumb along the right side
        m.box(-.085f,-.032f,-.064f,-.04f,.02f,-.026f);                 // heel of the hand / wrist
        return m.data();
    }
    static final float[] RIGHT_WRIST={-.12f,-.03f,-.1f},LEFT_WRIST={-.09f,-.006f,-.046f};   // forearms join here (gun / hand space)

    // ------------------------------------------------------------------ ammunition and state
    static final int CAPACITY=8;
    int ammo=CAPACITY,reserve=40;
    static final int IDLE=0,FIRE=1,RELOAD_IN=2,RELOAD_SHELL=3,RELOAD_OUT=4,RACK=5;
    int state=IDLE;float t;boolean emptyReload,wantFire;
    /** Events for the game this frame (sounds, pellets, ejected shells). */
    static final int EV_FIRE=1,EV_PUMP_BACK=2,EV_PUMP_FWD=4,EV_EJECT=8,EV_SHELL_IN=16,EV_DRY=32,EV_RELOAD=64;
    int events;
    private boolean ejected,racked,inserted,firstShell;

    /** Advances the animation; fireHeld / reloadTap come from the controls. */
    void update(float dt,boolean fireHeld,boolean reloadTap){
        events=0;t+=dt;
        if(reloadTap&&state==IDLE&&ammo<CAPACITY&&reserve>0)begin(RELOAD_IN);
        switch(state){
            case IDLE:
                if(fireHeld){if(ammo>0)shoot();else if(t>.3f){events|=EV_DRY;t=0;if(reserve>0){emptyReload=true;begin(RELOAD_IN);}}}
                break;
            case FIRE:
                if(t>=.32f&&!racked){racked=true;events|=EV_PUMP_BACK;}
                if(t>=.42f&&!ejected){ejected=true;events|=EV_EJECT;}
                if(t>=.56f&&racked&&!inserted){inserted=true;events|=EV_PUMP_FWD;}
                if(t>=.78f){begin(IDLE);if(ammo==0&&reserve>0){emptyReload=true;begin(RELOAD_IN);}}
                break;
            case RELOAD_IN:if(t>=.35f){begin(RELOAD_SHELL);firstShell=true;}break;
            case RELOAD_SHELL:
                if(fireHeld&&ammo>0){begin(RELOAD_OUT);wantFire=true;break;}
                if(t>=.45f&&!inserted){inserted=true;ammo++;reserve--;events|=EV_SHELL_IN;}
                if(t>=.62f){firstShell=false;if(ammo<CAPACITY&&reserve>0)begin(RELOAD_SHELL);else begin(RELOAD_OUT);}
                break;
            case RELOAD_OUT:if(t>=.38f){if(emptyReload){begin(RACK);}else begin(IDLE);}break;
            case RACK:
                if(t>=.08f&&!racked){racked=true;events|=EV_PUMP_BACK;}
                if(t>=.3f&&!inserted){inserted=true;events|=EV_PUMP_FWD;}
                if(t>=.5f){emptyReload=false;begin(IDLE);}
                break;
        }
        if(state==IDLE&&wantFire){wantFire=false;if(ammo>0)shoot();}
    }
    private void shoot(){ammo--;begin(FIRE);events|=EV_FIRE;}
    private void begin(int s){state=s;t=0;ejected=racked=inserted=false;if(s==RELOAD_IN)events|=EV_RELOAD;}
    boolean reloading(){return state==RELOAD_IN||state==RELOAD_SHELL||state==RELOAD_OUT||state==RACK;}

    // ------------------------------------------------------------------ pose
    /** Part transforms in camera space, rebuilt by pose(). */
    final float[][] mat=new float[PARTS][];final boolean[] visible=new boolean[PARTS];
    float[] muzzleCam;
    private float swayX,swayY;
    private static float ease(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    private static float[] lerp(float[] a,float[] b,float t){return v(lerp(a[0],b[0],t),lerp(a[1],b[1],t),lerp(a[2],b[2],t));}

    /**
     * Builds every part's transform. bob: walking phase, bobAmt 0..1, lookDX/DY: this frame's look motion (for sway),
     * time: seconds (breathing), aimIn unused.
     */
    void pose(float bobPhase,float bobAmt,float lookDX,float lookDY,float time,float dt){
        // weapon sway lags behind looking around, settles back
        swayX+=(-lookDX*.0009f-swayX)*Math.min(1,dt*7);swayY+=(lookDY*.0009f-swayY)*Math.min(1,dt*7);
        float bx=(float)Math.sin(bobPhase)*.011f*bobAmt,bz=-(float)Math.abs(Math.cos(bobPhase))*.009f*bobAmt+(float)Math.sin(time*1.7f)*.0018f;
        float fx=.4f,fy=-.15f,fz=-.16f;                  // rest: forward, to the right, below the eye
        float pitch=-.035f,yaw=.075f,roll=0,kick=0,pump=0;float drop=0,back=0;
        float[] lhand=null;float shellT=-1;            // left hand position (gun space) when not on the pump
        float recoil=0;
        switch(state){
            case FIRE:{float r=t<.045f?t/.045f:(float)Math.exp(-(t-.045f)*10);recoil=r;
                kick=.075f*r;pitch=-.26f*r;roll=.04f*r;
                float pb=ease((t-.28f)/.12f)*(1-ease((t-.5f)/.12f));pump=-.095f*pb;pitch+=-.05f*pb;roll+=-.08f*pb;break;}
            case RACK:{float pb=ease(t/.12f)*(1-ease((t-.22f)/.12f));pump=-.095f*pb;pitch=-.06f*pb;roll=-.1f*pb;break;}
            case RELOAD_IN:case RELOAD_SHELL:case RELOAD_OUT:{
                float b=state==RELOAD_IN?ease(t/.35f):state==RELOAD_OUT?1-ease(t/.38f):1;
                roll=.55f*b;pitch=-.035f-.1f*b;drop=.03f*b;back=.02f*b;yaw=.075f+.12f*b;
                float[] belt=v(-.05f,-.22f,-.24f),near=v(.13f,.02f,-.085f),in=v(.07f,0,-.05f),pumpP=PUMP_GRAB;
                if(state==RELOAD_IN){lhand=lerp(pumpP,belt,ease((t-.1f)/.25f));}
                else if(state==RELOAD_SHELL){float u=t;
                    if(u<.18f)lhand=lerp(ammoHandStart(),belt,ease(u/.18f));
                    else if(u<.36f){lhand=lerp(belt,near,ease((u-.18f)/.18f));shellT=0;}
                    else if(u<.47f){lhand=lerp(near,in,ease((u-.36f)/.11f));shellT=ease((u-.36f)/.11f);}
                    else lhand=lerp(in,v(.1f,0,-.07f),ease((u-.47f)/.15f));
                    if(u>=.45f)shellT=-1;}
                else lhand=lerp(v(.1f,0,-.07f),pumpP,ease(t/.3f));
                break;}
        }
        // gun → camera: rest offset, bob and sway, then recoil/reload rotations about the grip
        float[] G=M.mul(M.tr(fx+bx*.2f-kick-back,fy+bx+swayX,fz+bz+swayY-drop),M.mul(M.rz(yaw+swayX*2),M.mul(M.ry(pitch),M.mul(M.rx(roll),M.tr(.06f,0,.05f)))));
        G=M.mul(G,M.tr(-.06f,0,-.05f));
        for(int i=0;i<PARTS;i++)visible[i]=true;
        mat[P_BODY]=mat[P_BEAD]=mat[P_STOCK]=mat[P_PAD]=mat[P_RHAND]=G;
        float[] P=M.mul(G,M.tr(pump,0,0));mat[P_PUMP]=mat[P_BARS]=P;
        float[] L=lhand==null?M.mul(P,M.tr(PUMP_GRAB[0],PUMP_GRAB[1],PUMP_GRAB[2])):M.mul(G,M.mul(M.tr(lhand[0],lhand[1],lhand[2]),M.rx(-.5f)));
        mat[P_LHAND]=L;
        if(shellT>=0){float[] S=M.mul(L,M.tr(SHELL_IN_HAND[0]+.04f*shellT,SHELL_IN_HAND[1],SHELL_IN_HAND[2]));mat[P_HULL]=mat[P_BASE]=S;}
        else visible[P_HULL]=visible[P_BASE]=false;
        muzzleCam=M.pt(G,MUZZLE);portCam=M.pt(G,PORT);
        // forearms: from fixed elbows (camera space) to the wrists
        float[] rw=M.pt(G,RIGHT_WRIST),lw=M.pt(L,LEFT_WRIST);
        rightArm=arm(v(-.04f,-.26f,-.34f),rw);leftArm=arm(v(.12f,.1f,-.42f),lw);
        mat[P_SLEEVE]=rightArm;mat[P_CUFF]=rightArm;
        recoilNow=recoil;
    }
    float[] rightArm,leftArm,portCam;float recoilNow;
    private float[] ammoHandStart(){return firstShell?v(-.05f,-.22f,-.24f):v(.1f,0,-.07f);}
    /** A matrix stretching the unit forearm (x 0..1) from elbow to wrist. */
    private static float[] arm(float[] elbow,float[] wrist){
        float[] d=Model.sub(wrist,elbow);float len=(float)Math.sqrt(Model.dot(d,d));float[] x=Model.norm(d);
        float[] y=Model.norm(Model.cross(v(0,0,1),x));float[] z=Model.cross(x,y);
        return M.basis(elbow,v(x[0]*len,x[1]*len,x[2]*len),y,z);
    }
}
