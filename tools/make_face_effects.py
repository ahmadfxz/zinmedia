#!/usr/bin/env python3
"""Buat gambar efek wajah (PNG) di peta UV wajah MediaPipe, untuk com.zinmedia.effects.FaceEffect.

Setiap efek digambar di koordinat UV (0..1, v ke bawah): titik (u, v) di gambar menempel di titik
wajah yang sama pada wajah siapa pun. Bentuk bisa memakai garis wajah dari jaring (mata, bibir,
oval wajah) agar pas.

    python3 tools/make_face_effects.py sample/src/main/assets/face_mesh [pratinjau-dir]
"""
import math
import os
import sys

import numpy as np

import face_mesh

SIZE = 512
SS = 3  # supersampling tepi

_, UV, _ = face_mesh.load()

# Garis wajah MediaPipe (berurutan mengelilingi).
FACE_OVAL = [10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377,
             152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109]
EYE_RIGHT = [33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246]
EYE_LEFT = [263, 249, 390, 373, 374, 380, 381, 382, 362, 398, 384, 385, 386, 387, 388, 466]
LIPS_OUTER = [61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185]


def pt(i):
    return UV[i]


def outline(indices, grow=0.0):
    """Poligon titik-titik [indices], dibesarkan [grow] (pecahan) dari pusatnya."""
    p = np.array([UV[i] for i in indices])
    c = p.mean(0)
    return c + (p - c) * (1 + grow)


class Canvas:
    """Kanvas RGBA premultiplied beresolusi SIZE×SS, koordinat UV."""

    def __init__(self):
        n = SIZE * SS
        v, u = np.mgrid[0:n, 0:n]
        self.u = (u + 0.5) / n
        self.v = (v + 0.5) / n
        self.rgb = np.zeros((n, n, 3))
        self.a = np.zeros((n, n))

    def paint(self, mask, color, alpha=1.0):
        """Tumpuk warna [color] (RGB 0..1) di [mask] (bool atau 0..1) dengan [alpha]."""
        m = np.clip(mask.astype(float) * alpha, 0, 1)
        self.rgb = self.rgb * (1 - m[..., None]) + np.array(color) * m[..., None]
        self.a = self.a * (1 - m) + m

    def erase(self, mask):
        m = mask.astype(float)
        self.rgb *= (1 - m[..., None])
        self.a *= (1 - m)

    # ---- bentuk ----
    def ellipse(self, cx, cy, rx, ry, angle=0.0):
        c, s = math.cos(angle), math.sin(angle)
        x = self.u - cx
        y = self.v - cy
        return ((x * c + y * s) / rx) ** 2 + ((-x * s + y * c) / ry) ** 2 < 1

    def polygon(self, points):
        """Isi poligon (aturan genap-ganjil)."""
        inside = np.zeros_like(self.u, dtype=bool)
        p = np.asarray(points)
        for (x1, y1), (x2, y2) in zip(p, np.roll(p, -1, axis=0)):
            crosses = (y1 > self.v) != (y2 > self.v)
            x_at = x1 + (self.v - y1) * (x2 - x1) / np.where(y2 == y1, 1e-9, y2 - y1)
            inside ^= crosses & (self.u < x_at)
        return inside

    def line(self, points, width):
        """Garis tebal melalui [points]."""
        d = np.full_like(self.u, np.inf)
        for (x1, y1), (x2, y2) in zip(points[:-1], points[1:]):
            dx, dy = x2 - x1, y2 - y1
            t = np.clip(((self.u - x1) * dx + (self.v - y1) * dy) / max(dx * dx + dy * dy, 1e-12), 0, 1)
            d = np.minimum(d, np.hypot(self.u - (x1 + t * dx), self.v - (y1 + t * dy)))
        return d < width / 2

    def star(self, cx, cy, r, points=5, inner=0.45, rotation=-math.pi / 2):
        verts = []
        for k in range(points * 2):
            rr = r if k % 2 == 0 else r * inner
            a = rotation + k * math.pi / points
            verts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
        return self.polygon(verts)

    def heart(self, cx, cy, size):
        x = (self.u - cx) / size
        y = -(self.v - cy) / size
        return (x * x + y * y - 1) ** 3 - x * x * y ** 3 < 0

    def image(self):
        a = self.a.reshape(SIZE, SS, SIZE, SS).mean((1, 3))
        pm = (self.rgb * self.a[..., None]).reshape(SIZE, SS, SIZE, SS, 3).mean((1, 3))
        color = np.where(a[..., None] > 0, pm / np.maximum(a[..., None], 1e-6), 0)
        return np.dstack([np.clip(color, 0, 1), a])


def curve(f, t0, t1, steps=40):
    return [f(t0 + (t1 - t0) * k / steps) for k in range(steps + 1)]


def eye_holes(c, grow=0.35):
    return c.polygon(outline(EYE_RIGHT, grow)) | c.polygon(outline(EYE_LEFT, grow))


# ---------------- efek ----------------

def topeng(c):
    """Topeng pesta ungu berles emas, berlubang di mata."""
    du = np.abs(c.u - 0.5)
    t = np.clip(du / 0.36, 0, 1)
    top = 0.232 + 0.055 * t ** 2 + 0.05 * np.clip((du - 0.30) / 0.1, 0, 1) ** 2
    bottom = 0.428 + 0.045 * np.sin(np.pi * t) - 0.13 * np.clip((du - 0.29) / 0.1, 0, None) ** 1.5
    inside = (c.v > top) & (c.v < bottom) & (du < 0.40)
    holes = np.zeros_like(inside)
    rim = np.zeros_like(inside)
    for cx in (0.352, 0.648):
        x = (c.u - cx) / 0.088
        y = (c.v - 0.380 + 0.012 * np.sign(c.u - 0.5) * (c.u - cx) / 0.088) / 0.046
        r = x ** 2 + y ** 2
        holes |= r < 1
        rim |= (r >= 1) & (r < 1.35)
    edge = inside & ((c.v - top < 0.012) | (bottom - c.v < 0.012) | (du > 0.385))
    shade = 0.75 + 0.25 * (1 - du / 0.4)
    c.paint(inside & ~holes, (0.42, 0.10, 0.55))
    c.rgb *= np.where(inside & ~holes, shade, 1.0)[..., None]
    c.paint((edge | rim) & inside & ~holes, (0.95, 0.75, 0.30))


def kucing(c):
    """Hidung kucing merah muda, garis ke bibir, dan kumis kucing."""
    nose_u, nose_v = pt(4)
    c.paint(c.polygon([(nose_u - 0.045, nose_v - 0.025), (nose_u + 0.045, nose_v - 0.025), (nose_u, nose_v + 0.035)]),
            (0.98, 0.55, 0.68))
    c.paint(c.ellipse(nose_u - 0.012, nose_v - 0.012, 0.012, 0.007), (1, 0.85, 0.9), 0.8)
    lip_u, lip_v = pt(0)
    c.paint(c.line([(nose_u, nose_v + 0.03), (nose_u, lip_v - 0.005)], 0.008), (0.15, 0.1, 0.12))
    for side in (-1, 1):
        base_u = 0.5 + side * 0.11
        for k, dv in enumerate((-0.03, 0.0, 0.03)):
            end = (0.5 + side * 0.34, 0.60 + dv * 1.8 - 0.01 * k)
            pts = curve(lambda t: (base_u + (end[0] - base_u) * t, 0.6 + dv * 0.5 + (end[1] - 0.6 - dv * 0.5) * t - 0.02 * math.sin(math.pi * t)), 0, 1, 20)
            c.paint(c.line(pts, 0.007), (0.12, 0.1, 0.1))
        c.paint(c.ellipse(0.5 + side * 0.2, 0.55, 0.06, 0.035), (1.0, 0.55, 0.65), 0.35)


def kumis(c):
    """Kumis melengkung klasik di atas bibir."""
    lip_u, lip_v = pt(0)
    y0 = lip_v - 0.012
    for side in (-1, 1):
        pts = curve(lambda t: (0.5 + side * (0.01 + 0.17 * t),
                               y0 + 0.018 * math.sin(math.pi * min(t / 0.75, 1))
                               - 0.06 * max(t - 0.75, 0) / 0.25 * (1 - max(t - 0.75, 0) / 0.5)), 0, 1, 60)
        for k, (x, y) in enumerate(pts):
            w = 0.045 * math.sin(math.pi * min(k / len(pts) * 1.05, 1)) + 0.008
            c.paint(c.ellipse(x, y, w * 0.55, w * 0.5), (0.16, 0.09, 0.05))
    # Sedikit kilap rambut.
    c.paint(c.line(curve(lambda t: (0.5 + 0.12 * t, y0 - 0.006 + 0.012 * math.sin(math.pi * t)), -1, 1), 0.004), (0.45, 0.3, 0.2), 0.6)


def pipi_merah(c):
    """Rona pipi lembut dengan kilau dan hati kecil."""
    for side in (-1, 1):
        cu, cv = 0.5 + side * 0.215, 0.535
        d = ((c.u - cu) / 0.085) ** 2 + ((c.v - cv) / 0.05) ** 2
        c.paint(np.ones_like(c.u, dtype=bool), (1.0, 0.32, 0.45), np.clip(1 - d, 0, 1) ** 1.2 * 0.85)
        for k, (du, dv, r) in enumerate(((-0.04, -0.03, 0.012), (0.035, -0.02, 0.009), (0.01, 0.035, 0.008))):
            c.paint(c.star(cu + side * du, cv + dv, r, points=4, inner=0.3), (1, 1, 0.95), 0.9)
        c.paint(c.heart(cu + side * 0.06, cv + 0.045, 0.022), (0.95, 0.2, 0.35))


def badut(c):
    """Hidung merah bulat, berlian biru di mata, pipi merah, senyum lebar."""
    for side in (-1, 1):
        ex, ey = 0.5 + side * 0.157, 0.378
        diamond = c.polygon([(ex, ey - 0.1), (ex + 0.05, ey), (ex, ey + 0.1), (ex - 0.05, ey)])
        c.paint(diamond, (0.2, 0.45, 0.95))
        c.paint(c.ellipse(0.5 + side * 0.22, 0.56, 0.05, 0.035), (0.95, 0.2, 0.25), 0.8)
    c.erase(eye_holes(c, 0.25))
    mouth = c.polygon(outline(LIPS_OUTER, 0.18))
    c.paint(mouth & ~c.polygon(outline(LIPS_OUTER, -0.15)), (0.9, 0.1, 0.15))
    nu, nv = pt(4)
    c.paint(c.ellipse(nu, nv + 0.005, 0.06, 0.055), (0.92, 0.08, 0.1))
    c.paint(c.ellipse(nu - 0.018, nv - 0.018, 0.016, 0.011), (1, 0.75, 0.75), 0.85)


def tengkorak(c):
    """Cat wajah tengkorak: putih penuh, rongga mata & hidung hitam, jahitan di bibir."""
    face = c.polygon(outline(FACE_OVAL, -0.03))
    c.paint(face, (0.94, 0.94, 0.92))
    for side in (-1, 1):
        ex = 0.5 + side * 0.157
        c.paint(c.ellipse(ex, 0.385, 0.11, 0.085), (0.06, 0.06, 0.07))
    nu, nv = pt(4)
    c.paint(c.polygon([(nu, nv - 0.03), (nu + 0.05, nv + 0.07), (nu - 0.05, nv + 0.07)]), (0.06, 0.06, 0.07))
    lips = c.polygon(outline(LIPS_OUTER, 0.08))
    c.paint(lips, (0.08, 0.08, 0.09))
    mu, mv = pt(13)
    for k in range(-5, 6):
        x = mu + k * 0.022
        c.paint(c.line([(x, mv - 0.055), (x, mv + 0.055)], 0.008), (0.08, 0.08, 0.09))
    c.paint(c.line([(pt(61)[0] - 0.03, mv), (pt(291)[0] + 0.03, mv)], 0.008), (0.08, 0.08, 0.09))
    # Pelipis & tulang pipi sedikit gelap.
    for side in (-1, 1):
        d = ((c.u - (0.5 + side * 0.3)) / 0.07) ** 2 + ((c.v - 0.58) / 0.09) ** 2
        c.paint(face, (0.35, 0.35, 0.38), np.clip(1 - d, 0, 1) * 0.6)
    c.erase(eye_holes(c, 0.15))


def bintang(c):
    """Taburan bintang emas di tulang hidung dan pipi."""
    rng = np.random.default_rng(7)
    spots = []
    while len(spots) < 22:
        u = rng.uniform(0.22, 0.78)
        v = rng.uniform(0.42, 0.6)
        # Melengkung seperti bintik di pipi & hidung, tidak di mata.
        if abs(u - 0.5) < 0.05 and v > 0.55:
            continue
        if v < 0.47 and abs(abs(u - 0.5) - 0.157) < 0.08:
            continue
        if all(math.hypot(u - a, v - b) > 0.04 for a, b, _ in spots):
            spots.append((u, v, rng.uniform(0.008, 0.02)))
    for u, v, r in spots:
        c.paint(c.star(u, v, r * 1.25), (0.55, 0.38, 0.05), 0.6)
        c.paint(c.star(u, v, r, rotation=-math.pi / 2 + rng.uniform(-0.3, 0.3)), (1.0, 0.82, 0.25))
        c.paint(c.ellipse(u - r * 0.25, v - r * 0.3, r * 0.25, r * 0.18), (1, 1, 0.9), 0.8)


EFFECTS = {
    "topeng": topeng,
    "kucing": kucing,
    "kumis": kumis,
    "pipi_merah": pipi_merah,
    "badut": badut,
    "tengkorak": tengkorak,
    "bintang": bintang,
}


def main(out_dir, preview_dir=None):
    os.makedirs(out_dir, exist_ok=True)
    for name, draw in EFFECTS.items():
        c = Canvas()
        draw(c)
        img = c.image()
        path = os.path.join(out_dir, f"{name}.png")
        face_mesh.write_png(path, (img * 255 + 0.5).astype(np.uint8))
        print(path)
        if preview_dir:
            os.makedirs(preview_dir, exist_ok=True)
            p = img.copy()
            p[..., :3] = p[..., :3] * p[..., 3:] + 0.85 * (1 - p[..., 3:])
            p[..., 3] = 1
            for x, y in UV:
                xi, yi = int(x * SIZE), int(y * SIZE)
                p[yi, xi, :3] = [0.1, 0.5, 0.9]
            face_mesh.write_png(os.path.join(preview_dir, f"{name}.png"), (p * 255).astype(np.uint8))


if __name__ == "__main__":
    main(*sys.argv[1:])
