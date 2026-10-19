package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen forecast in the style of Apple Weather: a sky gradient for the current conditions,
 * the big temperature, an hourly strip (Left/Right scroll it) with sunrise and sunset marked, a
 * 10-day list with temperature range capsules, and detail tiles. Back returns Home.
 */
public class WeatherActivity extends Activity {

    private static final long STALE_MS = 15 * 60_000L;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private float density;
    private Typeface outfit;
    private boolean h24;

    private FrameLayout root;
    private HorizontalScrollView hourlyScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("weather", MODE_PRIVATE);
        h24 = DateFormat.is24HourFormat(this);
        try { outfit = getResources().getFont(R.font.inter); } catch (Exception ignored) { }
        root = new FrameLayout(this);
        setContentView(root);
        setSky(3, true);

        String cached = prefs.getString("detail", null);
        boolean shown = false;
        if (cached != null) {
            try { bind(new JSONObject(cached)); shown = true; } catch (Exception ignored) { }
        }
        if (!shown) message(getString(R.string.loading_weather));
        if (!shown || System.currentTimeMillis() - prefs.getLong("detailFetched", 0) > STALE_MS) fetch(!shown);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (hourlyScroll != null && (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
            int step = dp(64) * 4;
            hourlyScroll.smoothScrollBy(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? step : -step, 0);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void fetch(boolean showErrors) {
        io.execute(() -> {
            try {
                double[] loc = WeatherWidget.location(this);
                String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,"
                                + "weather_code,is_day,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index"
                                + "&hourly=temperature_2m,weather_code,is_day,precipitation_probability"
                                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,"
                                + "precipitation_probability_max,uv_index_max,wind_gusts_10m_max"
                                + "&timezone=auto&forecast_days=10", loc[0], loc[1]);
                JSONObject json = new JSONObject(WeatherWidget.get(url));
                prefs.edit().putString("detail", json.toString()).putLong("detailFetched", System.currentTimeMillis()).apply();
                main.post(() -> {
                    if (isFinishing()) return;
                    try { bind(json); } catch (Exception e) { message(getString(R.string.weather_unavailable)); }
                });
            } catch (Exception e) {
                if (showErrors) main.post(() -> { if (!isFinishing()) message(getString(R.string.weather_unavailable)); });
            }
        });
    }

    // ---- layout ----------------------------------------------------------------------------------

    private void message(String text) {
        root.removeAllViews();
        TextView t = text(20, 300, 0xE6FFFFFF);
        t.setText(text);
        root.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    }

    private void bind(JSONObject j) throws Exception {
        JSONObject cur = j.getJSONObject("current");
        JSONObject daily = j.getJSONObject("daily");
        JSONObject hourly = j.getJSONObject("hourly");
        int code = cur.getInt("weather_code");
        boolean day = cur.optInt("is_day", 1) == 1;
        setSky(code, day);
        root.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setPadding(dp(40), dp(26), dp(40), dp(26));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // Left: header, hourly, tiles.
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        page.addView(left, new LinearLayout.LayoutParams(0, -1, 1.7f));

        left.addView(header(cur, daily, code), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(10);
        left.addView(hourlyCard(cur, hourly, daily), hp);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, 0, 1f);
        tp.topMargin = dp(12);
        left.addView(tiles(cur, daily), tp);

        // Right: 10-day list.
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 1f);
        rp.setMarginStart(dp(16));
        page.addView(tenDay(cur, daily), rp);
    }

    private View header(JSONObject cur, JSONObject daily, int code) throws Exception {
        View v = headerLines(cur, daily, code);
        LinearLayout h = (LinearLayout) v;
        for (int i = 0; i < h.getChildCount(); i++) {
            h.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        }
        return h;
    }

    private View headerLines(JSONObject cur, JSONObject daily, int code) throws Exception {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setGravity(Gravity.CENTER_HORIZONTAL);
        h.setLayoutTransition(null);
        TextView city = text(26, 400, 0xFFFFFFFF);
        String c = WeatherWidget.city(this);
        city.setText(c.isEmpty() ? getString(R.string.now) : c);
        city.setShadowLayer(dp(6), 0, dp(1), 0x33000000);
        h.addView(city);
        TextView temp = text(78, 200, 0xFFFFFFFF);
        temp.setText(deg(cur.getDouble("temperature_2m")));
        temp.setIncludeFontPadding(false);
        temp.setShadowLayer(dp(8), 0, dp(2), 0x33000000);
        temp.setPadding(dp(14), 0, 0, 0);   // the degree sign hangs: optically centre the digits
        h.addView(temp);
        TextView cond = text(18, 400, 0xE6FFFFFF);
        cond.setText(WeatherWidget.conditionText(this, code));
        h.addView(cond);
        TextView hl = text(18, 400, 0xE6FFFFFF);
        hl.setText(getString(R.string.high_low_short,
                deg(daily.getJSONArray("temperature_2m_max").getDouble(0)),
                deg(daily.getJSONArray("temperature_2m_min").getDouble(0))));
        h.addView(hl);
        return h;
    }

    private View hourlyCard(JSONObject cur, JSONObject hourly, JSONObject daily) throws Exception {
        LinearLayout card = card();
        // Apple's one-line summary over the strip.
        JSONArray times = hourly.getJSONArray("time");
        JSONArray temps = hourly.getJSONArray("temperature_2m");
        JSONArray codes = hourly.getJSONArray("weather_code");
        JSONArray isDay = hourly.getJSONArray("is_day");
        JSONArray pop = hourly.getJSONArray("precipitation_probability");
        String nowHour = cur.getString("time").substring(0, 13);
        int start = 0;
        while (start < times.length() && times.getString(start).substring(0, 13).compareTo(nowHour) < 0) start++;

        String summary = getString(R.string.no_rain);
        for (int i = start + 1; i < Math.min(start + 13, times.length()); i++) {
            if (pop.optInt(i, 0) >= 50) {
                summary = getString(R.string.rain_around, hour(times.getString(i)));
                break;
            }
        }
        double gust = cur.optDouble("wind_gusts_10m", 0);
        if (gust >= 30) summary += " " + getString(R.string.gusts_up_to, String.valueOf(Math.round(gust)));
        TextView sum = text(14, 400, 0xF2FFFFFF);
        sum.setSingleLine(false);
        sum.setText(summary);
        card.addView(sum);
        card.addView(rule());

        hourlyScroll = new HorizontalScrollView(this);
        hourlyScroll.setHorizontalScrollBarEnabled(false);
        hourlyScroll.setFocusable(false);
        LinearLayout strip = new LinearLayout(this);
        hourlyScroll.addView(strip);
        card.addView(hourlyScroll);

        // Sunrise/sunset of today and tomorrow, slotted in among the hours like Apple does.
        JSONArray rise = daily.getJSONArray("sunrise"), set = daily.getJSONArray("sunset");
        String[] sunTimes = {rise.getString(0), set.getString(0), rise.optString(1, ""), set.optString(1, "")};
        boolean[] sunIsRise = {true, false, true, false};

        int end = Math.min(start + 25, times.length());
        for (int i = start; i < end; i++) {
            String t = times.getString(i);
            boolean first = i == start;
            int kind = WeatherIcon.forCode(first ? cur.getInt("weather_code") : codes.getInt(i),
                    (first ? cur.optInt("is_day", 1) : isDay.optInt(i, 1)) == 1);
            double temp = first ? cur.getDouble("temperature_2m") : temps.getDouble(i);
            strip.addView(hourItem(first ? getString(R.string.now) : hour(t), kind,
                    first ? 0 : pop.optInt(i, 0), deg(temp), true));
            for (int k = 0; k < 4; k++) {
                String st = sunTimes[k];
                if (st.isEmpty() || i + 1 >= times.length()) continue;
                // the sun event falls inside this hour (after t, before the next hour)
                if (st.compareTo(t) > 0 && st.compareTo(times.getString(i + 1)) < 0 && i + 1 < end) {
                    strip.addView(hourItem(clock(st), sunIsRise[k] ? WeatherIcon.SUNRISE : WeatherIcon.SUNSET, 0,
                            getString(sunIsRise[k] ? R.string.sunrise : R.string.sunset), false));
                }
            }
        }
        return card;
    }

    private View hourItem(String label, int iconKind, int pop, String value, boolean bigValue) {
        LinearLayout it = new LinearLayout(this);
        it.setOrientation(LinearLayout.VERTICAL);
        it.setGravity(Gravity.CENTER_HORIZONTAL);
        it.setMinimumWidth(dp(58));
        it.setPadding(dp(4), 0, dp(4), 0);
        TextView l = text(13, 500, 0xFFFFFFFF);
        l.setText(label);
        it.addView(l);
        WeatherIcon ic = new WeatherIcon(this);
        ic.setKind(iconKind);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(30), dp(30));
        ip.topMargin = dp(6);
        it.addView(ic, ip);
        TextView p = text(10, 600, 0xFF7FC8FF);
        p.setText(pop >= 20 ? pop + "%" : " ");
        it.addView(p);
        TextView v = text(bigValue ? 17 : 12, bigValue ? 500 : 500, 0xFFFFFFFF);
        v.setText(value);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-2, -2);
        vp.topMargin = bigValue ? 0 : dp(3);
        it.addView(v, vp);
        return it;
    }

    private View tiles(JSONObject cur, JSONObject daily) throws Exception {
        LinearLayout row = new LinearLayout(this);
        double t = cur.getDouble("temperature_2m"), feels = cur.getDouble("apparent_temperature");
        String feelsSub = Math.abs(feels - t) < 2 ? getString(R.string.feels_same)
                : getString(feels < t ? R.string.feels_colder : R.string.feels_warmer);
        row.addView(tile(getString(R.string.feels_like), deg(feels), feelsSub, null), tileLp(false));

        double uv = cur.optDouble("uv_index", 0);
        RangeBar uvBar = new RangeBar(this);
        uvBar.setUv((float) uv);
        row.addView(tile(getString(R.string.uv_index), String.valueOf(Math.round(uv)),
                uvLabel(uv) + "\n" + getString(R.string.uv_peak,
                        String.valueOf(Math.round(daily.getJSONArray("uv_index_max").optDouble(0, 0)))), uvBar), tileLp(true));

        row.addView(tile(getString(R.string.wind), Math.round(cur.getDouble("wind_speed_10m")) + " km/h",
                getString(R.string.gusts, String.valueOf(Math.round(cur.optDouble("wind_gusts_10m", 0))),
                        compass(cur.optDouble("wind_direction_10m", 0))), null), tileLp(true));

        row.addView(tile(getString(R.string.humidity), cur.getInt("relative_humidity_2m") + "%",
                getString(R.string.dew_point, deg(cur.optDouble("dew_point_2m", 0))), null), tileLp(true));

        // Next sun event: sunrise before dawn, sunset during the day, tomorrow's sunrise after dusk.
        String now = cur.getString("time");
        JSONArray rise = daily.getJSONArray("sunrise"), set = daily.getJSONArray("sunset");
        String title, value, sub;
        if (now.compareTo(rise.getString(0)) < 0) {
            title = getString(R.string.sunrise); value = clock(rise.getString(0)); sub = getString(R.string.sunset_at, clock(set.getString(0)));
        } else if (now.compareTo(set.getString(0)) < 0) {
            title = getString(R.string.sunset); value = clock(set.getString(0)); sub = getString(R.string.sunrise_at, clock(rise.getString(1)));
        } else {
            title = getString(R.string.sunrise); value = clock(rise.getString(1)); sub = getString(R.string.sunset_at, clock(set.getString(1)));
        }
        row.addView(tile(title, value, sub, null), tileLp(true));
        return row;
    }

    private LinearLayout.LayoutParams tileLp(boolean gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        if (gap) lp.setMarginStart(dp(10));
        return lp;
    }

    private View tile(String caption, String value, String sub, View extra) {
        LinearLayout c = card();
        c.addView(caption(caption));
        TextView v = text(24, 300, 0xFFFFFFFF);
        // A trailing unit ("16 km/h") is set smaller, as Apple does.
        int sp = value.indexOf(' ');
        if (sp > 0) {
            android.text.SpannableString ss = new android.text.SpannableString(value);
            ss.setSpan(new android.text.style.RelativeSizeSpan(0.55f), sp, value.length(), 0);
            v.setText(ss);
        } else {
            v.setText(value);
        }
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-2, -2);
        vp.topMargin = dp(4);
        c.addView(v, vp);
        if (extra != null) {
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(5));
            ep.topMargin = dp(6);
            ep.bottomMargin = dp(2);
            c.addView(extra, ep);
        }
        View spacer = new View(this);
        c.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        TextView s = text(11, 400, 0xE6FFFFFF);
        s.setSingleLine(false);
        s.setMaxLines(3);
        s.setLineSpacing(0, 1.05f);
        s.setText(sub);
        c.addView(s);
        return c;
    }

    private View tenDay(JSONObject cur, JSONObject daily) throws Exception {
        LinearLayout card = card();
        card.addView(caption(getString(R.string.ten_day_forecast)));
        JSONArray dates = daily.getJSONArray("time");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray max = daily.getJSONArray("temperature_2m_max");
        JSONArray min = daily.getJSONArray("temperature_2m_min");
        JSONArray pop = daily.getJSONArray("precipitation_probability_max");
        int n = Math.min(10, dates.length());
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            lo = Math.min(lo, min.getDouble(i));
            hi = Math.max(hi, max.getDouble(i));
        }
        double now = cur.getDouble("temperature_2m");
        lo = Math.min(lo, now);
        hi = Math.max(hi, now);
        for (int i = 0; i < n; i++) {
            View line = rule();
            ((LinearLayout.LayoutParams) line.getLayoutParams()).setMargins(0, dp(2), 0, dp(2));
            card.addView(line);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView d = text(16, 500, 0xFFFFFFFF);
            d.setText(i == 0 ? getString(R.string.today)
                    : LocalDate.parse(dates.getString(i)).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault()));
            row.addView(d, new LinearLayout.LayoutParams(dp(62), -2));

            LinearLayout iconCol = new LinearLayout(this);
            iconCol.setOrientation(LinearLayout.VERTICAL);
            iconCol.setGravity(Gravity.CENTER_HORIZONTAL);
            WeatherIcon ic = new WeatherIcon(this);
            ic.setKind(WeatherIcon.forCode(codes.getInt(i), true));
            iconCol.addView(ic, new LinearLayout.LayoutParams(dp(22), dp(22)));
            int p = pop.optInt(i, 0);
            if (p >= 30) {
                TextView pt = text(10, 600, 0xFF8FD0FF);
                pt.setIncludeFontPadding(false);
                pt.setText(p + "%");
                iconCol.addView(pt);
            }
            row.addView(iconCol, new LinearLayout.LayoutParams(dp(40), -2));

            TextView tl = text(16, 500, 0x99FFFFFF);
            tl.setGravity(Gravity.END);
            tl.setText(deg(min.getDouble(i)));
            row.addView(tl, new LinearLayout.LayoutParams(dp(40), -2));

            RangeBar bar = new RangeBar(this);
            bar.setTemps((float) lo, (float) hi, (float) min.getDouble(i), (float) max.getDouble(i),
                    i == 0 ? (float) now : Float.NaN);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(5), 1f);
            bp.setMarginStart(dp(12));
            bp.setMarginEnd(dp(12));
            row.addView(bar, bp);

            TextView th = text(16, 500, 0xFFFFFFFF);
            th.setText(deg(max.getDouble(i)));
            row.addView(th, new LinearLayout.LayoutParams(dp(36), -2));
            card.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        return card;
    }

    // ---- pieces ----------------------------------------------------------------------------------

    /** Apple's translucent rounded card. */
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        // Deeper, sky-tinted glass so white type and the blue rain chances keep their contrast.
        c.setBackground(Glass.make(dp(22), density, 0x33102A4D, 0x2B0B1F3D, 0x66FFFFFF, 0x0FFFFFFF));
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        return c;
    }

    private TextView caption(String s) {
        TextView t = text(11, 600, 0x99FFFFFF);
        t.setAllCaps(true);
        t.setLetterSpacing(0.08f);
        t.setText(s);
        t.setPadding(0, 0, 0, dp(4));
        return t;
    }

    private View rule() {
        View v = new View(this);
        v.setBackgroundColor(0x26FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.5f)));
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView text(float sp, int weight, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        if (outfit != null) t.setTypeface(outfit);
        t.setFontVariationSettings("'wght' " + weight);
        return t;
    }

    /** Sky colours after Apple Weather: clear blue by day, deep navy at night, greyer as it clouds over. */
    private void setSky(int code, boolean day) {
        int top, bottom;
        int kind = WeatherIcon.forCode(code, day);
        if (!day) { top = 0xFF0B1630; bottom = 0xFF2A3D66; }
        else if (kind == WeatherIcon.CLEAR) { top = 0xFF2F6FC4; bottom = 0xFF6FA8E8; }
        else if (kind == WeatherIcon.PARTLY) { top = 0xFF3B6EA8; bottom = 0xFF7EA4CC; }
        else if (kind == WeatherIcon.RAIN) { top = 0xFF3A4757; bottom = 0xFF66788C; }
        else if (kind == WeatherIcon.THUNDER) { top = 0xFF262A3D; bottom = 0xFF4B5070; }
        else if (kind == WeatherIcon.SNOW) { top = 0xFF6F86A3; bottom = 0xFFAFC1D6; }
        else { top = 0xFF51627A; bottom = 0xFF8698AE; }   // cloudy, fog
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        root.setBackground(g);
        getWindow().getDecorView().setBackgroundColor(top);
    }

    private int dp(float v) { return Math.round(v * density); }

    private static String deg(double t) { return Math.round(t) + "°"; }

    private String clock(String iso) {
        LocalDateTime t = LocalDateTime.parse(iso);
        return t.format(DateTimeFormatter.ofPattern(h24 ? "HH:mm" : "h:mm a"));
    }

    private String hour(String iso) {
        LocalDateTime t = LocalDateTime.parse(iso);
        return t.format(DateTimeFormatter.ofPattern(h24 ? "HH" : "h a"));
    }

    private String uvLabel(double uv) {
        if (uv < 3) return getString(R.string.uv_low);
        if (uv < 6) return getString(R.string.uv_moderate);
        if (uv < 8) return getString(R.string.uv_high);
        if (uv < 11) return getString(R.string.uv_very_high);
        return getString(R.string.uv_extreme);
    }

    private static String compass(double deg) {
        String[] d = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return d[(int) Math.round(((deg % 360) + 360) % 360 / 45.0) % 8];
    }
}
