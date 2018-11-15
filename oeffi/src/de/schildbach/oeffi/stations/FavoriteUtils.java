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

import java.util.HashMap;
import java.util.Map;

import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

public class FavoriteUtils {
    public static Uri persist(final ContentResolver contentResolver, final int type, final NetworkId networkId,
            final Location station) {
        final ContentValues values = new ContentValues();
        values.put(FavoriteStationsProvider.KEY_TYPE, type);
        values.put(FavoriteStationsProvider.KEY_STATION_NETWORK, networkId.name());
        values.put(FavoriteStationsProvider.KEY_STATION_ID, station.id);
        values.put(FavoriteStationsProvider.KEY_STATION_PLACE, station.place);
        values.put(FavoriteStationsProvider.KEY_STATION_NAME, station.name);
        values.put(FavoriteStationsProvider.KEY_STATION_LAT, station.getLatAs1E6());
        values.put(FavoriteStationsProvider.KEY_STATION_LON, station.getLonAs1E6());

        final Uri rowUri = contentResolver.insert(FavoriteStationsProvider.CONTENT_URI, values);

        return rowUri;
    }

    public static int delete(final ContentResolver contentResolver, final NetworkId networkId, final String stationId) {
        final int numRows = contentResolver
                .delete(FavoriteStationsProvider.CONTENT_URI,
                        FavoriteStationsProvider.KEY_STATION_NETWORK + "=? AND "
                                + FavoriteStationsProvider.KEY_STATION_ID + "=?",
                        new String[] { networkId.name(), stationId });

        return numRows;
    }

    public static Map<Location, Integer> loadAll(final ContentResolver contentResolver, final NetworkId networkId) {
        final Cursor c = contentResolver.query(FavoriteStationsProvider.CONTENT_URI, null,
                FavoriteStationsProvider.KEY_STATION_NETWORK + "=?", new String[] { networkId.name() }, null);
        final int typeIndex = c.getColumnIndexOrThrow(FavoriteStationsProvider.KEY_TYPE);

        final Map<Location, Integer> favorites = new HashMap<>(c.getCount());
        while (c.moveToNext())
            favorites.put(FavoriteStationsProvider.getLocation(c), c.getInt(typeIndex));

        c.close();
        return favorites;
    }

    public static void notifyFavoritesChanged(final Context context) {
        // notify widgets
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        for (final AppWidgetProviderInfo providerInfo : appWidgetManager.getInstalledProviders()) {
            // limit to own widgets
            if (providerInfo.provider.getPackageName().equals(context.getPackageName())) {
                final Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,
                        appWidgetManager.getAppWidgetIds(providerInfo.provider));
                context.sendBroadcast(intent);
            }
        }
    }
}
