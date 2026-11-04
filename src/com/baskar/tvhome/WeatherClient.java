package com.baskar.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** Reads city lookups and forecasts from Open-Meteo, which needs no API key. */
final class WeatherClient {
    private static final String[] CLEAR = {"☀️", "Clear"};
    private static final String[] PARTLY = {"⛅️", "Partly cloudy"};
    private static final String[] CLOUDY = {"☁️", "Cloudy"};
    private static final String[] FOG = {"🌫️", "Fog"};
    private static final String[] RAIN = {"🌧️", "Rain"};
    private static final String[] SNOW = {"❄️", "Snow"};
    private static final String[] STORM = {"⛈️", "Storm"};

    private WeatherClient() {
    }

    /** Returns the best match for a city name, or null if there is none. */
    static JSONObject findCity(String name) throws IOException, JSONException {
        JSONArray results = new JSONObject(read("https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                + URLEncoder.encode(name, "UTF-8"))).optJSONArray("results");
        return results == null || results.length() == 0 ? null : results.getJSONObject(0);
    }

    static String forecast(String latitude, String longitude) throws IOException {
        return read("https://api.open-meteo.com/v1/forecast?latitude=" + latitude + "&longitude=" + longitude
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,"
                + "pressure_msl,weather_code"
                + "&daily=weather_code,temperature_2m_max,sunrise,sunset,uv_index_max"
                + "&timezone=auto&forecast_days=6");
    }

    /** Returns {emoji, description} for a WMO weather code. */
    static String[] describe(int code) {
        if (code == 0) {
            return CLEAR;
        } else if (code <= 2) {
            return PARTLY;
        } else if (code == 3) {
            return CLOUDY;
        } else if (code <= 48) {
            return FOG;
        } else if (code >= 95) {
            return STORM;
        } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            return SNOW;
        }
        return RAIN;
    }

    private static String read(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                body.write(buffer, 0, read);
            }
            return body.toString("UTF-8");
        } finally {
            connection.disconnect();
        }
    }
}
