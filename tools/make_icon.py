#!/usr/bin/env python3
"""Draws the application icon (a flat dark square with a lime alarm clock) and writes the macOS icon file
src/main/dist/mac/WakeMeApp.icns (PNG entries, 16 to 1024 px, no tool of macOS needed).

    python3 tools/make_icon.py

Needs Pillow. The icon is original artwork, drawn here, in the colours of the overlay and of the dialogs.
"""
import io
import math
import struct
from pathlib import Path

from PIL import Image, ImageDraw

DARK = (11, 15, 26, 255)      # #0B0F1A
LIME = (198, 255, 61, 255)    # #C6FF3D
OUT = Path(__file__).resolve().parent.parent / "src/main/dist/mac/WakeMeApp.icns"


def draw(size):
    """The icon at `size` px, drawn at 4x and reduced for smooth edges."""
    s = size * 4
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # the square with the rounded corners of the macOS icons (about 22 %), inside a 10 % margin
    m = int(s * 0.10)
    d.rounded_rectangle((m, m, s - m, s - m), radius=int(s * 0.18), fill=DARK)
    cx, cy = s / 2, s * 0.54
    r = s * 0.25
    w = int(s * 0.045)
    # the two bells on top, then the face
    for sign in (-1, 1):
        a = math.radians(-90 + sign * 42)
        bx, by = cx + math.cos(a) * r * 1.12, cy + math.sin(a) * r * 1.12
        br = s * 0.075
        d.ellipse((bx - br, by - br, bx + br, by + br), fill=LIME)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=LIME, width=w)
    # the hands: 12 and 4 o'clock
    d.line((cx, cy, cx, cy - r * 0.62), fill=LIME, width=w, joint="curve")
    d.line((cx, cy, cx + r * 0.42, cy + r * 0.24), fill=LIME, width=w, joint="curve")
    d.ellipse((cx - w, cy - w, cx + w, cy + w), fill=LIME)
    # the two feet
    for sign in (-1, 1):
        fx = cx + sign * r * 0.62
        d.line((fx, cy + r * 1.02, fx + sign * r * 0.22, cy + r * 1.3), fill=LIME, width=w)
    return img.resize((size, size), Image.LANCZOS)


# icns entry types holding a PNG, and the pixel size of each
ENTRIES = [("icp4", 16), ("icp5", 32), ("icp6", 64), ("ic07", 128), ("ic08", 256), ("ic09", 512), ("ic10", 1024),
           ("ic11", 32), ("ic12", 64), ("ic13", 256), ("ic14", 512)]


def main():
    body = b""
    for kind, size in ENTRIES:
        buf = io.BytesIO()
        draw(size).save(buf, "PNG")
        data = buf.getvalue()
        body += kind.encode("ascii") + struct.pack(">I", 8 + len(data)) + data
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(b"icns" + struct.pack(">I", 8 + len(body)) + body)
    print("wrote", OUT, len(body) + 8, "bytes")


if __name__ == "__main__":
    main()
