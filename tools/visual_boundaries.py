# SPDX-License-Identifier: AGPL-3.0-only
import struct

def create(root):
    """Small WGS84 polygon + DBF, assigned to synthetic test code FLZ041."""
    data = bytearray(236)
    struct.pack_into('>i', data, 0, 9994)
    struct.pack_into('>i', data, 24, 118)
    struct.pack_into('<ii', data, 28, 1000, 5)
    struct.pack_into('>ii', data, 100, 1, 64)
    struct.pack_into('<i', data, 108, 5)
    struct.pack_into('<iii', data, 144, 1, 5, 0)
    points = [(-81.235, 28.545), (-81.22, 28.545), (-81.22, 28.555),
              (-81.235, 28.555), (-81.235, 28.545)]
    for i, (x, y) in enumerate(points):
        struct.pack_into('<dd', data, 156 + i * 16, x, y)
    (root / 'zones.shp').write_bytes(data)
    dbf = bytearray(73)
    dbf[0] = 3
    struct.pack_into('<IHH', dbf, 4, 1, 65, 7)
    dbf[32:35] = b'UGC'
    dbf[43] = ord('C')
    dbf[48] = 6
    dbf[64] = 13
    dbf[65] = 32
    dbf[66:72] = b'FLZ041'
    dbf[72] = 26
    (root / 'zones.dbf').write_bytes(dbf)
