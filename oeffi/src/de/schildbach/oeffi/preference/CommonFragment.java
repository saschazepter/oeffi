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

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import androidx.annotation.Nullable;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.network.NetworkPickerActivity;

public class CommonFragment extends PreferenceFragment {
    private static final String KEY_BATTERY_OPTIMIZATIONS = "battery_optimizations";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference_common);
        findPreference(KEY_BATTERY_OPTIMIZATIONS).setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
    }
}
