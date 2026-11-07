package com.harlequin.slayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.util.ArrayList;

/** Shared dark editor theme, density-aware sizing and drawing primitives (from RPG Pocket Editor). */
final class Ui {
    // Palette
    // Hellfire palette (matches the main menu): charred near-black, dried-blood surfaces, ember accents
    static final int BG=0xff0e0807, CANVAS=0xff140b09, SURFACE=0xff1c0f0c, SURFACE_2=0xff2a1510, SURFACE_3=0xff381c15,
            BORDER=0xff4a2219, DIVIDER=0xff2e1712, TEXT=0xfff6e9df, TEXT_2=0xffc9a898, TEXT_3=0xff8c6b5c,
            ACCENT=0xffff5a1a, ACCENT_DARK=0xffc23d0c, ACCENT_SOFT=0x40ff5a1a, ORANGE=0xffffb347, GREEN=0xff6fdc8c,
            RED=0xffff4a3a, AMBER=0xfff2b84b, EMBER_TEXT=0xffffb080;
    static final int NORMAL=0, ACTIVE=1, PRIMARY=2, DANGER=3, GHOST=4, DISABLED=5, SUCCESS=6;

    final float dp;
    final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface regular=Typeface.create("sans-serif",Typeface.NORMAL),
            medium=Typeface.create("sans-serif-medium",Typeface.NORMAL),
            bold=Typeface.create("sans-serif",Typeface.BOLD);
    private final RectF tmp=new RectF();

    /** Touch target registered while drawing, so drawing and hit-testing always agree. */
    static final class Hit { final RectF r=new RectF(); int id; }
    final ArrayList<Hit> hits=new ArrayList<>();
    /** Hit objects are reused from frame to frame instead of allocating hundreds per frame. */
    private final ArrayList<Hit> hitPool=new ArrayList<>();
    private Hit nextHit(){int n=hits.size();Hit h;if(n<hitPool.size())h=hitPool.get(n);else{h=new Hit();hitPool.add(h);}hits.add(h);return h;}

    Ui(Context ctx){dp=ctx.getResources().getDisplayMetrics().density;}
    float dp(float v){return v*dp;}
    static int brighten(int c){int a=c>>>24;int r=Math.min(255,((c>>16)&255)*5/4+20),g=Math.min(255,((c>>8)&255)*5/4+20),b=Math.min(255,(c&255)*5/4+20);return (a<<24)|(r<<16)|(g<<8)|b;}

    void clearHits(){hits.clear();}
    void hit(RectF r,int id){Hit h=nextHit();h.r.set(r);h.id=id;}
    void hit(float l,float t,float r,float b,int id){Hit h=nextHit();h.r.set(l,t,r,b);h.id=id;}
    /** Last registered target under the point wins (drawn on top). */
    int hitAt(float x,float y){for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).r.contains(x,y))return hits.get(i).id;return -1;}

    void rect(Canvas c,float l,float t,float r,float b,int color){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRect(l,t,r,b,p);}
    void round(Canvas c,float l,float t,float r,float b,float radius,int color){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);tmp.set(l,t,r,b);c.drawRoundRect(tmp,radius,radius,p);}
    void round(Canvas c,RectF r,float radius,int color){round(c,r.left,r.top,r.right,r.bottom,radius,color);}
    void outline(Canvas c,float l,float t,float r,float b,float radius,int color,float width){
        p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setColor(color);
        float h=width*.5f;tmp.set(l+h,t+h,r-h,b-h);c.drawRoundRect(tmp,radius,radius,p);p.setStyle(Paint.Style.FILL);
    }
    void outline(Canvas c,RectF r,float radius,int color,float width){outline(c,r.left,r.top,r.right,r.bottom,radius,color,width);}
    void hline(Canvas c,float l,float r,float y,int color){rect(c,l,y,r,y+Math.max(1,dp(.75f)),color);}
    void vline(Canvas c,float x,float t,float b,int color){rect(c,x,t,x+Math.max(1,dp(.75f)),b,color);}

    /** weight: 0 regular, 1 medium, 2 bold. align: -1 left, 0 centre, 1 right. y is the text baseline. */
    void text(Canvas c,String s,float x,float y,float sizeDp,int color,int weight,int align){
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(dp(sizeDp));
        p.setTypeface(weight==2?bold:weight==1?medium:regular);
        p.setTextAlign(align<0?Paint.Align.LEFT:align==0?Paint.Align.CENTER:Paint.Align.RIGHT);
        c.drawText(s,x,y,p);p.setTextAlign(Paint.Align.LEFT);
    }
    void text(Canvas c,String s,float x,float y,float sizeDp,int color){text(c,s,x,y,sizeDp,color,0,-1);}
    /** Draws text vertically centred on cy. */
    void textMid(Canvas c,String s,float x,float cy,float sizeDp,int color,int weight,int align){
        p.setTextSize(dp(sizeDp));p.setTypeface(weight==2?bold:weight==1?medium:regular);
        float base=cy-(p.ascent()+p.descent())*.5f;text(c,s,x,base,sizeDp,color,weight,align);
    }
    float measure(String s,float sizeDp,int weight){p.setTextSize(dp(sizeDp));p.setTypeface(weight==2?bold:weight==1?medium:regular);return p.measureText(s);}
    String ellipsize(String s,float sizeDp,int weight,float max){
        if(measure(s,sizeDp,weight)<=max)return s;
        for(int n=s.length()-1;n>0;n--){String t=s.substring(0,n).trim()+"…";if(measure(t,sizeDp,weight)<=max)return t;}
        return "…";
    }
    void icon(Canvas c,int id,float cx,float cy,float sizeDp,int color){Icons.draw(c,p,id,cx,cy,dp(sizeDp),color);}

    /** Small all-caps section label. */
    void caption(Canvas c,String s,float x,float y,int color){text(c,s.toUpperCase(),x,y,10,color,1,-1);}

    /** Standard button: optional icon (-1 for none) and optional label. */
    void button(Canvas c,RectF r,int icon,String label,int style){
        float rad=dp(6);
        int bg,fg;
        switch(style){
            case ACTIVE: bg=ACCENT_SOFT;fg=EMBER_TEXT;break;
            case PRIMARY: bg=ACCENT;fg=0xffffffff;break;
            case DANGER: bg=SURFACE_2;fg=0xffff7a6a;break;
            case GHOST: bg=0x00000000;fg=TEXT_2;break;
            case DISABLED: bg=0x802a1510;fg=0xff6a4c40;break;
            case SUCCESS: bg=0xff2e8a4c;fg=0xffffffff;break;
            default: bg=SURFACE_2;fg=TEXT;
        }
        if(bg!=0)round(c,r,rad,bg);
        if(style==ACTIVE)outline(c,r,rad,0xb0ff6a2a,dp(1));
        else if(style==NORMAL||style==DANGER||style==DISABLED)outline(c,r,rad,0x26ff8a5a,dp(1));
        float iconSize=Math.min(18,r.height()/dp-14);
        if(label==null||label.isEmpty()){if(icon>=0)icon(c,icon,r.centerX(),r.centerY(),iconSize,fg);return;}
        float textSize=r.height()<dp(30)?11.5f:12.5f;
        if(icon<0){textMid(c,ellipsize(label,textSize,1,r.width()-dp(12)),r.centerX(),r.centerY(),textSize,fg,1,0);return;}
        float pad=dp(r.width()<dp(110)?8:10),gap=dp(6),iw=dp(iconSize);
        float avail=r.width()-pad*2-iw-gap;
        String t=ellipsize(label,textSize,1,avail);
        if(avail<dp(18)){icon(c,icon,r.centerX(),r.centerY(),iconSize,fg);return;}
        icon(c,icon,r.left+pad+iw*.5f,r.centerY(),iconSize,style==NORMAL?TEXT_2:fg);
        textMid(c,t,r.left+pad+iw+gap,r.centerY(),textSize,fg,1,-1);
    }
    /** Centred icon-over-label button (used for big touch controls). */
    void stackButton(Canvas c,RectF r,int icon,String label,int style){
        int bg=style==PRIMARY?ACCENT:style==ACTIVE?ACCENT_SOFT:SURFACE_2,fg=style==PRIMARY?0xffffffff:TEXT;
        round(c,r,dp(8),bg);outline(c,r,dp(8),0x30ff8a5a,dp(1));
        icon(c,icon,r.centerX(),r.centerY()-dp(7),20,fg);
        text(c,label,r.centerX(),r.centerY()+dp(16),10.5f,fg,1,0);
    }
    /** Rounded pill with a soft shadow, used for floating hints and HUD chips. */
    void pill(Canvas c,float l,float t,float r,float b,int color){
        round(c,l,t+dp(2),r,b+dp(2),(b-t)*.5f,0x55000000);
        round(c,l,t,r,b,(b-t)*.5f,color);outline(c,l,t,r,b,(b-t)*.5f,0x50ff6a2a,dp(1));
    }
    /** A panel shaded like the menu: dried blood fading to char, with an ember hairline at the top. */
    void hellPanel(Canvas c,float l,float t,float r,float b){
        p.setShader(new android.graphics.LinearGradient(0,t,0,b,0xff24120d,0xff120908,android.graphics.Shader.TileMode.CLAMP));p.setStyle(Paint.Style.FILL);c.drawRect(l,t,r,b,p);p.setShader(null);
        rect(c,l,t,r,t+Math.max(1,dp(1)),0x55ff5a1a);
    }
    /** Title text with a fire glow (the menu's lettering). */
    void glowText(Canvas c,String s,float x,float y,float sizeDp,int color,int align){
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setTextSize(dp(sizeDp));p.setTypeface(bold);
        p.setTextAlign(align<0?Paint.Align.LEFT:align==0?Paint.Align.CENTER:Paint.Align.RIGHT);
        p.setShadowLayer(dp(8),0,0,0xffc02000);p.setColor(color);c.drawText(s,x,y,p);p.setShadowLayer(0,0,0,0);p.setTextAlign(Paint.Align.LEFT);
    }
    /** Panel with a 1dp border. */
    void panel(Canvas c,float l,float t,float r,float b,float radius){round(c,l,t,r,b,radius,SURFACE);outline(c,l,t,r,b,radius,BORDER,dp(1));}
}
