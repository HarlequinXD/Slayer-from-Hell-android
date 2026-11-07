package android.content;
import android.content.res.*;
import java.io.*;
import java.util.*;
/** The desktop app context: display density, assets from the jar, files and preferences in the user's app-data folder. */
public class Context {
    private final Resources res=new Resources();private final AssetManager assets=new AssetManager();private final File home;
    private final Map<String,Prefs> prefs=new HashMap<>();
    public Context(File home,float density){this.home=home;home.mkdirs();res.getDisplayMetrics().density=density;}
    public Resources getResources(){return res;}
    public AssetManager getAssets(){return assets;}
    public File getFilesDir(){File f=new File(home,"files");f.mkdirs();return f;}
    public File getCacheDir(){File f=new File(home,"cache");f.mkdirs();return f;}
    public SharedPreferences getSharedPreferences(String name,int mode){return prefs.computeIfAbsent(name,n->new Prefs(new File(home,n+".properties")));}

    static final class Prefs implements SharedPreferences {
        final File f;final Properties p=new Properties();
        Prefs(File f){this.f=f;if(f.exists())try(InputStream in=new FileInputStream(f)){p.load(in);}catch(IOException ignored){}}
        public int getInt(String k,int d){try{return Integer.parseInt(p.getProperty(k));}catch(Exception e){return d;}}
        public float getFloat(String k,float d){try{return Float.parseFloat(p.getProperty(k));}catch(Exception e){return d;}}
        public boolean getBoolean(String k,boolean d){String v=p.getProperty(k);return v==null?d:Boolean.parseBoolean(v);}
        public String getString(String k,String d){return p.getProperty(k,d);}
        public Editor edit(){return new Editor(){
            public Editor putInt(String k,int v){p.setProperty(k,""+v);return this;}
            public Editor putFloat(String k,float v){p.setProperty(k,""+v);return this;}
            public Editor putBoolean(String k,boolean v){p.setProperty(k,""+v);return this;}
            public Editor putString(String k,String v){p.setProperty(k,v);return this;}
            public void apply(){commit();}
            public boolean commit(){try(OutputStream o=new FileOutputStream(f)){p.store(o,"Slayer from Hell");return true;}catch(IOException e){return false;}}};}
    }
}
