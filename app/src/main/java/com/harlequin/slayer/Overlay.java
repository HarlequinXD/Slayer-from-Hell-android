package com.harlequin.slayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * The top layer of every screen: the app's own dialogs (choices, confirmations, text entry with an on-screen
 * keyboard), toasts, and the global cursor drawn over everything. Dialogs are drawn in the hellfire style and work the
 * same with a finger, a mouse, a controller (cursor + A, B to cancel) or a keyboard (typing, Enter, Esc), on the phone
 * and on Windows, so nothing in the app needs the system's dialogs.
 */
final class Overlay extends View implements Pointer.Aware {
    interface Pick{void pick(int i);}
    interface Text{void got(String s);}
    private static Overlay current;
    static Overlay get(){return current;}

    final Ui ui;final Pointer pointer;
    private static final int NONE=0,CHOOSE=1,CONFIRM=2,PROMPT=3;
    private int kind;private String title,message,okLabel;private String[] items;private int selected;
    private Pick onPick;private Runnable onOk;private Text onText;private final StringBuilder text=new StringBuilder();private boolean shift;
    private String toast;private long toastUntil;private float scroll;private boolean danger;
    private final RectF r=new RectF();private float downX,downY;private int downHit=-1;private boolean scrolling;
    private static final int H_ITEM=1000,H_CANCEL=1,H_OK=2,H_KEY=2000,H_PANEL=3;
    private static final String[] ROWS={"1234567890","qwertyuiop","asdfghjkl'","zxcvbnm-_."};

    Overlay(Context c){super(c);ui=new Ui(c);pointer=new Pointer(ui.dp);current=this;}

    // ---------------------------------------------------------------- API
    boolean busy(){return kind!=NONE;}
    void choose(String title,String[] items,int selected,Pick cb){this.title=title;this.items=items;this.selected=selected;onPick=cb;kind=CHOOSE;scroll=0;focus();}
    void confirm(String title,String message,String ok,boolean danger,Runnable cb){this.title=title;this.message=message;okLabel=ok;this.danger=danger;onOk=cb;kind=CONFIRM;focus();}
    void prompt(String title,String value,Text cb){this.title=title;text.setLength(0);text.append(value==null?"":value);onText=cb;kind=PROMPT;shift=false;focus();}
    void toast(String msg){toast=msg;toastUntil=System.currentTimeMillis()+2600;invalidate();}
    void dismiss(){kind=NONE;onPick=null;onOk=null;onText=null;invalidate();}
    /** Esc / B: closes the dialog; false when there was none. */
    boolean back(){if(kind==NONE)return false;dismiss();return true;}
    private void focus(){invalidate();post(()->{if(Float.isNaN(pointer.x))return;RectF t=firstTarget();if(t!=null&&pointer.visible)pointer.jumpTo(t.centerX(),t.centerY());});}
    private RectF firstTarget(){for(Ui.Hit h:ui.hits)if(h.id>=H_ITEM&&h.id<H_ITEM+999||h.id==H_OK)return h.r;return null;}
    /** Keyboard: typed characters, Backspace, Enter. */
    void typed(String s){if(kind!=PROMPT||s.isEmpty())return;for(char ch:s.toCharArray())if(ch>=32&&text.length()<40)text.append(ch);invalidate();}
    void backspace(){if(kind==PROMPT&&text.length()>0){text.setLength(text.length()-1);invalidate();}}
    void enter(){if(kind==PROMPT)finishPrompt();else if(kind==CONFIRM){Runnable ok=onOk;dismiss();if(ok!=null)ok.run();}}

    @Override public Ui pointerUi(){return ui;}

    // ---------------------------------------------------------------- drawing
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();ui.clearHits();
        if(kind!=NONE)drawDialog(c,w,h);
        if(toast!=null){long left=toastUntil-System.currentTimeMillis();
            if(left<=0)toast=null;else{float a=Math.min(1,left/400f);float tw=ui.measure(toast,13,1)+ui.dp(36);float l=(w-tw)/2,t=h-ui.dp(92);
                ui.pill(c,l,t,l+tw,t+ui.dp(36),(((int)(235*a))<<24)|0x1c0f0c);ui.textMid(c,toast,w/2,t+ui.dp(18),13,(((int)(255*a))<<24)|0xf6e9df,1,0);postInvalidateOnAnimation();}}
        pointer.draw(c);
    }
    private void drawDialog(Canvas c,float w,float h){
        float dp=ui.dp;ui.rect(c,0,0,w,h,0xb0080302);
        float pw=Math.min(w-dp*40,kind==PROMPT?dp*620:dp*440),ph;
        int rows=kind==CHOOSE?Math.min(items.length,7):0;
        ph=kind==CHOOSE?dp*(70+rows*46+56):kind==CONFIRM?dp*176:Math.min(h-dp*24,dp*(118+4*46+58));
        float l=(w-pw)/2,t=(h-ph)/2,rr=l+pw,b=t+ph;
        ui.round(c,l,t+dp*4,rr,b+dp*6,dp*12,0x80000000);
        ui.hellPanel(c,l,t,rr,b);ui.outline(c,l,t,rr,b,0,0x60ff5a1a,dp);ui.area(l,t,rr,b,H_PANEL);
        ui.glowText(c,title,l+dp*20,t+dp*38,20,0xffff8a50,-1);
        float y=t+dp*58;
        if(kind==CHOOSE){
            float viewH=rows*dp*46,content=items.length*dp*46;scroll=Math.max(0,Math.min(Math.max(0,content-viewH),scroll));
            c.save();c.clipRect(l,y,rr,y+viewH);
            for(int i=0;i<items.length;i++){float it=y+i*dp*46-scroll;if(it+dp*46<y||it>y+viewH)continue;
                r.set(l+dp*16,it+dp*4,rr-dp*16,it+dp*42);ui.button(c,r,-1,items[i],i==selected?Ui.ACTIVE:Ui.NORMAL);
                if(it>=y-dp*20&&it+dp*42<=y+viewH+dp*20)ui.hit(r,H_ITEM+i);}
            c.restore();
            if(content>viewH){float bh=viewH*viewH/content,by=y+(viewH-bh)*scroll/Math.max(1,content-viewH);ui.round(c,rr-dp*8,by,rr-dp*5,by+bh,dp*2,0x80ff8a5a);}
            r.set(rr-dp*130,b-dp*48,rr-dp*16,b-dp*12);ui.button(c,r,-1,"Cancel",Ui.GHOST);ui.hit(r,H_CANCEL);
        }else if(kind==CONFIRM){
            ui.text(c,message,l+dp*20,y+dp*18,14,Ui.TEXT_2);
            r.set(rr-dp*150,b-dp*52,rr-dp*16,b-dp*14);ui.button(c,r,-1,okLabel,danger?Ui.DANGER:Ui.PRIMARY);ui.hit(r,H_OK);
            r.set(rr-dp*280,b-dp*52,rr-dp*162,b-dp*14);ui.button(c,r,-1,"Cancel",Ui.GHOST);ui.hit(r,H_CANCEL);
        }else{
            // the field with a blinking caret
            r.set(l+dp*16,y,rr-dp*16,y+dp*42);ui.round(c,r,dp*6,0xff100806);ui.outline(c,r,dp*6,0xb0ff6a2a,dp*1.2f);
            String s=text.toString();boolean blink=(System.currentTimeMillis()/500)%2==0;
            ui.textMid(c,ui.ellipsize(s,15,1,r.width()-dp*30)+(blink?"|":" "),r.left+dp*12,r.centerY(),15,Ui.TEXT,1,-1);postInvalidateDelayed(250);
            // the on-screen keyboard
            float ky=y+dp*52,gap=dp*5,kw=(pw-dp*32-gap*9)/10,kh=dp*40;
            for(int row=0;row<ROWS.length;row++){String keys=ROWS[row];
                for(int i=0;i<keys.length();i++){char ch=keys.charAt(i);if(shift)ch=Character.toUpperCase(ch);
                    r.set(l+dp*16+i*(kw+gap),ky+row*(kh+gap),l+dp*16+i*(kw+gap)+kw,ky+row*(kh+gap)+kh);ui.button(c,r,-1,String.valueOf(ch),Ui.NORMAL);ui.hit(r,H_KEY+ch);}}
            float by=ky+4*(kh+gap),bw=(pw-dp*32-gap*4)/5;
            String[] labels={shift?"ABC":"abc","Space","⌫","Cancel","OK"};int[] ids={H_KEY+1,H_KEY+' ',H_KEY+8,H_CANCEL,H_OK};
            for(int i=0;i<5;i++){float kx=l+dp*16+i*(bw+gap);r.set(kx,by,kx+bw,by+kh);ui.button(c,r,-1,labels[i],i==4?Ui.PRIMARY:i==3?Ui.GHOST:shift&&i==0?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,ids[i]);}
        }
    }

    // ---------------------------------------------------------------- touch (fingers, and the cursor's clicks)
    @Override public boolean onTouchEvent(MotionEvent e){
        if(kind==NONE)return false;
        int a=e.getActionMasked();float x=e.getX(),y=e.getY();
        if(a==MotionEvent.ACTION_DOWN){downX=x;downY=y;downHit=ui.hitAt(x,y);scrolling=false;return true;}
        if(a==MotionEvent.ACTION_MOVE){if(kind==CHOOSE&&Math.abs(y-downY)>ui.dp*8){scrolling=true;scroll-=y-downY;downY=y;invalidate();}return true;}
        if(a==MotionEvent.ACTION_UP){if(!scrolling){int id=ui.hitAt(x,y);if(id==downHit)tap(id);}return true;}
        return true;
    }
    @Override public void pointerPan(float dx,float dy){if(kind==CHOOSE){scroll+=dy;invalidate();}}
    @Override public void pointerZoom(float steps,float x,float y){if(kind==CHOOSE){scroll-=steps*ui.dp*46;invalidate();}}
    private void tap(int id){
        if(id<0){dismiss();return;}   // outside the panel
        if(id==H_PANEL)return;
        if(id==H_CANCEL){dismiss();return;}
        if(kind==CHOOSE&&id>=H_ITEM&&id<H_ITEM+items.length){Pick cb=onPick;dismiss();if(cb!=null)cb.pick(id-H_ITEM);return;}
        if(kind==CONFIRM&&id==H_OK){Runnable ok=onOk;dismiss();if(ok!=null)ok.run();return;}
        if(kind==PROMPT){
            if(id==H_OK){finishPrompt();return;}
            if(id==H_KEY+1){shift=!shift;invalidate();return;}
            if(id==H_KEY+8){backspace();return;}
            if(id>=H_KEY&&id<H_KEY+256&&text.length()<40){text.append((char)(id-H_KEY));if(shift)shift=false;invalidate();}
        }
    }
    private void finishPrompt(){Text cb=onText;String s=text.toString().trim();dismiss();if(cb!=null&&!s.isEmpty())cb.got(s);}
}
