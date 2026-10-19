package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Glass weather panel: now, today's high/low, sunrise and sunset, and the next three days.
 * Data from Open-Meteo (no key). Location from config.xml, or looked up from the public IP once a
 * day. The last forecast is cached so the panel shows at once on start, and refreshed at most every
 * 30 minutes while the home screen is in front. Hidden until there is something to show.
 */
public class WeatherWidget extends LinearLayout {

    private static final long REFRESH_MS = 30 * 60_000L;
    private static final long LOCATION_MS = 24 * 3_600_000L;
    private static final String PREFS = "weather";

    private final SharedPreferences prefs;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final float density;
    private Typeface outfit;
    private boolean loading;

    private WeatherIcon nowIcon;
    private TextView nowTemp, nowCondition, nowPlace, highLow, sunrise, sunset;
    private final TextView[] dayName = new TextView[3], dayTemps = new TextView[3];
    private final WeatherIcon[] dayIcon = new WeatherIcon[3];

    public WeatherWidget(Context c) { this(c, null); }

    public WeatherWidget(Context c, AttributeSet a) {
        super(c, a);
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        density = getResources().getDisplayMetrics().density;
        try { outfit = getResources().getFont(R.font.inter); } catch (Exception ignored) { }
        setOrientation(VERTICAL);
        setBackground(Glass.focusPanel(dp(26), density));
        int pad = dp(18);
        setPadding(pad, dp(14), pad, dp(14));
        // Focusable: OK opens the 10-day forecast (WeatherActivity).
        setFocusable(true);
        setClickable(true);
        setOnClickListener(v -> getContext().startActivity(new android.content.Intent(getContext(), WeatherActivity.class)));
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(26));
            }
        });
        setOnFocusChangeListener((v, focused) -> animate().scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f)

                .setDuration(focused ? 240 : 160)
                .setInterpolator(new android.view.animation.OvershootInterpolator(focused ? 1.4f : 0f)).start());
        build();
        setVisibility(GONE);
        String cached = prefs.getString("forecast", null);
        if (cached != null) {
            try { bind(new JSONObject(cached), prefs.getString("city", "")); } catch (Exception ignored) { }
        }
    }

    private void build() {
        LinearLayout top = new LinearLayout(getContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        nowIcon = new WeatherIcon(getContext());
        top.addView(nowIcon, new LayoutParams(dp(50), dp(50)));
        nowTemp = text(40, 300, 0xFFFFFFFF);
        nowTemp.setIncludeFontPadding(false);
        LayoutParams tp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tp.setMarginStart(dp(10));
        top.addView(nowTemp, tp);

        LinearLayout mid = new LinearLayout(getContext());
        mid.setOrientation(VERTICAL);
        nowCondition = text(16, 400, 0xF2FFFFFF);
        nowPlace = text(11, 500, 0x8CFFFFFF);
        nowPlace.setLetterSpacing(0.16f);
        nowPlace.setAllCaps(true);
        mid.addView(nowCondition);
        mid.addView(nowPlace);
        LayoutParams mp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        mp.setMarginStart(dp(14));
        top.addView(mid, mp);

        LinearLayout right = new LinearLayout(getContext());
        right.setOrientation(VERTICAL);
        right.setGravity(Gravity.END);
        highLow = text(14, 400, 0xD9FFFFFF);
        right.addView(highLow);
        LinearLayout sun = new LinearLayout(getContext());
        sun.setGravity(Gravity.CENTER_VERTICAL);
        sunrise = sunItem(sun, WeatherIcon.SUNRISE);
        sunset = sunItem(sun, WeatherIcon.SUNSET);
        LayoutParams sp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(4);
        right.addView(sun, sp);
        top.addView(right);
        addView(top);

        View rule = new View(getContext());
        rule.setBackgroundColor(0x1FFFFFFF);
        LayoutParams rp = new LayoutParams(LayoutParams.MATCH_PARENT, 1);
        rp.topMargin = dp(12);
        rp.bottomMargin = dp(10);
        addView(rule, rp);

        LinearLayout days = new LinearLayout(getContext());
        for (int i = 0; i < 3; i++) {
            LinearLayout d = new LinearLayout(getContext());
            d.setGravity(Gravity.CENTER_VERTICAL);
            dayName[i] = text(12, 500, 0x99FFFFFF);
            dayName[i].setAllCaps(true);
            dayName[i].setLetterSpacing(0.12f);
            d.addView(dayName[i], new LayoutParams(dp(38), LayoutParams.WRAP_CONTENT));
            dayIcon[i] = new WeatherIcon(getContext());
            d.addView(dayIcon[i], new LayoutParams(dp(26), dp(26)));
            dayTemps[i] = text(14, 400, 0xE6FFFFFF);
            LayoutParams tempLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tempLp.setMarginStart(dp(8));
            d.addView(dayTemps[i], tempLp);
            days.addView(d, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
        addView(days);
    }

    private TextView sunItem(LinearLayout row, int kind) {
        WeatherIcon ic = new WeatherIcon(getContext());
        ic.setKind(kind);
        LayoutParams ip = new LayoutParams(dp(18), dp(18));
        if (row.getChildCount() > 0) ip.setMarginStart(dp(12));
        row.addView(ic, ip);
        TextView t = text(13, 400, 0xBFFFFFFF);
        LayoutParams tp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tp.setMarginStart(dp(4));
        row.addView(t, tp);
        return t;
    }

    private TextView text(float sp, int weight, int color) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        if (outfit != null) t.setTypeface(outfit);
        t.setFontVariationSettings("'wght' " + weight);
        return t;
    }

    private int dp(float v) { return Math.round(v * density); }

    // ---- data ------------------------------------------------------------------------------------

    /** Cheap to call often (onResume, every minute): only fetches when the cache is stale. */
    public void refreshIfStale() {
        if (loading) return;
        if (System.currentTimeMillis() - prefs.getLong("fetched", 0) < REFRESH_MS) return;
        loading = true;
        io.execute(() -> {
            try {
                double[] loc = location(getContext());
                String city = prefs.getString("city", "");
                String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                                + "&current=temperature_2m,weather_code,is_day"
                                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset"
                                + "&timezone=auto&forecast_days=4", loc[0], loc[1]);
                JSONObject json = new JSONObject(get(url));
                prefs.edit().putString("forecast", json.toString())
                        .putLong("fetched", System.currentTimeMillis()).apply();
                main.post(() -> { loading = false; bind(json, city); });
            } catch (Exception e) {
                // Offline or API down: keep whatever is on screen, try again in a few minutes.
                prefs.edit().putLong("fetched", System.currentTimeMillis() - REFRESH_MS + 5 * 60_000L).apply();
                main.post(() -> loading = false);
            }
        });
    }

    /** Fixed coordinates from config.xml, else the public IP's location, cached for a day. Network: call off the UI thread. */
    static double[] location(Context ctx) throws Exception {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String lat = ctx.getResources().getString(R.string.weather_latitude).trim();
        String lon = ctx.getResources().getString(R.string.weather_longitude).trim();
        if (!lat.isEmpty() && !lon.isEmpty()) {
            prefs.edit().putString("city", ctx.getResources().getString(R.string.weather_place)).apply();
            return new double[]{Double.parseDouble(lat), Double.parseDouble(lon)};
        }
        if (System.currentTimeMillis() - prefs.getLong("located", 0) < LOCATION_MS && prefs.contains("lat")) {
            return new double[]{prefs.getFloat("lat", 0), prefs.getFloat("lon", 0)};
        }
        JSONObject j;
        try {
            j = new JSONObject(get("https://ipapi.co/json/"));
            if (!j.has("latitude")) throw new Exception("no location");
        } catch (Exception first) {
            j = new JSONObject(get("https://ipwho.is/"));
        }
        double la = j.getDouble("latitude"), lo = j.getDouble("longitude");
        prefs.edit().putFloat("lat", (float) la).putFloat("lon", (float) lo)
                .putString("city", j.optString("city", "")).putLong("located", System.currentTimeMillis()).apply();
        return new double[]{la, lo};
    }

    /** The place name that goes with location(). */
    static String city(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("city", "");
    }

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "tvhome-weather");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    private void bind(JSONObject j, String city) {
        try {
            JSONObject now = j.getJSONObject("current");
            JSONObject daily = j.getJSONObject("daily");
            JSONArray codes = daily.getJSONArray("weather_code");
            JSONArray max = daily.getJSONArray("temperature_2m_max");
            JSONArray min = daily.getJSONArray("temperature_2m_min");
            JSONArray dates = daily.getJSONArray("time");
            int code = now.getInt("weather_code");
            nowIcon.setKind(WeatherIcon.forCode(code, now.optInt("is_day", 1) == 1));
            nowTemp.setText(deg(now.getDouble("temperature_2m")));
            nowCondition.setText(condition(code));
            nowPlace.setText(city);
            nowPlace.setVisibility(city.isEmpty() ? GONE : VISIBLE);
            highLow.setText(getResources().getString(R.string.weather_high_low, deg(max.getDouble(0)), deg(min.getDouble(0))));
            sunrise.setText(time(daily.getJSONArray("sunrise").getString(0)));
            sunset.setText(time(daily.getJSONArray("sunset").getString(0)));
            for (int i = 0; i < 3 && i + 1 < dates.length(); i++) {
                LocalDate d = LocalDate.parse(dates.getString(i + 1));
                dayName[i].setText(d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault()));
                dayIcon[i].setKind(WeatherIcon.forCode(codes.getInt(i + 1), true));
                dayTemps[i].setText(deg(max.getDouble(i + 1)) + "  " + deg(min.getDouble(i + 1)));
            }
            setVisibility(VISIBLE);
        } catch (Exception ignored) {
            // A malformed answer leaves the previous forecast in place.
        }
    }

    private static String deg(double t) { return Math.round(t) + "°"; }

    private String time(String iso) {
        LocalTime t = LocalTime.parse(iso.substring(iso.indexOf('T') + 1));
        return t.format(DateTimeFormatter.ofPattern(DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm a"));
    }

    private String condition(int code) {
        return conditionText(getContext(), code);
    }

    static String conditionText(Context ctx, int code) {
        int res;
        if (code == 0) res = R.string.weather_clear;
        else if (code == 1) res = R.string.weather_mostly_clear;
        else if (code == 2) res = R.string.weather_partly_cloudy;
        else if (code == 3) res = R.string.weather_overcast;
        else if (code == 45 || code == 48) res = R.string.weather_fog;
        else if (code >= 51 && code <= 57) res = R.string.weather_drizzle;
        else if (code == 66 || code == 67) res = R.string.weather_freezing_rain;
        else if (code >= 61 && code <= 65) res = R.string.weather_rain;
        else if (code >= 71 && code <= 77) res = R.string.weather_snow;
        else if (code >= 80 && code <= 82) res = R.string.weather_showers;
        else if (code == 85 || code == 86) res = R.string.weather_snow_showers;
        else if (code >= 95) res = R.string.weather_thunder;
        else res = R.string.weather_overcast;
        return ctx.getString(res);
    }

    public void shutdown() { io.shutdownNow(); }
}
