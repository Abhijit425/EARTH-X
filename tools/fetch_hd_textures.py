#!/usr/bin/env python3
"""
Fetch NASA "Ultra HD" Earth textures at build time and produce 8192x4096 JPEGs
(the largest size widely supported by mobile GPUs: GL_MAX_TEXTURE_SIZE >= 8192).

1st choice — NASA Visible Earth (public domain):
  * Blue Marble Next Generation w/ Topography & Bathymetry (Dec 2004)  https://visibleearth.nasa.gov/images/73909
  * Black Marble 2016 colour composite                                  https://visibleearth.nasa.gov/images/144898
2nd choice — the same NASA products served by GIBS WMS (EPSG:4326), fetched as four
4096x2048 quadrants and stitched:  BlueMarble_ShadedRelief_Bathymetry / VIIRS_Black_Marble.

If everything fails the app keeps the bundled 4K textures ("Ultra HD" then falls back to 4K).
No upscaling is ever performed — only genuine high-resolution sources are used.
"""
import io, os, subprocess, sys
from PIL import Image

Image.MAX_IMAGE_PIXELS = None
OUT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/web/textures"
GIBS = "https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi"


def curl(url, timeout=420):
    print("GET", url, flush=True)
    r = subprocess.run(["curl", "-4", "-fsSL", "--retry", "2", "--connect-timeout", "20", "--max-time", str(timeout),
                        "-A", "EARTH-X build (GitHub Actions)", url], capture_output=True)
    if r.returncode != 0:
        print("   failed:", r.returncode, r.stderr.decode()[:200], flush=True)
        return None
    try:
        img = Image.open(io.BytesIO(r.stdout)); img.load()
        print("   ->", img.size, flush=True)
        return img
    except Exception as e:  # noqa
        print("   not an image:", e, flush=True)
        return None


def gibs_stitched(layer, fmt="image/jpeg"):
    full = Image.new("RGB", (8192, 4096))
    for row, (s, n) in enumerate([(0, 90), (-90, 0)]):
        for col, (w, e) in enumerate([(-180, 0), (0, 180)]):
            url = (f"{GIBS}?SERVICE=WMS&REQUEST=GetMap&VERSION=1.1.1&LAYERS={layer}&STYLES=&SRS=EPSG:4326"
                   f"&BBOX={w},{s},{e},{n}&WIDTH=4096&HEIGHT=4096&FORMAT={fmt}")
            tile = curl(url, 300)
            if tile is None:
                return None
            full.paste(tile.convert("RGB").resize((4096, 2048), Image.LANCZOS), (col * 4096, row * 2048))
    return full


def save(img, name, q):
    w = min(8192, img.size[0])
    img = img.convert("RGB").resize((w, w // 2), Image.LANCZOS)
    path = os.path.join(OUT, name)
    img.save(path, quality=q, optimize=True, progressive=True)
    print("wrote", path, img.size, os.path.getsize(path) // 1024, "KB", flush=True)


day = curl("https://eoimages.gsfc.nasa.gov/images/imagerecords/73000/73909/world.topo.bathy.200412.3x21600x10800.jpg")
if day is None or day.size[0] < 8192:
    day = gibs_stitched("BlueMarble_ShadedRelief_Bathymetry")
night = curl("https://eoimages.gsfc.nasa.gov/images/imagerecords/144000/144898/BlackMarble_2016_3km.jpg")
if night is None or night.size[0] < 8192:
    night = gibs_stitched("VIIRS_Black_Marble", "image/png")

if day is not None:
    save(day, "earth_day_8k.jpg", 90)
    if night is not None:
        save(night, "earth_night_8k.jpg", 88)
else:
    print("HD day texture unavailable; Ultra HD falls back to bundled 4K textures")
