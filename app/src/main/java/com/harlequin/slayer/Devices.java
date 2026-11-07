package com.harlequin.slayer;

/**
 * Raw input from the non-touch devices, filled in by the platform (Android's key and motion events, or GLFW on the
 * desktop) and read once per frame by {@link Controls}: an Xbox-style gamepad, the keyboard (as logical keys) and
 * the mouse. Platform-neutral, so the same routing runs on the phone and on Windows.
 */
final class Devices {
    // gamepad buttons (bit flags), Xbox layout
    static final int A=1,B=2,X=4,Y=8,LB=16,RB=32,BACK=64,START=128,LS=256,RS=512,UP=1024,DOWN=2048,LEFT=4096,RIGHT=8192;
    // logical keys
    static final int K_W=0,K_A=1,K_S=2,K_D=3,K_SPACE=4,K_SHIFT=5,K_ESC=6,K_ENTER=7,K_BACKSPACE=8,K_TAB=9,K_E=10,K_F=11,K_Q=12,K_R=13,
            K_UP=14,K_DOWN=15,K_LEFT=16,K_RIGHT=17,K_CTRL=18,K_Z=19,K_Y=20,K_F11=21,K_DELETE=22,K_P=23,K_H=24,K_LBR=25,K_RBR=26,K_PGUP=27,K_PGDN=28,K_C=29,K_COUNT=30;
    static final int SRC_TOUCH=0,SRC_MOUSE=1,SRC_PAD=2,SRC_KEYS=3;

    // ---------------------------------------------------------------- gamepad
    volatile boolean padConnected;
    volatile float lx,ly,rx,ry,lt,rt;   // sticks -1..1 (y down positive, as reported), triggers 0..1
    volatile int buttons;
    // ---------------------------------------------------------------- keyboard
    final boolean[] keys=new boolean[K_COUNT];
    private final StringBuilder typed=new StringBuilder();
    // ---------------------------------------------------------------- mouse
    volatile float mouseX=Float.NaN,mouseY=Float.NaN;volatile boolean mouseL,mouseR,mouseM;
    private float mdx,mdy,wheel;
    /** The device used most recently: decides whether the touch controls or the cursor show. */
    volatile int last=SRC_TOUCH;

    synchronized void key(int k,boolean down){if(k>=0&&k<K_COUNT){keys[k]=down;if(down)last=SRC_KEYS;}}
    synchronized void type(char c){typed.append(c);last=SRC_KEYS;}
    synchronized String takeTyped(){String s=typed.toString();typed.setLength(0);return s;}
    synchronized void mouseMove(float x,float y){mouseX=x;mouseY=y;last=SRC_MOUSE;}
    synchronized void mouseDelta(float dx,float dy){mdx+=dx;mdy+=dy;last=SRC_MOUSE;}
    synchronized float[] takeMouseDelta(){float[] r={mdx,mdy};mdx=mdy=0;return r;}
    synchronized void scroll(float s){wheel+=s;last=SRC_MOUSE;}
    synchronized float takeWheel(){float w=wheel;wheel=0;return w;}
    void touched(){last=SRC_TOUCH;}
    /** Called by the platform after it updates the pad: any real input makes the pad the active device. */
    void padActivity(){if(buttons!=0||Math.abs(lx)>.3f||Math.abs(ly)>.3f||Math.abs(rx)>.3f||Math.abs(ry)>.3f||lt>.3f||rt>.3f)last=SRC_PAD;}

    /** A stick axis with a radial dead zone and a response curve (fine control near the centre). */
    static float[] stick(float x,float y,float dead,float curve){
        float l=(float)Math.hypot(x,y);if(l<dead)return new float[]{0,0};
        float k=Math.min(1,(l-dead)/(1-dead));k=(float)Math.pow(k,curve);return new float[]{x/l*k,y/l*k};
    }
}
