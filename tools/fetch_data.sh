#!/usr/bin/env bash
# Rebuild the bundled geography (only needed when updating datasets).
# Requires: curl, node/npm, python3.
set -euo pipefail
RAW=${1:-/tmp/earthx-raw}
mkdir -p "$RAW" && cd "$RAW"
B=https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson
for f in ne_50m_admin_0_countries ne_10m_populated_places_simple ne_10m_populated_places ne_10m_admin_1_states_provinces \
         ne_10m_geography_regions_points ne_10m_geography_regions_polys ne_10m_geography_marine_polys; do
  curl -sSL -o $f.geojson $B/$f.geojson
done
curl -sSL -o pb.json https://raw.githubusercontent.com/fraxen/tectonicplates/master/GeoJSON/PB2002_boundaries.json
npm i --no-save mapshaper world-countries >/dev/null
MS=./node_modules/.bin/mapshaper
$MS ne_50m_admin_0_countries.geojson -filter-fields ADM0_A3,NAME -simplify 40% keep-shapes -o precision=0.01 format=geojson countries_simpl.geojson
$MS ne_10m_admin_1_states_provinces.geojson -filter-fields name,adm0_a3,type_en,latitude,longitude,iso_3166_2 -simplify 4% keep-shapes -o precision=0.01 format=geojson states_simpl.geojson
$MS ne_10m_geography_regions_polys.geojson -filter 'FEATURECLA!="Island" && FEATURECLA!="Island group" && FEATURECLA!="Dragons-be-here"' -points inner -filter-fields NAME,FEATURECLA,REGION,SCALERANK -o format=geojson regions_pts.geojson
$MS ne_10m_geography_regions_polys.geojson -filter 'FEATURECLA=="Range/mtn" || FEATURECLA=="Plateau"' -filter-fields NAME,FEATURECLA,SCALERANK -simplify 20% keep-shapes -o precision=0.01 format=geojson mountains_simpl.geojson
$MS ne_10m_geography_marine_polys.geojson -points inner -filter-fields name,featurecla,scalerank -o format=geojson marine_pts.geojson
cd - >/dev/null
python3 tools/build_data.py "$RAW" "$RAW/node_modules/world-countries/countries.json" app/src/main/assets/web/data
