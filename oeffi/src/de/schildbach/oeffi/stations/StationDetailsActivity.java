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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osmdroid.util.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.MyActionBar;
import de.schildbach.oeffi.OeffiActivity;
import de.schildbach.oeffi.OeffiMapView;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.StationsAware;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.util.DividerItemDecoration;
import de.schildbach.oeffi.util.Formats;
import de.schildbach.oeffi.util.ToggleImageButton;
import de.schildbach.oeffi.util.ToggleImageButton.OnCheckedChangeListener;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewAnimator;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class StationDetailsActivity extends OeffiActivity implements StationsAware {
    private static final String INTENT_EXTRA_NETWORK = StationDetailsActivity.class.getName() + ".network";
    private static final String INTENT_EXTRA_STATION = StationDetailsActivity.class.getName() + ".station";
    private static final String INTENT_EXTRA_DEPARTURES = StationDetailsActivity.class.getName() + ".departures";

    public static void start(final Context context, final NetworkId networkId, final Location station) {
        start(context, networkId, station, null);
    }

    public static void start(final Context context, final NetworkId networkId, final Location station,
            @Nullable final List<Departure> departures) {
        checkArgument(station.type == LocationType.STATION);
        final Intent intent = new Intent(context, StationDetailsActivity.class);
        intent.putExtra(StationDetailsActivity.INTENT_EXTRA_NETWORK, checkNotNull(networkId));
        intent.putExtra(StationDetailsActivity.INTENT_EXTRA_STATION, station);
        if (departures != null)
            intent.putExtra(StationDetailsActivity.INTENT_EXTRA_DEPARTURES, (Serializable) departures);
        context.startActivity(intent);
    }

    public static final int MAX_DEPARTURES = 200;

    private final List<Station> stations = new ArrayList<>();

    private NetworkId selectedNetwork;
    private Location selectedStation;
    @Nullable
    private List<Departure> selectedDepartures = null;
    @Nullable
    private Integer selectedFavState = null;
    @Nullable
    private LinkedHashMap<Line, List<Location>> selectedLines = null;
    @Nullable
    private List<Line> selectedAdditionalLines = null;

    private MyActionBar actionBar;
    private ToggleImageButton favoriteButton;
    private ViewAnimator viewAnimator;
    private RecyclerView listView;
    private DeparturesAdapter listAdapter;
    private TextView resultStatusView;
    private TextView disclaimerSourceView;
    private OeffiMapView mapView;

    private BroadcastReceiver tickReceiver;

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private static final Logger log = LoggerFactory.getLogger(StationDetailsActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        backgroundThread = new HandlerThread("queryDeparturesThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        setContentView(R.layout.stations_station_details_content);
        actionBar = getMyActionBar();
        setPrimaryColor(R.color.action_bar_background_stations);
        actionBar.setBack(v -> finish());
        actionBar.swapTitles();
        actionBar.addProgressButton().setOnClickListener(v -> load());
        favoriteButton = actionBar.addToggleButton(R.drawable.ic_star_24dp,
                R.string.stations_station_details_action_favorite_title);
        favoriteButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                final Uri rowUri = FavoriteUtils.persist(getContentResolver(),
                        FavoriteStationsProvider.TYPE_FAVORITE, selectedNetwork, selectedStation);
                if (rowUri != null) {
                    selectedFavState = FavoriteStationsProvider.TYPE_FAVORITE;
                    FavoriteUtils.notifyFavoritesChanged(StationDetailsActivity.this);
                }
            } else {
                final int numRows = FavoriteUtils.delete(getContentResolver(), selectedNetwork, selectedStation.id);
                if (numRows > 0) {
                    selectedFavState = null;
                    FavoriteUtils.notifyFavoritesChanged(StationDetailsActivity.this);
                }
            }
        });

        viewAnimator = (ViewAnimator) findViewById(R.id.stations_station_details_list_layout);

        listView = (RecyclerView) findViewById(R.id.stations_station_details_list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        listAdapter = new DeparturesAdapter(this);
        listView.setAdapter(listAdapter);

        mapView = (OeffiMapView) findViewById(R.id.stations_station_details_map);
        mapView.setStationsAware(this);
        ((TextView) findViewById(R.id.stations_station_details_map_disclaimer))
                .setText(mapView.getTileProvider().getTileSource().getCopyrightNotice());

        resultStatusView = (TextView) findViewById(R.id.stations_station_details_result_status);

        final Intent intent = getIntent();
        final Uri uri = intent.getData();

        if (uri != null && uri.getScheme().equals("http")) {
            log.info("Got intent: {}", intent);

            final String host = uri.getHost();
            final String path = uri.getPath().trim();

            if ("oeffi.schildbach.de".equals(host)) {
                final NetworkId network = NetworkId.valueOf(uri.getQueryParameter("network").toUpperCase());
                final String stationId = uri.getQueryParameter("id");
                selectStation(new Station(network, new Location(LocationType.STATION, stationId, null, null)));
            } else if ("qr.bvg.de".equals(host)) {
                final Matcher m = Pattern.compile("/h(\\d+)").matcher(path);
                if (m.matches()) {
                    final NetworkId network = NetworkId.BVG;
                    String stationId = bvgStationIdNfcToQr(m.group(1));
                    if (stationId.length() <= 6) // mast
                        stationId = '~' + stationId;
                    selectStation(new Station(network, new Location(LocationType.STATION, stationId, null, null)));
                } else {
                    throw new IllegalArgumentException("could not parse path: '" + path + "'");
                }
            } else if ("mobil.s-bahn-berlin.de".equals(host)) {
                if ("/".equals(path)) {
                    final NetworkId network = NetworkId.VBB;
                    final String qr = uri.getQueryParameter("QR");
                    final String stationId = qr != null ? qr : uri.getQueryParameter("qr");
                    selectStation(new Station(network, new Location(LocationType.STATION, stationId, null, null)));
                } else {
                    throw new IllegalArgumentException("could not parse path: '" + path + "'");
                }
            } else if ("www.mvg-live.de".equals(host)) {
                final Matcher m = Pattern.compile("/qr/(\\d+)-\\d*-\\d*").matcher(path);
                if (m.matches()) {
                    final NetworkId network = NetworkId.MVV;
                    final String stationId = m.group(1);
                    selectStation(new Station(network, new Location(LocationType.STATION, stationId, null, null)));
                } else {
                    throw new IllegalArgumentException("could not parse path: '" + path + "'");
                }
            } else if ("wap.rmv.de".equals(host)) {
                final NetworkId network = NetworkId.NVV;
                final String stationId = uri.getQueryParameter("id");
                selectStation(new Station(network, new Location(LocationType.STATION, stationId, null, null)));
            } else if ("m.vrn.de".equals(host)) {
                final Matcher m = Pattern.compile("/(\\d+)").matcher(path);
                if (m.matches()) {
                    final NetworkId network = NetworkId.VRN;
                    final String stationId = Integer.toString(Integer.parseInt(m.group(1)) + 6000000);
                    selectStation(new Station(network, new Location(LocationType.STATION, stationId, null, null)));
                } else {
                    throw new IllegalArgumentException("could not parse path: '" + path + "'");
                }
            } else if ("www.rheinbahn.de".equals(host)) {
                final Matcher m = Pattern.compile("/QRBarcode/HS/(\\d+)_\\d*.html").matcher(path);
                if (m.matches()) {
                    final NetworkId network = NetworkId.VRR;
                    final String stationId = Integer.toString(Integer.parseInt(m.group(1)) + 20000000);
                    selectStation(new Station(network, new Location(LocationType.STATION, stationId, null, null)));
                } else {
                    throw new IllegalArgumentException("could not parse path: '" + path + "'");
                }
            } else if ("mobil.vvs.de".equals(host)) {
                final NetworkId network = NetworkId.VVS;
                final int stationId = Integer.parseInt(uri.getQueryParameter("name_dm"));
                // final String lineId = uri.getQueryParameter("line");
                selectStation(new Station(network, new Location(LocationType.STATION,
                        Integer.toString(stationId < 10000 ? stationId + 5000000 : stationId), null, null)));
            } else {
                throw new RuntimeException("cannot handle host: '" + host + "'");
            }
        } else {
            final NetworkId network = (NetworkId) checkNotNull(getIntent().getSerializableExtra(INTENT_EXTRA_NETWORK));
            final Station station = new Station(network, (Location) intent.getSerializableExtra(INTENT_EXTRA_STATION));
            if (intent.hasExtra(INTENT_EXTRA_DEPARTURES))
                station.departures = (List<Departure>) intent.getSerializableExtra(INTENT_EXTRA_DEPARTURES);
            selectStation(station);
            statusMessage(getString(R.string.stations_station_details_progress));
        }

        favoriteButton
                .setChecked(selectedFavState != null && selectedFavState == FavoriteStationsProvider.TYPE_FAVORITE);

        disclaimerSourceView = (TextView) findViewById(R.id.stations_station_details_disclaimer_source);
        updateDisclaimerSource(disclaimerSourceView, selectedNetwork.name(), null);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // regular refresh
        tickReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                load();
            }
        };
        registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

        load();

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
        backgroundThread.getLooper().quit();

        super.onDestroy();
    }

    @Override
    public void onAttachedToWindow() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }

    @Override
    public void onConfigurationChanged(final Configuration config) {
        super.onConfigurationChanged(config);

        updateFragments();
    }

    private void updateFragments() {
        updateFragments(R.id.stations_station_details_list_fragment, R.id.stations_station_details_map_fragment);
    }

    private void updateGUI() {
        final List<Departure> selectedDepartures = this.selectedDepartures;
        if (selectedDepartures != null) {
            if (!selectedDepartures.isEmpty()) {
                viewAnimator.setDisplayedChild(0);
                listAdapter.notifyDataSetChanged();
            } else {
                statusMessage(getString(R.string.stations_station_details_list_empty));
            }
        }
    }

    private void load() {
        final String requestedStationId = selectedStation.id;
        final NetworkProvider networkProvider = NetworkProviderFactory.provider(selectedNetwork);

        backgroundHandler.removeCallbacksAndMessages(null);
        backgroundHandler
                .post(new QueryDeparturesRunnable(handler, networkProvider, requestedStationId, MAX_DEPARTURES) {
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
                            updateDisclaimerSource(disclaimerSourceView, selectedNetwork.name(),
                                    product(result.header));

                        if (result.status == QueryDeparturesResult.Status.OK) {
                            for (final StationDepartures stationDepartures : result.stationDepartures) {
                                Location location = stationDepartures.location;
                                if (location.hasId()) {
                                    List<Line> additionalLines = null;

                                    final Cursor cursor = getContentResolver().query(
                                            NetworkContentProvider.CONTENT_URI.buildUpon()
                                                    .appendPath(selectedNetwork.name()).build(),
                                            null, NetworkContentProvider.KEY_ID + "=?", new String[] { location.id },
                                            null);
                                    if (cursor != null) {
                                        if (cursor.moveToFirst()) {
                                            final int placeCol = cursor
                                                    .getColumnIndex(NetworkContentProvider.KEY_PLACE);
                                            final int nameCol = cursor
                                                    .getColumnIndexOrThrow(NetworkContentProvider.KEY_NAME);
                                            final int latCol = cursor
                                                    .getColumnIndexOrThrow(NetworkContentProvider.KEY_LAT);
                                            final int lonCol = cursor
                                                    .getColumnIndexOrThrow(NetworkContentProvider.KEY_LON);
                                            final int productsCol = cursor
                                                    .getColumnIndex(NetworkContentProvider.KEY_PRODUCTS);
                                            final int linesCol = cursor
                                                    .getColumnIndexOrThrow(NetworkContentProvider.KEY_LINES);

                                            final Point coord = Point.from1E6(cursor.getInt(latCol),
                                                    cursor.getInt(lonCol));
                                            final String place = placeCol != -1 ? cursor.getString(placeCol)
                                                    : selectedStation.place;
                                            final String name = cursor.getString(nameCol);
                                            final Set<Product> products;
                                            if (productsCol != -1 && !cursor.isNull(productsCol))
                                                products = Product
                                                        .fromCodes(cursor.getString(productsCol).toCharArray());
                                            else
                                                products = null;
                                            location = new Location(LocationType.STATION, location.id, coord, place,
                                                    name, products);

                                            final String[] additionalLinesArray = cursor.getString(linesCol).split(",");
                                            additionalLines = new ArrayList<>(additionalLinesArray.length);
                                            l: for (final String additionalLineStr : additionalLinesArray) {
                                                if (!additionalLineStr.isEmpty()) {
                                                    final Product additionalLineProduct = Product
                                                            .fromCode(additionalLineStr.charAt(0));
                                                    final String additionalLineLabel = Strings
                                                            .emptyToNull(additionalLineStr.substring(1));
                                                    final Line additionalLine = new Line(null, null,
                                                            additionalLineProduct, additionalLineLabel);
                                                    final List<LineDestination> lineDestinations = stationDepartures.lines;
                                                    if (lineDestinations != null)
                                                        for (final LineDestination lineDestination : lineDestinations)
                                                            if (lineDestination.line.equals(additionalLine))
                                                                continue l;
                                                    final Style style = networkProvider.lineStyle(null,
                                                            additionalLine.product, additionalLine.label);
                                                    additionalLines.add(new Line(null, null, additionalLine.product,
                                                            additionalLine.label, style));
                                                }
                                            }
                                        }

                                        cursor.close();
                                    }

                                    Station station = findStation(location.id);
                                    if (station == null) {
                                        station = new Station(selectedNetwork, location);
                                        stations.add(station);
                                    }

                                    station.departures = stationDepartures.departures;
                                    station.setLines(stationDepartures.lines);

                                    if (location.equals(selectedStation)) {
                                        selectedDepartures = stationDepartures.departures;
                                        selectedLines = groupDestinationsByLine(stationDepartures.lines);
                                        selectedAdditionalLines = additionalLines;
                                    }
                                }
                            }

                            updateGUI();
                        } else {
                            log.info("Got {}", result.toShortString());
                            statusMessage(getString(QueryDeparturesRunnable.statusMsgResId(result.status)));
                        }
                    }

                    @Override
                    protected void onInputOutputError(final IOException x) {
                        statusMessage(x.getMessage());
                    }

                    @Override
                    protected void onAllErrors() {
                        statusMessage(getString(R.string.toast_network_problem));
                    }

                    private Station findStation(final String stationId) {
                        for (final Station station : stations)
                            if (stationId.equals(station.location.id))
                                return station;

                        return null;
                    }
                });
    }

    public List<Station> getStations() {
        return stations;
    }

    public Integer getFavoriteState(final String stationId) {
        throw new UnsupportedOperationException();
    }

    private void statusMessage(final String message) {
        final List<Departure> selectedDepartures = this.selectedDepartures;
        if (selectedDepartures == null || selectedDepartures.isEmpty()) {
            viewAnimator.setDisplayedChild(1);
            resultStatusView.setText(message);
        }
    }

    public void selectStation(final Station station) {
        final boolean changed = !station.location.equals(selectedStation);

        selectedNetwork = station.network;
        selectedStation = station.location;
        selectedDepartures = station.departures;
        selectedLines = groupDestinationsByLine(station.getLines());
        selectedAdditionalLines = null;

        final Cursor stationCursor = getContentResolver().query(
                NetworkContentProvider.CONTENT_URI.buildUpon().appendPath(selectedNetwork.name()).build(), null,
                NetworkContentProvider.KEY_ID + "=?", new String[] { selectedStation.id }, null);
        if (stationCursor != null) {
            if (stationCursor.moveToFirst()) {
                final int placeCol = stationCursor.getColumnIndex(NetworkContentProvider.KEY_PLACE);
                final int nameCol = stationCursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_NAME);
                final int latCol = stationCursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_LAT);
                final int lonCol = stationCursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_LON);
                final int productsCol = stationCursor.getColumnIndex(NetworkContentProvider.KEY_PRODUCTS);
                final int linesCol = stationCursor.getColumnIndexOrThrow(NetworkContentProvider.KEY_LINES);

                final Point coord = Point.from1E6(stationCursor.getInt(latCol), stationCursor.getInt(lonCol));
                final String place = placeCol != -1 ? stationCursor.getString(placeCol) : selectedStation.place;
                final String name = stationCursor.getString(nameCol);
                final Set<Product> products;
                if (productsCol != -1 && !stationCursor.isNull(productsCol))
                    products = Product.fromCodes(stationCursor.getString(productsCol).toCharArray());
                else
                    products = null;
                selectedStation = new Location(LocationType.STATION, selectedStation.id, coord, place, name, products);

                final NetworkProvider networkProvider = NetworkProviderFactory.provider(selectedNetwork);

                final String[] additionalLinesArray = stationCursor.getString(linesCol).split(",");
                final List<Line> additionalLines = new ArrayList<>(additionalLinesArray.length);
                l: for (final String additionalLineStr : additionalLinesArray) {
                    if (!additionalLineStr.isEmpty()) {
                        final Product additionalLineProduct = Product.fromCode(additionalLineStr.charAt(0));
                        final String additionalLineLabel = Strings.emptyToNull(additionalLineStr.substring(1));
                        final Line additionalLine = new Line(null, null, additionalLineProduct, additionalLineLabel);
                        final List<LineDestination> lineDestinations = station.getLines();
                        if (lineDestinations != null)
                            for (final LineDestination line : lineDestinations)
                                if (line.line.equals(additionalLine))
                                    continue l;
                        additionalLines.add(new Line(null, null, additionalLine.product, additionalLine.label,
                                networkProvider.lineStyle(null, additionalLine.product, additionalLine.label)));
                    }
                }
                selectedAdditionalLines = additionalLines;
            }

            stationCursor.close();
        }

        selectedFavState = FavoriteStationsProvider.favState(getContentResolver(), selectedNetwork, selectedStation);

        if (selectedStation.hasCoord())
            mapView.getController()
                    .animateTo(new GeoPoint(selectedStation.getLatAsDouble(), selectedStation.getLonAsDouble()));

        updateGUI();

        actionBar.setPrimaryTitle(selectedStation.name);
        actionBar.setSecondaryTitle(selectedStation.place);

        if (changed)
            load();
    }

    public boolean isSelectedStation(final String stationId) {
        return selectedStation != null && stationId.equals(selectedStation.id);
    }

    private LinkedHashMap<Line, List<Location>> groupDestinationsByLine(final List<LineDestination> lineDestinations) {
        if (lineDestinations == null)
            return null;

        final LinkedHashMap<Line, List<Location>> groups = new LinkedHashMap<>();
        for (final LineDestination lineDestination : lineDestinations) {
            if (lineDestination.destination != null) {
                List<Location> list = groups.get(lineDestination.line);
                if (list == null) {
                    list = new ArrayList<>(2); // A typical line will have two destinations.
                    groups.put(lineDestination.line, list);
                }
                list.add(lineDestination.destination);
            }
        }
        return groups;
    }

    private class DeparturesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final Context context;
        private final LayoutInflater inflater;

        public DeparturesAdapter(final Context context) {
            this.context = context;
            this.inflater = LayoutInflater.from(context);

            setHasStableIds(true);
        }

        @Override
        public int getItemCount() {
            final List<Departure> selectedDepartures = StationDetailsActivity.this.selectedDepartures;
            if (selectedDepartures == null || selectedDepartures.isEmpty())
                return 1;
            return selectedDepartures.size();
        }

        @Override
        public int getItemViewType(final int position) {
            if (position == 0)
                return R.layout.stations_station_details_header;
            return R.layout.stations_station_details_entry;
        }

        public Departure getItem(final int position) {
            if (position == 0)
                return null;
            return checkNotNull(selectedDepartures).get(position - 1);
        }

        @Override
        public long getItemId(final int position) {
            if (position == 0)
                return RecyclerView.NO_ID;
            return getItem(position).hashCode();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            if (viewType == R.layout.stations_station_details_header)
                return new HeaderViewHolder(context,
                        inflater.inflate(R.layout.stations_station_details_header, parent, false));
            else
                return new DepartureViewHolder(context,
                        inflater.inflate(R.layout.stations_station_details_entry, parent, false));
        }

        @Override
        public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).bind(selectedStation, selectedLines, selectedAdditionalLines);
            } else {
                final Departure departure = getItem(position);
                ((DepartureViewHolder) holder).bind(selectedNetwork, departure);
            }
        }
    }

    private static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView idView;
        private final LinearLayout linesGroup;
        private final LineView additionalLinesView;

        private final LayoutInflater inflater;

        private final LinearLayout.LayoutParams LINES_LAYOUT_PARAMS = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        public HeaderViewHolder(final Context context, final View itemView) {
            super(itemView);

            idView = (TextView) itemView.findViewById(R.id.stations_station_details_header_id);
            linesGroup = (LinearLayout) itemView.findViewById(R.id.stations_station_details_header_lines);
            additionalLinesView = (LineView) itemView
                    .findViewById(R.id.stations_station_details_header_additional_lines);

            inflater = LayoutInflater.from(context);
            final Resources res = context.getResources();
            LINES_LAYOUT_PARAMS.setMargins(0, res.getDimensionPixelSize(R.dimen.list_entry_padding_vertical_cram), 0,
                    0);
        }

        public void bind(final Location station, @Nullable final LinkedHashMap<Line, List<Location>> lines,
                @Nullable final List<Line> additionalLines) {
            // id
            idView.setText(station.hasId() ? station.id : "");

            // lines
            linesGroup.removeAllViews();
            if (lines != null) {
                linesGroup.setVisibility(View.VISIBLE);
                for (final Map.Entry<Line, List<Location>> linesEntry : lines.entrySet()) {
                    final Line line = linesEntry.getKey();
                    final List<Location> destinations = linesEntry.getValue();

                    final View lineRow = inflater.inflate(R.layout.stations_station_details_header_line, null);
                    linesGroup.addView(lineRow, LINES_LAYOUT_PARAMS);

                    final LineView lineView = (LineView) lineRow
                            .findViewById(R.id.stations_station_details_header_line_line);
                    lineView.setLine(line);

                    final TextView destinationView = (TextView) lineRow
                            .findViewById(R.id.stations_station_details_header_line_destination);
                    final StringBuilder text = new StringBuilder();
                    for (final Location destination : destinations) {
                        if (text.length() > 0)
                            text.append(Constants.CHAR_THIN_SPACE).append(Constants.CHAR_LEFT_RIGHT_ARROW)
                                    .append(Constants.CHAR_THIN_SPACE);
                        text.append(destination.uniqueShortName());
                    }
                    destinationView.setText(text);
                }
            } else {
                linesGroup.setVisibility(View.GONE);
            }

            // additional lines
            additionalLinesView.setLines(additionalLines);
        }
    }

    private static class DepartureViewHolder extends RecyclerView.ViewHolder {
        private final TextView timeRelView;
        private final TextView timeAbsView;
        private final TextView delayView;
        private final LineView lineView;
        private final TextView destinationView;
        private final TextView positionView;
        private final TextView capacity1stView;
        private final TextView capacity2ndView;
        private final TextView msgView;

        private final Context context;
        private final java.text.DateFormat timeFormat;

        public DepartureViewHolder(final Context context, final View itemView) {
            super(itemView);

            timeRelView = (TextView) itemView.findViewById(R.id.stations_station_entry_time_rel);
            timeAbsView = (TextView) itemView.findViewById(R.id.stations_station_entry_time_abs);
            delayView = (TextView) itemView.findViewById(R.id.stations_station_entry_delay);
            lineView = (LineView) itemView.findViewById(R.id.stations_station_entry_line);
            destinationView = (TextView) itemView.findViewById(R.id.stations_station_entry_destination);
            positionView = (TextView) itemView.findViewById(R.id.stations_station_entry_position);
            capacity1stView = (TextView) itemView.findViewById(R.id.stations_station_entry_capacity_1st_class);
            capacity2ndView = (TextView) itemView.findViewById(R.id.stations_station_entry_capacity_2nd_class);
            msgView = (TextView) itemView.findViewById(R.id.stations_station_entry_msg);

            this.context = context;
            this.timeFormat = DateFormat.getTimeFormat(context);
        }

        public void bind(final NetworkId network, final Departure departure) {
            final long currentTime = System.currentTimeMillis();

            final Date predictedTime = departure.predictedTime;
            final Date plannedTime = departure.plannedTime;

            long time;
            final boolean isPredicted = predictedTime != null;
            if (predictedTime != null)
                time = predictedTime.getTime();
            else if (plannedTime != null)
                time = plannedTime.getTime();
            else
                throw new IllegalStateException();

            // time rel
            timeRelView.setText(Formats.formatTimeDiff(context, currentTime, time));
            timeRelView.setTypeface(Typeface.DEFAULT, isPredicted ? Typeface.ITALIC : Typeface.NORMAL);

            // time abs
            final StringBuilder timeAbs = new StringBuilder(Formats.formatDate(context, currentTime, time, false, ""));
            if (timeAbs.length() > 0)
                timeAbs.append(',').append(Constants.CHAR_HAIR_SPACE);
            timeAbs.append(timeFormat.format(time));
            timeAbsView.setText(timeAbs);
            timeAbsView.setTypeface(Typeface.DEFAULT, isPredicted ? Typeface.ITALIC : Typeface.NORMAL);

            // delay
            final long delay = predictedTime != null && plannedTime != null
                    ? predictedTime.getTime() - plannedTime.getTime() : 0;
            final long delayMins = delay / DateUtils.MINUTE_IN_MILLIS;
            delayView.setText(delayMins != 0 ? String.format(Locale.US, "(%+d)", delayMins) + ' ' : "");
            delayView.setTypeface(Typeface.DEFAULT, isPredicted ? Typeface.ITALIC : Typeface.NORMAL);

            // line
            lineView.setLine(departure.line);

            // destination
            final Location destination = departure.destination;
            if (destination != null) {
                destinationView.setText(Constants.DESTINATION_ARROW_PREFIX + destination.uniqueShortName());
                itemView.setOnClickListener(destination.id != null ? (OnClickListener) v -> start(context, network, destination) : null);
            } else {
                destinationView.setText(null);
                itemView.setOnClickListener(null);
            }

            // position
            positionView.setText(departure.position != null ? Constants.DESTINATION_ARROW_INVISIBLE_PREFIX
                    + context.getString(R.string.position_platform, departure.position) : null);

            // capacity
            final int[] capacity = departure.capacity;
            if (capacity != null) {
                capacity1stView.setVisibility(View.VISIBLE);
                capacity2ndView.setVisibility(View.VISIBLE);
                capacity(capacity1stView, capacity[0]);
                capacity(capacity2ndView, capacity[1]);
            } else {
                capacity1stView.setVisibility(View.GONE);
                capacity2ndView.setVisibility(View.GONE);
            }

            // message
            if (departure.message != null || departure.line.message != null) {
                msgView.setVisibility(View.VISIBLE);
                msgView.setText(Joiner.on('\n').skipNulls().join(departure.message, departure.line.message));
            } else {
                msgView.setVisibility(View.GONE);
                msgView.setText(null);
            }
        }

        private void capacity(final TextView capacityView, final int capacity) {
            if (capacity == 1)
                capacityView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.capacity_1, 0);
            else if (capacity == 2)
                capacityView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.capacity_2, 0);
            else if (capacity == 3)
                capacityView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.capacity_3, 0);
        }
    }

    private static String bvgStationIdNfcToQr(final String stationIdStr) {
        final int stationId = Integer.parseInt(stationIdStr);

        if (stationId < 100000000 || stationId >= 1000000000)
            return stationIdStr;
        final int low = stationId % 100000;
        final int middle = (stationId % 100000000) - low;

        if (middle != 1000000)
            return stationIdStr;

        final int high = stationId - (stationId % 100000000);

        return Integer.toString(high / 1000 + low);
    }
}
