import subprocess, sys
sys.path.insert(0, '.')
from deva2roman import sentence
from evalcommon import report
src, label = sys.argv[1], sys.argv[2]
rows = [l.rstrip('\n').split('\t') for l in open(src, encoding='utf-8')]
refs = [r[0] for r in rows]; hyps = [r[1] if len(r) > 1 else '' for r in rows]
roman = [sentence(h) for h in hyps]
p = subprocess.run(['java', '-Dstdout.encoding=UTF-8', '-cp', '../java/out', 'Batch', '/home/thomas/AI_Works/PersonalBoard/app/src/main/assets/urdu/urdu_translit.bin'],
                   input='\n'.join(roman) + '\n', capture_output=True, text=True, encoding='utf-8')
urdu = p.stdout.rstrip('\n').split('\n')
report(label, refs, urdu, 'ur', 1.0, 1.0)
