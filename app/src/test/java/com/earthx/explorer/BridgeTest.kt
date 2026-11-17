package com.earthx.explorer

import com.earthx.explorer.data.repositories.ImageryProxy
import com.earthx.explorer.engine.GlobeBridge
import com.earthx.explorer.engine.GlobeEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeTest {
    @Test fun parsesCamera() {
        val e = GlobeBridge.parse("""{"type":"camera","payload":{"lat":12.5,"lon":-40,"altKm":3000,"level":3}}""")
        assertEquals(GlobeEvent.Camera(12.5, -40.0, 3000.0, 3), e)
    }

    @Test fun rejectsOutOfRangeAndUnknown() {
        assertNull(GlobeBridge.parse("""{"type":"camera","payload":{"lat":120,"lon":0,"altKm":10}}"""))
        assertNull(GlobeBridge.parse("""{"type":"launchMissiles","payload":{}}"""))
        assertNull(GlobeBridge.parse("not json"))
        assertNull(GlobeBridge.parse("""{"type":"quakeTapped","payload":{"id":"<script>"}}"""))
    }

    @Test fun locationTappedSanitisesCountry() {
        val e = GlobeBridge.parse("""{"type":"locationTapped","payload":{"lat":1,"lon":2,"countryId":"IND","state":{"name":"Kerala","type":"State"}}}""")
        assertTrue(e is GlobeEvent.LocationTapped)
        e as GlobeEvent.LocationTapped
        assertEquals("IND", e.countryId); assertEquals("Kerala", e.stateName)
        val bad = GlobeBridge.parse("""{"type":"locationTapped","payload":{"lat":1,"lon":2,"countryId":"x y;"}}""") as GlobeEvent.LocationTapped
        assertNull(bad.countryId)
    }

    @Test fun screenshotRequiresJpegDataUrl() {
        assertNull(GlobeBridge.parse("""{"type":"screenshot","payload":{"dataUrl":"javascript:alert(1)"}}"""))
        assertTrue(GlobeBridge.parse("""{"type":"screenshot","payload":{"dataUrl":"data:image/jpeg;base64,AAAA"}}""") is GlobeEvent.Screenshot)
    }

    @Test fun imageSniffing() {
        assertTrue(ImageryProxy.isImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0, 0, 0)))
        assertTrue(ImageryProxy.isImage(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0)))
        assertFalse(ImageryProxy.isImage("<?xml version".toByteArray()))
    }
}
