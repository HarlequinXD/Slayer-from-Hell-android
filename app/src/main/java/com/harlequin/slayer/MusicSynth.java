package com.harlequin.slayer;

import java.util.Random;

/**
 * The game's music, synthesized from scratch: for each of two themes (HELL: horror dubstep in F phrygian; TECH:
 * sci-fi dubstep in D minor) a seamless ambient bed for exploring, a 16-bar 140 BPM half-time combat loop, and the
 * impact that the drop lands on. Everything is rendered offline into stereo float buffers (44.1 kHz) by small,
 * honest DSP pieces: polyBLEP oscillators, a TPT state-variable filter, formant banks, a Schroeder reverb, sidechain
 * ducking and a soft-clipping master. Pure Java, deterministic (seeded), so it renders the same on every device.
 */
final class MusicSynth {
    static final int RATE=44100,HELL=0,TECH=1;
    static final float BPM=140;
    static final int STEP=Math.round(RATE*60f/BPM/4);   // one sixteenth
    static final int BAR=STEP*16,BARS=16;

    /** A stereo buffer. */
    static final class Track {final float[] l,r;Track(int n){l=new float[n];r=new float[n];}int length(){return l.length;}}

    private final Random rng;private final int theme;
    MusicSynth(int theme){this.theme=theme;rng=new Random(theme==HELL?666:2087);}

    // ================================================================== DSP pieces
    /** TPT state-variable filter (Zavalishin): stable under fast modulation. */
    static final class Svf {
        float ic1,ic2,a1,a2,a3,k,g;float low,band,high;
        void set(float fc,float q){fc=Math.max(15,Math.min(RATE*.45f,fc));g=(float)Math.tan(Math.PI*fc/RATE);k=1f/Math.max(.3f,q);a1=1f/(1+g*(g+k));a2=g*a1;a3=g*a2;}
        float run(float x){float v3=x-ic2,v1=a1*ic1+a2*v3,v2=ic2+a2*ic1+a3*v3;ic1=2*v1-ic1;ic2=2*v2-ic2;low=v2;band=v1;high=x-k*v1-v2;return low;}
    }
    /** A band-limited saw (polyBLEP). */
    static final class Saw {double ph;float next(float f){double dt=f/RATE;ph+=dt;if(ph>=1)ph-=1;float t=(float)ph;float v=2*t-1;v-=blep(t,(float)dt);return v;}}
    static final class Sqr {double ph;float next(float f,float pw){double dt=f/RATE;ph+=dt;if(ph>=1)ph-=1;float t=(float)ph;float v=t<pw?1:-1;v+=blep(t,(float)dt);float t2=t-pw;if(t2<0)t2+=1;v-=blep(t2,(float)dt);return v;}}
    static float blep(float t,float dt){if(dt<=0)return 0;if(t<dt){t/=dt;return t+t-t*t-1;}if(t>1-dt){t=(t-1)/dt;return t*t+t+t+1;}return 0;}
    /** Schroeder reverb: four combs and two allpasses per side, detuned between sides. */
    static final class Verb {
        final float[][] comb,ap;final int[] ci=new int[8],ai=new int[4];final float fb,damp;final float[] lp=new float[8];
        Verb(float size,float damp){this.damp=damp;fb=.70f+.26f*size;int[] cl={1116,1188,1277,1356,1139,1211,1300,1379},al={556,441,579,464};
            comb=new float[8][];for(int i=0;i<8;i++)comb[i]=new float[Math.round(cl[i]*(.6f+.9f*size))];ap=new float[4][];for(int i=0;i<4;i++)ap[i]=new float[al[i]];}
        void run(float[] inL,float[] inR,float[] outL,float[] outR,float wet){
            int n=inL.length;
            for(int s=0;s<n;s++){float x=(inL[s]+inR[s])*.015f;float yl=0,yr=0;
                for(int c=0;c<8;c++){float[] b=comb[c];int i=ci[c];float o=b[i];lp[c]=o*(1-damp)+lp[c]*damp;b[i]=x+lp[c]*fb;ci[c]=(i+1)%b.length;if(c<4)yl+=o;else yr+=o;}
                for(int a=0;a<4;a++){float[] b=ap[a];int i=ai[a];float o=b[i];float in=a<2?yl:yr;float v=-in+o;b[i]=in+o*.5f;ai[a]=(i+1)%b.length;if(a<2)yl=v;else yr=v;}
                outL[s]+=yl*wet;outR[s]+=yr*wet;}
        }
    }
    static float noteHz(float midi){return 440f*(float)Math.pow(2,(midi-69)/12f);}
    float rnd(float a,float b){return a+rng.nextFloat()*(b-a);}
    float noise(){return rng.nextFloat()*2-1;}
    static float softClip(float x){return (float)Math.tanh(x);}
    /** Equal-power pan: adds v to the two channels at position p (-1 left .. 1 right). */
    static void pan(Track t,int i,float v,float p){if(i<0||i>=t.l.length)return;float a=(p+1)*.25f*(float)Math.PI;t.l[i]+=v*(float)Math.cos(a);t.r[i]+=v*(float)Math.sin(a);}

    // ================================================================== drums
    private void kick(Track t,int at,float vel,float[] duck){
        int n=Math.round(RATE*.42f);double ph=0;
        for(int i=0;i<n;i++){float s=i/(float)RATE;float f=46+130*(float)Math.exp(-s*38)+20*(float)Math.exp(-s*9);ph+=f/RATE;
            float body=(float)Math.sin(2*Math.PI*ph)*(float)Math.exp(-s*7.5f);float click=i<220?noise()*(1-i/220f)*.35f:0;
            float v=softClip((body*1.6f+click)*1.4f)*vel*.9f;pan(t,at+i,v,0);}
        if(duck!=null)for(int i=0;i<RATE*.32f&&at+i<duck.length;i++){float s=i/(RATE*.32f);duck[at+i]=Math.min(duck[at+i],.2f+.8f*s*s);}
    }
    private void snare(Track t,Track send,int at,float vel){
        int n=Math.round(RATE*.38f);Svf bp=new Svf();bp.set(theme==HELL?1800:2600,1.2f);Svf hp=new Svf();hp.set(900,.7f);double ph=0;
        for(int i=0;i<n;i++){float s=i/(float)RATE;ph+=(185+60*Math.exp(-s*40))/RATE;float body=(float)Math.sin(2*Math.PI*ph)*(float)Math.exp(-s*22)*.9f;
            float nz=noise();bp.run(nz);hp.run(nz);float rattle=(bp.band*1.2f+hp.high*.5f)*(float)Math.exp(-s*(theme==HELL?11:14));
            float clap=0;for(int k=0;k<3;k++){float d=s-k*.011f;if(d>=0)clap+=noise()*(float)Math.exp(-d*(k==2?30:160))*.5f;}
            float v=softClip((body+rattle+clap)*1.5f)*vel*.62f;pan(t,at+i,v,0);pan(send,at+i,v*.9f,0);}
    }
    private void hat(Track t,int at,float vel,boolean open,float p){
        int n=Math.round(RATE*(open?.24f:.05f));Svf hp=new Svf();hp.set(7500,.9f);
        for(int i=0;i<n;i++){float s=i/(float)RATE;hp.run(noise());float v=hp.high*(float)Math.exp(-s*(open?14:85))*vel*.22f;pan(t,at+i,v,p);}
    }
    private void crash(Track t,Track send,int at,float vel,float len){
        int n=Math.round(RATE*len);Svf hp=new Svf();hp.set(4500,.7f);Svf bp=new Svf();bp.set(9000,2);
        for(int i=0;i<n;i++){float s=i/(float)RATE;float nz=noise();hp.run(nz);bp.run(nz);float v=(hp.high*.7f+bp.band*.6f)*(float)Math.exp(-s*2.4f/len*1.6f)*vel*.35f;
            pan(t,at+i,v,(i%2==0?.3f:-.3f));pan(send,at+i,v*.6f,0);}
    }
    private void tom(Track t,Track send,int at,float vel,float hz){
        int n=Math.round(RATE*.5f);double ph=0;
        for(int i=0;i<n;i++){float s=i/(float)RATE;ph+=(hz*(1+.6f*Math.exp(-s*25)))/RATE;float v=softClip((float)Math.sin(2*Math.PI*ph)*(float)Math.exp(-s*6)*1.4f+noise()*.08f*(float)Math.exp(-s*30))*vel*.55f;pan(t,at+i,v,0);pan(send,at+i,v*.7f,0);}
    }

    // ================================================================== bass
    /** One bass event: notes in semitones from the key root (MIDI), LFO cycles per beat, and its voice. */
    static final int WOB=0,GROWL=1,YOI=2,FMB=3,SUBONLY=4,LASER=5;
    private static final class Ev {final int step,len,voice;final float semi,rate,bend;Ev(int st,int ln,float se,float ra,int vo,float be){step=st;len=ln;semi=se;rate=ra;voice=vo;bend=be;}}
    private static Ev ev(int st,int ln,float se,float ra,int vo){return new Ev(st,ln,se,ra,vo,0);}
    private static Ev ev(int st,int ln,float se,float ra,int vo,float bend){return new Ev(st,ln,se,ra,vo,bend);}

    /** The bass line over 16 bars (steps from bar 0), as events. */
    private java.util.ArrayList<Ev> bassLine(){
        java.util.ArrayList<Ev> e=new java.util.ArrayList<>();
        if(theme==HELL){
            // F phrygian: F, Gb, Ab, Bb, C, Db, Eb. A: heavy slow wobbles; B: chopped growls and yoi's
            float[] rootA={0,0,1,-4};   // per two bars: F F Gb Db
            for(int ph=0;ph<4;ph++){int b=ph*32;float r=rootA[ph];
                e.add(ev(b,6,r,2,WOB));e.add(ev(b+6,2,r,4,WOB));e.add(ev(b+8,4,r+1,3,WOB));e.add(ev(b+12,4,r,1,WOB,-2));
                e.add(ev(b+16,4,r-2,2,WOB));e.add(ev(b+20,2,r-2,4,WOB));e.add(ev(b+22,2,r+3,8,GROWL));e.add(ev(b+24,8,r-4,.5f,WOB,-12));}
            float[] rootB={0,1,0,-4};
            for(int ph=0;ph<4;ph++){int b=128+ph*32;float r=rootB[ph];
                e.add(ev(b,3,r,4,GROWL));e.add(ev(b+4,4,r+12,1,YOI));e.add(ev(b+8,2,r+1,8,WOB));e.add(ev(b+10,2,r,6,GROWL));e.add(ev(b+12,4,r-2,3,WOB));
                e.add(ev(b+16,2,r,8,GROWL));e.add(ev(b+18,2,r+3,4,WOB));e.add(ev(b+20,4,r+12,1,YOI,-5));e.add(ev(b+24,4,r-4,2,WOB));
                if(ph==3)e.add(ev(b+28,4,r+7,16,GROWL,-24));else e.add(ev(b+28,4,r-5,6,GROWL));}
        }else{
            // D minor: D E F G A Bb C. A: FM wobbles with laser stabs; B: talking FM bass, faster
            float[] rootA={0,0,-2,-4};   // D D C Bb
            for(int ph=0;ph<4;ph++){int b=ph*32;float r=rootA[ph];
                e.add(ev(b,4,r,2,FMB));e.add(ev(b+4,2,r,4,FMB));e.add(ev(b+6,2,r+12,0,LASER));e.add(ev(b+8,4,r+3,3,FMB));e.add(ev(b+12,4,r,1,FMB,-3));
                e.add(ev(b+16,6,r-2,2,FMB));e.add(ev(b+22,2,r+12,0,LASER));e.add(ev(b+24,4,r+5,4,FMB));e.add(ev(b+28,4,r,8,FMB,-12));}
            float[] rootB={0,3,-2,-4};
            for(int ph=0;ph<4;ph++){int b=128+ph*32;float r=rootB[ph];
                e.add(ev(b,2,r,8,FMB));e.add(ev(b+2,2,r+12,0,LASER));e.add(ev(b+4,4,r+7,1,YOI));e.add(ev(b+8,2,r,6,FMB));e.add(ev(b+10,2,r+3,8,FMB));e.add(ev(b+12,4,r-2,3,FMB));
                e.add(ev(b+16,3,r,4,FMB));e.add(ev(b+19,1,r+12,0,LASER));e.add(ev(b+20,4,r+12,1,YOI,-7));e.add(ev(b+24,4,r+5,6,FMB));
                if(ph==3)e.add(ev(b+28,4,r+12,16,FMB,-24));else e.add(ev(b+28,4,r-5,4,FMB));}
        }
        return e;
    }
    /** Renders one bass event into the bass bus (mono) and the sub bus. */
    private void bassEvent(float[] bus,float[] sub,Ev e,float rootMidi){
        int at=e.step*STEP,n=e.len*STEP;float beat=STEP*4f;
        Saw s1=new Saw(),s2=new Saw(),s3=new Saw();Sqr q=new Sqr();Svf lp=new Svf(),lp2=new Svf();Svf[] fm={new Svf(),new Svf(),new Svf()};double sp=0,cp=0,mp=0;
        float rel=Math.min(n*.15f,RATE*.03f);
        for(int i=0;i<n+RATE*.03f&&at+i<bus.length;i++){
            float env=i<120?i/120f:i>n-rel?Math.max(0,(n-i)/rel):1;if(i>=n)env=0;if(env<=0&&i>=n)break;
            float prog=i/(float)n;float semi=e.semi+e.bend*prog*prog;float f=noteHz(rootMidi+semi);
            float lfoPh=e.rate*i/beat;float lfo=.5f-.5f*(float)Math.cos(2*Math.PI*lfoPh);
            float v=0;
            switch(e.voice){
                case WOB:{float src=s1.next(f)*.55f+s2.next(f*1.006f)*.55f+q.next(f*.5f*2,.5f)*.35f+s3.next(f*2.003f)*.25f;
                    float cut=theme==HELL?70+3200*lfo*lfo:90+2600*lfo*lfo;lp.set(cut,4.5f);lp2.set(cut*1.4f,1.2f);
                    v=lp2.run(lp.run(src));v=softClip(v*3.2f)*.55f;break;}
                case GROWL:{float vib=1+.012f*(float)Math.sin(2*Math.PI*i*6.0/RATE);float src=s1.next(f*2*vib)*.6f+s2.next(f*2.01f)*.5f+s3.next(f)*.4f;
                    src=softClip(src*2.5f);
                    float[][] vow={{400,750,2400},{650,1080,2650},{300,870,2240},{730,1090,2440}};float vp=lfo*(vow.length-1);int a=(int)Math.min(vow.length-2,vp);float t=vp-a;
                    v=0;for(int k=0;k<3;k++){float fc=vow[a][k]*(1-t)+vow[a+1][k]*t;fm[k].set(fc,k==0?6:9);fm[k].run(src);v+=fm[k].band*(k==0?1.1f:.8f);}
                    lp.set(5200,.7f);v=softClip(lp.run(v)*2.6f)*.5f;break;}
                case YOI:{float src=s1.next(f*vibr(i))*.6f+s2.next(f*1.01f)*.6f;src=softClip(src*2);float t=Math.min(1,prog*1.6f);
                    float[] o={400,750,2400},iV={280,2250,2900};v=0;for(int k=0;k<3;k++){float fc=o[k]+(iV[k]-o[k])*t;fm[k].set(fc,8);fm[k].run(src);v+=fm[k].band;}
                    v=softClip(v*2.2f)*.5f;break;}
                case FMB:{sp+=f/RATE;mp+=f*2/RATE;float idx=.6f+7*lfo;float m=(float)Math.sin(2*Math.PI*mp)*idx;float car=(float)Math.sin(2*Math.PI*sp+m);
                    float sq=q.next(f,.25f+.2f*lfo)*.35f;float src=car*.8f+sq;lp.set(200+5200*lfo,2.5f);v=softClip(lp.run(src)*2.4f);
                    v=Math.round(v*24)/24f*.5f;break;}   // a little bit reduction
                case LASER:{float lf=4200*(float)Math.exp(-prog*6)+180;cp+=lf/RATE;float sq=(float)Math.sin(2*Math.PI*cp+2.5f*Math.sin(2*Math.PI*cp*.5));v=sq*(float)Math.exp(-prog*3)*.45f;break;}
                default:break;
            }
            bus[at+i]+=v*env;
            if(e.voice!=LASER){double sf=Math.max(38,noteHz(rootMidi+e.semi+Math.min(0,e.bend)*prog*prog));cpSub+=sf/RATE;sub[at+i]+=(float)Math.sin(2*Math.PI*cpSub)*env*.36f;}
        }
    }
    private double cpSub;
    private float vibr(int i){return 1+.01f*(float)Math.sin(2*Math.PI*i*5.5/RATE);}

    // ================================================================== tonal colour
    /** A bell or pluck of inharmonic partials (horror bells, sci-fi chimes). */
    private void bell(Track t,Track send,int at,float hz,float vel,float decay,float p){
        float[] ratios=theme==HELL?new float[]{1,2.76f,5.40f,8.93f,.5f}:new float[]{1,2,3.01f,4.2f,.5f};float[] amps={1,.55f,.35f,.2f,.3f};
        int n=Math.round(RATE*decay*1.4f);
        for(int i=0;i<n;i++){float s=i/(float)RATE;float v=0;for(int k=0;k<ratios.length;k++)v+=(float)Math.sin(2*Math.PI*hz*ratios[k]*s)*amps[k]*(float)Math.exp(-s*(1.2f+k*1.1f)/decay);
            v*=vel*.12f*(i<60?i/60f:1);pan(t,at+i,v,p);pan(send,at+i,v*1.4f,p);}
    }
    /** A slow choir/pad chord (formant-filtered saws), added for `len` samples with soft edges. */
    private void pad(Track t,Track send,int at,int len,float[] midi,float vel,float bright){
        for(int vi=0;vi<midi.length;vi++){Saw a=new Saw(),b=new Saw();Svf f1=new Svf(),f2=new Svf();float hz=noteHz(midi[vi]);float p=vi%2==0?-.5f:.5f;
            f1.set(theme==HELL?600:420+bright*900,4);f2.set(theme==HELL?1050:1300+bright*1200,6);
            for(int i=0;i<len;i++){float edge=Math.min(1,Math.min(i,len-i)/(RATE*.8f));float vib=1+.004f*(float)Math.sin(2*Math.PI*(i/(float)RATE)*(4.6f+vi*.3f));
                float src=a.next(hz*vib)*.5f+b.next(hz*1.005f)*.5f;f1.run(src);f2.run(src);float v=(f1.band+f2.band*.7f)*vel*.09f*edge;pan(t,at+i,v,p);pan(send,at+i,v,p);}}
    }
    /** A rising noise sweep into `end`, `len` samples long. */
    private void riser(Track t,Track send,int end,int len,float vel){
        Svf bp=new Svf();int at=end-len;
        for(int i=0;i<len;i++){float k=i/(float)len;bp.set(300+9000*k*k,3);bp.run(noise());float v=bp.band*k*k*vel*.35f;pan(t,at+i,v,(float)Math.sin(i*.0003f)*.6f);pan(send,at+i,v,0);}
    }

    // ================================================================== the combat loop
    Track combat(){
        int len=BARS*BAR,tail=RATE*3;Track mix=new Track(len+tail),send=new Track(len+tail);float[] duck=new float[len+tail];java.util.Arrays.fill(duck,1);
        float[] bass=new float[len+tail],sub=new float[len+tail];
        float root=theme==HELL?29:26;   // F1 / D1 (MIDI)
        // drums: half-time, kick on 1 with pickups, snare on 3; hats in eighths, rolls into each four-bar line
        for(int bar=0;bar<BARS;bar++){int b0=bar*BAR;boolean secB=bar>=8;
            kick(mix,b0,1f,duck);
            if(bar%2==1)kick(mix,b0+10*STEP,.85f,duck);
            if(secB&&bar%2==0)kick(mix,b0+7*STEP,.7f,duck);
            if(secB)kick(mix,b0+14*STEP,.75f,duck);
            snare(mix,send,b0+8*STEP,1f);
            if(bar%4==3)snare(mix,send,b0+14*STEP,.45f);
            for(int s=0;s<16;s+=2){boolean off=s%4==2;hat(mix,b0+s*STEP+(off?STEP/7:0),off?.9f:.55f,s==6&&secB,off?.25f:-.2f);}
            if(secB)for(int s=1;s<16;s+=4)hat(mix,b0+s*STEP,.35f,false,.4f);
            if(bar%4==3){for(int k=0;k<8;k++)hat(mix,b0+12*STEP+k*STEP/2,.4f+k*.06f,false,-.3f+k*.08f);}
            if(bar==7||bar==15){for(int k=0;k<6;k++)snare(mix,send,b0+12*STEP+k*STEP*2/3,.35f+k*.1f);tom(mix,send,b0+12*STEP,.6f,theme==HELL?90:120);tom(mix,send,b0+14*STEP,.6f,theme==HELL?70:95);}
            if(bar==0||bar==8)crash(mix,send,b0,1f,2.2f);
        }
        riser(mix,send,8*BAR,BAR*2,1f);riser(mix,send,16*BAR,BAR,.8f);
        // bass
        cpSub=0;for(Ev e:bassLine())bassEvent(bass,sub,e,root);
        Svf subLp=new Svf();subLp.set(110,.7f);
        for(int i=0;i<len+tail;i++){float d=duck[i];float b=bass[i]*d*1.15f,s=subLp.run(sub[i])*d*.9f;mix.l[i]+=b+s;mix.r[i]+=b+s;}
        // colour: horror bells and choir; sci-fi chimes and arpeggio
        if(theme==HELL){
            for(int bar=0;bar<BARS;bar+=2){bell(mix,send,bar*BAR,noteHz(77),.9f,2.5f,-.4f);bell(mix,send,bar*BAR+STEP*3,noteHz(78),.6f,2.2f,.5f);}
            float[][] ch={{53,56,60,61},{53,56,60,61},{54,57,61,64},{49,53,56,60}};
            for(int k=0;k<8;k++){float[] c=ch[k%4];int at=k*2*BAR;float[] dk=new float[c.length];for(int j=0;j<c.length;j++)dk[j]=c[j];pad(mix,send,at,2*BAR,dk,k>=4?1.1f:.8f,0);}
        }else{
            float[] arp={62,65,69,74,72,69,65,64};float[][] roots={{0},{0},{-2},{-4}};
            for(int bar=0;bar<BARS;bar++){float tr=roots[(bar/2)%4][0];for(int s=0;s<16;s++){if(bar<8&&s%2==1)continue;
                float hz=noteHz(arp[(s+bar*3)%arp.length]+tr+12);pluck(mix,send,bar*BAR+s*STEP,hz,bar>=8?.8f:.6f,(s%4-1.5f)*.3f,duck);}}
            float[][] ch={{50,53,57,64},{50,53,57,64},{48,52,55,62},{46,50,53,57}};
            for(int k=0;k<8;k++){float[] c=ch[k%4];pad(mix,send,k*2*BAR,2*BAR,c,k>=4?.9f:.7f,.6f);}
        }
        // reverb, then fold the tail back onto the start (seamless loop), master
        Verb v=new Verb(theme==HELL?.92f:.75f,.35f);v.run(send.l,send.r,mix.l,mix.r,theme==HELL?1.3f:1f);
        return master(fold(mix,len),.92f);
    }
    /** A short square pluck with a ping-pong delay (the sci-fi arpeggio). */
    private void pluck(Track t,Track send,int at,float hz,float vel,float p,float[] duck){
        int n=Math.round(RATE*.25f);Sqr q=new Sqr();Svf lp=new Svf();
        for(int i=0;i<n;i++){float s=i/(float)RATE;lp.set(600+5000*(float)Math.exp(-s*20),2);float v=lp.run(q.next(hz,.3f))*(float)Math.exp(-s*14)*vel*.11f;float d=at+i<duck.length?.5f+.5f*duck[at+i]:1;
            pan(t,at+i,v*d,p);pan(t,at+i+STEP*3,v*d*.45f,-p);pan(t,at+i+STEP*6,v*d*.2f,p);pan(send,at+i,v*.5f,0);}
    }

    /** The drop's impact: a sub boom, a distorted hit, a crash and (in Hell) a roar. ~3 s. */
    Track impact(){
        int n=RATE*3;Track t=new Track(n),send=new Track(n);
        double ph=0;for(int i=0;i<n;i++){float s=i/(float)RATE;ph+=(28+70*Math.exp(-s*3))/RATE;float v=softClip((float)Math.sin(2*Math.PI*ph)*2.2f)*(float)Math.exp(-s*1.3f)*.7f;pan(t,i,v,0);}
        kick(t,0,1.1f,null);crash(t,send,0,1.2f,2.6f);snare(t,send,0,.8f);
        if(theme==HELL){Svf[] f={new Svf(),new Svf(),new Svf()};float[] fr={600,1000,2500};
            for(int i=0;i<RATE*1.6f;i++){float s=i/(float)RATE;float nz=noise();float v=0;for(int k=0;k<3;k++){f[k].set(fr[k]*(1-.35f*s),5);f[k].run(nz);v+=f[k].band;}
                v=softClip(v*3)*(float)(Math.min(1,s*12)*Math.exp(-s*1.6f))*.4f;pan(t,i,v,0);pan(send,i,v,0);}}
        else{for(int i=0;i<RATE*1.2f;i++){float s=i/(float)RATE;float f=1800*(float)Math.exp(-s*3.5f)+60;float v=(float)Math.sin(2*Math.PI*f*s*(1+.5f*Math.sin(s*40)))*(float)Math.exp(-s*2.2f)*.3f;pan(t,i,v,.3f);pan(send,i,v,-.3f);}}
        Verb v=new Verb(.95f,.3f);v.run(send.l,send.r,t.l,t.r,1.4f);
        return master(t,.95f);
    }

    // ================================================================== ambience
    /** A seamless ambient bed (~28 s): drones, wind, distant moans and metal in Hell; hum, vents and machines on the station. */
    Track ambient(){
        int len=RATE*28,xf=RATE*4;Track t=new Track(len+xf),send=new Track(len+xf);int n=len+xf;
        if(theme==HELL){
            // the drone: detuned saws on F and C, darkly filtered, breathing
            float[] notes={29,36,41,29.07f};Saw[] o=new Saw[notes.length];for(int k=0;k<o.length;k++)o[k]=new Saw();Svf lp=new Svf(),lpR=new Svf();
            Svf rum=new Svf();rum.set(70,.7f);Svf wind=new Svf(),windR=new Svf();float brown=0;
            for(int i=0;i<n;i++){float s=i/(float)RATE;float v=0;for(int k=0;k<o.length;k++)v+=o[k].next(noteHz(notes[k])*(1+.002f*(float)Math.sin(s*.31f+k)))*.3f;
                float cut=240+160*(float)Math.sin(2*Math.PI*s/14)+80*(float)Math.sin(2*Math.PI*s/5.3);lp.set(cut,1.6f);lpR.set(cut*1.1f,1.6f);
                float dl=lp.run(v),dr=lpR.run(v);brown=(brown+noise()*.02f)*.998f;float r=rum.run(brown)*1.2f;
                float w=noise();wind.set(380+260*(float)Math.sin(2*Math.PI*s/9.5),2.2f);windR.set(420+260*(float)Math.sin(2*Math.PI*s/11.7+1),2.2f);wind.run(w);windR.run(w*.9f+noise()*.1f);
                float wa=.5f+.5f*(float)Math.sin(2*Math.PI*s/7);
                t.l[i]+=dl*.5f+r+wind.band*.17f*wa;t.r[i]+=dr*.5f+r+windR.band*.17f*wa;}
            // distant moans and chains, a far-off drum
            for(int k=0;k<5;k++){int at=Math.round(RATE*(2+k*5.4f+rnd(0,1.5f)));moan(t,send,at,rnd(-.8f,.8f),rnd(.5f,.9f));}
            for(int k=0;k<9;k++){int at=Math.round(RATE*rnd(0,len/(float)RATE));bellAmb(t,send,at,rnd(700,1600),rnd(.25f,.6f),rnd(-1,1));}
            for(int k=0;k<4;k++){int at=Math.round(RATE*(3.5f+k*7));tom(t,send,at,.35f,48);tom(t,send,at+RATE/3,.25f,44);}
        }else{
            // the hum of a dead station: mains hum, air handling, a slow pad, machinery and terminal blips
            Svf vent=new Svf(),ventR=new Svf();Svf rum=new Svf();rum.set(90,.7f);float brown=0;
            for(int i=0;i<n;i++){float s=i/(float)RATE;float hum=(float)(Math.sin(2*Math.PI*50*s)*.5+Math.sin(2*Math.PI*100.3*s)*.25+Math.sin(2*Math.PI*150*s)*.12+Math.sin(2*Math.PI*49.6*s)*.3+Math.sin(2*Math.PI*200.4*s)*.12+Math.sin(2*Math.PI*250*s)*.08)*.06f;
                float nz=noise();vent.set(1100+200*(float)Math.sin(2*Math.PI*s/13),1.1f);ventR.set(1300+200*(float)Math.sin(2*Math.PI*s/17),1.1f);vent.run(nz);ventR.run(noise());
                brown=(brown+noise()*.02f)*.998f;float r=rum.run(brown)*1f;
                t.l[i]+=hum+vent.band*.075f+r;t.r[i]+=hum*.9f+ventR.band*.075f+r;}
            pad(t,send,0,n,new float[]{50,57,64,65},.55f,.2f);
            for(int k=0;k<6;k++){int at=Math.round(RATE*(1.5f+k*4.6f+rnd(0,1)));groan(t,send,at,rnd(-.7f,.7f));}
            for(int k=0;k<14;k++){int at=Math.round(RATE*rnd(0,len/(float)RATE));blip(t,send,at,rnd(-1,1));}
            for(int k=0;k<6;k++){int at=Math.round(RATE*rnd(0,len/(float)RATE));bellAmb(t,send,at,rnd(300,900),rnd(.2f,.4f),rnd(-1,1));}
        }
        Verb v=new Verb(.97f,.25f);v.run(send.l,send.r,t.l,t.r,1.6f);
        // crossfade the end into the start: the loop has no seam
        Track out=new Track(len);
        for(int i=0;i<len;i++){out.l[i]=t.l[i];out.r[i]=t.r[i];}
        for(int i=0;i<xf;i++){float k=i/(float)xf;float a=(float)Math.sin(k*Math.PI/2),b=(float)Math.cos(k*Math.PI/2);out.l[i]=t.l[i]*a+t.l[len+i]*b;out.r[i]=t.r[i]*a+t.r[len+i]*b;}
        return master(out,.6f);
    }
    private void moan(Track t,Track send,int at,float p,float vel){
        int n=RATE*4;Saw a=new Saw(),b=new Saw();Svf[] f={new Svf(),new Svf()};
        for(int i=0;i<n&&at+i<t.l.length;i++){float k=i/(float)n;float hz=noteHz(45-3*k+.3f*(float)Math.sin(i*2*Math.PI*4.5/RATE));float src=a.next(hz)*.6f+b.next(hz*1.01f)*.5f;
            float o=450-150*k,u=800-400*k;f[0].set(o,7);f[1].set(u,9);f[0].run(src);f[1].run(src);float env=(float)Math.sin(Math.PI*k);env*=env;
            float v=(f[0].band+f[1].band*.6f)*env*vel*.32f;pan(t,at+i,v*.5f,p);pan(send,at+i,v*1.5f,p);}
    }
    private void bellAmb(Track t,Track send,int at,float hz,float vel,float p){
        int n=RATE*3;float[] r={1,2.76f,5.4f,8.93f};
        for(int i=0;i<n&&at+i<t.l.length;i++){float s=i/(float)RATE;float v=0;for(int k=0;k<4;k++)v+=(float)Math.sin(2*Math.PI*hz*r[k]*s)*(float)Math.exp(-s*(2+k*2.5f))/(k+1);v*=vel*.09f;pan(t,at+i,v*.3f,p);pan(send,at+i,v*1.6f,p);}
    }
    private void groan(Track t,Track send,int at,float p){
        int n=RATE*3;Saw a=new Saw();Svf f=new Svf();
        for(int i=0;i<n&&at+i<t.l.length;i++){float k=i/(float)n;float hz=38+14*(float)Math.sin(Math.PI*k);f.set(220+500*(float)Math.sin(Math.PI*k),12);f.run(a.next(hz)+noise()*.3f);
            float env=(float)Math.sin(Math.PI*k);float v=f.band*env*.07f;pan(t,at+i,v*.5f,p);pan(send,at+i,v*1.5f,p);}
    }
    private void blip(Track t,Track send,int at,float p){
        float hz=rnd(900,2400);int n=Math.round(RATE*.09f);int reps=1+rng.nextInt(3);
        for(int r=0;r<reps;r++)for(int i=0;i<n;i++){float s=i/(float)RATE;float v=(float)Math.sin(2*Math.PI*hz*(r==1?1.5f:1)*s)*(float)Math.exp(-s*30)*.04f;int j=at+r*Math.round(RATE*.12f)+i;pan(t,j,v,p);pan(send,j,v*.8f,p);}
    }

    // ================================================================== mastering
    private static Track fold(Track t,int len){Track o=new Track(len);for(int i=0;i<t.l.length;i++){int j=i%len;o.l[j]+=t.l[i];o.r[j]+=t.r[i];}return o;}
    /** Gentle high-pass, normalize to `level`, soft clip. */
    private static Track master(Track t,float level){
        float hl=0,hr=0,pl=0,pr=0,a=.99602f;float peak=1e-6f;   // a 28 Hz high-pass: nothing below what speakers can play
        int n=t.l.length,warm=Math.min(n,RATE*2);
        for(int i=n-warm;i<n;i++){float x=t.l[i],y=t.r[i];hl=a*(hl+x-pl);pl=x;hr=a*(hr+y-pr);pr=y;}   // settle the filter on the loop's end, so its start follows on without a click
        for(int i=0;i<n;i++){float x=t.l[i],y=t.r[i];hl=a*(hl+x-pl);pl=x;hr=a*(hr+y-pr);pr=y;t.l[i]=hl;t.r[i]=hr;}
        for(int i=0;i<t.l.length;i++)peak=Math.max(peak,Math.max(Math.abs(t.l[i]),Math.abs(t.r[i])));
        float g=level/peak*1.35f;
        for(int i=0;i<t.l.length;i++){t.l[i]=softClip(t.l[i]*g)*level/(float)Math.tanh(1.35f);t.r[i]=softClip(t.r[i]*g)*level/(float)Math.tanh(1.35f);}
        return t;
    }
}
