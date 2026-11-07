package com.harlequin.slayer;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

/**
 * The app-wide cursor: one on-screen pointer for the menus, the level editor and its 3D view, driven by a mouse or
 * an Xbox controller's left stick (A clicks, holding A drags), so the whole app works without touching the screen.
 *
 * It presses buttons by sending the screen under it ordinary touch events, so every screen works with it as-is.
 * With a controller it behaves like a console cursor: a response curve on the stick, speed that builds while held,
 * friction over anything clickable so it is easy to stop on a button, and the d-pad jumping straight to the next
 * button in that direction. What it is over is framed by animated corner brackets.
 *
 * The look: a hellforged talon, an ember gradient over charred iron with a hot rim, a soft fire glow that flares on
 * a target, a squash when pressed and a shock ring on each click.
 */
final class Pointer {
    /** Screens that know about the cursor (all optional). */
    interface Aware {
        /** Its hit targets (for brackets, friction and d-pad jumps). */
        Ui pointerUi();
        /** The cursor moved over it (the editor tracks its map cursor with this). */
        default void pointerHover(float x,float y){}
        /** Right stick / mouse drag with the right or middle button: pan (dx,dy in pixels). */
        default void pointerPan(float dx,float dy){}
        /** Wheel / bumpers: zoom in (+) or out (-) about (x,y). */
        default void pointerZoom(float steps,float x,float y){}
        /** The d-pad: true if the screen used it (the editor steps its map cursor), else the cursor jumps. */
        default boolean pointerDpad(int dx,int dy){return false;}
        /** The sticks drive a camera instead of the cursor (the 3D editor view). */
        default boolean pointerCamera(){return false;}
        /** Ctrl + a letter (z undo, y redo, s save). */
        default void pointerShortcut(char key){}
    }
    /** True while the cursor is delivering a click, so a screen can tell it from a finger. */
    static boolean dispatching;

    float x=Float.NaN,y=Float.NaN;boolean visible;boolean down;
    private float vx,vy,hold,pressK,hoverK,glowT;private long downTime;private View pressed;
    private final RectF bracket=new RectF();private boolean hasBracket;private RectF target;
    private float jumpX,jumpY,jumpT=1;
    private final float[] rings={0,0,1,0,0,1,0,0,1,0,0,1};   // x, y, age for up to four click rings
    final float dp;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path talon=new Path();

    Pointer(float dp){this.dp=dp;}

    void place(float nx,float ny){x=nx;y=ny;jumpT=1;}
    /** Glide to (nx,ny) (a d-pad jump). */
    void jumpTo(float nx,float ny){jumpX=nx;jumpY=ny;jumpT=0;}

    /**
     * Moves with the stick (sx,sy in -1..1, already curved) for dt seconds on a w x h screen. Friction applies over a
     * target; speed builds the longer the stick is held over.
     */
    void stick(float sx,float sy,float dt,float w,float h){
        float l=(float)Math.hypot(sx,sy);
        if(l<1e-3f){hold=0;vx*=.5f;vy*=.5f;return;}
        hold+=dt;jumpT=1;
        float speed=(float)Math.hypot(w,h)*.62f*(1+.7f*Hero.smooth(.35f,1.1f,hold));
        if(target!=null)speed*=hold<.5f?.42f:.7f;   // sticky over buttons
        float tx=sx*speed,ty=sy*speed;vx+=(tx-vx)*Math.min(1,dt*18);vy+=(ty-vy)*Math.min(1,dt*18);
        x=Math.max(0,Math.min(w-1,x+vx*dt));y=Math.max(0,Math.min(h-1,y+vy*dt));
    }

    /** Per frame: glides, hover target and its brackets, animation timers. */
    void update(float dt,Aware screen){
        glowT+=dt;
        if(jumpT<1){jumpT=Math.min(1,jumpT+dt/.12f);float k=Hero.smooth(0,1,jumpT);x+=(jumpX-x)*k;y+=(jumpY-y)*k;}
        target=null;
        if(visible&&screen!=null&&screen.pointerUi()!=null){Ui ui=screen.pointerUi();
            for(int i=ui.hits.size()-1;i>=0;i--){if(!ui.hits.get(i).target)continue;RectF r=ui.hits.get(i).r;if(r.contains(x,y)&&r.width()<dp*700&&r.height()<dp*400){target=r;break;}}}
        hoverK=Hero.approach(hoverK,target!=null?1:0,14,dt);pressK=Hero.approach(pressK,down?1:0,30,dt);
        if(target!=null){float k=hasBracket?Math.min(1,dt*22):1;float g=dp*3;
            bracket.set(Hero.mix(bracket.left,target.left-g,k),Hero.mix(bracket.top,target.top-g,k),Hero.mix(bracket.right,target.right+g,k),Hero.mix(bracket.bottom,target.bottom+g,k));hasBracket=true;}
        else if(hoverK<.02f)hasBracket=false;
        for(int i=0;i<4;i++)if(rings[i*3+2]<1)rings[i*3+2]+=dt/.35f;
        Ui.hoverX=visible?x:Float.NaN;Ui.hoverY=visible?y:Float.NaN;
    }
    boolean overTarget(){return target!=null;}

    // ---------------------------------------------------------------- clicks (as touch events)
    void press(View v){
        if(v==null||Float.isNaN(x))return;down=true;downTime=SystemClock.uptimeMillis();pressed=v;send(v,MotionEvent.ACTION_DOWN);
        for(int i=0;i<4;i++)if(rings[i*3+2]>=1){rings[i*3]=x;rings[i*3+1]=y;rings[i*3+2]=0;break;}
    }
    void drag(){if(down&&pressed!=null)send(pressed,MotionEvent.ACTION_MOVE);}
    void release(){if(!down)return;down=false;if(pressed!=null)send(pressed,MotionEvent.ACTION_UP);pressed=null;}
    /** Lifts a held click without clicking (the screen changed under it). */
    void cancel(){if(down&&pressed!=null)send(pressed,MotionEvent.ACTION_CANCEL);down=false;pressed=null;}
    private void send(View v,int action){
        MotionEvent e=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(InputDevice.SOURCE_MOUSE);
        dispatching=true;try{v.dispatchTouchEvent(e);}finally{dispatching=false;e.recycle();}
    }

    /** The d-pad: the nearest target whose centre lies in that direction (favouring ones straight ahead). */
    boolean dpadJump(int dx,int dy,Ui ui){
        if(ui==null)return false;float best=Float.MAX_VALUE;RectF pick=null;
        for(Ui.Hit h:ui.hits){RectF r=h.r;if(!h.target||r.width()>dp*700||r.height()>dp*400||r.contains(x,y))continue;
            float ex=r.centerX()-x,ey=r.centerY()-y;float along=ex*dx+ey*dy;if(along<dp*4)continue;
            float perp=Math.abs(ex*dy-ey*dx);if(perp>along*2.2f)continue;
            float score=along+perp*2.5f;if(score<best){best=score;pick=r;}}
        if(pick==null)return false;jumpTo(pick.centerX(),pick.centerY());return true;
    }

    // ---------------------------------------------------------------- drawing
    void draw(Canvas c){
        if(!visible||Float.isNaN(x))return;
        // the target's corner brackets
        if(hasBracket&&hoverK>.02f){
            float pulse=.75f+.25f*(float)Math.sin(glowT*6);int a=(int)(220*hoverK*pulse);
            float L=Math.min(dp*12,Math.min(bracket.width(),bracket.height())*.35f),o=dp*2*(1-hoverK);
            p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp*2.2f);p.setStrokeCap(Paint.Cap.SQUARE);
            p.setShadowLayer(dp*6,0,0,(a<<24)|0xff4a0a);p.setColor((a<<24)|0xffb050);
            float l=bracket.left-o,t=bracket.top-o,r=bracket.right+o,b=bracket.bottom+o;
            c.drawLine(l,t,l+L,t,p);c.drawLine(l,t,l,t+L,p);c.drawLine(r,t,r-L,t,p);c.drawLine(r,t,r,t+L,p);
            c.drawLine(l,b,l+L,b,p);c.drawLine(l,b,l,b-L,p);c.drawLine(r,b,r-L,b,p);c.drawLine(r,b,r,b-L,p);
            p.setShadowLayer(0,0,0,0);p.setStrokeCap(Paint.Cap.BUTT);}
        // click shock rings
        p.setStyle(Paint.Style.STROKE);
        for(int i=0;i<4;i++){float t=rings[i*3+2];if(t>=1)continue;p.setStrokeWidth(dp*(3-2*t));p.setColor(((int)(200*(1-t))<<24)|0xff7a2a);c.drawCircle(rings[i*3],rings[i*3+1],dp*(5+22*t),p);}
        // the talon
        float s=dp*(1+.1f*hoverK-.12f*pressK);
        talon.reset();
        talon.moveTo(x,y);talon.lineTo(x+1.2f*s,y+21.5f*s);talon.lineTo(x+6.2f*s,y+16.4f*s);talon.lineTo(x+10.6f*s,y+25.4f*s);
        talon.lineTo(x+14.6f*s,y+23.5f*s);talon.lineTo(x+10.2f*s,y+14.8f*s);talon.lineTo(x+17.4f*s,y+14.2f*s);talon.close();
        float glow=.55f+.45f*hoverK+.12f*(float)Math.sin(glowT*3.2f);
        p.setStyle(Paint.Style.FILL);p.setShadowLayer(dp*(5+5*hoverK),0,dp,(((int)(200*glow))<<24)|0xff3a00);p.setColor(0xff1a0604);c.drawPath(talon,p);p.setShadowLayer(0,0,0,0);
        p.setShader(new LinearGradient(x,y,x+8*s,y+24*s,new int[]{0xfffff0b0,0xffffa030,0xffd23a08,0xff6a1004},new float[]{0,.25f,.65f,1},Shader.TileMode.CLAMP));
        c.save();c.translate(x,y);c.scale(.78f,.78f);c.translate(-x+2.6f*s,-y+4.2f*s);c.drawPath(talon,p);c.restore();p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,dp*1.1f));p.setStrokeJoin(Paint.Join.MITER);p.setColor(0xff2a0805);c.drawPath(talon,p);
        p.setStrokeWidth(Math.max(1,dp*.7f));p.setColor((((int)(150+100*hoverK))<<24)|0xffd080);c.drawLine(x+1.3f*s,y+3.5f*s,x+1.9f*s,y+15f*s,p);   // a hot edge down the blade
        p.setStyle(Paint.Style.FILL);
    }
}
