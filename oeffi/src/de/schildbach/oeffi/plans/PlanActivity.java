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

package de.schildbach.oeffi.plans;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.StationsAware;
import de.schildbach.oeffi.network.NetworkProviderFactory;
import de.schildbach.oeffi.plans.ScrollImageView.OnMoveListener;
import de.schildbach.oeffi.stations.LineView;
import de.schildbach.oeffi.stations.QueryDeparturesRunnable;
import de.schildbach.oeffi.stations.Station;
import de.schildbach.oeffi.stations.StationContextMenu;
import de.schildbach.oeffi.stations.StationDetailsActivity;
import de.schildbach.oeffi.util.Downloader;
import de.schildbach.oeffi.util.Toast;
import de.schildbach.oeffi.util.UiThreadExecutor;
import de.schildbach.oeffi.util.ZoomControls;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Departure;
import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.LineDestination;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.QueryDeparturesResult;
import de.schildbach.pte.dto.StationDepartures;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.ViewAnimator;
import androidx.annotation.Nullable;
import okhttp3.HttpUrl;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;

public class PlanActivity extends Activity {
    public static final String INTENT_EXTRA_PLAN_ID = "plan_id"; // Used in launcher shortcuts
    private static final String INTENT_EXTRA_SELECTED_STATION_ID = PlanActivity.class.getName()
            + ".selected_station_id";

    protected SharedPreferences prefs;

    public static Intent intent(final Context context, final String planId, final String selectedStationId) {
        final Intent intent = new Intent(Intent.ACTION_VIEW, null, context, PlanActivity.class);
        intent.putExtra(INTENT_EXTRA_PLAN_ID, checkNotNull(planId));
        if (selectedStationId != null)
            intent.putExtra(INTENT_EXTRA_SELECTED_STATION_ID, selectedStationId);
        return intent;
    }

    public static void start(final Context context, final String planId, final String selectedStationId) {
        context.startActivity(intent(context, planId, selectedStationId));
    }

    private Application application;

    private ViewAnimator viewAnimator;
    private ScrollImageView plan;
    private View bubble;
    private TextView bubbleName;
    private LineView bubbleLinesView;
    private ZoomControls zoom;
    private TiledImageDrawable drawable;
    @Nullable
    private Station selection = null;
    private List<Station> stations = new LinkedList<>();

    private final Handler handler = new Handler();
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private static final Logger log = LoggerFactory.getLogger(PlanActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.application = (Application) getApplication();

        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // background thread
        backgroundThread = new HandlerThread("queryDeparturesThread", Process.THREAD_PRIORITY_BACKGROUND);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.plans_content);

        final Animation zoomControlsAnimation = AnimationUtils.loadAnimation(this, R.anim.zoom_controls);
        zoomControlsAnimation.setFillAfter(true); // workaround: set through code because XML does not work

        viewAnimator = (ViewAnimator) findViewById(R.id.plans_layout);

        plan = (ScrollImageView) findViewById(R.id.plans_plan);
        plan.setOnMoveListener(() -> {
            updateBubble();
            updateScale();

            zoom.clearAnimation();
            zoom.startAnimation(zoomControlsAnimation);
        });

        bubble = findViewById(R.id.plans_bubble);
        bubble.setVisibility(View.GONE);
        bubble.setOnClickListener(v -> {
            final Station selection = checkNotNull(PlanActivity.this.selection);
            final PopupMenu contextMenu = new StationContextMenu(PlanActivity.this, v, selection.network,
                    selection.location, null, false, false, false, false, false);
            contextMenu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.station_context_details) {
                    StationDetailsActivity.start(PlanActivity.this, selection.network, selection.location);
                    return true;
                } else {
                    return false;
                }
            });
            contextMenu.show();
        });

        bubbleName = (TextView) findViewById(R.id.plans_bubble_name);

        bubbleLinesView = (LineView) findViewById(R.id.plans_bubble_lines);

        zoom = (ZoomControls) findViewById(R.id.plans_zoom);
        zoom.setOnZoomInClickListener(v -> {
            plan.animateScaleStepIn();
            updateScale();
        });
        zoom.setOnZoomOutClickListener(v -> {
            plan.animateScaleStepOut();
            updateScale();
        });

        final String planId = checkNotNull(getIntent().getExtras().getString(INTENT_EXTRA_PLAN_ID),
                "Required intent extra: %s", INTENT_EXTRA_PLAN_ID);
        final Uri planContentUri = PlanContentProvider.planUri(planId);
        final String planFilename = planId + ".png";
        final File planFile = new File(getDir(Constants.PLANS_DIR, Context.MODE_PRIVATE), planFilename);

        final Cursor cursor = getContentResolver().query(planContentUri, null, null, null, null);
        cursor.moveToFirst();
        final String planUrlStr = cursor
                .getString(cursor.getColumnIndexOrThrow(PlanContentProvider.KEY_PLAN_REMOTE_URL));
        cursor.close();

        stations.clear();
        final Cursor stationsCursor = getContentResolver().query(PlanContentProvider.stationsUri(planId), null, null,
                null, null);
        if (stationsCursor != null) {
            final int networkColumn = stationsCursor.getColumnIndexOrThrow(PlanContentProvider.KEY_STATION_NETWORK);
            final int localIdColumn = stationsCursor.getColumnIndexOrThrow(PlanContentProvider.KEY_STATION_ID);
            final int labelColumn = stationsCursor.getColumnIndexOrThrow(PlanContentProvider.KEY_STATION_LABEL);
            final int xColumn = stationsCursor.getColumnIndexOrThrow(PlanContentProvider.KEY_STATION_X);
            final int yColumn = stationsCursor.getColumnIndexOrThrow(PlanContentProvider.KEY_STATION_Y);
            while (stationsCursor.moveToNext()) {
                final String networkStr = stationsCursor.getString(networkColumn);
                final NetworkId network = networkStr != null ? NetworkId.valueOf(networkStr.toUpperCase(Locale.US))
                        : null;
                final String localId = stationsCursor.getString(localIdColumn);
                final String label = stationsCursor.getString(labelColumn);
                final int x = stationsCursor.getInt(xColumn);
                final int y = stationsCursor.getInt(yColumn);
                final Point point = Point.from1E6(y, x);
                stations.add(new Station(network, new Location(LocationType.STATION, localId, point, null, label)));
            }
            stationsCursor.close();
        }

        final Downloader downloader = new Downloader(getCacheDir());
        final HttpUrl remoteUrl = planUrlStr != null ? HttpUrl.parse(planUrlStr)
                : Constants.PLANS_BASE_URL.newBuilder().addEncodedPathSegment(planFilename).build();
        final ListenableFuture<Integer> download = downloader.download(application.okHttpClient(), remoteUrl, planFile);
        Futures.addCallback(download, new FutureCallback<Integer>() {
            public void onSuccess(final @Nullable Integer status) {
                if (status == HttpURLConnection.HTTP_OK)
                    loadPlan(planFile);
            }

            public void onFailure(final Throwable t) {
            }
        }, new UiThreadExecutor());

        if (planFile.exists())
            loadPlan(planFile);

        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);
    }

    @Override
    public void onNewIntent(final Intent intent) {
        final String query = intent.getStringExtra(SearchManager.QUERY);

        if (query != null && !stations.isEmpty()) {
            final String lcQuery = query.trim().toLowerCase(Constants.DEFAULT_LOCALE);

            for (final Station station : stations) {
                final String stationName = station.location.name;
                if (stationName != null) {
                    if (stationName.toLowerCase(Constants.DEFAULT_LOCALE).contains(lcQuery)) {
                        selectStation(station);
                        break;
                    }
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        // cancel background thread
        backgroundThread.getLooper().quit();

        super.onDestroy();
    }

    @Override
    public void onAttachedToWindow() {
        if(prefs.getBoolean(Constants.PREFS_KEY_SHOW_PLAN_ON_LOCKSCREEN, true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
    }

    @Override
    public boolean onSearchRequested() {
        if (!stations.isEmpty())
            return super.onSearchRequested();
        else
            return false;
    }

    private void selectStation(final Station selection) {
        this.selection = selection;

        plan.animatePlanIntoView(selection.location.getLonAs1E6(), selection.location.getLatAs1E6());

        bubble.setVisibility(View.VISIBLE);
        bubbleName.setText(selection.location.name);
        bubbleLinesView.setVisibility(View.GONE);
        updateBubble();

        if (selection.location.hasId()) {
            final NetworkProvider networkProvider = NetworkProviderFactory.provider(selection.network);
            backgroundHandler.post(new QueryDeparturesRunnable(handler, networkProvider, selection.location.id, 0) {
                @Override
                protected void onResult(final QueryDeparturesResult result) {
                    log.info("Got {}", result.toShortString());
                    if (result.status == QueryDeparturesResult.Status.OK) {
                        final StationDepartures stationDeparture = result.findStationDepartures(selection.location.id);
                        if (stationDeparture != null) {
                            bubbleLinesView.setVisibility(View.GONE);

                            // collect lines, remove duplicates, sort
                            final Set<Line> lines = new TreeSet<>();
                            final List<LineDestination> lineDestinations = stationDeparture.lines;
                            if (lineDestinations != null)
                                for (final LineDestination lineDestination : lineDestinations)
                                    lines.add(lineDestination.line);
                            if (stationDeparture.departures != null)
                                for (final Departure departure : stationDeparture.departures)
                                    lines.add(departure.line);

                            // add lines to bubble
                            bubbleLinesView.setVisibility(View.VISIBLE);
                            bubbleLinesView.setLines(lines);
                            updateBubble();
                        }
                    }
                }

                @Override
                protected void onAllErrors() {
                    bubbleLinesView.setVisibility(View.GONE);
                    updateBubble();
                }
            });
        }
    }

    private void updateBubble() {
        final Station selection = this.selection;
        if (selection != null) {
            final int[] coords = new int[] { selection.location.getLonAs1E6(), selection.location.getLatAs1E6() };
            plan.translateToViewCoordinates(coords);
            final BubbleLayout.LayoutParams layoutParams = (BubbleLayout.LayoutParams) bubble.getLayoutParams();
            layoutParams.x = coords[0];
            layoutParams.y = coords[1];
            bubble.requestLayout();
            bubble.setEnabled(selection.location.hasId());
        } else {
            bubble.setEnabled(false);
        }
    }

    private void updateScale() {
        zoom.setIsZoomInEnabled(plan.canZoomIn());
        zoom.setIsZoomOutEnabled(plan.canZoomOut());
    }

    private static final BitmapFactory.Options LOWMEM_OPTIONS = new BitmapFactory.Options();
    static {
        LOWMEM_OPTIONS.inPreferredConfig = Bitmap.Config.RGB_565;
        LOWMEM_OPTIONS.inDither = true;
    }

    private void loadPlan(final File planFile) {
        try {
            final Bitmap bitmap = BitmapFactory.decodeFile(planFile.getPath(), LOWMEM_OPTIONS);
            if (bitmap == null)
                throw new IOException("Cannot decode bitmap from file descriptor");

            final int height = bitmap.getHeight();
            final int halfHeight = height / 2;
            final int width = bitmap.getWidth();
            final int halfWidth = width / 2;

            final Bitmap nwBitmap = Bitmap.createBitmap(bitmap, 0, 0, halfWidth, halfHeight);
            final Bitmap neBitmap = Bitmap.createBitmap(bitmap, halfWidth, 0, width - halfWidth, halfHeight);
            final Bitmap swBitmap = Bitmap.createBitmap(bitmap, 0, halfHeight, halfWidth, height - halfHeight);
            final Bitmap seBitmap = Bitmap.createBitmap(bitmap, halfWidth, halfHeight, width - halfWidth,
                    height - halfHeight);
            drawable = new TiledImageDrawable(nwBitmap, neBitmap, swBitmap, seBitmap);

            plan.setImageDrawable(drawable);

            updateScale();

            viewAnimator.setDisplayedChild(1);

            if (!stations.isEmpty()) {
                new Toast(PlanActivity.this).imageToast(R.drawable.ic_info_outline_white_24dp,
                        R.string.toast_plan_interactive_hint);

                plan.setStationsAware(new StationsAware() {
                    public List<Station> getStations() {
                        return stations;
                    }

                    public Integer getFavoriteState(final String stationId) {
                        return null;
                    }

                    public void selectStation(final Station station) {
                        PlanActivity.this.selectStation(station);
                    }

                    public boolean isSelectedStation(final String stationId) {
                        final Station selection = PlanActivity.this.selection;
                        return selection != null && selection.location.hasId()
                                && stationId.equals(selection.location.id);
                    }
                });

                final String selectedId = getIntent().getExtras().getString(INTENT_EXTRA_SELECTED_STATION_ID);
                if (selectedId != null) {
                    for (final Station station : stations) {
                        if (selectedId.equals(station.location.id)) {
                            // delay until after layout finished
                            handler.postDelayed(() -> selectStation(station), 500);

                            break;
                        }
                    }
                }
            }
        } catch (final IOException | OutOfMemoryError x) {
            log.info("Problem loading " + planFile, x);
            new Toast(PlanActivity.this).longToast(x.getMessage());
        }
    }
}
