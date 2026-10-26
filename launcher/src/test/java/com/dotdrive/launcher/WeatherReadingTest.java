package com.dotdrive.launcher;

import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class WeatherReadingTest {
    private JSONObject input() throws Exception {return new JSONObject("{\"timezone\":\"Asia/Jakarta\",\"current\":{\"temperature_2m\":27.5,\"relative_humidity_2m\":80,\"wind_speed_10m\":12,\"weather_code\":3},\"hourly\":{\"time\":[\"2026-10-26T08:00\",\"2026-10-26T09:00\",\"2026-10-26T10:00\",\"2026-10-26T11:00\"],\"temperature_2m\":[26,27,28,29]}}");}
    @Test public void futureForecastRespectsLocationTimezone() throws Exception {
        WeatherReading w=new WeatherReading(input(),1792978200000L); // 26 Oct 2026 01:30 UTC / 08:30 WIB
        assertEquals(27.5,w.temperature,.001);
        assertFalse(w.forecast.contains("08:00"));assertTrue(w.forecast.contains("09:00"));
        assertEquals("Berawan",WeatherReading.label(w.code));
    }
    @Test public void incompleteDataDoesNotTurnIntoFakeZeroWeather() throws Exception {
        final JSONObject missing=input();missing.getJSONObject("current").remove("temperature_2m");
        assertThrows(Exception.class,()->new WeatherReading(missing,0));
        final JSONObject invalid=input();invalid.getJSONObject("current").put("relative_humidity_2m",120);
        assertThrows(IllegalArgumentException.class,()->new WeatherReading(invalid,0));
    }
    @Test public void weatherCodesDoNotMislabelUnknownConditions() {
        assertEquals("Kondisi lainnya",WeatherReading.label(-1));assertEquals("Hujan",WeatherReading.label(63));assertEquals("Badai petir",WeatherReading.label(95));
    }
}
