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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.LocationAware;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.OeffiMapView;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.TripAware;
import de.schildbach.oeffi.directions.TimeSpec.DepArr;
import de.schildbach.oeffi.stations.LineView;
import de.schildbach.oeffi.stations.NetworkContentProvider;
import de.schildbach.oeffi.stations.StationContextMenu;
import de.schildbach.oeffi.stations.StationDetailsActivity;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.LocationHelper;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.ToggleImageButton;
import de.schildbach.oeffi.util.ToggleImageButton.OnCheckedChangeListener;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Fare;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Position;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Style;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.dto.Trip.Individual;
import de.schildbach.pte.dto.Trip.Leg;
import de.schildbach.pte.dto.Trip.Public;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.location.Criteria;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TableLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;

public class TripDetailsActivity extends OeffiActivity implements LocationListener, LocationAware {
    private static final String INTENT_EXTRA_NETWORK = TripDetailsActivity.class.getName() + ".network";
    private static final String INTENT_EXTRA_TRIP = TripDetailsActivity.class.getName() + ".trip";

    protected SharedPreferences prefs;

    public static void start(final Context context, final NetworkId network, final Trip trip) {
        final Intent intent = new Intent(context, TripDetailsActivity.class);
        intent.putExtra(INTENT_EXTRA_NETWORK, checkNotNull(network));
        intent.putExtra(INTENT_EXTRA_TRIP, checkNotNull(trip));
        context.startActivity(intent);
    }

    private LayoutInflater inflater;
    private Resources res;
    private int colorText;
    private int colorTextGhosted;
    private int colorTextHighlight;
    private int colorGrey600;
    private DisplayMetrics displayMetrics;
    private LocationManager locationManager;
    private BroadcastReceiver tickReceiver;

    private ViewGroup legsGroup;
    private OeffiMapView mapView;
    private ToggleImageButton trackButton;

    private NetworkId network;
    private Trip trip;
    private Date highlightedTime;
    private Location highlightedLocation;
    private Point location;
    private int selectedLegIndex = -1;

    private final Map<Leg, Boolean> legExpandStates = new HashMap<>();
    private Intent scheduleTripIntent;

    private static final int LEGSGROUP_INSERT_INDEX = 2;

    private static final Logger log = LoggerFactory.getLogger(TripDetailsActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        inflater = getLayoutInflater();
        res = getResources();
        colorText = res.getColor(R.color.text);
        colorTextGhosted = res.getColor(R.color.text_ghosted);
        colorTextHighlight = res.getColor(R.color.text_highlight);
        colorGrey600 = res.getColor(R.color.grey600);
        displayMetrics = res.getDisplayMetrics();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);

        final Intent intent = getIntent();
        network = (NetworkId) intent.getSerializableExtra(INTENT_EXTRA_NETWORK);
        trip = (Trip) intent.getSerializableExtra(INTENT_EXTRA_TRIP);

        log.info("Showing trip from {} to {}", trip.from, trip.to);

        // try to build up paths
        for (final Leg leg : trip.legs) {
            if (leg.path == null) {
                leg.path = new ArrayList<>();

                if (leg.departure != null) {
                    final Point departurePoint = pointFromLocation(leg.departure);
                    if (departurePoint != null)
                        leg.path.add(departurePoint);
                }

                if (leg instanceof Public) {
                    final Public publicLeg = (Public) leg;
                    final List<Stop> intermediateStops = publicLeg.intermediateStops;

                    if (intermediateStops != null) {
                        for (final Stop stop : intermediateStops) {
                            final Point stopPoint = pointFromLocation(stop.location);
                            if (stopPoint != null)
                                leg.path.add(stopPoint);
                        }
                    }
                }

                if (leg.arrival != null) {
                    final Point arrivalPoint = pointFromLocation(leg.arrival);
                    if (arrivalPoint != null)
                        leg.path.add(arrivalPoint);
                }
            }
        }

        scheduleTripIntent = scheduleTripIntent(trip);

        setContentView(R.layout.directions_trip_details_content);
        final MyActionBar actionBar = getMyActionBar();
        setPrimaryColor(R.color.action_bar_background_directions);
        actionBar.setPrimaryTitle(getTitle());
        actionBar.setBack(v -> finish());

        // action bar secondary title
        final StringBuilder secondaryTitle = new StringBuilder();
        final Long duration = trip.getPublicDuration();
        if (duration != null)
            secondaryTitle.append(getString(R.string.directions_trip_details_duraton, formatTimeSpan(duration)));

        if (trip.numChanges != null && trip.numChanges > 0) {
            if (secondaryTitle.length() > 0)
                secondaryTitle.append(" / ");
            secondaryTitle.append(getString(R.string.directions_trip_details_num_changes, trip.numChanges));
        }

        actionBar.setSecondaryTitle(secondaryTitle.length() > 0 ? secondaryTitle : null);

        // action bar buttons
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            trackButton = actionBar.addToggleButton(R.drawable.ic_location_white_24dp,
                    R.string.directions_trip_details_action_track_title);
            trackButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    final String provider = requestLocationUpdates();
                    if (provider != null) {
                        final android.location.Location lastKnownLocation = locationManager
                                .getLastKnownLocation(provider);
                        if (lastKnownLocation != null
                                && (lastKnownLocation.getLatitude() != 0 || lastKnownLocation.getLongitude() != 0))
                            location = LocationHelper.locationToPoint(lastKnownLocation);
                        else
                            location = null;
                        mapView.setLocationAware(TripDetailsActivity.this);
                    }
                } else {
                    locationManager.removeUpdates(TripDetailsActivity.this);
                    location = null;

                    mapView.setLocationAware(null);
                }

                mapView.zoomToAll();
                updateGUI();
            });
        }
        actionBar.addButton(R.drawable.ic_share_white_24dp, R.string.directions_trip_details_action_share_title)
                .setOnClickListener(v -> {
                    final PopupMenu popupMenu = new PopupMenu(TripDetailsActivity.this, v);
                    popupMenu.inflate(R.menu.directions_trip_details_action_share);
                    popupMenu.setOnMenuItemClickListener(item -> {
                        if (item.getItemId() == R.id.directions_trip_details_action_share_short) {
                            shareTripShort();
                            return true;
                        } else if (item.getItemId() == R.id.directions_trip_details_action_share_long) {
                            shareTripLong();
                            return true;
                        } else {
                            return false;
                        }
                    });
                    popupMenu.show();
                });
        if (getPackageManager().resolveActivity(scheduleTripIntent, 0) != null) {
            actionBar.addButton(R.drawable.ic_today_white_24dp, R.string.directions_trip_details_action_calendar_title)
                    .setOnClickListener(v -> startActivity(scheduleTripIntent));
        }

        legsGroup = (ViewGroup) findViewById(R.id.directions_trip_details_legs_group);

        updateLocations();
        updateFares(trip.fares);
        int i = LEGSGROUP_INSERT_INDEX;
        for (final Leg leg : trip.legs) {
            final View row;
            if (leg instanceof Public)
                row = inflater.inflate(R.layout.directions_trip_details_public_entry, null);
            else
                row = inflater.inflate(R.layout.directions_trip_details_individual_entry, null);
            legsGroup.addView(row, i++);
        }
        ((TextView) findViewById(R.id.directions_trip_details_footer))
                .setText(Html.fromHtml(getString(R.string.directions_trip_details_realtime)));

        final TextView disclaimerSourceView = (TextView) findViewById(R.id.directions_trip_details_disclaimer_source);
        updateDisclaimerSource(disclaimerSourceView, network.name(), null);

        mapView = (OeffiMapView) findViewById(R.id.directions_trip_details_map);
        mapView.setTripAware(new TripAware() {
            public Trip getTrip() {
                return trip;
            }

            public void selectLeg(final int partIndex) {
                selectedLegIndex = partIndex;
                mapView.zoomToAll();
            }

            public boolean hasSelection() {
                return selectedLegIndex != -1;
            }

            public boolean isSelectedLeg(final Leg part) {
                if (!hasSelection())
                    return false;

                return trip.legs.get(selectedLegIndex).equals(part);
            }
        });
        ((TextView) findViewById(R.id.directions_trip_details_map_disclaimer))
                .setText(mapView.getTileProvider().getTileSource().getCopyrightNotice());
    }

    @Override
    protected void onStart() {
        super.onStart();

        // regular refresh
        tickReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateGUI();
            }
        };
        registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

        updateGUI();
        updateFragments();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (tickReceiver != null) {
            unregisterReceiver(tickReceiver);
            tickReceiver = null;
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        locationManager.removeUpdates(TripDetailsActivity.this);

        super.onDestroy();
    }

    @Override
    public void onAttachedToWindow() {
        if(prefs.getBoolean(Constants.PREFS_KEY_SHOW_TRIP_DETAILS_ON_LOCKSCREEN, true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
    }

    @Override
    public void onConfigurationChanged(final Configuration config) {
        super.onConfigurationChanged(config);

        updateFragments();
    }

    private String requestLocationUpdates() {
        final Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        final String provider = locationManager.getBestProvider(criteria, true);
        if (provider != null) {
            locationManager.requestLocationUpdates(provider, 5000, 5, TripDetailsActivity.this);
            return provider;
        } else {
            new Toast(this).toast(R.string.acquire_location_no_provider);
            trackButton.setChecked(false);

            return null;
        }
    }

    public void onLocationChanged(final android.location.Location location) {
        this.location = LocationHelper.locationToPoint(location);

        updateGUI();
    }

    public void onProviderEnabled(final String provider) {
    }

    public void onProviderDisabled(final String provider) {
        locationManager.removeUpdates(TripDetailsActivity.this);

        final String newProvider = requestLocationUpdates();
        if (newProvider == null)
            mapView.setLocationAware(null);
    }

    public void onStatusChanged(final String provider, final int status, final Bundle extras) {
    }

    public final Point getDeviceLocation() {
        return location;
    }

    public final Location getReferenceLocation() {
        return null;
    }

    public final Float getDeviceBearing() {
        return null;
    }

    private void updateFragments() {
        updateFragments(R.id.directions_trip_details_list_frame, R.id.directions_trip_details_map_frame);
    }

    private void updateGUI() {
        final Date now = new Date();
        updateHighlightedTime(now);
        updateHighlightedLocation();

        int i = LEGSGROUP_INSERT_INDEX;
        for (final Leg leg : trip.legs) {
            if (leg instanceof Public)
                updatePublicLeg(legsGroup.getChildAt(i), (Public) leg, now);
            else
                updateIndividualLeg(legsGroup.getChildAt(i), (Individual) leg);
            i++;
        }
    }

    private void updateHighlightedTime(final Date now) {
        highlightedTime = null;

        final Date firstPublicLegDepartureTime = trip.getFirstPublicLegDepartureTime();
        if (firstPublicLegDepartureTime == null
                || firstPublicLegDepartureTime.getTime() - now.getTime() > 10 * DateUtils.MINUTE_IN_MILLIS)
            return;

        for (final Trip.Leg leg : trip.legs) {
            if (leg instanceof Trip.Public) {
                final Trip.Public publicLeg = (Trip.Public) leg;
                final Date departureTime = publicLeg.getDepartureTime();
                final Date arrivalTime = publicLeg.getArrivalTime();

                if (departureTime.after(now)) {
                    highlightedTime = departureTime;
                    return;
                }

                final List<Stop> intermediateStops = publicLeg.intermediateStops;
                if (intermediateStops != null) {
                    for (final Stop stop : intermediateStops) {
                        Date stopTime = stop.getArrivalTime();
                        if (stopTime == null)
                            stopTime = stop.getDepartureTime();

                        if (stopTime != null && stopTime.after(now)) {
                            highlightedTime = stopTime;
                            return;
                        }
                    }
                }

                if (arrivalTime.after(now)) {
                    highlightedTime = arrivalTime;
                    return;
                }
            }
        }
    }

    private void updateHighlightedLocation() {
        highlightedLocation = null;

        if (location != null) {
            float minDistance = Float.MAX_VALUE;

            final float[] distanceBetweenResults = new float[1];

            for (final Trip.Leg leg : trip.legs) {
                if (leg instanceof Trip.Public) {
                    final Trip.Public publicLeg = (Trip.Public) leg;

                    if (publicLeg.departure.hasCoord()) {
                        android.location.Location.distanceBetween(publicLeg.departure.getLatAsDouble(),
                                publicLeg.departure.getLonAsDouble(), location.getLatAsDouble(),
                                location.getLonAsDouble(), distanceBetweenResults);
                        final float distance = distanceBetweenResults[0];
                        if (distance < minDistance) {
                            minDistance = distance;
                            highlightedLocation = publicLeg.departure;
                        }
                    }

                    final List<Stop> intermediateStops = publicLeg.intermediateStops;
                    if (intermediateStops != null) {
                        for (final Stop stop : intermediateStops) {
                            if (stop.location.hasCoord()) {
                                android.location.Location.distanceBetween(stop.location.getLatAsDouble(),
                                        stop.location.getLonAsDouble(), location.getLatAsDouble(),
                                        location.getLonAsDouble(), distanceBetweenResults);
                                final float distance = distanceBetweenResults[0];
                                if (distance < minDistance) {
                                    minDistance = distance;
                                    highlightedLocation = stop.location;
                                }
                            }
                        }
                    }

                    if (publicLeg.arrival.hasCoord()) {
                        android.location.Location.distanceBetween(publicLeg.arrival.getLatAsDouble(),
                                publicLeg.arrival.getLonAsDouble(), location.getLatAsDouble(),
                                location.getLonAsDouble(), distanceBetweenResults);
                        final float distance = distanceBetweenResults[0];
                        if (distance < minDistance) {
                            minDistance = distance;
                            highlightedLocation = publicLeg.arrival;
                        }
                    }
                }
            }
        }
    }

    private void updateLocations() {
        final LocationTextView fromView = (LocationTextView) findViewById(R.id.directions_trip_details_location_from);
        fromView.setLabel(R.string.directions_overview_from);
        fromView.setLocation(trip.from);
        final LocationTextView toView = (LocationTextView) findViewById(R.id.directions_trip_details_location_to);
        toView.setLabel(R.string.directions_overview_to);
        toView.setLocation(trip.to);
    }

    private void updateFares(final List<Fare> fares) {
        final TableLayout faresTable = (TableLayout) findViewById(R.id.directions_trip_details_fares);
        if (trip.fares != null && !trip.fares.isEmpty()) {
            faresTable.setVisibility(View.VISIBLE);

            final String[] fareTypes = res.getStringArray(R.array.fare_types);

            int i = 0;
            for (final Fare fare : fares) {
                final View fareRow = inflater.inflate(R.layout.directions_trip_details_fares_row, null);
                ((TextView) fareRow.findViewById(R.id.directions_trip_details_fare_entry_row_type))
                        .setText(fareTypes[fare.type.ordinal()]);
                ((TextView) fareRow.findViewById(R.id.directions_trip_details_fare_entry_row_name)).setText(fare.name);
                ((TextView) fareRow.findViewById(R.id.directions_trip_details_fare_entry_row_fare))
                        .setText(String.format(Locale.US, "%s%.2f", fare.currency.getSymbol(), fare.fare));
                final TextView unitView = (TextView) fareRow
                        .findViewById(R.id.directions_trip_details_fare_entry_row_unit);
                if (fare.units != null && fare.unitName != null)
                    unitView.setText(String.format("(%s %s)", fare.units, fare.unitName));
                else if (fare.units == null && fare.unitName == null)
                    unitView.setText(null);
                else
                    unitView.setText(String.format("(%s)", MoreObjects.firstNonNull(fare.units, fare.unitName)));
                faresTable.addView(fareRow, i++);
            }
        } else {
            faresTable.setVisibility(View.GONE);
        }
    }

    private void updatePublicLeg(final View row, final Public leg, final Date now) {
        final Location destination = leg.destination;
        final String destinationName = destination != null ? destination.uniqueShortName() : null;
        final boolean showDestination = destinationName != null;
        final boolean showAccessibility = leg.line.hasAttr(Line.Attr.WHEEL_CHAIR_ACCESS);
        final boolean showBicycleCarriage = leg.line.hasAttr(Line.Attr.BICYCLE_CARRIAGE);
        final List<Stop> intermediateStops = leg.intermediateStops;

        final LineView lineView = (LineView) row.findViewById(R.id.directions_trip_details_public_entry_line);
        lineView.setLine(leg.line);
        if (showDestination || showAccessibility)
            lineView.setMaxWidth(res.getDimensionPixelSize(R.dimen.line_max_width));

        final LinearLayout lineGroup = (LinearLayout) row
                .findViewById(R.id.directions_trip_details_public_entry_line_group);
        if (showDestination)
            lineGroup.setBaselineAlignedChildIndex(0);
        else if (showAccessibility)
            lineGroup.setBaselineAlignedChildIndex(1);
        else if (showBicycleCarriage)
            lineGroup.setBaselineAlignedChildIndex(2);

        final TextView destinationView = (TextView) row
                .findViewById(R.id.directions_trip_details_public_entry_destination);
        if (destination != null) {
            destinationView.setVisibility(View.VISIBLE);
            destinationView.setText(Constants.DESTINATION_ARROW_PREFIX + destinationName);
            destinationView.setOnClickListener(destination.hasId() ? new LocationClickListener(destination) : null);
        } else {
            destinationView.setVisibility(View.GONE);
        }

        final View accessibilityView = row.findViewById(R.id.directions_trip_details_public_entry_accessibility);
        accessibilityView.setVisibility(showAccessibility ? View.VISIBLE : View.GONE);

        final View bicycleCarriageView = row.findViewById(R.id.directions_trip_details_public_entry_bicycle_carriage);
        bicycleCarriageView.setVisibility(showBicycleCarriage ? View.VISIBLE : View.GONE);

        final ToggleImageButton expandButton = (ToggleImageButton) row
                .findViewById(R.id.directions_trip_details_public_entry_expand);
        final Boolean checked = legExpandStates.get(leg);
        expandButton
                .setVisibility(intermediateStops != null && !intermediateStops.isEmpty() ? View.VISIBLE : View.GONE);
        expandButton.setChecked(checked != null ? checked : false);
        expandButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            legExpandStates.put(leg, isChecked);
            updateGUI();
        });

        final TableLayout stopsView = (TableLayout) row.findViewById(R.id.directions_trip_details_public_entry_stops);
        stopsView.removeAllViews();
        final CollapseColumns collapseColumns = new CollapseColumns();
        collapseColumns.dateChanged(now);

        final View departureRow = stopRow(PearlView.Type.DEPARTURE, leg.departureStop, leg.line.style, highlightedTime,
                leg.departureStop.location.equals(highlightedLocation), now, collapseColumns);
        stopsView.addView(departureRow);

        if (intermediateStops != null) {
            if (expandButton.isChecked()) {
                for (final Stop stop : intermediateStops) {
                    final boolean hasStopTime = stop.getArrivalTime() != null || stop.getDepartureTime() != null;

                    final View stopRow = stopRow(hasStopTime ? PearlView.Type.INTERMEDIATE : PearlView.Type.PASSING,
                            stop, leg.line.style, highlightedTime, stop.location.equals(highlightedLocation), now,
                            collapseColumns);
                    stopsView.addView(stopRow);
                }
            } else {
                int numIntermediateStops = 0;
                for (final Stop stop : intermediateStops) {
                    final boolean hasStopTime = stop.getArrivalTime() != null || stop.getDepartureTime() != null;
                    if (hasStopTime)
                        numIntermediateStops++;
                }

                if (numIntermediateStops > 0) {
                    final View collapsedIntermediateStopsRow = collapsedIntermediateStopsRow(numIntermediateStops,
                            leg.line.style);
                    stopsView.addView(collapsedIntermediateStopsRow);
                    collapsedIntermediateStopsRow.setOnClickListener(v -> expandButton.setChecked(true));
                }
            }
        }

        final View arrivalRow = stopRow(PearlView.Type.ARRIVAL, leg.arrivalStop, leg.line.style, highlightedTime,
                leg.arrivalStop.location.equals(highlightedLocation), now, collapseColumns);
        stopsView.addView(arrivalRow);

        stopsView.setColumnCollapsed(1, collapseColumns.collapseDateColumn);
        stopsView.setColumnCollapsed(3, collapseColumns.collapseDelayColumn);
        stopsView.setColumnCollapsed(4, collapseColumns.collapsePositionColumn);

        final TextView messageView = (TextView) row.findViewById(R.id.directions_trip_details_public_entry_message);
        final String message = leg.message != null ? leg.message : leg.line.message;
        messageView.setText(message);
        messageView.setVisibility(message != null ? View.VISIBLE : View.GONE);
    }

    private void updateIndividualLeg(final View row, final Individual leg) {
        final TextView textView = (TextView) row.findViewById(R.id.directions_trip_details_individual_entry_text);
        final String distanceStr = leg.distance != 0 ? "(" + leg.distance + "m) " : "";
        final int textResId, iconResId;
        if (leg.type == Individual.Type.WALK) {
            textResId = R.string.directions_trip_details_walk;
            iconResId = R.drawable.ic_directions_walk_grey600_24dp;
        } else if (leg.type == Individual.Type.BIKE) {
            textResId = R.string.directions_trip_details_bike;
            iconResId = R.drawable.ic_directions_bike_grey600_24dp;
        } else if (leg.type == Individual.Type.CAR) {
            textResId = R.string.directions_trip_details_car;
            iconResId = R.drawable.ic_local_taxi_grey600_24dp;
        } else if (leg.type == Individual.Type.TRANSFER) {
            textResId = R.string.directions_trip_details_transfer;
            iconResId = R.drawable.ic_local_taxi_grey600_24dp;
        } else {
            throw new IllegalStateException("unknown type: " + leg.type);
        }
        textView.setText(Html.fromHtml(getString(textResId, leg.min, distanceStr, leg.arrival.uniqueShortName())));
        textView.setCompoundDrawablesWithIntrinsicBounds(iconResId, 0, 0, 0);

        final ImageButton mapView = (ImageButton) row.findViewById(R.id.directions_trip_details_individual_entry_map);
        mapView.setVisibility(View.GONE);
        mapView.setOnClickListener(null);
        if (leg.arrival.hasCoord()) {
            mapView.setVisibility(View.VISIBLE);
            mapView.setOnClickListener(new MapClickListener(leg.arrival));
        } else if (leg.arrival.hasId()) {
            final Point point = pointFromStationDb(leg.arrival.id);
            if (point != null) {
                mapView.setVisibility(View.VISIBLE);
                mapView.setOnClickListener(new MapClickListener(new Location(LocationType.STATION, leg.arrival.id,
                        point, leg.arrival.place, leg.arrival.name)));
            }
        }
    }

    private class CollapseColumns {
        private boolean collapseDateColumn = true;
        private boolean collapsePositionColumn = true;
        private boolean collapseDelayColumn = true;
        private Calendar c = new GregorianCalendar();

        public boolean dateChanged(final Date time) {
            final int oldYear = c.get(Calendar.YEAR);
            final int oldDayOfYear = c.get(Calendar.DAY_OF_YEAR);
            c.setTime(time);
            return c.get(Calendar.YEAR) != oldYear || c.get(Calendar.DAY_OF_YEAR) != oldDayOfYear;
        }
    }

    private View stopRow(final PearlView.Type pearlType, final Stop stop, final Style style, final Date highlightedTime,
            final boolean highlightLocation, final Date now, final CollapseColumns collapseColumns) {
        final View row = inflater.inflate(R.layout.directions_trip_details_public_entry_stop, null);

        final boolean isTimePredicted;
        final Date time;
        final Long delay;
        final boolean isCancelled;
        final boolean isPositionPredicted;
        final Position position;

        if (pearlType == PearlView.Type.DEPARTURE
                || ((pearlType == PearlView.Type.INTERMEDIATE || pearlType == PearlView.Type.PASSING)
                        && stop.plannedArrivalTime == null)) {
            isTimePredicted = stop.isDepartureTimePredicted();
            time = stop.getDepartureTime();
            delay = stop.getDepartureDelay();
            isCancelled = stop.departureCancelled;

            isPositionPredicted = stop.isDeparturePositionPredicted();
            position = stop.getDeparturePosition();
        } else if (pearlType == PearlView.Type.ARRIVAL
                || ((pearlType == PearlView.Type.INTERMEDIATE || pearlType == PearlView.Type.PASSING)
                        && stop.plannedArrivalTime != null)) {
            isTimePredicted = stop.isArrivalTimePredicted();
            time = stop.getArrivalTime();
            delay = stop.getArrivalDelay();
            isCancelled = stop.arrivalCancelled;

            isPositionPredicted = stop.isArrivalPositionPredicted();
            position = stop.getArrivalPosition();
        } else {
            throw new IllegalStateException("cannot handle: " + pearlType);
        }

        // name
        final TextView stopNameView = (TextView) row.findViewById(R.id.directions_trip_details_public_entry_stop_name);
        stopNameView.setText(stop.location.uniqueShortName());
        setStrikeThru(stopNameView, isCancelled);
        if (highlightLocation) {
            stopNameView.setTextColor(colorTextHighlight);
            stopNameView.setTypeface(null, Typeface.BOLD);
        } else if (pearlType == PearlView.Type.DEPARTURE || pearlType == PearlView.Type.ARRIVAL) {
            stopNameView.setTextColor(colorText);
            stopNameView.setTypeface(null, Typeface.BOLD);
        } else if (pearlType == PearlView.Type.PASSING) {
            stopNameView.setTextColor(colorTextGhosted);
            stopNameView.setTypeface(null, Typeface.NORMAL);
        } else {
            stopNameView.setTextColor(colorText);
            stopNameView.setTypeface(null, Typeface.NORMAL);
        }
        if (stop.location.hasId())
            stopNameView.setOnClickListener(new StopClickListener(stop));
        else
            stopNameView.setOnClickListener(null);

        // pearl
        final PearlView pearlView = (PearlView) row.findViewById(R.id.directions_trip_details_public_entry_stop_pearl);
        pearlView.setType(pearlType);
        pearlView.setStyle(style);
        pearlView.setFontMetrics(stopNameView.getPaint().getFontMetrics());

        // time
        final TextView stopDateView = (TextView) row.findViewById(R.id.directions_trip_details_public_entry_stop_date);
        final TextView stopTimeView = (TextView) row.findViewById(R.id.directions_trip_details_public_entry_stop_time);
        stopDateView.setText(null);
        stopTimeView.setText(null);
        boolean highlightTime = false;
        if (time != null) {
            if (collapseColumns.dateChanged(time)) {
                stopDateView.setText(Formats.formatDate(TripDetailsActivity.this, now.getTime(), time.getTime(), true,
                        res.getString(R.string.time_today_abbrev)));
                collapseColumns.collapseDateColumn = false;
            }
            stopTimeView.setText(Formats.formatTime(TripDetailsActivity.this, time.getTime()));
            setStrikeThru(stopTimeView, isCancelled);
            highlightTime = time.equals(highlightedTime);
        }
        final int stopTimeColor = highlightTime ? colorTextHighlight : colorText;
        stopDateView.setTextColor(stopTimeColor);
        stopTimeView.setTextColor(stopTimeColor);
        stopDateView.setTypeface(null, (highlightTime ? Typeface.BOLD : 0) + (isTimePredicted ? Typeface.ITALIC : 0));
        stopTimeView.setTypeface(null, (highlightTime || pearlType != PearlView.Type.INTERMEDIATE ? Typeface.BOLD : 0)
                + (isTimePredicted ? Typeface.ITALIC : 0));

        // delay
        final TextView stopDelayView = (TextView) row
                .findViewById(R.id.directions_trip_details_public_entry_stop_delay);
        if (delay != null) {
            final long delayMins = delay / DateUtils.MINUTE_IN_MILLIS;
            if (delayMins != 0) {
                collapseColumns.collapseDelayColumn = false;
                stopDelayView.setText(String.format("(%+d)", delayMins));
                stopDelayView.setTypeface(Typeface.DEFAULT, isTimePredicted ? Typeface.ITALIC : Typeface.NORMAL);
            }
        }

        // position
        final TextView stopPositionView = (TextView) row
                .findViewById(R.id.directions_trip_details_public_entry_stop_position);
        if (position != null && !isCancelled) {
            collapseColumns.collapsePositionColumn = false;
            final SpannableStringBuilder positionStr = new SpannableStringBuilder(position.name);
            if (position.section != null) {
                final int sectionStart = positionStr.length();
                positionStr.append(position.section);
                positionStr.setSpan(new RelativeSizeSpan(0.85f), sectionStart, positionStr.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            stopPositionView.setText(positionStr);
            stopPositionView.setTypeface(null, Typeface.BOLD + (isPositionPredicted ? Typeface.ITALIC : 0));
            final int padding = (int) (2 * displayMetrics.density);
            stopPositionView.setBackgroundDrawable(new ColorDrawable(colorGrey600));
            stopPositionView.setTextColor(Color.WHITE);
            stopPositionView.setPadding(padding, 0, padding, 0);
        }

        return row;
    }

    private View collapsedIntermediateStopsRow(final int numIntermediateStops, final Style style) {
        final View row = inflater.inflate(R.layout.directions_trip_details_public_entry_collapsed, null);

        // message
        final TextView stopNameView = (TextView) row
                .findViewById(R.id.directions_trip_details_public_entry_collapsed_message);
        stopNameView.setText(
                res.getQuantityString(R.plurals.directions_trip_details_public_entry_collapsed_intermediate_stops,
                        numIntermediateStops, numIntermediateStops));
        stopNameView.setTextColor(colorTextGhosted);

        // pearl
        final PearlView pearlView = (PearlView) row
                .findViewById(R.id.directions_trip_details_public_entry_collapsed_pearl);
        pearlView.setType(PearlView.Type.PASSING);
        pearlView.setStyle(style);
        pearlView.setFontMetrics(stopNameView.getPaint().getFontMetrics());

        return row;
    }

    private void setStrikeThru(final TextView view, final boolean strikeThru) {
        if (strikeThru)
            view.setPaintFlags(view.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        else
            view.setPaintFlags(view.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
    }

    private class LocationClickListener implements android.view.View.OnClickListener {
        private final Location location;

        public LocationClickListener(final Location location) {
            this.location = location;
        }

        public void onClick(final View v) {
            final PopupMenu contextMenu = new StationContextMenu(TripDetailsActivity.this, v, network, location, null,
                    false, false, true, false, false);
            contextMenu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.station_context_details) {
                    StationDetailsActivity.start(TripDetailsActivity.this, network, location);
                    return true;
                } else {
                    return false;
                }
            });
            contextMenu.show();
        }
    }

    private class StopClickListener implements android.view.View.OnClickListener {
        private final Stop stop;

        public StopClickListener(final Stop stop) {
            this.stop = stop;
        }

        public void onClick(final View v) {
            final PopupMenu contextMenu = new StationContextMenu(TripDetailsActivity.this, v, network, stop.location,
                    null, false, false, true, true, false);
            contextMenu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.station_context_details) {
                    StationDetailsActivity.start(TripDetailsActivity.this, network, stop.location);
                    return true;
                } else if (item.getItemId() == R.id.station_context_directions_from) {
                    final Date arrivalTime = stop.getArrivalTime();
                    final TimeSpec.Absolute time = new TimeSpec.Absolute(DepArr.DEPART,
                            arrivalTime != null ? arrivalTime.getTime() : stop.getDepartureTime().getTime());
                    DirectionsActivity.start(TripDetailsActivity.this, stop.location, trip.to, time,
                            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    return true;
                } else if (item.getItemId() == R.id.station_context_directions_to) {
                    final Date arrivalTime = stop.getArrivalTime();
                    final TimeSpec.Absolute time = new TimeSpec.Absolute(DepArr.ARRIVE,
                            arrivalTime != null ? arrivalTime.getTime() : stop.getDepartureTime().getTime());
                    DirectionsActivity.start(TripDetailsActivity.this, trip.from, stop.location, time,
                            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    return true;
                } else {
                    return false;
                }
            });
            contextMenu.show();
        }
    }

    private class MapClickListener implements android.view.View.OnClickListener {
        private final Location location;

        public MapClickListener(final Location location) {
            this.location = location;
        }

        public void onClick(final View v) {
            final PopupMenu popupMenu = new PopupMenu(TripDetailsActivity.this, v);
            StationContextMenu.prepareMapMenu(TripDetailsActivity.this, popupMenu.getMenu(), network, location);
            popupMenu.show();
        }
    }

    private Point pointFromStationDb(final String id) {
        final Cursor cursor = getContentResolver().query(
                NetworkContentProvider.CONTENT_URI.buildUpon().appendPath(network.name()).build(),
                new String[] { NetworkContentProvider.KEY_LAT, NetworkContentProvider.KEY_LON },
                NetworkContentProvider.KEY_ID + "=?", new String[] { id }, null);
        if (cursor == null)
            return null;

        final Point point;

        if (cursor.moveToFirst()) {
            final int latColumnIndex = cursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_LAT);
            final int lonColumnIndex = cursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_LON);
            point = Point.from1E6(cursor.getInt(latColumnIndex), cursor.getInt(lonColumnIndex));
        } else {
            point = null;
        }

        cursor.close();

        return point;
    }

    private Point pointFromStationDb(final String place, final String name) {
        final Cursor cursor = getContentResolver().query(
                NetworkContentProvider.CONTENT_URI.buildUpon().appendPath(network.name())
                        .appendQueryParameter(NetworkContentProvider.KEY_PLACE, place != null ? place : "")
                        .appendQueryParameter(NetworkContentProvider.KEY_NAME, name != null ? name : "").build(),
                new String[] { NetworkContentProvider.KEY_LAT, NetworkContentProvider.KEY_LON }, null, null, null);
        if (cursor == null)
            return null;

        final Point point;

        if (cursor.moveToFirst()) {
            final int latColumnIndex = cursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_LAT);
            final int lonColumnIndex = cursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_LON);
            point = Point.from1E6(cursor.getInt(latColumnIndex), cursor.getInt(lonColumnIndex));
        } else {
            point = null;
        }

        cursor.close();

        return point;
    }

    private void shareTripShort() {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, tripToShortText(trip));
        startActivity(
                Intent.createChooser(intent, getString(R.string.directions_trip_details_action_share_short_title)));

    }

    private void shareTripLong() {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.directions_trip_details_text_long_title,
                trip.from.uniqueShortName(), trip.to.uniqueShortName()));
        intent.putExtra(Intent.EXTRA_TEXT, tripToLongText(trip));
        startActivity(
                Intent.createChooser(intent, getString(R.string.directions_trip_details_action_share_long_title)));
    }

    private Intent scheduleTripIntent(final Trip trip) {
        final Intent intent = new Intent(Intent.ACTION_INSERT);
        intent.setData(CalendarContract.Events.CONTENT_URI);
        intent.putExtra(CalendarContract.Events.TITLE, getString(R.string.directions_trip_details_text_long_title,
                trip.from.uniqueShortName(), trip.to.uniqueShortName()));
        intent.putExtra(CalendarContract.Events.DESCRIPTION, tripToLongText(trip));
        intent.putExtra(CalendarContract.Events.EVENT_LOCATION, trip.from.uniqueShortName());
        final Date firstDepartureTime = trip.getFirstDepartureTime();
        if (firstDepartureTime != null)
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, firstDepartureTime.getTime());
        final Date lastArrivalTime = trip.getLastArrivalTime();
        if (lastArrivalTime != null)
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, lastArrivalTime.getTime());
        intent.putExtra(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_DEFAULT);
        intent.putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);
        return intent;
    }

    private String tripToShortText(final Trip trip) {
        final java.text.DateFormat dateFormat = DateFormat.getDateFormat(TripDetailsActivity.this);
        final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(TripDetailsActivity.this);

        final Public firstPublicLeg = trip.getFirstPublicLeg();
        final Public lastPublicLeg = trip.getLastPublicLeg();
        if (firstPublicLeg == null || lastPublicLeg == null)
            return null;

        final String departureDateStr = dateFormat.format(firstPublicLeg.getDepartureTime(true));
        final String departureTimeStr = timeFormat.format(firstPublicLeg.getDepartureTime(true));
        final String departureLineStr = firstPublicLeg.line.label;
        final String departureNameStr = firstPublicLeg.departure.uniqueShortName();

        final String arrivalDateStr = dateFormat.format(lastPublicLeg.getArrivalTime(true));
        final String arrivalTimeStr = timeFormat.format(lastPublicLeg.getArrivalTime(true));
        final String arrivalLineStr = lastPublicLeg.line.label;
        final String arrivalNameStr = lastPublicLeg.arrival.uniqueShortName();

        return getString(R.string.directions_trip_details_text_short, departureDateStr, departureTimeStr,
                departureLineStr, departureNameStr, arrivalDateStr, arrivalTimeStr, arrivalLineStr, arrivalNameStr);
    }

    private String tripToLongText(final Trip trip) {
        final java.text.DateFormat dateFormat = DateFormat.getDateFormat(TripDetailsActivity.this);
        final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(TripDetailsActivity.this);

        final StringBuilder description = new StringBuilder();

        for (final Trip.Leg leg : trip.legs) {
            final String legStr;

            if (leg instanceof Public) {
                final Public publicLeg = (Public) leg;

                final String lineStr = publicLeg.line.label;
                final Location lineDestination = publicLeg.destination;
                final String lineDestinationStr = lineDestination != null
                        ? " " + Constants.CHAR_RIGHTWARDS_ARROW + " " + lineDestination.uniqueShortName() : "";

                final String departureDateStr = dateFormat.format(publicLeg.getDepartureTime(true));
                final String departureTimeStr = timeFormat.format(publicLeg.getDepartureTime(true));
                final String departureNameStr = publicLeg.departure.uniqueShortName();
                final String departurePositionStr = publicLeg.getDeparturePosition() != null
                        ? publicLeg.getDeparturePosition().toString() : "";

                final String arrivalDateStr = dateFormat.format(publicLeg.getArrivalTime(true));
                final String arrivalTimeStr = timeFormat.format(publicLeg.getArrivalTime(true));
                final String arrivalNameStr = publicLeg.arrival.uniqueShortName();
                final String arrivalPositionStr = publicLeg.getArrivalPosition() != null
                        ? publicLeg.getArrivalPosition().toString() : "";

                legStr = getString(R.string.directions_trip_details_text_long_public, lineStr + lineDestinationStr,
                        departureDateStr, departureTimeStr, departurePositionStr, departureNameStr, arrivalDateStr,
                        arrivalTimeStr, arrivalPositionStr, arrivalNameStr);
            } else if (leg instanceof Individual) {
                final Individual individualLeg = (Individual) leg;

                final String distanceStr = individualLeg.distance != 0 ? "(" + individualLeg.distance + "m) " : "";
                final int legStrResId;
                if (individualLeg.type == Individual.Type.WALK)
                    legStrResId = R.string.directions_trip_details_text_long_walk;
                else if (individualLeg.type == Individual.Type.BIKE)
                    legStrResId = R.string.directions_trip_details_text_long_bike;
                else if (individualLeg.type == Individual.Type.CAR)
                    legStrResId = R.string.directions_trip_details_text_long_car;
                else if (individualLeg.type == Individual.Type.TRANSFER)
                    legStrResId = R.string.directions_trip_details_text_long_transfer;
                else
                    throw new IllegalStateException("unknown type: " + individualLeg.type);
                legStr = getString(legStrResId, individualLeg.min, distanceStr,
                        individualLeg.arrival.uniqueShortName());
            } else
                throw new IllegalStateException("cannot handle: " + leg);

            description.append(legStr);
            description.append("\n\n");
        }

        if (description.length() > 0)
            description.setLength(description.length() - 2);

        return description.toString();
    }

    private Point pointFromLocation(final Location location) {
        if (location.hasCoord())
            return location.coord;

        if (location.hasId()) {
            final Point point = pointFromStationDb(location.id);
            if (point != null)
                return point;
        }

        // fallback kludge: search db by name
        if (location.name != null) {
            final Point point = pointFromStationDb(location.place, location.name);
            if (point != null)
                return point;
        }

        return null;
    }

    private static String formatTimeSpan(final long millis) {
        final long mins = millis / DateUtils.MINUTE_IN_MILLIS;
        return String.format(Locale.ENGLISH, "%d:%02d", mins / 60, mins % 60);
    }
}
