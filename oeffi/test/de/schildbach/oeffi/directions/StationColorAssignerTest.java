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

import de.schildbach.pte.dto.Line;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Product;
import de.schildbach.pte.dto.Stop;
import de.schildbach.pte.dto.Style;
import de.schildbach.pte.dto.Trip;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StationColorAssignerTest {
    private static final float EPSILON = 1.5f;

    @Test
    public void sameKeySameColor() {
        final StationColorAssigner assigner = new StationColorAssigner();
        final int color = assigner.colorFor("Hagen Hbf");
        assertEquals(color, assigner.colorFor("Hagen Hbf"));
        assertEquals(color, new StationColorAssigner().colorFor("Hagen Hbf"));
    }

    @Test
    public void noCollisionKeepsBaseColor() {
        final String[] keys = findKeysWithHueDistance(90, 180);
        final StationColorAssigner assigner = new StationColorAssigner();
        assertEquals(baseColor(keys[0]), assigner.colorFor(keys[0]));
        assertEquals(baseColor(keys[1]), assigner.colorFor(keys[1]));
    }

    @Test
    public void collisionRehashes() {
        final String[] keys = findKeysWithHueDistance(0, 5);
        final StationColorAssigner assigner = new StationColorAssigner();
        final int color1 = assigner.colorFor(keys[0]);
        final int color2 = assigner.colorFor(keys[1]);
        assertEquals(baseColor(keys[0]), color1);
        assertTrue(color2 != baseColor(keys[1]));
        assertTrue(hueDistance(color1, color2) >= StationColorAssigner.minDistance(2) - EPSILON);
    }

    @Test
    public void reservedHuesAvoided() {
        final StationColorAssigner assigner = new StationColorAssigner();
        final int[] lineColors = { 0xff00a000, 0xffe30613, 0xff0066b3 }; // green, red, blue
        for (final int lineColor : lineColors)
            assigner.reserveLineColor(lineColor);
        for (int i = 0; i < 40; i++) {
            final int color = assigner.colorFor("Station " + i);
            for (final int lineColor : lineColors)
                assertTrue("Station " + i,
                        hueDistance(color, lineColor) >= StationColorAssigner.RESERVED_DISTANCE - EPSILON);
        }
    }

    @Test
    public void greyLineColorsReserveNothing() {
        final String key = findKeyWithBaseHueNear(0);
        final StationColorAssigner assigner = new StationColorAssigner();
        assigner.reserveLineColor(0xff808080);
        assigner.reserveLineColor(0xff000000);
        assigner.reserveLineColor(0xffffffff);
        assertEquals(baseColor(key), assigner.colorFor(key));
    }

    @Test
    public void assignedColorsAreStable() {
        final StationColorAssigner assigner = new StationColorAssigner();
        final List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < 5; i++)
            colors.add(assigner.colorFor("Station " + i));
        for (int i = 5; i < 30; i++) {
            assigner.colorFor("Station " + i);
            assigner.reserveLineColor(StationColorAssigner.hslToRgb(i * 37 % 360, 0.8f, 0.5f));
        }
        for (int i = 0; i < 5; i++)
            assertEquals(colors.get(i).intValue(), assigner.colorFor("Station " + i));
    }

    @Test
    public void saturationAndLightness() {
        final StationColorAssigner assigner = new StationColorAssigner();
        for (int i = 0; i < 30; i++) {
            final float[] hsl = StationColorAssigner.rgbToHsl(assigner.colorFor("Station " + i));
            assertEquals(StationColorAssigner.SATURATION, hsl[1], 0.02f);
            assertEquals(StationColorAssigner.LIGHTNESS, hsl[2], 0.02f);
        }
    }

    @Test
    public void hslRoundTrip() {
        for (int h = 0; h < 360; h += 15) {
            final float[] hsl = StationColorAssigner.rgbToHsl(StationColorAssigner.hslToRgb(h, 0.65f, 0.55f));
            assertEquals(h, hsl[0], EPSILON);
        }
    }

    @Test
    public void minDistanceDependsOnNumberOfStations() {
        assertEquals(60f, StationColorAssigner.minDistance(2), 0.01f);
        assertEquals(30f, StationColorAssigner.minDistance(10), 0.01f);
        assertEquals(15f, StationColorAssigner.minDistance(20), 0.01f);
        assertEquals(15f, StationColorAssigner.minDistance(100), 0.01f);
    }

    @Test
    public void registerTrips() {
        final Location duesseldorf = station("1", "Düsseldorf Hbf");
        final Location hagen = station("2", "Hagen Hbf");
        final Location bochum = station("3", "Bochum Hbf");
        final Location witten = station("4", "Witten-Annen Nord");
        final Location essen = station("5", "Essen Hbf");

        // a: Düsseldorf -> Witten, passing through Hagen and Essen
        final Trip a = trip(leg("RE4", duesseldorf, witten, 0, 60, essen, hagen));
        // b: Düsseldorf -> Hagen -> Witten, transfer in Hagen
        final Trip b = trip(leg("RE7", duesseldorf, hagen, 10, 50), leg("S5", hagen, witten, 55, 75));

        final StationColorAssigner assigner = new StationColorAssigner();
        assigner.registerTrips(Collections.singletonList(a));
        assertTrue(assigner.isRelevant(duesseldorf));
        assertTrue(assigner.isRelevant(witten));
        assertFalse(assigner.isRelevant(hagen));
        assertNull(assigner.assignedColor(hagen));

        assigner.registerTrips(Arrays.asList(a, b));
        assertTrue(assigner.isRelevant(hagen));
        assertFalse(assigner.isRelevant(essen));
        assertNull(assigner.assignedColor(essen));
        assertNull(assigner.assignedColor(duesseldorf));
        final Integer hagenColor = assigner.assignedColor(hagen);
        assertNotNull(hagenColor);
        assertEquals(hagenColor.intValue(), assigner.colorFor("2"));

        // registering again changes nothing
        assigner.registerTrips(Arrays.asList(a, b));
        assertEquals(hagenColor, assigner.assignedColor(hagen));
    }

    @Test
    public void keyPrefersId() {
        assertEquals("42", StationColorAssigner.keyOf(station("42", "Foo")));
        assertEquals("Foo", StationColorAssigner.keyOf(new Location(LocationType.STATION, null, null, "Foo")));
    }

    private static Location station(final String id, final String name) {
        return new Location(LocationType.STATION, id, null, name);
    }

    private static Trip.Public leg(final String label, final Location from, final Location to, final int depMinute,
            final int arrMinute, final Location... intermediate) {
        final List<Stop> intermediateStops = new ArrayList<>();
        for (int i = 0; i < intermediate.length; i++) {
            final Date time = minute(depMinute + (arrMinute - depMinute) * (i + 1) / (intermediate.length + 1));
            intermediateStops.add(new Stop(intermediate[i], time, null, null, null, time, null, null, null));
        }
        final Line line = new Line(null, null, Product.REGIONAL_TRAIN, label, new Style(0xff808080, 0xffffffff));
        return new Trip.Public(line, to, new Stop(from, true, minute(depMinute), null, null, null),
                new Stop(to, false, minute(arrMinute), null, null, null), intermediateStops, null, null);
    }

    private static Trip trip(final Trip.Leg... legs) {
        final List<Trip.Leg> legList = Arrays.asList(legs);
        return new Trip(null, legs[0].departure, legs[legs.length - 1].arrival, legList, null, null,
                legs.length - 1);
    }

    private static Date minute(final int minute) {
        return new Date(1_700_000_000_000L + minute * 60_000L);
    }

    private static int baseColor(final String key) {
        return StationColorAssigner.hslToRgb(StationColorAssigner.hue(key, 0), StationColorAssigner.SATURATION,
                StationColorAssigner.LIGHTNESS);
    }

    private static float hueDistance(final int color1, final int color2) {
        return StationColorAssigner.hueDistance(StationColorAssigner.rgbToHsl(color1)[0],
                StationColorAssigner.rgbToHsl(color2)[0]);
    }

    private static String[] findKeysWithHueDistance(final float min, final float max) {
        final String first = "Station A";
        final float firstHue = StationColorAssigner.hue(first, 0);
        for (int i = 0; i < 10000; i++) {
            final String key = "Station B" + i;
            final float d = StationColorAssigner.hueDistance(firstHue, StationColorAssigner.hue(key, 0));
            if (d >= min && d <= max)
                return new String[] { first, key };
        }
        throw new AssertionError();
    }

    private static String findKeyWithBaseHueNear(final float hue) {
        for (int i = 0; i < 10000; i++) {
            final String key = "Station " + i;
            if (StationColorAssigner.hueDistance(hue, StationColorAssigner.hue(key, 0)) < 5)
                return key;
        }
        throw new AssertionError();
    }
}
