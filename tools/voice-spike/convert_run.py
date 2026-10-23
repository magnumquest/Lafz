import sys, runpy
sys.path.insert(0, '/home/thomas/AI_Works/voice-spike/scripts')
import fakeflash
script = sys.argv[1]; sys.argv = sys.argv[1:]
runpy.run_path(script, run_name='__main__')
