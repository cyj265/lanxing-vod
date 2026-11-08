package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MediaQualityTest {

    @Test
    public void label_mapsStandardSizes() {
        assertEquals("4K", MediaQuality.label(3840, 2160));
        assertEquals("2K", MediaQuality.label(2560, 1440));
        assertEquals("1080P", MediaQuality.label(1920, 1080));
        assertEquals("720P", MediaQuality.label(1280, 720));
        assertEquals("480P", MediaQuality.label(854, 480));
        assertEquals("360P", MediaQuality.label(640, 360));
    }

    @Test
    public void label_handlesPortraitAndUltraWide() {
        assertEquals("1080P", MediaQuality.label(1080, 1920));
        assertEquals("720P", MediaQuality.label(720, 1280));
        assertEquals("1080P", MediaQuality.label(1920, 800));
        assertEquals("4K", MediaQuality.label(3840, 1600));
    }

    @Test
    public void label_handlesUnknownSize() {
        assertEquals("", MediaQuality.label(0, 0));
        assertEquals("", MediaQuality.label(-1, 1080));
    }
}
