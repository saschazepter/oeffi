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

package de.schildbach.oeffi;

import de.schildbach.oeffi.network.NetworkPickerActivity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;

public class PreferencesActivity extends PreferenceActivity {
    private static final String KEY_NETWORK_PROVIDER = "network_provider";
    private static final String KEY_LOCATION_SETTINGS = "location_settings";
    private static final String KEY_BATTERY_OPTIMIZATIONS = "battery_optimizations";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getListView().setFitsSystemWindows(true);
        getListView().setClipToPadding(false);

        addPreferencesFromResource(R.xml.preferences);
        findPreference(KEY_BATTERY_OPTIMIZATIONS).setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
    }

    @Override
    public boolean onPreferenceTreeClick(final PreferenceScreen preferenceScreen, final Preference preference) {
        final String key = preference.getKey();

        if (KEY_NETWORK_PROVIDER.equals(key)) {
            startActivity(new Intent(this, NetworkPickerActivity.class));
            return true;
        } else if (KEY_LOCATION_SETTINGS.equals(key)) {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return true;
        } else if (KEY_BATTERY_OPTIMIZATIONS.equals(key)) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            return true;
        }

        return false;
    }
}
