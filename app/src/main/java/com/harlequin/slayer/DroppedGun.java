package com.harlequin.slayer;

import static com.harlequin.slayer.Model.v;

/**
 * The shotgun after she drops it: a small rigid body that tumbles, hits the floor (contact points at the muzzle,
 * the butt and the centre of mass), bounces, slides and settles lying on its side. She picks it up again by walking
 * over it. Pure Java.
 */
final class DroppedGun {
    final float[] c,vel,w;float[] R;boolean resting;private float restT;
    static final float[] COM=v(-.05f,0,-.03f);
    private static final float[][] CONTACTS={v(.8f,0,0),v(-.45f,0,-.09f),v(-.45f,0,-.02f),COM,v(.3f,0,-.03f)};

    DroppedGun(float[] G,float[] velocity,java.util.Random rng){
        R=G.clone();R[12]=R[13]=R[14]=0;c=M.pt(G,COM);vel=velocity.clone();
        w=v((rng.nextFloat()-.5f)*8,(rng.nextFloat()-.5f)*8,(rng.nextFloat()-.5f)*6);
    }
    /** World matrix of the gun (action + stock; the barrels are closed). */
    float[] matrix(){return M.mul(M.tr(c[0],c[1],c[2]),M.mul(R,M.tr(-COM[0],-COM[1],-COM[2])));}

    void update(float dt,Level L){
        if(resting)return;
        int sub=2;float h=dt/sub;
        for(int s=0;s<sub;s++){
            vel[2]-=14*h;
            float ox=c[0],oy=c[1];
            c[0]+=vel[0]*h;c[1]+=vel[1]*h;c[2]+=vel[2]*h;
            if(L.atF(c[0],c[1]).solid){c[0]=ox;c[1]=oy;vel[0]*=-.4f;vel[1]*=-.4f;}
            float ang=Model.len(w)*h;if(ang>1e-6f){float[] ax=Model.mul(w,1/Model.len(w));R=M.mul(axisAngle(ax,ang),R);}
            // floor contacts
            float[] Mx=matrix();float pen=0;
            for(float[] cp:CONTACTS){float[] p=M.pt(Mx,cp);float fl=L.floorUnder(p[0],p[1],.01f);pen=Math.max(pen,fl+.018f-p[2]);}
            if(pen>0){c[2]+=pen;if(vel[2]<0)vel[2]=-vel[2]*.28f;vel[0]*=.8f;vel[1]*=.8f;w[0]*=.75f;w[1]*=.75f;w[2]*=.85f;
                float sp=Model.len(vel),sw=Model.len(w);
                if(sp<.5f&&sw<2f){   // settle: roll over onto its side
                    float[] x=v(R[0],R[1],0);if(Model.len(x)<1e-3f)x=v(1,0,0);x=Model.norm(x);float[] y=v(0,0,1);float[] z=Model.cross(x,y);
                    float[] flat=M.basis(v(0,0,0),x,y,z);R=Hero.mlerp(R,flat,Math.min(1,h*6));R[12]=R[13]=R[14]=0;
                    restT+=h;if(restT>.5f){resting=true;R=flat;c[2]=L.floorUnder(c[0],c[1],.05f)+.025f;}}}
        }
    }
    private static float[] axisAngle(float[] k,float a){
        float co=(float)Math.cos(a),si=(float)Math.sin(a),t=1-co;float x=k[0],y=k[1],z=k[2];
        return new float[]{t*x*x+co,t*x*y+si*z,t*x*z-si*y,0, t*x*y-si*z,t*y*y+co,t*y*z+si*x,0, t*x*z+si*y,t*y*z-si*x,t*z*z+co,0, 0,0,0,1};
    }
}
