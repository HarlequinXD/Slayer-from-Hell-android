package android.os;
public final class SystemClock { private static final long T0=System.nanoTime(); public static long uptimeMillis(){return (System.nanoTime()-T0)/1000000L;} }
