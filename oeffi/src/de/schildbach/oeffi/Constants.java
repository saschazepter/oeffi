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

import java.util.Locale;

import android.app.AlertDialog;
import android.os.Build;
import android.text.format.DateUtils;
import okhttp3.HttpUrl;

public class Constants {
    public static final HttpUrl OEFFI_BASE_URL = HttpUrl.parse("https://oeffi.schildbach.de/");
    public static final HttpUrl STATIONS_BASE_URL = OEFFI_BASE_URL.newBuilder().addPathSegment("stations").build();
    public static final HttpUrl PLANS_BASE_URL = OEFFI_BASE_URL.newBuilder().addPathSegment("plans").build();
    public static final HttpUrl MESSAGES_BASE_URL = OEFFI_BASE_URL.newBuilder().addPathSegment("messages").build();
    public static final String PLANS_DIR = "plans";
    public static final String PLAN_INDEX_FILENAME = "plans-index.txt";
    public static final String PLAN_STATIONS_FILENAME = "plans-stations.txt";

    public static final String MARKET_APP_URL = "market://details?id=%s";
    public static final String GOOGLE_PLAY_APP_URL = "https://play.google.com/store/apps/details?id=%s";
    public static final String BITCOIN_ADDRESS = "1PW21MuBgPnjfUQriwK7TnRADFotEhdysr";

    public static final String REPORT_EMAIL = "oeffi.app@gmail.com";

    public static final long LOCATION_UPDATE_FREQ_MS = 10 * DateUtils.SECOND_IN_MILLIS;
    public static final int LOCATION_UPDATE_DISTANCE = 3;
    public static final long LOCATION_FOREGROUND_UPDATE_TIMEOUT_MS = 1 * DateUtils.MINUTE_IN_MILLIS;
    public static final long LOCATION_BACKGROUND_UPDATE_TIMEOUT_MS = 5 * DateUtils.MINUTE_IN_MILLIS;
    public static final long STALE_UPDATE_MS = 2 * DateUtils.MINUTE_IN_MILLIS;
    public static final int MAX_NUMBER_OF_STOPS = 150;
    public static final int MAX_HISTORY_ENTRIES = 50;
    public static final float BEARING_ACCURACY_THRESHOLD = 0.5f;
    public static final double MAP_MIN_ZOOM_LEVEL = 3.0;
    public static final double MAP_MAX_ZOOM_LEVEL = 18.0;
    public static final double INITIAL_MAP_ZOOM_LEVEL_NETWORK = 12.0;
    public static final double INITIAL_MAP_ZOOM_LEVEL = 17.0;
    public static final int MAX_TRIES_ON_IO_PROBLEM = 2;

    public static final Locale DEFAULT_LOCALE = Locale.GERMAN;

    public static final String PREFS_KEY_NETWORK_PROVIDER = "network_provider";
    public static final String PREFS_KEY_LAST_NETWORK_PROVIDERS = "last_network_providers";
    public static final String PREFS_KEY_SHOW_CHANGELOG = "show_changelog";
    public static final String PREFS_KEY_PRODUCT_FILTER = "product_filter";
    public static final String PREFS_KEY_OPTIMIZE_TRIP = "optimize_trip";
    public static final String PREFS_KEY_WALK_SPEED = "walk_speed";
    public static final String PREFS_KEY_ACCESSIBILITY = "accessibility";
    public static final String PREFS_KEY_LAST_VERSION = "last_version";
    public static final String PREFS_KEY_SHOW_INFO = "show_hints";
    public static final String PREFS_KEY_LAST_INFO_AT = "last_hint_at";
    
    public static final String PREFS_KEY_SHOW_PLAN_ON_LOCKSCREEN = "show_plan_on_lockscreen";
    public static final String PREFS_KEY_SHOW_TRIP_DETAILS_ON_LOCKSCREEN = "show_trip_details_on_lockscreen";
    public static final String PREFS_KEY_SHOW_STATION_DETAILS_ON_LOCKSCREEN = "show_station_details_on_lockscreen";            

    public static final char CHAR_THIN_SPACE = '\u2009';
    public static final char CHAR_HAIR_SPACE = '\u200a';
    public static final char CHAR_RIGHTWARDS_ARROW = '\u279d';
    public static final char CHAR_LEFT_RIGHT_ARROW = '\u21c4';
    public static final String DESTINATION_ARROW_PREFIX = Character.toString(Constants.CHAR_RIGHTWARDS_ARROW)
            + Constants.CHAR_THIN_SPACE;
    public static final String DESTINATION_ARROW_INVISIBLE_PREFIX = "     ";

    @SuppressWarnings("deprecation")
    public static final int ALERT_DIALOG_THEME = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
            ? AlertDialog.THEME_DEVICE_DEFAULT_LIGHT : android.R.style.Theme_DeviceDefault_Light_Dialog;
}
