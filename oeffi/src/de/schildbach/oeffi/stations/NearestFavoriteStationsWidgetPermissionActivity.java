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
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class NearestFavoriteStationsWidgetPermissionActivity extends Activity {
    private static final Logger log = LoggerFactory.getLogger(NearestFavoriteStationsWidgetPermissionActivity.class);

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
        ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), 0);
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions,
                                           final int[] grantResults) {
        for (int i = 0; i < permissions.length; i++)
            log.info("{}{} granted",
                    permissions[i], grantResults[i] == PackageManager.PERMISSION_GRANTED ? "" : " " + "not");
        NearestFavoriteStationWidgetService.scheduleImmediate(this); // refresh app-widget
        finish();
    }
}
