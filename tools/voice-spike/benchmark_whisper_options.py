#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
import argparse,subprocess,pathlib,time,json,math,wave,re

parser=argparse.ArgumentParser(description="Screen existing English/Urdu Whisper models for context/fallback speed and character accuracy. Local CPU timings include model loading; use phone benchmarks before changing defaults.")
parser.add_argument("--models",type=pathlib.Path,required=True,help="Lafz_voice_files folder containing model folders and tests/")
parser.add_argument("--cli",required=True,help="Path to whisper-cli")
parser.add_argument("--output",type=pathlib.Path,required=True)
parser.add_argument("--threads",default="4")
parser.add_argument("--count",type=int,default=8)
args=parser.parse_args()
base=args.models
cli=args.cli
results=[]
def norm(text):return ''.join(c for c in text.lower() if c.isalnum())
def distance(a,b):
 prev=list(range(len(b)+1))
 for i,x in enumerate(a,1):
  row=[i]
  for j,y in enumerate(b,1):row.append(min(row[-1]+1,prev[j]+1,prev[j-1]+(x!=y)))
  prev=row
 return prev[-1]
for lang,model in [('en','whisper_en_base_q5_1/ggml-base.en-q5_1.bin'),('ur','whisper_ur_small_q5_1/ggml-small-urdu-q5_1.bin')]:
 for i in range(1,args.count+1):
  clip=base/'tests'/f'{lang}_{i:02}.wav'
  with wave.open(str(clip)) as w: duration=w.getnframes()/w.getframerate()
  ref=(base/'tests'/f'{lang}_{i:02}.txt').read_text()
  context=min(1500,max(768,math.ceil((duration+2)*50/64)*64))
  for label,extra in [('default',[]),('short_context',['-ac',str(context)]),('no_fallback',['-nf'])]:
   t=time.monotonic()
   p=subprocess.run([cli,'-m',str(base/model),'-f',str(clip),'-l',lang,'-t',args.threads,'-bs','1','-bo','1','-nt','-np','-ng','-mc','0',*extra],capture_output=True,text=True,check=True)
   text=p.stdout.strip()
   result=dict(language=lang,clip=i,variant=label,duration=duration,wall_s=time.monotonic()-t,text=text,errors=distance(norm(ref),norm(text)),chars=len(norm(ref)))
   timing=re.search(r'total time\s*=\s*([\d.]+) ms',p.stderr)
   if timing:result['native_s']=float(timing.group(1))/1000
   results.append(result)
  print(lang,i,'done',flush=True)
 args.output.write_text(json.dumps(results,ensure_ascii=False,indent=2))
 for variant in ['default','short_context','no_fallback']:
  rs=[r for r in results if r['language']==lang and r['variant']==variant]
  print(lang,variant,'CER',round(sum(r['errors'] for r in rs)/sum(r['chars'] for r in rs),4),'seconds',round(sum(r['wall_s'] for r in rs),2),flush=True)
