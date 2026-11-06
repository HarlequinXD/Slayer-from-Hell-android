package com.harlequin.slayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * The touch controls and HUD drawn over the 3D view: a floating move stick on the left, drag anywhere on the right
 * to look (also while holding Fire), Fire / Saw (hold) / Reload / Jump buttons, crosshair, ammo, health, frame rate.
 */
final class Hud extends View {
    /** Shared with the GL thread. */
    static final class Input {
        volatile float moveX,moveY;volatile boolean fire,jumpHeld,saw;
        private float lookX,lookY;private boolean reload,jump,ragdoll,push;volatile boolean debug;
        synchronized void pressPush(){push=true;}
        synchronized boolean takePush(){boolean r=push;push=false;return r;}
        synchronized void pressRagdoll(){ragdoll=true;}
        synchronized boolean takeRagdoll(){boolean r=ragdoll;ragdoll=false;return r;}
        synchronized void look(float dx,float dy){lookX+=dx;lookY+=dy;}
        synchronized float[] takeLook(){float[] r={lookX,lookY};lookX=lookY=0;return r;}
        synchronized void pressReload(){reload=true;}
        synchronized boolean takeReload(){boolean r=reload;reload=false;return r;}
        synchronized void pressJump(){jump=true;}
        synchronized boolean takeJump(){boolean r=jump;jump=false;return r;}
    }
    final Input in=new Input();
    private final Game game;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final float dp;
    private int movePtr=-1,lookPtr=-1,firePtr=-1,sawPtr=-1;private float mx0,my0,mx,my,lx,ly;
    private final RectF r=new RectF();

    Hud(Context c,Game g){super(c);game=g;dp=c.getResources().getDisplayMetrics().density;p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));}
    private float fireX(){return getWidth()-dp*104;}private float fireY(){return getHeight()-dp*112;}private float fireR(){return dp*56;}
    private float relX(){return getWidth()-dp*214;}private float relY(){return getHeight()-dp*58;}private float smallR(){return dp*34;}
    private float jumpX(){return getWidth()-dp*226;}private float jumpY(){return getHeight()-dp*156;}
    private float ragX(){return getWidth()-dp*230;}private float ragY(){return dp*96;}
    private float pushX(){return getWidth()-dp*310;}private float pushY(){return dp*96;}private float dbgX(){return getWidth()-dp*390;}private float dbgY(){return dp*96;}
    private float sawX(){return getWidth()-dp*92;}private float sawY(){return getHeight()-dp*238;}private float sawR(){return dp*42;}

    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked(),idx=e.getActionIndex(),id=e.getPointerId(idx);
        switch(a){
            case MotionEvent.ACTION_DOWN:case MotionEvent.ACTION_POINTER_DOWN:{float x=e.getX(idx),y=e.getY(idx);
                if(near(x,y,fireX(),fireY(),fireR()*1.1f)){firePtr=id;in.fire=true;lx=x;ly=y;}
                else if(near(x,y,sawX(),sawY(),sawR()*1.15f)){sawPtr=id;in.saw=true;lx=x;ly=y;}
                else if(near(x,y,relX(),relY(),smallR()*1.25f))in.pressReload();
                else if(near(x,y,ragX(),ragY(),smallR()*1.2f))in.pressRagdoll();
                else if(near(x,y,pushX(),pushY(),smallR()*1.2f))in.pressPush();
                else if(near(x,y,dbgX(),dbgY(),smallR()*1.2f))in.debug=!in.debug;
                else if(near(x,y,jumpX(),jumpY(),smallR()*1.25f)){in.pressJump();in.jumpHeld=true;}
                else if(x<getWidth()*.42f&&movePtr<0){movePtr=id;mx0=mx=x;my0=my=y;}
                else if(lookPtr<0){lookPtr=id;lx=x;ly=y;}
                break;}
            case MotionEvent.ACTION_MOVE:
                for(int i=0;i<e.getPointerCount();i++){int pid=e.getPointerId(i);float x=e.getX(i),y=e.getY(i);
                    if(pid==movePtr){mx=x;my=y;float R=dp*60,dx=(mx-mx0)/R,dy=(my-my0)/R;float l=(float)Math.hypot(dx,dy);
                        if(l>1.4f){mx0+=(mx-mx0)*(1-1.4f/l);my0+=(my-my0)*(1-1.4f/l);}   // the stick follows a far-dragging thumb
                        float sx=Math.max(-1,Math.min(1,dx)),sy=Math.max(-1,Math.min(1,-dy));in.moveX=sx;in.moveY=sy;}
                    else if(pid==lookPtr||pid==firePtr||pid==sawPtr){in.look((x-lx)/dp,(y-ly)/dp);lx=x;ly=y;}}
                break;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_POINTER_UP:case MotionEvent.ACTION_CANCEL:
                if(a==MotionEvent.ACTION_CANCEL){movePtr=lookPtr=firePtr=sawPtr=-1;in.fire=false;in.saw=false;in.moveX=in.moveY=0;in.jumpHeld=false;break;}
                if(id==movePtr){movePtr=-1;in.moveX=in.moveY=0;}
                if(id==lookPtr)lookPtr=-1;
                if(id==firePtr){firePtr=-1;in.fire=false;}
                if(id==sawPtr){sawPtr=-1;in.saw=false;}
                in.jumpHeld=false;
                break;
        }
        return true;
    }
    private static boolean near(float x,float y,float cx,float cy,float r){return Math.hypot(x-cx,y-cy)<=r;}

    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();
        if(game.loading!=null){
            p.setColor(0xff120604);c.drawRect(0,0,w,h,p);
            text(c,"SLAYER FROM HELL",w/2,h*.42f,34,0xffe04a1a,Paint.Align.CENTER);
            text(c,game.loading,w/2,h*.42f+dp*36,14,0xffd0b8a8,Paint.Align.CENTER);
            float bw=w*.4f;p.setColor(0x40ffffff);c.drawRect(w/2-bw/2,h*.6f,w/2+bw/2,h*.6f+dp*6,p);p.setColor(0xffe04a1a);c.drawRect(w/2-bw/2,h*.6f,w/2-bw/2+bw*game.progress,h*.6f+dp*6,p);
            postInvalidateOnAnimation();return;}
        // lava / damage vignette
        if(game.hurt>0){p.setShader(new android.graphics.RadialGradient(w/2,h/2,Math.max(w,h)*.7f,0x00ff2000,((int)(game.hurt*150)<<24)|0xff2000,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);}
        // crosshair (opens up with recoil)
        float g=dp*(7+game.spread*26),L=dp*7;p.setStrokeWidth(dp*2);p.setColor(0xd0ffffff);
        c.drawLine(w/2-g-L,h/2,w/2-g,h/2,p);c.drawLine(w/2+g,h/2,w/2+g+L,h/2,p);c.drawLine(w/2,h/2-g-L,w/2,h/2-g,p);c.drawLine(w/2,h/2+g,w/2,h/2+g+L,p);
        p.setColor(0xffffffff);c.drawCircle(w/2,h/2,dp*1.4f,p);
        // move stick
        if(movePtr>=0){p.setStyle(Paint.Style.STROKE);p.setColor(0x60ffffff);c.drawCircle(mx0,my0,dp*60,p);p.setStyle(Paint.Style.FILL);p.setColor(0x80ffffff);
            float dx=mx-mx0,dy=my-my0,l=(float)Math.hypot(dx,dy),R=dp*60;if(l>R){dx*=R/l;dy*=R/l;}c.drawCircle(mx0+dx,my0+dy,dp*24,p);}
        else{p.setColor(0x26ffffff);c.drawCircle(dp*120,h-dp*110,dp*60,p);text(c,"MOVE",dp*120,h-dp*106,11,0x70ffffff,Paint.Align.CENTER);}
        // buttons
        button(c,fireX(),fireY(),fireR(),"FIRE",in.fire,0xffd2401a);
        button(c,sawX(),sawY(),sawR(),"SAW",in.saw,0xffd39a12);
        button(c,relX(),relY(),smallR(),"RELOAD",game.reloading,0xff7a7a80);
        button(c,jumpX(),jumpY(),smallR(),"JUMP",in.jumpHeld,0xff7a7a80);
        button(c,ragX(),ragY(),smallR(),"RAGDOLL",false,0xff5a7040);
        button(c,pushX(),pushY(),smallR(),"PUSH",false,0xff5a7040);
        button(c,dbgX(),dbgY(),smallR(),"DEBUG",in.debug,0xff40607a);
        if(in.debug){String[] ls=game.debugText.split("\n");for(int i=0;i<ls.length;i++)text(c,ls[i],dp*24,dp*84+i*dp*16,11,0xffd8f0ff,Paint.Align.LEFT);}
        else text(c,game.moveState,dp*24,dp*84,12,0xb0ffffff,Paint.Align.LEFT);
        // ammo: loaded shells as icons, reserve as a number
        float ax=w-dp*24,ay=dp*38;
        text(c,game.hasGun?game.ammo+" / "+game.reserve:"NO GUN  "+game.reserve,ax,ay,26,!game.hasGun||game.ammo==0?0xffff5030:0xffffffff,Paint.Align.RIGHT);
        for(int i=0;i<Gun.CAPACITY;i++){float sx=ax-dp*10-i*dp*11,sy=ay+dp*12;boolean has=i<game.ammo;
            p.setColor(has?0xffc02018:0x40ffffff);r.set(sx-dp*4,sy,sx+dp*4,sy+dp*16);c.drawRoundRect(r,dp*2,dp*2,p);
            p.setColor(has?0xffd8b048:0x30ffffff);r.set(sx-dp*4.5f,sy+dp*12,sx+dp*4.5f,sy+dp*17);c.drawRect(r,p);}
        // health
        int hp=Math.max(0,Math.round(game.health));
        text(c,"+ "+hp,dp*24,dp*40,26,hp<30?0xffff4030:0xffffffff,Paint.Align.LEFT);
        p.setColor(0x40ffffff);c.drawRect(dp*24,dp*50,dp*184,dp*56,p);p.setColor(hp<30?0xffff4030:0xffe8d0b0);c.drawRect(dp*24,dp*50,dp*24+dp*1.6f*hp,dp*56,p);
        text(c,game.fps+" fps",w/2,dp*22,11,0x90ffffff,Paint.Align.CENTER);
        if(game.hint>0)text(c,"Find the lava hall. Shoot things. Don't touch the lava.",w/2,h*.28f,15,((int)(Math.min(1,game.hint)*230)<<24)|0xffe0c0,Paint.Align.CENTER);
        postInvalidateOnAnimation();
    }
    private void button(Canvas c,float x,float y,float rad,String label,boolean down,int col){
        p.setStyle(Paint.Style.FILL);p.setColor((down?0x90:0x48)<<24|(col&0xffffff));c.drawCircle(x,y,rad,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp*2);p.setColor(0x90ffffff);c.drawCircle(x,y,rad,p);p.setStyle(Paint.Style.FILL);
        text(c,label,x,y+dp*5,rad>dp*40?16:11,0xf0ffffff,Paint.Align.CENTER);
    }
    private void text(Canvas c,String s,float x,float y,float size,int col,Paint.Align al){
        p.setTextSize(size*dp);p.setTextAlign(al);p.setColor(0xa0000000);c.drawText(s,x+dp,y+dp,p);p.setColor(col);c.drawText(s,x,y,p);
    }
}
