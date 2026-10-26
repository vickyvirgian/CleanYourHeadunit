package com.dotdrive.launcher;

import org.json.*;
import java.util.*;
import java.text.SimpleDateFormat;

final class WeatherReading {
    final double temperature,humidity,wind;
    final int code;
    final String forecast;
    WeatherReading(JSONObject json,long now) throws Exception {
        JSONObject current=json.getJSONObject("current");
        temperature=current.getDouble("temperature_2m");humidity=current.getDouble("relative_humidity_2m");wind=current.getDouble("wind_speed_10m");code=current.getInt("weather_code");
        if(Double.isNaN(temperature)||Double.isInfinite(temperature)||Double.isNaN(humidity)||Double.isInfinite(humidity)||Double.isNaN(wind)||Double.isInfinite(wind)||humidity<0||humidity>100||wind<0||code<0)throw new IllegalArgumentException("Data cuaca tidak lengkap");
        StringBuilder line=new StringBuilder();
        JSONObject hourly=json.optJSONObject("hourly");
        if(hourly!=null){
            JSONArray times=hourly.getJSONArray("time"),values=hourly.getJSONArray("temperature_2m");
            SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm",Locale.US);f.setTimeZone(TimeZone.getTimeZone(json.optString("timezone","UTC")));
            int count=0;
            for(int i=0;i<times.length() && count<3;i++) {
                String t=times.getString(i);Date date=f.parse(t);
                if(date==null||date.getTime()<now||i>=values.length()||values.isNull(i))continue;
                if(count>0)line.append("    ");line.append(t.substring(11)).append("  ").append(Math.round(values.getDouble(i))).append("°");count++;i++;
            }
        }
        forecast=line.toString();
    }
    static String label(int code) {
        if(code==0)return "Cerah";if(code>=1&&code<=3)return "Berawan";if(code==45||code==48)return "Berkabut";
        if(code>=51&&code<=57)return "Gerimis";if(code>=61&&code<=67)return "Hujan";
        if(code>=71&&code<=77)return "Salju";if(code>=80&&code<=82)return "Hujan lokal";
        if(code>=85&&code<=86)return "Salju lokal";if(code>=95&&code<=99)return "Badai petir";return "Kondisi lainnya";
    }
}
