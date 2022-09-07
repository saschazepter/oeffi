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

package de.schildbach.oeffi.stations;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class NearestFavoriteStationsWidgetPermissionActivity extends ComponentActivity {
    private static final Logger log = LoggerFactory.getLogger(NearestFavoriteStationsWidgetPermissionActivity.class);

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                for (Map.Entry<String, Boolean> entry : results.entrySet())
                    log.info("{} {}", entry.getKey(), entry.getValue() ? "granted" : "denied");
                NearestFavoriteStationWidgetService.scheduleImmediate(this); // refresh app-widget
                finish();
            });

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final List<String> permissions = new LinkedList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || permissions.isEmpty())
                permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        log.info("Requesting permissions: {}", permissions);
        requestPermissionsLauncher.launch(permissions.toArray(new String[0]));
    }
}
