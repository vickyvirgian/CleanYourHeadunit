package com.dotdrive.launcher;

import android.content.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;

final class WeatherStore {
    private final SharedPreferences prefs;
    private long attempted;
    WeatherStore(Context c){prefs=c.getSharedPreferences("weather",Context.MODE_PRIVATE);}
    boolean configured(){return prefs.contains("lat");}
    String city(){return prefs.getString("city","Atur kota cuaca");}
    long updated(){return prefs.getLong("updated",0);}
    boolean due(){return configured()&&System.currentTimeMillis()-Math.max(updated(),attempted)>30*60*1000L;}
    WeatherReading cached(){try{return new WeatherReading(new JSONObject(prefs.getString("json","")),System.currentTimeMillis());}catch(Exception e){return null;}}
    void choose(JSONObject place) throws Exception {
        prefs.edit().putString("city",place.getString("name")).putString("lat",Double.toString(place.getDouble("latitude")))
            .putString("lon",Double.toString(place.getDouble("longitude"))).remove("json").remove("updated").apply();attempted=0;
    }
    JSONArray search(String query)throws Exception {
        if(query.trim().length()<2||query.length()>80)throw new IllegalArgumentException("Masukkan nama kota.");
        return read("https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(query.trim(),"UTF-8")+"&count=5&language=id&format=json").optJSONArray("results");
    }
    void refresh()throws Exception {
        if(!configured())return;
        attempted=System.currentTimeMillis();
        // Snapshot the requested coordinates so a late response cannot replace another city's weather.
        String lat=prefs.getString("lat",""),lon=prefs.getString("lon","");
        String url="https://api.open-meteo.com/v1/forecast?latitude="+URLEncoder.encode(lat,"UTF-8")+"&longitude="+URLEncoder.encode(lon,"UTF-8")+
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&hourly=temperature_2m&forecast_days=2&timezone=auto";
        JSONObject json=read(url);new WeatherReading(json,System.currentTimeMillis());
        if(lat.equals(prefs.getString("lat",""))&&lon.equals(prefs.getString("lon","")))prefs.edit().putString("json",json.toString()).putLong("updated",System.currentTimeMillis()).apply();
    }
    private static JSONObject read(String address)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setInstanceFollowRedirects(false);
        try {
            if(c.getResponseCode()!=200)throw new IOException("Layanan cuaca belum merespons.");
            try(BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))){
                StringBuilder out=new StringBuilder();char[] buffer=new char[4096];int n;
                while((n=r.read(buffer))>=0){if(out.length()+n>262144)throw new IOException("Respons terlalu besar.");out.append(buffer,0,n);}
                return new JSONObject(out.toString());
            }
        } finally {c.disconnect();}
    }
}
