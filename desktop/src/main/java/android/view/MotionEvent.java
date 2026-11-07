package android.view;
/** A single-pointer touch event (the desktop has one pointer: the cursor). */
public final class MotionEvent {
    public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5,ACTION_POINTER_UP=6,ACTION_HOVER_MOVE=7,ACTION_SCROLL=8,ACTION_MASK=0xff;
    private int action,source=InputDevice.SOURCE_TOUCHSCREEN;private float x,y;private long down,time;
    public static MotionEvent obtain(long downTime,long eventTime,int action,float x,float y,int meta){MotionEvent e=new MotionEvent();e.down=downTime;e.time=eventTime;e.action=action;e.x=x;e.y=y;return e;}
    public int getAction(){return action;} public int getActionMasked(){return action&ACTION_MASK;} public int getActionIndex(){return 0;}
    public float getX(){return x;} public float getY(){return y;} public float getX(int i){return x;} public float getY(int i){return y;}
    public int getPointerId(int i){return 0;} public int getPointerCount(){return 1;} public int findPointerIndex(int id){return id==0?0:-1;}
    public long getDownTime(){return down;} public long getEventTime(){return time;}
    public void setSource(int s){source=s;} public int getSource(){return source;} public boolean isFromSource(int s){return (source&s)==s;}
    public void setLocation(float nx,float ny){x=nx;y=ny;}
    public void recycle(){}
}
