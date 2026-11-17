package com.earthx.explorer

import com.earthx.explorer.util.GeoMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GeoMathTest {
    @Test fun haversine_londonToParis() {
        // London (51.5074, -0.1278) to Paris (48.8566, 2.3522) ≈ 343.5 km
        assertEquals(343.5, GeoMath.haversineKm(51.5074, -0.1278, 48.8566, 2.3522), 1.0)
    }

    @Test fun haversine_quarterCircumference() {
        val q = GeoMath.haversineKm(0.0, 0.0, 0.0, 90.0)
        assertEquals(Math.PI / 2 * GeoMath.EARTH_RADIUS_KM, q, 0.01)
    }

    @Test fun haversine_antipodes() {
        assertEquals(Math.PI * GeoMath.EARTH_RADIUS_KM, GeoMath.haversineKm(10.0, 20.0, -10.0, -160.0), 0.5)
    }

    @Test fun bearing_dueEastAtEquator() {
        assertEquals(90.0, GeoMath.initialBearing(0.0, 0.0, 0.0, 10.0), 1e-6)
    }

    @Test fun wrapLon() {
        assertEquals(-170.0, GeoMath.wrapLon(190.0), 1e-9)
        assertEquals(180.0, GeoMath.wrapLon(-180.0), 1e-9)
        assertEquals(10.0, GeoMath.wrapLon(370.0), 1e-9)
    }

    @Test fun subsolar_juneSolstice() {
        // 2024-06-20 20:51 UTC solstice: declination ≈ +23.44°
        val (lat, _) = GeoMath.subsolarPoint(Instant.parse("2024-06-20T20:51:00Z").toEpochMilli())
        assertEquals(23.44, lat, 0.05)
    }

    @Test fun subsolar_equinoxNoonGreenwich() {
        // Near the March equinox at ~12:07 UTC the sun is close to (0°, 0°) (equation of time ≈ -7 min)
        val (lat, lon) = GeoMath.subsolarPoint(Instant.parse("2024-03-20T12:07:00Z").toEpochMilli())
        assertEquals(0.0, lat, 0.5)
        assertEquals(0.0, lon, 0.6)
    }

    @Test fun solarElevation_nightAndDay() {
        val t = Instant.parse("2024-06-21T12:00:00Z").toEpochMilli()
        assertTrue(GeoMath.solarElevation(51.5, 0.0, t) > 55)      // London midday in June
        assertTrue(GeoMath.solarElevation(-33.9, 151.2, t) < -20)  // Sydney at 22:00 local
    }

    @Test fun zoomLevels() {
        assertEquals(1, GeoMath.zoomLevel(20000.0))
        assertEquals(3, GeoMath.zoomLevel(3000.0))
        assertEquals(6, GeoMath.zoomLevel(20.0))
    }

    @Test fun localTime_usesIanaZone() {
        val (txt, exact) = GeoMath.localTimeText(Instant.parse("2024-01-15T12:00:00Z").toEpochMilli(), "Asia/Kolkata", 77.0)
        assertTrue(exact)
        assertTrue(txt, txt.contains("17:30"))
    }

    @Test fun localTime_fallsBackToSolar() {
        val (txt, exact) = GeoMath.localTimeText(Instant.parse("2024-01-15T12:00:00Z").toEpochMilli(), null, 90.0)
        assertTrue(!exact)
        assertTrue(txt, txt.contains("18:00"))
    }
}
