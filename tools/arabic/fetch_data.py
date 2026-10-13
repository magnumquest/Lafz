#!/usr/bin/env python3
"""Download the raw Arabic sources into data/Arabic/raw (idempotent). Needs internet; stdlib only.
   python3 -I tools/arabic/fetch_data.py
"""
import io, os, sys, urllib.request, zipfile

RAW = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'data', 'Arabic', 'raw')
TANZIL = 'https://tanzil.net/pub/download/index.php?quranType=%s&outType=txt-2&agree=true'
FILES = {
    'tanzil_quran_simple.txt': TANZIL % 'simple',        # modern (imla'i) spelling, with harakat
    'tanzil_quran_uthmani.txt': TANZIL % 'uthmani',      # Uthmani rasm, with harakat
    'tanzil_transliteration_en.txt': 'https://tanzil.net/trans/en.transliteration',
}
HADITH_ZIP = 'https://github.com/AhmedBaset/hadith-json/archive/refs/heads/main.zip'


def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'lafz-data/1.0'})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def main():
    os.makedirs(RAW, exist_ok=True)
    for name, url in FILES.items():
        p = os.path.join(RAW, name)
        if not os.path.exists(p):
            print('fetch', name); open(p, 'wb').write(get(url))
    hd = os.path.join(RAW, 'hadith_json')
    if not os.path.isdir(hd):
        print('fetch hadith-json (~70 MB)')
        z = zipfile.ZipFile(io.BytesIO(get(HADITH_ZIP)))
        os.makedirs(hd)
        for n in z.namelist():
            if '/db/by_book/' in n and n.endswith('.json'):
                open(os.path.join(hd, os.path.basename(n)), 'wb').write(z.read(n))
    print('raw data in', os.path.normpath(RAW))


if __name__ == '__main__':
    sys.exit(main())
