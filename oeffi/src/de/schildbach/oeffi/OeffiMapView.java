package de.schildbach.oeffi;

import java.util.ArrayList;
import java.util.List;

import com.amazon.geo.maps.GeoPoint;
import com.amazon.geo.maps.MapController;
import com.amazon.geo.maps.MapView;
import com.amazon.geo.maps.Overlay;
import com.amazon.geo.maps.Projection;

import de.schildbach.oeffi.stations.LineView;
import de.schildbach.oeffi.stations.Station;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.dto.Trip.Leg;
import de.schildbach.pte.dto.Trip.Public;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.FillType;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.TextView;

public class OeffiMapView extends MapView {
    private FromViaToAware fromViaToAware = null;
    private TripAware tripAware = null;
    private StationsAware stationsAware = null;
    private LocationAware locationAware = null;
    private AreaAware areaAware = null;
    private boolean firstLocation = true;
    private boolean zoomLocked = true;

    private final int AREA_FILL_COLOR = Color.parseColor("#22000000");

    public OeffiMapView(final Context context) {
        this(context, null, 0);
    }

    public OeffiMapView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OeffiMapView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        final Resources res = context.getResources();
        final LayoutInflater inflater = LayoutInflater.from(context);

        final float stationFontSize = res.getDimension(R.dimen.font_size_normal);
        final float tripStrokeWidth = res.getDimension(R.dimen.map_trip_stroke_width);
        final float tripStrokeWidthSelected = res.getDimension(R.dimen.map_trip_stroke_width_selected);
        final float tripStrokeWidthSelectedGlow = res.getDimension(R.dimen.map_trip_stroke_width_selected_glow);

        final Drawable startIcon = drawablePointer(R.drawable.ic_maps_indicator_startpoint_list, 2);
        final Drawable pointIcon = drawableCenter(R.drawable.ic_maps_product_default, 2);
        final Drawable endIcon = drawablePointer(R.drawable.ic_maps_indicator_endpoint_list, 2);

        final Drawable deviceLocationIcon = drawableCenter(R.drawable.location_on, 2);
        final Drawable referenceLocationIcon = drawablePointer(R.drawable.da_marker_red, 2);

        final Drawable glowIcon = drawableCenter(R.drawable.station_glow, 2);
        final Drawable stationDefaultIcon = drawableCenter(R.drawable.ic_maps_product_default, 2);
        final Drawable stationHighspeedIcon = drawableCenter(R.drawable.product_highspeed_color_22dp, 2);
        final Drawable stationTrainIcon = drawableCenter(R.drawable.product_train_color_22dp, 2);
        final Drawable stationSuburbanIcon = drawableCenter(R.drawable.product_suburban_color_22dp, 2);
        final Drawable stationSubwayIcon = drawableCenter(R.drawable.product_subway_color_22dp, 2);
        final Drawable stationTramIcon = drawableCenter(R.drawable.product_tram_color_22dp, 2);
        final Drawable stationBusIcon = drawableCenter(R.drawable.product_bus_color_22dp, 2);
        final Drawable stationFerryIcon = drawableCenter(R.drawable.product_ferry_color_22dp, 2);
        final Drawable stationCablecarIcon = drawableCenter(R.drawable.product_cablecar_color_22dp, 2);
        final Drawable stationCallIcon = drawableCenter(R.drawable.product_call_color_22dp, 2);

        getController().setZoom(Constants.INITIAL_MAP_ZOOM_LEVEL);

        getOverlays().add(new Overlay() {
            @Override
            public void draw(final Canvas canvas, final MapView mapView, final boolean shadow) {
                if (!shadow) {
                    final Projection projection = mapView.getProjection();
                    final android.graphics.Point point = new android.graphics.Point();

                    if (areaAware != null) {
                        final Point[] area = areaAware.getArea();
                        if (area != null) {
                            final Paint paint = new Paint();
                            paint.setAntiAlias(true);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(AREA_FILL_COLOR);

                            final Path path = pointsToPath(projection, area);
                            path.close();

                            path.setFillType(FillType.INVERSE_WINDING);

                            canvas.drawPath(path, paint);
                        }
                    }

                    if (fromViaToAware != null) {
                        final List<Point> path = new ArrayList<>(3);
                        final Point from = fromViaToAware.getFrom();
                        if (from != null)
                            path.add(from);
                        final Point via = fromViaToAware.getVia();
                        if (via != null)
                            path.add(via);
                        final Point to = fromViaToAware.getTo();
                        if (to != null)
                            path.add(to);

                        if (path.size() >= 2) {
                            final Paint paint = new Paint();
                            paint.setAntiAlias(true);
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setStrokeJoin(Paint.Join.ROUND);
                            paint.setStrokeCap(Paint.Cap.ROUND);

                            paint.setColor(Color.DKGRAY);
                            paint.setAlpha(92);
                            paint.setStrokeWidth(tripStrokeWidth);
                            canvas.drawPath(pointsToPath(projection, path), paint);
                        }

                        if (from != null) {
                            projection.toPixels(new GeoPoint(from.lat, from.lon), point);
                            drawAt(canvas, startIcon, point.x, point.y, false);
                        }

                        if (to != null) {
                            projection.toPixels(new GeoPoint(to.lat, to.lon), point);
                            drawAt(canvas, endIcon, point.x, point.y, false);
                        }
                    }

                    if (tripAware != null) {
                        final Trip trip = tripAware.getTrip();

                        final Paint paint = new Paint();
                        paint.setAntiAlias(true);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeJoin(Paint.Join.ROUND);
                        paint.setStrokeCap(Paint.Cap.ROUND);

                        // first paint all unselected legs
                        for (final Trip.Leg leg : trip.legs) {
                            if (!tripAware.isSelectedLeg(leg)) {
                                final Path path = pointsToPath(projection, leg.path);

                                paint.setColor(leg instanceof Public ? Color.RED : Color.DKGRAY);
                                paint.setAlpha(92);
                                paint.setStrokeWidth(tripStrokeWidth);
                                canvas.drawPath(path, paint);
                            }
                        }

                        // then paint selected legs
                        for (final Trip.Leg leg : trip.legs) {
                            if (tripAware.isSelectedLeg(leg)) {
                                final List<Point> points = leg.path;
                                final Path path = pointsToPath(projection, points);

                                paint.setColor(Color.GREEN);
                                paint.setAlpha(92);
                                paint.setStrokeWidth(tripStrokeWidthSelectedGlow);
                                canvas.drawPath(path, paint);

                                paint.setColor(leg instanceof Public ? Color.RED : Color.DKGRAY);
                                paint.setAlpha(128);
                                paint.setStrokeWidth(tripStrokeWidthSelected);
                                canvas.drawPath(path, paint);

                                if (leg instanceof Public && !points.isEmpty()) {
                                    final Public publicLeg = (Public) leg;

                                    final int lat;
                                    final int lon;

                                    final int size = points.size();
                                    if (size >= 2) {
                                        final int pivot = size / 2;
                                        final Point p1 = points.get(pivot - 1);
                                        final Point p2 = points.get(pivot);
                                        lat = (p1.lat + p2.lat) / 2;
                                        lon = (p1.lon + p2.lon) / 2;
                                    } else if (size == 1) {
                                        final Point p = points.get(0);
                                        lat = p.lat;
                                        lon = p.lon;
                                    } else {
                                        lat = 0;
                                        lon = 0;
                                    }
                                    projection.toPixels(new GeoPoint(lat, lon), point);

                                    final LineView lineView = (LineView) inflater.inflate(R.layout.map_trip_line, null);
                                    lineView.setLine(publicLeg.line);
                                    lineView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, 0),
                                            MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, 0));
                                    final int width = lineView.getMeasuredWidth();
                                    final int height = lineView.getMeasuredHeight();
                                    lineView.layout(point.x - width / 2, point.y - height / 2, point.x + width / 2,
                                            point.y + height / 2);
                                    // since point.x is ignored in layout (why?), we need to translate canvas
                                    // ourselves
                                    canvas.save();
                                    canvas.translate(point.x - width / 2, point.y - height / 2);
                                    lineView.draw(canvas);
                                    canvas.restore();
                                }
                            }
                        }

                        // then paint decorators
                        final Leg firstLeg = trip.legs.get(0);
                        final Leg lastLeg = trip.legs.get(trip.legs.size() - 1);

                        for (final Trip.Leg leg : trip.legs) {
                            if (!leg.path.isEmpty()) {
                                final Point firstPoint = leg.path.get(0);
                                final Point lastPoint = leg.path.get(leg.path.size() - 1);

                                if (firstPoint == lastPoint) {
                                    projection.toPixels(new GeoPoint(firstPoint.lat, firstPoint.lon), point);
                                    drawAt(canvas, startIcon, point.x, point.y, false);
                                } else if (leg == firstLeg || leg == lastLeg) {
                                    if (leg == firstLeg) {
                                        projection.toPixels(new GeoPoint(firstPoint.lat, firstPoint.lon), point);
                                        drawAt(canvas, startIcon, point.x, point.y, false);
                                    }

                                    if (leg == lastLeg) {
                                        projection.toPixels(new GeoPoint(lastPoint.lat, lastPoint.lon), point);
                                        drawAt(canvas, endIcon, point.x, point.y, false);
                                    }
                                } else {
                                    projection.toPixels(new GeoPoint(firstPoint.lat, firstPoint.lon), point);
                                    drawAt(canvas, pointIcon, point.x, point.y, false);
                                    projection.toPixels(new GeoPoint(lastPoint.lat, lastPoint.lon), point);
                                    drawAt(canvas, pointIcon, point.x, point.y, false);
                                }
                            }
                        }
                    }

                    if (locationAware != null) {
                        final Point deviceLocation = locationAware.getDeviceLocation();
                        if (deviceLocation != null) {
                            projection.toPixels(new GeoPoint(deviceLocation.lat, deviceLocation.lon), point);
                            drawAt(canvas, deviceLocationIcon, point.x, point.y, false);
                        }

                        final Location referenceLocation = locationAware.getReferenceLocation();
                        if (referenceLocation != null) {
                            projection.toPixels(new GeoPoint(referenceLocation.lat, referenceLocation.lon), point);
                            drawAt(canvas, referenceLocationIcon, point.x, point.y, false);
                        }
                    }

                    if (stationsAware != null) {
                        final List<Station> stations = stationsAware.getStations();
                        if (stations != null) {
                            Station selectedStation = null;

                            for (final Station station : stations) {
                                if (station.location.hasLocation()) {
                                    projection.toPixels(new GeoPoint(station.location.lat, station.location.lon),
                                            point);

                                    if (stationsAware.isSelectedStation(station.location.id)) {
                                        drawAt(canvas, glowIcon, point.x, point.y, false);
                                        selectedStation = station;
                                    }

                                    final Product product = station.getRelevantProduct();
                                    if (product == null)
                                        drawAt(canvas, stationDefaultIcon, point.x, point.y, false);
                                    else if (product == Product.HIGH_SPEED_TRAIN)
                                        drawAt(canvas, stationHighspeedIcon, point.x, point.y, false);
                                    else if (product == Product.REGIONAL_TRAIN)
                                        drawAt(canvas, stationTrainIcon, point.x, point.y, false);
                                    else if (product == Product.SUBURBAN_TRAIN)
                                        drawAt(canvas, stationSuburbanIcon, point.x, point.y, false);
                                    else if (product == Product.SUBWAY)
                                        drawAt(canvas, stationSubwayIcon, point.x, point.y, false);
                                    else if (product == Product.TRAM)
                                        drawAt(canvas, stationTramIcon, point.x, point.y, false);
                                    else if (product == Product.BUS)
                                        drawAt(canvas, stationBusIcon, point.x, point.y, false);
                                    else if (product == Product.FERRY)
                                        drawAt(canvas, stationFerryIcon, point.x, point.y, false);
                                    else if (product == Product.CABLECAR)
                                        drawAt(canvas, stationCablecarIcon, point.x, point.y, false);
                                    else if (product == Product.ON_DEMAND)
                                        drawAt(canvas, stationCallIcon, point.x, point.y, false);
                                    else
                                        drawAt(canvas, stationDefaultIcon, point.x, point.y, false);
                                }
                            }

                            if (selectedStation != null) {
                                projection.toPixels(
                                        new GeoPoint(selectedStation.location.lat, selectedStation.location.lon),
                                        point);
                                final TextView bubble = new TextView(getContext());
                                bubble.setBackgroundResource(R.drawable.popup_dir_pointer_button);
                                bubble.setText(selectedStation.location.name);
                                bubble.setTypeface(Typeface.DEFAULT_BOLD);
                                bubble.setTextSize(TypedValue.COMPLEX_UNIT_PX, stationFontSize);
                                bubble.setIncludeFontPadding(false);
                                bubble.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, 0),
                                        MeasureSpec.makeMeasureSpec(MeasureSpec.UNSPECIFIED, 0));
                                final int width = bubble.getMeasuredWidth();
                                final int height = bubble.getMeasuredHeight();
                                bubble.layout(point.x - width / 2, point.y - height / 2, point.x + width / 2,
                                        point.y + height / 2);
                                // since point.x is ignored in layout (why?), we need to translate canvas
                                // ourselves
                                canvas.save();
                                canvas.translate(point.x - width / 2,
                                        point.y - height - stationDefaultIcon.getIntrinsicHeight() / 2.5f);
                                bubble.draw(canvas);
                                canvas.restore();
                            }
                        }
                    }
                }
            }

            private Path pointsToPath(final Projection projection, final List<Point> points) {
                final Path path = new Path();
                path.incReserve(points.size());

                final android.graphics.Point point = new android.graphics.Point();

                for (final Point p : points) {
                    projection.toPixels(new GeoPoint(p.lat, p.lon), point);
                    if (path.isEmpty())
                        path.moveTo(point.x, point.y);
                    else
                        path.lineTo(point.x, point.y);
                }

                return path;
            }

            private Path pointsToPath(final Projection projection, final Point[] points) {
                final Path path = new Path();
                path.incReserve(points.length);

                final android.graphics.Point point = new android.graphics.Point();

                for (final Point p : points) {
                    projection.toPixels(new GeoPoint(p.lat, p.lon), point);
                    if (path.isEmpty())
                        path.moveTo(point.x, point.y);
                    else
                        path.lineTo(point.x, point.y);
                }

                return path;
            }

            @Override
            public boolean onTap(final GeoPoint p, final MapView mapView) {
                final double tappedLat = p.getLatitudeE6() / 1E6;
                final double tappedLon = p.getLongitudeE6() / 1E6;
                boolean consumed = false;

                final float[] distanceBetweenResults = new float[1];

                if (tripAware != null) {
                    int tappedLegIndex = -1;
                    float tappedPointDistance = 0;

                    int iRoute = 0;
                    for (final Leg leg : tripAware.getTrip().legs) {
                        for (final Point point : leg.path) {
                            android.location.Location.distanceBetween(tappedLat, tappedLon, point.lat / 1E6,
                                    point.lon / 1E6, distanceBetweenResults);
                            final float distance = distanceBetweenResults[0];
                            if (tappedLegIndex == -1 || distance < tappedPointDistance) {
                                tappedLegIndex = iRoute;
                                tappedPointDistance = distance;
                            }
                        }

                        iRoute++;
                    }

                    if (tappedLegIndex != -1) {
                        tripAware.selectLeg(tappedLegIndex);
                        consumed = true;
                    }
                }

                if (stationsAware != null) {
                    Station tappedStation = null;
                    float tappedStationDistance = 0;

                    for (final Station station : stationsAware.getStations()) {
                        android.location.Location.distanceBetween(tappedLat, tappedLon, station.location.lat / 1E6,
                                station.location.lon / 1E6, distanceBetweenResults);
                        final float distance = distanceBetweenResults[0];
                        if (tappedStation == null || distance < tappedStationDistance) {
                            tappedStation = station;
                            tappedStationDistance = distance;
                        }
                    }

                    if (locationAware != null) {
                        if (tappedStation == null) {
                            stationsAware.selectStation(null);
                            consumed = true;
                        } else {
                            final Point deviceLocation = locationAware.getDeviceLocation();
                            if (deviceLocation != null) {
                                android.location.Location.distanceBetween(tappedLat, tappedLon,
                                        deviceLocation.lat / 1E6, deviceLocation.lon / 1E6, distanceBetweenResults);
                                final float distance = distanceBetweenResults[0];
                                if (distance < tappedStationDistance) {
                                    stationsAware.selectStation(null);
                                    consumed = true;
                                }
                            }
                        }
                    }

                    if (!consumed && tappedStation != null) {
                        stationsAware.selectStation(tappedStation);
                        consumed = true;
                    }
                }

                return consumed;
            }
        });
    }

    public void setFromViaToAware(final FromViaToAware fromViaToAware) {
        this.fromViaToAware = fromViaToAware;
        invalidate();
    }

    public void setTripAware(final TripAware tripAware) {
        this.tripAware = tripAware;
        invalidate();
    }

    public void setStationsAware(final StationsAware stationsAware) {
        this.stationsAware = stationsAware;
        invalidate();
    }

    public void setLocationAware(final LocationAware locationAware) {
        this.locationAware = locationAware;
        invalidate();
    }

    public void setAreaAware(final AreaAware areaAware) {
        this.areaAware = areaAware;
        invalidate();
    }

    public void animateToLocation(final int lat, final int lon) {
        if (lat == 0 && lon == 0)
            return;

        final GeoPoint point = new GeoPoint(lat, lon);

        if (firstLocation)
            getController().setCenter(point);
        else
            getController().animateTo(point);

        firstLocation = false;
    }

    public void zoomToAll(final int maxZoomLevel) {
        zoomLocked = true;

        final boolean hasLegSelection = tripAware != null && tripAware.hasSelection();

        int minLat = Integer.MAX_VALUE, maxLat = Integer.MIN_VALUE;
        int minLon = Integer.MAX_VALUE, maxLon = Integer.MIN_VALUE;

        boolean anything = false;

        if (areaAware != null) {
            final Point[] area = areaAware.getArea();
            if (area != null) {
                for (final Point p : area) {
                    final int lat = p.lat;
                    final int lon = p.lon;

                    if (lat < minLat)
                        minLat = lat;
                    if (lat > maxLat)
                        maxLat = lat;
                    if (lon < minLon)
                        minLon = lon;
                    if (lon > maxLon)
                        maxLon = lon;

                    anything = true;
                }
            }
        }

        if (fromViaToAware != null) {
            final Point from = fromViaToAware.getFrom();
            if (from != null) {
                if (from.lat < minLat)
                    minLat = from.lat;
                if (from.lat > maxLat)
                    maxLat = from.lat;
                if (from.lon < minLon)
                    minLon = from.lon;
                if (from.lon > maxLon)
                    maxLon = from.lon;

                anything = true;
            }

            final Point via = fromViaToAware.getVia();
            if (via != null) {
                if (via.lat < minLat)
                    minLat = via.lat;
                if (via.lat > maxLat)
                    maxLat = via.lat;
                if (via.lon < minLon)
                    minLon = via.lon;
                if (via.lon > maxLon)
                    maxLon = via.lon;

                anything = true;
            }

            final Point to = fromViaToAware.getTo();
            if (to != null) {
                if (to.lat < minLat)
                    minLat = to.lat;
                if (to.lat > maxLat)
                    maxLat = to.lat;
                if (to.lon < minLon)
                    minLon = to.lon;
                if (to.lon > maxLon)
                    maxLon = to.lon;

                anything = true;
            }
        }

        if (tripAware != null) {
            for (final Leg leg : tripAware.getTrip().legs) {
                if (!hasLegSelection || tripAware.isSelectedLeg(leg)) {
                    for (final Point p : leg.path) {
                        final int lat = p.lat;
                        final int lon = p.lon;

                        if (lat < minLat)
                            minLat = lat;
                        if (lat > maxLat)
                            maxLat = lat;
                        if (lon < minLon)
                            minLon = lon;
                        if (lon > maxLon)
                            maxLon = lon;

                        anything = true;
                    }
                }
            }
        }

        if (locationAware != null && !hasLegSelection) {
            final Location referenceLocation = locationAware.getReferenceLocation();
            if (referenceLocation != null) {
                if (referenceLocation.lat < minLat)
                    minLat = referenceLocation.lat;
                if (referenceLocation.lat > maxLat)
                    maxLat = referenceLocation.lat;
                if (referenceLocation.lon < minLon)
                    minLon = referenceLocation.lon;
                if (referenceLocation.lon > maxLon)
                    maxLon = referenceLocation.lon;

                anything = true;
            } else {
                final Point location = locationAware.getDeviceLocation();
                if (location != null) {
                    if (location.lat < minLat)
                        minLat = location.lat;
                    if (location.lat > maxLat)
                        maxLat = location.lat;
                    if (location.lon < minLon)
                        minLon = location.lon;
                    if (location.lon > maxLon)
                        maxLon = location.lon;

                    anything = true;
                }
            }
        }

        if (anything) {
            final MapController controller = getController();

            final float spanLat = (maxLat - minLat) * 1.1f;
            final float spanLon = (maxLon - minLon) * 1.1f;
            controller.zoomToSpan((int) spanLat, (int) spanLon);
            if (getZoomLevel() > maxZoomLevel)
                controller.setZoom(maxZoomLevel);

            final int centerLat = (maxLat + minLat) / 2;
            final int centerLon = (maxLon + minLon) / 2;
            controller.setCenter(new GeoPoint(centerLat, centerLon));
        }
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        if (zoomLocked)
            zoomToAll(Constants.INITIAL_MAP_ZOOM_LEVEL);

        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent ev) {
        zoomLocked = false;

        return super.onTouchEvent(ev);
    }

    private Drawable drawablePointer(final int resId, final int sizeDivider) {
        final Resources res = getResources();
        final Drawable drawable = res.getDrawable(resId);
        drawable.setBounds(-drawable.getIntrinsicWidth() / sizeDivider, -drawable.getIntrinsicHeight(),
                drawable.getIntrinsicWidth() / sizeDivider, 0);
        return drawable;
    }

    private Drawable drawableCenter(final int resId, final int sizeDivider) {
        final Resources res = getResources();
        final Drawable drawable = res.getDrawable(resId);
        drawable.setBounds(-drawable.getIntrinsicWidth() / sizeDivider, -drawable.getIntrinsicHeight() / sizeDivider,
                drawable.getIntrinsicWidth() / sizeDivider, drawable.getIntrinsicHeight() / sizeDivider);
        return drawable;
    }
}
