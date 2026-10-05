#!/usr/bin/env python3
"""Ekspor templat kepala standar MediaPipe (.glb) untuk membuat efek wajah 3D di Blender dkk.

Isi: jaring wajah (468 titik, peta UV + tekstur kisi UV resmi MediaPipe) dan kepala tak terlihat
(penutup yang dipakai aplikasi, transparan). Satuan: 1 unit = 1 cm; +X kiri orangnya (kanan
penonton), +Y atas, +Z depan (ke kamera).

Cara pakai di Blender: File > Import > glTF 2.0 -> modelkan aset di kepala ini -> HAPUS kedua objek
templat -> File > Export > glTF 2.0 (.glb, "+Y Up" bawaan). Hasilnya langsung dipakai tanpa
konfigurasi: FaceEffect("Nama", "…/nama.glb").

    python3 tools/export_head_template.py <hasil.glb> [kisi-uv.png]
"""
import math
import sys

import numpy as np

import face_mesh
import fit_face_props as fit


def head_shell():
    """Kepala tak terlihat (superelips), sama dengan MeshRenderer.HEAD_OCCLUDER."""
    pos, tris = fit.head_mesh()
    return pos, tris


def main(out, uv_grid=None):
    pos, uv, tris = face_mesh.load()
    image = open(uv_grid, "rb").read() if uv_grid else None
    shell_pos, shell_tris = head_shell()
    prims = [
        dict(name="TEMPLAT_wajah_hapus_sebelum_ekspor", pos=pos, nrm=None, uv=uv, idx=tris,
             color=[1, 1, 1, 1], metallic=0.0, roughness=0.8, blend=False, mask=False, image=image),
        dict(name="TEMPLAT_kepala_hapus_sebelum_ekspor", pos=shell_pos, nrm=None, uv=None, idx=shell_tris,
             color=[0.55, 0.5, 0.48, 0.35], metallic=0.0, roughness=1.0, blend=True, mask=False, image=None),
    ]
    fit.write(prims, out)
    print(out)


if __name__ == "__main__":
    main(*sys.argv[1:])
