package android.content.res;
import java.io.*;
/** Assets from the classpath (the APK's assets folder is packed into the desktop jar). */
public class AssetManager {
    public InputStream open(String name) throws IOException {
        InputStream in=AssetManager.class.getResourceAsStream("/"+name);
        if(in==null)throw new FileNotFoundException(name);return new BufferedInputStream(in);
    }
}
