#!/usr/bin/env python3
"""Data beauty untuk media-effects: jaring warp (wajah + cincin luar) dan bobot masker per titik.

Titik: 0..477 = titik wajah MediaPipe (termasuk iris), 478.. = cincin luar (satu per titik oval
wajah, didorong keluar saat dipakai; tidak ikut digeser warp, sehingga tepi menyatu dengan latar).

Format (little-endian):
  int jumlahTitik, int jumlahIndeks, int jumlahOval, int jumlahKanal
  short indeks[jumlahIndeks]          segitiga warp/masker
  short oval[jumlahOval]              titik oval wajah berurutan (sumber cincin luar)
  ubyte bobot[jumlahTitik * kanal]    0..255 per kanal (area lembut), lihat CHANNELS
  lalu untuk tiap area bertepi tegas (REGIONS: bibir, mulut/gigi, mata):
  int jumlahIndeksArea, short indeksArea[...]   segitiga area itu (digambar penuh)

    python3 tools/make_beauty_mesh_bin.py media/media-effects/src/main/res/raw/zm_beauty_mesh.bin [pratinjau-dir]
"""
import math
import os
import struct
import sys

import numpy as np

import face_mesh

POINTS = 478
FACE_OVAL = [10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377,
             152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109]
EYE_RIGHT = [33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246]
EYE_LEFT = [263, 249, 390, 373, 374, 380, 381, 382, 362, 398, 384, 385, 386, 387, 388, 466]
BROW_RIGHT = [46, 53, 52, 65, 55, 107, 66, 105, 63, 70]
BROW_LEFT = [276, 283, 282, 295, 285, 336, 296, 334, 293, 300]
LIPS_OUTER = [61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185]
LIPS_INNER = [78, 95, 88, 178, 87, 14, 317, 402, 318, 324, 308, 415, 310, 311, 312, 13, 82, 81, 80, 191]

# Area bertepi tegas: segitiga yang titik beratnya (di peta UV) di dalam poligon, digambar penuh ke
# kanalnya (bobot per titik tidak cukup: tepi dalam bibir dipakai bersama bibir & bukaan mulut).
REGIONS = ["lips", "teeth", "eyes"]

# Urutan kanal = urutan di shader (3 tekstur × RGBA).
CHANNELS = ["skin", "underEye", "smileLines", "lips",
            "teeth", "eyes", "cheeks", "highlight",
            "contour", "reserved1", "reserved2", "reserved3"]


def gauss(uv, center, radius):
    d = (uv - np.array(center)) / np.array(radius)
    return np.exp(-(d ** 2).sum(1) * 1.6)


def segment_gauss(uv, a, b, width):
    a, b = np.array(a), np.array(b)
    t = np.clip(((uv - a) @ (b - a)) / ((b - a) @ (b - a)), 0, 1)
    d = np.linalg.norm(uv - (a + t[:, None] * (b - a)), axis=1)
    return np.exp(-(d / width) ** 2)


def weights(uv):
    n = len(uv)
    w = np.zeros((n, len(CHANNELS)))
    eyes = set(EYE_RIGHT + EYE_LEFT)
    brows = set(BROW_RIGHT + BROW_LEFT)
    lips = set(LIPS_OUTER + LIPS_INNER)
    inner = set(LIPS_INNER)

    # Kulit: seluruh wajah kecuali mata, alis, bibir; tepi oval separuh agar memudar.
    skin = np.ones(n)
    for i in eyes | brows | lips:
        skin[i] = 0.0
    for i in FACE_OVAL:
        skin[i] = 0.6
    w[:, 0] = skin
    eye_r = uv[EYE_RIGHT].mean(0)
    eye_l = uv[EYE_LEFT].mean(0)
    # Bawah mata (lingkar hitam): pita di bawah kelopak bawah.
    w[:, 1] = np.maximum(gauss(uv, eye_r + [0.0, 0.06], [0.085, 0.035]), gauss(uv, eye_l + [0.0, 0.06], [0.085, 0.035])) * skin
    # Garis senyum: sayap hidung -> sudut bibir, sedikit di luar.
    w[:, 2] = np.maximum(segment_gauss(uv, uv[48] + [-0.02, 0.0], uv[61] + [-0.025, 0.0], 0.03),
                         segment_gauss(uv, uv[278] + [0.02, 0.0], uv[291] + [0.025, 0.0], 0.03)) * skin
    # Bibir, gigi, mata: lihat region_triangles (bobot per titik 0).
    # Perona (apel pipi), highlight (tulang hidung, dahi, tulang pipi atas, dagu), kontur.
    w[:, 6] = np.maximum(gauss(uv, [0.255, 0.53], [0.09, 0.06]), gauss(uv, [0.745, 0.53], [0.09, 0.06])) * skin
    w[:, 7] = np.maximum.reduce([
        segment_gauss(uv, [0.5, 0.36], [0.5, 0.5], 0.022),
        gauss(uv, [0.5, 0.2], [0.09, 0.05]),
        gauss(uv, [0.22, 0.45], [0.06, 0.03]), gauss(uv, [0.78, 0.45], [0.06, 0.03]),
        gauss(uv, [0.5, 0.86], [0.05, 0.03]),
    ]) * skin
    w[:, 8] = np.maximum.reduce([
        gauss(uv, [0.17, 0.6], [0.07, 0.05]), gauss(uv, [0.83, 0.6], [0.07, 0.05]),
        segment_gauss(uv, [0.08, 0.6], [0.2, 0.86], 0.04), segment_gauss(uv, [0.92, 0.6], [0.8, 0.86], 0.04),
        segment_gauss(uv, [0.455, 0.4], [0.445, 0.52], 0.015), segment_gauss(uv, [0.545, 0.4], [0.555, 0.52], 0.015),
    ]) * skin
    return np.clip(w, 0, 1)


def inside(points, poly):
    """Titik [points] (N×2) di dalam poligon [poly] (aturan genap-ganjil)."""
    result = np.zeros(len(points), bool)
    for (x1, y1), (x2, y2) in zip(poly, np.roll(poly, -1, axis=0)):
        crosses = (y1 > points[:, 1]) != (y2 > points[:, 1])
        x_at = x1 + (points[:, 1] - y1) * (x2 - x1) / np.where(y2 == y1, 1e-9, y2 - y1)
        result ^= crosses & (points[:, 0] < x_at)
    return result


def region_triangles(uv, tris):
    """Segitiga tiap area REGIONS berdasarkan titik beratnya di peta UV."""
    centers = uv[tris].mean(1)
    outer = inside(centers, uv[LIPS_OUTER])
    mouth = inside(centers, uv[LIPS_INNER])
    eyes = inside(centers, uv[EYE_RIGHT]) | inside(centers, uv[EYE_LEFT])
    return {"lips": tris[outer & ~mouth], "teeth": tris[mouth], "eyes": tris[eyes]}


def main(out, preview_dir=None):
    _, uv, tris = face_mesh.load()
    oval = np.array(FACE_OVAL)
    ring = POINTS + np.arange(len(oval))
    ring_tris = []
    for k in range(len(oval)):
        a, b = oval[k], oval[(k + 1) % len(oval)]
        ra, rb = ring[k], ring[(k + 1) % len(oval)]
        ring_tris += [[a, b, rb], [a, rb, ra]]
    all_tris = np.vstack([tris, np.array(ring_tris)])
    vertex_count = POINTS + len(oval)
    w = np.zeros((vertex_count, len(CHANNELS)))
    w[:len(uv)] = weights(uv)  # iris (468..477) & cincin luar: 0 di semua kanal
    with open(out, "wb") as f:
        f.write(struct.pack("<iiii", vertex_count, all_tris.size, len(oval), len(CHANNELS)))
        f.write(all_tris.astype("<i2").tobytes())
        f.write(oval.astype("<i2").tobytes())
        f.write((w * 255 + 0.5).astype(np.uint8).tobytes())
        regions = region_triangles(uv, tris)
        for name in REGIONS:
            f.write(struct.pack("<i", regions[name].size))
            f.write(regions[name].astype("<i2").tobytes())
    print(out, vertex_count, "titik,", len(all_tris), "segitiga,", len(CHANNELS), "kanal,",
          {k: len(v) for k, v in region_triangles(uv, tris).items()}, "segitiga area")
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        size = 256
        for c, name in enumerate(CHANNELS[:9]):
            img = np.zeros((size, size, 4), np.uint8)
            for t in tris:
                p = uv[t] * size
                x0, x1 = int(p[:, 0].min()), int(p[:, 0].max()) + 1
                y0, y1 = int(p[:, 1].min()), int(p[:, 1].max()) + 1
                gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
                (ax, ay), (bx, by), (cx, cy) = p
                d = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
                if abs(d) < 1e-9:
                    continue
                l0 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / d
                l1 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / d
                l2 = 1 - l0 - l1
                inside = (l0 >= 0) & (l1 >= 0) & (l2 >= 0)
                val = l0 * w[t[0], c] + l1 * w[t[1], c] + l2 * w[t[2], c]
                region = img[y0:y1, x0:x1]
                region[inside] = np.stack([val[inside] * 255] * 3 + [np.full(inside.sum(), 255)], 1).astype(np.uint8)
            face_mesh.write_png(os.path.join(preview_dir, f"{c}_{name}.png"), img)


if __name__ == "__main__":
    main(*sys.argv[1:])
