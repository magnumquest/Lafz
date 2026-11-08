#!/usr/bin/env python3
"""Build Lafz's English STEM word list from NLM 2026 MeSH descriptors."""

from __future__ import annotations

import argparse
import re
import urllib.request
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
URL = "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2026.zip"
DEST = ROOT / "app/src/main/assets/technical_words/en.txt"
EXTRA = Path(__file__).with_name("engineering_physics.txt")
WORD = re.compile(r"^[A-Za-z][A-Za-z0-9'-]*$")
STEM_TREE_ROOTS = tuple("ABCDEFGHJL")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mesh-zip", type=Path, help="use an already downloaded NLM MeSH ZIP")
    args = parser.parse_args()
    archive = args.mesh_zip
    if archive is None:
        archive = ROOT / "build/technical-terms/desc2026.zip"
        archive.parent.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(URL, archive)

    words: set[str] = set()
    with zipfile.ZipFile(archive) as zf, zf.open("desc2026.xml") as xml:
        for _, record in ET.iterparse(xml, events=("end",)):
            if record.tag != "DescriptorRecord":
                continue
            tree_numbers = (e.text or "" for e in record.findall("./TreeNumberList/TreeNumber"))
            if any(tree.startswith(STEM_TREE_ROOTS) for tree in tree_numbers):
                name = record.findtext("./DescriptorName/String", default="").strip()
                if WORD.fullmatch(name) and len(name) >= 4 and sum(c.isalpha() for c in name) >= 4:
                    words.add(name.lower())
            record.clear()

    words.update(
        line.strip().lower()
        for line in EXTRA.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.startswith("#")
    )
    DEST.parent.mkdir(parents=True, exist_ok=True)
    DEST.write_text("\n".join(sorted(words)) + "\n", encoding="utf-8")
    print(f"wrote {len(words)} English STEM terms to {DEST.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
