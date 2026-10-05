#!/usr/bin/env python3
"""Tulis segitiga & UV jaring wajah MediaPipe ke res/raw/zm_face_mesh.bin (media-effects).

Format (little-endian): int jumlah titik n, int jumlah indeks m, float uv[2n] (v ke bawah),
short indeks[m] (segitiga menghadap keluar), float posisi[3n] (kepala standar, cm; +Y atas, +Z depan).

    python3 tools/make_face_mesh_bin.py media/media-effects/src/main/res/raw/zm_face_mesh.bin
"""
import struct
import sys

import numpy as np

import face_mesh

pos, uv, tris = face_mesh.load()
with open(sys.argv[1], "wb") as f:
    f.write(struct.pack("<ii", len(uv), tris.size))
    f.write(uv.astype("<f4").tobytes())
    f.write(tris.astype("<i2").tobytes())
    f.write(pos.astype("<f4").tobytes())
print(sys.argv[1], len(uv), "titik,", len(tris), "segitiga")
