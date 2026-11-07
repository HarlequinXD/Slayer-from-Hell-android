package com.harlequin.slayer;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Consistent outline icon set drawn on a 24x24 grid with round caps and joins.
 * Every icon is vector, so it stays sharp at any screen density.
 */
final class Icons {
    private Icons(){}

    static final int SECTORS=0,ENTITIES=1,CUBE=2,PLAY=3,GRAPH=4,SAVE=5,RESET=6,PEN=7,POINTER=8,FRAME=9,
            CLOSE_SHAPE=10,UNDO=11,REDO=12,BACKSPACE=13,CANCEL=14,FLOOR_UP=15,FLOOR_DOWN=16,CEIL_UP=17,CEIL_DOWN=18,
            VALIDATE=19,SPARKLE=20,COPY=21,TRASH=22,SCISSORS=23,EXTRUDE=24,MOVE=25,STAIRS=26,DOOR=27,PACKAGE=28,
            WALL_TEX=29,FLOOR_TEX=30,CEIL_TEX=31,NEXT_TEX=32,PREV_TEX=33,LAYERS=34,SUN=35,SUN_DIM=36,GRID=37,
            CROSSHAIR=38,HELP=39,SKULL=40,MEDKIT=41,CHECK=42,CHEVRON_UP=43,CHEVRON_DOWN=44,CHEVRON_LEFT=45,
            CHEVRON_RIGHT=46,ARROW_UP=47,ARROW_DOWN=48,INFO=49,HEART=50,ZAP=51,PLUS_SQUARE=52,LINK=53,FLAG=54,
            STOP=55,ARROW_LEFT=56,FIT=57,LIST=58,TAG=59,PLUS=60,MINUS=61,LOGO=62,CLOCK=63,DOT_CIRCLE=64,CLOUD=65,SEARCH=66,FOLDER=67,ERASER=68,BUCKET=69,DROPPER=70,LINE_TOOL=71,SQUARE=72,SQUARE_FILL=73,CIRCLE=74,CHECKER=75,MOON=76,MIRROR=77,SELECT_RECT=78,PALETTE=79,MORE=80,PIXEL=81,FILM=82,CHEVRONS=83,CAMERA=84,PERSON=85,SPEAKER=86,MUSIC=87,WAVE=88,PIANO=89,SWORD=90,CHAT=91,SCROLL=92,DICE=93,COIN=94,SHIELD=95,POTION=96,BACKPACK=97,GAMEPAD=98;

    private static final Path path=new Path();
    private static final RectF oval=new RectF();

    /** Draws icon {@code id} centred on (cx,cy), {@code size} pixels square. */
    static void draw(Canvas c,Paint p,int id,float cx,float cy,float size,int color){
        int save=c.save();
        c.translate(cx-size*.5f,cy-size*.5f);
        float s=size/24f;c.scale(s,s);
        p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.9f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        shape(c,p,id);
        p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.BUTT);p.setStrokeJoin(Paint.Join.MITER);
        c.restoreToCount(save);
    }

    private static void line(Canvas c,Paint p,float a,float b,float x,float y){c.drawLine(a,b,x,y,p);}
    private static void dot(Canvas c,Paint p,float x,float y,float r){Paint.Style st=p.getStyle();p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r,p);p.setStyle(st);}
    private static void poly(Canvas c,Paint p,boolean close,float... pts){
        path.reset();path.moveTo(pts[0],pts[1]);for(int i=2;i<pts.length;i+=2)path.lineTo(pts[i],pts[i+1]);if(close)path.close();c.drawPath(path,p);
    }
    private static void rrect(Canvas c,Paint p,float l,float t,float r,float b,float rad){oval.set(l,t,r,b);c.drawRoundRect(oval,rad,rad,p);}
    private static void arc(Canvas c,Paint p,float cx,float cy,float r,float start,float sweep){oval.set(cx-r,cy-r,cx+r,cy+r);c.drawArc(oval,start,sweep,false,p);}

    private static void shape(Canvas c,Paint p,int id){
        switch(id){
            case LOGO: // stylised sector with portal
                poly(c,p,true,3,8,12,3,21,8,21,16,12,21,3,16);line(c,p,12,3,12,21);dot(c,p,12,12,2.2f);break;
            case SECTORS:
                poly(c,p,true,4,8,12,3.5f,20,8,18,19,6.5f,20);
                dot(c,p,4,8,1.7f);dot(c,p,12,3.5f,1.7f);dot(c,p,20,8,1.7f);dot(c,p,18,19,1.7f);dot(c,p,6.5f,20,1.7f);break;
            case ENTITIES:
                c.drawCircle(12,8,4,p);
                path.reset();path.moveTo(4.5f,21);path.cubicTo(4.5f,16,8,13.5f,12,13.5f);path.cubicTo(16,13.5f,19.5f,16,19.5f,21);c.drawPath(path,p);break;
            case CUBE:
                poly(c,p,true,21,7.5f,12,2.5f,3,7.5f,3,16.5f,12,21.5f,21,16.5f);poly(c,p,false,3,7.5f,12,12.5f,21,7.5f);line(c,p,12,12.5f,12,21.5f);break;
            case PLAY: poly(c,p,true,7,4,19,12,7,20);break;
            case GRAPH:
                rrect(c,p,2.5f,3.5f,9.5f,9,1.5f);rrect(c,p,14.5f,3.5f,21.5f,9,1.5f);rrect(c,p,8.5f,15,15.5f,20.5f,1.5f);
                line(c,p,9.5f,6.25f,14.5f,6.25f);poly(c,p,false,6,9,6,12,12,12,12,15);line(c,p,18,9,18,12);line(c,p,18,12,12,12);break;
            case SAVE:
                path.reset();path.moveTo(5,3);path.lineTo(16,3);path.lineTo(21,8);path.lineTo(21,19);path.quadTo(21,21,19,21);path.lineTo(5,21);path.quadTo(3,21,3,19);path.lineTo(3,5);path.quadTo(3,3,5,3);path.close();c.drawPath(path,p);
                poly(c,p,false,7,3,7,8,15,8);poly(c,p,false,7,21,7,14,17,14,17,21);break;
            case RESET:
                arc(c,p,12,12,9,180,-270);path.reset();path.moveTo(12,3);path.quadTo(8,3,5.3f,5.7f);path.lineTo(3,8);c.drawPath(path,p);poly(c,p,false,3,3,3,8,8,8);break;
            case PEN:
                poly(c,p,true,17,3,21,7,8,20,3,21,4,16);line(c,p,15,5,19,9);break;
            case POINTER: poly(c,p,true,4,4,11,21,13.5f,13.5f,21,11);break;
            case FRAME:
                c.drawRect(5,5,19,19,p);
                dot(c,p,5,5,2);dot(c,p,19,5,2);dot(c,p,5,19,2);dot(c,p,19,19,2);break;
            case CLOSE_SHAPE:
                poly(c,p,true,4,9,12,3.5f,20,9,17.5f,20,6.5f,20);poly(c,p,false,8.5f,12.5f,11,15,15.5f,10.5f);break;
            case UNDO:
                poly(c,p,false,9,14,4,9,9,4);path.reset();path.moveTo(4,9);path.lineTo(14.5f,9);path.cubicTo(17.5f,9,20,11.5f,20,14.5f);path.cubicTo(20,17.5f,17.5f,20,14.5f,20);path.lineTo(11,20);c.drawPath(path,p);break;
            case REDO:
                poly(c,p,false,15,14,20,9,15,4);path.reset();path.moveTo(20,9);path.lineTo(9.5f,9);path.cubicTo(6.5f,9,4,11.5f,4,14.5f);path.cubicTo(4,17.5f,6.5f,20,9.5f,20);path.lineTo(13,20);c.drawPath(path,p);break;
            case BACKSPACE:
                path.reset();path.moveTo(21,5);path.lineTo(9,5);path.lineTo(2,12);path.lineTo(9,19);path.lineTo(21,19);path.quadTo(22,19,22,18);path.lineTo(22,6);path.quadTo(22,5,21,5);path.close();c.drawPath(path,p);
                line(c,p,18,9,12,15);line(c,p,12,9,18,15);break;
            case CANCEL: c.drawCircle(12,12,9,p);line(c,p,15,9,9,15);line(c,p,9,9,15,15);break;
            case FLOOR_UP: line(c,p,12,17,12,4);poly(c,p,false,7,9,12,4,17,9);line(c,p,4,21,20,21);break;
            case FLOOR_DOWN: line(c,p,12,4,12,17);poly(c,p,false,7,12,12,17,17,12);line(c,p,4,21,20,21);break;
            case CEIL_UP: line(c,p,4,3,20,3);line(c,p,12,20,12,7);poly(c,p,false,7,12,12,7,17,12);break;
            case CEIL_DOWN: line(c,p,4,3,20,3);line(c,p,12,7,12,20);poly(c,p,false,7,15,12,20,17,15);break;
            case VALIDATE:
                path.reset();path.moveTo(12,3);path.lineTo(20,6);path.lineTo(20,12);path.cubicTo(20,16.5f,16.5f,19.8f,12,21);path.cubicTo(7.5f,19.8f,4,16.5f,4,12);path.lineTo(4,6);path.close();c.drawPath(path,p);
                poly(c,p,false,8.5f,12,11,14.5f,15.5f,10);break;
            case SPARKLE:
                poly(c,p,true,11,4,12.6f,10.4f,19,12,12.6f,13.6f,11,20,9.4f,13.6f,3,12,9.4f,10.4f);line(c,p,19,3,19,7);line(c,p,17,5,21,5);break;
            case COPY:
                rrect(c,p,8,8,21,21,2);path.reset();path.moveTo(5,16);path.quadTo(3,16,3,14);path.lineTo(3,5);path.quadTo(3,3,5,3);path.lineTo(14,3);path.quadTo(16,3,16,5);c.drawPath(path,p);break;
            case TRASH:
                line(c,p,3,6,21,6);poly(c,p,false,8,6,8,3.5f,16,3.5f,16,6);poly(c,p,false,5,6,6,21,18,21,19,6);line(c,p,10,11,10,17);line(c,p,14,11,14,17);break;
            case SCISSORS:
                c.drawCircle(6,6,3,p);c.drawCircle(6,18,3,p);line(c,p,20,4,8.1f,15.9f);line(c,p,14.5f,14.5f,20,20);line(c,p,8.1f,8.1f,12,12);break;
            case EXTRUDE:
                poly(c,p,false,8,13,4,13,4,21,20,21,20,13,16,13);line(c,p,12,17,12,3);poly(c,p,false,8,7,12,3,16,7);break;
            case MOVE:
                poly(c,p,false,5,9,2,12,5,15);poly(c,p,false,9,5,12,2,15,5);poly(c,p,false,15,19,12,22,9,19);poly(c,p,false,19,9,22,12,19,15);line(c,p,2,12,22,12);line(c,p,12,2,12,22);break;
            case STAIRS: poly(c,p,false,3,20,8,20,8,15,13,15,13,10,18,10,18,5,21,5);break;
            case DOOR:
                path.reset();path.moveTo(6,21);path.lineTo(6,5);path.quadTo(6,3,8,3);path.lineTo(16,3);path.quadTo(18,3,18,5);path.lineTo(18,21);c.drawPath(path,p);line(c,p,3,21,21,21);dot(c,p,14.5f,12.5f,1.2f);break;
            case PACKAGE:
                poly(c,p,true,21,7.5f,12,2.5f,3,7.5f,3,16.5f,12,21.5f,21,16.5f);poly(c,p,false,3,7.5f,12,12.5f,21,7.5f);line(c,p,12,12.5f,12,21.5f);line(c,p,7.5f,5,16.5f,10);break;
            case WALL_TEX:
                rrect(c,p,3,4,21,20,1.5f);line(c,p,3,9.3f,21,9.3f);line(c,p,3,14.7f,21,14.7f);line(c,p,9,4,9,9.3f);line(c,p,15,4,15,9.3f);line(c,p,12,9.3f,12,14.7f);line(c,p,9,14.7f,9,20);line(c,p,15,14.7f,15,20);break;
            case FLOOR_TEX: poly(c,p,true,2,20,7,9,17,9,22,20);line(c,p,12,9,12,20);line(c,p,4.4f,14.5f,19.6f,14.5f);break;
            case CEIL_TEX: poly(c,p,true,2,4,7,15,17,15,22,4);line(c,p,12,4,12,15);line(c,p,4.4f,9.5f,19.6f,9.5f);break;
            case NEXT_TEX: rrect(c,p,3,3,21,21,2.5f);poly(c,p,false,10,8,14,12,10,16);break;
            case PREV_TEX: rrect(c,p,3,3,21,21,2.5f);poly(c,p,false,14,8,10,12,14,16);break;
            case LAYERS: poly(c,p,true,12,3,21,8,12,13,3,8);poly(c,p,false,3,12.5f,12,17.5f,21,12.5f);poly(c,p,false,3,17,12,22,21,17);break;
            case SUN:
                c.drawCircle(12,12,4,p);
                for(int i=0;i<8;i++){double a=i*Math.PI/4;float cs=(float)Math.cos(a),sn=(float)Math.sin(a);line(c,p,12+cs*7,12+sn*7,12+cs*9.5f,12+sn*9.5f);}break;
            case SUN_DIM:
                c.drawCircle(12,12,4,p);
                for(int i=0;i<8;i++){double a=i*Math.PI/4;dot(c,p,12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,1.1f);}break;
            case GRID: rrect(c,p,3,3,21,21,2);line(c,p,3,9,21,9);line(c,p,3,15,21,15);line(c,p,9,3,9,21);line(c,p,15,3,15,21);break;
            case CROSSHAIR: c.drawCircle(12,12,8,p);line(c,p,22,12,17,12);line(c,p,7,12,2,12);line(c,p,12,7,12,2);line(c,p,12,22,12,17);dot(c,p,12,12,1.4f);break;
            case HELP:
                c.drawCircle(12,12,9,p);path.reset();path.moveTo(9.2f,9.2f);path.cubicTo(9.5f,7.7f,10.7f,6.9f,12.1f,6.9f);path.cubicTo(13.7f,6.9f,15,8,15,9.5f);path.cubicTo(15,11.5f,12,11.9f,12,13.8f);c.drawPath(path,p);dot(c,p,12,17,1.2f);break;
            case SKULL:
                path.reset();path.moveTo(12,3);path.cubicTo(7.3f,3,4,6.3f,4,10.5f);path.cubicTo(4,13,5.2f,14.9f,7,16);path.lineTo(7,20);path.lineTo(17,20);path.lineTo(17,16);path.cubicTo(18.8f,14.9f,20,13,20,10.5f);path.cubicTo(20,6.3f,16.7f,3,12,3);path.close();c.drawPath(path,p);
                dot(c,p,9,11,1.8f);dot(c,p,15,11,1.8f);line(c,p,10.5f,20,10.5f,17.5f);line(c,p,13.5f,20,13.5f,17.5f);break;
            case MEDKIT: rrect(c,p,3,6,21,20,2);poly(c,p,false,8.5f,6,8.5f,3.5f,15.5f,3.5f,15.5f,6);line(c,p,12,10,12,16);line(c,p,9,13,15,13);break;
            case CHECK: poly(c,p,false,5,12.5f,10,17.5f,19,7);break;
            case CHEVRON_UP: poly(c,p,false,6,15,12,9,18,15);break;
            case CHEVRON_DOWN: poly(c,p,false,6,9,12,15,18,9);break;
            case CHEVRON_LEFT: poly(c,p,false,15,6,9,12,15,18);break;
            case CHEVRON_RIGHT: poly(c,p,false,9,6,15,12,9,18);break;
            case ARROW_UP: line(c,p,12,20,12,4);poly(c,p,false,5,11,12,4,19,11);break;
            case ARROW_DOWN: line(c,p,12,4,12,20);poly(c,p,false,5,13,12,20,19,13);break;
            case ARROW_LEFT: line(c,p,20,12,4,12);poly(c,p,false,11,5,4,12,11,19);break;
            case INFO: c.drawCircle(12,12,9,p);line(c,p,12,16,12,11.5f);dot(c,p,12,8,1.2f);break;
            case HEART:
                path.reset();path.moveTo(12,20);path.cubicTo(5,15.5f,2.5f,12,2.5f,8.8f);path.cubicTo(2.5f,6,4.6f,4,7.2f,4);path.cubicTo(9.2f,4,10.9f,5.1f,12,7);
                path.cubicTo(13.1f,5.1f,14.8f,4,16.8f,4);path.cubicTo(19.4f,4,21.5f,6,21.5f,8.8f);path.cubicTo(21.5f,12,19,15.5f,12,20);path.close();c.drawPath(path,p);break;
            case ZAP: poly(c,p,true,13,2,4,14,12,14,11,22,20,10,12,10);break;
            case PLUS_SQUARE: rrect(c,p,3,3,21,21,2.5f);line(c,p,12,8,12,16);line(c,p,8,12,16,12);break;
            case PLUS: line(c,p,12,5,12,19);line(c,p,5,12,19,12);break;
            case MINUS: line(c,p,5,12,19,12);break;
            case LINK:
                c.drawCircle(5,18,2.5f,p);c.drawCircle(19,6,2.5f,p);path.reset();path.moveTo(7.5f,18);path.cubicTo(15,18,9,6,16.5f,6);c.drawPath(path,p);break;
            case FLAG: line(c,p,5,22,5,3);poly(c,p,true,5,3.5f,18,3.5f,15.5f,8.25f,18,13,5,13);break;
            case STOP: rrect(c,p,5,5,19,19,2);break;
            case FIT:
                poly(c,p,false,3,8,3,3,8,3);poly(c,p,false,16,3,21,3,21,8);poly(c,p,false,21,16,21,21,16,21);poly(c,p,false,8,21,3,21,3,16);rrect(c,p,8,8,16,16,1.5f);break;
            case LIST:
                line(c,p,9,6,20,6);line(c,p,9,12,20,12);line(c,p,9,18,20,18);dot(c,p,4.5f,6,1.3f);dot(c,p,4.5f,12,1.3f);dot(c,p,4.5f,18,1.3f);break;
            case CLOCK: c.drawCircle(12,12,9,p);poly(c,p,false,12,7,12,12,15.5f,14);break;
            case DOT_CIRCLE: c.drawCircle(12,12,9,p);dot(c,p,12,12,3.5f);break;
            case CLOUD:
                path.reset();path.moveTo(7,19);path.cubicTo(4,19,2.5f,17,2.5f,14.8f);path.cubicTo(2.5f,12.4f,4.4f,10.7f,6.6f,10.8f);path.cubicTo(7.4f,7.6f,10,5.5f,13,5.5f);
                path.cubicTo(16.6f,5.5f,19.4f,8.3f,19.5f,11.8f);path.cubicTo(21.2f,12.3f,22,13.8f,22,15.4f);path.cubicTo(22,17.4f,20.4f,19,18.4f,19);path.close();c.drawPath(path,p);break;
            case SEARCH: c.drawCircle(10.5f,10.5f,6.5f,p);line(c,p,15.5f,15.5f,21,21);break;
            case FOLDER:
                path.reset();path.moveTo(3,6);path.quadTo(3,4,5,4);path.lineTo(9.5f,4);path.lineTo(11.5f,6.5f);path.lineTo(19,6.5f);path.quadTo(21,6.5f,21,8.5f);path.lineTo(21,18);path.quadTo(21,20,19,20);path.lineTo(5,20);path.quadTo(3,20,3,18);path.close();c.drawPath(path,p);break;
            case ERASER:
                poly(c,p,true,8,20,3.5f,15.5f,13.5f,5.5f,20.5f,12.5f,13,20);line(c,p,9,10,16,17);line(c,p,8,20,20,20);break;
            case BUCKET:
                poly(c,p,true,4,11,11,4,18,11,11,18);line(c,p,4,11,18,11);
                path.reset();path.moveTo(19.5f,14);path.cubicTo(18,16.5f,18,18.5f,19.5f,18.5f);path.cubicTo(21,18.5f,21,16.5f,19.5f,14);path.close();c.drawPath(path,p);break;
            case DROPPER:
                poly(c,p,false,14.5f,6.5f,4,17,4,20,7,20,17.5f,9.5f);rrect(c,p,13,3,21,11,2.5f);break;
            case LINE_TOOL: line(c,p,5,19,19,5);dot(c,p,5,19,2);dot(c,p,19,5,2);break;
            case SQUARE: rrect(c,p,4,4,20,20,1.5f);break;
            case SQUARE_FILL: {Paint.Style st=p.getStyle();p.setStyle(Paint.Style.FILL);rrect(c,p,4,4,20,20,1.5f);p.setStyle(st);break;}
            case CIRCLE: c.drawCircle(12,12,8.5f,p);break;
            case CHECKER: {rrect(c,p,4,4,20,20,1.5f);Paint.Style st=p.getStyle();p.setStyle(Paint.Style.FILL);c.drawRect(4,4,12,12,p);c.drawRect(12,12,20,20,p);p.setStyle(st);break;}
            case MOON: path.reset();path.moveTo(20,14.5f);path.cubicTo(18.8f,18.3f,15.2f,21,11,21);path.cubicTo(6,21,3,17,3,12);path.cubicTo(3,7.8f,5.7f,4.2f,9.5f,3);path.cubicTo(7.4f,9,12,16.5f,20,14.5f);path.close();c.drawPath(path,p);break;
            case MIRROR: line(c,p,12,3,12,21);poly(c,p,true,9,7,3,17,9,17);poly(c,p,true,15,7,21,17,15,17);break;
            case SELECT_RECT:
                for(int i=0;i<4;i++){float a=4+i*4.5f;line(c,p,a,4,a+2,4);line(c,p,a,20,a+2,20);line(c,p,4,a,4,a+2);line(c,p,20,a,20,a+2);}break;
            case PALETTE:
                path.reset();path.moveTo(12,3);path.cubicTo(6.5f,3,3,7,3,12);path.cubicTo(3,17,7,21,12,21);path.cubicTo(13.5f,21,14,20,14,19);path.cubicTo(14,17.5f,13,17,13,16);
                path.cubicTo(13,15,14,14,15,14);path.lineTo(17,14);path.cubicTo(19.5f,14,21,12.5f,21,10.5f);path.cubicTo(21,6.5f,17,3,12,3);path.close();c.drawPath(path,p);
                dot(c,p,7.5f,11,1.4f);dot(c,p,10,7,1.4f);dot(c,p,15,7.5f,1.4f);break;
            case MORE: dot(c,p,5,12,1.8f);dot(c,p,12,12,1.8f);dot(c,p,19,12,1.8f);break;
            case PIXEL: {Paint.Style st=p.getStyle();p.setStyle(Paint.Style.FILL);c.drawRect(5,5,11,11,p);c.drawRect(13,13,19,19,p);p.setStyle(st);rrect(c,p,3,3,21,21,2);break;}
            case FILM: rrect(c,p,3,5,21,19,2);line(c,p,7,5,7,19);line(c,p,17,5,17,19);line(c,p,3,12,7,12);line(c,p,17,12,21,12);break;
            case CAMERA: rrect(c,p,2,7,16,17,2);poly(c,p,true,16,10,22,7,22,17,16,14);dot(c,p,6,11,1.3f);break;
            case PERSON: c.drawCircle(12,6.5f,3.5f,p);poly(c,p,false,5,21,5,16,8,12,16,12,19,16,19,21);break;
            case SWORD: line(c,p,19,5,8,16);poly(c,p,false,19,5,19,8,16,8);line(c,p,6,13,11,18);line(c,p,8,16,5,19);break;
            case CHAT: rrect(c,p,3,4,21,16,4);poly(c,p,false,8,16,7,21,12,16);break;
            case SCROLL: poly(c,p,false,7,4,18,4);poly(c,p,false,6,20,17,20);arc(c,p,7,6,2,90,180);line(c,p,5,6,5,18);arc(c,p,17,18,2,-90,180);line(c,p,19,6,19,18);line(c,p,9,9,15,9);line(c,p,9,13,15,13);break;
            case DICE: poly(c,p,true,12,2,21,7,21,17,12,22,3,17,3,7);poly(c,p,false,3,7,12,12,21,7);line(c,p,12,12,12,22);break;
            case COIN: c.drawCircle(12,12,8.5f,p);c.drawCircle(12,12,5,p);break;
            case SHIELD: poly(c,p,true,12,3,20,6,19,13,12,21,5,13,4,6);line(c,p,12,6,12,18);break;
            case POTION: poly(c,p,false,10,3,14,3);poly(c,p,false,10,3,10,8,5,14,5,18,8,21,16,21,19,18,19,14,14,8,14,3);line(c,p,6,14,18,14);break;
            case GAMEPAD: rrect(c,p,2,7,22,18,5);c.drawCircle(16,11.5f,1.4f,p);c.drawCircle(18.5f,14,1.4f,p);line(c,p,7,10,7,15);line(c,p,4.5f,12.5f,9.5f,12.5f);break;
            case BACKPACK: rrect(c,p,5,6,19,21,3);poly(c,p,false,9,6,9,3,15,3,15,6);rrect(c,p,8,12,16,17,1.5f);break;
            case SPEAKER: poly(c,p,true,3,9.5f,7,9.5f,12,5,12,19,7,14.5f,3,14.5f);arc(c,p,13,12,4,-50,100);arc(c,p,13,12,8,-50,100);break;
            case MUSIC: poly(c,p,false,9,18,9,5,20,3,20,16);c.drawCircle(6.5f,18,2.6f,p);c.drawCircle(17.5f,16,2.6f,p);break;
            case WAVE: poly(c,p,false,2,12,4,12,6,6,8.5f,18,11,4,13.5f,20,16,8,18,15,20,12,22,12);break;
            case PIANO: rrect(c,p,3,4,21,20,2);line(c,p,9,12,9,20);line(c,p,15,12,15,20);
                {Paint.Style st=p.getStyle();p.setStyle(Paint.Style.FILL);c.drawRect(7.5f,4,10.5f,12,p);c.drawRect(13.5f,4,16.5f,12,p);p.setStyle(st);}break;
            case CHEVRONS: poly(c,p,false,6,6,12,12,6,18);poly(c,p,false,12,6,18,12,12,18);break;
            case TAG:
                poly(c,p,true,3,3,12,3,21,12,12,21,3,12);dot(c,p,8,8,1.5f);break;
        }
    }
}
