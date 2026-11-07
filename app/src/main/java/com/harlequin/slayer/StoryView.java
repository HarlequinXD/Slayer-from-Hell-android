package com.harlequin.slayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

/**
 * A chapter of the story between two maps: the chapter heading and title burn in, then Slayer's words type
 * themselves out over a dark, slowly breathing fire, line by line, scrolling as they go. Tap (or A / click / Enter)
 * to show the rest at once, then again to continue; the page can be scrolled back to re-read. Skip is always there.
 */
final class StoryView extends View implements Pointer.Aware {
    private final Story.Chapter ch;private final Runnable done;private final Ui ui;private final Paint p,text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<String> lines=new ArrayList<>();private final ArrayList<Integer> starts=new ArrayList<>();
    private float wrapW=-1,typed,scroll,maxScroll,t;private long last;private boolean finished,autoScroll=true,left;
    private float downY,lastY;private boolean dragged;private int pressed=-1;
    private final float[] embers=new float[60*4];
    private static final float CPS=42;   // characters per second
    private static final int H_SKIP=1,H_GO=2;

    StoryView(Context c,Story.Chapter ch,Runnable done){
        super(c);this.ch=ch;this.done=done;ui=new Ui(c);p=ui.p;
        text.setTypeface(Typeface.create("serif",Typeface.NORMAL));text.setColor(0xffe8d6c4);
        java.util.Random r=new java.util.Random(7);for(int i=0;i<60;i++){embers[i*4]=r.nextFloat();embers[i*4+1]=r.nextFloat();embers[i*4+2]=.3f+r.nextFloat()*.7f;embers[i*4+3]=r.nextFloat()*6.28f;}
        setFocusable(true);
    }
    private float dp(float v){return ui.dp(v);}
    /** Finish the chapter (Skip, or Continue once it has been read). */
    void finish(){if(left)return;left=true;if(done!=null)done.run();}
    /** A press anywhere: the rest of the text at once, or on to the next map. */
    void advance(){if(!finished){typed=ch.text.length();finished=true;}else finish();invalidate();}

    private void wrap(float width){
        if(width==wrapW)return;wrapW=width;lines.clear();starts.clear();text.setTextSize(dp(16));
        String s=ch.text;int i=0;
        for(String para:s.split("\n",-1)){
            if(para.isEmpty()){lines.add("");starts.add(i);i+=1;continue;}
            String[] words=para.split(" ");StringBuilder line=new StringBuilder();int lineStart=i;
            for(String w:words){String test=line.length()==0?w:line+" "+w;
                if(text.measureText(test)>width&&line.length()>0){lines.add(line.toString());starts.add(lineStart);lineStart+=line.length()+1;line=new StringBuilder(w);}
                else line=new StringBuilder(test);}
            lines.add(line.toString());starts.add(lineStart);i=lineStart+line.length()+1;
        }
    }

    @Override protected void onDraw(Canvas c){
        long now=System.nanoTime();float dt=last==0?0:Math.min(.05f,(now-last)/1e9f);last=now;t+=dt;
        float w=getWidth(),h=getHeight();
        // the fire: a dark sky, a slow breathing glow below, embers drifting up
        p.setShader(new LinearGradient(0,0,0,h,new int[]{0xff050203,0xff0e0405,0xff2a0704},new float[]{0,.6f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
        float br=.75f+.25f*(float)Math.sin(t*.6f);
        p.setShader(new RadialGradient(w*.5f,h*1.15f,h*.95f,new int[]{((int)(150*br)<<24)|0xc02a06,0x00000000},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
        for(int i=0;i<60;i++){float ex=embers[i*4],ey=embers[i*4+1],sp=embers[i*4+2];float y=((ey-t*.025f*sp)%1+1)%1;float x=ex+.015f*(float)Math.sin(t*.7f+embers[i*4+3]);
            int a=(int)(200*Math.min(1,y*3)*(.4f+.6f*sp));p.setColor((a<<24)|0xff7a2a);c.drawCircle(x*w,y*h,dp(1+1.4f*sp),p);}
        // the column of text
        float colW=Math.min(dp(640),w-dp(96)),l=(w-colW)/2,top=dp(28);
        ui.clearHits();
        ui.text(c,ch.title,l,top+dp(12),12,0xffd06a3a,2,-1);
        float tk=Math.min(1,t/1.2f);
        ui.glowText(c,ch.heading,l,top+dp(48),28,(((int)(255*tk))<<24)|0xff8a50,-1);
        float bodyTop=top+dp(70),bodyBottom=h-dp(64);
        ui.round(c,l-dp(2),bodyTop-dp(4),l+dp(56),bodyTop-dp(2),dp(1),0x90ff5a1a);
        wrap(colW);
        if(t>1.0f&&!finished){typed+=dt*CPS;if(typed>=ch.text.length()){typed=ch.text.length();finished=true;}}
        float lh=dp(16)*1.62f;int n=(int)typed;float lastY=0;
        int save=c.save();c.clipRect(0,bodyTop,w,bodyBottom);
        for(int k=0;k<lines.size();k++){int st=starts.get(k);if(st>n)break;String s=lines.get(k);int vis=Math.min(s.length(),n-st);if(vis<0)break;
            float y=bodyTop+dp(22)+k*lh-scroll;lastY=k*lh+dp(30);if(y-dp(13)<bodyTop||y>bodyBottom+lh)continue;   // whole lines only at the top edge
            String part=s.substring(0,vis);c.drawText(part,l,y,text);
            if(vis<s.length()&&!finished){float cw=text.measureText(part);p.setColor((((int)(200*(.5f+.5f*Math.sin(t*10))))<<24)|0xff9a5a);c.drawRect(l+cw+dp(2),y-dp(13),l+cw+dp(4),y+dp(3),p);}}
        c.restoreToCount(save);
        // keep the newest line in view while it types; allow scrolling back once read
        float total=lines.size()*lh+dp(30);maxScroll=Math.max(0,total-(bodyBottom-bodyTop));
        if(autoScroll){float want=Math.max(0,lastY-(bodyBottom-bodyTop)+dp(10));scroll+=(Math.min(maxScroll,want)-scroll)*Math.min(1,dt*6);}
        scroll=Math.max(0,Math.min(maxScroll,scroll));
        if(scroll>1){p.setShader(new LinearGradient(0,bodyTop,0,bodyTop+dp(28),0xff080304,0x00080304,Shader.TileMode.CLAMP));c.drawRect(0,bodyTop,w,bodyTop+dp(28),p);p.setShader(null);}
        // controls
        r.set(w-dp(104),dp(18),w-dp(18),dp(52));ui.button(c,r,-1,"Skip",Ui.GHOST);ui.hit(r,H_SKIP);
        if(finished){float pulse=.5f+.5f*(float)Math.sin(t*3);r.set(w-dp(190),h-dp(56),w-dp(18),h-dp(14));ui.button(c,r,Icons.PLAY,"Continue",Ui.PRIMARY);ui.hit(r,H_GO);
            ui.text(c,Hud.touchControls?"Tap to continue":Controls.padHint?"A to continue":"Click or Enter to continue",l,h-dp(30),11.5f,(((int)(120+100*pulse))<<24)|0xe0c0a0,0,-1);}
        else ui.text(c,Hud.touchControls?"Tap to read on":"A / click to read on",l,h-dp(30),11.5f,0x80e0c0a0,0,-1);
        if(!left)postInvalidateOnAnimation();
    }
    private final android.graphics.RectF r=new android.graphics.RectF();

    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked();float y=e.getY();
        if(a==MotionEvent.ACTION_DOWN){downY=lastY=y;dragged=false;pressed=ui.hitAt(e.getX(),y);return true;}
        if(a==MotionEvent.ACTION_MOVE){if(Math.abs(y-downY)>dp(10)&&finished){dragged=true;autoScroll=false;scroll-=y-lastY;invalidate();}lastY=y;return true;}
        if(a==MotionEvent.ACTION_UP){if(!dragged){int id=ui.hitAt(e.getX(),y);if(id==H_SKIP&&pressed==H_SKIP)finish();else if(id==H_GO&&pressed==H_GO)finish();else if(pressed<0)advance();}return true;}
        return true;
    }
    @Override public Ui pointerUi(){return ui;}
    @Override public void pointerPan(float dx,float dy){autoScroll=false;scroll+=dy;invalidate();}
    @Override public void pointerZoom(float steps,float x,float y){autoScroll=false;scroll-=steps*dp(40);invalidate();}
}
