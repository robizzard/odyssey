#!/bin/bash

# run this script from your audio root directory

python3 \
    ./tools/generate-directory-index.py . \
    -o ./odyssey-directory-index.tsv
