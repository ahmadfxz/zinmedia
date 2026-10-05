#!/usr/bin/env python3
"""Gambar panduan peta UV wajah MediaPipe (PNG transparan 1024×1024) untuk membuat efek gambar.

Garis tipis = jaring wajah; garis tebal = mata, alis, hidung, bibir, oval wajah. Lukis efek di
lapisan baru di atasnya, sembunyikan lapisan panduan, lalu ekspor PNG transparan 1024×1024.
Area di luar oval wajah tidak tampil di wajah.

    python3 tools/export_uv_guide.py tools/templat/panduan_uv.png
"""
import sys

import numpy as np

import face_mesh

SIZE = 1024
SS = 2

FACE_OVAL = [10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377,
             152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109]
EYE_RIGHT = [33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246]
EYE_LEFT = [263, 249, 390, 373, 374, 380, 381, 382, 362, 398, 384, 385, 386, 387, 388, 466]
BROW_RIGHT = [46, 53, 52, 65, 55, 107, 66, 105, 63, 70]
BROW_LEFT = [276, 283, 282, 295, 285, 336, 296, 334, 293, 300]
LIPS_OUTER = [61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185]
LIPS_INNER = [78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 415, 310, 311, 312, 13, 82, 81, 80, 191]
NOSE = [168, 6, 197, 195, 5, 4, 1, 19, 94, 2, 98, 97, 326, 327, 2]
NOSE_WINGS = [240, 64, 48, 115, 220, 45, 4, 275, 440, 344, 278, 294, 460]

# (titik, tertutup, warna RGBA, tebal px)
FEATURES = [
    (FACE_OVAL, True, (230, 60, 60, 255), 4),
    (EYE_RIGHT, True, (40, 120, 230, 255), 4), (EYE_LEFT, True, (40, 120, 230, 255), 4),
    (BROW_RIGHT, True, (140, 80, 30, 255), 3), (BROW_LEFT, True, (140, 80, 30, 255), 3),
    (LIPS_OUTER, True, (220, 40, 140, 255), 4), (LIPS_INNER, True, (220, 40, 140, 255), 3),
    (NOSE, False, (40, 160, 90, 255), 3), (NOSE_WINGS, False, (40, 160, 90, 255), 3),
]


def draw_line(img, p, q, color, width):
    n = SIZE * SS
    (x0, y0), (x1, y1) = p * n, q * n
    length = max(abs(x1 - x0), abs(y1 - y0), 1)
    r = max(width * SS / 2, 0.5)
    for t in np.linspace(0, 1, int(length) + 1):
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        xa, xb = int(max(x - r, 0)), int(min(x + r + 1, n))
        ya, yb = int(max(y - r, 0)), int(min(y + r + 1, n))
        gy, gx = np.mgrid[ya:yb, xa:xb]
        hit = (gx + 0.5 - x) ** 2 + (gy + 0.5 - y) ** 2 <= r * r
        img[ya:yb, xa:xb][hit] = color


def main(out):
    _, uv, tris = face_mesh.load()
    n = SIZE * SS
    img = np.zeros((n, n, 4), np.float64)
    edges = {tuple(sorted(e)) for t in tris for e in ((t[0], t[1]), (t[1], t[2]), (t[2], t[0]))}
    for a, b in edges:
        draw_line(img, uv[a], uv[b], (150, 150, 150, 170), 1)
    for points, closed, color, width in FEATURES:
        seq = points + ([points[0]] if closed else [])
        for a, b in zip(seq[:-1], seq[1:]):
            draw_line(img, uv[a], uv[b], color, width)
    img = img.reshape(SIZE, SS, SIZE, SS, 4).mean((1, 3))
    face_mesh.write_png(out, img.round().astype(np.uint8))
    print(out)


if __name__ == "__main__":
    main(*sys.argv[1:])
