"""Data jaring wajah MediaPipe (canonical_face_model.obj, Apache 2.0) untuk alat-alat zinmedia."""
import os
import struct
import zlib

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))


def load():
    """(posisi 468×3, uv 468×2 dengan v ke bawah, segitiga N×3 menghadap keluar)."""
    vs, vts, faces = [], [], []
    for line in open(os.path.join(HERE, "canonical_face_model.obj")):
        p = line.split()
        if not p:
            continue
        if p[0] == "v":
            vs.append([float(x) for x in p[1:4]])
        elif p[0] == "vt":
            vts.append([float(x) for x in p[1:3]])
        elif p[0] == "f":
            faces.append([tuple(int(i) - 1 for i in c.split("/")[:2]) for c in p[1:4]])
    pos = np.array(vs)
    uv = np.full((len(vs), 2), np.nan)
    for face in faces:
        for v, t in face:
            if not np.isnan(uv[v, 0]):
                assert np.allclose(uv[v], vts[t]), f"UV ganda titik {v}"
            uv[v] = vts[t]
    assert not np.isnan(uv).any()
    uv[:, 1] = 1 - uv[:, 1]  # v ke bawah, seperti gambar
    tris = np.array([[v for v, _ in f] for f in faces])
    # Putaran segitiga: normal menjauh dari pusat kepala (keluar).
    center = pos.mean(0) - np.array([0, 0, 5.0])
    for i, (a, b, c) in enumerate(tris):
        n = np.cross(pos[b] - pos[a], pos[c] - pos[a])
        if np.dot(n, (pos[a] + pos[b] + pos[c]) / 3 - center) < 0:
            tris[i] = [a, c, b]
    return pos, uv, tris


def write_png(path, rgba):
    """rgba: H×W×4 uint8."""
    h, w, _ = rgba.shape
    raw = b"".join(b"\x00" + rgba[y].tobytes() for y in range(h))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        f.write(chunk(b"IEND", b""))
