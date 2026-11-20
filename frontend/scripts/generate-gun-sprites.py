"""Pixel-art guns held by the agent, in the same chunky outlined style as the agent sprites.

Each gun is drawn on a small grid of art pixels (roles: metal light/mid/dark, wood, accent),
outlined in near-black like the agents, then scaled up by BLOCK so one art pixel matches the
agent sheets' chunky pixels. Every gun gets five skins: the standard finish plus the
proposal's rank gun skins (Recruit bronze, Field Agent silver, Special Agent gold) and the
Legendary engraved black finish. Skins are cosmetic only.

No third-party packages. Run from frontend/:

    python scripts/generate-gun-sprites.py

Writes public/assets/weapons/<gun>-<skin>.png and src/content/gunSpriteMeta.ts (grip pivots).
"""
import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
FRONTEND = os.path.normpath(os.path.join(HERE, ".."))
OUT_DIR = os.path.join(FRONTEND, "public", "assets", "weapons")
META_TS = os.path.join(FRONTEND, "src", "content", "gunSpriteMeta.ts")

BLOCK = 4  # output pixels per art pixel

# ------------------------------------------------------------------ designs (facing right)
# Roles: h metal light, m metal, s metal dark, w wood, W wood dark, g sight/accent, e engraving
# Each entry: (role, x0, y0, x1, y1) inclusive; later entries paint over earlier ones.
GUNS = {
    # Semi-automatic pistol: slide, frame, grip at the back.
    "starter_sidearm": {
        "size": (12, 7),
        "grip": (2.5, 5.0),
        "rects": [
            ("m", 0, 1, 10, 3),
            ("h", 0, 1, 10, 1),
            ("s", 0, 3, 9, 3),
            ("e", 3, 2, 7, 2),
            ("g", 10, 0, 10, 0),
            ("g", 1, 0, 1, 0),
            ("s", 1, 4, 4, 6),
            ("m", 1, 4, 3, 6),
            ("s", 5, 4, 6, 4),
        ],
    },
    # AK-47: wooden stock and handguard, curved magazine, long barrel.
    "assault_rifle": {
        "size": (27, 10),
        "grip": (8.5, 6.0),
        "rects": [
            # stock (slanted)
            ("w", 3, 2, 6, 2),
            ("w", 1, 3, 6, 3),
            ("w", 0, 4, 6, 4),
            ("W", 0, 5, 3, 5),
            ("W", 4, 4, 6, 4),
            # receiver
            ("m", 7, 2, 15, 4),
            ("h", 7, 2, 15, 2),
            ("s", 7, 4, 15, 4),
            ("e", 9, 3, 14, 3),
            # pistol grip
            ("W", 8, 5, 9, 7),
            ("w", 8, 5, 8, 7),
            ("s", 10, 5, 10, 5),
            # curved magazine
            ("s", 12, 5, 14, 5),
            ("m", 12, 6, 14, 6),
            ("m", 13, 7, 15, 7),
            ("s", 14, 8, 15, 8),
            # gas tube + handguard
            ("s", 16, 1, 22, 1),
            ("w", 16, 2, 21, 4),
            ("W", 16, 4, 21, 4),
            # barrel + front sight + muzzle
            ("m", 22, 3, 26, 3),
            ("s", 24, 1, 24, 2),
            ("h", 26, 3, 26, 3),
            ("g", 8, 1, 8, 1),
        ],
    },
    # Pump shotgun: stock, receiver, long barrel over the magazine tube, wooden pump.
    "combat_shotgun": {
        "size": (26, 8),
        "grip": (7.5, 5.0),
        "rects": [
            ("w", 3, 2, 6, 2),
            ("w", 1, 3, 6, 3),
            ("w", 0, 4, 6, 4),
            ("W", 0, 5, 3, 5),
            ("W", 4, 4, 6, 4),
            ("m", 7, 2, 12, 4),
            ("h", 7, 2, 12, 2),
            ("s", 7, 4, 12, 4),
            ("e", 8, 3, 11, 3),
            ("W", 7, 5, 8, 6),
            ("w", 7, 5, 7, 6),
            ("m", 13, 2, 25, 2),
            ("h", 13, 1, 24, 1),
            ("s", 13, 3, 23, 3),
            ("w", 15, 3, 19, 4),
            ("W", 15, 4, 19, 4),
            ("g", 25, 1, 25, 1),
        ],
    },
}

# ------------------------------------------------------------------ skins
OUTLINE = (20, 17, 15, 255)


def hexc(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


SKINS = {
    "standard": {"h": "#8e9aa6", "m": "#5b6670", "s": "#343b42", "w": "#8a5a33", "W": "#5e3a1f", "g": "#d9d9d9", "e": "#5b6670"},
    # Recruit Medal: bronze-plated metal.
    "recruit": {"h": "#e8b27a", "m": "#b9773d", "s": "#7a4a22", "w": "#6e4426", "W": "#4a2c18", "g": "#ffd59a", "e": "#d99a5a"},
    # Field Agent Medal: polished silver with dark grip.
    "field": {"h": "#f4f7fa", "m": "#bcc5ce", "s": "#7d8893", "w": "#3d4148", "W": "#26292e", "g": "#ffffff", "e": "#9aa5b0"},
    # Special Agent Medal: gold with black furniture.
    "special": {"h": "#ffeaa0", "m": "#e2b23a", "s": "#a3761b", "w": "#2a2a31", "W": "#18181d", "g": "#fff6cf", "e": "#c9952a"},
    # Master Spy (Legendary): blacked-out finish with gold engraving.
    "legendary": {"h": "#4c4c55", "m": "#2a2a31", "s": "#16161a", "w": "#1f1f25", "W": "#111114", "g": "#f0c75e", "e": "#e0b24c"},
}


def render(gun, skin):
    gw, gh = gun["size"]
    grid = [[None] * gw for _ in range(gh)]
    for role, x0, y0, x1, y1 in gun["rects"]:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                grid[y][x] = role
    # 1 art-pixel outline around the silhouette (8-neighbourhood), like the agent sheets.
    w, h = gw + 2, gh + 2
    art = [[None] * w for _ in range(h)]
    for y in range(gh):
        for x in range(gw):
            if grid[y][x]:
                art[y + 1][x + 1] = hexc(skin[grid[y][x]])
    for y in range(h):
        for x in range(w):
            if art[y][x] is not None:
                continue
            near = any(
                0 <= y + dy < h and 0 <= x + dx < w and art[y + dy][x + dx] not in (None, OUTLINE)
                for dy in (-1, 0, 1)
                for dx in (-1, 0, 1)
            )
            if near:
                art[y][x] = OUTLINE
    out = []
    for y in range(h):
        row = []
        for x in range(w):
            px = art[y][x] or (0, 0, 0, 0)
            row.extend([px] * BLOCK)
        for _ in range(BLOCK):
            out.append(list(row))
    return out


def write_png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in pixels)

    def chunk(tag, d):
        return struct.pack(">I", len(d)) + tag + d + struct.pack(">I", zlib.crc32(tag + d) & 0xFFFFFFFF)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    meta = []
    for gun_id, gun in GUNS.items():
        for skin_id, skin in SKINS.items():
            write_png(os.path.join(OUT_DIR, f"{gun_id}-{skin_id}.png"), render(gun, skin))
        gw, gh = gun["size"]
        w, h = (gw + 2) * BLOCK, (gh + 2) * BLOCK
        gx, gy = gun["grip"]
        # Origin = the grip, as a fraction of the texture (outline adds one art pixel each side).
        meta.append((gun_id, w, h, round((gx + 1) * BLOCK / w, 4), round((gy + 1) * BLOCK / h, 4)))
    lines = [
        "// Generated by scripts/generate-gun-sprites.py — do not edit by hand.",
        "/** Held-gun textures: size in px and the grip point (texture origin, 0..1). */",
        "export const GUN_SPRITE_META: Record<string, { width: number; height: number; gripX: number; gripY: number }> = {",
    ]
    for gun_id, w, h, ox, oy in meta:
        lines.append(f"  {gun_id}: {{ width: {w}, height: {h}, gripX: {ox}, gripY: {oy} }},")
    lines.append("}")
    with open(META_TS, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print("wrote", len(GUNS) * len(SKINS), "gun sprites and", META_TS)


if __name__ == "__main__":
    main()
