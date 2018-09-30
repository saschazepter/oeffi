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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.LocationAware;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiMainActivity;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.StationsAware;
import de.schildbach.oeffi.directions.DirectionsActivity;
import de.schildbach.oeffi.network.NetworkPickerActivity;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.stations.list.StationContextMenuItemListener;
import de.schildbach.oeffi.stations.list.StationsAdapter;
import de.schildbach.oeffi.util.ConnectivityBroadcastReceiver;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.oeffi.util.DividerItemDecoration;
import de.schildbach.oeffi.util.LocationUriParser;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.NetworkProvider.Capability;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.LineDestination;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.StationDepartures;
import de.schildbach.pte.dto.Style;
import de.schildbach.pte.dto.SuggestLocationsResult;

import android.Manifest;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.format.DateUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.ViewAnimator;
import okhttp3.HttpUrl;

public class StationsActivity extends OeffiMainActivity implements StationsAware, LocationAware,
        ActivityCompat.OnRequestPermissionsResultCallback, StationContextMenuItemListener {
    private ConnectivityManager connectivityManager;
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor sensorAccelerometer;
    private Sensor sensorMagnetometer;
    private Resources res;

    private final List<Station> stations = new ArrayList<>();
    private final Map<String, Station> stationsMap = new HashMap<>();
    private final Map<String, Integer> favorites = new HashMap<>();
    private String selectedStationId;
    private Point deviceLocation;
    private Location fixedLocation;
    private Float deviceBearing = null;
    private String searchQuery;
    private boolean anyProviderEnabled = false;
    private boolean loading = true;

    private final Set<Product> products = new HashSet<>(Product.values().length);
    private String accurateLocationProvider, lowPowerLocationProvider;

    private MyActionBar actionBar;
    private RecyclerView stationList;
    private LinearLayoutManager stationListLayoutManager;
    private StationsAdapter stationListAdapter;
    private TextView connectivityWarningView;
    private TextView disclaimerSourceView;
    private View filterActionButton;
    private ViewGroup locationProvidersView;

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private BroadcastReceiver connectivityReceiver;
    private BroadcastReceiver tickReceiver;

    private int maxDeparturesPerStation;

    private static final int DIALOG_NEARBY_STATIONS_ERROR = 1;

    private static final Logger log = LoggerFactory.getLogger(StationsActivity.class);

    @Override
    protected String taskName() {
        return "stations";
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorMagnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        res = getResources();

        setContentView(R.layout.stations_content);
        actionBar = getMyActionBar();
        setPrimaryColor(R.color.action_bar_background_stations);
        actionBar.setPrimaryTitle(R.string.stations_activity_title);
        actionBar.setTitlesOnClickListener(new OnClickListener() {
            public void onClick(final View v) {
                NetworkPickerActivity.start(StationsActivity.this);
            }
        });
        actionBar.addProgressButton().setOnClickListener(new OnClickListener() {
            public void onClick(final View v) {
                for (final Station station : stations)
                    station.requestedAt = null;
                handler.post(initStationsRunnable);
            }
        });
        actionBar.addButton(R.drawable.ic_search_white_24dp, R.string.stations_action_search_title)
                .setOnClickListener(new OnClickListener() {
                    public void onClick(final View v) {
                        onSearchRequested();
                    }
                });
        filterActionButton = actionBar.addButton(R.drawable.ic_filter_list_24dp, R.string.stations_filter_title);
        filterActionButton.setOnClickListener(new OnClickListener() {
            public void onClick(final View v) {
                final StationsFilterPopup popup = new StationsFilterPopup(StationsActivity.this, products,
                        new StationsFilterPopup.Listener() {
                            public void filterChanged(final Set<Product> filter) {
                                final Set<Product> added = new HashSet<>(filter);
                                added.removeAll(products);

                                final Set<Product> removed = new HashSet<>(products);
                                removed.removeAll(filter);

                                products.clear();
                                products.addAll(filter);

                                if (!added.isEmpty()) {
                                    handler.post(initStationsRunnable);
                                }

                                if (!removed.isEmpty()) {
                                    for (final Iterator<Station> i = stations.iterator(); i.hasNext();) {
                                        final Station station = i.next();
                                        final List<LineDestination> lines = station.getLines();
                                        if (!filter(lines, products)) {
                                            i.remove();
                                            stationsMap.remove(station.location.id);
                                        }
                                    }

                                    stationListAdapter.notifyDataSetChanged();
                                }

                                updateGUI();
                            }
                        });
                popup.showAsDropDown(v);
            }
        });

        initNavigation();

        FloatingActionButton fab = findViewById(R.id.stations_fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FavoriteStationsActivity.start(StationsActivity.this);
            }
        });

        locationProvidersView = (ViewGroup) findViewById(R.id.stations_list_location_providers);

        final Button locationPermissionRequestButton = (Button) findViewById(
                R.id.stations_location_permission_request_button);
        locationPermissionRequestButton.setOnClickListener(new OnClickListener() {
            public void onClick(final View v) {
                ActivityCompat.requestPermissions(StationsActivity.this,
                        new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 0);
            }
        });

        final Button locationSettingsButton = (Button) findViewById(R.id.stations_list_location_settings);
        locationSettingsButton.setOnClickListener(new OnClickListener() {
            public void onClick(final View v) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        });

        final OnClickListener selectNetworkListener = new OnClickListener() {
            public void onClick(final View v) {
                NetworkPickerActivity.start(StationsActivity.this);
            }
        };
        final Button networkSettingsButton = (Button) findViewById(R.id.stations_list_empty_network_settings);
        networkSettingsButton.setOnClickListener(selectNetworkListener);
        final Button missingCapabilityButton = (Button) findViewById(R.id.stations_network_missing_capability_button);
        missingCapabilityButton.setOnClickListener(selectNetworkListener);

        connectivityWarningView = (TextView) findViewById(R.id.stations_connectivity_warning_box);
        disclaimerSourceView = (TextView) findViewById(R.id.stations_disclaimer_source);

        // initialize stations list
        maxDeparturesPerStation = res.getInteger(R.integer.max_departures_per_station);

        final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

                    private float lastDX = 0;
                    private int lastDirection = 0;

                    private final Drawable drawableStar = getResources()
                            .getDrawable(R.drawable.ic_star_border_black_24dp);
                    private final Drawable drawableClear = getResources().getDrawable(R.drawable.ic_clear_black_24dp);
                    private final Drawable drawableBlock = getResources().getDrawable(R.drawable.ic_block_black_24dp);
                    private final int starMargin = getResources()
                            .getDimensionPixelOffset(R.dimen.list_entry_padding_horizontal_lax);
                    private final int actionTriggerThreshold = Ints.max(drawableStar.getIntrinsicWidth(),
                            drawableClear.getIntrinsicWidth(), drawableBlock.getIntrinsicWidth()) + starMargin * 2;

                    @Override
                    public boolean isItemViewSwipeEnabled() {
                        return true;
                    }

                    @Override
                    public float getSwipeEscapeVelocity(final float defaultValue) {
                        return Float.MAX_VALUE; // disable swipe by flinging
                    }

                    @Override
                    public float getSwipeThreshold(final RecyclerView.ViewHolder viewHolder) {
                        return Float.MAX_VALUE; // disable swipe by dragging
                    }

                    @Override
                    public void onChildDraw(final Canvas c, final RecyclerView recyclerView,
                            final RecyclerView.ViewHolder viewHolder, float dX, final float dY, final int actionState,
                            final boolean isCurrentlyActive) {
                        final int adapterPosition = viewHolder.getAdapterPosition();
                        if (adapterPosition == RecyclerView.NO_POSITION)
                            return;
                        final Station station = stationListAdapter.getItem(adapterPosition);
                        final Integer favState = favorites.get(station.location.id);
                        final Drawable drawable;
                        if (favState != null)
                            drawable = drawableClear;
                        else if (dX > 0)
                            drawable = drawableStar;
                        else
                            drawable = drawableBlock;

                        final int drawableHeight = drawable.getIntrinsicHeight();
                        final int drawableWidth = drawable.getIntrinsicWidth();
                        final int drawableTop = viewHolder.itemView.getTop() + viewHolder.itemView.getHeight() / 2
                                - drawableHeight / 2;
                        if (dX > 0 && (favState == null || favState == FavoriteStationsProvider.TYPE_IGNORE)) {
                            // drag right
                            if (dX > actionTriggerThreshold) {
                                dX = actionTriggerThreshold;
                                if (isCurrentlyActive) {
                                    drawable.setBounds(starMargin, drawableTop, starMargin + drawableWidth,
                                            drawableTop + drawableHeight);
                                    drawable.draw(c);
                                }
                            }
                        } else if (dX < 0 && (favState == null || favState == FavoriteStationsProvider.TYPE_FAVORITE)) {
                            // drag left
                            if (dX < -actionTriggerThreshold) {
                                dX = -actionTriggerThreshold;
                                if (isCurrentlyActive) {
                                    final int right = viewHolder.itemView.getWidth() - starMargin;
                                    drawable.setBounds(right - drawableWidth, drawableTop, right,
                                            drawableTop + drawableHeight);
                                    drawable.draw(c);
                                }
                            }
                        } else {
                            dX = 0;
                        }

                        lastDX = dX;
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

                        if (dX == 0 && lastDirection != 0) {
                            onAction(adapterPosition, lastDirection);
                            lastDirection = 0;
                        }
                    }

                    @Override
                    public void onSelectedChanged(final RecyclerView.ViewHolder viewHolder, final int actionState) {
                        super.onSelectedChanged(viewHolder, actionState);

                        if (actionState == ItemTouchHelper.ACTION_STATE_IDLE
                                && Math.abs(lastDX) >= actionTriggerThreshold)
                            lastDirection = lastDX > 0 ? ItemTouchHelper.RIGHT : ItemTouchHelper.LEFT;
                    }

                    private void onAction(final int adapterPosition, final int direction) {
                        final Station station = stationListAdapter.getItem(adapterPosition);
                        final Location location = station.location;
                        final Integer favState = favorites.get(location.id);
                        if (direction == ItemTouchHelper.RIGHT && favState == null)
                            addFavorite(location);
                        else if (direction == ItemTouchHelper.RIGHT && favState == FavoriteStationsProvider.TYPE_IGNORE)
                            removeIgnore(location);
                        else if (direction == ItemTouchHelper.LEFT && favState == null)
                            addIgnore(location);
                        else if (direction == ItemTouchHelper.LEFT
                                && favState == FavoriteStationsProvider.TYPE_FAVORITE)
                            removeFavorite(location);
                        stationListAdapter.notifyItemChanged(adapterPosition);
                    }

                    @Override
                    public void onSwiped(final RecyclerView.ViewHolder viewHolder, final int direction) {
                        throw new IllegalStateException();
                    }

                    @Override
                    public boolean onMove(final RecyclerView recyclerView, final RecyclerView.ViewHolder viewHolder,
                            final RecyclerView.ViewHolder target) {
                        throw new IllegalStateException();
                    }
                });
        stationList = (RecyclerView) findViewById(R.id.stations_list);
        stationListLayoutManager = new LinearLayoutManager(this);
        stationList.setLayoutManager(stationListLayoutManager);
        stationList.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        stationListAdapter = new StationsAdapter(this, network, maxDeparturesPerStation, products, this, this);
        stationList.setAdapter(stationListAdapter);
        stationList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE)
                    postLoadNextVisible(0);
            }
        });
        itemTouchHelper.attachToRecyclerView(stationList);

        connectivityReceiver = new ConnectivityBroadcastReceiver(connectivityManager) {
            @Override
            protected void onConnected() {
                connectivityWarningView.setVisibility(View.GONE);
                postLoadNextVisible(0);
            }

            @Override
            protected void onDisconnected() {
                connectivityWarningView.setVisibility(View.VISIBLE);
            }
        };
        registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        loadProductFilter();

        handleIntent(getIntent());

        updateGUI();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // background thread
        backgroundThread = new HandlerThread("queryDeparturesThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        if (network != null && NetworkProviderFactory.provider(network).hasCapabilities(Capability.DEPARTURES)) {
            startLocationProvider();

            // request update on content change (db loaded)
            getContentResolver().registerContentObserver(NetworkContentProvider.CONTENT_URI, true, contentObserver);

            // request update on orientation change
            sensorManager.registerListener(orientationListener, sensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(orientationListener, sensorMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);

            // regular refresh
            tickReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    postLoadNextVisible(0);
                }
            };
            registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        }

        setActionBarSecondaryTitleFromNetwork();
        updateGUI();
    }

    @Override
    protected void onResume() {
        super.onResume();

        postLoadNextVisible(0);
    }

    @Override
    protected void onChangeNetwork(final NetworkId network) {
        clearListFilter();

        stations.clear();
        stationsMap.clear();

        stationListAdapter.notifyDataSetChanged();
        loading = true;

        updateDisclaimerSource(disclaimerSourceView, network.name(), null);
        updateGUI();
        setActionBarSecondaryTitleFromNetwork();

        handler.removeCallbacksAndMessages(null);
        handler.post(initStationsRunnable);
    }

    @Override
    protected void onPause() {
        saveProductFilter();

        super.onPause();
    }

    @Override
    protected void onStop() {
        // cancel refresh
        if (tickReceiver != null) {
            unregisterReceiver(tickReceiver);
            tickReceiver = null;
        }

        stopLocationProvider();

        // cancel update on orientation change
        sensorManager.unregisterListener(orientationListener);

        // cancel content change
        getContentResolver().unregisterContentObserver(contentObserver);

        // cancel background thread
        backgroundThread.getLooper().quit();

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(connectivityReceiver);

        stations.clear();
        stationsMap.clear();

        stationList.clearOnScrollListeners();

        super.onDestroy();
    }

    @Override
    public void onNewIntent(final Intent intent) {
        setIntent(intent);

        handleIntent(intent);
    }

    private void handleIntent(final Intent intent) {
        final Uri intentUri = intent.getData();

        if (intentUri != null) {
            final Location[] locations = LocationUriParser.parseLocations(intentUri.toString());
            fixedLocation = locations != null && locations.length >= 1 ? locations[0] : null;

            if (fixedLocation != null) {
                findViewById(R.id.stations_location_clear).setOnClickListener(new OnClickListener() {
                    public void onClick(final View v) {
                        fixedLocation = null;

                        if (deviceLocation != null) {
                            final float[] distanceBetweenResults = new float[2];

                            // remove non-favorites and re-calculate distances
                            for (final Iterator<Station> i = stations.iterator(); i.hasNext();) {
                                final Station station = i.next();

                                final Integer favState = favorites.get(station.location.id);
                                if (favState == null || favState != FavoriteStationsProvider.TYPE_FAVORITE) {
                                    i.remove();
                                    stationsMap.remove(station.location.id);
                                } else if (station.location.hasLocation()) {
                                    android.location.Location.distanceBetween(deviceLocation.lat / 1E6,
                                            deviceLocation.lon / 1E6, station.location.lat / 1E6,
                                            station.location.lon / 1E6, distanceBetweenResults);
                                    station.setDistanceAndBearing(distanceBetweenResults[0], distanceBetweenResults[1]);
                                }
                            }
                            stationListAdapter.notifyDataSetChanged();
                        }

                        handler.post(initStationsRunnable);
                        updateGUI();
                    }
                });

                handler.post(initStationsRunnable);
            }
        }

        final String query = intent.getStringExtra(SearchManager.QUERY);

        if (query != null) {
            setListFilter(query.trim());
        }
    }

    @Override
    public void onBackPressed() {
        if (isNavigationOpen())
            closeNavigation();
        else if (searchQuery != null)
            clearListFilter();
        else
            super.onBackPressed();
    }

    private void setListFilter(final String filter) {
        searchQuery = filter;

        findViewById(R.id.stations_search_clear).setOnClickListener(new OnClickListener() {
            public void onClick(final View v) {
                clearListFilter();
            }
        });

        stations.clear();
        stationsMap.clear();
        stationListAdapter.setShowPlaces(true);
        stationListAdapter.notifyDataSetChanged();

        handler.post(initStationsRunnable);
        updateGUI();
    }

    private void clearListFilter() {
        searchQuery = null;

        stations.clear();
        stationsMap.clear();
        stationListAdapter.setShowPlaces(false);
        stationListAdapter.notifyDataSetChanged();

        handler.post(initStationsRunnable);
        updateGUI();
    }

    private void updateGUI() {
        // filter indicator
        final boolean isActive = products.size() < Product.values().length;
        filterActionButton.setSelected(isActive);

        final ViewAnimator viewAnimator = (ViewAnimator) findViewById(R.id.stations_list_layout);
        if (network == null || !NetworkProviderFactory.provider(network).hasCapabilities(Capability.DEPARTURES)) {
            viewAnimator.setDisplayedChild(1); // Missing capability
        } else if (searchQuery == null && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            viewAnimator.setDisplayedChild(2); // Location permission denied
        } else if (searchQuery == null && loading && deviceLocation == null && !anyProviderEnabled) {
            viewAnimator.setDisplayedChild(3); // Location providers disabled
        } else if (searchQuery == null && loading && deviceLocation == null && anyProviderEnabled) {
            viewAnimator.setDisplayedChild(4); // Acquiring location
        } else if (stations.isEmpty() && searchQuery == null && loading && deviceLocation != null) {
            viewAnimator.setDisplayedChild(5); // Querying nearby stations
        } else if (stations.isEmpty() && searchQuery != null && loading) {
            viewAnimator.setDisplayedChild(6); // Matching stations
        } else if (stations.isEmpty() && searchQuery == null) {
            viewAnimator.setDisplayedChild(7); // List empty, no nearby stations
        } else if (stations.isEmpty() && searchQuery != null) {
            viewAnimator.setDisplayedChild(8); // List empty, no query match
        } else {
            viewAnimator.setDisplayedChild(0); // Stations list
        }

        // location box
        findViewById(R.id.stations_location_box).setVisibility(fixedLocation != null ? View.VISIBLE : View.GONE);
        if (fixedLocation != null)
            ((TextView) findViewById(R.id.stations_location_text)).setText(fixedLocation.name != null
                    ? fixedLocation.name
                    : String.format(Locale.ENGLISH, "%.6f, %.6f", fixedLocation.lat / 1E6, fixedLocation.lon / 1E6));

        // search box
        findViewById(R.id.stations_search_box).setVisibility(searchQuery != null ? View.VISIBLE : View.GONE);
        if (searchQuery != null)
            ((TextView) findViewById(R.id.stations_search_text)).setText(searchQuery);
    }

    private boolean addFavorite(final Location location) {
        final Uri rowUri = FavoriteUtils.persist(getContentResolver(), FavoriteStationsProvider.TYPE_FAVORITE, network,
                location);
        if (rowUri != null) {
            favorites.put(location.id, FavoriteStationsProvider.TYPE_FAVORITE);
            postLoadNextVisible(0);
            FavoriteUtils.notifyFavoritesChanged(this);
            return true;
        } else {
            return false;
        }
    }

    private boolean removeFavorite(final Location location) {
        final int numRows = FavoriteUtils.delete(getContentResolver(), network, location.id);
        if (numRows > 0) {
            favorites.remove(location.id);
            FavoriteUtils.notifyFavoritesChanged(this);
            return true;
        } else {
            return false;
        }
    }

    private boolean addIgnore(final Location location) {
        final Uri rowUriIgnored = FavoriteUtils.persist(getContentResolver(), FavoriteStationsProvider.TYPE_IGNORE,
                network, location);
        if (rowUriIgnored != null) {
            favorites.put(location.id, FavoriteStationsProvider.TYPE_IGNORE);
            FavoriteUtils.notifyFavoritesChanged(this);
            return true;
        } else {
            return false;
        }
    }

    private boolean removeIgnore(final Location location) {
        final int numRowsIgnored = FavoriteUtils.delete(getContentResolver(), network, location.id);
        if (numRowsIgnored > 0) {
            favorites.remove(location.id);
            postLoadNextVisible(0);
            FavoriteUtils.notifyFavoritesChanged(this);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        switch (id) {
        case DIALOG_NEARBY_STATIONS_ERROR:
            final DialogBuilder builder = DialogBuilder.warn(this, R.string.stations_nearby_stations_error_title);
            builder.setMessage(getString(R.string.stations_nearby_stations_error_message));
            builder.setPositiveButton(getString(R.string.stations_nearby_stations_error_continue),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });
            builder.setNegativeButton(getString(R.string.stations_nearby_stations_error_exit),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                            finish();
                        }
                    });
            return builder.create();
        }

        return super.onCreateDialog(id);
    }

    private final ContentObserver contentObserver = new ContentObserver(handler) {
        @Override
        public void onChange(final boolean selfChange) {
            runOnUiThread(initStationsRunnable);
        }
    };

    private final Runnable initStationsRunnable = new Runnable() {
        public void run() {
            if (network != null) {
                if (searchQuery == null)
                    runNearbyQuery();
                else
                    runSearchQuery();
            }
        }

        private void runNearbyQuery() {
            final Location referenceLocation = getReferenceLocation();

            if (referenceLocation != null) {
                final MyActionBar actionBar = getMyActionBar();

                final double referenceLat = referenceLocation.lat / 1E6;
                final double referenceLon = referenceLocation.lon / 1E6;

                final StringBuilder favoriteIds = new StringBuilder();
                for (final Map.Entry<String, Integer> entry : favorites.entrySet())
                    if (entry.getValue() == FavoriteStationsProvider.TYPE_FAVORITE)
                        favoriteIds.append(entry.getKey()).append(',');
                if (favoriteIds.length() != 0)
                    favoriteIds.setLength(favoriteIds.length() - 1);

                backgroundHandler.post(new Runnable() {
                    public void run() {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                actionBar.startProgress();
                                loading = true;
                                updateGUI();
                            }
                        });

                        final Builder uriBuilder = NetworkContentProvider.CONTENT_URI.buildUpon();
                        uriBuilder.appendPath(network.name());
                        uriBuilder.appendQueryParameter("lat", Integer.toString(referenceLocation.lat));
                        uriBuilder.appendQueryParameter("lon", Integer.toString(referenceLocation.lon));
                        uriBuilder.appendQueryParameter("ids", favoriteIds.toString());
                        final Cursor cursor = getContentResolver().query(uriBuilder.build(), null, null, null, null);

                        if (cursor != null) {
                            final int nativeIdColumnIndex = cursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_ID);
                            final int localIdColumnIndex = cursor.getColumnIndex(NetworkContentProvider.KEY_LOCAL_ID);
                            final int placeColumnIndex = cursor.getColumnIndex(NetworkContentProvider.KEY_PLACE);
                            final int nameColumnIndex = cursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_NAME);
                            final int latColumnIndex = cursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_LAT);
                            final int lonColumnIndex = cursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_LON);
                            final int linesColumnIndex = cursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_LINES);

                            final List<Station> freshStations = new ArrayList<>(cursor.getCount());

                            final float[] distanceBetweenResults = new float[2];

                            final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);

                            while (cursor.moveToNext()) {
                                final List<LineDestination> lineDestinations = new LinkedList<>();
                                for (final String lineStr : cursor.getString(linesColumnIndex).split(",")) {
                                    if (!lineStr.isEmpty()) {
                                        final Product product = Product.fromCode(lineStr.charAt(0));
                                        final String label = Strings.emptyToNull(lineStr.substring(1));
                                        // FIXME don't access networkProvider
                                        // from thread
                                        final Style style = networkProvider.lineStyle(null, product, label);
                                        lineDestinations.add(
                                                new LineDestination(new Line(null, null, product, label, style), null));
                                    }
                                }

                                final String id = localIdColumnIndex != -1 ? cursor.getString(localIdColumnIndex)
                                        : cursor.getString(nativeIdColumnIndex);
                                final String place = placeColumnIndex != -1 ? cursor.getString(placeColumnIndex) : null;
                                final String name = cursor.getString(nameColumnIndex);
                                final int lat = cursor.getInt(latColumnIndex);
                                final int lon = cursor.getInt(lonColumnIndex);
                                final Station station = new Station(network, new de.schildbach.pte.dto.Location(
                                        LocationType.STATION, id, lat, lon, place, name), lineDestinations);
                                if (deviceLocation != null) {
                                    android.location.Location.distanceBetween(referenceLat, referenceLon, lat / 1E6,
                                            lon / 1E6, distanceBetweenResults);
                                    station.setDistanceAndBearing(distanceBetweenResults[0], distanceBetweenResults[1]);
                                }
                                freshStations.add(station);
                            }

                            cursor.close();

                            runOnUiThread(new Runnable() {
                                public void run() {
                                    mergeIntoStations(freshStations, true);
                                }
                            });
                        }

                        runOnUiThread(new Runnable() {
                            public void run() {
                                actionBar.stopProgress();
                                loading = false;
                                updateGUI();
                            }
                        });
                    }
                });
            }

            // refresh favorites
            final Map<Location, Integer> favoriteMap = FavoriteUtils.loadAll(getContentResolver(), network);
            final List<Station> freshStations = new ArrayList<>(favoriteMap.size());

            final float[] distanceBetweenResults = new float[2];

            for (final Map.Entry<Location, Integer> entry : favoriteMap.entrySet()) {
                final Location location = entry.getKey();
                final String stationId = location.id;
                final int favType = entry.getValue();
                favorites.put(stationId, favType);

                if (favType == FavoriteStationsProvider.TYPE_FAVORITE) {
                    final Station station = new Station(network, location, null);
                    if (deviceLocation != null && location.hasLocation()) {
                        android.location.Location.distanceBetween(deviceLocation.lat / 1E6, deviceLocation.lon / 1E6,
                                location.lat / 1E6, location.lon / 1E6, distanceBetweenResults);
                        station.setDistanceAndBearing(distanceBetweenResults[0], distanceBetweenResults[1]);
                    }
                    freshStations.add(station);
                }
            }
            mergeIntoStations(freshStations, false);
        }

        private void runSearchQuery() {
            loading = true;

            new SearchTask() {
                @Override
                protected void onPostExecute(final List<Station> freshStations) {
                    final Location referenceLocation = getReferenceLocation();

                    if (referenceLocation != null) {
                        final double referenceLat = referenceLocation.lat / 1E6;
                        final double referenceLon = referenceLocation.lon / 1E6;

                        final float[] distanceBetweenResults = new float[2];

                        for (final Station freshStation : freshStations) {
                            if (freshStation.location.hasLocation()) {
                                android.location.Location.distanceBetween(referenceLat, referenceLon,
                                        freshStation.location.lat / 1E6, freshStation.location.lon / 1E6,
                                        distanceBetweenResults);
                                freshStation.setDistanceAndBearing(distanceBetweenResults[0],
                                        distanceBetweenResults[1]);
                            }
                        }
                    }

                    loading = false;
                    mergeIntoStations(freshStations, true);
                }
            }.execute(searchQuery);
        }
    };

    private void mergeIntoStations(final List<Station> freshStations, final boolean updateExisting) {
        boolean added = false;
        boolean changed = false;

        for (final Station freshStation : freshStations) {
            final Station station = stationsMap.get(freshStation.location.id);
            if (station != null) {
                if (updateExisting) {
                    if (freshStation.location != null) {
                        station.location = freshStation.location;
                        changed = true;
                    }
                    if (freshStation.hasDistanceAndBearing) {
                        station.setDistanceAndBearing(freshStation.distance, freshStation.bearing);
                        changed = true;
                    }
                    if (freshStation.departures != null) {
                        station.departures = freshStation.departures;
                        changed = true;
                    }
                    if (freshStation.getLines() != null) {
                        station.setLines(freshStation.getLines());
                        changed = true;
                    }
                }
            } else if (filter(freshStation.getLines(), products)) {
                stations.add(freshStation);
                stationsMap.put(freshStation.location.id, freshStation);

                added = true;
                changed = true;
            }
        }

        if (changed) {
            // need to sort again
            sortStations(stations);
        }

        if (added) {
            // clip list at end, retaining favorites
            int stationToRemove = stations.size() - 1;
            while (stations.size() > Constants.MAX_NUMBER_OF_STOPS && stationToRemove >= 0) {
                final Integer favState = favorites.get(stations.get(stationToRemove).location.id);
                if (favState == null || favState != FavoriteStationsProvider.TYPE_FAVORITE)
                    // remove from list & map at once
                    stationsMap.remove(stations.remove(stationToRemove).location.id);

                stationToRemove--;
            }

            postLoadNextVisible(100); // List needs time to initialize.
        }

        if (added || changed) {
            stationListAdapter.notifyDataSetChanged();
        }

        updateGUI();
    }

    private static boolean filter(final List<LineDestination> lines, final Collection<Product> products) {
        if (lines == null)
            return true;

        for (final LineDestination line : lines) {
            final Product product = line.line.product;
            if (product != null)
                for (final Product filterProduct : products)
                    if (product == filterProduct)
                        return true;
        }

        return false;
    }

    private static void sortStations(final List<Station> stations) {
        Collections.sort(stations, new Comparator<Station>() {
            public int compare(final Station station1, final Station station2) {
                ComparisonChain chain = ComparisonChain.start();

                // order by distance
                chain = chain.compareTrueFirst(station1.hasDistanceAndBearing, station2.hasDistanceAndBearing)
                        .compare(station1.distance, station2.distance);

                // order by lines
                final List<LineDestination> lines1 = station1.getLines();
                final List<LineDestination> lines2 = station2.getLines();
                final List<LineDestination> lineDestinations1 = lines1 != null ? lines1
                        : Collections.<LineDestination> emptyList();
                final List<LineDestination> lineDestinations2 = lines2 != null ? lines2
                        : Collections.<LineDestination> emptyList();
                final int length1 = lineDestinations1.size();
                final int length2 = lineDestinations2.size();
                final int length = Math.max(length1, length2);

                for (int i = 0; i < length; i++) {
                    final Line line1 = i < length1 ? lineDestinations1.get(i).line : null;
                    final Line line2 = i < length2 ? lineDestinations2.get(i).line : null;
                    chain = chain.compare(line1, line2, Ordering.natural().nullsLast());
                }

                return chain.result();
            }
        });
    }

    private void postLoadNextVisible(final long delay) {
        final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnected())
            return;

        if (delay == 0)
            handler.post(loadVisibleRunnable);
        else
            handler.postDelayed(loadVisibleRunnable, delay);
    }

    private final Runnable loadVisibleRunnable = new Runnable() {
        public void run() {
            final Station station = nextStationToLoad();

            if (station != null) {
                final String requestedStationId = station.location.id;
                if (requestedStationId != null) {
                    station.requestedAt = new Date();

                    final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);
                    final int maxDepartures = maxDeparturesPerStation * 2;

                    backgroundHandler.post(
                            new QueryDeparturesRunnable(handler, networkProvider, requestedStationId, maxDepartures) {
                                @Override
                                protected void onPreExecute() {
                                    actionBar.startProgress();
                                }

                                @Override
                                protected void onPostExecute() {
                                    actionBar.stopProgress();
                                }

                                @Override
                                protected void onResult(final QueryDeparturesResult result) {
                                    if (result.header != null)
                                        updateDisclaimerSource(disclaimerSourceView, network.name(),
                                                product(result.header));

                                    if (result.status == QueryDeparturesResult.Status.OK) {
                                        if (!result.stationDepartures.isEmpty()) {
                                            for (final StationDepartures stationDepartures : result.stationDepartures) {
                                                final String stationId = stationDepartures.location.id;
                                                final Station resultStation = stationsMap.get(stationId);
                                                if (resultStation != null && (requestedStationId.equals(stationId)
                                                        || (resultStation.requestedAt == null
                                                                && !stationDepartures.departures.isEmpty()))) {
                                                    // Trim departures
                                                    final List<Departure> departures = stationDepartures.departures;
                                                    while (departures.size() > maxDepartures)
                                                        departures.remove(departures.size() - 1);

                                                    resultStation.departures = departures;
                                                    resultStation.departureQueryStatus = QueryDeparturesResult.Status.OK;
                                                    resultStation.updatedAt = new Date();
                                                }
                                            }
                                        } else {
                                            // Station is existing but yields no StationDepartures
                                            station.departures = Collections.emptyList();
                                            station.departureQueryStatus = QueryDeparturesResult.Status.OK;
                                            station.updatedAt = new Date();
                                        }

                                        stationListAdapter.notifyDataSetChanged();
                                    } else if (result.status == QueryDeparturesResult.Status.INVALID_STATION) {
                                        final Station resultStation = stationsMap.get(requestedStationId);
                                        if (resultStation != null) {
                                            resultStation.departureQueryStatus = QueryDeparturesResult.Status.INVALID_STATION;
                                            resultStation.updatedAt = new Date();

                                            stationListAdapter.notifyDataSetChanged();
                                        }
                                    } else {
                                        log.info("Got {}", result.toShortString());
                                        new Toast(StationsActivity.this)
                                                .toast(QueryDeparturesRunnable.statusMsgResId(result.status));
                                    }

                                    postLoadNextVisible(0);
                                }

                                @Override
                                protected void onRedirect(final HttpUrl url) {
                                    log.info("Redirect while querying departures on {}", requestedStationId);

                                    handler.post(new Runnable() {
                                        public void run() {
                                            new Toast(StationsActivity.this).toast(R.string.toast_network_problem);
                                        }
                                    });
                                };

                                @Override
                                protected void onBlocked(final HttpUrl url) {
                                    log.info("Blocked querying departures on {}", requestedStationId);

                                    handler.post(new Runnable() {
                                        public void run() {
                                            new Toast(StationsActivity.this).toast(R.string.toast_network_blocked,
                                                    url.host());
                                        }
                                    });
                                }

                                @Override
                                protected void onInternalError(final HttpUrl url) {
                                    log.info("Internal error querying departures on {}", requestedStationId);

                                    handler.post(new Runnable() {
                                        public void run() {
                                            new Toast(StationsActivity.this).toast(R.string.toast_internal_error,
                                                    url.host());
                                        }
                                    });
                                }

                                @Override
                                protected void onParserException(final String message) {
                                    log.info("Cannot parse departures on {}: {}", requestedStationId, message);

                                    handler.post(new Runnable() {
                                        public void run() {
                                            final String limitedMessage = message != null
                                                    ? message.substring(0, Math.min(100, message.length())) : null;
                                            new Toast(StationsActivity.this).toast(R.string.toast_invalid_data,
                                                    limitedMessage);
                                        }
                                    });
                                }

                                @Override
                                protected void onInputOutputError(final IOException x) {
                                    handler.post(new Runnable() {
                                        public void run() {
                                            new Toast(StationsActivity.this).toast(R.string.toast_network_problem);
                                        }
                                    });
                                }
                            });
                }
            }
        }

        private Station nextStationToLoad() {
            if (stations.isEmpty())
                return null;

            int firstVisible = stationListLayoutManager.findFirstVisibleItemPosition();
            int lastVisible = stationListLayoutManager.findLastVisibleItemPosition();
            if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION)
                return null;
            if (firstVisible >= stations.size())
                firstVisible = stations.size() - 1;
            if (lastVisible >= stations.size())
                lastVisible = stations.size() - 1;

            final long now = System.currentTimeMillis();

            for (int i = firstVisible; i <= lastVisible; i++) // first load selected
            {
                final Station station = stations.get(i);

                final Date requestedAt = station.requestedAt;
                if ((requestedAt == null || now - requestedAt.getTime() > DateUtils.MINUTE_IN_MILLIS)) {
                    if (selectedStationId != null && selectedStationId.equals(station.location.id))
                        return station;
                }
            }

            for (int i = firstVisible; i <= lastVisible; i++) // then load favorites
            {
                final Station station = stations.get(i);

                final Date requestedAt = station.requestedAt;
                if ((requestedAt == null || now - requestedAt.getTime() > DateUtils.MINUTE_IN_MILLIS)) {
                    final Integer favState = favorites.get(station.location.id);
                    if (favState != null && favState == FavoriteStationsProvider.TYPE_FAVORITE)
                        return station;
                }
            }

            for (int i = firstVisible; i <= lastVisible; i++) // then load others
            {
                final Station station = stations.get(i);

                if (station.requestedAt == null) {
                    final Integer favState = favorites.get(station.location.id);
                    if (favState == null)
                        return station;
                }
            }

            return null;
        }
    };

    private void loadProductFilter() {
        final String p = prefs.getString(Constants.PREFS_KEY_PRODUCT_FILTER, null);
        if (p != null) {
            products.clear();
            for (final char c : p.toCharArray())
                products.add(Product.fromCode(c));
        } else {
            products.addAll(Arrays.asList(Product.values()));
        }
    }

    private void saveProductFilter() {
        final StringBuilder p = new StringBuilder();
        for (final Product product : products)
            p.append(product.code);
        prefs.edit().putString(Constants.PREFS_KEY_PRODUCT_FILTER, p.toString()).commit();
    }

    public final List<Station> getStations() {
        return stations;
    }

    public final Integer getFavoriteState(final String stationId) {
        return favorites.get(stationId);
    }

    public final void selectStation(final Station station) {
        selectedStationId = station != null ? station.location.id : null;
        stationListAdapter.notifyDataSetChanged();

        // scroll list into view
        for (int position = 0; position < stations.size(); position++) {
            if (stations.get(position).equals(station)) {
                stationList.smoothScrollToPosition(position);
                break;
            }
        }

        postLoadNextVisible(0);
    }

    public final boolean isSelectedStation(final String stationId) {
        return selectedStationId != null && selectedStationId.equals(stationId);
    }

    public final Point getDeviceLocation() {
        return deviceLocation;
    }

    public final Location getReferenceLocation() {
        if (fixedLocation != null)
            return fixedLocation;
        else if (deviceLocation != null)
            return Location.coord(deviceLocation);
        else
            return null;
    }

    public final Float getDeviceBearing() {
        return fixedLocation == null ? deviceBearing : null;
    }

    private void startLocationProvider() {
        // determine location providers
        final Criteria accurateCriteria = new Criteria();
        accurateCriteria.setAccuracy(Criteria.ACCURACY_FINE);
        accurateLocationProvider = locationManager.getBestProvider(accurateCriteria, true);
        final Criteria lowPowerCriteria = new Criteria();
        lowPowerCriteria.setPowerRequirement(Criteria.POWER_LOW);
        lowPowerLocationProvider = locationManager.getBestProvider(lowPowerCriteria, true);

        if (accurateLocationProvider != null && accurateLocationProvider.equals(lowPowerLocationProvider))
            accurateLocationProvider = null;

        // request update on location change
        if (accurateLocationProvider != null)
            locationManager.requestLocationUpdates(accurateLocationProvider, Constants.LOCATION_UPDATE_FREQ_MS,
                    Constants.LOCATION_UPDATE_DISTANCE, locationListener);
        if (lowPowerLocationProvider != null)
            locationManager.requestLocationUpdates(lowPowerLocationProvider, Constants.LOCATION_UPDATE_FREQ_MS,
                    Constants.LOCATION_UPDATE_DISTANCE, locationListener);

        // last known location
        final android.location.Location here = determineLastKnownLocation();
        if (here != null)
            locationListener.onLocationChanged(here);

        // display state of location providers
        locationProvidersView.removeAllViews();
        anyProviderEnabled = false;
        for (final String provider : locationManager.getAllProviders()) {
            if (provider.equals(LocationManager.PASSIVE_PROVIDER))
                continue;

            final boolean enabled = locationManager.isProviderEnabled(provider);
            final boolean acquiring = provider.equals(accurateLocationProvider)
                    || provider.equals(lowPowerLocationProvider);
            final View row = (View) getLayoutInflater().inflate(R.layout.stations_location_provider_row, null);
            ((TextView) row.findViewById(R.id.stations_location_provider_row_provider)).setText(provider + ":");
            final TextView enabledView = (TextView) row.findViewById(R.id.stations_location_provider_row_enabled);
            enabledView.setText(enabled
                    ? (acquiring ? R.string.stations_location_provider_acquiring
                    : R.string.stations_location_provider_enabled)
                    : R.string.stations_location_provider_disabled);
            enabledView.setTypeface(acquiring ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            locationProvidersView.addView(row);

            if (enabled)
                anyProviderEnabled = true;
        }
    }

    private void stopLocationProvider() {
        locationManager.removeUpdates(locationListener);
    }

    private android.location.Location determineLastKnownLocation() {
        android.location.Location accurateLocation = null;
        if (accurateLocationProvider != null) {
            accurateLocation = locationManager.getLastKnownLocation(accurateLocationProvider);
            if (accurateLocation != null
                    && (accurateLocation.getLatitude() == 0 && accurateLocation.getLongitude() == 0))
                accurateLocation = null;
        }

        android.location.Location lowPowerLocation = null;
        if (lowPowerLocationProvider != null) {
            lowPowerLocation = locationManager.getLastKnownLocation(lowPowerLocationProvider);
            if (lowPowerLocation != null
                    && (lowPowerLocation.getLatitude() == 0 && lowPowerLocation.getLongitude() == 0))
                lowPowerLocation = null;
        }

        if (lowPowerLocation != null || accurateLocation != null) {
            final long accurateLocationTime = accurateLocation != null ? accurateLocation.getTime() : -1;
            final long lowPowerLocationTime = lowPowerLocation != null ? lowPowerLocation.getTime() : -1;
            final android.location.Location location = lowPowerLocationTime > accurateLocationTime ? lowPowerLocation
                    : accurateLocation;
            if (location != null)
                return location;
        }

        return null;
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions,
            final int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            startLocationProvider();
    }

    public boolean onStationContextMenuItemClick(final int adapterPosition, final NetworkId network,
            final Location station, final @Nullable List<Departure> departures, final int menuItemId) {
        if (menuItemId == R.id.station_context_add_favorite) {
            if (StationsActivity.this.addFavorite(station))
                stationListAdapter.notifyItemChanged(adapterPosition);
            return true;
        } else if (menuItemId == R.id.station_context_remove_favorite) {
            if (StationsActivity.this.removeFavorite(station))
                stationListAdapter.notifyItemChanged(adapterPosition);
            return true;
        } else if (menuItemId == R.id.station_context_add_ignore) {
            if (StationsActivity.this.addIgnore(station))
                stationListAdapter.notifyItemChanged(adapterPosition);
            return true;
        } else if (menuItemId == R.id.station_context_remove_ignore) {
            if (StationsActivity.this.removeIgnore(station))
                stationListAdapter.notifyItemChanged(adapterPosition);
            return true;
        } else if (menuItemId == R.id.station_context_details) {
            StationDetailsActivity.start(StationsActivity.this, network, station, departures);
            return true;
        } else if (menuItemId == R.id.station_context_directions_from) {
            DirectionsActivity.start(StationsActivity.this, station, null, null,
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            return true;
        } else if (menuItemId == R.id.station_context_directions_to) {
            DirectionsActivity.start(StationsActivity.this, null, station, null,
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            return true;
        } else if (menuItemId == R.id.station_context_launcher_shortcut) {
            StationContextMenu.createLauncherShortcutDialog(StationsActivity.this, network, station).show();
            return true;
        } else {
            return false;
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(final android.location.Location here) {
            log.info("Got relevant location: {}", here);

            final double hereLat = here.getLatitude();
            final double hereLon = here.getLongitude();

            deviceLocation = Point.fromDouble(hereLat, hereLon);

            stationListAdapter.setDeviceLocation(here);

            // re-calculate distances for sorting
            if (fixedLocation == null) {
                final float[] distanceBetweenResults = new float[2];

                for (final Station station : stations) {
                    if (station.location.hasLocation()) {
                        android.location.Location.distanceBetween(hereLat, hereLon, station.location.lat / 1E6,
                                station.location.lon / 1E6, distanceBetweenResults);
                        station.setDistanceAndBearing(distanceBetweenResults[0], distanceBetweenResults[1]);
                    }
                }

                stationListAdapter.notifyDataSetChanged();

                handler.post(initStationsRunnable);
            }

            updateGUI();
        }

        public void onStatusChanged(final String provider, final int status, final Bundle extras) {
        }

        public void onProviderEnabled(final String provider) {
        }

        public void onProviderDisabled(final String provider) {
        }
    };

    private final SensorEventListener orientationListener = new SensorEventListener() {
        private final float[] accelerometerValues = new float[3];
        private final float[] magnetometerValues = new float[3];

        private final float accelerometerFactor = 0.2f;
        private final float accelerometerCofactor = 1f - accelerometerFactor;

        private float[] rotationMatrix = new float[9];
        private float[] orientation = new float[3];

        private long lastTime = 0;

        public void onSensorChanged(final SensorEvent event) {
            if (event.sensor == sensorAccelerometer) {
                accelerometerValues[0] = event.values[0] * accelerometerFactor
                        + accelerometerValues[0] * accelerometerCofactor;
                accelerometerValues[1] = event.values[1] * accelerometerFactor
                        + accelerometerValues[1] * accelerometerCofactor;
                accelerometerValues[2] = event.values[2] * accelerometerFactor
                        + accelerometerValues[2] * accelerometerCofactor;
            } else if (event.sensor == sensorMagnetometer) {
                System.arraycopy(event.values, 0, magnetometerValues, 0, event.values.length);
            }

            if (System.currentTimeMillis() - lastTime < 50)
                return;

            final boolean faceDown = accelerometerValues[2] < 0;

            final boolean success = SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerValues,
                    magnetometerValues);
            if (!success)
                return;

            SensorManager.getOrientation(rotationMatrix, orientation);
            final float azimuth = (float) Math.toDegrees(orientation[0]);

            lastTime = System.currentTimeMillis();

            runOnUiThread(new Runnable() {
                public void run() {
                    deviceBearing = azimuth;
                    stationListAdapter.setDeviceBearing(azimuth, faceDown);

                    // refresh compass needles
                    final int childCount = stationList.getChildCount();
                    for (int i = 0; i < childCount; i++) {
                        final View childAt = stationList.getChildAt(i);
                        final View bearingView = childAt.findViewById(R.id.station_entry_bearing);
                        if (bearingView != null)
                            bearingView.invalidate();
                    }
                }
            });
        }

        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
        }
    };

    private class SearchTask extends AsyncTask<String, Void, List<Station>> {
        @Override
        protected List<Station> doInBackground(final String... params) {
            if (params.length != 1)
                throw new IllegalArgumentException();

            final String query = params[0];

            final Builder uriBuilder = NetworkContentProvider.CONTENT_URI.buildUpon();
            uriBuilder.appendPath(network.name());
            uriBuilder.appendQueryParameter(NetworkContentProvider.QUERY_PARAM_Q, query);

            final Cursor cursor = getContentResolver().query(uriBuilder.build(), null, null, null,
                    NetworkContentProvider.KEY_NAME);
            final Matcher mQuery = Pattern.compile("(^|[ -/\\(])" + query, Pattern.CASE_INSENSITIVE).matcher("");
            final NetworkProvider networkProvider = NetworkProviderFactory.provider(network);

            final List<Station> stations = new LinkedList<>();
            if (cursor != null) {
                final int nativeIdColumnIndex = cursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_ID);
                final int localIdColumnIndex = cursor.getColumnIndex(NetworkContentProvider.KEY_LOCAL_ID);
                final int placeColumnIndex = cursor.getColumnIndex(NetworkContentProvider.KEY_PLACE);
                final int nameColumnIndex = cursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_NAME);
                final int latColumnIndex = cursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_LAT);
                final int lonColumnIndex = cursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_LON);
                final int linesColumnIndex = cursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_LINES);

                while (cursor.moveToNext()) {
                    final String id = localIdColumnIndex != -1 ? cursor.getString(localIdColumnIndex)
                            : cursor.getString(nativeIdColumnIndex);
                    final String place = placeColumnIndex != -1 ? cursor.getString(placeColumnIndex) : null;
                    final String name = cursor.getString(nameColumnIndex);

                    if (id.equals(query) || (place != null && mQuery.reset(place).find())
                            || mQuery.reset(name).find()) {
                        final int lat = cursor.getInt(latColumnIndex);
                        final int lon = cursor.getInt(lonColumnIndex);
                        final List<LineDestination> lineDestinations = new LinkedList<>();
                        for (final String lineStr : cursor.getString(linesColumnIndex).split(",")) {
                            if (!lineStr.isEmpty()) {
                                final Product product = Product.fromCode(lineStr.charAt(0));
                                final String label = Strings.emptyToNull(lineStr.substring(1));
                                final Style style = networkProvider.lineStyle(null, product, label);
                                lineDestinations
                                        .add(new LineDestination(new Line(null, null, product, label, style), null));
                            }
                        }
                        final Location location = new Location(LocationType.STATION, id, lat, lon, place, name);
                        stations.add(new Station(network, location, lineDestinations));
                    }
                }

                cursor.close();
            } else {
                try {
                    final SuggestLocationsResult result = networkProvider.suggestLocations(query);
                    if (result.status == SuggestLocationsResult.Status.OK)
                        for (final Location l : result.getLocations())
                            if (l.type == LocationType.STATION)
                                stations.add(new Station(network, l));
                } catch (final IOException x) {
                    x.printStackTrace();
                }
            }

            return stations;
        }
    }
}
