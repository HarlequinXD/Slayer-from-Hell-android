package android.content;
public interface SharedPreferences {
    interface Editor { Editor putInt(String k,int v);Editor putFloat(String k,float v);Editor putBoolean(String k,boolean v);Editor putString(String k,String v);void apply();boolean commit(); }
    int getInt(String k,int d);float getFloat(String k,float d);boolean getBoolean(String k,boolean d);String getString(String k,String d);Editor edit();
}
