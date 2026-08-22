# Vendored: MAME 0.289

Upstream: https://github.com/mamedev/mame
Tag:      mame0289 (latest stable at time of copy)
Source:   official tagged tarball via codeload.github.com (unmodified "same-to-same")
License:  GPL-2.0-or-later — see COPYING inside this directory

## Why the source is not committed here

The tree is ~31,400 files / ~800 MB. Committing it would bloat this repository
and slow every clone. Instead:

- This directory exists locally for adapter development.
- CI and fresh checkouts reproduce it exactly:

```bash
curl -L https://codeload.github.com/mamedev/mame/tar.gz/refs/tags/mame0289 \
  | tar -xz --strip-components=1 -C third_party/mame
```

- A future step may convert this path to a pinned git submodule if desired;
  the recorded tag above guarantees reproducibility either way.

## License obligations

MAME is GPL-2.0+. If/when Chotobela ships binaries derived from these sources,
the distributed app must be licensed under a GPL-2.0-compatible license and
must be accompanied by corresponding source (the submodule/tag reference above
satisfies the written-offer requirement when combined with the pinned tag).
All upstream copyright notices remain intact in this tree.
