#!/usr/bin/env python3
"""
EARTH-X data pipeline.

Converts public-domain Natural Earth datasets (https://www.naturalearthdata.com)
and the `world-countries` dataset (mledoze/countries, ODbL 1.0) into the compact
JSON files bundled inside the APK under app/src/main/assets/web/data/.

Usage:
    python3 tools/build_data.py <raw_dir> <world_countries_json> <out_dir>

<raw_dir> must contain the Natural Earth GeoJSON files plus the mapshaper
outputs produced by tools/fetch_data.sh.
"""
import json
import math
import os
import sys

RAW, WC, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(OUT, exist_ok=True)


def load(name):
    with open(os.path.join(RAW, name), encoding="utf-8") as f:
        return json.load(f)


def dump(name, obj):
    path = os.path.join(OUT, name)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{name}: {os.path.getsize(path)/1024:.0f} KB")


def r2(v):
    return round(v, 2)


def rings_of(geom):
    """Return list of polygons, each a list of rings [[lon,lat],...]."""
    if geom is None:
        return []
    t = geom["type"]
    if t == "Polygon":
        polys = [geom["coordinates"]]
    elif t == "MultiPolygon":
        polys = geom["coordinates"]
    else:
        return []
    out = []
    for poly in polys:
        out.append([[[r2(x), r2(y)] for x, y in ring] for ring in poly])
    return out


def nice(name):
    if name and name.isupper():
        small = {"of", "the", "and", "de", "du", "la"}
        words = name.lower().split()
        name = " ".join(w if (i and w in small) else w[:1].upper() + w[1:] for i, w in enumerate(words))
        name = name.replace("Mts.", "Mountains").replace("Mts", "Mountains")
    return name


# ---------------------------------------------------------------- countries
ne = load("ne_50m_admin_0_countries.geojson")
simpl = load("countries_simpl.geojson")
wc = {c["cca3"]: c for c in json.load(open(WC, encoding="utf-8"))}
ALIAS = {"SDS": "SSD", "PSX": "PSE", "SAH": "ESH", "KOS": "UNK", "SDN": "SDN", "CYN": None,
         "SOL": None, "KAB": None, "ALD": "ALA", "PRI": "PRI"}

places = load("ne_10m_populated_places_simple.geojson")["features"]
capital_pt = {}
for f in places:
    p = f["properties"]
    if p.get("adm0cap") == 1 and p["adm0_a3"] not in capital_pt:
        capital_pt[p["adm0_a3"]] = (p["latitude"], p["longitude"], p["name"])


def area_of(rings):
    """Spherical polygon area in km^2 (fallback when no dataset value)."""
    R = 6371.0088
    total = 0.0
    for poly in rings:
        for k, ring in enumerate(poly):
            a = 0.0
            for i in range(len(ring) - 1):
                lon1, lat1 = map(math.radians, ring[i])
                lon2, lat2 = map(math.radians, ring[i + 1])
                a += (lon2 - lon1) * (2 + math.sin(lat1) + math.sin(lat2))
            a = abs(a * R * R / 2)
            total += a if k == 0 else -a
    return total


geo_by_id = {f["properties"]["ADM0_A3"]: f["geometry"] for f in simpl["features"]}
countries, shapes = [], []
for f in ne["features"]:
    p = f["properties"]
    cid = p["ADM0_A3"]
    key = ALIAS.get(cid, cid)
    w = wc.get(key) or {}
    if not w and p.get("ADMIN") == p.get("SOVEREIGNT"):
        # ISO fallback only for non-dependencies (avoid e.g. Ashmore Is. inheriting Australia's data)
        w = wc.get(p.get("ISO_A3_EH")) or wc.get(p.get("ISO_A3")) or {}
    rings = rings_of(geo_by_id.get(cid))
    if not rings:
        continue
    cap = capital_pt.get(cid)
    capital_names = w.get("capital") or ([cap[2]] if cap else [])
    entry = {
        "id": cid,
        "name": p["NAME_LONG"] or p["NAME"],
        "short": p["NAME"],
        "official": (w.get("name") or {}).get("official"),
        "iso2": p["ISO_A2"] if p["ISO_A2"] != "-99" else w.get("cca2"),
        "flag": w.get("flag", ""),
        "capital": ", ".join(capital_names),
        "capLat": r2(cap[0]) if cap else None,
        "capLon": r2(cap[1]) if cap else None,
        "pop": p.get("POP_EST") or None,
        "popYear": p.get("POP_YEAR") or None,
        "area": w.get("area") if w.get("area") and w.get("area") > 0 else round(area_of(rings)),
        "areaSrc": "world-countries" if w.get("area") and w.get("area") > 0 else "computed",
        "continent": p["CONTINENT"],
        "subregion": p["SUBREGION"],
        "lat": r2(p["LABEL_Y"]),
        "lon": r2(p["LABEL_X"]),
        "currencies": [f'{v["name"]} ({k}{(" " + v["symbol"]) if v.get("symbol") else ""})'
                       for k, v in (w.get("currencies") or {}).items()],
        "languages": list((w.get("languages") or {}).values()),
        "landlocked": w.get("landlocked"),
        "borders": w.get("borders", []),
        "tld": (w.get("tld") or [None])[0],
        "calling": ((w.get("idd") or {}).get("root", "") + ((w.get("idd") or {}).get("suffixes") or [""])[0]) if (w.get("idd") or {}).get("suffixes") and len(w["idd"]["suffixes"]) == 1 else (w.get("idd") or {}).get("root"),
        "gdpMd": p.get("GDP_MD") or None,
        "type": p.get("TYPE"),
        "sovereign": p.get("SOVEREIGNT"),
    }
    # bounding box for zoom-to-country
    xs = [pt[0] for poly in rings for pt in poly[0]]
    ys = [pt[1] for poly in rings for pt in poly[0]]
    entry["bbox"] = [min(xs), min(ys), max(xs), max(ys)]
    countries.append(entry)
    shapes.append({"id": cid, "p": rings})

countries.sort(key=lambda c: c["name"])
dump("countries.json", countries)
dump("countries.geo.json", shapes)

# ---------------------------------------------------------------- states
st = load("states_simpl.geojson")
states = []
for f in st["features"]:
    p = f["properties"]
    rings = rings_of(f["geometry"])
    if not rings:
        continue
    states.append({"n": p.get("name") or "", "c": p.get("adm0_a3"), "t": p.get("type_en") or "",
                   "lat": r2(p.get("latitude") or 0), "lon": r2(p.get("longitude") or 0), "p": rings})
dump("states.geo.json", states)

# ---------------------------------------------------------------- cities
cities = []
for f in places:
    p = f["properties"]
    pop = p.get("pop_max") or 0
    cap = p.get("adm0cap") == 1
    if pop < 150000 and not cap:
        continue
    cities.append({"n": p["name"], "c": p["adm0_a3"], "cn": p["adm0name"], "a1": p.get("adm1name") or "",
                   "lat": round(p["latitude"], 4), "lon": round(p["longitude"], 4), "pop": pop,
                   "cap": 1 if cap else 0, "r": p.get("scalerank", 10), "w": p.get("worldcity", 0) or 0})
cities.sort(key=lambda c: -c["pop"])
dump("cities.json", cities)

# ---------------------------------------------------------------- geography: mountains, oceans, landmarks
geo = []
reg = load("regions_pts.geojson")
KIND = {"Range/mtn": "mountain", "Plateau": "plateau", "Desert": "desert", "Continent": "continent",
        "Basin": "basin", "Plain": "plain", "Peninsula": "peninsula", "Pen/cape": "peninsula",
        "Valley": "valley", "Delta": "delta", "Geoarea": "region", "Tundra": "region", "Lowland": "plain",
        "Foothills": "mountain", "Gorge": "landmark", "Depression": "landmark", "Wetlands": "region",
        "Isthmus": "landmark", "Coast": "region", "Lake": "lake"}
for f in reg["features"]:
    p = f["properties"]
    k = KIND.get(p["FEATURECLA"])
    if not k or not f["geometry"]:
        continue
    lon, lat = f["geometry"]["coordinates"]
    geo.append({"n": nice(p["NAME"]), "k": k, "lat": r2(lat), "lon": r2(lon), "r": p.get("SCALERANK", 9),
                "reg": p.get("REGION") or ""})
mar = load("marine_pts.geojson")
for f in mar["features"]:
    p = f["properties"]
    if not f["geometry"] or not p.get("name"):
        continue
    lon, lat = f["geometry"]["coordinates"]
    k = "ocean" if p["featurecla"] == "ocean" else "sea"
    geo.append({"n": nice(p["name"]), "k": k, "lat": r2(lat), "lon": r2(lon), "r": p.get("scalerank", 9), "reg": ""})
pts = load("ne_10m_geography_regions_points.geojson")
for f in pts["features"]:
    p = f["properties"]
    if p["featurecla"] in ("waterfall", "pole"):
        lon, lat = f["geometry"]["coordinates"]
        geo.append({"n": p["name"], "k": "landmark", "lat": r2(lat), "lon": r2(lon), "r": 3, "reg": ""})
dump("geography.json", geo)

mt = load("mountains_simpl.geojson")
mountains = [{"n": nice(f["properties"]["NAME"]), "k": f["properties"]["FEATURECLA"], "r": f["properties"].get("SCALERANK", 9),
              "p": rings_of(f["geometry"])} for f in mt["features"] if f["geometry"]]
dump("mountains.geo.json", mountains)
print("countries", len(countries), "states", len(states), "cities", len(cities), "geo", len(geo))

# ---------------------------------------------------------------- time zones (city + capital), tectonic plates
full = load("ne_10m_populated_places.geojson")["features"]
tz_by_id = {f["properties"]["NE_ID"]: f["properties"].get("TIMEZONE") for f in full}
id_by_key = {(f["properties"]["name"], f["properties"]["adm0_a3"]): f["properties"]["ne_id"] for f in places}
for c in cities:
    c["tz"] = tz_by_id.get(id_by_key.get((c["n"], c["c"]))) or ""
dump("cities.json", cities)
cap_tz = {}
for f in places:
    p = f["properties"]
    if p.get("adm0cap") == 1:
        cap_tz.setdefault(p["adm0_a3"], tz_by_id.get(p["ne_id"]) or "")
for c in countries:
    c["tz"] = cap_tz.get(c["id"], "")
dump("countries.json", countries)

pb = load("pb.json")
plates = []
for f in pb["features"]:
    g = f["geometry"]
    lines = [g["coordinates"]] if g["type"] == "LineString" else g["coordinates"]
    for ln in lines:
        plates.append({"n": f["properties"].get("Name", ""), "l": [[r2(x), r2(y)] for x, y in ln]})
dump("plates.geo.json", plates)
