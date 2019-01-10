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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLException;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.FromViaToAware;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiMainActivity;
import de.schildbach.oeffi.OeffiMapView;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.directions.TimeSpec.DepArr;
import de.schildbach.oeffi.directions.list.QueryHistoryAdapter;
import de.schildbach.oeffi.directions.list.QueryHistoryClickListener;
import de.schildbach.oeffi.directions.list.QueryHistoryContextMenuItemListener;
import de.schildbach.oeffi.network.NetworkPickerActivity;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.stations.FavoriteStationsActivity;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.oeffi.stations.FavoriteUtils;
import de.schildbach.oeffi.stations.StationContextMenu;
import de.schildbach.oeffi.stations.StationDetailsActivity;
import de.schildbach.oeffi.util.ConnectivityBroadcastReceiver;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.oeffi.util.DividerItemDecoration;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.GeocoderThread;
import de.schildbach.oeffi.util.LocationUriParser;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.ToggleImageButton;
import de.schildbach.oeffi.util.ToggleImageButton.OnCheckedChangeListener;
import de.schildbach.oeffi.util.ZoomControls;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.NetworkProvider.Accessibility;
import de.schildbach.pte.NetworkProvider.Capability;
import de.schildbach.pte.NetworkProvider.Optimize;
import de.schildbach.pte.NetworkProvider.TripFlag;
import de.schildbach.pte.NetworkProvider.WalkSpeed;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.QueryTripsResult;
import de.schildbach.pte.dto.SuggestLocationsResult;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.dto.TripOptions;

import android.Manifest;
import android.animation.LayoutTransition;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Canvas;
import android.location.Address;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.provider.ContactsContract.CommonDataKinds;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.TimePicker;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import okhttp3.HttpUrl;

public class DirectionsActivity extends OeffiMainActivity implements ActivityCompat.OnRequestPermissionsResultCallback,
        QueryHistoryClickListener, QueryHistoryContextMenuItemListener {
    private ConnectivityManager connectivityManager;
    private LocationManager locationManager;

    private ToggleImageButton buttonExpand;
    private LocationView viewFromLocation;
    private LocationView viewViaLocation;
    private LocationView viewToLocation;
    private View viewProducts;
    private List<ToggleImageButton> viewProductToggles = new ArrayList<>(8);
    private CheckBox viewBike;
    private Button viewTimeDepArr;
    private Button viewTime1;
    private Button viewTime2;
    private Button viewGo;
    private RecyclerView viewQueryHistoryList;
    private QueryHistoryAdapter queryHistoryListAdapter;
    private TextView connectivityWarningView;
    private OeffiMapView mapView;

    private TimeSpec time = null;

    private QueryTripsRunnable queryTripsRunnable;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private final Handler handler = new Handler();
    private BroadcastReceiver connectivityReceiver;
    private BroadcastReceiver tickReceiver;

    private static final int DIALOG_CLEAR_HISTORY = 1;

    private static final int REQUEST_CODE_LOCATION_PERMISSION_FROM = 1;
    private static final int REQUEST_CODE_LOCATION_PERMISSION_VIA = 2;
    private static final int REQUEST_CODE_LOCATION_PERMISSION_TO = 3;
    private static final int REQUEST_CODE_PICK_CONTACT_FROM = 4;
    private static final int REQUEST_CODE_PICK_CONTACT_VIA = 5;
    private static final int REQUEST_CODE_PICK_CONTACT_TO = 6;
    private static final int REQUEST_CODE_PICK_STATION_FROM = 7;
    private static final int REQUEST_CODE_PICK_STATION_VIA = 8;
    private static final int REQUEST_CODE_PICK_STATION_TO = 9;

    private static final Logger log = LoggerFactory.getLogger(DirectionsActivity.class);

    private static final String INTENT_EXTRA_FROM_LOCATION = DirectionsActivity.class.getName() + ".from_location";
    private static final String INTENT_EXTRA_TO_LOCATION = DirectionsActivity.class.getName() + ".to_location";
    private static final String INTENT_EXTRA_TIME_SPEC = DirectionsActivity.class.getName() + ".time_spec";

    public static void start(final Context context, @Nullable final Location fromLocation,
            @Nullable final Location toLocation, @Nullable final TimeSpec timeSpec, final int intentFlags) {
        final Intent intent = new Intent(context, DirectionsActivity.class).addFlags(intentFlags);
        if (fromLocation != null)
            intent.putExtra(DirectionsActivity.INTENT_EXTRA_FROM_LOCATION, fromLocation);
        if (toLocation != null)
            intent.putExtra(DirectionsActivity.INTENT_EXTRA_TO_LOCATION, toLocation);
        if (timeSpec != null)
            intent.putExtra(DirectionsActivity.INTENT_EXTRA_TIME_SPEC, timeSpec);
        context.startActivity(intent);
    }

    private class LocationContextMenuItemClickListener implements PopupMenu.OnMenuItemClickListener {
        private final LocationView locationView;
        private final int locationPermissionRequestCode;
        private final int pickContactRequestCode;
        private final int pickStationRequestCode;

        public LocationContextMenuItemClickListener(final LocationView locationView,
                final int locationPermissionRequestCode, final int pickContactRequestCode,
                final int pickStationRequestCode) {
            this.locationView = locationView;
            this.locationPermissionRequestCode = locationPermissionRequestCode;
            this.pickContactRequestCode = pickContactRequestCode;
            this.pickStationRequestCode = pickStationRequestCode;
        }

        public boolean onMenuItemClick(final MenuItem item) {
            if (item.getItemId() == R.id.directions_location_current_location) {
                if (ContextCompat.checkSelfPermission(DirectionsActivity.this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    locationView.acquireLocation();
                else
                    ActivityCompat.requestPermissions(DirectionsActivity.this,
                            new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, locationPermissionRequestCode);
                return true;
            } else if (item.getItemId() == R.id.directions_location_contact) {
                startActivityForResult(new Intent(Intent.ACTION_PICK, CommonDataKinds.StructuredPostal.CONTENT_URI),
                        pickContactRequestCode);
                return true;
            } else if (item.getItemId() == R.id.directions_location_favorite_station) {
                if (network != null)
                    FavoriteStationsActivity.startForResult(DirectionsActivity.this, pickStationRequestCode, network);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    protected String taskName() {
        return "directions";
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        } else {
            time = new TimeSpec.Relative(0);
        }

        backgroundThread = new HandlerThread("queryTripsThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        setContentView(R.layout.directions_content);
        final MyActionBar actionBar = getMyActionBar();
        setPrimaryColor(R.color.action_bar_background_directions);
        actionBar.setPrimaryTitle(R.string.directions_activity_title);
        actionBar.setTitlesOnClickListener(new OnClickListener() {
            public void onClick(final View v) {
                NetworkPickerActivity.start(DirectionsActivity.this);
            }
        });
        buttonExpand = actionBar.addToggleButton(R.drawable.ic_expand_white_24dp,
                R.string.directions_action_expand_title);
        buttonExpand.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(final ToggleImageButton buttonView, final boolean isChecked) {
                if (isChecked)
                    expandForm();
                else
                    collapseForm();

                updateMap();
            }
        });
        actionBar.addButton(R.drawable.ic_shuffle_white_24dp, R.string.directions_action_return_trip_title)
                .setOnClickListener(new OnClickListener() {
                    public void onClick(final View v) {
                        viewToLocation.exchangeWith(viewFromLocation);
                    }
                });
        actionBar.overflow(R.menu.directions_options, new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(final MenuItem item) {
                if (item.getItemId() == R.id.directions_options_clear_history) {
                    if (network != null)
                        showDialog(DIALOG_CLEAR_HISTORY);
                    return true;
                } else {
                    return false;
                }
            }
        });

        initNavigation();

        ((Button) findViewById(R.id.directions_network_missing_capability_button))
                .setOnClickListener(new OnClickListener() {
                    public void onClick(final View v) {
                        NetworkPickerActivity.start(DirectionsActivity.this);
                    }
                });
        connectivityWarningView = (TextView) findViewById(R.id.directions_connectivity_warning_box);

        initLayoutTransitions();

        final AutoCompleteLocationAdapter autoCompleteAdapter = new AutoCompleteLocationAdapter();

        final LocationView.Listener locationChangeListener = new LocationView.Listener() {
            public void changed() {
                updateMap();
                queryHistoryListAdapter.clearSelectedEntry();
                requestFocusFirst();
            }
        };

        viewFromLocation = (LocationView) findViewById(R.id.directions_from);
        viewFromLocation.setAdapter(autoCompleteAdapter);
        viewFromLocation.setListener(locationChangeListener);
        viewFromLocation.setContextMenuItemClickListener(new LocationContextMenuItemClickListener(viewFromLocation,
                REQUEST_CODE_LOCATION_PERMISSION_FROM, REQUEST_CODE_PICK_CONTACT_FROM, REQUEST_CODE_PICK_STATION_FROM));

        viewViaLocation = (LocationView) findViewById(R.id.directions_via);
        viewViaLocation.setAdapter(autoCompleteAdapter);
        viewViaLocation.setListener(locationChangeListener);
        viewViaLocation.setContextMenuItemClickListener(new LocationContextMenuItemClickListener(viewViaLocation,
                REQUEST_CODE_LOCATION_PERMISSION_VIA, REQUEST_CODE_PICK_CONTACT_VIA, REQUEST_CODE_PICK_STATION_VIA));

        viewToLocation = (LocationView) findViewById(R.id.directions_to);
        viewToLocation.setAdapter(autoCompleteAdapter);
        viewToLocation.setListener(locationChangeListener);
        viewToLocation.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    viewGo.performClick();
                    return true;
                } else if (actionId == EditorInfo.IME_ACTION_DONE) {
                    requestFocusFirst();
                    return true;
                } else {
                    return false;
                }
            }
        });
        viewToLocation.setContextMenuItemClickListener(new LocationContextMenuItemClickListener(viewToLocation,
                REQUEST_CODE_LOCATION_PERMISSION_TO, REQUEST_CODE_PICK_CONTACT_TO, REQUEST_CODE_PICK_STATION_TO));

        viewProducts = findViewById(R.id.directions_products);
        viewProductToggles.add((ToggleImageButton) findViewById(R.id.directions_products_i));
        viewProductToggles.add((ToggleImageButton) findViewById(R.id.directions_products_r));
        viewProductToggles.add((ToggleImageButton) findViewById(R.id.directions_products_s));
        viewProductToggles.add((ToggleImageButton) findViewById(R.id.directions_products_u));
        viewProductToggles.add((ToggleImageButton) findViewById(R.id.directions_products_t));
        viewProductToggles.add((ToggleImageButton) findViewById(R.id.directions_products_b));
        viewProductToggles.add((ToggleImageButton) findViewById(R.id.directions_products_p));
        viewProductToggles.add((ToggleImageButton) findViewById(R.id.directions_products_f));
        viewProductToggles.add((ToggleImageButton) findViewById(R.id.directions_products_c));
        initProductToggles();

        final OnLongClickListener productLongClickListener = new OnLongClickListener() {
            public boolean onLongClick(final View v) {
                final DialogBuilder builder = DialogBuilder.get(DirectionsActivity.this);
                builder.setTitle(R.string.directions_products_prompt);
                builder.setItems(R.array.directions_products, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        for (final ToggleImageButton view : viewProductToggles) {
                            if (which == 0)
                                view.setChecked(view.equals(v));
                            if (which == 1)
                                view.setChecked(!view.equals(v));
                            if (which == 2)
                                view.setChecked(true);
                            if (which == 3)
                                view.setChecked("SUTBP".contains((String) view.getTag()));
                        }
                    }
                });
                builder.show();
                return true;
            }
        };
        for (final View view : viewProductToggles)
            view.setOnLongClickListener(productLongClickListener);

        viewBike = (CheckBox) findViewById(R.id.directions_option_bike);

        viewTimeDepArr = (Button) findViewById(R.id.directions_time_dep_arr);
        viewTimeDepArr.setOnClickListener(new OnClickListener() {
            public void onClick(final View v) {
                final DialogBuilder builder = DialogBuilder.get(DirectionsActivity.this);
                builder.setTitle(R.string.directions_set_time_prompt);
                builder.setItems(R.array.directions_set_time, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        final String[] parts = getResources().getStringArray(R.array.directions_set_time_values)[which]
                                .split("_");
                        final DepArr depArr = DepArr.valueOf(parts[0]);
                        if (parts[1].equals("AT")) {
                            time = new TimeSpec.Absolute(depArr, time.timeInMillis());
                        } else if (parts[1].equals("IN")) {
                            if (parts.length > 2) {
                                time = new TimeSpec.Relative(depArr,
                                        Long.parseLong(parts[2]) * DateUtils.MINUTE_IN_MILLIS);
                            } else {
                                time = new TimeSpec.Relative(depArr, 0);
                                handleDiffClick();
                            }
                        } else {
                            throw new IllegalStateException(parts[1]);
                        }
                        updateGUI();
                    }
                });
                builder.show();
            }
        });

        viewTime1 = (Button) findViewById(R.id.directions_time_1);
        viewTime2 = (Button) findViewById(R.id.directions_time_2);

        viewGo = (Button) findViewById(R.id.directions_go);
        viewGo.setOnClickListener(new OnClickListener() {
            public void onClick(final View v) {
                handleGo();
            }
        });

        viewQueryHistoryList = (RecyclerView) findViewById(android.R.id.list);
        viewQueryHistoryList.setLayoutManager(new LinearLayoutManager(this));
        viewQueryHistoryList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        queryHistoryListAdapter = new QueryHistoryAdapter(this, network, this, this);
        viewQueryHistoryList.setAdapter(queryHistoryListAdapter);

        mapView = (OeffiMapView) findViewById(R.id.directions_map);
        if (ContextCompat.checkSelfPermission(DirectionsActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            android.location.Location location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            if (location != null)
                mapView.animateToLocation(location.getLatitude(), location.getLongitude());
        }
        mapView.getOverlays().add(new Overlay() {
            private Location pinLocation;
            private View pinView;

            @Override
            public void draw(final Canvas canvas, final MapView mapView, final boolean shadow) {
                if (pinView != null)
                    pinView.requestLayout();
            }

            @Override
            public boolean onSingleTapConfirmed(final MotionEvent e, final MapView mapView) {
                final IGeoPoint p = mapView.getProjection().fromPixels((int) e.getX(), (int) e.getY());
                pinLocation = Location.coord(Point.fromDouble(p.getLatitude(), p.getLongitude()));

                final View view = getLayoutInflater().inflate(R.layout.directions_map_pin, null);
                final LocationTextView locationView = (LocationTextView) view
                        .findViewById(R.id.directions_map_pin_location);
                final View buttonGroup = view.findViewById(R.id.directions_map_pin_buttons);
                buttonGroup.findViewById(R.id.directions_map_pin_button_from).setOnClickListener(new OnClickListener() {
                    public void onClick(final View v) {
                        viewFromLocation.setLocation(pinLocation);
                        mapView.removeAllViews();
                    }
                });
                buttonGroup.findViewById(R.id.directions_map_pin_button_to).setOnClickListener(new OnClickListener() {
                    public void onClick(final View v) {
                        viewToLocation.setLocation(pinLocation);
                        mapView.removeAllViews();
                    }
                });
                locationView.setLocation(pinLocation);
                locationView.setShowLocationType(false);

                // exchange view for the pin
                if (pinView != null)
                    mapView.removeView(pinView);
                pinView = view;
                mapView.addView(pinView, new MapView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, p, MapView.LayoutParams.BOTTOM_CENTER, 0, 0));

                new GeocoderThread(DirectionsActivity.this, p.getLatitude(), p.getLongitude(),
                        new GeocoderThread.Callback() {
                            public void onGeocoderResult(final Address address) {
                                pinLocation = GeocoderThread.addressToLocation(address);
                                locationView.setLocation(pinLocation);
                                locationView.setShowLocationType(false);
                            }

                            public void onGeocoderFail(final Exception exception) {
                                log.info("Problem in geocoder: {}", exception.getMessage());
                            }
                        });

                final IMapController controller = mapView.getController();
                controller.animateTo(p);

                return false;
            }
        });
        ((TextView) findViewById(R.id.directions_map_disclaimer))
                .setText(mapView.getTileProvider().getTileSource().getCopyrightNotice());

        final ZoomControls zoom = (ZoomControls) findViewById(R.id.directions_map_zoom);
        mapView.setZoomControls(zoom);

        connectivityReceiver = new ConnectivityBroadcastReceiver(connectivityManager) {
            @Override
            protected void onConnected() {
                connectivityWarningView.setVisibility(View.GONE);
            }

            @Override
            protected void onDisconnected() {
                connectivityWarningView.setVisibility(View.VISIBLE);
            }
        };
        registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        // populate
        final Intent intent = getIntent();
        final Uri intentData = intent.getData();

        if (intentData != null) {
            log.info("Got intent: {}, data={}", intent, intentData);

            final Location[] locations = LocationUriParser.parseLocations(intentData.toString());

            if (locations.length == 1) {
                viewFromLocation.acquireLocation();
                if (locations[0] != null)
                    viewToLocation.setLocation(locations[0]);
            } else {
                if (locations[0] != null)
                    viewFromLocation.setLocation(locations[0]);
                if (locations[1] != null)
                    viewToLocation.setLocation(locations[1]);
            }
        } else {
            if (intent.hasExtra(INTENT_EXTRA_FROM_LOCATION))
                viewFromLocation.setLocation((Location) intent.getSerializableExtra(INTENT_EXTRA_FROM_LOCATION));
            if (intent.hasExtra(INTENT_EXTRA_TO_LOCATION))
                viewToLocation.setLocation((Location) intent.getSerializableExtra(INTENT_EXTRA_TO_LOCATION));
            if (intent.hasExtra(INTENT_EXTRA_TIME_SPEC))
                time = (TimeSpec) intent.getSerializableExtra(INTENT_EXTRA_TIME_SPEC);
        }

        // initial focus
        if (!viewToLocation.isInTouchMode()) {
            requestFocusFirst();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();

        // can do directions?
        final NetworkProvider networkProvider = network != null ? NetworkProviderFactory.provider(network) : null;
        final boolean hasDirectionsCap = networkProvider != null ? networkProvider.hasCapabilities(Capability.TRIPS)
                : false;
        viewFromLocation.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        viewViaLocation.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        viewToLocation.setImeOptions(hasDirectionsCap ? EditorInfo.IME_ACTION_GO : EditorInfo.IME_ACTION_NONE);
        viewGo.setEnabled(hasDirectionsCap);

        viewQueryHistoryList.setVisibility(hasDirectionsCap ? View.VISIBLE : View.GONE);
        findViewById(R.id.directions_network_missing_capability)
                .setVisibility(hasDirectionsCap ? View.GONE : View.VISIBLE);
        findViewById(R.id.directions_query_history_empty).setVisibility(
                hasDirectionsCap && queryHistoryListAdapter.getItemCount() == 0 ? View.VISIBLE : View.INVISIBLE);

        // regular refresh
        tickReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateGUI();
            }
        };
        registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

        setActionBarSecondaryTitleFromNetwork();
        updateGUI();
        updateMap();
        updateFragments();
    }

    @Override
    protected void onChangeNetwork(final NetworkId network) {
        viewFromLocation.reset();
        viewViaLocation.reset();
        viewToLocation.reset();

        viewBike.setChecked(false);

        initProductToggles();

        collapseForm();
        buttonExpand.setChecked(false);

        queryHistoryListAdapter.close();
        queryHistoryListAdapter = new QueryHistoryAdapter(this, network, this, this);
        viewQueryHistoryList.setAdapter(queryHistoryListAdapter);

        updateGUI();
        setActionBarSecondaryTitleFromNetwork();
    }

    private void initProductToggles() {
        final NetworkProvider networkProvider = network != null ? NetworkProviderFactory.provider(network) : null;
        final Collection<Product> defaultProducts = networkProvider != null ? networkProvider.defaultProducts()
                : Product.ALL;
        for (final ToggleImageButton view : viewProductToggles) {
            final Product product = Product.fromCode(((String) view.getTag()).charAt(0));
            final boolean checked = defaultProducts.contains(product);
            view.setChecked(checked);
        }
    }

    @Override
    protected void onPause() {
        if (tickReceiver != null) {
            unregisterReceiver(tickReceiver);
            tickReceiver = null;
        }

        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable("time", time);
    }

    private void restoreInstanceState(final Bundle savedInstanceState) {
        time = (TimeSpec) savedInstanceState.getSerializable("time");
    }

    @Override
    protected void onDestroy() {
        backgroundThread.getLooper().quit();

        queryHistoryListAdapter.close();
        unregisterReceiver(connectivityReceiver);

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(final Configuration config) {
        super.onConfigurationChanged(config);

        updateFragments();
    }

    @Override
    public void onBackPressed() {
        if (isNavigationOpen())
            closeNavigation();
        else
            super.onBackPressed();
    }

    private void requestFocusFirst() {
        if (!saneLocation(viewFromLocation.getLocation(), true))
            viewFromLocation.requestFocus();
        else if (!saneLocation(viewToLocation.getLocation(), true))
            viewToLocation.requestFocus();
        else
            viewGo.requestFocus();
    }

    private void updateFragments() {
        updateFragments(R.id.navigation_drawer_layout, R.id.directions_map_fragment);
    }

    private void updateGUI() {
        viewFromLocation.setHint(R.string.directions_from);
        viewViaLocation.setHint(R.string.directions_via);
        viewToLocation.setHint(R.string.directions_to);

        viewTimeDepArr
                .setText(time.depArr == DepArr.DEPART ? R.string.directions_time_dep : R.string.directions_time_arr);

        if (time == null) {
            viewTime1.setVisibility(View.GONE);
            viewTime2.setVisibility(View.GONE);
        } else if (time instanceof TimeSpec.Absolute) {
            final long now = System.currentTimeMillis();
            final long t = ((TimeSpec.Absolute) time).timeMs;
            viewTime1.setVisibility(View.VISIBLE);
            viewTime1.setOnClickListener(dateClickListener);
            viewTime1.setText(Formats.formatDate(this, now, t));
            viewTime2.setVisibility(View.VISIBLE);
            viewTime2.setOnClickListener(timeClickListener);
            viewTime2.setText(Formats.formatTime(this, t));
        } else if (time instanceof TimeSpec.Relative) {
            final long diff = ((TimeSpec.Relative) time).diffMs;
            viewTime1.setVisibility(View.VISIBLE);
            viewTime1
                    .setText(diff > 0 ? getString(R.string.directions_time_relative, Formats.formatTimeDiff(this, diff))
                            : getString(R.string.time_now));
            viewTime1.setOnClickListener(diffClickListener);
            viewTime2.setVisibility(View.GONE);
        }
    }

    private final OnClickListener dateClickListener = new OnClickListener() {
        public void onClick(final View v) {
            final Calendar calendar = new GregorianCalendar();
            calendar.setTimeInMillis(((TimeSpec.Absolute) time).timeMs);
            final int year = calendar.get(Calendar.YEAR);
            final int month = calendar.get(Calendar.MONTH);
            final int day = calendar.get(Calendar.DAY_OF_MONTH);

            new DatePickerDialog(DirectionsActivity.this, Constants.ALERT_DIALOG_THEME, new OnDateSetListener() {
                public void onDateSet(final DatePicker view, final int year, final int month, final int day) {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, day);
                    time = new TimeSpec.Absolute(time.depArr, calendar.getTimeInMillis());
                    updateGUI();
                }
            }, year, month, day).show();
        }
    };

    private final OnClickListener timeClickListener = new OnClickListener() {
        public void onClick(final View v) {
            final Calendar calendar = new GregorianCalendar();
            calendar.setTimeInMillis(((TimeSpec.Absolute) time).timeMs);
            final int hour = calendar.get(Calendar.HOUR_OF_DAY);
            final int minute = calendar.get(Calendar.MINUTE);

            new TimePickerDialog(DirectionsActivity.this, Constants.ALERT_DIALOG_THEME, new OnTimeSetListener() {
                public void onTimeSet(final TimePicker view, final int hour, final int minute) {
                    calendar.set(Calendar.HOUR_OF_DAY, hour);
                    calendar.set(Calendar.MINUTE, minute);
                    time = new TimeSpec.Absolute(time.depArr, calendar.getTimeInMillis());
                    updateGUI();
                }
            }, hour, minute, DateFormat.is24HourFormat(DirectionsActivity.this)).show();
        }
    };

    private final OnClickListener diffClickListener = new OnClickListener() {
        public void onClick(final View v) {
            handleDiffClick();
        }
    };

    private void handleDiffClick() {
        final int[] relativeTimeValues = getResources().getIntArray(R.array.directions_set_time_relative);
        final String[] relativeTimeStrings = new String[relativeTimeValues.length + 1];
        relativeTimeStrings[relativeTimeValues.length] = getString(R.string.directions_set_time_relative_fixed);
        for (int i = 0; i < relativeTimeValues.length; i++) {
            if (relativeTimeValues[i] == 0)
                relativeTimeStrings[i] = getString(R.string.time_now);
            else
                relativeTimeStrings[i] = getString(R.string.directions_time_relative,
                        Formats.formatTimeDiff(this, relativeTimeValues[i] * DateUtils.MINUTE_IN_MILLIS));
        }
        final DialogBuilder builder = DialogBuilder.get(this);
        builder.setTitle(R.string.directions_set_time_relative_prompt);
        builder.setItems(relativeTimeStrings, new DialogInterface.OnClickListener() {
            public void onClick(final DialogInterface dialog, final int which) {
                if (which < relativeTimeValues.length) {
                    final int mins = relativeTimeValues[which];
                    time = new TimeSpec.Relative(mins * DateUtils.MINUTE_IN_MILLIS);
                } else {
                    time = new TimeSpec.Absolute(DepArr.DEPART, time.timeInMillis());
                }
                updateGUI();
            }
        });
        builder.show();
    }

    private void updateMap() {
        mapView.removeAllViews();
        mapView.setFromViaToAware(new FromViaToAware() {
            public Point getFrom() {
                final Location from = viewFromLocation.getLocation();
                if (from == null || !from.hasCoord())
                    return null;
                return from.coord;
            }

            public Point getVia() {
                final Location via = viewViaLocation.getLocation();
                if (via == null || !via.hasCoord() || viewViaLocation.getVisibility() != View.VISIBLE)
                    return null;
                return via.coord;
            }

            public Point getTo() {
                final Location to = viewToLocation.getLocation();
                if (to == null || !to.hasCoord())
                    return null;
                return to.coord;
            }
        });
        mapView.zoomToAll();
    }

    private void expandForm() {
        initLayoutTransitions(true);

        final boolean hasBikeOption = network != NetworkId.BVG;

        viewViaLocation.setVisibility(View.VISIBLE);
        viewProducts.setVisibility(View.VISIBLE);
        if (hasBikeOption)
            viewBike.setVisibility(View.VISIBLE);
    }

    private void collapseForm() {
        initLayoutTransitions(false);

        viewViaLocation.setVisibility(View.GONE);
        viewProducts.setVisibility(View.GONE);
        viewBike.setVisibility(View.GONE);
    }

    private void initLayoutTransitions() {
        final LayoutTransition lt1 = new LayoutTransition();
        lt1.enableTransitionType(LayoutTransition.CHANGING);
        ((ViewGroup) findViewById(R.id.directions_list_layout)).setLayoutTransition(lt1);

        final LayoutTransition lt2 = new LayoutTransition();
        lt2.enableTransitionType(LayoutTransition.CHANGING);
        ((ViewGroup) findViewById(R.id.directions_content_layout)).setLayoutTransition(lt2);

        final LayoutTransition lt3 = new LayoutTransition();
        lt3.enableTransitionType(LayoutTransition.CHANGING);
        ((ViewGroup) findViewById(R.id.directions_form)).setLayoutTransition(lt3);

        final LayoutTransition lt4 = new LayoutTransition();
        ((ViewGroup) findViewById(R.id.directions_form_location_group)).setLayoutTransition(lt4);
    }

    private void initLayoutTransitions(final boolean expand) {
        ((ViewGroup) findViewById(R.id.directions_list_layout)).getLayoutTransition()
                .setStartDelay(LayoutTransition.CHANGING, expand ? 0 : 300);
        ((ViewGroup) findViewById(R.id.directions_content_layout)).getLayoutTransition()
                .setStartDelay(LayoutTransition.CHANGING, expand ? 0 : 300);
    }

    public void onEntryClick(final int adapterPosition, final Location from, final Location to) {
        handleReuseQuery(from, to);
        queryHistoryListAdapter.setSelectedEntry(queryHistoryListAdapter.getItemId(adapterPosition));
    }

    public void onSavedTripClick(final int adapterPosition, final byte[] serializedSavedTrip) {
        handleShowSavedTrip(serializedSavedTrip);
    }

    public boolean onQueryHistoryContextMenuItemClick(final int adapterPosition, final Location from, final Location to,
            @Nullable final byte[] serializedSavedTrip, final int menuItemId,
            @Nullable final Location menuItemLocation) {
        if (menuItemId == R.id.directions_query_history_context_show_trip) {
            handleShowSavedTrip(serializedSavedTrip);
            return true;
        } else if (menuItemId == R.id.directions_query_history_context_remove_trip) {
            queryHistoryListAdapter.setSavedTrip(adapterPosition, 0, 0, null);
            return true;
        } else if (menuItemId == R.id.directions_query_history_context_remove_entry) {
            queryHistoryListAdapter.removeEntry(adapterPosition);
            return true;
        } else if (menuItemId == R.id.directions_query_history_context_add_favorite) {
            queryHistoryListAdapter.setIsFavorite(adapterPosition, true);
            return true;
        } else if (menuItemId == R.id.directions_query_history_context_remove_favorite) {
            queryHistoryListAdapter.setIsFavorite(adapterPosition, false);
            return true;
        } else if (menuItemId == R.id.directions_query_history_location_context_details && menuItemLocation != null) {
            StationDetailsActivity.start(this, network, menuItemLocation);
            return true;
        } else if (menuItemId == R.id.directions_query_history_location_context_add_favorite
                && menuItemLocation != null) {
            FavoriteUtils.persist(getContentResolver(), FavoriteStationsProvider.TYPE_FAVORITE, network,
                    menuItemLocation);
            new Toast(DirectionsActivity.this).longToast(R.string.toast_add_favorite,
                    menuItemLocation.uniqueShortName());
            queryHistoryListAdapter.notifyDataSetChanged();
            return true;
        } else if (menuItemId == R.id.directions_query_history_location_context_launcher_shortcut
                && menuItemLocation != null) {
            StationContextMenu.createLauncherShortcutDialog(DirectionsActivity.this, network, menuItemLocation).show();
            return true;
        } else {
            return false;
        }
    }

    private void handleReuseQuery(final Location from, final Location to) {
        viewFromLocation.setLocation(from);
        viewToLocation.setLocation(to);
    }

    private void handleShowSavedTrip(final byte[] serializedTrip) {
        final Trip trip = (Trip) deserialize(serializedTrip);
        if (trip != null)
            TripDetailsActivity.start(this, network, trip);
        else
            new Toast(this).longToast(R.string.directions_query_history_invalid_blob);
    }

    private Object deserialize(final byte[] bytes) {
        try {
            final ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(bytes));
            final Object object = is.readObject();
            is.close();

            return object;
        } catch (final InvalidClassException | ClassNotFoundException | StreamCorruptedException x) {
            x.printStackTrace();
            return null;
        } catch (final IOException x) {
            throw new RuntimeException(x);
        }
    }

    private boolean saneLocation(final @Nullable Location location, final boolean allowIncompleteAddress) {
        if (location == null)
            return false;
        if (location.type == LocationType.ANY && location.name == null)
            return false;
        if (!allowIncompleteAddress && location.type == LocationType.ADDRESS && !location.hasCoord()
                && location.name == null)
            return false;

        return true;
    }

    private void handleGo() {
        final Location from = viewFromLocation.getLocation();
        if (!saneLocation(from, false)) {
            new Toast(this).longToast(R.string.directions_message_choose_from);
            viewFromLocation.requestFocus();
            return;
        }

        Location via = null;
        if (viewViaLocation.getVisibility() == View.VISIBLE) {
            via = viewViaLocation.getLocation();
            if (!saneLocation(via, false))
                via = null;
        }

        final Location to = viewToLocation.getLocation();
        if (!saneLocation(to, false)) {
            new Toast(this).longToast(R.string.directions_message_choose_to);
            viewToLocation.requestFocus();
            return;
        }

        final Set<Product> products = new HashSet<>();
        for (final ToggleImageButton view : viewProductToggles)
            if (view.isChecked())
                products.add(Product.fromCode(((String) view.getTag()).charAt(0)));

        final Set<TripFlag> flags;
        if (viewBike.isChecked()) {
            flags = new HashSet<>();
            flags.add(TripFlag.BIKE);
        } else {
            flags = null;
        }

        final ProgressDialog progressDialog = ProgressDialog.show(DirectionsActivity.this, null,
                getString(R.string.directions_query_progress), true, true, new DialogInterface.OnCancelListener() {
                    public void onCancel(final DialogInterface dialog) {
                        if (queryTripsRunnable != null)
                            queryTripsRunnable.cancel();
                    }
                });
        progressDialog.setCanceledOnTouchOutside(false);

        final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
        final TripOptions options = new TripOptions(products, prefsGetOptimizeTrip(), prefsGetWalkSpeed(),
                prefsGetAccessibility(), flags);
        queryTripsRunnable = new QueryTripsRunnable(getResources(), progressDialog, handler, networkProvider, from, via,
                to, time, options) {
            @Override
            protected void onPreExecute() {
                viewGo.setClickable(false);
            }

            @Override
            protected void onPostExecute() {
                viewGo.setClickable(true);
                progressDialog.dismiss();
            }

            @Override
            protected void onResult(final QueryTripsResult result) {
                if (result.status == QueryTripsResult.Status.OK) {
                    log.debug("Got {}", result.toShortString());

                    final Uri historyUri;
                    if (result.from != null && result.from.name != null && result.to != null && result.to.name != null)
                        historyUri = queryHistoryListAdapter.putEntry(result.from, result.to);
                    else
                        historyUri = null;

                    TripsOverviewActivity.start(DirectionsActivity.this, network, time.depArr, result, historyUri);
                } else if (result.status == QueryTripsResult.Status.UNKNOWN_FROM) {
                    new Toast(DirectionsActivity.this).longToast(R.string.directions_message_unknown_from);
                } else if (result.status == QueryTripsResult.Status.UNKNOWN_VIA) {
                    new Toast(DirectionsActivity.this).longToast(R.string.directions_message_unknown_via);
                } else if (result.status == QueryTripsResult.Status.UNKNOWN_TO) {
                    new Toast(DirectionsActivity.this).longToast(R.string.directions_message_unknown_to);
                } else if (result.status == QueryTripsResult.Status.UNKNOWN_LOCATION) {
                    new Toast(DirectionsActivity.this).longToast(R.string.directions_message_unknown_location);
                } else if (result.status == QueryTripsResult.Status.TOO_CLOSE) {
                    new Toast(DirectionsActivity.this).longToast(R.string.directions_message_too_close);
                } else if (result.status == QueryTripsResult.Status.UNRESOLVABLE_ADDRESS) {
                    new Toast(DirectionsActivity.this).longToast(R.string.directions_message_unresolvable_address);
                } else if (result.status == QueryTripsResult.Status.NO_TRIPS) {
                    new Toast(DirectionsActivity.this).longToast(R.string.directions_message_no_trips);
                } else if (result.status == QueryTripsResult.Status.INVALID_DATE) {
                    new Toast(DirectionsActivity.this).longToast(R.string.directions_message_invalid_date);
                } else if (result.status == QueryTripsResult.Status.SERVICE_DOWN) {
                    networkProblem();
                } else if (result.status == QueryTripsResult.Status.AMBIGUOUS) {
                    final List<Location> autocompletes = result.ambiguousFrom != null ? result.ambiguousFrom
                            : (result.ambiguousVia != null ? result.ambiguousVia : result.ambiguousTo);
                    if (autocompletes != null) {
                        final DialogBuilder builder = DialogBuilder.get(DirectionsActivity.this);
                        builder.setTitle(getString(R.string.ambiguous_address_title));
                        builder.setAdapter(new AmbiguousLocationAdapter(DirectionsActivity.this, autocompletes),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(final DialogInterface dialog, final int which) {
                                        final LocationView locationView = result.ambiguousFrom != null
                                                ? viewFromLocation
                                                : (result.ambiguousVia != null ? viewViaLocation : viewToLocation);
                                        locationView.setLocation(autocompletes.get(which));
                                        viewGo.performClick();
                                    }
                                });
                        builder.create().show();
                    } else {
                        new Toast(DirectionsActivity.this).longToast(R.string.directions_message_ambiguous_location);
                    }
                }
            }

            @Override
            protected void onRedirect(final HttpUrl url) {
                final DialogBuilder builder = DialogBuilder.warn(DirectionsActivity.this,
                        R.string.directions_alert_redirect_title);
                builder.setMessage(getString(R.string.directions_alert_redirect_message, url.host()));
                builder.setPositiveButton(R.string.directions_alert_redirect_button_follow,
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())));
                            }
                        });
                builder.setNegativeButton(R.string.directions_alert_redirect_button_dismiss, null);
                builder.show();
            }

            @Override
            protected void onBlocked(final HttpUrl url) {
                final DialogBuilder builder = DialogBuilder.warn(DirectionsActivity.this,
                        R.string.directions_alert_blocked_title);
                builder.setMessage(getString(R.string.directions_alert_blocked_message, url.host()));
                builder.setPositiveButton(R.string.directions_alert_blocked_button_retry,
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                viewGo.performClick();
                            }
                        });
                builder.setNegativeButton(R.string.directions_alert_blocked_button_dismiss, null);
                builder.show();
            }

            @Override
            protected void onInternalError(final HttpUrl url) {
                final DialogBuilder builder = DialogBuilder.warn(DirectionsActivity.this,
                        R.string.directions_alert_internal_error_title);
                builder.setMessage(getString(R.string.directions_alert_internal_error_message, url.host()));
                builder.setPositiveButton(R.string.directions_alert_internal_error_button_retry,
                        new DialogInterface.OnClickListener() {
                            public void onClick(final DialogInterface dialog, final int which) {
                                viewGo.performClick();
                            }
                        });
                builder.setNegativeButton(R.string.directions_alert_internal_error_button_dismiss, null);
                builder.show();
            }

            @Override
            protected void onSSLException(final SSLException x) {
                final DialogBuilder builder = DialogBuilder.warn(DirectionsActivity.this,
                        R.string.directions_alert_ssl_exception_title);
                builder.setMessage(getString(R.string.directions_alert_ssl_exception_message,
                        Throwables.getRootCause(x).toString()));
                builder.setNeutralButton(R.string.directions_alert_ssl_exception_button_dismiss, null);
                builder.show();
            }

            private void networkProblem() {
                final DialogBuilder builder = DialogBuilder.warn(DirectionsActivity.this,
                        R.string.alert_network_problem_title);
                builder.setMessage(R.string.alert_network_problem_message);
                builder.setPositiveButton(R.string.alert_network_problem_retry, new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                        viewGo.performClick();
                    }
                });
                builder.setOnCancelListener(new OnCancelListener() {
                    public void onCancel(final DialogInterface dialog) {
                        dialog.dismiss();
                    }
                });
                builder.show();
            }
        };

        log.info("Executing: {}", queryTripsRunnable);

        backgroundHandler.post(queryTripsRunnable);
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        switch (id) {
        case DIALOG_CLEAR_HISTORY:
            final DialogBuilder builder = DialogBuilder.get(this);
            builder.setMessage(R.string.directions_query_history_clear_confirm_message);
            builder.setPositiveButton(R.string.directions_query_history_clear_confirm_button_clear,
                    new Dialog.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            queryHistoryListAdapter.removeAllEntries();
                            viewFromLocation.reset();
                            viewViaLocation.reset();
                            viewToLocation.reset();
                        }
                    });
            builder.setNegativeButton(R.string.directions_query_history_clear_confirm_button_dismiss, null);
            return builder.create();
        }

        return super.onCreateDialog(id);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent result) {
        if (result == null)
            return;

        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
            case (REQUEST_CODE_PICK_CONTACT_FROM):
                resultPickContact(result, viewFromLocation);
                break;

            case (REQUEST_CODE_PICK_CONTACT_VIA):
                resultPickContact(result, viewViaLocation);
                break;

            case (REQUEST_CODE_PICK_CONTACT_TO):
                resultPickContact(result, viewToLocation);
                break;

            case (REQUEST_CODE_PICK_STATION_FROM):
                resultPickStation(result, viewFromLocation);
                break;

            case (REQUEST_CODE_PICK_STATION_VIA):
                resultPickStation(result, viewViaLocation);
                break;

            case (REQUEST_CODE_PICK_STATION_TO):
                resultPickStation(result, viewToLocation);
                break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions,
            final int[] grantResults) {
        if (requestCode == REQUEST_CODE_LOCATION_PERMISSION_FROM)
            viewFromLocation.acquireLocation();
        else if (requestCode == REQUEST_CODE_LOCATION_PERMISSION_VIA)
            viewViaLocation.acquireLocation();
        else if (requestCode == REQUEST_CODE_LOCATION_PERMISSION_TO)
            viewToLocation.acquireLocation();
    }

    private void resultPickContact(final Intent result, final LocationView targetLocationView) {
        try {
            final Cursor c = managedQuery(result.getData(), null, null, null, null);
            if (c.moveToFirst()) {
                final String data = c
                        .getString(c.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS));
                final Location location = new Location(LocationType.ADDRESS, null, null, data.replace("\n", " "));
                targetLocationView.setLocation(location);
                log.info("Picked {} from contacts", location);
                requestFocusFirst();
            }
        } catch (final SecurityException x) {
            new Toast(this).longToast(R.string.directions_location_choose_error_permission, x.getMessage());
        }
    }

    private void resultPickStation(final Intent result, final LocationView targetLocationView) {
        final Cursor c = managedQuery(result.getData(), null, null, null, null);
        if (c.moveToFirst()) {
            final Location location = FavoriteStationsProvider.getLocation(c);
            targetLocationView.setLocation(location);
            log.info("Picked {} from station favorites", location);
            requestFocusFirst();
        }
    }

    private class AmbiguousLocationAdapter extends ArrayAdapter<Location> {
        public AmbiguousLocationAdapter(final Context context, final List<Location> autocompletes) {
            super(context, R.layout.directions_location_dropdown_entry, autocompletes);
        }

        @Override
        public View getView(final int position, View row, final ViewGroup parent) {
            row = super.getView(position, row, parent);

            final Location location = getItem(position);
            ((LocationTextView) row).setLocation(location);

            return row;
        }
    }

    private class AutoCompleteLocationAdapter extends BaseAdapter implements Filterable {
        private List<Location> locations = new LinkedList<>();

        public int getCount() {
            return locations.size();
        }

        public Object getItem(final int position) {
            return locations.get(position);
        }

        public long getItemId(final int position) {
            return position;
        }

        public View getView(final int position, View row, final ViewGroup parent) {
            if (row == null)
                row = getLayoutInflater().inflate(R.layout.directions_location_dropdown_entry, null);

            final Location location = locations.get(position);
            ((LocationTextView) row).setLocation(location);

            return row;
        }

        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(final CharSequence constraint) {
                    final FilterResults filterResults = new FilterResults();

                    try {
                        if (constraint != null) {
                            final String constraintStr = constraint.toString().trim();
                            if (constraintStr.length() > 0) {
                                final List<Location> results = new LinkedList<>();

                                // local autocomplete
                                final Cursor cursor = getContentResolver().query(
                                        QueryHistoryProvider.CONTENT_URI.buildUpon().appendPath(network.name())
                                                .appendQueryParameter(QueryHistoryProvider.QUERY_PARAM_Q, constraintStr)
                                                .build(),
                                        null, null, null, QueryHistoryProvider.KEY_LAST_QUERIED + " DESC");

                                final int fromTypeC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_TYPE);
                                final int fromIdC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_ID);
                                final int fromLatC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_LAT);
                                final int fromLonC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_LON);
                                final int fromPlaceC = cursor
                                        .getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_PLACE);
                                final int fromNameC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_FROM_NAME);
                                final int toTypeC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_TYPE);
                                final int toIdC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_ID);
                                final int toLatC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_LAT);
                                final int toLonC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_LON);
                                final int toPlaceC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_PLACE);
                                final int toNameC = cursor.getColumnIndexOrThrow(QueryHistoryProvider.KEY_TO_NAME);

                                while (cursor.moveToNext()) {
                                    final String fromName = cursor.getString(fromNameC);
                                    if (fromName.toLowerCase(Constants.DEFAULT_LOCALE)
                                            .contains(constraintStr.toLowerCase(Constants.DEFAULT_LOCALE))) {
                                        final LocationType fromType = QueryHistoryProvider
                                                .convert(cursor.getInt(fromTypeC));
                                        final String fromId = cursor.getString(fromIdC);
                                        final int fromLat = cursor.getInt(fromLatC);
                                        final int fromLon = cursor.getInt(fromLonC);
                                        final Point fromCoord = fromLat != 0 || fromLon != 0
                                                ? Point.from1E6(fromLat, fromLon) : null;
                                        final String fromPlace = cursor.getString(fromPlaceC);
                                        final Location location = new Location(fromType, fromId, fromCoord, fromPlace,
                                                fromName);
                                        if (!results.contains(location))
                                            results.add(location);
                                    }
                                    final String toName = cursor.getString(toNameC);
                                    if (toName.toLowerCase(Constants.DEFAULT_LOCALE)
                                            .contains(constraintStr.toLowerCase(Constants.DEFAULT_LOCALE))) {
                                        final LocationType toType = QueryHistoryProvider
                                                .convert(cursor.getInt(toTypeC));
                                        final String toId = cursor.getString(toIdC);
                                        final int toLat = cursor.getInt(toLatC);
                                        final int toLon = cursor.getInt(toLonC);
                                        final Point toCoord = toLat != 0 || toLon != 0 ? Point.from1E6(toLat, toLon)
                                                : null;
                                        final String toPlace = cursor.getString(toPlaceC);
                                        final Location location = new Location(toType, toId, toCoord, toPlace, toName);
                                        if (!results.contains(location))
                                            results.add(location);
                                    }
                                }
                                cursor.close();

                                // remote autocomplete
                                if (constraint.length() >= 3) {
                                    final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
                                    final EnumSet<LocationType> suggestedLocationTypes = EnumSet
                                            .of(LocationType.STATION, LocationType.POI, LocationType.ADDRESS);
                                    final SuggestLocationsResult suggestLocationsResult = networkProvider
                                            .suggestLocations(constraint, suggestedLocationTypes, 0);
                                    if (suggestLocationsResult.status == SuggestLocationsResult.Status.OK)
                                        for (final Location location : suggestLocationsResult.getLocations())
                                            if (!results.contains(location))
                                                results.add(location);
                                }

                                filterResults.values = results;
                                filterResults.count = results.size();
                            }
                        }
                    } catch (final IOException x) {
                        x.printStackTrace();
                    }

                    return filterResults;
                }

                @Override
                protected void publishResults(final CharSequence constraint, final FilterResults filterResults) {
                    if (filterResults.values != null) {
                        locations = (List<Location>) filterResults.values;
                        notifyDataSetChanged();
                    }
                }
            };
        }
    }

    private Optimize prefsGetOptimizeTrip() {
        final String optimize = prefs.getString(Constants.PREFS_KEY_OPTIMIZE_TRIP, null);
        if (optimize != null)
            return Optimize.valueOf(optimize);
        else
            return null;
    }

    private WalkSpeed prefsGetWalkSpeed() {
        return WalkSpeed.valueOf(prefs.getString(Constants.PREFS_KEY_WALK_SPEED, WalkSpeed.NORMAL.name()));
    }

    private Accessibility prefsGetAccessibility() {
        return Accessibility.valueOf(prefs.getString(Constants.PREFS_KEY_ACCESSIBILITY, Accessibility.NEUTRAL.name()));
    }
}
