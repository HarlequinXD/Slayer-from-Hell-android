package com.harlequin.slayer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * The texture browser used by the 2D editor's picker and the 3D view's side panel: category tabs (All, Classic,
 * Sci-fi, Hell) over a scrolling grid of lit thumbnails, with the selection marked. Works with touch, the mouse
 * wheel and the global cursor (every tab and thumbnail is a target).
 */
final class TexBrowser {
    static final String[] TABS={"All","Classic","Sci-fi","Hell"};
    int filter;float scroll;
    private int[] list;private int listFilter=-1;
    private static final Bitmap[] bitmaps=new Bitmap[Tex.COUNT];
    private final Paint pixel=new Paint();private final Path path=new Path();private final RectF r=new RectF();
    /** The thumbnails still being made (the view should redraw shortly). */
    boolean waiting;

    TexBrowser(){pixel.setFilterBitmap(false);}

    /** Map texture indices (into SectorMap.MAP_TEXTURES) shown under the current tab. */
    int[] list(){
        if(list!=null&&listFilter==filter)return list;
        int[] all=SectorMap.MAP_TEXTURES;int n=0;int[] out=new int[all.length];
        for(int k=0;k<all.length;k++){int cat=Tex.category(all[k]);if(filter==0||(filter==1&&cat==Tex.CAT_CLASSIC)||(filter==2&&cat==Tex.CAT_SCIFI)||(filter==3&&cat==Tex.CAT_HELL))out[n++]=k;}
        list=java.util.Arrays.copyOf(out,n);listFilter=filter;return list;
    }
    static Bitmap bitmap(int tex){
        if(bitmaps[tex]!=null)return bitmaps[tex];int[] px=Materials.thumb(tex);if(px==null)return null;
        bitmaps[tex]=Bitmap.createBitmap(px,Materials.THUMB,Materials.THUMB,Bitmap.Config.ARGB_8888);return bitmaps[tex];
    }
    /** Tabs in `rows` rows across l..rr from t; returns the bottom. Hit ids: tabBase + tab. */
    float drawTabs(Canvas c,Ui ui,float l,float t,float rr,int rows,int tabBase){
        int per=(TABS.length+rows-1)/rows;float gap=ui.dp(4),h=ui.dp(26),w=(rr-l-gap*(per-1))/per;
        for(int i=0;i<TABS.length;i++){int row=i/per,col=i%per;r.set(l+col*(w+gap),t+row*(h+gap),l+col*(w+gap)+w,t+row*(h+gap)+h);
            ui.button(c,r,-1,TABS[i],i==filter?Ui.ACTIVE:Ui.NORMAL);ui.hit(r,tabBase+i);}
        return t+rows*(h+gap);
    }
    /** The grid in l,t..rr,b with `cols` columns; selected = map texture index. Hit ids: hitBase + map texture index. */
    void drawGrid(Canvas c,Ui ui,float l,float t,float rr,float b,int cols,int selected,int hitBase){
        int[] ls=list();float gap=ui.dp(6),size=(rr-l-gap*(cols-1))/cols;
        float content=((ls.length+cols-1)/cols)*(size+gap);float maxS=Math.max(0,content-(b-t));scroll=Math.max(0,Math.min(maxS,scroll));
        int save=c.save();c.clipRect(l-ui.dp(2),t,rr+ui.dp(2),b);waiting=false;
        for(int k=0;k<ls.length;k++){int col=k%cols,row=k/cols;float tx=l+col*(size+gap),ty=t+row*(size+gap)-scroll;if(ty+size<t||ty>b)continue;
            int idx=ls[k],tex=SectorMap.MAP_TEXTURES[idx];r.set(tx,ty,tx+size,ty+size);Bitmap bm=bitmap(tex);
            int sv=c.save();path.reset();path.addRoundRect(r,ui.dp(6),ui.dp(6),Path.Direction.CW);c.clipPath(path);
            if(bm!=null)c.drawBitmap(bm,null,r,pixel);else{ui.rect(c,r.left,r.top,r.right,r.bottom,Ui.SURFACE_2);waiting=true;}
            c.restoreToCount(sv);
            boolean on=idx==selected,hov=Ui.hovered(r);
            ui.outline(c,tx,ty,tx+size,ty+size,ui.dp(6),on?Ui.ACCENT:hov?0xd0ff8a4a:0x40ff8a5a,on?ui.dp(2.5f):ui.dp(1));
            if(on){ui.p.setShader(null);ui.p.setStyle(Paint.Style.FILL);ui.p.setColor(Ui.ACCENT);c.drawCircle(tx+size-ui.dp(9),ty+ui.dp(9),ui.dp(7),ui.p);ui.icon(c,Icons.CHECK,tx+size-ui.dp(9),ty+ui.dp(9),10,0xffffffff);}
            if(Tex.emissive(tex)){ui.p.setColor(0xffffc060);c.drawCircle(tx+ui.dp(7),ty+size-ui.dp(7),ui.dp(2.5f),ui.p);}   // a glowing material
            float vt=Math.max(t,ty),vb=Math.min(b,ty+size);if(vb>vt)ui.hit(tx,vt,tx+size,vb,hitBase+idx);}
        c.restoreToCount(save);
        if(content>b-t){float bh=(b-t)*(b-t)/content,by=t+(b-t-bh)*scroll/Math.max(1,content-(b-t));ui.round(c,rr+ui.dp(1),by,rr+ui.dp(4),by+bh,ui.dp(2),0x80ff8a5a);}
    }
    /** The map texture index under (x,y) in the last drawn grid (for showing its name), or -1. */
    static int at(Ui ui,float x,float y,int hitBase){int id=ui.hitAt(x,y);return id>=hitBase&&id<hitBase+SectorMap.MAP_TEXTURES.length?id-hitBase:-1;}
    /** Scrolls so map texture index `idx` is in view (a grid of `cols` columns, cell `cell` tall, viewport h). */
    void reveal(int idx,int cols,float cell,float h){int[] ls=list();for(int k=0;k<ls.length;k++)if(ls[k]==idx){float y=(k/cols)*cell;if(y<scroll||y+cell>scroll+h)scroll=Math.max(0,y-h*.4f);return;}}
}
