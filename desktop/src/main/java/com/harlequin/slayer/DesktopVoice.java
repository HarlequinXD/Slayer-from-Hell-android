package com.harlequin.slayer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * Slayer's voice on the desktop. Windows: the built-in System.Speech engine through PowerShell, with an installed
 * female voice (Zira on most PCs), pitched down and slowed with SSML. Linux: espeak-ng (or espeak) if installed.
 * {@link Narrator} turns what it renders into the trailer read.
 */
final class DesktopVoice implements Narrator.Voice {
    private static final String PS1=String.join("\r\n",
        "param([string]$In,[string]$Out)",
        "$ErrorActionPreference='Stop'",
        "Add-Type -AssemblyName System.Speech",
        "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer",
        "$f=$s.GetInstalledVoices()|Where-Object{$_.Enabled -and $_.VoiceInfo.Gender -eq 'Female' -and $_.VoiceInfo.Culture.TwoLetterISOLanguageName -eq 'en'}|Select-Object -First 1",
        "if($f){$s.SelectVoice($f.VoiceInfo.Name)}",
        "$fmt=New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(22050,[System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,[System.Speech.AudioFormat.AudioChannel]::Mono)",
        "$s.SetOutputToWaveFile($Out,$fmt)",
        "$t=[System.Security.SecurityElement]::Escape([IO.File]::ReadAllText($In))",
        "$lang=$s.Voice.Culture.Name",
        "$s.SpeakSsml(\"<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='$lang'><prosody pitch='-30%' rate='-12%'>$t</prosody></speak>\")",
        "$s.Dispose()");
    private final boolean windows;private final String espeak;private final File dir;

    private DesktopVoice(boolean windows,String espeak,File dir){this.windows=windows;this.espeak=espeak;this.dir=dir;}
    /** The voice this machine can give her, or null. */
    static Narrator.Voice detect(File tmpDir){
        String os=System.getProperty("os.name","").toLowerCase();
        if(os.contains("win"))return new DesktopVoice(true,null,tmpDir);
        for(String e:new String[]{"espeak-ng","espeak"})
            for(String p:System.getenv().getOrDefault("PATH","").split(File.pathSeparator))
                if(new File(p,e).canExecute())return new DesktopVoice(false,e,tmpDir);
        return null;
    }
    /** Her recorded narration (assets/narration/*.mp3 on the classpath), decoded with JLayer. */
    static final class Recordings implements Narrator.Recordings {
        @Override public float[][] load(String asset) throws Exception {
            java.io.InputStream raw=DesktopVoice.class.getClassLoader().getResourceAsStream(asset);if(raw==null)return null;
            try(java.io.InputStream in=new java.io.BufferedInputStream(raw)){
                javazoom.jl.decoder.Bitstream bs=new javazoom.jl.decoder.Bitstream(in);javazoom.jl.decoder.Decoder d=new javazoom.jl.decoder.Decoder();
                float[] out=new float[24000*20];int len=0,rate=24000;javazoom.jl.decoder.Header h;
                while((h=bs.readFrame())!=null){
                    javazoom.jl.decoder.SampleBuffer sb=(javazoom.jl.decoder.SampleBuffer)d.decodeFrame(h,bs);
                    int ch=sb.getChannelCount(),frames=sb.getBufferLength()/ch;rate=sb.getSampleFrequency();short[] buf=sb.getBuffer();
                    if(len+frames>out.length)out=java.util.Arrays.copyOf(out,Math.max(out.length*2,len+frames));
                    for(int f=0;f<frames;f++){float v=0;for(int c=0;c<ch;c++)v+=buf[f*ch+c];out[len++]=v/(32768f*ch);}
                    bs.closeFrame();}
                bs.close();
                return len==0?null:new float[][]{java.util.Arrays.copyOf(out,len),{rate}};
            }
        }
    }
    @Override public void render(String text,File wav) throws Exception {
        File in=new File(dir,"narration-"+System.nanoTime()+".txt");
        try{
            Files.write(in.toPath(),text.getBytes(StandardCharsets.UTF_8));
            ProcessBuilder pb;
            if(windows){File ps=new File(dir,"narrate.ps1");if(!ps.exists()||ps.length()!=PS1.length())Files.write(ps.toPath(),PS1.getBytes(StandardCharsets.UTF_8));
                pb=new ProcessBuilder("powershell","-NoProfile","-NonInteractive","-WindowStyle","Hidden","-ExecutionPolicy","Bypass","-File",ps.getAbsolutePath(),"-In",in.getAbsolutePath(),"-Out",wav.getAbsolutePath());}
            else pb=new ProcessBuilder(espeak,"-v","en-us+f3","-s","135","-p","25","-w",wav.getAbsolutePath(),"-f",in.getAbsolutePath());
            Process p=pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if(!p.waitFor(90,TimeUnit.SECONDS)){p.destroyForcibly();throw new java.io.IOException("speech timed out");}
            if(p.exitValue()!=0||!wav.exists()||wav.length()<64)throw new java.io.IOException("speech failed ("+p.exitValue()+")");
        }finally{in.delete();}
    }
}
