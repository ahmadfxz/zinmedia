"""Baca/tulis .glb sederhana untuk alat zinmedia: mesh segitiga, transformasi node, bahan dasar."""
import json
import struct

import numpy as np

COMPONENT = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
WIDTH = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}


def read(path):
    data = open(path, "rb").read()
    assert data[:4] == b"glTF"
    off, gltf, binary = 12, None, b""
    while off < len(data):
        length, kind = struct.unpack_from("<II", data, off)
        chunk = data[off + 8: off + 8 + length]
        if kind == 0x4E4F534A:
            gltf = json.loads(chunk)
        elif kind == 0x004E4942:
            binary = chunk
        off += 8 + length
    return gltf, binary


def accessor(gltf, binary, index):
    a = gltf["accessors"][index]
    view = gltf["bufferViews"][a["bufferView"]]
    dtype = COMPONENT[a["componentType"]]
    n = WIDTH[a["type"]]
    start = view.get("byteOffset", 0) + a.get("byteOffset", 0)
    stride = view.get("byteStride", 0) or np.dtype(dtype).itemsize * n
    raw = np.frombuffer(binary, dtype=np.uint8, count=stride * (a["count"] - 1) + np.dtype(dtype).itemsize * n, offset=start)
    rows = np.lib.stride_tricks.as_strided(raw, shape=(a["count"], np.dtype(dtype).itemsize * n), strides=(stride, 1))
    out = np.frombuffer(rows.copy().tobytes(), dtype=dtype).reshape(a["count"], n).astype(np.float64)
    if a.get("normalized") and dtype != np.float32:
        out /= np.iinfo(dtype).max
    return out


def local_matrix(node):
    if "matrix" in node:
        return np.array(node["matrix"], dtype=np.float64).reshape(4, 4).T
    t = node.get("translation", [0, 0, 0])
    x, y, z, w = node.get("rotation", [0, 0, 0, 1])
    s = node.get("scale", [1, 1, 1])
    r = np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])
    m = np.eye(4)
    m[:3, :3] = r * np.array(s)
    m[:3, 3] = t
    return m


def triangles(gltf, binary):
    """[(posisi N×3 dunia, indeks M×3, warna RGBA)] untuk semua mesh di scene."""
    out = []
    scene = gltf["scenes"][gltf.get("scene", 0)]

    def visit(i, parent):
        node = gltf["nodes"][i]
        world = parent @ local_matrix(node)
        if "mesh" in node:
            for prim in gltf["meshes"][node["mesh"]]["primitives"]:
                if prim.get("mode", 4) != 4:
                    continue
                pos = accessor(gltf, binary, prim["attributes"]["POSITION"])
                pos = (np.c_[pos, np.ones(len(pos))] @ world.T)[:, :3]
                idx = accessor(gltf, binary, prim["indices"]).astype(int).reshape(-1, 3) if "indices" in prim \
                    else np.arange(len(pos)).reshape(-1, 3)
                color = [1, 1, 1, 1]
                if "material" in prim:
                    mat = gltf["materials"][prim["material"]]
                    color = mat.get("pbrMetallicRoughness", {}).get("baseColorFactor", color)
                out.append((pos, idx, color))
        for c in node.get("children", []):
            visit(c, world)

    for root in scene["nodes"]:
        visit(root, np.eye(4))
    return out


def write_with_root(src, dst, matrix):
    """Salin [src] ke [dst] dengan semua node akar dibungkus satu node ber-[matrix] (4×4)."""
    gltf, binary = read(src)
    scene = gltf["scenes"][gltf.get("scene", 0)]
    gltf["nodes"].append({"name": "zinmedia_face_space", "matrix": list(np.asarray(matrix, dtype=float).T.reshape(-1)),
                          "children": scene["nodes"]})
    scene["nodes"] = [len(gltf["nodes"]) - 1]
    js = json.dumps(gltf, separators=(",", ":")).encode()
    js += b" " * (-len(js) % 4)
    binary = binary + b"\0" * (-len(binary) % 4)
    total = 12 + 8 + len(js) + (8 + len(binary) if binary else 0)
    with open(dst, "wb") as f:
        f.write(struct.pack("<III", 0x46546C67, 2, total))
        f.write(struct.pack("<II", len(js), 0x4E4F534A) + js)
        if binary:
            f.write(struct.pack("<II", len(binary), 0x004E4942) + binary)


def read_any(path):
    """(gltf, buffers, image_bytes) untuk .glb atau .gltf (berkas luar di folder yang sama)."""
    import os
    if path.lower().endswith(".glb"):
        gltf, binary = read(path)
        buffers = [binary]
    else:
        gltf = json.load(open(path))
        base = os.path.dirname(path)
        buffers = [open(os.path.join(base, b["uri"]), "rb").read() for b in gltf.get("buffers", [])]

    def image_bytes(index):
        image = gltf["images"][index]
        if "bufferView" in image:
            view = gltf["bufferViews"][image["bufferView"]]
            start = view.get("byteOffset", 0)
            return buffers[view.get("buffer", 0)][start:start + view["byteLength"]]
        return open(os.path.join(os.path.dirname(path), image["uri"]), "rb").read()

    return gltf, buffers, image_bytes


def accessor_any(gltf, buffers, index):
    a = gltf["accessors"][index]
    view = gltf["bufferViews"][a["bufferView"]]
    return accessor({**gltf, "accessors": [a], "bufferViews": [{**view, "buffer": 0}]}, buffers[view.get("buffer", 0)], 0) \
        if False else _accessor(gltf, buffers[view.get("buffer", 0)], a, view)


def _accessor(gltf, binary, a, view):
    dtype = COMPONENT[a["componentType"]]
    n = WIDTH[a["type"]]
    item = np.dtype(dtype).itemsize * n
    start = view.get("byteOffset", 0) + a.get("byteOffset", 0)
    stride = view.get("byteStride", 0) or item
    raw = np.frombuffer(binary, dtype=np.uint8, count=stride * (a["count"] - 1) + item, offset=start)
    rows = np.lib.stride_tricks.as_strided(raw, shape=(a["count"], item), strides=(stride, 1))
    out = np.frombuffer(rows.copy().tobytes(), dtype=dtype).reshape(a["count"], n).astype(np.float64)
    if a.get("normalized") and dtype != np.float32:
        out /= np.iinfo(dtype).max
    return out


def primitives(path):
    """Semua primitif segitiga di scene, sudah dalam koordinat dunia:
    [dict(name, pos N×3, nrm N×3|None, uv N×2|None, idx M×3, color, metallic, roughness, blend, image|None)]."""
    gltf, buffers, image_bytes = read_any(path)
    out = []
    scenes = gltf.get("scenes") or [{"nodes": list(range(len(gltf["nodes"])))}]
    scene = scenes[gltf.get("scene", 0)]

    def visit(i, parent):
        node = gltf["nodes"][i]
        world = parent @ local_matrix(node)
        if "mesh" in node:
            normal_m = np.linalg.inv(world[:3, :3]).T
            for prim in gltf["meshes"][node["mesh"]]["primitives"]:
                if prim.get("mode", 4) != 4:
                    continue
                at = prim["attributes"]
                pos = accessor_any(gltf, buffers, at["POSITION"])
                pos = (np.c_[pos, np.ones(len(pos))] @ world.T)[:, :3]
                nrm = None
                if "NORMAL" in at:
                    nrm = accessor_any(gltf, buffers, at["NORMAL"]) @ normal_m.T
                    nrm /= np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-9)
                uv = accessor_any(gltf, buffers, at["TEXCOORD_0"]) if "TEXCOORD_0" in at else None
                idx = accessor_any(gltf, buffers, prim["indices"]).astype(np.int64).reshape(-1, 3) if "indices" in prim \
                    else np.arange(len(pos)).reshape(-1, 3)
                if np.linalg.det(world[:3, :3]) < 0:
                    idx = idx[:, ::-1]
                mat = gltf["materials"][prim["material"]] if "material" in prim else {}
                pbr = mat.get("pbrMetallicRoughness", {})
                image = None
                if "baseColorTexture" in pbr:
                    source = gltf["textures"][pbr["baseColorTexture"]["index"]].get("source")
                    if source is not None:
                        image = image_bytes(source)
                out.append(dict(
                    name=mat.get("name", ""), pos=pos, nrm=nrm, uv=uv, idx=idx,
                    color=pbr.get("baseColorFactor", [1, 1, 1, 1]),
                    metallic=pbr.get("metallicFactor", 1.0), roughness=pbr.get("roughnessFactor", 1.0),
                    blend=mat.get("alphaMode", "OPAQUE") == "BLEND", mask=mat.get("alphaMode") == "MASK",
                    image=image,
                ))
        for c in node.get("children", []):
            visit(c, world)

    for root in scene["nodes"]:
        visit(root, np.eye(4))
    return out
