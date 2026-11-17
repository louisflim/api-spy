"""Builds the extra agent outfits, enemies and bosses FROM THE PROJECT'S OWN ART.

  * Agents / outfits  <- public/assets/characters/rookie-detective.png
      Material-aware recolouring (hat, coat, shoes, trousers, tie, shirt, skin) that keeps the
      original shading, plus hand-placed additions (the girl's long hair and hat ribbon).
  * Enemies / bosses  <- public/assets/enemies/security-patrol-guard.png
      Armour / visor / light recolouring plus pixel weapons and emblems drawn on each frame.

No third-party packages (pure-Python PNG read/write). Run from frontend/:

    python scripts/derive-sprites.py

Outputs keep the source layouts, so the game loads them exactly like the originals:
agents 4x4 frames of 272x272 (rows down/left/right/up), enemies 4x6 frames of 32x40.
"""
import colorsys
import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "public", "assets"))
ROOKIE = os.path.join(ASSETS, "characters", "rookie-detective.png")
GUARD = os.path.join(ASSETS, "enemies", "security-patrol-guard.png")


# =============================================================== PNG I/O (no PIL)
def read_png(path):
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    pos, idat = 8, b""
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag, body = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            assert depth == 8 and interlace == 0 and ctype in (2, 6), (path, depth, ctype)
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break
    bpp = 4 if ctype == 6 else 3
    raw, stride = zlib.decompress(idat), width * bpp
    rows, prev, i = [], bytearray(stride), 0
    for _ in range(height):
        f = raw[i]
        cur = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = cur[x - bpp] if x >= bpp else 0
            b, c = prev[x], (prev[x - bpp] if x >= bpp else 0)
            if f == 1:
                cur[x] = (cur[x] + a) & 255
            elif f == 2:
                cur[x] = (cur[x] + b) & 255
            elif f == 3:
                cur[x] = (cur[x] + ((a + b) >> 1)) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                cur[x] = (cur[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(cur)
        prev = cur
    if bpp == 4:
        return [[tuple(r[x:x + 4]) for x in range(0, stride, 4)] for r in rows]
    return [[tuple(r[x:x + 3]) + (255,) for x in range(0, stride, 3)] for r in rows]


def write_png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in pixels)

    def chunk(tag, d):
        return struct.pack(">I", len(d)) + tag + d + struct.pack(">I", zlib.crc32(tag + d) & 0xFFFFFFFF)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def hsv(p):
    return colorsys.rgb_to_hsv(p[0] / 255, p[1] / 255, p[2] / 255)


def recolor(p, target_hex, ref_v):
    """Keep the pixel's shading (value) but take hue/saturation from the target colour."""
    _, _, v = hsv(p)
    th, ts, tv = colorsys.rgb_to_hsv(*(c / 255 for c in rgb(target_hex)))
    r, g, b = colorsys.hsv_to_rgb(th, ts, max(0.0, min(1.0, tv * (v / ref_v))))
    return (int(r * 255), int(g * 255), int(b * 255), p[3])


# =============================================================== agents (from the Rookie)
FRAME = 272

# Outfit keys: hat, coat, shoes, pants, tie, shirt, skin; optional longHair ramp + ribbon.
AGENTS = {
    "agent-boy": dict(hat="#2b4a6b", coat="#2f6f8f", pants="#2b3440", tie="#f2b134", skin="#c98e64"),
    "agent-girl": dict(hat="#7e2c3a", coat="#a23a4a", pants="#2c2a38", tie="#f2d27a", skin="#f2c7a4",
                       longHair=("#4e2418", "#7a3a26", "#a65438"), ribbon=("#f6d6e0", "#e86a9a")),
    # Rank skins (proposal: all 21 missions of a rank)
    "skin-recruit": dict(hat="#5a3a1c", coat="#a0682c", pants="#3a2e22", tie="#e0a050", shoes="#3a2412"),
    "skin-field": dict(hat="#4a5560", coat="#8c97a3", pants="#2a323c", tie="#2f6f8f"),
    "skin-special": dict(hat="#14171f", coat="#1d2230", pants="#141820", tie="#ffd23f", shirt="#f4e6b0"),
    # Legendary (all 63 missions): original black-suit operative named after the folklore figure
    "skin-baba-yaga": dict(hat="#1a1a1f", coat="#16161a", pants="#141418", tie="#0e0e10", shirt="#f2f2f2",
                           shoes="#0c0c0e"),
}

REF_V = {"brown": 0.52, "skin": 0.92, "tie": 0.36, "shirt": 0.93, "slate": 0.24}


def material(p):
    if p[3] < 40:
        return None
    h, s, v = hsv(p)
    hd = h * 360
    if v < 0.13:
        return "line"
    if (hd >= 340 or hd <= 8) and s > 0.45 and v > 0.2:
        return "tie"
    if 18 <= hd <= 45 and 0.18 <= s <= 0.5 and v > 0.72:
        return "skin"
    if s < 0.22 and v > 0.78:
        return "shirt"
    if (hd >= 280 or hd <= 8) and s < 0.4 and v < 0.45:
        return "slate"
    if 5 <= hd <= 32 and s >= 0.35 and v <= 0.78:
        return "brown"
    return "other"


def face_top(px, fx, fy):
    for y in range(40, 170):
        run = 0
        for x in range(fx, fx + FRAME):
            if material(px[fy + y][x]) == "skin":
                run += 1
                if run >= 6:
                    return y
            else:
                run = 0
    return 110


def face_box(px, fx, fy, top):
    xs = sorted(x for y in range(top, min(top + 45, FRAME)) for x in range(FRAME)
                if material(px[fy + y][fx + x]) == "skin")
    if not xs:
        return 100, 172
    return xs[len(xs) // 20], xs[-len(xs) // 20 - 1]


def derive_agent(outfit, px):
    out = [list(r) for r in px]
    for row_i in range(4):
        for col in range(4):
            fx, fy = col * FRAME, row_i * FRAME
            # Front-view face line for every direction: side frames can catch a light hat
            # highlight as "skin", which would cut the hat in half.
            top = face_top(px, col * FRAME, 0)
            hat_line = top + 6
            for y in range(FRAME):
                for x in range(FRAME):
                    p = px[fy + y][fx + x]
                    m = material(p)
                    if m in (None, "line", "other"):
                        continue
                    if m == "brown":
                        if hat_line <= y < top + 34:
                            continue  # hair locks beside the face share the coat brown: keep them
                        key = "hat" if y < hat_line else ("shoes" if y > 232 else "coat")
                        ref = REF_V["brown"]
                    elif m == "slate":
                        if y < top + 70:  # hat band / hair share this tone: leave them
                            continue
                        key, ref = "pants", REF_V["slate"]
                    else:
                        key, ref = m, REF_V[m]
                    if outfit.get(key):
                        out[fy + y][fx + x] = recolor(p, outfit[key], ref)
    if outfit.get("longHair"):
        add_long_hair(out, px, [rgb(c) for c in outfit["longHair"]],
                      [rgb(c) for c in outfit["ribbon"]] if outfit.get("ribbon") else None)
    return out


def block(out, fx, fy, x, y, color, size=4):
    for yy in range(y, y + size):
        for xx in range(x, x + size):
            if 0 <= xx < FRAME and 0 <= yy < FRAME:
                out[fy + yy][fx + xx] = tuple(color) + (255,)


def hair_mass(out, fx, fy, rows, ramp, outline=(22, 12, 12)):
    cells = {}
    for y, xl, xr in rows:
        xl, xr, y = xl - xl % 4, xr - xr % 4, y - y % 4
        for x in range(xl, xr + 1, 4):
            cells[(x, y)] = (xl, xr)
    for (x, y), (xl, xr) in cells.items():
        if any((x + dx, y + dy) not in cells for dx, dy in ((4, 0), (-4, 0), (0, 4), (0, -4))):
            color = outline
        elif x in (xl + 4, xr - 4):
            color = ramp[0]
        elif ((x - xl) // 4) % 3 == 1:
            color = ramp[2]
        else:
            color = ramp[1]
        block(out, fx, fy, x, y, color)


def taper(y0, length, cx, w_top, w_bottom, curl=0, lean=0):
    rows, steps = [], max(1, length // 4)
    for i in range(steps + 1):
        t = i / steps
        w = (w_top + (w_bottom - w_top) * t) * (0.6 if i == steps else 1)
        x = cx + lean * t + (curl * (t - 0.75) * 4 if t > 0.75 else 0)
        rows.append((y0 + i * 4, int(x - w / 2), int(x + w / 2)))
    return rows


def add_long_hair(out, px, ramp, ribbon):
    for col in range(4):
        top = face_top(px, col * FRAME, 0)
        for row_i in range(4):
            fx, fy = col * FRAME, row_i * FRAME
            left, right = face_box(px, fx, fy, top)
            y0 = top - 2
            if row_i == 0:
                hair_mass(out, fx, fy, taper(y0, 60, left - 4, 18, 12, curl=-1, lean=-4), ramp)
                hair_mass(out, fx, fy, taper(y0, 60, right + 4, 18, 12, curl=1, lean=4), ramp)
            elif row_i == 2:
                hair_mass(out, fx, fy, taper(y0 - 4, 66, left - 12, 26, 16, curl=-1, lean=-6), ramp)
            elif row_i == 1:
                hair_mass(out, fx, fy, taper(y0 - 4, 66, right + 12, 26, 16, curl=1, lean=6), ramp)
            else:
                hair_mass(out, fx, fy, taper(y0 - 8, 74, 136, 70, 44), ramp)
            if ribbon and row_i != 3:
                bx, by = {0: right - 6, 1: right - 18, 2: left + 6}[row_i], top - 26
                for dx, dy, c in ((0, 0, 1), (4, 0, 0), (-4, 0, 0), (0, 4, 0),
                                  (-8, -4, 1), (8, -4, 1), (-8, 4, 1), (8, 4, 1)):
                    block(out, fx, fy, bx + dx, by + dy, ribbon[c])


# =============================================================== enemies / bosses (from the guard)
FW, FH = 32, 40

ENEMIES = {
    "rifleman": dict(armor="#3f5232", visor="#ff9a3c", light="#c9d18a", gear="rifle"),
    "shock-enforcer": dict(armor="#6b1f2c", visor="#7af5ff", light="#ffd23f", gear="baton"),
    "boss-nexus-warden": dict(armor="#4a1420", visor="#ff3d6e", light="#ffb3c4", gear="spikes"),
    "boss-packet-juggernaut": dict(armor="#2d5f6b", visor="#7dffb0", light="#f2b134", gear="cannons"),
    "boss-cipher-queen": dict(armor="#3b2458", visor="#ff6bd5", light="#ffd23f", gear="staff"),
}

STEEL, STEEL_HI, WOOD = "#2e3138", "#5d626d", "#6a4e30"


def enemy_material(p):
    if p[3] == 0:
        return None
    h, s, v = hsv(p)
    hd = h * 360
    if 175 <= hd <= 200 and v > 0.6:
        return "visor"
    if (hd >= 340 or hd <= 10) and s > 0.5:
        return "light"
    if 200 <= hd <= 240 and s >= 0.4:
        return "armor"
    return None


def put(frame, x, y, color, w=1, h=1):
    c = rgb(color) + (255,)
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            if 0 <= xx < FW and 0 <= yy < FH:
                frame[yy][xx] = c


def outline_new(frame, before):
    """Dark outline around pixels that were added on top of the sprite."""
    ink = (12, 14, 20, 255)
    added = {(x, y) for y in range(FH) for x in range(FW) if frame[y][x] != before[y][x] and frame[y][x][3] > 0}
    for x, y in added:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < FW and 0 <= ny < FH and frame[ny][nx][3] == 0:
                frame[ny][nx] = ink


def draw_gear(frame, gear, view, attack, e):
    """view: down | right | up (left frames are mirrored from right). Coordinates follow the guard art."""
    glow, hot = e["light"], e["visor"]
    if gear == "rifle":
        if view == "down":
            put(frame, 24, 19, STEEL, 4, 3); put(frame, 25, 22, STEEL, 2, 10 if not attack else 12)
            put(frame, 25, 22, STEEL_HI, 1, 8)
            if attack:
                put(frame, 24, 34, "#ffd23f", 4, 2); put(frame, 25, 36, "#fff4b0", 2, 1)
        elif view == "right":
            put(frame, 16, 22, STEEL, 6, 3); put(frame, 21, 21, STEEL, 9 if not attack else 10, 2)
            put(frame, 21, 21, STEEL_HI, 8, 1)
            if attack:
                put(frame, 30, 20, "#ffd23f", 2, 4)
        else:
            for i in range(10):
                put(frame, 9 + i, 16 + i, STEEL)
    elif gear == "baton":
        if view == "down":
            if attack:
                put(frame, 26, 9, "#26262c", 2, 12); put(frame, 25, 6, "#fff7a8", 4, 3)
            else:
                put(frame, 26, 20, "#26262c", 2, 10); put(frame, 26, 18, glow, 2, 2)
        elif view == "right":
            if attack:
                put(frame, 24, 22, "#26262c", 6, 2); put(frame, 29, 21, "#fff7a8", 3, 4)
            else:
                put(frame, 27, 18, "#26262c", 2, 9); put(frame, 27, 16, glow, 2, 2)
        else:
            put(frame, 5, 20, "#26262c", 2, 8)
    elif gear == "spikes":
        for x in (5, 9, 22, 26):
            put(frame, x, 13, glow, 2, 2)
        if view != "up":
            put(frame, 13, 2, glow, 6, 2)
    elif gear == "cannons":
        if view == "down":
            for x in (0, 27):
                put(frame, x, 12, STEEL, 5, 7); put(frame, x + 1, 19, hot if attack else STEEL_HI, 3, 2)
            put(frame, 11, 18, glow, 1, 6); put(frame, 10, 20, glow, 1, 2)
            put(frame, 20, 18, glow, 1, 6); put(frame, 21, 20, glow, 1, 2)
        elif view == "right":
            put(frame, 14, 10, STEEL, 15, 4); put(frame, 29, 10, hot if attack else STEEL_HI, 3, 4)
        else:
            for x in (0, 27):
                put(frame, x, 12, STEEL, 5, 7)
    elif gear == "staff":
        crown = [(12, 2), (15, 1), (18, 2)] if view != "right" else [(13, 2), (16, 1), (19, 2)]
        for x, y in crown:
            put(frame, x, y, glow, 2, 2)
        put(frame, 12, 3, glow, 8, 1) if view != "right" else put(frame, 13, 3, glow, 8, 1)
        sx = 29 if view != "up" else 2
        put(frame, sx, 6, WOOD, 2, 30)
        put(frame, sx - 1, 2, hot if attack else glow, 4, 4)
        if view == "down":
            put(frame, 15, 18, glow, 2, 2); put(frame, 15, 20, glow, 1, 4); put(frame, 16, 22, glow, 1, 1)


def derive_enemy(e, src):
    out = [list(r) for r in src]
    refs = {"armor": 0.23, "visor": 0.78, "light": 0.6}
    for y in range(len(src)):
        for x in range(len(src[0])):
            m = enemy_material(src[y][x])
            if m:
                out[y][x] = recolor(src[y][x], e[m], refs[m])
    # (frame index -> view, attack, mirrored)
    layout = {}
    for col in range(4):
        layout[0 * 4 + col] = ("down", False, False)
        layout[1 * 4 + col] = ("right", False, True)
        layout[2 * 4 + col] = ("right", False, False)
        layout[3 * 4 + col] = ("up", False, False)
    layout.update({16: ("down", True, False), 17: ("right", True, True), 18: ("right", True, False),
                   19: ("up", True, False), 20: ("down", False, False)})  # 21-23 = defeat fade (left as-is)
    for idx, (view, attack, mirrored) in layout.items():
        fx, fy = (idx % 4) * FW, (idx // 4) * FH
        frame = [row[fx:fx + FW] for row in out[fy:fy + FH]]
        if mirrored:
            frame = [list(reversed(r)) for r in frame]
        before = [list(r) for r in frame]
        draw_gear(frame, e["gear"], view, attack, e)
        outline_new(frame, before)
        if mirrored:
            frame = [list(reversed(r)) for r in frame]
        for yy in range(FH):
            out[fy + yy][fx:fx + FW] = frame[yy]
    return out


def main():
    rookie = read_png(ROOKIE)
    for name, outfit in AGENTS.items():
        write_png(os.path.join(ASSETS, "characters", f"{name}.png"), derive_agent(outfit, rookie))
        print("characters/" + name + ".png")
    guard = read_png(GUARD)
    for name, e in ENEMIES.items():
        folder = "bosses" if name.startswith("boss-") else "enemies"
        write_png(os.path.join(ASSETS, folder, f"{name}.png"), derive_enemy(e, guard))
        print(folder + "/" + name + ".png")


if __name__ == "__main__":
    main()
