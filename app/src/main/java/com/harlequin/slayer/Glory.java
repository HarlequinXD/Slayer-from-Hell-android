package com.harlequin.slayer;

import static com.harlequin.slayer.Model.v;

/**
 * A chainsaw glory kill: a short cutscene in which the warden lunges in, seizes the demon by the shoulder, cocks the
 * saw back and drives it into his gut, then rips it up through his chest while he arches back screaming, clawing at
 * the bar, his cannon arm flailing and knees buckling; she wrenches it out and shoves him away, and he topples dead.
 *
 * Both bodies are the same procedural rig ({@link Hero}), steered through {@link Hero.Cine}: this class is the
 * choreography (keyframed torso, head and hand targets for both, eased with smoothstep), the blocking (where each
 * of them stands), the timing (a hit-stop in slow motion on the impact), the cues for sound and effects, and the
 * cinematography: three cuts (a side two-shot that pushes in on the thrust, a low angle looking up during the rip,
 * a wide shot from behind her as he falls), all from one side of the action so the screen direction never flips,
 * kept clear of walls. Pure Java, so it runs in the unit tests.
 */
final class Glory {
    /** Reach to start one, how far in front she must face (cos 60 degrees), and the gap they end up at. */
    static final float REACH=2.2f,CONE=.5f,GAP=.7f;
    // the beats (seconds of cutscene time)
    static final float T_GRAB=.24f,T_THRUST=.46f,T_HIT=.53f,T_RIP=.62f,T_OUT=1.38f,T_SHOVE=1.5f,T_DIE=1.6f,DUR=2.15f;
    static final int CUE_GRAB=1,CUE_HIT=2,CUE_RIP=4,CUE_OUT=8,CUE_DIE=16,CUE_END=32,CUE_SHOVE=64;

    final Demon d;
    /** Unit vector from her to him, and her right (the camera's side). */
    final float ux,uy,sx,sy;
    private final float px0,py0,px1,py1,dx0,dy0;
    float t;
    /** This frame: her position and facing, his position and facing. */
    float px,py,pz,herYaw,dx,dy,dz,himYaw;
    final Hero.Cine her=new Hero.Cine(),him=new Hero.Cine();
    /** Camera this frame. */
    final float[] camPos=new float[3],camTarget=new float[3];float camFov=50;
    /** The current shot (0, 1, 2): a change is a cut. */
    int shot;

    private Glory(Player pl,Demon d,float px1,float py1,float dx0,float dy0,float side){
        this.d=d;px0=pl.x;py0=pl.y;this.px1=px1;this.py1=py1;this.dx0=dx0;this.dy0=dy0;
        float ex=dx0-px1,ey=dy0-py1,l=(float)Math.hypot(ex,ey);ux=ex/l;uy=ey/l;
        sx=uy*side;sy=-ux*side;   // her right (side 1) or left (side -1)
        px=pl.x;py=pl.y;pz=pl.z;dx=dx0;dy=dy0;dz=d.body.z;herYaw=(float)Math.atan2(uy,ux);himYaw=herYaw+(float)Math.PI;
    }

    /** A living demon in reach in front of her, with room to stage the kill: the glory kill, or null. */
    static Glory tryStart(Player pl,Enemies E,Level L){
        float fx=(float)Math.cos(pl.yaw),fy=(float)Math.sin(pl.yaw);Demon best=null;float bestD=REACH;
        for(Demon d:E.demons){if(d.dead)continue;
            float ex=d.body.x-pl.x,ey=d.body.y-pl.y,dist=(float)Math.hypot(ex,ey);
            if(dist>bestD||dist<1e-3f||Math.abs(d.body.z-pl.z)>.7f)continue;
            if((ex*fx+ey*fy)/dist<CONE)continue;
            float[] w=L.ray(pl.x,pl.y,pl.z+1.2f,ex/dist,ey/dist,0,dist);if(w!=null&&w[6]<dist-.35f)continue;
            best=d;bestD=dist;}
        if(best==null)return null;
        Demon d=best;float ex=d.body.x-pl.x,ey=d.body.y-pl.y,dist=(float)Math.hypot(ex,ey),ux=ex/dist,uy=ey/dist;
        // she closes the gap; if she can't stand there, he is dragged to her instead
        float tx=d.body.x-ux*GAP,ty=d.body.y-uy*GAP,tz=L.floorUnder(tx,ty,Player.RADIUS);
        float dx0=d.body.x,dy0=d.body.y;
        if(!standable(L,tx,ty,tz,pl.z)){tx=pl.x;ty=pl.y;dx0=pl.x+ux*GAP;dy0=pl.y+uy*GAP;
            if(!standable(L,dx0,dy0,L.floorUnder(dx0,dy0,Player.RADIUS),d.body.z))return null;}
        // the camera on her right unless the left has much more room
        float mx=(tx+dx0)*.5f,my=(ty+dy0)*.5f,mz=pl.z+1.15f;
        float rr=room(L,mx,my,mz,uy,-ux),rl=room(L,mx,my,mz,-uy,ux);
        return new Glory(pl,d,tx,ty,dx0,dy0,rl>rr+.5f?-1:1);
    }
    private static boolean standable(Level L,float x,float y,float floor,float z){
        return floor>-50&&Math.abs(floor-z)<.5f&&!L.blocked(x,y,floor,Player.RADIUS,Player.HEIGHT)&&Nav.standable(L.atF(x,y));
    }
    private static float room(Level L,float x,float y,float z,float dx,float dy){float[] h=L.ray(x,y,z,dx,dy,0,3);return h==null?3:h[6];}

    /** Playback speed: a brief hit-stop in slow motion as the saw goes in. */
    float timeScale(){
        if(t<T_HIT)return 1;float k=t-T_HIT;
        return k<.07f?.22f:k<.15f?Hero.mix(.22f,1,(k-.07f)/.08f):1;
    }
    boolean ripping(){return t>T_HIT&&t<T_OUT;}
    float ripAmount(){return ripping()?Hero.smooth(T_HIT,T_RIP,t)*(1-Hero.smooth(T_OUT-.1f,T_OUT,t)):0;}
    /** Camera shake (metres) for this moment. */
    float shake(){
        float s=0;if(t>T_HIT)s+=.07f*(float)Math.exp(-(t-T_HIT)*10);
        s+=.014f*ripAmount();if(t>T_DIE+.55f)s+=.03f*(float)Math.exp(-(t-T_DIE-.55f)*12);   // his body hits the floor
        return s;
    }

    // ---------------------------------------------------------------- keyframes
    /** Value at time x on a keyframed curve (smoothstep between keys). */
    static float key(float[] T,float[] V,float x){
        if(x<=T[0])return V[0];
        for(int i=1;i<T.length;i++)if(x<=T[i])return Hero.mix(V[i-1],V[i],Hero.smooth(T[i-1],T[i],x));
        return V[V.length-1];
    }
    static float[] key3(float[] T,float[][] V,float x){
        if(x<=T[0])return V[0].clone();
        for(int i=1;i<T.length;i++)if(x<=T[i])return Hero.lerpW(V[i-1],V[i],Hero.smooth(T[i-1],T[i],x));
        return V[V.length-1].clone();
    }
    // her: lunge and grab, cock back, thrust, hold, rip up, wrench out, recover
    private static final float[] HT={0,.3f,T_THRUST,T_HIT,T_RIP,T_OUT,T_SHOVE,DUR};
    private static final float[][] H_RHAND={v(.43f,-.13f,1.22f),v(.08f,-.3f,1.47f),v(.06f,-.31f,1.5f),v(.3f,-.13f,1.16f),v(.29f,-.13f,1.17f),v(.32f,-.1f,1.46f),v(.12f,-.3f,1.38f),v(.43f,-.13f,1.22f)};
    private static final float[][] H_RPOLE={v(-.2f,-.8f,-.6f),v(-.2f,-.4f,-1f),v(-.2f,-.4f,-1f),v(-.3f,-.35f,-1f),v(-.3f,-.35f,-1f),v(0,-.5f,-1f),v(-.6f,-.6f,-.5f),v(-.2f,-.8f,-.6f)};
    private static final float[] H_TWIST={0,-.32f,-.36f,.3f,.22f,0,-.15f,0};
    private static final float[] H_LEAN={0,.04f,.07f,.22f,.22f,.26f,-.04f,0};
    private static final float[] H_CROUCH={0,.07f,.09f,.13f,.12f,.04f,.03f,0};
    private static final float[] H_HEAD={0,.04f,.04f,-.06f,-.05f,.22f,.08f,0};
    private static final float[] H_ROLL={0,.03f,.03f,-.04f,-.04f,-.02f,0,0};
    // him: startled, seized, the saw goes in (doubles over), arches back as it rips up, released, falls
    private static final float[] DT_={0,.2f,T_THRUST,T_HIT,T_RIP,1f,T_OUT,T_DIE};
    private static final float[] D_LEAN={0,-.14f,-.1f,.3f,.24f,-.1f,-.24f,-.3f};
    private static final float[] D_TWIST={0,.05f,0,-.1f,-.08f,.1f,0,0};
    private static final float[] D_CROUCH={0,.02f,.03f,.1f,.1f,.13f,.16f,.14f};
    private static final float[] D_HEAD={0,.1f,.05f,-.35f,-.25f,.55f,.85f,.6f};
    private static final float[][] D_RHAND={v(.2f,-.25f,1.03f),v(.32f,-.36f,1.3f),v(.22f,-.4f,1.16f),v(.15f,-.45f,1.2f),v(.15f,-.45f,1.22f),v(.1f,-.5f,1.35f),v(.06f,-.45f,1.2f),v(.05f,-.35f,.95f)};
    private static final float[][] D_LHAND={v(.02f,.25f,.97f),v(.26f,.28f,1.3f),v(.24f,.3f,1.22f),v(.3f,.12f,1.15f),v(.3f,.12f,1.15f),v(.3f,.12f,1.25f),v(.15f,.3f,1.1f),v(.05f,.3f,.95f)};
    /** Her position (lunge) and his (pushed back by the rip and the shove) along the line between them. */
    private static final float[] PT={0,.22f},DPT={0,T_RIP,T_OUT,T_SHOVE+.08f},DPUSH={0,0,.02f,.42f};

    /**
     * Advances the cutscene and poses both actors' overrides; returns the cues crossed (CUE_*).
     * @param sawMat her saw's world matrix last frame (his hand claws at the bar).
     */
    int step(float dt,Level L,float[] sawMat){
        float t0=t;t+=dt;int cue=0;
        if(t0<T_GRAB&&t>=T_GRAB)cue|=CUE_GRAB;if(t0<T_HIT&&t>=T_HIT)cue|=CUE_HIT;if(t0<T_RIP&&t>=T_RIP)cue|=CUE_RIP;
        if(t0<T_OUT&&t>=T_OUT)cue|=CUE_OUT;if(t0<T_SHOVE&&t>=T_SHOVE)cue|=CUE_SHOVE;if(t0<T_DIE&&t>=T_DIE)cue|=CUE_DIE;if(t>=DUR)cue|=CUE_END;
        float rip=ripAmount();
        // blocking
        float lk=Hero.smooth(PT[0],PT[1],t);px=Hero.mix(px0,px1,lk);py=Hero.mix(py0,py1,lk);float f=L.floorUnder(px,py,Player.RADIUS);if(f>-50)pz=f;
        float push=key(DPT,DPUSH,t);float ndx=dx0+ux*push,ndy=dy0+uy*push;
        if(!L.blocked(ndx,ndy,dz,Player.RADIUS,Player.HEIGHT)){dx=ndx;dy=ndy;}float g=L.floorUnder(dx,dy,Player.RADIUS);if(g>-50)dz=g;
        herYaw=(float)Math.atan2(uy,ux);
        himYaw=herYaw+(float)Math.PI+.12f*rip*(float)Math.sin(t*23);
        // ---------------------------------------------------------------- her
        Hero.Cine c=her;c.yaw=herYaw;
        c.twist=key(HT,H_TWIST,t);c.lean=key(HT,H_LEAN,t);c.crouch=key(HT,H_CROUCH,t);c.roll=key(HT,H_ROLL,t);
        c.headYaw=0;c.headPitch=key(HT,H_HEAD,t);
        float[] rh=key3(HT,H_RHAND,t);rh[2]+=.025f*rip*(float)Math.sin(t*41);rh[0]+=.02f*rip*(float)Math.sin(t*29);   // tearing: the saw bucks as it chews
        c.rHand=rh;c.rPole=key3(HT,H_RPOLE,t);c.rWorld=false;c.rW=Hero.smooth(0,.08f,t)*(1-Hero.smooth(DUR-.35f,DUR,t));
        c.shake=.004f*rip;
        // her left hand seizes his right shoulder (his pauldron), holds him on the saw, then shoves him off it
        float[] sh=hisShoulder();float shove=Hero.smooth(T_OUT,T_SHOVE,t);
        c.lHand=v(sh[0]-ux*(.08f-.36f*shove),sh[1]-uy*(.08f-.36f*shove),sh[2]-.03f);c.lWorld=true;
        c.lPole=v(-.4f,.9f,-.6f);c.lW=Hero.smooth(.04f,T_GRAB,t)*(1-Hero.smooth(T_SHOVE+.05f,T_SHOVE+.4f,t));
        // ---------------------------------------------------------------- him
        Hero.Cine m=him;m.yaw=himYaw;
        float conv=rip*(float)Math.sin(t*55);   // convulsing on the saw
        m.lean=key(DT_,D_LEAN,t)+.05f*conv;m.twist=key(DT_,D_TWIST,t)+.07f*conv;m.crouch=key(DT_,D_CROUCH,t);m.roll=.06f*rip*(float)Math.sin(t*17);
        m.headYaw=.2f*rip*(float)Math.sin(t*9);m.headPitch=key(DT_,D_HEAD,t)+.12f*conv;
        float[] dr=key3(DT_,D_RHAND,t);   // the cannon arm flails
        dr[0]+=.07f*rip*(float)Math.sin(t*13);dr[1]-=.09f*rip*Math.abs((float)Math.sin(t*9));dr[2]+=.18f*rip*(float)Math.sin(t*11);
        m.rHand=dr;m.rPole=v(0,-1,-.4f);m.rWorld=false;m.rW=1;
        // his left hand claws at the bar while it is in him
        float[] lh=key3(DT_,D_LHAND,t);float clutch=Hero.smooth(T_HIT+.03f,T_RIP+.06f,t)*(1-Hero.smooth(T_OUT-.05f,T_OUT+.12f,t));
        if(clutch>0&&sawMat!=null&&d.rig.mat[Hero.P_TORSO]!=null){float[] bar=M.pt(sawMat,v(.55f+.04f*(float)Math.sin(t*31),0,.03f));
            float[] local=toTorso(toRig(bar));lh=Hero.lerpW(lh,local,clutch);}
        m.lHand=lh;m.lPole=v(-.2f,1,-.5f);m.lWorld=false;m.lW=1;m.shake=0;
        camera(L);
        return cue;
    }
    /** His right shoulder in the world (from last frame's pose), or an estimate before he has been posed. */
    private float[] hisShoulder(){
        float[] T=d.rig.mat[Hero.P_TORSO];
        if(T!=null)return M.pt(d.world,M.pt(T,Hero.shoulder(-1)));
        return v(dx-ux*.15f-sx*.22f,dy-uy*.15f-sy*.22f,dz+1.45f*Demon.SCALE);
    }
    /** A world point into his rig's space (the rig is drawn scaled about his hooves), then into his torso's bind space. */
    private float[] toRig(float[] w){return v(dx+(w[0]-dx)/Demon.SCALE,dy+(w[1]-dy)/Demon.SCALE,dz+(w[2]-dz)/Demon.SCALE);}
    private float[] toTorso(float[] r){return M.pt(M.inv(d.rig.mat[Hero.P_TORSO]),r);}

    // ---------------------------------------------------------------- cinematography
    private void camera(Level L){
        float mx=(px+dx)*.5f,my=(py+dy)*.5f,mz=pz+1.15f;float[] pos,tgt;float fov;
        if(t<T_RIP){shot=0;   // side two-shot, orbiting slowly behind her, pushing in on the thrust
            float a=-.18f+.32f*(t/T_RIP),dist=2.4f-.4f*Hero.smooth(T_THRUST,T_HIT+.05f,t);
            float ca=(float)Math.cos(a),sa=(float)Math.sin(a);
            float ox=sx*ca-ux*sa,oy=sy*ca-uy*sa;
            pos=v(mx+ox*dist,my+oy*dist,mz-.05f+.1f*(t/T_RIP));tgt=v(mx,my,mz);fov=Hero.mix(50,44,Hero.smooth(T_THRUST,T_HIT+.05f,t));}
        else if(t<T_OUT){shot=1;   // low angle looking up at him as the saw rips upward
            float k=(t-T_RIP)/(T_OUT-T_RIP),dist=Hero.mix(1.55f,1.25f,k);
            pos=v(mx+sx*dist+ux*.25f,my+sy*dist+uy*.25f,mz-.6f);tgt=v(mx+ux*.15f,my+uy*.15f,mz+.3f+.15f*k);fov=44;}
        else{shot=2;   // wide, from behind her shoulder, as he goes down
            float k=t-T_OUT;
            pos=v(px-ux*(1.2f+.25f*k)+sx*1.35f,py-uy*(1.2f+.25f*k)+sy*1.35f,pz+1.6f+.1f*k);
            tgt=v(dx,dy,dz+1f-.6f*Hero.smooth(T_DIE,T_DIE+.8f,t));fov=56;}
        // keep the camera out of the walls: pull in toward the subject
        float ex=pos[0]-tgt[0],ey=pos[1]-tgt[1],ez=pos[2]-tgt[2],el=(float)Math.sqrt(ex*ex+ey*ey+ez*ez);
        if(el>1e-3f){float[] h=L.ray(tgt[0],tgt[1],tgt[2],ex/el,ey/el,ez/el,el+.2f);
            if(h!=null){float k=Math.max(.45f,h[6]-.2f);pos=v(tgt[0]+ex/el*k,tgt[1]+ey/el*k,tgt[2]+ez/el*k);}}
        float s=shake();
        pos[0]+=s*(float)Math.sin(t*71);pos[1]+=s*(float)Math.sin(t*53+1);pos[2]+=s*(float)Math.sin(t*67+2);
        System.arraycopy(pos,0,camPos,0,3);System.arraycopy(tgt,0,camTarget,0,3);camFov=fov;
    }
}
