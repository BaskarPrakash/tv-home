package com.baskar.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final String PREF_BACKGROUND = "background";
    private static final String PREF_SORT = "sort";
    private static final String PREF_ORDER = "order";
    private static final String PREF_CITY = "city";
    private static final String PREF_LATITUDE = "latitude";
    private static final String PREF_LONGITUDE = "longitude";
    private static final String PREF_WEATHER = "weather";
    private static final int UPLOADED_IMAGE = -1;
    private static final int SORT_CUSTOM = 0;
    private static final int SORT_USAGE = 1;
    private static final int SORT_NAME = 2;
    private static final String[] SORT_NAMES = {"My order", "Most used", "A to Z"};
    private static final int CATEGORY_ALL = 0;
    private static final int CATEGORY_VIDEO = 1;
    private static final int CATEGORY_MUSIC = 2;
    private static final int CATEGORY_GAMES = 3;
    private static final int CATEGORY_OTHER = 4;
    private static final String[] CATEGORY_NAMES = {"All", "Video", "Music", "Games", "Other"};
    private static final String[] CATEGORY_ICONS = {"ic_all", "ic_video", "ic_music", "ic_games", "ic_other"};
    private static final String[] PRESET_NAMES = {"Midnight", "Forest", "Sunset", "Slate", "Ocean"};
    private static final int[][] PRESET_COLORS = {
            {0xFF0F2027, 0xFF2C5364},
            {0xFF0B3D2E, 0xFF3A7D44},
            {0xFF42275A, 0xFFE9765B},
            {0xFF232526, 0xFF596164},
            {0xFF1565C0, 0xFF26C6DA},
    };
    private static final int FORECAST_DAYS = 5;
    private static final long WEATHER_REFRESH_MS = TimeUnit.MINUTES.toMillis(30);
    private static final int PANEL_COLOR = 0xB8081420;
    private static final int PANEL_EDGE = 0x8000E5FF;
    private static final int INK = 0xFFE6FBFF;
    private static final int MUTED_INK = 0xFF8FC7D6;
    private static final int ACCENT = 0xFF00E5FF;
    private static final int ON_ACCENT = 0xFF04131D;
    private static final int FOCUS_RING = 0xFFFFFFFF;
    private static final int MOVE_RING = 0xFF39FF14;
    private static final int BUBBLE_SIZE = 54;
    private static final int TILE_WIDTH = 63;
    private static final int COLUMNS = 12;
    private static final int GRID_HEIGHT = 86;
    private static final int GRID_PADDING = 8;
    private static final int SCRIM_SIZE = 170;
    private static final int RAIL_BUTTON = 32;
    private static final String TV_INPUT_PACKAGE = "com.tcl.tvinput";
    private static final String TV_APP_PACKAGE = "com.tcl.tv";

    private static class App {
        final String label;
        final Drawable icon;
        final Intent launch;
        final String packageName;
        final int category;

        App(String label, Drawable icon, Intent launch, String packageName, int category) {
            this.label = label;
            this.icon = icon;
            this.launch = launch;
            this.packageName = packageName;
            this.category = category;
        }
    }

    private final List<App> apps = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            if (System.currentTimeMillis() - lastWeatherFetch > WEATHER_REFRESH_MS) {
                refreshWeather();
            }
            handler.postDelayed(this, 1000);
        }
    };
    private final ImageView[] railIcons = new ImageView[CATEGORY_NAMES.length];
    private final TextView[] forecastIcons = new TextView[FORECAST_DAYS];
    private final TextView[] forecastTemps = new TextView[FORECAST_DAYS];
    private final TextView[] forecastDays = new TextView[FORECAST_DAYS];

    private ImageView background;
    private ScrollView appScroller;
    private GridLayout appGrid;
    private ClockView clock;
    private TextView timeText;
    private TextView dateText;
    private TextView cityText;
    private TextView hourText;
    private TextView minuteText;
    private TextView temperatureText;
    private TextView feelsLikeText;
    private TextView sunriseText;
    private TextView sunsetText;
    private TextView weatherIcon;
    private TextView conditionText;
    private TextView humidityText;
    private TextView windText;
    private TextView pressureText;
    private TextView uvText;
    private SharedPreferences prefs;
    private File imageFile;
    private BackgroundServer server;
    private int category = CATEGORY_ALL;
    private App moving;
    private boolean swallowKeyUp;
    private long lastWeatherFetch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("home", Context.MODE_PRIVATE);
        imageFile = new File(getFilesDir(), "background");

        FrameLayout root = new FrameLayout(this);
        background = new ImageView(this);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(background, new FrameLayout.LayoutParams(-1, -1));

        // Darkens the edges of the photo so the sidebar and app labels stay readable on bright pictures.
        root.addView(scrim(GradientDrawable.Orientation.BOTTOM_TOP),
                new FrameLayout.LayoutParams(-1, dp(SCRIM_SIZE), Gravity.BOTTOM));
        root.addView(scrim(GradientDrawable.Orientation.LEFT_RIGHT),
                new FrameLayout.LayoutParams(dp(SCRIM_SIZE), -1, Gravity.START));

        LinearLayout content = new LinearLayout(this);
        content.setPadding(dp(24), dp(20), dp(28), dp(14));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.addView(buildRail(), new LinearLayout.LayoutParams(dp(48), -1));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setClipChildren(false);
        main.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(36)));
        LinearLayout.LayoutParams cards = new LinearLayout.LayoutParams(-1, dp(88));
        cards.topMargin = dp(10);
        main.addView(buildCards(), cards);
        LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(-1, dp(36));
        bar.topMargin = dp(10);
        main.addView(buildCategoryBar(), bar);
        // The gap leaves the middle of the screen clear for the background photo.
        main.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1));
        main.addView(buildAppGrid(), new LinearLayout.LayoutParams(-1, dp(GRID_HEIGHT)));
        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(0, -1, 1);
        mainParams.leftMargin = dp(16);
        content.addView(main, mainParams);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
        applyBackground();
        showCategory();
        renderWeather();
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(tick);
        try {
            server = new BackgroundServer(imageFile, () -> runOnUiThread(() -> {
                prefs.edit().putInt(PREF_BACKGROUND, UPLOADED_IMAGE).apply();
                applyBackground();
            }));
            server.start();
        } catch (IOException e) {
            server = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(tick);
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (moving != null) {
            stopMoving();
        }
    }

    @Override
    public void onBackPressed() {
        // This is the home screen, so there is nothing to go back to.
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean select = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        View focus = getCurrentFocus();
        if (swallowKeyUp && select) {
            // These are the repeats and release of the long press that was already handled.
            if (!down) {
                swallowKeyUp = false;
                if (focus != null) {
                    focus.setPressed(false);
                }
            }
            return true;
        }
        if (moving == null) {
            if (select && down && event.isLongPress() && focus != null && focus.getTag() instanceof App) {
                swallowKeyUp = true;
                focus.setPressed(false);
                if (category == CATEGORY_ALL) {
                    startMoving((App) focus.getTag(), focus);
                } else {
                    Toast.makeText(this, "Open All to move apps", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        if (select || key == KeyEvent.KEYCODE_BACK) {
            if (!down) {
                stopMoving();
            }
            return true;
        }
        int step = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1
                : key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1
                : key == KeyEvent.KEYCODE_DPAD_UP ? -COLUMNS
                : key == KeyEvent.KEYCODE_DPAD_DOWN ? COLUMNS : 0;
        if (step == 0) {
            return super.dispatchKeyEvent(event);
        }
        if (down) {
            moveTile(step);
        }
        return true;
    }

    private View buildRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(0, dp(10), 0, dp(10));
        rail.setClipChildren(false);
        rail.setClipToPadding(false);
        for (int i = 0; i < CATEGORY_NAMES.length; i++) {
            int index = i;
            railIcons[i] = railButton(CATEGORY_ICONS[i], () -> {
                category = index;
                showCategory();
                renderApps();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(RAIL_BUTTON), dp(RAIL_BUTTON));
            params.bottomMargin = dp(10);
            rail.addView(railIcons[i], params);
        }
        rail.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1));
        LinearLayout.LayoutParams input = new LinearLayout.LayoutParams(dp(RAIL_BUTTON), dp(RAIL_BUTTON));
        input.bottomMargin = dp(10);
        rail.addView(railButton("ic_input", this::showInputDialog), input);
        rail.addView(railButton("ic_customize", this::showCustomizeDialog),
                new LinearLayout.LayoutParams(dp(RAIL_BUTTON), dp(RAIL_BUTTON)));
        return rail;
    }

    private ImageView railButton(String icon, Runnable action) {
        ImageView button = new ImageView(this);
        button.setImageDrawable(icon(icon, ACCENT));
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setBackground(panel(RAIL_BUTTON / 2));
        focusable(button, RAIL_BUTTON / 2, action);
        return button;
    }

    private void showCategory() {
        for (int i = 0; i < railIcons.length; i++) {
            boolean active = i == category;
            railIcons[i].setBackground(active ? rounded(ACCENT, RAIL_BUTTON / 2) : panel(RAIL_BUTTON / 2));
            railIcons[i].setImageDrawable(icon(CATEGORY_ICONS[i], active ? ON_ACCENT : ACCENT));
        }
        appScroller.scrollTo(0, 0);
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(pill("ic_settings", "Settings", () -> launch(new Intent(Settings.ACTION_SETTINGS))),
                new LinearLayout.LayoutParams(-2, -1));
        View network = pill("ic_network", "Network", () -> launch(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        LinearLayout.LayoutParams networkParams = new LinearLayout.LayoutParams(-2, -1);
        networkParams.leftMargin = dp(10);
        bar.addView(network, networkParams);
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1));

        LinearLayout time = new LinearLayout(this);
        time.setGravity(Gravity.CENTER_VERTICAL);
        time.setPadding(dp(12), 0, dp(12), 0);
        time.setBackground(panel(10));
        timeText = text(17, ACCENT, true);
        time.addView(timeText);
        dateText = text(10, MUTED_INK, false);
        dateText.setGravity(Gravity.END);
        dateText.setPadding(dp(12), 0, 0, 0);
        time.addView(dateText);
        bar.addView(time, new LinearLayout.LayoutParams(-2, -1));
        return bar;
    }

    private View pill(String icon, String label, Runnable action) {
        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(10), 0, dp(12), 0);
        pill.setBackground(panel(10));
        ImageView image = new ImageView(this);
        image.setImageDrawable(icon(icon, ACCENT));
        pill.addView(image, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView text = text(12, INK, false);
        text.setText(label);
        text.setPadding(dp(6), 0, 0, 0);
        pill.addView(text);
        focusable(pill, 10, action);
        return pill;
    }

    private View buildCards() {
        LinearLayout cards = new LinearLayout(this);
        cards.setClipChildren(false);
        cards.addView(buildClockCard(), new LinearLayout.LayoutParams(0, -1, 0.95f));
        LinearLayout.LayoutParams weather = new LinearLayout.LayoutParams(0, -1, 2.1f);
        weather.leftMargin = dp(12);
        cards.addView(buildWeatherCard(), weather);
        LinearLayout.LayoutParams forecast = new LinearLayout.LayoutParams(0, -1, 1.25f);
        forecast.leftMargin = dp(12);
        cards.addView(buildForecastCard(), forecast);
        return cards;
    }

    private View buildClockCard() {
        FrameLayout card = new FrameLayout(this);
        card.setPadding(dp(10), dp(4), dp(10), dp(4));
        card.setBackground(panel(12));
        cityText = text(10, INK, true);
        cityText.setSingleLine(true);
        card.addView(cityText, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        clock = new ClockView(this);
        FrameLayout.LayoutParams hands = new FrameLayout.LayoutParams(-1, -1);
        hands.topMargin = dp(12);
        card.addView(clock, hands);
        hourText = text(11, INK, true);
        card.addView(hourText, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START));
        minuteText = text(11, INK, true);
        card.addView(minuteText, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END));
        return card;
    }

    private View buildWeatherCard() {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(3), dp(10), dp(3));
        card.setBackground(panel(12));
        focusable(card, 12, this::showCityDialog);

        LinearLayout now = new LinearLayout(this);
        now.setOrientation(LinearLayout.VERTICAL);
        temperatureText = text(20, ACCENT, true);
        now.addView(temperatureText);
        feelsLikeText = text(8, MUTED_INK, false);
        now.addView(feelsLikeText);
        sunriseText = text(8, INK, false);
        sunriseText.setPadding(0, dp(2), 0, 0);
        now.addView(sunriseText);
        sunsetText = text(8, INK, false);
        now.addView(sunsetText);
        card.addView(now, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout sky = new LinearLayout(this);
        sky.setOrientation(LinearLayout.VERTICAL);
        sky.setGravity(Gravity.CENTER);
        weatherIcon = text(26, INK, false);
        weatherIcon.setGravity(Gravity.CENTER);
        sky.addView(weatherIcon, new LinearLayout.LayoutParams(-1, -2));
        conditionText = text(9, INK, true);
        conditionText.setGravity(Gravity.CENTER);
        sky.addView(conditionText, new LinearLayout.LayoutParams(-1, -2));
        card.addView(sky, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.VERTICAL);
        humidityText = text(10, INK, true);
        windText = text(10, INK, true);
        pressureText = text(10, INK, true);
        uvText = text(10, INK, true);
        stats.addView(statRow(humidityText, "Humidity", windText, "Wind speed"));
        stats.addView(statRow(pressureText, "Pressure", uvText, "UV"));
        card.addView(stats, new LinearLayout.LayoutParams(0, -2, 1.1f));
        return card;
    }

    private View statRow(TextView first, String firstLabel, TextView second, String secondLabel) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(2), 0, dp(2));
        row.addView(stat(first, firstLabel), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(stat(second, secondLabel), new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private View stat(TextView value, String label) {
        LinearLayout stat = new LinearLayout(this);
        stat.setOrientation(LinearLayout.VERTICAL);
        stat.setGravity(Gravity.CENTER_HORIZONTAL);
        value.setSingleLine(true);
        stat.addView(value);
        TextView caption = text(8, MUTED_INK, false);
        caption.setText(label);
        stat.addView(caption);
        return stat;
    }

    private View buildForecastCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(4), dp(10), dp(4));
        card.setBackground(panel(12));
        for (int i = 0; i < FORECAST_DAYS; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            forecastIcons[i] = text(9, INK, false);
            row.addView(forecastIcons[i], new LinearLayout.LayoutParams(dp(18), -2));
            forecastTemps[i] = text(9, INK, true);
            row.addView(forecastTemps[i], new LinearLayout.LayoutParams(dp(28), -2));
            forecastDays[i] = text(9, INK, true);
            forecastDays[i].setGravity(Gravity.END);
            forecastDays[i].setSingleLine(true);
            row.addView(forecastDays[i], new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        return card;
    }

    private View buildCategoryBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(16), 0);
        ImageView sort = new ImageView(this);
        sort.setImageDrawable(icon("ic_sort", ON_ACCENT));
        sort.setPadding(dp(5), dp(5), dp(5), dp(5));
        sort.setBackground(rounded(ACCENT, 13));
        focusable(sort, 13, this::showSortDialog);
        bar.addView(sort, new LinearLayout.LayoutParams(dp(26), dp(26)));
        return bar;
    }

    private View buildAppGrid() {
        appScroller = new ScrollView(this);
        appScroller.setVerticalScrollBarEnabled(false);
        appGrid = new GridLayout(this);
        appGrid.setColumnCount(COLUMNS);
        appGrid.setClipChildren(false);
        appGrid.setClipToPadding(false);
        appGrid.setPadding(dp(8), dp(GRID_PADDING), dp(8), dp(GRID_PADDING));
        appScroller.addView(appGrid, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL));
        // The layouts above leave children unclipped so focus rings can overflow, but rows
        // outside the visible strip must stay hidden, so this parent clips the scroller.
        FrameLayout clip = new FrameLayout(this);
        clip.addView(appScroller, new FrameLayout.LayoutParams(-1, -1));
        return clip;
    }

    /** Snaps to the row holding the tile, so only whole rows are ever visible. */
    private void showRow(View tile) {
        appScroller.smoothScrollTo(0, tile.getTop() - dp(GRID_PADDING));
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Set<String> seen = new HashSet<>();
        seen.add(getPackageName());
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        found.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        apps.clear();
        for (ResolveInfo app : found) {
            ActivityInfo info = app.activityInfo;
            if (!seen.add(info.packageName)) {
                continue;
            }
            // Wide banners crop badly in a circle, so they are only for apps with no icon.
            Drawable banner = info.getIconResource() == 0 ? info.loadBanner(pm) : null;
            Drawable icon = banner != null ? banner : info.loadIcon(pm);
            Intent leanback = pm.getLeanbackLaunchIntentForPackage(info.packageName);
            Intent launch = leanback != null ? leanback : pm.getLaunchIntentForPackage(info.packageName);
            if (launch != null) {
                apps.add(new App(app.loadLabel(pm).toString(), icon, launch, info.packageName,
                        categoryOf(info.applicationInfo)));
            }
        }
        sortApps();
        renderApps();
    }

    private static int categoryOf(ApplicationInfo info) {
        if (info.category == ApplicationInfo.CATEGORY_GAME || (info.flags & ApplicationInfo.FLAG_IS_GAME) != 0) {
            return CATEGORY_GAMES;
        } else if (info.category == ApplicationInfo.CATEGORY_VIDEO) {
            return CATEGORY_VIDEO;
        } else if (info.category == ApplicationInfo.CATEGORY_AUDIO) {
            return CATEGORY_MUSIC;
        }
        return CATEGORY_OTHER;
    }

    private void sortApps() {
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        int sort = prefs.getInt(PREF_SORT, SORT_NAME);
        if (sort == SORT_USAGE) {
            Map<String, Long> usage = usageTimes();
            Collections.sort(apps, (a, b) -> Long.compare(
                    usage.getOrDefault(b.packageName, 0L), usage.getOrDefault(a.packageName, 0L)));
        } else if (sort == SORT_CUSTOM) {
            Map<String, Integer> rank = new HashMap<>();
            for (String name : prefs.getString(PREF_ORDER, "").split(",")) {
                rank.put(name, rank.size());
            }
            // Newly installed apps have no rank yet, so they go at the end.
            Collections.sort(apps, (a, b) -> Integer.compare(
                    rank.getOrDefault(a.packageName, Integer.MAX_VALUE),
                    rank.getOrDefault(b.packageName, Integer.MAX_VALUE)));
        }
    }

    /** Foreground time per app over the last 30 days; empty without usage access. */
    private Map<String, Long> usageTimes() {
        long now = System.currentTimeMillis();
        Map<String, UsageStats> stats = getSystemService(UsageStatsManager.class)
                .queryAndAggregateUsageStats(now - TimeUnit.DAYS.toMillis(30), now);
        Map<String, Long> times = new HashMap<>();
        for (Map.Entry<String, UsageStats> entry : stats.entrySet()) {
            times.put(entry.getKey(), entry.getValue().getTotalTimeInForeground());
        }
        return times;
    }

    private void renderApps() {
        View focus = getCurrentFocus();
        boolean focusInGrid = focus == null || focus.getParent() == appGrid;
        String focused = focus != null && focus.getTag() instanceof App ? ((App) focus.getTag()).packageName : null;
        appGrid.removeAllViews();
        View first = null;
        View restore = null;
        for (App app : apps) {
            if (category == CATEGORY_ALL || app.category == category) {
                View tile = appTile(app.label, app.icon, app, () -> launch(app.launch));
                appGrid.addView(tile);
                first = first == null ? tile : first;
                restore = app.packageName.equals(focused) ? tile : restore;
            }
        }
        Drawable add = icon("ic_add", ACCENT);
        View addTile = appTile("Add app", add, null, () -> {
            Intent store = getPackageManager().getLeanbackLaunchIntentForPackage("com.android.vending");
            if (store != null) {
                launch(store);
            }
        });
        appGrid.addView(addTile);
        if (focusInGrid) {
            View target = restore != null ? restore : first != null ? first : addTile;
            target.requestFocus();
        }
    }

    private View appTile(String label, Drawable icon, App app, Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setClipChildren(false);
        tile.setTag(app);

        ImageView image = new ImageView(this);
        image.setImageDrawable(icon);
        image.setScaleType(app == null ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        if (app == null) {
            image.setPadding(dp(15), dp(15), dp(15), dp(15));
        }
        image.setBackground(app == null ? panel(BUBBLE_SIZE / 2) : rounded(Color.WHITE, BUBBLE_SIZE / 2));
        image.setClipToOutline(true);
        tile.addView(image, new LinearLayout.LayoutParams(dp(BUBBLE_SIZE), dp(BUBBLE_SIZE)));

        TextView text = text(9, Color.WHITE, false);
        text.setText(label);
        text.setShadowLayer(4, 0, 1, Color.BLACK);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, dp(4), 0, 0);
        tile.addView(text, new LinearLayout.LayoutParams(dp(TILE_WIDTH), -2));

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.rightMargin = dp(4);
        params.bottomMargin = dp(8);
        tile.setLayoutParams(params);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setOnClickListener(view -> action.run());
        tile.setOnFocusChangeListener((view, hasFocus) -> {
            image.setForeground(hasFocus ? ring(moving != null ? MOVE_RING : FOCUS_RING, BUBBLE_SIZE / 2) : null);
            view.animate().scaleX(hasFocus ? 1.12f : 1f).scaleY(hasFocus ? 1.12f : 1f).setDuration(120).start();
            if (hasFocus) {
                // Wait for layout, because a tile that was just added has no position yet.
                view.post(() -> showRow(view));
            }
        });
        return tile;
    }

    private void startMoving(App app, View tile) {
        moving = app;
        ((ImageView) ((ViewGroup) tile).getChildAt(0)).setForeground(ring(MOVE_RING, BUBBLE_SIZE / 2));
        Toast.makeText(this, "Use the arrows to move, then press OK", Toast.LENGTH_LONG).show();
    }

    private void moveTile(int step) {
        int from = apps.indexOf(moving);
        int to = from + step;
        if (to < 0 || to >= apps.size()) {
            return;
        }
        apps.add(to, apps.remove(from));
        // Move the neighbours rather than the tile, so the tile keeps its focus.
        int direction = Integer.signum(step);
        for (int i = from; i != to; i += direction) {
            View neighbour = appGrid.getChildAt(i + direction);
            appGrid.removeViewAt(i + direction);
            appGrid.addView(neighbour, i);
        }
        View tile = appGrid.getChildAt(to);
        tile.post(() -> showRow(tile));
    }

    private void stopMoving() {
        moving = null;
        View focus = getCurrentFocus();
        if (focus != null && focus.getParent() == appGrid) {
            ((ImageView) ((ViewGroup) focus).getChildAt(0)).setForeground(ring(FOCUS_RING, BUBBLE_SIZE / 2));
        }
        StringBuilder order = new StringBuilder();
        for (App app : apps) {
            order.append(app.packageName).append(',');
        }
        // Moving a tile switches to the custom order, starting from what was on screen.
        prefs.edit().putString(PREF_ORDER, order.toString()).putInt(PREF_SORT, SORT_CUSTOM).apply();
    }

    private void updateClock() {
        Date now = new Date();
        Calendar calendar = Calendar.getInstance();
        boolean twentyFour = DateFormat.is24HourFormat(this);
        timeText.setText(DateFormat.getTimeFormat(this).format(now));
        dateText.setText(DateFormat.format("EEEE\nd MMMM yyyy", now));
        int hour = calendar.get(twentyFour ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
        hourText.setText(String.format(Locale.getDefault(), "%02d", !twentyFour && hour == 0 ? 12 : hour));
        minuteText.setText(String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.MINUTE)));
        clock.invalidate();
    }

    private void refreshWeather() {
        String latitude = prefs.getString(PREF_LATITUDE, null);
        String longitude = prefs.getString(PREF_LONGITUDE, null);
        lastWeatherFetch = System.currentTimeMillis();
        if (latitude == null || longitude == null) {
            return;
        }
        new Thread(() -> {
            try {
                String weather = WeatherClient.forecast(latitude, longitude);
                prefs.edit().putString(PREF_WEATHER, weather).apply();
                runOnUiThread(this::renderWeather);
            } catch (IOException ignored) {
                // Keep showing the last forecast; the next refresh will try again.
            }
        }).start();
    }

    private void renderWeather() {
        String city = prefs.getString(PREF_CITY, null);
        cityText.setText(city != null ? city : "Local time");
        String weather = prefs.getString(PREF_WEATHER, null);
        try {
            if (city != null && weather != null) {
                showForecast(new JSONObject(weather));
                return;
            }
        } catch (JSONException ignored) {
            // A bad cached forecast is treated the same as having none.
        }
        temperatureText.setText("--°");
        feelsLikeText.setText(city != null ? "Loading weather" : "Press OK to set your city");
        for (TextView view : new TextView[]{sunriseText, sunsetText, weatherIcon, conditionText,
                humidityText, windText, pressureText, uvText}) {
            view.setText("");
        }
        for (int i = 0; i < FORECAST_DAYS; i++) {
            forecastIcons[i].setText("");
            forecastTemps[i].setText("");
            forecastDays[i].setText("");
        }
    }

    private void showForecast(JSONObject weather) throws JSONException {
        JSONObject current = weather.getJSONObject("current");
        JSONObject daily = weather.getJSONObject("daily");
        String[] condition = WeatherClient.describe(current.getInt("weather_code"));
        temperatureText.setText(degrees(current.getDouble("temperature_2m")) + "C");
        feelsLikeText.setText("Feels like " + degrees(current.getDouble("apparent_temperature")) + "C");
        sunriseText.setText("Sunrise  " + daily.getJSONArray("sunrise").getString(0).substring(11));
        sunsetText.setText("Sunset  " + daily.getJSONArray("sunset").getString(0).substring(11));
        weatherIcon.setText(condition[0]);
        conditionText.setText(condition[1]);
        humidityText.setText(current.getInt("relative_humidity_2m") + "%");
        windText.setText(String.format(Locale.getDefault(), "%.1f km/h", current.getDouble("wind_speed_10m")));
        pressureText.setText(String.format(Locale.getDefault(), "%.0f hPa", current.getDouble("pressure_msl")));
        uvText.setText(String.format(Locale.getDefault(), "%.1f", daily.getJSONArray("uv_index_max").optDouble(0, 0)));

        JSONArray days = daily.getJSONArray("time");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray highs = daily.getJSONArray("temperature_2m_max");
        DateTimeFormatter format = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault());
        for (int i = 0; i < FORECAST_DAYS; i++) {
            forecastIcons[i].setText(WeatherClient.describe(codes.getInt(i + 1))[0]);
            forecastTemps[i].setText(degrees(highs.getDouble(i + 1)));
            forecastDays[i].setText(LocalDate.parse(days.getString(i + 1)).format(format));
        }
    }

    private static String degrees(double value) {
        return Math.round(value) + "°";
    }

    private void showInputDialog() {
        List<TvInputInfo> inputs = new ArrayList<>();
        TvInputManager manager = getSystemService(TvInputManager.class);
        if (manager != null) {
            for (TvInputInfo info : manager.getTvInputList()) {
                // Streaming apps register as inputs too, so keep only the TV's own hardware.
                // Inputs with a parent are devices behind an HDMI port, which is already listed.
                if (TV_INPUT_PACKAGE.equals(info.getServiceInfo().packageName)
                        && (info.getType() == TvInputInfo.TYPE_HDMI || info.getType() == TvInputInfo.TYPE_TUNER)
                        && info.getParentId() == null && !info.isHidden(this)) {
                    inputs.add(info);
                }
            }
        }
        Collections.sort(inputs, (a, b) -> inputLabel(a).compareToIgnoreCase(inputLabel(b)));
        List<String> labels = new ArrayList<>();
        for (TvInputInfo info : inputs) {
            labels.add(inputLabel(info));
        }
        if (inputs.isEmpty()) {
            Toast.makeText(this, "No inputs found", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Input source")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> openInput(inputs.get(which)))
                .show();
    }

    private String inputLabel(TvInputInfo info) {
        CharSequence custom = info.loadCustomLabel(this);
        return (custom != null ? custom : info.loadLabel(this)).toString();
    }

    private void openInput(TvInputInfo input) {
        if (input.isPassthroughInput()) {
            launch(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.getId())));
            return;
        }
        Intent tuner = getPackageManager().getLeanbackLaunchIntentForPackage(TV_APP_PACKAGE);
        launch(tuner != null ? tuner : new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI));
    }

    private void showCustomizeDialog() {
        Intent original = originalHome();
        List<String> items = new ArrayList<>();
        Collections.addAll(items, "Change background", "Sort apps", "Set city");
        if (original != null) {
            items.add("Original home");
        }
        new AlertDialog.Builder(this)
                .setTitle("Customize")
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        showBackgroundDialog();
                    } else if (which == 1) {
                        showSortDialog();
                    } else if (which == 2) {
                        showCityDialog();
                    } else {
                        launch(original);
                    }
                })
                .show();
    }

    private void showCityDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(prefs.getString(PREF_CITY, ""));
        input.setHint("City name");
        new AlertDialog.Builder(this)
                .setTitle("Set city for weather")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> saveCity(input.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveCity(String name) {
        if (name.isEmpty()) {
            prefs.edit().remove(PREF_CITY).remove(PREF_LATITUDE).remove(PREF_LONGITUDE)
                    .remove(PREF_WEATHER).apply();
            renderWeather();
            return;
        }
        new Thread(() -> {
            try {
                JSONObject city = WeatherClient.findCity(name);
                runOnUiThread(() -> {
                    if (city == null) {
                        Toast.makeText(this, "City not found", Toast.LENGTH_LONG).show();
                        return;
                    }
                    prefs.edit().putString(PREF_CITY, city.optString("name", name))
                            .putString(PREF_LATITUDE, city.optString("latitude"))
                            .putString(PREF_LONGITUDE, city.optString("longitude"))
                            .remove(PREF_WEATHER).apply();
                    renderWeather();
                    refreshWeather();
                });
            } catch (IOException | JSONException e) {
                runOnUiThread(() -> Toast.makeText(this, "Could not look up that city", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showSortDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Sort apps (hold OK on an app to move it)")
                .setSingleChoiceItems(SORT_NAMES, prefs.getInt(PREF_SORT, SORT_NAME), (dialog, which) -> {
                    dialog.dismiss();
                    prefs.edit().putInt(PREF_SORT, which).apply();
                    if (which == SORT_USAGE && usageTimes().isEmpty()) {
                        Toast.makeText(this, "Usage access is off for TV Home", Toast.LENGTH_LONG).show();
                    }
                    sortApps();
                    renderApps();
                })
                .show();
    }

    private Intent originalHome() {
        for (ResolveInfo home : getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) {
            ActivityInfo info = home.activityInfo;
            if (!info.packageName.equals(getPackageName()) && !info.name.endsWith("FallbackHome")) {
                return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setComponent(new ComponentName(info.packageName, info.name));
            }
        }
        return null;
    }

    private void launch(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Toast.makeText(this, "Could not open that", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBackgroundDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Upload a picture at " + uploadAddress())
                .setItems(PRESET_NAMES, (dialog, which) -> {
                    prefs.edit().putInt(PREF_BACKGROUND, which).apply();
                    applyBackground();
                })
                .show();
    }

    private String uploadAddress() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return "http://" + address.getHostAddress() + ":" + BackgroundServer.PORT;
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return "port " + BackgroundServer.PORT;
    }

    private void applyBackground() {
        int choice = prefs.getInt(PREF_BACKGROUND, 0);
        int preset = Math.max(choice, 0) % PRESET_COLORS.length;
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, PRESET_COLORS[preset]);
        if (choice != UPLOADED_IMAGE) {
            background.setImageDrawable(gradient);
            return;
        }
        // Large photos are slow to decode, so the screen shouldn't wait for them.
        new Thread(() -> {
            Bitmap image = loadImage();
            runOnUiThread(() -> {
                if (image != null) {
                    background.setImageBitmap(image);
                } else {
                    background.setImageDrawable(gradient);
                }
            });
        }).start();
    }

    /** Reads the screen-sized copy of the upload, making it first if the upload is newer. */
    private Bitmap loadImage() {
        File cache = new File(getFilesDir(), "background.cache");
        if (cache.lastModified() >= imageFile.lastModified()) {
            Bitmap cached = BitmapFactory.decodeFile(cache.getPath());
            if (cached != null) {
                return cached;
            }
        }
        Bitmap image = decodeUpload();
        if (image != null) {
            try (FileOutputStream out = new FileOutputStream(cache)) {
                image.compress(Bitmap.CompressFormat.JPEG, 92, out);
            } catch (IOException ignored) {
                // The copy only saves time, so the next start can simply decode the upload again.
            }
        }
        return image;
    }

    private Bitmap decodeUpload() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getPath(), options);
        int width = getResources().getDisplayMetrics().widthPixels;
        options.inSampleSize = 1;
        while (options.outWidth / (options.inSampleSize * 2) >= width) {
            options.inSampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        Bitmap image = BitmapFactory.decodeFile(imageFile.getPath(), options);
        int rotation = photoRotation();
        if (image == null || rotation == 0) {
            return image;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        return Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true);
    }

    /** Phone photos store their rotation as a tag, which BitmapFactory ignores. */
    private int photoRotation() {
        try {
            int orientation = new ExifInterface(imageFile.getPath())
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            return orientation == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                    : orientation == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : orientation == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    private void focusable(View view, int radius, Runnable action) {
        view.setFocusable(true);
        view.setClickable(true);
        view.setOnClickListener(clicked -> action.run());
        view.setOnFocusChangeListener((focused, hasFocus) -> {
            focused.setForeground(hasFocus ? ring(FOCUS_RING, radius) : null);
            // Grow by a fixed distance so wide views don't spread over their neighbours.
            float scale = hasFocus ? 1 + (float) dp(8) / focused.getWidth() : 1f;
            focused.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
        });
    }

    private TextView text(int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        text.setTextColor(color);
        text.setTypeface(Typeface.create("sans-serif-condensed", bold ? Typeface.BOLD : Typeface.NORMAL));
        text.setLetterSpacing(0.04f);
        return text;
    }

    private Drawable icon(String name, int color) {
        Drawable icon = getDrawable(getResources().getIdentifier(name, "drawable", getPackageName())).mutate();
        icon.setTint(color);
        return icon;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private View scrim(GradientDrawable.Orientation orientation) {
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(orientation, new int[]{0xF2000000, 0xB3000000, 0x00000000}));
        return scrim;
    }

    /** Dark glass with a thin neon edge, used by the sidebar buttons and widgets. */
    private GradientDrawable panel(int radius) {
        GradientDrawable shape = rounded(PANEL_COLOR, radius);
        shape.setStroke(dp(1), PANEL_EDGE);
        return shape;
    }

    private GradientDrawable ring(int color, int radius) {
        GradientDrawable shape = rounded(Color.TRANSPARENT, radius);
        shape.setStroke(dp(3), color);
        return shape;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
