package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The owner's arrangement of the home screen, kept on the TV: tile order by package (the first
 * row is the dock), apps removed from Home, and apps added from All Apps that config.xml hides or
 * that have no TV entry point. Empty until the owner first moves, adds or removes something; until
 * then config.xml's dock_apps and alphabetical order apply. Apps installed later are not in the
 * order yet and go to the end.
 */
final class HomeLayout {

    private static final String PREFS = "home_layout", ORDER = "order", REMOVED = "removed", ADDED = "added";

    private final SharedPreferences prefs;

    HomeLayout(Context c) {
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<String> order() {
        String s = prefs.getString(ORDER, "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    void saveOrder(List<String> pkgs) {
        prefs.edit().putString(ORDER, String.join(",", pkgs)).apply();
    }

    boolean isRemoved(String pkg) { return set(REMOVED).contains(pkg); }

    boolean isAdded(String pkg) { return set(ADDED).contains(pkg); }

    Set<String> added() { return set(ADDED); }

    /** Back on Home, at the end of the current order. */
    void add(String pkg, List<String> currentOrder) {
        Set<String> removed = set(REMOVED), added = set(ADDED);
        removed.remove(pkg);
        added.add(pkg);
        List<String> order = new ArrayList<>(currentOrder);
        order.remove(pkg);
        order.add(pkg);
        prefs.edit().putStringSet(REMOVED, removed).putStringSet(ADDED, added)
                .putString(ORDER, String.join(",", order)).apply();
    }

    /** Off Home (still in All Apps); the others keep their places. */
    void remove(String pkg, List<String> currentOrder) {
        Set<String> removed = set(REMOVED), added = set(ADDED);
        removed.add(pkg);
        added.remove(pkg);
        List<String> order = new ArrayList<>(currentOrder);
        order.remove(pkg);
        prefs.edit().putStringSet(REMOVED, removed).putStringSet(ADDED, added)
                .putString(ORDER, String.join(",", order)).apply();
    }

    /** A copy: SharedPreferences must not see its own set modified. */
    private Set<String> set(String key) {
        return new HashSet<>(prefs.getStringSet(key, new HashSet<>()));
    }
}
