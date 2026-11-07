package com.harlequin.slayer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** The player's own levels: one text file per map (SectorMap's format) in the app's private storage. Pure Java. */
final class MapStore {
    static final class Entry {final String id,name;final long modified;Entry(String id,String name,long m){this.id=id;this.name=name;modified=m;}}
    final File dir;
    MapStore(File base){dir=new File(base,"maps");dir.mkdirs();}

    ArrayList<Entry> list(){
        ArrayList<Entry> out=new ArrayList<>();File[] fs=dir.listFiles((d,n)->n.endsWith(".smap"));if(fs==null)return out;
        for(File f:fs){String id=f.getName().substring(0,f.getName().length()-5);String name=id;
            try{String t=read(f);for(String line:t.split("\n"))if(line.startsWith("name ")){name=line.substring(5).trim();break;}}catch(Exception ignored){}
            out.add(new Entry(id,name,f.lastModified()));}
        out.sort((a,b)->Long.compare(b.modified,a.modified));
        return out;
    }
    SectorMap load(String id)throws Exception{return SectorMap.load(read(new File(dir,id+".smap")));}
    /** Saves the map (a new id when id is null); returns the id. Written to a temp file first so a crash can't corrupt it. */
    String save(String id,SectorMap m)throws Exception{
        if(id==null)id="map_"+System.currentTimeMillis();
        File tmp=new File(dir,id+".tmp"),f=new File(dir,id+".smap");
        try(FileOutputStream o=new FileOutputStream(tmp)){o.write(m.save().getBytes(StandardCharsets.UTF_8));}
        if(!tmp.renameTo(f)){f.delete();if(!tmp.renameTo(f))throw new java.io.IOException("couldn't save "+f);}
        return id;
    }
    boolean delete(String id){return new File(dir,id+".smap").delete();}
    private static String read(File f)throws Exception{try(FileInputStream in=new FileInputStream(f)){java.io.ByteArrayOutputStream o=new java.io.ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);return new String(o.toByteArray(),StandardCharsets.UTF_8);}}
}
