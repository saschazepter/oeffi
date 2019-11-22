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

import java.io.File;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.util.Downloader;
import de.schildbach.pte.NetworkId;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import androidx.annotation.Nullable;
import okhttp3.HttpUrl;

public final class NetworkContentProvider extends ContentProvider {
    public static final Uri CONTENT_URI = Uri.parse("content://de.schildbach.oeffi.networks");

    private static final String DATABASE_TABLE = "stations";

    public static final String KEY_ID = "_id";
    public static final String KEY_LOCAL_ID = "local_id"; // optional!
    public static final String KEY_PLACE = "place"; // optional!
    public static final String KEY_NAME = "name";
    public static final String KEY_LAT = "lat";
    public static final String KEY_LON = "lon";
    public static final String KEY_PRODUCTS = "products"; // optional!
    public static final String KEY_LINES = "lines";

    public static final String QUERY_PARAM_Q = "q";

    private Application application;
    private Downloader downloader;
    private final List<SQLiteDatabase> databasesToClose = new LinkedList<>();
    private static final int NUM_DATABASES_TO_KEEP = 4;

    private static Pattern PATTERN_Q_ID = Pattern.compile("\\d+");

    public static String dbName(final NetworkId networkId) {
        return networkId.name().toLowerCase(Locale.ENGLISH) + ".db";
    }

    private static HttpUrl downloadUrl(final NetworkId networkId) {
        return Constants.STATIONS_BASE_URL.newBuilder()
                .addPathSegment(networkId.name().toLowerCase(Locale.ENGLISH) + ".db.bz2").build();
    }

    private static final Logger log = LoggerFactory.getLogger(NetworkContentProvider.class);

    @Override
    public boolean onCreate() {
        this.application = (Application) getContext();
        downloader = new Downloader(application.getCacheDir());
        return true;
    }

    @Override
    public synchronized void shutdown() {
        for (final Iterator<SQLiteDatabase> i = databasesToClose.iterator(); i.hasNext();) {
            i.next().close();
            i.remove();
        }
    }

    @Override
    public synchronized Cursor query(final Uri uri, final String[] projection, final String _selection,
            final String[] _selectionArgs, final String sortOrder) {
        if (databasesToClose.size() >= NUM_DATABASES_TO_KEEP)
            databasesToClose.remove(0).close();

        final NetworkId networkId = NetworkId.valueOf(uri.getPathSegments().get(0));
        final File dbFile = new File(getContext().getFilesDir(), dbName(networkId));
        final HttpUrl remoteUrl = downloadUrl(networkId);
        final ListenableFuture<Integer> download = downloader.download(application.okHttpClient(), remoteUrl, dbFile, true, null);
        Futures.addCallback(download, new FutureCallback<Integer>() {
            public void onSuccess(final @Nullable Integer status) {
                if (status == HttpURLConnection.HTTP_OK)
                    getContext().getContentResolver().notifyChange(uri, null);
            }

            public void onFailure(final Throwable t) {
            }
        }, MoreExecutors.directExecutor());

        if (!dbFile.exists())
            return null;

        final String lat = uri.getQueryParameter("lat");
        final String lon = uri.getQueryParameter("lon");
        final String ids = uri.getQueryParameter("ids");
        final String place = uri.getQueryParameter(KEY_PLACE);
        final String name = uri.getQueryParameter(KEY_NAME);
        final String q = uri.getQueryParameter(QUERY_PARAM_Q);
        final String devLat = "100000"; // 0.1 degrees
        final String devLon = "200000"; // 0.2 degrees

        final SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);

        // test if database contains optional columns
        final Cursor testCursor = db.query(DATABASE_TABLE, null, null, null, null, null, null, "0");
        final boolean hasLocalId = testCursor.getColumnIndex(NetworkContentProvider.KEY_LOCAL_ID) != -1;
        final boolean hasPlace = testCursor.getColumnIndex(NetworkContentProvider.KEY_PLACE) != -1;
        final boolean hasProducts = testCursor.getColumnIndex(NetworkContentProvider.KEY_PRODUCTS) != -1;
        testCursor.close();

        String selection = null;
        List<String> selectionArgs = new ArrayList<>();

        if (lat != null || lon != null) {
            selection = "(" + KEY_LAT + ">?-" + devLat + " AND " + KEY_LAT + "<?+" + devLat + " AND " //
                    + KEY_LON + ">?-" + devLon + " AND " + KEY_LON + "<?+" + devLon + ")";
            selectionArgs.addAll(Arrays.asList(lat, lat, lon, lon));
        }

        if (place != null && hasPlace) {
            selection = (selection != null ? selection + " AND " : "") + KEY_PLACE
                    + (!place.isEmpty() ? "=?" : " IS NULL");
            if (!place.isEmpty())
                selectionArgs.add(place);
        }

        if (name != null) {
            selection = (selection != null ? selection + " AND " : "") + KEY_NAME
                    + (!name.isEmpty() ? "=?" : " IS NULL");
            if (!name.isEmpty())
                selectionArgs.add(name);
        }

        if (ids != null && ids.length() > 0 && selection != null) {
            final StringBuilder escapedIds = new StringBuilder();
            for (final String id : ids.split(",")) {
                DatabaseUtils.appendEscapedSQLString(escapedIds, id);
                escapedIds.append(',');
            }
            if (escapedIds.length() > 0)
                escapedIds.setLength(escapedIds.length() - 1);

            selection = "(" + selection + ") OR " + (hasLocalId ? KEY_LOCAL_ID : KEY_ID) + " IN (" + escapedIds + ")";
        }

        if (q != null) {
            final boolean maybeId = PATTERN_Q_ID.matcher(q).matches();

            selection = (selection != null ? "(" + selection + ") AND " : "") + "("
                    + (maybeId ? KEY_ID + " = ? OR " : "") + (hasPlace ? KEY_PLACE + " LIKE ? OR " : "") + KEY_NAME
                    + " LIKE ?)";
            if (maybeId)
                selectionArgs.add(q);
            if (hasPlace)
                selectionArgs.add("%" + q + "%");
            selectionArgs.add("%" + q + "%");
        }

        if (_selection != null) {
            selection = (selection != null ? "(" + selection + ") AND " : "") //
                    + "(" + _selection + ")";
            if (_selectionArgs != null)
                selectionArgs.addAll(Arrays.asList(_selectionArgs));
        }

        final Cursor result = db.query(DATABASE_TABLE, projection, selection, selectionArgs.toArray(new String[0]),
                null, null, sortOrder);
        result.setNotificationUri(getContext().getContentResolver(), uri);

        databasesToClose.add(db);
        return result;
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
