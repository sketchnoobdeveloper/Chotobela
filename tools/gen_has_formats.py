#!/usr/bin/env python3
"""Generate has_formats.h for MAME's src/lib/formats/all.cpp.

Mirrors what upstream's genie build generates: one HAS_FORMATS_<NAME>
define per format header enumerated by all.cpp.
"""
import re
import sys

def main(all_cpp_path, out_path):
    src = open(all_cpp_path).read()
    incs = re.findall(r'^#include\s+"([A-Za-z0-9_]+)\.h"', src, re.M)
    skip = {"all", "has_formats"}
    lines = ["// Generated — mirrors upstream genie has_formats generation"]
    seen = set()
    for h in incs:
        if h in skip or h in seen:
            continue
        seen.add(h)
        lines.append(f"#define HAS_FORMATS_{h.upper()}")
    open(out_path, "w").write("\n".join(lines) + "\n")
    print(f"has_formats.h: {len(seen)} formats")

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
