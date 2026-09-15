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

import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Style;
import de.schildbach.pte.dto.Trip;
import de.schildbach.pte.dto.Trip.Leg;
import de.schildbach.pte.dto.Trip.Public;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assigns colors to transfer stations for the lifetime of one trip query session, including trips loaded later by
 * scrolling. Colors are derived from a hash of the station, so they are reproducible across sessions. If a color is
 * too close to an already assigned color or to a line color, it is rehashed with a salt.
 *
 * Assignments are append-only: once a station has a color, it never changes, even if stations or line colors that
 * would have influenced the choice show up later.
 */
public final class StationColorAssigner {
    static final float SATURATION = 0.65f;
    static final float LIGHTNESS = 0.55f;
    static final float RESERVED_DISTANCE = 25f;
    private static final float USABLE_HUE_ARC = 300f;
    private static final float MIN_DISTANCE_FLOOR = 15f;
    private static final float MIN_DISTANCE_CEILING = 60f;
    private static final int MAX_SALTS = 8;
    private static final int MAX_SALTS_RESERVED = 64;
    private static final float MIN_RESERVED_SATURATION = 0.25f;

    private final Map<String, Integer> assignedColors = new HashMap<>();
    private final List<Float> assignedHues = new ArrayList<>();
    private final Set<Integer> reservedHues = new HashSet<>();
    private final Set<String> relevantStations = new HashSet<>();

    public static @Nullable String keyOf(final Location location) {
        if (location.hasId())
            return location.id;
        return location.name;
    }

    /**
     * Registers trips of the session. Can be called repeatedly with the complete, growing list of trips.
     */
    public void registerTrips(final Collection<Trip> trips) {
        // first reserve line colors and collect all start, destination and transfer stations
        for (final Trip trip : trips) {
            addRelevant(trip.from);
            addRelevant(trip.to);
            if (trip.legs == null)
                continue;
            for (final Leg leg : trip.legs) {
                if (leg instanceof Public) {
                    final Style style = ((Public) leg).line.style;
                    if (style != null) {
                        reserveLineColor(style.backgroundColor);
                        if (style.backgroundColor2 != 0)
                            reserveLineColor(style.backgroundColor2);
                    }
                }
            }
            final Public firstPublicLeg = trip.getFirstPublicLeg();
            if (firstPublicLeg != null)
                addRelevant(firstPublicLeg.departureStop.location);
            final Public lastPublicLeg = trip.getLastPublicLeg();
            if (lastPublicLeg != null)
                addRelevant(lastPublicLeg.arrivalStop.location);
            Public previous = null;
            for (final Leg leg : trip.legs) {
                if (leg instanceof Public) {
                    if (previous != null) {
                        addRelevant(previous.arrivalStop.location);
                        addRelevant(((Public) leg).departureStop.location);
                    }
                    previous = (Public) leg;
                }
            }
        }

        // then assign transfer stations in trip order, so assignment doesn't depend on drawing order
        for (final Trip trip : trips) {
            if (trip.legs == null)
                continue;
            Public previous = null;
            for (final Leg leg : trip.legs) {
                if (leg instanceof Public) {
                    if (previous != null) {
                        colorFor(previous.arrivalStop.location);
                        colorFor(((Public) leg).departureStop.location);
                    }
                    previous = (Public) leg;
                }
            }
        }

        // last, assign stations that are passed through, but relevant in any trip
        for (final Trip trip : trips) {
            if (trip.legs == null)
                continue;
            for (final Leg leg : trip.legs) {
                if (leg instanceof Public) {
                    final List<Stop> intermediateStops = ((Public) leg).intermediateStops;
                    if (intermediateStops != null)
                        for (final Stop stop : intermediateStops)
                            if (isRelevant(stop.location))
                                colorFor(stop.location);
                }
            }
        }
    }

    public boolean isRelevant(final Location location) {
        final String key = keyOf(location);
        return key != null && relevantStations.contains(key);
    }

    /**
     * @return the color already assigned to the station, or {@code null} if it has none. Never assigns, so it is safe
     *         to call while drawing.
     */
    public @Nullable Integer assignedColor(final Location location) {
        final String key = keyOf(location);
        return key != null ? assignedColors.get(key) : null;
    }

    private void addRelevant(final @Nullable Location location) {
        if (location == null)
            return;
        final String key = keyOf(location);
        if (key != null)
            relevantStations.add(key);
    }

    private @Nullable Integer colorFor(final Location location) {
        final String key = keyOf(location);
        return key != null ? colorFor(key) : null;
    }

    /**
     * Reserves the hue of a line color, so that future station colors keep a distance to it. Unsaturated colors like
     * grey, black or white don't reserve anything, station colors are saturated enough to be distinguishable anyway.
     */
    public void reserveLineColor(final int argb) {
        final float[] hsl = rgbToHsl(argb);
        if (hsl[1] >= MIN_RESERVED_SATURATION && hsl[2] > 0.1f && hsl[2] < 0.9f)
            reservedHues.add(Math.round(hsl[0]) % 360);
    }

    public int colorFor(final String key) {
        final Integer assigned = assignedColors.get(key);
        if (assigned != null)
            return assigned;

        final float minDistance = minDistance(assignedHues.size() + 1);
        float bestHue = 0;
        boolean bestReservedOk = false;
        float bestMargin = Float.NEGATIVE_INFINITY;
        for (int salt = 0; salt < MAX_SALTS_RESERVED; salt++) {
            final float hue = hue(key, salt);
            final float reservedMargin = minHueDistance(hue, reservedHues) - RESERVED_DISTANCE;
            final float stationMargin = minHueDistance(hue, assignedHues) - minDistance;
            final boolean reservedOk = reservedMargin >= 0;
            final float margin = reservedOk ? stationMargin : reservedMargin;
            if (salt == 0 || (reservedOk && !bestReservedOk)
                    || (reservedOk == bestReservedOk && margin > bestMargin)) {
                bestHue = hue;
                bestReservedOk = reservedOk;
                bestMargin = margin;
            }
            if (reservedOk && stationMargin >= 0)
                break;
            // give up on station distance after a few tries, but keep looking for a hue outside of reserved zones
            if (salt >= MAX_SALTS - 1 && bestReservedOk)
                break;
        }

        final int color = hslToRgb(bestHue, SATURATION, LIGHTNESS);
        assignedColors.put(key, color);
        assignedHues.add(bestHue);
        return color;
    }

    static float minDistance(final int numStations) {
        return Math.max(MIN_DISTANCE_FLOOR, Math.min(MIN_DISTANCE_CEILING, USABLE_HUE_ARC / numStations));
    }

    static float hue(final String key, final int salt) {
        // FNV-1a, 32 bit
        int hash = 0x811c9dc5;
        for (final byte b : (key + '#' + salt).getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xff;
            hash *= 0x01000193;
        }
        return ((hash & 0xffffffffL) % 3600) / 10f;
    }

    static float hueDistance(final float hue1, final float hue2) {
        final float d = Math.abs(hue1 - hue2) % 360;
        return Math.min(d, 360 - d);
    }

    private static float minHueDistance(final float hue, final Collection<? extends Number> hues) {
        float min = 180;
        for (final Number other : hues)
            min = Math.min(min, hueDistance(hue, other.floatValue()));
        return min;
    }

    static int hslToRgb(final float h, final float s, final float l) {
        final float c = (1 - Math.abs(2 * l - 1)) * s;
        final float hp = (h % 360) / 60f;
        final float x = c * (1 - Math.abs(hp % 2 - 1));
        final float r, g, b;
        if (hp < 1) {
            r = c; g = x; b = 0;
        } else if (hp < 2) {
            r = x; g = c; b = 0;
        } else if (hp < 3) {
            r = 0; g = c; b = x;
        } else if (hp < 4) {
            r = 0; g = x; b = c;
        } else if (hp < 5) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }
        final float m = l - c / 2;
        return 0xff000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8)
                | Math.round((b + m) * 255);
    }

    static float[] rgbToHsl(final int argb) {
        final float r = ((argb >> 16) & 0xff) / 255f;
        final float g = ((argb >> 8) & 0xff) / 255f;
        final float b = (argb & 0xff) / 255f;
        final float max = Math.max(r, Math.max(g, b));
        final float min = Math.min(r, Math.min(g, b));
        final float l = (max + min) / 2;
        final float d = max - min;
        if (d == 0)
            return new float[] { 0, 0, l };
        final float s = d / (1 - Math.abs(2 * l - 1));
        float h;
        if (max == r)
            h = 60 * (((g - b) / d) % 6);
        else if (max == g)
            h = 60 * ((b - r) / d + 2);
        else
            h = 60 * ((r - g) / d + 4);
        if (h < 0)
            h += 360;
        return new float[] { h, s, l };
    }
}
