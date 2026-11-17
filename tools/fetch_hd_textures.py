#!/usr/bin/env python3
"""
Fetch NASA "Ultra HD" Earth textures at build time and downsample them to 8192x4096
(the largest size widely supported by mobile GPUs, GL_MAX_TEXTURE_SIZE >= 8192).

Sources (NASA Visible Earth, public domain):
  * Blue Marble Next Generation w/ Topography and Bathymetry, December 2004
    https://visibleearth.nasa.gov/images/73909
  * Earth at Night (Black Marble) 2016 colour composite
    https://visibleearth.nasa.gov/images/144898

If a download fails, the app simply keeps using the bundled 4K textures
(the "Ultra HD" quality option then falls back to 4K).
"""
import io, os, sys, urllib.request
from PIL import Image

Image.MAX_IMAGE_PIXELS = None
OUT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/web/textures"

DAY = [
    "https://eoimages.gsfc.nasa.gov/images/imagerecords/73000/73909/world.topo.bathy.200412.3x21600x10800.jpg",
    "https://eoimages.gsfc.nasa.gov/images/imagerecords/73000/73909/world.topo.bathy.200412.3x5400x2700.jpg",
]
NIGHT = [
    "https://eoimages.gsfc.nasa.gov/images/imagerecords/144000/144898/BlackMarble_2016_3km.jpg",
    "https://eoimages.gsfc.nasa.gov/images/imagerecords/144000/144898/BlackMarble_2016_01deg.jpg",
]


def fetch(urls):
    for u in urls:
        try:
            print("downloading", u, flush=True)
            req = urllib.request.Request(u, headers={"User-Agent": "EARTH-X build (github actions)"})
            with urllib.request.urlopen(req, timeout=300) as r:
                data = r.read()
            img = Image.open(io.BytesIO(data))
            img.load()
            print(" ->", img.size, flush=True)
            return img, u
        except Exception as e:  # noqa
            print("   failed:", e, flush=True)
    return None, None


def save(img, name, q):
    w = min(8192, img.size[0])
    img = img.convert("RGB").resize((w, w // 2), Image.LANCZOS)
    path = os.path.join(OUT, name)
    img.save(path, quality=q, optimize=True, progressive=True)
    print("wrote", path, img.size, os.path.getsize(path) // 1024, "KB")


day, du = fetch(DAY)
night, nu = fetch(NIGHT)
if day is not None and day.size[0] >= 5400:
    save(day, "earth_day_8k.jpg", 90)
    if night is not None:
        save(night, "earth_night_8k.jpg", 88)
    else:
        # keep night texture consistent: upscale bundled 4K night lights is NOT done (no fake detail)
        print("night 8K unavailable; engine will use earth_night_4k.jpg")
else:
    print("HD day texture unavailable; Ultra HD falls back to bundled 4K textures")
