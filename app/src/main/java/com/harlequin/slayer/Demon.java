package com.harlequin.slayer;

/**
 * A demon: red-armoured, horned, on the same hoofed legs and procedural locomotion as the warden (a Hero rig driven
 * by its own Player controller), with a hand cannon on his right forearm that throws blue fireballs.
 *
 * Behaviour is an {@link Ai} state machine:
 *   IDLE    stands, looks about; after a while wanders (PATROL)
 *   PATROL  walks to a nearby spot it can reach in a straight line
 *   ALERT   has just seen / heard / been hit: turns toward the threat and roars
 *   CHASE   runs at the warden along the navigation flow field (straight at her when the way is clear)
 *   ATTACK  stops, raises the cannon, charges and fires a fireball at where she is going to be
 *   STRAFE  sidesteps with the cannon up between shots; backs off if she is too close
 *   PAIN    a stagger when a hit is heavy enough
 *   DEAD    topples over
 * Pure Java.
 */
final class Demon {
    static final float MAX_HEALTH=150,SCALE=1.08f,EYE=1.68f,RADIUS=.33f;
    static final float SIGHT=34f,FOV=1.2f,ATTACK_RANGE=24f;
    private static final Gun NOGUN=new Gun();
    static final float[] MUZZLE_LOCAL=new float[]{.675f,0,0};

    final Hero rig=new Hero();final Player body=new Player();final Ai<Demon> ai;
    final float spawnX,spawnY,spawnYaw;final int id;
    float health=MAX_HEALTH;boolean dead;float deathT,fallYaw,painCool,hitFlash;
    /** Controls for this frame, set by the states. */
    float moveX,moveY,wantYaw,aimYaw,aimPitch;boolean aiming;
    /** Free per-state values. */
    float timer,tx,ty,dir;boolean fired;
    /** Cannon charge (0..1) for the glow, and the world transform (scale about the feet, the fall when dead). */
    float charge;float[] world=M.id();
    private float stuckT,stuckX,stuckY,unstick,unstickDir;
    Enemies E;

    Demon(int id,float x,float y,float yaw){this.id=id;spawnX=x;spawnY=y;spawnYaw=yaw;ai=new Ai<>(1000+id*7919L);rig.hasGun=false;}

    void reset(Level L){
        body.x=spawnX;body.y=spawnY;body.z=L.floorUnder(spawnX,spawnY,Player.RADIUS);body.vx=body.vy=body.vz=0;body.yaw=spawnYaw;body.onGround=true;
        rig.bodyYaw=spawnYaw;health=MAX_HEALTH;dead=false;deathT=0;painCool=0;charge=0;hitFlash=0;
        ai.alerted=false;ai.canSee=false;ai.sinceSeen=99;ai.cooldown=0;ai.set(this,null);ai.set(this,IDLE);
    }

    float[] eye(){return new float[]{body.x,body.y,body.z+EYE*SCALE};}
    float[] muzzle(){return M.pt(world,M.pt(rig.sawMat,MUZZLE_LOCAL));}
    float[] headPos(){return M.pt(world,M.pt(rig.mat[Hero.P_HEAD],Hero.HEAD));}

    void update(float dt,float time,Level L){
        hitFlash=Math.max(0,hitFlash-dt*4);painCool-=dt;
        if(dead){deathT+=dt;world=deathMatrix();return;}
        Player p=E.player;
        ai.perceive(L,eye(),rig.bodyYaw,new float[]{p.x,p.y,p.z+1.2f},SIGHT,FOV,dt,.15f);
        moveX=moveY=0;aiming=false;wantYaw=body.yaw;aimYaw=ai.bearing;
        aimPitch=(float)Math.atan2(p.z+1.1f-(body.z+1.3f*SCALE),Math.max(.5f,ai.dist));
        ai.think(this,dt);
        // getting unstuck: if trying to move but going nowhere, sidestep for a moment
        stuckT+=dt;
        if(stuckT>.8f){boolean trying=Math.abs(moveY)>.3f||Math.abs(moveX)>.3f;float moved=(float)Math.hypot(body.x-stuckX,body.y-stuckY);
            if(trying&&moved<.2f){unstick=.5f;unstickDir=ai.rng.nextBoolean()?1:-1;}stuckT=0;stuckX=body.x;stuckY=body.y;}
        if(unstick>0){unstick-=dt;moveX=unstickDir;moveY=Math.max(moveY,.3f);}
        // turn toward the wanted heading, then move (the controller is camera-relative: moveY forward, moveX right)
        body.yaw+=Hero.wrap(wantYaw-body.yaw)*Math.min(1,dt*7);
        body.health=1e6f;
        float ox=body.x,oy=body.y;
        body.update(dt,L,moveX,moveY,0,0,false,0);
        if(!Nav.standable(L.atF(body.x,body.y))&&Nav.standable(L.atF(ox,oy))){body.x=ox;body.y=oy;body.vx=body.vy=0;}   // never step into lava
        charge=Hero.approach(charge,aiming?1:0,aiming?5:3,dt);
        rig.update(dt,time,body,L,aimYaw,aimPitch,false,aiming,NOGUN);
        world=M.mul(M.tr(body.x,body.y,body.z),M.mul(M.scale(SCALE,SCALE,SCALE),M.tr(-body.x,-body.y,-body.z)));
    }
    /** The 3D editor: stand at the spawn in the idle pose (no AI, no movement). */
    void pose(float dt,float time,Level L){
        body.health=1e6f;body.update(dt,L,0,0,0,0,false,0);
        rig.update(dt,time,body,L,body.yaw,0,false,false,NOGUN);
        world=M.mul(M.tr(body.x,body.y,body.z),M.mul(M.scale(SCALE,SCALE,SCALE),M.tr(-body.x,-body.y,-body.z)));
    }
    /** Topples away from the killing blow, pivoting on the hooves. */
    float[] deathMatrix(){
        float t=Math.min(1,deathT/.85f);float a=t*t*1.48f;float drop=-.06f*t;
        float[] R=M.mul(M.rz(fallYaw),M.mul(M.ry(a),M.rz(-fallYaw)));
        return M.mul(M.tr(body.x,body.y,body.z+drop),M.mul(R,M.mul(M.scale(SCALE,SCALE,SCALE),M.tr(-body.x,-body.y,-body.z))));
    }

    /** A hit for `amount` from direction (dx,dy) (the way the shot travelled). */
    void damage(float amount,float dx,float dy){
        if(dead)return;health-=amount;hitFlash=1;
        Player p=E.player;ai.hear(p.x,p.y,p.z);
        if(health<=0){fallYaw=(float)Math.atan2(dy,dx);ai.set(this,DEAD);return;}
        if(amount>=14&&painCool<=0&&ai.rng.nextFloat()<.65f){tx=dx;ty=dy;ai.set(this,PAIN);}
        else if(ai.state()==IDLE||ai.state()==PATROL)ai.set(this,ALERT);
    }

    // ---------------------------------------------------------------- steering
    private void face(float yaw){wantYaw=yaw;}
    private void headTo(float x,float y,float speed){face((float)Math.atan2(y-body.y,x-body.x));moveY=speed;}
    /** Toward the warden: straight when the way is clear, else down the flow field. */
    private void pursue(float speed){
        Player p=E.player;
        if(ai.canSee&&E.nav.straight(body.x,body.y,p.x,p.y)){headTo(p.x,p.y,speed);return;}
        float[] n=E.nav.next(body.x,body.y);
        if(n!=null)headTo(n[0],n[1],speed);else headTo(ai.lastKnown[0],ai.lastKnown[1],speed*.6f);
    }

    // ---------------------------------------------------------------- states
    static final Ai.State<Demon> IDLE=new Ai.State<Demon>(){
        public String name(){return "idle";}
        public void enter(Demon d,Ai<Demon> ai){d.timer=2+ai.rng.nextFloat()*4;}
        public Ai.State<Demon> update(Demon d,Ai<Demon> ai,float dt){
            if(ai.alerted)return ALERT;
            if(ai.stateTime>d.timer)return PATROL;
            return this;}};
    static final Ai.State<Demon> PATROL=new Ai.State<Demon>(){
        public String name(){return "patrol";}
        public void enter(Demon d,Ai<Demon> ai){
            d.tx=d.body.x;d.ty=d.body.y;
            for(int k=0;k<12;k++){float a=ai.rng.nextFloat()*6.2832f,r=1.5f+ai.rng.nextFloat()*4.5f;float x=d.body.x+(float)Math.cos(a)*r,y=d.body.y+(float)Math.sin(a)*r;
                if(d.E.nav.straight(d.body.x,d.body.y,x,y)){d.tx=x;d.ty=y;break;}}
            d.timer=7;}
        public Ai.State<Demon> update(Demon d,Ai<Demon> ai,float dt){
            if(ai.alerted)return ALERT;
            if(Math.hypot(d.tx-d.body.x,d.ty-d.body.y)<.45f||ai.stateTime>d.timer)return IDLE;
            d.headTo(d.tx,d.ty,.45f);return this;}};
    static final Ai.State<Demon> ALERT=new Ai.State<Demon>(){
        public String name(){return "alert";}
        public void enter(Demon d,Ai<Demon> ai){d.E.event(Enemies.EV_ROAR,d);}
        public Ai.State<Demon> update(Demon d,Ai<Demon> ai,float dt){
            d.face((float)Math.atan2(ai.lastKnown[1]-d.body.y,ai.lastKnown[0]-d.body.x));d.aimYaw=d.wantYaw;
            if(ai.stateTime<.7f)return this;
            if(ai.canSee&&ai.dist<ATTACK_RANGE&&ai.cooldown<=0&&d.E.canAttack())return ATTACK;
            return CHASE;}};
    static final Ai.State<Demon> CHASE=new Ai.State<Demon>(){
        public String name(){return "chase";}
        public Ai.State<Demon> update(Demon d,Ai<Demon> ai,float dt){
            if(ai.canSee&&ai.dist<ATTACK_RANGE&&ai.cooldown<=0&&d.E.canAttack())return ATTACK;
            if(ai.canSee&&ai.dist<3f)return STRAFE;
            if(ai.sinceSeen>12){ai.alerted=false;return IDLE;}
            d.pursue(.9f);return this;}};
    static final Ai.State<Demon> ATTACK=new Ai.State<Demon>(){
        public String name(){return "attack";}
        public void enter(Demon d,Ai<Demon> ai){d.fired=false;d.E.attackers++;d.E.event(Enemies.EV_CHARGE,d);}
        public void exit(Demon d,Ai<Demon> ai){d.E.attackers--;}
        public Ai.State<Demon> update(Demon d,Ai<Demon> ai,float dt){
            d.aiming=true;d.face(ai.bearing);
            if(!d.fired&&!ai.canSee&&ai.sinceSeen>.6f)return CHASE;   // lost her during the wind-up
            if(!d.fired&&ai.stateTime>.65f){d.E.fireball(d);d.fired=true;}
            if(ai.stateTime>1.05f){ai.cooldown=2f+ai.rng.nextFloat()*2f;return ai.canSee?STRAFE:CHASE;}
            return this;}};
    static final Ai.State<Demon> STRAFE=new Ai.State<Demon>(){
        public String name(){return "strafe";}
        public void enter(Demon d,Ai<Demon> ai){d.dir=ai.rng.nextBoolean()?1:-1;d.timer=.7f+ai.rng.nextFloat()*.9f;}
        public Ai.State<Demon> update(Demon d,Ai<Demon> ai,float dt){
            d.aiming=true;d.face(ai.bearing);
            d.moveX=d.dir*.75f;d.moveY=ai.dist<4f?-.7f:ai.dist>16?.5f:0;
            if(d.unstick>0)d.dir=-d.dir;
            if(ai.stateTime<d.timer)return this;
            if(ai.canSee&&ai.cooldown<=0&&d.E.canAttack())return ATTACK;
            return ai.canSee&&ai.dist<4?STRAFE:CHASE;}};
    static final Ai.State<Demon> PAIN=new Ai.State<Demon>(){
        public String name(){return "pain";}
        public void enter(Demon d,Ai<Demon> ai){d.painCool=1.1f;float l=(float)Math.hypot(d.tx,d.ty);if(l>1e-3f)d.rig.bump(d.tx/l,d.ty/l,3.5f);d.E.event(Enemies.EV_PAIN,d);}
        public Ai.State<Demon> update(Demon d,Ai<Demon> ai,float dt){
            if(ai.stateTime<.38f)return this;
            return ai.canSee&&ai.cooldown<=0&&d.E.canAttack()?ATTACK:CHASE;}};
    static final Ai.State<Demon> DEAD=new Ai.State<Demon>(){
        public String name(){return "dead";}
        public void enter(Demon d,Ai<Demon> ai){d.dead=true;d.deathT=0;d.charge=0;d.E.event(Enemies.EV_DIE,d);}
        public Ai.State<Demon> update(Demon d,Ai<Demon> ai,float dt){return this;}};
}
