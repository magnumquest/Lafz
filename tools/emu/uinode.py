#!/usr/bin/env python3
# Helper for tools/emu/emu.sh. usage: uinode.py <dump.xml> <list|center> <regex>
#   list:   print matching nodes (text, content-desc, class, bounds)
#   center: print "x y" of the centre of the first matching node with bounds; exit 1 if none
import re
import sys

path, mode, pat = sys.argv[1], sys.argv[2], re.compile(sys.argv[3], re.I)
xml = open(path, encoding="utf-8").read()
for m in re.finditer(r"<node [^>]*>", xml):
    node = m.group(0)
    if not pat.search(node):
        continue
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    if mode == "list":
        print(" ".join(re.findall(r'(?:text|content-desc|class|bounds)="[^"]*"', node)))
    elif b:
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        sys.exit(0)
if mode == "center":
    sys.exit(1)
