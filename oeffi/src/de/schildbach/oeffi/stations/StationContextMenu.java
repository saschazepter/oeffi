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

import java.net.URLEncoder;
import java.util.Locale;

import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.directions.DirectionsShortcutActivity;
import de.schildbach.oeffi.plans.PlanActivity;
import de.schildbach.oeffi.plans.PlanContentProvider;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.content.pm.ShortcutInfoCompat;
import android.support.v4.content.pm.ShortcutManagerCompat;
import android.support.v4.graphics.drawable.IconCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;

public class StationContextMenu extends PopupMenu {
    public StationContextMenu(final Context context, final View anchor, final NetworkId network, final Location station,
            final Integer favState, final boolean showFavorite, final boolean showIgnore, final boolean showMap,
            final boolean showDirections, final boolean showShortcut) {
        super(context, anchor);
        inflate(R.menu.stations_station_context);
        final Menu menu = getMenu();
        final boolean isFavorite = favState != null && favState == FavoriteStationsProvider.TYPE_FAVORITE;
        final boolean isIgnored = favState != null && favState == FavoriteStationsProvider.TYPE_IGNORE;
        menu.findItem(R.id.station_context_add_favorite).setVisible(showFavorite && !isFavorite);
        menu.findItem(R.id.station_context_remove_favorite).setVisible(showFavorite && isFavorite);
        menu.findItem(R.id.station_context_add_ignore).setVisible(showIgnore && !isIgnored);
        menu.findItem(R.id.station_context_remove_ignore).setVisible(showIgnore && isIgnored);
        final MenuItem mapItem = menu.findItem(R.id.station_context_map);
        if (showMap && station.hasLocation())
            prepareMapMenu(context, mapItem.getSubMenu(), network, station);
        else
            mapItem.setVisible(false);
        menu.findItem(R.id.station_context_directions_from).setVisible(showDirections);
        menu.findItem(R.id.station_context_directions_to).setVisible(showDirections);
        menu.findItem(R.id.station_context_launcher_shortcut).setVisible(showShortcut);
    }

    public static AlertDialog createLauncherShortcutDialog(final Context context, final NetworkId networkId,
            final Location location) {
        final View view = LayoutInflater.from(context).inflate(R.layout.create_launcher_shortcut_dialog, null);

        final DialogBuilder builder = DialogBuilder.get(context);
        builder.setTitle(R.string.station_context_launcher_shortcut_title);
        builder.setView(view);
        builder.setPositiveButton(R.string.create_launcher_shortcut_dialog_button_ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        final EditText nameView = (EditText) view
                                .findViewById(R.id.create_launcher_shortcut_dialog_name);
                        final String shortcutName = nameView.getText().toString();
                        final String shortcutId = "directions-to-" + networkId.name() + "-" + location.id;
                        final Intent shortcutIntent = new Intent(Intent.ACTION_MAIN, null, context,
                                DirectionsShortcutActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        shortcutIntent.putExtra(DirectionsShortcutActivity.INTENT_EXTRA_NETWORK, networkId.name());
                        shortcutIntent.putExtra(DirectionsShortcutActivity.INTENT_EXTRA_TYPE, location.type.name());
                        shortcutIntent.putExtra(DirectionsShortcutActivity.INTENT_EXTRA_NAME, location.name);
                        if (location.type == LocationType.STATION
                                && (networkId != NetworkId.BVG || Integer.parseInt(location.id) >= 1000000))
                            shortcutIntent.putExtra(DirectionsShortcutActivity.INTENT_EXTRA_ID, location.id);
                        if (location.hasLocation()) {
                            shortcutIntent.putExtra(DirectionsShortcutActivity.INTENT_EXTRA_LAT, location.lat);
                            shortcutIntent.putExtra(DirectionsShortcutActivity.INTENT_EXTRA_LON, location.lon);
                        }

                        ShortcutManagerCompat.requestPinShortcut(context,
                                new ShortcutInfoCompat.Builder(context, shortcutId)
                                        .setActivity(new ComponentName(context, DirectionsActivity.class))
                                        .setShortLabel(shortcutName.length() > 0 ? shortcutName
                                                : context.getString(R.string.directions_shortcut_default_name))
                                        .setIcon(IconCompat.createWithResource(context,
                                                R.mipmap.ic_oeffi_directions_color_48dp))
                                        .setIntent(shortcutIntent).build(),
                                null);
                    }
                });
        builder.setNegativeButton(R.string.button_cancel, null);
        return builder.create();
    }

    public static void prepareMapMenu(final Context context, final Menu menu, final NetworkId network,
            final Location location) {
        final PackageManager pm = context.getPackageManager();

        new MenuInflater(context).inflate(R.menu.station_map_context, menu);

        final double lat = location.getLatAsDouble();
        final double lon = location.getLonAsDouble();
        final String name = location.name;

        final MenuItem googleMapsItem = menu.findItem(R.id.station_map_context_google_maps);
        final Intent googleMapsIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(String.format(Locale.ENGLISH, "geo:%.6f,%.6f?q=%.6f,%.6f%s", lat, lon, lat, lon,
                        name != null ? '(' + URLEncoder.encode(name.replaceAll("[()]", "")) + ')' : "")));
        googleMapsIntent.setComponent(
                new ComponentName("com.google.android.apps.maps", "com.google.android.maps.MapsActivity"));
        googleMapsItem.setVisible(location.hasLocation() && pm.resolveActivity(googleMapsIntent, 0) != null);
        googleMapsItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(final MenuItem item) {
                context.startActivity(googleMapsIntent);
                return true;
            }
        });

        final MenuItem amazonMapsItem = menu.findItem(R.id.station_map_context_amazon_maps);
        final Intent amazonMapsIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(String.format(Locale.ENGLISH, "geo:%.6f,%.6f?q=%.6f,%.6f%s", lat, lon, lat, lon,
                        name != null ? '(' + URLEncoder.encode(name.replaceAll("[()]", "")) + ')' : "")));
        amazonMapsIntent.setComponent(
                new ComponentName("com.amazon.geo.client.maps", "com.amazon.geo.client.renderer.MapsAppActivityDuke"));
        amazonMapsItem.setVisible(location.hasLocation() && pm.resolveActivity(amazonMapsIntent, 0) != null);
        amazonMapsItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(final MenuItem item) {
                context.startActivity(amazonMapsIntent);
                return true;
            }
        });

        final MenuItem openStreetMapsItem = menu.findItem(R.id.station_map_context_open_street_maps);
        final Intent openStreetMapsIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(String.format(Locale.ENGLISH, "osmand.geo:%.6f,%.6f?q=%.6f,%.6f%s", lat, lon, lat, lon,
                        name != null ? '(' + URLEncoder.encode(name.replaceAll("[()]", "")) + ')' : "")));
        openStreetMapsItem.setVisible(location.hasLocation() && pm.resolveActivity(openStreetMapsIntent, 0) != null);
        openStreetMapsItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(final MenuItem item) {
                context.startActivity(openStreetMapsIntent);
                return true;
            }
        });

        final MenuItem googleStreetViewItem = menu.findItem(R.id.station_map_context_google_street_view);
        final Intent googleStreetViewIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(String.format(Locale.ENGLISH, "google.streetview:cbll=%.6f,%.6f", lat, lon)));
        googleStreetViewIntent
                .setComponent(new ComponentName("com.google.android.street", "com.google.android.street.Street"));
        googleStreetViewItem
                .setVisible(location.hasLocation() && pm.resolveActivity(googleStreetViewIntent, 0) != null);
        googleStreetViewItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(final MenuItem item) {
                context.startActivity(googleStreetViewIntent);
                return true;
            }
        });

        final MenuItem googleNavigationItem = menu.findItem(R.id.station_map_context_google_navigation);
        final Intent googleNavigationIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(String.format(Locale.ENGLISH, "google.navigation:ll=%.6f,%.6f&title=%s&mode=w", lat, lon,
                        name != null ? URLEncoder.encode(name) : "")));
        googleNavigationIntent.setComponent(new ComponentName("com.google.android.apps.maps",
                "com.google.android.maps.driveabout.app.NavigationActivity"));
        googleNavigationItem
                .setVisible(location.hasLocation() && pm.resolveActivity(googleNavigationIntent, 0) != null);
        googleNavigationItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(final MenuItem item) {
                context.startActivity(googleNavigationIntent);
                return true;
            }
        });

        final ContentResolver contentResolver = context.getContentResolver();
        final Cursor stationsCursor = contentResolver.query(PlanContentProvider.stationsUri(network, location.id), null,
                null, null, null);
        if (stationsCursor != null) {
            final int planIdColumn = stationsCursor.getColumnIndexOrThrow(PlanContentProvider.KEY_STATION_PLAN_ID);
            while (stationsCursor.moveToNext()) {
                final String planId = stationsCursor.getString(planIdColumn);
                final Cursor plansCursor = contentResolver.query(PlanContentProvider.planUri(planId), null, null, null,
                        null);
                plansCursor.moveToFirst();
                final String planName = plansCursor
                        .getString(plansCursor.getColumnIndexOrThrow(PlanContentProvider.KEY_PLAN_NAME));
                plansCursor.close();
                menu.add(planName).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(final MenuItem item) {
                        PlanActivity.start(context, planId, location.id);
                        return true;
                    }
                });
            }
            stationsCursor.close();
        }
    }
}
