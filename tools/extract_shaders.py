#!/usr/bin/env python3
"""Pulls the GLSL strings out of Shaders.java and writes them as .vert/.frag files for glslangValidator (CI check)."""
import re,sys,os,codecs
src=open(sys.argv[1]).read();out=sys.argv[2];os.makedirs(out,exist_ok=True)
for m in re.finditer(r'static final String (\w+)=(.*?);\n',src,re.S):
    name,body=m.group(1),m.group(2)
    parts=re.findall(r'"((?:[^"\\]|\\.)*)"',body)
    text=codecs.decode(''.join(parts),'unicode_escape')
    ext='.vert' if name.startswith('VS') else '.frag'
    open(os.path.join(out,name+ext),'w').write(text);print(name+ext)
