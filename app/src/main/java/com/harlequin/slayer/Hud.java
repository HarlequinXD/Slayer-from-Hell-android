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
        /** The same controls from a controller or keyboard and mouse (set by Controls), merged with the touch ones. */
        volatile float devMoveX,devMoveY;volatile boolean devFire,devSaw,devJump;
        float mx(){return Math.max(-1,Math.min(1,moveX+devMoveX));}
        float my(){return Math.max(-1,Math.min(1,moveY+devMoveY));}
        boolean firing(){return fire||devFire;}
        boolean sawing(){return saw||devSaw;}
        boolean jumping(){return jumpHeld||devJump;}
        private float lookX,lookY;private boolean reload,jump;
        synchronized void look(float dx,float dy){lookX+=dx;lookY+=dy;}
        synchronized float[] takeLook(){float[] r={lookX,lookY};lookX=lookY=0;return r;}
        synchronized void pressReload(){reload=true;}
        synchronized boolean takeReload(){boolean r=reload;reload=false;return r;}
        synchronized void pressJump(){jump=true;}
        synchronized boolean takeJump(){boolean r=jump;jump=false;return r;}
    }
    final Input in=new Input();
    /** Called when the pause button is tapped. */
    Runnable onPause;
    static boolean showFps=true;
    /** Show the on-screen sticks and buttons (off while a controller or keyboard is in use). */
    static volatile boolean touchControls=true;
    private float pauseX(){return getWidth()*.5f;}private float pauseY(){return dp*30;}
    private final Game game;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final float dp;
    private int movePtr=-1,lookPtr=-1,firePtr=-1,sawPtr=-1;private float mx0,my0,mx,my,lx,ly;
    private final RectF r=new RectF();

    Hud(Context c,Game g){super(c);game=g;dp=c.getResources().getDisplayMetrics().density;p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));}
    private float fireX(){return getWidth()-dp*104;}private float fireY(){return getHeight()-dp*112;}private float fireR(){return dp*56;}
    private float relX(){return getWidth()-dp*214;}private float relY(){return getHeight()-dp*58;}private float smallR(){return dp*34;}
    private float jumpX(){return getWidth()-dp*226;}private float jumpY(){return getHeight()-dp*156;}
    private float sawX(){return getWidth()-dp*92;}private float sawY(){return getHeight()-dp*238;}private float sawR(){return dp*42;}

    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked(),idx=e.getActionIndex(),id=e.getPointerId(idx);
        switch(a){
            case MotionEvent.ACTION_DOWN:case MotionEvent.ACTION_POINTER_DOWN:{float x=e.getX(idx),y=e.getY(idx);
                if(near(x,y,pauseX(),pauseY(),dp*30)){if(onPause!=null)onPause.run();break;}
                if(near(x,y,fireX(),fireY(),fireR()*1.1f)){firePtr=id;in.fire=true;lx=x;ly=y;}
                else if(near(x,y,sawX(),sawY(),sawR()*1.15f)){sawPtr=id;in.saw=true;lx=x;ly=y;}
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
        // a glory kill: cinematic bars, no controls
        if(game.letterbox>0){float bh=h*.11f*game.letterbox;p.setColor(0xff000000);c.drawRect(0,0,w,bh,p);c.drawRect(0,h-bh,w,h,p);
            if(game.letterbox>.99f){postInvalidateOnAnimation();return;}}
        // crosshair (opens up with recoil)
        float g=dp*(7+game.spread*26),L=dp*7;p.setStrokeWidth(dp*2);p.setColor(game.assistLock?0xf0ff5a3a:0xd0ffffff);
        c.drawLine(w/2-g-L,h/2,w/2-g,h/2,p);c.drawLine(w/2+g,h/2,w/2+g+L,h/2,p);c.drawLine(w/2,h/2-g-L,w/2,h/2-g,p);c.drawLine(w/2,h/2+g,w/2,h/2+g+L,p);
        p.setColor(0xffffffff);c.drawCircle(w/2,h/2,dp*1.4f,p);
        if(touchControls){
            // move stick
            if(movePtr>=0){p.setStyle(Paint.Style.STROKE);p.setColor(0x60ffffff);c.drawCircle(mx0,my0,dp*60,p);p.setStyle(Paint.Style.FILL);p.setColor(0x80ffffff);
                float dx=mx-mx0,dy=my-my0,l=(float)Math.hypot(dx,dy),R=dp*60;if(l>R){dx*=R/l;dy*=R/l;}c.drawCircle(mx0+dx,my0+dy,dp*24,p);}
            else{p.setColor(0x26ffffff);c.drawCircle(dp*120,h-dp*110,dp*60,p);text(c,"MOVE",dp*120,h-dp*106,11,0x70ffffff,Paint.Align.CENTER);}
            // buttons
            button(c,fireX(),fireY(),fireR(),"FIRE",in.fire,0xffd2401a);
            if(game.gloryReady){float pulse=.5f+.5f*(float)Math.sin(System.nanoTime()/1e9*12);   // a staggered demon in reach: GLORY KILL
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp*(3+3*pulse));p.setColor(((int)(120+120*pulse)<<24)|0xff7a1a);c.drawCircle(sawX(),sawY(),sawR()+dp*(6+5*pulse),p);p.setStyle(Paint.Style.FILL);
                button(c,sawX(),sawY(),sawR(),"GLORY",true,0xffff6a10);text(c,"GLORY KILL",sawX(),sawY()-sawR()-dp*14,13,0xffffb070,Paint.Align.CENTER);}
            else button(c,sawX(),sawY(),sawR(),"SAW",in.saw,0xffd39a12);
            button(c,jumpX(),jumpY(),smallR(),"JUMP",in.jumpHeld,0xff7a7a80);
        }else{   // controller / keyboard: no touch controls, just the glory kill prompt with the right button
            boolean pad=Controls.padHint;
            if(game.gloryReady){float pulse=.5f+.5f*(float)Math.sin(System.nanoTime()/1e9*12);float cx=w/2,cy=h*.7f;
                String key=pad?"LT":"RMB";float kw=p.measureText(key)+dp*20;
                p.setTextSize(dp*15);kw=p.measureText(key)+dp*22;float tw=p.measureText("GLORY KILL");
                float l=cx-(kw+dp*10+tw)/2;r.set(l,cy-dp*16,l+kw,cy+dp*16);
                p.setStyle(Paint.Style.FILL);p.setColor(((int)(170+80*pulse)<<24)|0xff6a10);c.drawRoundRect(r,dp*8,dp*8,p);
                text(c,key,r.centerX(),cy+dp*5,15,0xffffffff,Paint.Align.CENTER);
                text(c,"GLORY KILL",r.right+dp*10,cy+dp*5,15,0xffffb070,Paint.Align.LEFT);}
        }
        // ammo: the cannon's energy cells, as a number and a charge bar
        float ax=w-dp*24,ay=dp*38;boolean empty=game.ammo==0;
        text(c,game.ammo+" CELLS",ax,ay,24,empty?0xffff5030:0xff8cff9c,Paint.Align.RIGHT);
        p.setColor(0x40ffffff);c.drawRect(ax-dp*160,ay+dp*10,ax,ay+dp*16,p);p.setColor(empty?0xffff5030:0xff5aff7a);c.drawRect(ax-dp*160,ay+dp*10,ax-dp*160+dp*160*Math.min(1,game.ammo/(float)Math.max(1,game.reserve)),ay+dp*16,p);
        // health
        int hp=Math.max(0,Math.round(game.health));
        text(c,"+ "+hp,dp*24,dp*40,26,hp<30?0xffff4030:0xffffffff,Paint.Align.LEFT);
        p.setColor(0x40ffffff);c.drawRect(dp*24,dp*50,dp*184,dp*56,p);p.setColor(hp<30?0xffff4030:0xffe8d0b0);c.drawRect(dp*24,dp*50,dp*24+dp*1.6f*hp,dp*56,p);
        // pause button
        p.setStyle(Paint.Style.FILL);p.setColor(0x40000000);c.drawCircle(pauseX(),pauseY(),dp*20,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp*1.5f);p.setColor(0x90ffffff);c.drawCircle(pauseX(),pauseY(),dp*20,p);
        p.setStyle(Paint.Style.FILL);p.setColor(0xe0ffffff);c.drawRect(pauseX()-dp*6,pauseY()-dp*7,pauseX()-dp*2,pauseY()+dp*7,p);c.drawRect(pauseX()+dp*2,pauseY()-dp*7,pauseX()+dp*6,pauseY()+dp*7,p);
        if(showFps)text(c,game.fps+" fps",w/2,dp*66,11,0x90ffffff,Paint.Align.CENTER);
        if(game.bannerT>0&&game.banner!=null)text(c,game.banner,w/2,h*.36f,game.banner.equals("YOU DIED")?34:20,((int)(Math.min(1,game.bannerT)*255)<<24)|(game.banner.equals("YOU DIED")?0xff3020:0xffe8c0),Paint.Align.CENTER);
        // demons left in the keep
        text(c,"DEMONS "+game.demonsLeft+" / "+game.demonsTotal,dp*24,dp*66,15,game.demonsLeft==0?0xff80ff90:0xffff8a50,Paint.Align.LEFT);
        if(game.demonsLeft==0&&game.demonsTotal>0)text(c,"ALL DEMONS SLAIN · FIND THE EXIT",w/2,h*.3f,20,0xffffd080,Paint.Align.CENTER);
        if(game.hint>0){int a=((int)(Math.min(1,game.hint)*230)<<24);text(c,game.map.name.toUpperCase(),w/2,h*.24f,28,a|0xff6a30,Paint.Align.CENTER);text(c,Skill.NAMES[Skill.current]+" · Slay the demons. Reach the exit. Mind the lava.",w/2,h*.24f+dp*26,14,a|0xffe0c0,Paint.Align.CENTER);}
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
