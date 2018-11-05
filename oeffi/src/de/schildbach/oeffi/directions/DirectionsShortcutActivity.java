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

package de.schildbach.oeffi.directions;

import java.util.Locale;
import java.util.Set;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.oeffi.util.GeocoderThread;
import de.schildbach.oeffi.util.LocationHelper;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.NetworkProvider.Accessibility;
import de.schildbach.pte.NetworkProvider.Optimize;
import de.schildbach.pte.NetworkProvider.WalkSpeed;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.QueryTripsResult;
import de.schildbach.pte.dto.TripOptions;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Criteria;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import okhttp3.HttpUrl;

public class DirectionsShortcutActivity extends OeffiActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback, LocationHelper.Callback {
    public static final String INTENT_EXTRA_NETWORK = "network";
    public static final String INTENT_EXTRA_TYPE = "type";
    public static final String INTENT_EXTRA_NAME = "stationname";
    public static final String INTENT_EXTRA_ID = "stationid";
    public static final String INTENT_EXTRA_LAT = "lat";
    public static final String INTENT_EXTRA_LON = "lon";

    private LocationHelper locationHelper;
    private ProgressDialog progressDialog;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private final Handler handler = new Handler();
    private QueryTripsRunnable queryTripsRunnable;

    private static final int REQUEST_CODE_REQUEST_LOCATION_PERMISSION = 1;

    private static final Logger log = LoggerFactory.getLogger(DirectionsShortcutActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        locationHelper = new LocationHelper((LocationManager) getSystemService(Context.LOCATION_SERVICE), this);

        backgroundThread = new HandlerThread("queryTripsThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            maybeStartLocation();
        else
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    REQUEST_CODE_REQUEST_LOCATION_PERMISSION);
    }

    @Override
    protected void onDestroy() {
        backgroundThread.getLooper().quit();

        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions,
            final int[] grantResults) {
        if (requestCode == REQUEST_CODE_REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                maybeStartLocation();
            else
                errorDialog(R.string.acquire_location_no_permission);
        }
    }

    public void maybeStartLocation() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;
        if (locationHelper.isRunning())
            return;

        final Criteria criteria = new Criteria();
        criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        locationHelper.startLocation(criteria, false, Constants.LOCATION_TIMEOUT_MS);
    }

    public void stopLocation() {
        locationHelper.stop();
    }

    public void onLocationStart(final String provider) {
        progressDialog = ProgressDialog.show(DirectionsShortcutActivity.this, null,
                getString(R.string.acquire_location_start, provider), true, true, new OnCancelListener() {
                    public void onCancel(final DialogInterface dialog) {
                        locationHelper.stop();

                        if (queryTripsRunnable != null)
                            queryTripsRunnable.cancel();

                        finish();
                    }
                });
        progressDialog.setCanceledOnTouchOutside(false);
    }

    public void onLocationStop(final boolean timedOut) {
        if (timedOut) {
            progressDialog.dismiss();

            errorDialog(R.string.acquire_location_timeout);
        }
    }

    public void onLocationFail() {
        errorDialog(R.string.acquire_location_no_provider);
    }

    public void onLocation(final Point here) {
        new GeocoderThread(DirectionsShortcutActivity.this, here, new GeocoderThread.Callback() {
            public void onGeocoderResult(final Address address) {
                final String resolved = extractAddress(address);
                if (resolved != null)
                    query(here, resolved);
                else
                    query(here);
            }

            public void onGeocoderFail(final Exception exception) {
                query(here);
            }

            private String extractAddress(final Address address) {
                final int maxAddressLineIndex = address.getMaxAddressLineIndex();
                if (maxAddressLineIndex < 0)
                    return null;

                final StringBuilder builder = new StringBuilder();
                for (int i = 0; i <= maxAddressLineIndex; i++) {
                    builder.append(address.getAddressLine(i));
                    builder.append(", ");
                }
                builder.setLength(builder.length() - 2);

                return builder.toString();
            }
        });
    }

    private void query(final Point here) {
        final String hereName = String.format(Locale.ENGLISH, "%.6f, %.6f", here.lat / 1E6, here.lon / 1E6);

        query(here, hereName);
    }

    private void query(final Point here, final String hereName) {
        final Location from = new Location(LocationType.ADDRESS, null, here, null, hereName);

        final Intent intent = getIntent();

        final NetworkProvider networkProvider = getNetworkExtra(intent);
        final LocationType type = getLocationTypeExtra(intent);
        final String name = intent.getStringExtra(INTENT_EXTRA_NAME);
        final String id = getLocationIdExtra(intent);
        final int lat = intent.getIntExtra(INTENT_EXTRA_LAT, 0);
        final int lon = intent.getIntExtra(INTENT_EXTRA_LON, 0);
        final Location to = new Location(type, id, lat, lon, null, id != null ? name : name + "!");

        if (networkProvider != null) {
            final Optimize optimize = prefs.contains(Constants.PREFS_KEY_OPTIMIZE_TRIP)
                    ? Optimize.valueOf(prefs.getString(Constants.PREFS_KEY_OPTIMIZE_TRIP, null)) : null;
            final WalkSpeed walkSpeed = WalkSpeed
                    .valueOf(prefs.getString(Constants.PREFS_KEY_WALK_SPEED, WalkSpeed.NORMAL.name()));
            final Accessibility accessibility = Accessibility
                    .valueOf(prefs.getString(Constants.PREFS_KEY_ACCESSIBILITY, Accessibility.NEUTRAL.name()));
            final Set<Product> products = networkProvider.defaultProducts();
            final TripOptions options = new TripOptions(products, optimize, walkSpeed, accessibility, null);
            query(networkProvider, from, to, options);
        } else {
            errorDialog(R.string.directions_shortcut_error_message_network);
        }
    }

    private void query(final NetworkProvider networkProvider, final Location from, final Location to,
            final TripOptions options) {
        queryTripsRunnable = new QueryTripsRunnable(getResources(), progressDialog, handler, networkProvider, from,
                null, to, new TimeSpec.Relative(0), options) {
            @Override
            protected void onPostExecute() {
                progressDialog.dismiss();
            }

            @Override
            protected void onResult(final QueryTripsResult result) {
                if (result.status == QueryTripsResult.Status.OK) {
                    log.debug("Got {}", result.toShortString());

                    final Uri historyUri;
                    if (result.from != null && result.from.name != null && result.to != null && result.to.name != null)
                        historyUri = QueryHistoryProvider.put(getContentResolver(), networkProvider.id(), result.from,
                                result.to, null, true);
                    else
                        historyUri = null;

                    TripsOverviewActivity.start(DirectionsShortcutActivity.this, networkProvider.id(),
                            TimeSpec.DepArr.DEPART, result, historyUri);
                    finish();
                } else if (result.status == QueryTripsResult.Status.UNKNOWN_FROM) {
                    errorDialog(R.string.directions_message_unknown_from);
                } else if (result.status == QueryTripsResult.Status.UNKNOWN_VIA) {
                    errorDialog(R.string.directions_message_unknown_via);
                } else if (result.status == QueryTripsResult.Status.UNKNOWN_TO) {
                    errorDialog(R.string.directions_message_unknown_to);
                } else if (result.status == QueryTripsResult.Status.UNKNOWN_LOCATION) {
                    errorDialog(R.string.directions_message_unknown_location);
                } else if (result.status == QueryTripsResult.Status.TOO_CLOSE) {
                    errorDialog(R.string.directions_message_too_close);
                } else if (result.status == QueryTripsResult.Status.UNRESOLVABLE_ADDRESS) {
                    errorDialog(R.string.directions_message_unresolvable_address);
                } else if (result.status == QueryTripsResult.Status.NO_TRIPS) {
                    errorDialog(R.string.directions_message_no_trips);
                } else if (result.status == QueryTripsResult.Status.INVALID_DATE) {
                    errorDialog(R.string.directions_message_invalid_date);
                } else if (result.status == QueryTripsResult.Status.SERVICE_DOWN) {
                    throw new RuntimeException("network problem");
                } else if (result.status == QueryTripsResult.Status.AMBIGUOUS) {
                    errorDialog(R.string.directions_message_ambiguous_location);
                }
            }

            @Override
            protected void onRedirect(final HttpUrl url) {
                final DialogBuilder builder = DialogBuilder.warn(DirectionsShortcutActivity.this,
                        R.string.directions_alert_redirect_title);
                builder.setMessage(getString(R.string.directions_alert_redirect_message, url.host()));
                builder.setPositiveButton(R.string.directions_alert_redirect_button_follow,
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())));
                                finish();
                            }
                        });
                builder.setNegativeButton(R.string.directions_alert_redirect_button_dismiss,
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                finish();
                            }
                        });
                builder.setOnCancelListener(new OnCancelListener() {
                    public void onCancel(final DialogInterface dialog) {
                        finish();
                    }
                });
                builder.show();
            }

            @Override
            protected void onBlocked(final HttpUrl url) {
                final DialogBuilder builder = DialogBuilder.warn(DirectionsShortcutActivity.this,
                        R.string.directions_alert_blocked_title);
                builder.setMessage(getString(R.string.directions_alert_blocked_message, url.host()));
                builder.setNeutralButton(R.string.directions_alert_blocked_button_dismiss,
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                finish();
                            }
                        });
                builder.setOnCancelListener(new OnCancelListener() {
                    public void onCancel(final DialogInterface dialog) {
                        finish();
                    }
                });
                builder.show();
            }

            @Override
            protected void onInternalError(final HttpUrl url) {
                final DialogBuilder builder = DialogBuilder.warn(DirectionsShortcutActivity.this,
                        R.string.directions_alert_internal_error_title);
                builder.setMessage(getString(R.string.directions_alert_internal_error_message, url.host()));
                builder.setNeutralButton(R.string.directions_alert_internal_error_button_dismiss,
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                finish();
                            }
                        });
                builder.setOnCancelListener(new OnCancelListener() {
                    public void onCancel(final DialogInterface dialog) {
                        finish();
                    }
                });
                builder.show();
            }

            @Override
            protected void onSSLException(final SSLException x) {
                final DialogBuilder builder = DialogBuilder.warn(DirectionsShortcutActivity.this,
                        R.string.directions_alert_ssl_exception_title);
                builder.setMessage(getString(R.string.directions_alert_ssl_exception_message, x.toString()));
                builder.setNeutralButton(R.string.directions_alert_ssl_exception_button_dismiss,
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                finish();
                            }
                        });
                builder.setOnCancelListener(new OnCancelListener() {
                    public void onCancel(final DialogInterface dialog) {
                        finish();
                    }
                });
                builder.show();
            }
        };

        log.info("Executing: {}", queryTripsRunnable);

        backgroundHandler.post(queryTripsRunnable);
    }

    private void errorDialog(final int resId) {
        final DialogBuilder builder = DialogBuilder.warn(this, R.string.directions_shortcut_error_title);
        builder.setMessage(resId);
        builder.setPositiveButton("Ok", new OnClickListener() {
            public void onClick(final DialogInterface dialog, final int which) {
                finish();
            }
        });
        builder.setOnCancelListener(new OnCancelListener() {
            public void onCancel(final DialogInterface dialog) {
                finish();
            }
        });
        builder.show();
    }

    private NetworkProvider getNetworkExtra(final Intent intent) {
        try {
            final NetworkId network = NetworkId.valueOf(intent.getStringExtra(INTENT_EXTRA_NETWORK));
            return NetworkProviderFactory.provider(network);
        } catch (final IllegalArgumentException x) {
            return null;
        }
    }

    private LocationType getLocationTypeExtra(final Intent intent) {
        final String type = intent.getStringExtra(INTENT_EXTRA_TYPE);
        return type != null ? LocationType.valueOf(type) : LocationType.STATION;
    }

    private String getLocationIdExtra(final Intent intent) {
        final String id = intent.getStringExtra(INTENT_EXTRA_ID);
        if (id != null)
            return id;

        // old shortcuts
        final int idInt = intent.getIntExtra(INTENT_EXTRA_ID, -1);
        if (idInt != -1)
            return Integer.toString(idInt);

        return null;
    }
}
