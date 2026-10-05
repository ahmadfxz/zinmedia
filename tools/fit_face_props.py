#!/usr/bin/env python3
"""Tempatkan & kemas model 3D (.glb/.gltf) ke ruang kepala standar MediaPipe untuk efek wajah 3D.

Ruang kepala (sama dengan canonical_face_model.obj, satuan cm): titik asal di tengah kepala,
+X ke kiri orangnya (kanan penonton), +Y ke atas, +Z ke depan (ke kamera). Contoh titik: sudut
luar mata (±4,45; 2,66; 3,17), ujung hidung (0; −0,46; 7,59), dahi atas (0; 8,26; 4,48).

Hasilnya satu .glb ringkas: semua node digabung, tekstur warna dikecilkan (maks. TEXTURE_SIZE),
tekstur lain dibuang, bagian > 65.535 titik dipecah. Di aplikasi cukup
`FaceEffect("Helm", "…/helm_pilot.glb")`: kepala standar dicocokkan ke wajah tiap orang setiap frame.

    python3 tools/fit_face_props.py <folder-sumber> <folder-hasil> [folder-pratinjau]
"""
import json
import math
import os
import struct
import subprocess
import sys
import tempfile

import numpy as np

import face_mesh
import glb

TEXTURE_SIZE = 1024

# Kepala tak terlihat (penutup) di ruang kepala: superelips; sama dengan MeshRenderer.HEAD_OCCLUDER.
HEAD_CENTER = (0.0, 1.0, -3.6)
HEAD_RADIUS = (7.2, 11.0, 9.2)


def glasses(width=16.0, center_y=2.75, front_z=6.9):
    """Kacamata: lebar total, tinggi pusat lensa, dan posisi depan bingkai (bagian z terbesar = depan)."""
    return dict(kind="glasses", width=width, center_y=center_y, front_z=front_z)


def hat(width, base_y=8.4, base_z=-0.6, tilt=12.0, turn=0.0):
    """Topi: lebar [width], alas (y terendah) di (0, base_y, base_z), diputar [turn]° lalu depan naik [tilt]°."""
    return dict(kind="hat", width=width, base_y=base_y, base_z=base_z, tilt=tilt, turn=turn)


def around(width, center, turn=0.0, tilt=0.0, fit_materials=(), fit_top=None, keep_above=None, clip_to_fit=False):
    """
    Helm/masker: lebar [width], pusat kotak pembatas di [center] (x, y, z). Kotak pembatas diukur
    dari bahan [fit_materials] saja, atau dari [fit_top] (pecahan) bagian teratas model, bila diisi.
    Segitiga yang seluruhnya di bawah [keep_above] (y sumber) dibuang (mis. dudukan); [clip_to_fit]
    = buang segitiga di luar kotak pembatas bahan acuan (+10%), mis. sisa baut dudukan.
    """
    return dict(kind="around", width=width, center=center, turn=turn, tilt=tilt,
                fit_materials=fit_materials, fit_top=fit_top, keep_above=keep_above, clip_to_fit=clip_to_fit)


CANONICAL = dict(kind="canonical")

# Nama hasil -> (sumber, penempatan, bahan yang dibuang, (lisensi, pembuat, sumber)).
ASSETS = {
    "kacamata_sport": ("mediapipe/glasses.pbtxt", CANONICAL, (),
                       ("Apache-2.0", "The MediaPipe Authors", "https://github.com/google-ai-edge/mediapipe/tree/master/mediapipe/graphs/face_effect/data")),
    "kacamata_hitam": ("sunglasses.glb", glasses(), (),
                       ("CC-BY-4.0", "Eric Chadwick, Darmstadt Graphics Group GmbH", "https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/SunglassesKhronos")),
    "helm_pilot": ("FlightHelmet/FlightHelmet.gltf",
                   around(19.5, (0.0, 4.6, -1.2), fit_materials=("LeatherPartsMat",), keep_above=0.40, clip_to_fit=True), ("RubberWoodMat", "HoseMat"),
                   ("CC0-1.0", "Gary Hsu", "https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/FlightHelmet")),
    "helm_scifi": ("SciFiHelmet/SciFiHelmet.gltf", around(25.0, (0.0, 1.5, -1.0)), (),
                   ("CC0-1.0", "Michael Pavlovic", "https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/SciFiHelmet")),
    "topi_nelayan": ("fishermans_hat/fishermans_hat_1k.gltf", hat(26.0, base_y=7.6, tilt=8), (),
                     ("CC0-1.0", "PierreB3D (Poly Haven)", "https://polyhaven.com/a/fishermans_hat")),
    "masker_gas": ("old_gas_mask/old_gas_mask_1k.gltf", around(17.0, (0.0, -0.5, 3.5), fit_top=0.15), (),
                   ("CC0-1.0", "Poly Haven", "https://polyhaven.com/a/old_gas_mask")),
}


# ---------------- matriks ----------------

def rot_y(deg):
    a = math.radians(deg)
    m = np.eye(4)
    m[0, 0], m[0, 2], m[2, 0], m[2, 2] = math.cos(a), math.sin(a), -math.sin(a), math.cos(a)
    return m


def rot_x(deg):
    a = math.radians(deg)
    m = np.eye(4)
    m[1:3, 1:3] = [[math.cos(a), -math.sin(a)], [math.sin(a), math.cos(a)]]
    return m


def apply(m, pts):
    return (np.c_[pts, np.ones(len(pts))] @ m.T)[:, :3]


def placement(points, spec):
    """Matriks 4×4: koordinat model asli -> ruang kepala."""
    if spec["kind"] == "canonical":
        return np.eye(4)
    turn = rot_y(spec.get("turn", 0.0))
    points = apply(turn, points)
    lo, hi = points.min(0), points.max(0)
    s = spec["width"] / (hi[0] - lo[0])
    scale = np.diag([s, s, s, 1.0])
    move = np.eye(4)
    if spec["kind"] == "glasses":
        front = points[points[:, 2] > hi[2] - 0.25 * (hi[2] - lo[2])]
        mid_y = (front[:, 1].min() + front[:, 1].max()) / 2
        move[:3, 3] = [-(lo[0] + hi[0]) / 2 * s, spec["center_y"] - mid_y * s, spec["front_z"] - hi[2] * s]
        return move @ scale @ turn
    if spec["kind"] == "hat":
        base = np.eye(4)
        base[:3, 3] = [-(lo[0] + hi[0]) / 2 * s, -lo[1] * s, -(lo[2] + hi[2]) / 2 * s]
        move[:3, 3] = [0, spec["base_y"], spec["base_z"]]
        return move @ rot_x(-spec["tilt"]) @ base @ scale @ turn
    center = np.eye(4)
    center[:3, 3] = -(lo + hi) / 2 * s
    move[:3, 3] = spec["center"]
    return move @ rot_x(-spec["tilt"]) @ center @ scale @ turn


# ---------------- sumber khusus ----------------

def mediapipe_mesh(path):
    """Model efek MediaPipe (.pbtxt Mesh3d VERTEX_PT: x y z u v) + tekstur .pngblob di sebelahnya."""
    floats, ints = [], []
    for line in open(path):
        line = line.strip()
        if line.startswith("vertex_buffer:"):
            floats.append(float(line.split(":")[1]))
        elif line.startswith("index_buffer:"):
            ints.append(int(line.split(":")[1]))
    v = np.array(floats).reshape(-1, 5)
    texture = open(os.path.splitext(path)[0] + ".pngblob", "rb").read()
    # Tekstur MediaPipe: v ke atas (OpenGL); glTF: v ke bawah.
    return [dict(name="glasses", pos=v[:, :3], nrm=None, uv=np.c_[v[:, 3], 1 - v[:, 4]],
                 idx=np.array(ints).reshape(-1, 3), color=[1, 1, 1, 1], metallic=0.0, roughness=0.4,
                 blend=False, mask=False, image=texture)]


def load(path):
    return mediapipe_mesh(path) if path.endswith(".pbtxt") else glb.primitives(path)


# ---------------- tekstur ----------------

def has_alpha(image):
    return image[:8] == b"\x89PNG\r\n\x1a\n" and image[25] in (4, 6)


def shrink(image, size=TEXTURE_SIZE):
    """Kecilkan gambar (sips macOS) ke sisi maks. [size]; PNG bila beralfa, selain itu JPEG."""
    with tempfile.TemporaryDirectory() as d:
        src = os.path.join(d, "in")
        open(src, "wb").write(image)
        fmt, ext = ("png", "png") if has_alpha(image) else ("jpeg", "jpg")
        dst = os.path.join(d, "out." + ext)
        args = ["sips", "-s", "format", fmt, "-Z", str(size), src, "--out", dst]
        if fmt == "jpeg":
            args[1:1] = ["-s", "formatOptions", "85"]
        subprocess.run(args, check=True, capture_output=True)
        return open(dst, "rb").read(), "image/" + fmt


def preview_texture(image):
    """Gambar kecil 64×64 RGB (0..1) untuk pratinjau, lewat BMP dari sips."""
    with tempfile.TemporaryDirectory() as d:
        src, dst = os.path.join(d, "in"), os.path.join(d, "out.bmp")
        open(src, "wb").write(image)
        subprocess.run(["sips", "-s", "format", "bmp", "-z", "64", "64", src, "--out", dst], check=True, capture_output=True)
        b = open(dst, "rb").read()
        off = struct.unpack_from("<I", b, 10)[0]
        w, h = struct.unpack_from("<ii", b, 18)
        bpp = struct.unpack_from("<H", b, 28)[0]
        row = ((w * bpp // 8) + 3) & ~3
        px = np.frombuffer(b, np.uint8, offset=off, count=row * abs(h)).reshape(abs(h), row)[:, :w * bpp // 8]
        px = px.reshape(abs(h), w, bpp // 8)[:, :, :3][:, :, ::-1] / 255.0
        return px[::-1] if h > 0 else px


# ---------------- tulis .glb ----------------

def split_large(prim, limit=65535):
    """Gabung titik kembar; bila masih > limit, pecah per kelompok segitiga."""
    cols = [prim["pos"]] + [prim[k] for k in ("nrm", "uv") if prim[k] is not None]
    key = np.round(np.hstack(cols), 6)
    _, first, inverse = np.unique(key, axis=0, return_index=True, return_inverse=True)
    inverse = inverse.reshape(-1)
    base = {k: (prim[k][first] if prim[k] is not None else None) for k in ("pos", "nrm", "uv")}
    idx = inverse[prim["idx"]]
    if len(first) <= limit:
        return [{**prim, **base, "idx": idx}]
    parts, start = [], 0
    while start < len(idx):
        used, end = {}, start
        while end < len(idx) and len(used) + 3 <= limit:
            for v in idx[end]:
                used.setdefault(int(v), len(used))
            end += 1
        order = np.array(list(used.keys()))
        remap = np.vectorize(used.get)(idx[start:end])
        parts.append({**prim, **{k: (v[order] if v is not None else None) for k, v in base.items()}, "idx": remap})
        start = end
    return parts


def write(prims, path):
    blob = bytearray()
    views, accessors, materials, images, textures, mesh_prims = [], [], [], [], [], []

    def view(data, target=None):
        while len(blob) % 4:
            blob.append(0)
        v = {"buffer": 0, "byteOffset": len(blob), "byteLength": len(data)}
        if target:
            v["target"] = target
        views.append(v)
        blob.extend(data)
        return len(views) - 1

    def acc(array, ctype, typ, target):
        a = {"bufferView": view(array.tobytes(), target), "componentType": ctype, "count": len(array), "type": typ}
        if typ == "VEC3" and ctype == 5126:
            a["min"], a["max"] = array.min(0).tolist(), array.max(0).tolist()
        accessors.append(a)
        return len(accessors) - 1

    image_cache = {}
    for prim in prims:
        for part in split_large(prim):
            attrs = {"POSITION": acc(part["pos"].astype("<f4"), 5126, "VEC3", 34962)}
            if part["nrm"] is not None:
                attrs["NORMAL"] = acc(part["nrm"].astype("<f4"), 5126, "VEC3", 34962)
            pbr = {"baseColorFactor": [float(c) for c in part["color"]], "metallicFactor": float(part["metallic"]),
                   "roughnessFactor": float(part["roughness"])}
            if part["image"] is not None and part["uv"] is not None:
                attrs["TEXCOORD_0"] = acc(part["uv"].astype("<f4"), 5126, "VEC2", 34962)
                key = hash(part["image"])
                if key not in image_cache:
                    data, mime = shrink(part["image"])
                    images.append({"bufferView": view(data), "mimeType": mime})
                    textures.append({"source": len(images) - 1})
                    image_cache[key] = len(textures) - 1
                pbr["baseColorTexture"] = {"index": image_cache[key]}
            mat = {"name": part["name"], "pbrMetallicRoughness": pbr, "doubleSided": True}
            if part["blend"]:
                mat["alphaMode"] = "BLEND"
            elif part["mask"]:
                mat["alphaMode"] = "MASK"
            materials.append(mat)
            mesh_prims.append({"attributes": attrs, "indices": acc(part["idx"].astype("<u2").reshape(-1), 5123, "SCALAR", 34963),
                               "material": len(materials) - 1})
    gltf = {"asset": {"version": "2.0", "generator": "zinmedia fit_face_props.py"}, "scene": 0,
            "scenes": [{"nodes": [0]}], "nodes": [{"mesh": 0}], "meshes": [{"primitives": mesh_prims}],
            "materials": materials, "accessors": accessors, "bufferViews": views, "buffers": [{"byteLength": len(blob)}]}
    if images:
        gltf["images"], gltf["textures"] = images, textures
    js = json.dumps(gltf, separators=(",", ":")).encode()
    js += b" " * (-len(js) % 4)
    while len(blob) % 4:
        blob.append(0)
    with open(path, "wb") as f:
        f.write(struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(blob)))
        f.write(struct.pack("<II", len(js), 0x4E4F534A) + js)
        f.write(struct.pack("<II", len(blob), 0x004E4942) + bytes(blob))


# ---------------- pratinjau (perender perangkat lunak sederhana) ----------------

def head_mesh():
    rings, segs = 16, 32
    verts = []
    boxy = lambda v: math.copysign(math.sqrt(abs(v)), v)
    for r in range(rings + 1):
        phi = math.pi * r / rings
        for k in range(segs + 1):
            th = 2 * math.pi * k / segs
            verts.append((math.sin(phi) * boxy(math.cos(th)), math.cos(phi), math.sin(phi) * boxy(math.sin(th))))
    verts = np.array(verts) * HEAD_RADIUS + HEAD_CENTER
    tris = []
    for r in range(rings):
        for k in range(segs):
            a = r * (segs + 1) + k
            b = a + segs + 1
            tris += [[a, b, a + 1], [a + 1, b, b + 1]]
    return verts, np.array(tris)


def render(parts, yaw, size=360, span=46.0, center=(0.0, 4.0), alpha=False):
    """parts: [(posisi, segitiga, warna per segitiga N×3)] -> gambar RGB (RGBA bila [alpha]), diputar [yaw]°."""
    a = math.radians(yaw)
    rot = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    img = np.full((size, size, 3), 0.93)
    depth = np.full((size, size), -np.inf)
    light = np.array([0.35, 0.6, 0.75]) / np.linalg.norm([0.35, 0.6, 0.75])
    for pos, tris, colors in parts:
        p = pos @ rot.T
        sx = ((p[:, 0] - center[0]) / span + 0.5) * size
        sy = (0.5 - (p[:, 1] - center[1]) / span) * size
        for t, color in zip(tris, colors):
            x, y, z = sx[t], sy[t], p[t, 2]
            x0, x1 = max(int(x.min()), 0), min(int(x.max()) + 1, size)
            y0, y1 = max(int(y.min()), 0), min(int(y.max()) + 1, size)
            if x0 >= x1 or y0 >= y1:
                continue
            d = (y[1] - y[2]) * (x[0] - x[2]) + (x[2] - x[1]) * (y[0] - y[2])
            if abs(d) < 1e-9:
                continue
            n = np.cross(p[t[1]] - p[t[0]], p[t[2]] - p[t[0]])
            shade = 0.45 + 0.55 * abs(n @ light) / (np.linalg.norm(n) + 1e-9)
            gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
            w0 = ((y[1] - y[2]) * (gx - x[2]) + (x[2] - x[1]) * (gy - y[2])) / d
            w1 = ((y[2] - y[0]) * (gx - x[2]) + (x[0] - x[2]) * (gy - y[2])) / d
            inside = (w0 >= 0) & (w1 >= 0) & (1 - w0 - w1 >= 0)
            zz = w0 * z[0] + w1 * z[1] + (1 - w0 - w1) * z[2]
            region = depth[y0:y1, x0:x1]
            hit = inside & (zz > region)
            region[hit] = zz[hit]
            img[y0:y1, x0:x1][hit] = np.clip(np.array(color) * shade, 0, 1)
    if alpha:
        return np.dstack([img, np.isfinite(depth).astype(float)])
    return img


def icon(prims, size=128):
    """Ikon RGBA transparan: model saja, dilihat serong depan, memenuhi gambar."""
    pts = np.vstack([p["pos"] for p in prims])
    # Bingkai hanya area sekitar kepala (mis. tanpa selang masker yang menjuntai).
    near = pts[(np.abs(pts[:, 0]) < 16) & (pts[:, 1] > -14) & (pts[:, 1] < 22) & (pts[:, 2] > -16) & (pts[:, 2] < 22)]
    pts = near if len(near) else pts
    a = math.radians(30)
    rot = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    q = pts @ rot.T
    lo, hi = q.min(0), q.max(0)
    span = max(hi[0] - lo[0], hi[1] - lo[1]) * 1.08
    ss = 3
    img = render([(p["pos"], p["idx"], triangle_colors(p)) for p in prims], 30, size=size * ss, span=span,
                 center=((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2), alpha=True)
    img = img.reshape(size, ss, size, ss, 4).mean((1, 3))
    rgb = np.where(img[..., 3:] > 0, img[..., :3] / np.maximum(img[..., 3:], 1e-6) * img[..., 3:], 0)
    return np.dstack([np.where(img[..., 3:] > 0, rgb / np.maximum(img[..., 3:], 1e-6), 0), img[..., 3]])


def triangle_colors(prim):
    base = np.array(prim["color"][:3])
    if prim["image"] is None or prim["uv"] is None:
        return np.tile(base, (len(prim["idx"]), 1))
    tex = preview_texture(prim["image"])
    uv = prim["uv"][prim["idx"]].mean(1) % 1.0
    px = tex[(uv[:, 1] * 63).astype(int), (uv[:, 0] * 63).astype(int)]
    return px * base


def main(src_dir, out_dir, preview_dir=None):
    os.makedirs(out_dir, exist_ok=True)
    face_pos, _, face_tris = face_mesh.load()
    head = head_mesh()
    credits = []
    for name, (src, spec, drop, (license_, author, url)) in ASSETS.items():
        prims = [p for p in load(os.path.join(src_dir, src)) if p["name"] not in drop]
        if spec.get("keep_above") is not None:
            for p in prims:
                p["idx"] = p["idx"][(p["pos"][p["idx"]][:, :, 1] >= spec["keep_above"]).any(1)]
        fit = [p for p in prims if not spec.get("fit_materials") or p["name"] in spec["fit_materials"]]
        points = np.vstack([p["pos"][np.unique(p["idx"])] for p in fit])
        if spec.get("fit_top"):
            y = points[:, 1]
            points = points[y >= y.max() - spec["fit_top"] * (y.max() - y.min())]
        if spec.get("clip_to_fit"):
            lo, hi = points.min(0), points.max(0)
            pad = (hi - lo) * 0.1
            for p in prims:
                inside = ((p["pos"] >= lo - pad) & (p["pos"] <= hi + pad)).all(1)
                p["idx"] = p["idx"][inside[p["idx"]].all(1)]
        m = placement(points, spec)
        normal_m = np.linalg.inv(m[:3, :3]).T
        for p in prims:
            p["pos"] = apply(m, p["pos"])
            if p["nrm"] is not None:
                p["nrm"] = p["nrm"] @ normal_m.T
                p["nrm"] /= np.maximum(np.linalg.norm(p["nrm"], axis=1, keepdims=True), 1e-9)
        out = os.path.join(out_dir, f"{name}.glb")
        write(prims, out)
        credits.append(f"{name}.glb: {license_}, {author}, {url}")
        face_mesh.write_png(os.path.join(out_dir, f"{name}_ikon.png"), (icon(prims) * 255 + 0.5).astype(np.uint8))
        print(f"{name}: {sum(len(p['idx']) for p in prims)} segitiga, {os.path.getsize(out) // 1024} KB")
        if preview_dir:
            os.makedirs(preview_dir, exist_ok=True)
            scene = [(head[0], head[1], np.tile((0.55, 0.5, 0.48), (len(head[1]), 1))),
                     (face_pos, face_tris, np.tile((0.85, 0.72, 0.62), (len(face_tris), 1)))]
            scene += [(p["pos"], p["idx"], triangle_colors(p)) for p in prims]
            views = [render(scene, yaw) for yaw in (0, 40, 90)]
            face_mesh.write_png(os.path.join(preview_dir, f"{name}.png"),
                                np.dstack([(np.hstack(views) * 255).astype(np.uint8),
                                           np.full((views[0].shape[0], 3 * views[0].shape[1]), 255, np.uint8)]))
    with open(os.path.join(out_dir, "CREDITS.txt"), "w") as f:
        f.write("Model 3D efek wajah. CC-BY: wajib mencantumkan nama pembuat di aplikasi.\n")
        f.write("\n".join(credits) + "\n")


if __name__ == "__main__":
    main(*sys.argv[1:])
