package android.text.format;
public class DateFormat { public static CharSequence format(CharSequence pattern,long ms){return new java.text.SimpleDateFormat(pattern.toString()).format(new java.util.Date(ms));} }
