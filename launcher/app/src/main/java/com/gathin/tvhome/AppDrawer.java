package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "All Apps" drawer: Right at the right edge of the home screen slides it in, like swiping to the
 * App Library on a phone. Every launchable app (hidden ones included) in one alphabetical list.
 * Left or Back slides it away and focus goes back where it was.
 */
public class AppDrawer extends FrameLayout {

    private static final int DURATION = 280;

    /** The home screen, which owns the Add to Home / Remove from Home menu. */
    interface Host {
        void onDrawerLongPress(String pkg, String label, View row);
    }

    private final Host host;

    private final float density;
    private final View scrim;
    private final LinearLayout panel;
    private final TextView count;
    private final RecyclerView list;
    private final Adapter adapter = new Adapter();
    private Typeface outfit;
    private View returnFocus;
    private boolean open;

    private static final class App {
        final String label, pkg;
        final Intent launch;
        Drawable icon;
        App(String label, String pkg, Intent launch) { this.label = label; this.pkg = pkg; this.launch = launch; }
    }

    public AppDrawer(Context c, Host host) {
        super(c);
        this.host = host;
        density = getResources().getDisplayMetrics().density;
        try { outfit = getResources().getFont(R.font.inter); } catch (Exception ignored) { }
        setVisibility(GONE);

        scrim = new View(c);
        scrim.setBackgroundColor(0x99000000);
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        // Glass sheet; its right corners run off screen so only the inner edge is rounded.
        panel.setBackground(Glass.sheet(dp(30), density));
        panel.setPadding(dp(30), dp(44), dp(30) + dp(32), 0);
        LayoutParams pp = new LayoutParams(dp(440) + dp(32), LayoutParams.MATCH_PARENT, Gravity.END);
        pp.rightMargin = -dp(32);
        addView(panel, pp);

        TextView title = text(32, 700, 0xFFFFFFFF);
        title.setLetterSpacing(-0.02f);
        title.setText(R.string.all_apps);
        panel.addView(title);
        count = text(12, 500, 0x8CFFFFFF);
        count.setAllCaps(true);
        count.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.bottomMargin = dp(18);
        cp.setMarginStart(dp(2));
        panel.addView(count, cp);

        list = new RecyclerView(c);
        list.setLayoutManager(new LinearLayoutManager(c));
        list.setAdapter(adapter);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(40));
        list.setOverScrollMode(OVER_SCROLL_NEVER);
        list.setItemAnimator(null);
        panel.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    public boolean isOpen() { return open; }

    public void show() {
        if (open) return;
        open = true;
        returnFocus = getRootView().findFocus();
        adapter.apps = loadApps();
        adapter.notifyDataSetChanged();
        count.setText(getResources().getQuantityString(R.plurals.app_count, adapter.apps.size(), adapter.apps.size()));
        setVisibility(VISIBLE);
        list.scrollToPosition(0);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(DURATION).start();
        panel.setTranslationX(dp(440));
        panel.animate().translationX(0f).setDuration(DURATION).setInterpolator(new DecelerateInterpolator(2f)).start();
        list.post(() -> {
            RecyclerView.ViewHolder h = list.findViewHolderForAdapterPosition(0);
            if (h != null) h.itemView.requestFocus();
        });
    }

    public void hide() {
        if (!open) return;
        open = false;
        scrim.animate().alpha(0f).setDuration(DURATION).start();
        panel.animate().translationX(dp(440)).setDuration(DURATION).setInterpolator(new DecelerateInterpolator(2f))
                .withEndAction(() -> setVisibility(GONE)).start();
        if (returnFocus != null && returnFocus.isAttachedToWindow()) returnFocus.requestFocus();
    }

    /** Closes at once, without animation (e.g. when an app starts over the home screen). */
    public void dismissNow() {
        if (!open) return;
        open = false;
        panel.animate().cancel();
        scrim.animate().cancel();
        setVisibility(GONE);
        if (returnFocus != null && returnFocus.isAttachedToWindow()) returnFocus.requestFocus();
    }

    private List<App> loadApps() {
        PackageManager pm = getContext().getPackageManager();
        List<App> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String self = getContext().getPackageName();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(self) || seen.contains(pkg)) continue;
                // Phone-style entries from the firmware are factory tools (disclaimers, test menus);
                // only list those the owner installed.
                boolean system = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (cat.equals(Intent.CATEGORY_LAUNCHER) && system) continue;
                seen.add(pkg);
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                App a = new App(String.valueOf(ri.loadLabel(pm)).trim(), pkg, i);
                a.icon = ri.loadIcon(pm);
                out.add(a);
            }
        }
        Collator col = Collator.getInstance();
        col.setStrength(Collator.PRIMARY);
        out.sort((x, y) -> col.compare(x.label, y.label));
        return out;
    }

    private void launch(App a) {
        try {
            getContext().startActivity(a.launch);
        } catch (Exception e) {
            Toast.makeText(getContext(), getContext().getString(R.string.cant_open, a.label), Toast.LENGTH_SHORT).show();
        }
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

    // ---- list ------------------------------------------------------------------------------------

    private final class Adapter extends RecyclerView.Adapter<Adapter.Row> {
        List<App> apps = new ArrayList<>();

        final class Row extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;

            Row(LinearLayout v) {
                super(v);
                icon = (ImageView) v.getChildAt(0);
                label = (TextView) v.getChildAt(1);
            }
        }

        @NonNull
        @Override
        public Row onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.input_row_bg);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding(dp(12), 0, dp(12), 0);
            RecyclerView.LayoutParams rp = new RecyclerView.LayoutParams(-1, dp(54));
            rp.bottomMargin = dp(4);
            row.setLayoutParams(rp);

            ImageView icon = new ImageView(parent.getContext());
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(8));
                }
            });
            icon.setClipToOutline(true);
            row.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));

            TextView label = text(17, 500, 0xFFFFFFFF);
            label.setTextColor(getResources().getColorStateList(R.drawable.pill_text, null));
            label.setDuplicateParentStateEnabled(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMarginStart(dp(16));
            row.addView(label, lp);

            Row h = new Row(row);
            row.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.03f : 1f).scaleY(f ? 1.03f : 1f)
                    .translationZ(f ? dp(10) : 0).setDuration(f ? 260 : 160)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(f ? 1.4f : 0f)).start());
            row.setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) launch(apps.get(pos));
            });
            row.setOnLongClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return false;
                host.onDrawerLongPress(apps.get(pos).pkg, apps.get(pos).label, v);
                return true;
            });
            row.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int pos = h.getBindingAdapterPosition();
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_LEFT: hide(); return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT: return true;
                    case KeyEvent.KEYCODE_DPAD_UP: return pos == 0;
                    case KeyEvent.KEYCODE_DPAD_DOWN: return pos == apps.size() - 1;
                    default: return false;
                }
            });
            return h;
        }

        @Override
        public void onBindViewHolder(@NonNull Row h, int position) {
            App a = apps.get(position);
            h.icon.setImageDrawable(a.icon);
            h.label.setText(a.label);
        }

        @Override
        public int getItemCount() { return apps.size(); }
    }
}
