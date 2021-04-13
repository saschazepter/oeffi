/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.oeffi.preference;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import androidx.annotation.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import de.schildbach.oeffi.BuildConfig;
import de.schildbach.oeffi.R;

public class CommonFragment extends PreferenceFragment {
    private static final String KEY_BATTERY_OPTIMIZATIONS = "battery_optimizations";

    private enum LAUNCHER_ICONS {
        DIRECTION("show_directions_launcher", ".directions.DirectionsActivity.aliasLauncherIconVisible"),
        PLANS("show_plans_launcher", ".plans.PlansPickerActivity.aliasLauncherIconVisible"),
        STATIONS("show_stations_launcher", ".stations.StationsActivity.aliasLauncherIconVisible");

        private String key;
        private String activityAlias;

        LAUNCHER_ICONS(String key, String activityAlias) {
            this.key = key;
            this.activityAlias = BuildConfig.APPLICATION_ID + activityAlias;
        }

        protected static LAUNCHER_ICONS getItemByKey(String key) {
            for (LAUNCHER_ICONS item: LAUNCHER_ICONS.values()) {
                if (item.key.equals(key)) {
                    return item;
                }
            }
            throw new NoSuchElementException();
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference_common);
        findPreference(KEY_BATTERY_OPTIMIZATIONS).setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);

        for (LAUNCHER_ICONS item: LAUNCHER_ICONS.values()) {
            findPreference(item.key).setOnPreferenceChangeListener(this::onUpdateLauncherItem);
        }
        handleOneIcon();
    }

    private boolean onUpdateLauncherItem(Preference preference, Object showLauncher) {
        LAUNCHER_ICONS item = LAUNCHER_ICONS.getItemByKey(preference.getKey());

        // Update Icon visibility
        PackageManager p = getActivity().getPackageManager();
        int flag = (boolean) showLauncher ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        ComponentName componentName = new ComponentName(this.getActivity(), item.activityAlias);
        p.setComponentEnabledSetting(componentName,flag, PackageManager.DONT_KILL_APP);

        handleOneIcon(preference);
        return true;
    }

    //If only one icon visible, then disable this button because Android needs at least one icon per app
    //Called when Common Settings are opened
    private void handleOneIcon () {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

        List<Preference> checked = new LinkedList<>();
        for (LAUNCHER_ICONS item: LAUNCHER_ICONS.values()) {
            if (prefs.getBoolean(item.key, false)) {
                checked.add(findPreference(item.key));
            }
        }
        if (checked.size() == 1) {
            checked.get(0).setEnabled(false);
        } else {
            for (LAUNCHER_ICONS item: LAUNCHER_ICONS.values()) {
                findPreference(item.key).setEnabled(true);
            }
        }
    }

    //If only one icon visible, then disable this button because Android needs at least one icon per app
    //Called when checkbox changed
    private void handleOneIcon (Preference changedPreference) {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();

        List<Preference> checked = new LinkedList<>();
        for (LAUNCHER_ICONS item: LAUNCHER_ICONS.values()) {
            if (prefs.getBoolean(item.key, false)) {
                checked.add(findPreference(item.key));
            }
        }

        //At this point, changed checkbox still has old value, so assume it is still checked
        if (checked.size() == 2 && checked.contains(changedPreference)) {
            for (Preference p: checked) {
                if (p.equals(changedPreference)) {
                    continue;
                }
                p.setEnabled(false);
            }
        } else {
            for (LAUNCHER_ICONS item: LAUNCHER_ICONS.values()) {
                findPreference(item.key).setEnabled(true);
            }
        }
    }
}
