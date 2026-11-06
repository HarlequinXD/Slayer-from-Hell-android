package com.harlequin.slayer;

/**
 * Quake-style first-person controller: ground friction and acceleration, low air control that still allows
 * strafe-jumping, gravity and jumping, stepping up stairs and ledges, snapping down stairs, ceiling bumps,
 * head bob, landing dip and recoil kick. Lava hurts; dying respawns you. Pure Java.
 */
final class Player {
    static final float RADIUS=.3f,HEIGHT=1.75f,EYE=1.62f;
    static final float MAX_SPEED=7f,ACCEL=12f,AIR_ACCEL=10f,AIR_CAP=.9f,FRICTION=6f,STOP_SPEED=1.5f,GRAVITY=18f,JUMP=6.3f;
    float x,y,z,vx,vy,vz,yaw,pitch;boolean onGround;
    float health=100;float hurtFlash;
    float bobPhase,bobAmt,landDip,landVel,stepSmooth,kick,kickVel,roll;
    int stepCount;boolean justLanded,justJumped,stepped;float landSpeed;
    private int lastStep;

    void spawn(Level L){x=L.spawnX;y=L.spawnY;z=L.floorUnder(x,y,RADIUS);vx=vy=vz=0;yaw=L.spawnAngle;pitch=0;health=100;onGround=true;}

    void update(float dt,Level L,float moveX,float moveY,float lookDX,float lookDY,boolean jump,float sens){
        justLanded=justJumped=stepped=false;
        yaw-=lookDX*sens;pitch-=lookDY*sens;pitch=Math.max(-1.53f,Math.min(1.53f,pitch));
        float cy=(float)Math.cos(yaw),sy=(float)Math.sin(yaw);
        // wish direction: stick up = forward, right = strafe right (y is north, +yaw turns left)
        float wx=cy*moveY+sy*moveX,wy=sy*moveY-cy*moveX;float wl=(float)Math.hypot(wx,wy);
        float wishSpeed=Math.min(1,wl)*MAX_SPEED;if(wl>1e-4f){wx/=wl;wy/=wl;}
        if(onGround){
            float sp=(float)Math.hypot(vx,vy);
            if(sp>0){float drop=Math.max(sp,STOP_SPEED)*FRICTION*dt;float ns=Math.max(0,sp-drop)/sp;vx*=ns;vy*=ns;}
            accelerate(wx,wy,wishSpeed,ACCEL,dt);
            if(jump){vz=JUMP;onGround=false;justJumped=true;}
        }else accelerate(wx,wy,Math.min(wishSpeed,AIR_CAP),AIR_ACCEL,dt);
        // horizontal movement, one axis at a time so we slide along walls
        float before=z;
        float nx=x+vx*dt;if(!L.blocked(nx,y,z,RADIUS,HEIGHT))x=nx;else vx=0;
        float ny=y+vy*dt;if(!L.blocked(x,ny,z,RADIUS,HEIGHT))y=ny;else vy=0;
        float fl=L.floorUnder(x,y,RADIUS);
        if(onGround){
            if(fl>z){stepSmooth-=fl-z;z=fl;stepped=true;}                       // step up
            else if(z-fl<=Level.STEP+.05f&&vz<=0){if(z-fl>.05f){stepSmooth+=z-fl;stepped=true;}z=fl;}   // stay on the stairs going down
            else onGround=false;
        }
        if(!onGround){
            vz-=GRAVITY*dt;z+=vz*dt;
            float ce=L.ceilOver(x,y,RADIUS);if(z+HEIGHT>ce){z=ce-HEIGHT;if(vz>0)vz=0;}
            if(z<=fl){landSpeed=-vz;if(vz<-4f){landVel=-Math.min(1.6f,-vz*.09f);justLanded=true;}z=fl;vz=0;onGround=true;}
        }
        // camera motion
        float sp=(float)Math.hypot(vx,vy);
        bobAmt+=((onGround?Math.min(1,sp/MAX_SPEED):0)-bobAmt)*Math.min(1,dt*8);
        bobPhase+=dt*sp*1.45f;
        int step=(int)(bobPhase/Math.PI);if(step!=lastStep&&onGround&&sp>1){lastStep=step;stepCount++;}
        landDip+=landVel*dt;landVel+=(-landDip*120-landVel*14)*dt;
        stepSmooth*=Math.max(0,1-dt*12);
        kick+=kickVel*dt;kickVel+=(-kick*160-kickVel*18)*dt;
        float side=-sy*vx+cy*vy;roll+=((-side/MAX_SPEED)*.018f-roll)*Math.min(1,dt*8);
        // lava
        Level.Cell c=L.atF(x,y);
        if(!c.solid&&c.floorMat==Tex.LAVA&&z<=c.floor+.05f){health-=40*dt;hurtFlash=1;}
        hurtFlash=Math.max(0,hurtFlash-dt*2);
        if(health<=0)spawn(L);
    }
    private void accelerate(float wx,float wy,float wishSpeed,float accel,float dt){
        float cur=vx*wx+vy*wy;float add=wishSpeed-cur;if(add<=0)return;
        float as=Math.min(add,accel*dt*wishSpeed);vx+=as*wx;vy+=as*wy;
    }
    float eyeZ(){return z+EYE+(float)Math.sin(bobPhase*2)*.03f*bobAmt+landDip+stepSmooth;}
    float viewPitch(){return pitch+kick;}
    float[] forward(){float cp=(float)Math.cos(viewPitch());return new float[]{(float)Math.cos(yaw)*cp,(float)Math.sin(yaw)*cp,(float)Math.sin(viewPitch())};}
}
