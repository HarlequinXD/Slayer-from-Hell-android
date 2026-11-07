package com.harlequin.slayer;

/**
 * A small, reusable AI framework: a finite state machine plus a blackboard of what the agent knows.
 *
 * Each frame the owner calls {@link #think}: perception first (sight with a field of view and line of sight,
 * hearing, damage) updates the blackboard, then the current state's update runs and returns the state to be in next.
 * Changing state runs the old state's exit and the new state's enter. States are stateless singletons; anything
 * that must persist lives on the agent or the blackboard, so one state object serves every agent of a kind.
 *
 * Pure Java (no Android), so it runs in the unit tests.
 */
final class Ai<A> {
    /** One behaviour. update returns the next state (itself to stay). */
    interface State<A> {
        String name();
        default void enter(A agent,Ai<A> ai){}
        State<A> update(A agent,Ai<A> ai,float dt);
        default void exit(A agent,Ai<A> ai){}
    }

    // ---------------------------------------------------------------- blackboard
    /** True once the agent knows a target is around (seen, heard or hurt); widens its view to all round. */
    boolean alerted;
    /** Can it see the target right now (refreshed by perceive). */
    boolean canSee;
    /** Where the target was last seen or heard, and how long ago. */
    final float[] lastKnown=new float[3];float sinceSeen=99;
    /** Distance and heading to the target (refreshed by perceive). */
    float dist=99,bearing;
    /** Timers states may use: time in the current state, and a free cooldown. */
    float stateTime,cooldown;
    /** A per-agent random stream (deterministic per agent). */
    final java.util.Random rng;
    /** Name of the state (for debugging and the tests). */
    String stateName(){return state==null?"none":state.name();}

    private State<A> state;
    private float lookTimer;

    Ai(long seed){rng=new java.util.Random(seed);}

    State<A> state(){return state;}
    void set(A agent,State<A> next){
        if(next==state)return;
        if(state!=null)state.exit(agent,this);
        state=next;stateTime=0;
        if(next!=null)next.enter(agent,this);
    }

    /**
     * Sight: inside range, inside the field of view (or anywhere once alerted), and an unobstructed line from the
     * eye to the target's chest. The line-of-sight ray is only cast every `interval` seconds (it is the expensive part).
     */
    void perceive(Level level,float[] eye,float facing,float[] target,float range,float halfFov,float dt,float interval){
        float dx=target[0]-eye[0],dy=target[1]-eye[1],dz=target[2]-eye[2];
        dist=(float)Math.sqrt(dx*dx+dy*dy);bearing=(float)Math.atan2(dy,dx);
        sinceSeen+=dt;cooldown-=dt;
        lookTimer-=dt;
        if(lookTimer<=0){lookTimer=interval*(.8f+.4f*rng.nextFloat());
            boolean inView=dist<range&&(alerted||Math.abs(Hero.wrap(bearing-facing))<halfFov);
            boolean clear=false;
            if(inView){float l=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);float[] hit=level.ray(eye[0],eye[1],eye[2],dx/l,dy/l,dz/l,l);clear=hit==null||hit[6]>=l-.25f;}
            canSee=inView&&clear;}
        if(canSee){alerted=true;sinceSeen=0;lastKnown[0]=target[0];lastKnown[1]=target[1];lastKnown[2]=target[2];}
    }
    /** A noise (a gunshot) or a hit: the agent now knows roughly where the target is. */
    void hear(float x,float y,float z){alerted=true;if(sinceSeen>.5f){lastKnown[0]=x;lastKnown[1]=y;lastKnown[2]=z;sinceSeen=Math.min(sinceSeen,1);}}

    /** Runs the current state once and switches if it asks to. */
    void think(A agent,float dt){
        if(state==null)return;
        stateTime+=dt;
        State<A> next=state.update(agent,this,dt);
        if(next!=state)set(agent,next);
    }
}
