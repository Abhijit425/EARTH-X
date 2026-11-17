# EARTH-X — Living Planet Explorer

A premium interactive 3D Earth explorer for Android tablets (landscape-first, portrait supported).
Kotlin · Jetpack Compose · Material 3 · Room · DataStore · Coroutines, with a bundled Three.js/WebGL
engine running in a hardened WebView.

## Features
- **Realistic globe** — NASA Blue Marble 4K (8K "Ultra HD" fetched at build time), normal-mapped relief,
  ocean sun glint, drifting cloud layer with cloud shadows, ray-based atmospheric limb glow, stars & sun.
- **Day/night simulator** — real sub-solar point, twilight band, Black Marble city lights, time slider,
  date picker, play/pause, ×60 … ×1 day/s acceleration, equinox/solstice jumps, local time via IANA zones.
- **Zoom-to-anywhere** — 6 levels (space → continents → countries → states/provinces → cities → regional
  detail). Cinematic fly-to. At low altitude, NASA GIBS imagery for the area under the camera is streamed
  (≈250–500 m/pixel). No street-level imagery is claimed.
- **Selection** — tap countries (outline + highlight + profile: capital, population, area, currency,
  languages, continent, coordinates, density, GDP estimate…), states/provinces, cities (2,500 bundled),
  summits, trenches; double-tap to zoom; inertia; pinch.
- **Search** — countries, capitals, cities, continents, mountain ranges, plateaus, deserts, oceans, seas,
  summits, trenches, landmarks; recent searches; favourites with collections.
- **Live weather** (Open-Meteo, no key) — temperature / cloud / rainfall / animated wind / condition-marker
  layers interpolated from a 15° grid, and per-location current conditions (model analysis), 24 h and 7-day
  forecast, with retrieval timestamps.
- **Earthquakes** (USGS GeoJSON) — last hour / 24 h / 7 days / 30 days, animated pulsing markers sized by
  magnitude and coloured by depth, magnitude filter, details incl. tsunami flag, PAGER alert, USGS link.
- **Satellite mode** (NASA GIBS WMS) — VIIRS/MODIS daily true colour with date selection, Blue Marble NG,
  shaded relief + bathymetry, Black Marble; graceful fallback to the bundled texture.
- **Education** — tectonic plate boundaries (PB2002), mountain highlighting, ocean trenches, continents,
  highest summits, Earth facts dashboard (NASA fact sheet values).
- **Tools** — great-circle distance & bearing measurement, globe screenshots (Pictures/EARTH-X).
- **Offline** — globe, borders, states, atlas, country directory, search, day/night, favourites and any
  previously cached weather/quake/imagery data. Clear offline indicator.
- **Performance** — progressive texture loading (1K → 4K → 8K), adaptive render resolution, low-power mode
  (30 fps, render-on-change), WebGL context-loss recovery, WebView renderer-crash recovery, lifecycle pause.

## Architecture
```
app/src/main/java/com/earthx/explorer/
  MainActivity.kt, EarthXApplication.kt (DI container)
  engine/      GlobeView (WebView host + asset loader + GIBS proxy), GlobeBridge (validated JS→Kotlin),
               GlobeController (Kotlin→JS command queue)
  data/model   domain models          data/parse  pure JSON parsers (unit-tested)
  data/network HttpClient (allow-listed hosts, gzip, 429 back-off), DiskCache, ConnectivityObserver
  data/database Room: favourites, recent searches, cache metadata
  data/repositories Geo (offline atlas + search), Weather, Quake, ImageryProxy, Settings (DataStore), …
  viewmodels/  EarthViewModel (single source of UI state)
  ui/          EarthXScreen (adaptive tablet layout), components, screens (panels), theme
  util/        GeoMath (haversine, bearing, solar position, local time), WeatherCodes
app/src/main/assets/web/
  index.html, js/ (main, controls, geo, shaders, vectors, labels, quakes, weather, imagery), lib/three
  data/ (Natural Earth derived JSON), textures/
tools/  build_data.py, fetch_data.sh, fetch_hd_textures.py
```
The 3D engine knows nothing about Android; all networking happens natively. The page can reach only
`https://appassets.androidplatform.net` (bundled assets + the native, parameter-validated GIBS proxy).
Messages from JS are size-limited, type-whitelisted and range-checked (`GlobeBridge.parse`).

## Data sources & licences
| Data | Source | Licence |
|---|---|---|
| Day / night Earth textures | NASA Visible Earth (Blue Marble, Black Marble) | Public domain |
| Relief, water mask, clouds, stars | three.js examples / three-globe (from NASA & Solar System Scope) | MIT / CC BY 4.0 |
| Satellite imagery | NASA GIBS (ESDIS) | Open, attribution requested |
| Weather | Open-Meteo.com | CC BY 4.0 |
| Earthquakes | USGS Earthquake Hazards Program | Public domain |
| Borders, cities, geography | Natural Earth | Public domain |
| Country facts | world-countries (mledoze) | ODbL 1.0 |
| Tectonic plates | PB2002, Bird (2003) via fraxen/tectonicplates | ODC-By 1.0 |
| Engine | three.js r170 | MIT |

## Setup
Open the folder in Android Studio (Ladybug or newer), let Gradle sync, run on a tablet or emulator
(API 26+, OpenGL ES 3 / WebGL2). See **BUILD-APK.md** for command-line builds and installation.

## Known limitations
- Regional detail is limited by public datasets (best ≈250 m/pixel); there is no street-level imagery.
- The weather layers interpolate a 15° grid (kept small for Open-Meteo fair use); point forecasts are exact
  for the tapped location.
- The decorative cloud layer is a static texture; for real clouds use Satellite mode (daily true colour).
- Population figures are dataset estimates (Natural Earth, year shown).
