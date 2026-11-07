package com.harlequin.slayer;

/**
 * Difficulty, as in Doom. Each placed thing says which skills it appears on (Doom's easy / medium / hard flags);
 * the two easiest skills use the easy set, Hurt Me Plenty the medium set, Ultra-Violence and Nightmare the hard
 * set. I'm Too Young To Die halves the damage she takes and doubles ammo; Nightmare doubles ammo, makes demons
 * faster (quicker wind-ups, faster fireballs, shorter rests) and brings dead demons back after a while. Pure Java.
 */
final class Skill {
    static final int BABY=0,EASY=1,MEDIUM=2,HARD=3,NIGHTMARE=4;
    static final String[] NAMES={"I'm Too Young To Die","Hey, Not Too Rough","Hurt Me Plenty","Ultra-Violence","Nightmare!"};
    static final String[] BLURBS={"Half damage, double ammo, fewest demons","Fewer demons","The intended fight","More demons","Fast demons that come back. Not even remotely fair."};
    /** Thing flags: which skill sets a thing belongs to. */
    static final int F_EASY=1,F_NORMAL=2,F_HARD=4,F_ALL=7;
    static int current=MEDIUM;

    static int flag(int skill){return skill<=EASY?F_EASY:skill==MEDIUM?F_NORMAL:F_HARD;}
    static boolean present(int thingFlags){return (thingFlags&flag(current))!=0;}
    static float damageTaken(){return current==BABY?.5f:1f;}
    static float ammo(){return current==BABY||current==NIGHTMARE?2f:1f;}
    static boolean fast(){return current==NIGHTMARE;}
    static boolean respawn(){return current==NIGHTMARE;}
}
