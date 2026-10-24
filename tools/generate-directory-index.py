#!/usr/bin/env python3
"""Generate an Odyssey V1 offline folder index (independent of SD mount path)."""
import argparse
import base64
import os
from pathlib import Path

EXTENSIONS = set('3gp aac flac imy m3u m4a m4b mid mkv mp3 mp4 mxmf ogg oga opus ota pls rtttl rtx ts wav wma xmf'.split())
MAGIC = 'ODYSSEY_INDEX_V1'

def generate(root: Path, output: Path):
    root = root.resolve(strict=True)
    if not root.is_dir():
        raise ValueError('Root must be a directory')
    total = [0, 0]
    with output.open('w', encoding='utf-8', newline='\n') as dst:
        dst.write(MAGIC + '\n')
        def walk(folder):
            # Match Odyssey's .nomedia behavior: this directory is suppressed.
            if (folder / '.nomedia').exists():
                return
            with os.scandir(folder) as it:
                entries = sorted(it, key=lambda e: (not e.is_dir(follow_symlinks=False), e.name.casefold()))
            for ent in entries:
                if ent.is_symlink():
                    continue
                is_dir = ent.is_dir(follow_symlinks=False)
                if not (is_dir or (ent.is_file(follow_symlinks=False) and ent.name.rpartition('.')[-1].lower() in EXTENSIONS)):
                    continue
                path = Path(ent.path)
                relative = path.relative_to(root).as_posix()
                encoded = base64.urlsafe_b64encode(relative.encode('utf-8')).decode('ascii')
                dst.write(('D' if is_dir else 'F') + '\t' + encoded + '\n')
                total[0 if is_dir else 1] += 1
                if is_dir:
                    walk(path)
        walk(root)
    print(f'Wrote {output}: {total[0]} directories, {total[1]} media files')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('music_root', type=Path, help='Music directory on the mounted SD card')
    parser.add_argument('-o', '--output', type=Path, default=Path('odyssey-directory-index.tsv'), help='Output file (default: current working directory)')
    args = parser.parse_args()
    generate(args.music_root, args.output)
