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

package de.schildbach.oeffi.util;

import org.junit.Assert;
import org.junit.Test;

import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;

public class LocationUriParserTest {
    @Test
    public void googleNow() throws Exception {
        final Location[] results = LocationUriParser.parseLocations(
                "http://maps.google.com/maps/?saddr=@53.5878849,9.9050791&daddr=home@52.36894989013672,9.719719886779785&layer=t&dirflg=d");

        Assert.assertEquals(2, results.length);
        final Location fromLocation = results[0];
        final Location toLocation = results[1];

        Assert.assertEquals(LocationType.ADDRESS, fromLocation.type);
        Assert.assertEquals(9905079, fromLocation.getLonAs1E6());
        Assert.assertEquals(53587885, fromLocation.getLatAs1E6());
        Assert.assertNull(fromLocation.name);

        Assert.assertEquals(LocationType.ADDRESS, toLocation.type);
        Assert.assertEquals(9719720, toLocation.getLonAs1E6());
        Assert.assertEquals(52368950, toLocation.getLatAs1E6());
        Assert.assertEquals("home", toLocation.name);
    }

    @Test
    public void oldGoogleNow() throws Exception {
        final Location[] results = LocationUriParser.parseLocations(
                "http://maps.google.com/maps?daddr=52.52568054199219,13.374129295349121(Work)&dirflg=r&saddr=52.5360876,13.4263088");

        Assert.assertEquals(2, results.length);
        final Location fromLocation = results[0];
        final Location toLocation = results[1];

        Assert.assertEquals(LocationType.COORD, fromLocation.type);
        Assert.assertEquals(13426309, fromLocation.getLonAs1E6());
        Assert.assertEquals(52536088, fromLocation.getLatAs1E6());
        Assert.assertNull(fromLocation.name);

        Assert.assertEquals(LocationType.ADDRESS, toLocation.type);
        Assert.assertEquals(13374129, toLocation.getLonAs1E6());
        Assert.assertEquals(52525681, toLocation.getLatAs1E6());
        Assert.assertEquals("Work", toLocation.name);
    }

    @Test
    public void artem() throws Exception {
        final Location[] results = LocationUriParser.parseLocations(
                "http://maps.google.com/maps?saddr=52.5474,13.344510000000014&daddr=52.5057193,13.353883799999949");

        Assert.assertEquals(2, results.length);
        final Location fromLocation = results[0];
        final Location toLocation = results[1];

        Assert.assertEquals(LocationType.COORD, fromLocation.type);
        Assert.assertEquals(13344510, fromLocation.getLonAs1E6());
        Assert.assertEquals(52547400, fromLocation.getLatAs1E6());
        Assert.assertNull(fromLocation.name);

        Assert.assertEquals(LocationType.COORD, toLocation.type);
        Assert.assertEquals(13353884, toLocation.getLonAs1E6());
        Assert.assertEquals(52505719, toLocation.getLatAs1E6());
        Assert.assertNull(toLocation.name);
    }

    @Test
    public void googleNavigationThrownByGoogleNow() throws Exception {
        // "Take me to Alexanderplatz"

        final Location[] results = LocationUriParser.parseLocations(
                "google.navigation:title=Alexanderplatz,+10178+Berlin&ll=52.521918,13.413215&token=Fb5rIQMdX6vMACFuFuRmwwH8MA&entry=r&mode=d");

        Assert.assertEquals(1, results.length);
        final Location location = results[0];

        Assert.assertEquals(LocationType.ADDRESS, location.type);
        Assert.assertEquals(13413215, location.getLonAs1E6());
        Assert.assertEquals(52521918, location.getLatAs1E6());
        Assert.assertEquals("Alexanderplatz, 10178 Berlin", location.name);
    }

    @Test
    public void googleNavigation() throws Exception {
        final Location[] results = LocationUriParser.parseLocations(
                "google.navigation:///?ll=52.512845,13.420671&q=Rungestraße+20,+10179+Berlin&title=c-base&entry=w&opt=+flg=0x3000000");

        Assert.assertEquals(1, results.length);
        final Location location = results[0];

        Assert.assertEquals(LocationType.ADDRESS, location.type);
        Assert.assertEquals(13420671, location.getLonAs1E6());
        Assert.assertEquals(52512845, location.getLatAs1E6());
        Assert.assertEquals("c-base", location.name);
    }

    @Test
    public void googleNavigationOpaque() throws Exception {
        final Location[] results = LocationUriParser.parseLocations(
                "google.navigation:q=Work&ll=47.460044860839844,9.6347074508667&mode=d&entry=r&altvia=47.4140989,9.7192978&altvia=47.460046399999996,9.6350649");

        Assert.assertEquals(1, results.length);
        final Location location = results[0];

        Assert.assertEquals(LocationType.ADDRESS, location.type);
        Assert.assertEquals(9634707, location.getLonAs1E6());
        Assert.assertEquals(47460045, location.getLatAs1E6());
        Assert.assertEquals("Work", location.name);
    }

    @Test
    public void googleNavigationQueryOnly() throws Exception {
        final Location[] results = LocationUriParser.parseLocations("google.navigation:///?q=gleimstr.&mode=w&entry=v");

        Assert.assertEquals(1, results.length);
        final Location location = results[0];

        Assert.assertEquals(LocationType.ANY, location.type);
        Assert.assertEquals(0, location.getLonAs1E6());
        Assert.assertEquals(0, location.getLatAs1E6());
        Assert.assertEquals("gleimstr.", location.name);
    }

    @Test
    public void googleNavigationTitle() throws Exception {
        final Location[] results = LocationUriParser.parseLocations(
                "google.navigation:///?ll=52.535836,13.401525&title=Zionskirchstraße+7,+10119+Berlin,+Germany&entry=w");

        Assert.assertEquals(1, results.length);
        final Location location = results[0];

        Assert.assertEquals(LocationType.ADDRESS, location.type);
        Assert.assertEquals(13401525, location.getLonAs1E6());
        Assert.assertEquals(52535836, location.getLatAs1E6());
        Assert.assertEquals("Zionskirchstraße 7, 10119 Berlin, Germany", location.name);
    }

    @Test
    public void googleNavigationQueryOnlyMixed() throws Exception {
        final Location[] results = LocationUriParser.parseLocations(
                "google.navigation:///?q=52.513064,13.420106000000033(C-Base,+Rungestraße,+Berlin,+Germany)&entry=w");

        Assert.assertEquals(1, results.length);
        final Location location = results[0];

        Assert.assertEquals(LocationType.ADDRESS, location.type);
        Assert.assertEquals(13420106, location.getLonAs1E6());
        Assert.assertEquals(52513064, location.getLatAs1E6());
        Assert.assertEquals("C-Base, Rungestraße, Berlin, Germany", location.name);
    }

    @Test
    public void contacts() throws Exception {
        final Location[] results = LocationUriParser.parseLocations("geo:0,0?q=Karl-Marx-Allee+84,+Berlin");

        Assert.assertEquals(1, results.length);
        final Location location = results[0];

        Assert.assertEquals(LocationType.ANY, location.type);
        Assert.assertEquals(0, location.getLatAs1E6());
        Assert.assertEquals(0, location.getLonAs1E6());
        Assert.assertEquals("Karl-Marx-Allee 84, Berlin", location.name);
    }

    @Test
    public void contactsMultiline() throws Exception {
        final Location[] resultsNewline = LocationUriParser.parseLocations("geo:0,0?q=Karl-Marx-Allee+84\nBerlin");

        Assert.assertEquals(1, resultsNewline.length);
        final Location locationNewline = resultsNewline[0];

        Assert.assertEquals(LocationType.ANY, locationNewline.type);
        Assert.assertEquals(0, locationNewline.getLatAs1E6());
        Assert.assertEquals(0, locationNewline.getLonAs1E6());
        Assert.assertEquals("Karl-Marx-Allee 84, Berlin", locationNewline.name);

        final Location[] resultsEncodedNewline = LocationUriParser
                .parseLocations("geo:0,0?q=Karl-Marx-Allee+84%0aBerlin");

        Assert.assertEquals(1, resultsEncodedNewline.length);
        final Location locationEncodedNewline = resultsEncodedNewline[0];

        Assert.assertEquals(LocationType.ANY, locationEncodedNewline.type);
        Assert.assertEquals(0, locationEncodedNewline.getLatAs1E6());
        Assert.assertEquals(0, locationEncodedNewline.getLonAs1E6());
        Assert.assertEquals("Karl-Marx-Allee 84, Berlin", locationEncodedNewline.name);

        final Location[] resultsComma = LocationUriParser.parseLocations("geo:0,0?q=Karl-Marx-Allee+84,%0aBerlin");

        Assert.assertEquals(1, resultsComma.length);
        final Location locationComma = resultsComma[0];

        Assert.assertEquals(LocationType.ANY, locationComma.type);
        Assert.assertEquals(0, locationComma.getLatAs1E6());
        Assert.assertEquals(0, locationComma.getLonAs1E6());
        Assert.assertEquals("Karl-Marx-Allee 84, Berlin", locationComma.name);
    }

    @Test
    public void oldCalendar() throws Exception {
        final Location[] results = LocationUriParser.parseLocations("geo:0,0?q=Prinzenstraße 85, Berlin");

        Assert.assertEquals(1, results.length);
        final Location location = results[0];

        Assert.assertEquals(LocationType.ANY, location.type);
        Assert.assertEquals(0, location.getLatAs1E6());
        Assert.assertEquals(0, location.getLonAs1E6());
        Assert.assertEquals("Prinzenstraße 85, Berlin", location.name);
    }

    @Test
    public void calendar() throws Exception {
        final Location[] results = LocationUriParser.parseLocations(
                "http://maps.google.com/maps?q=motion*s Tanz- und Bewegungsstudio - Stella Caric GmbH, Prinzenstraße 85, Aufgang B1 - Zugang von der Oranienstraße, 10969 Berlin, Germany&sll=52.50352,13.409187999999972&radius=5");

        Assert.assertEquals(1, results.length);
        final Location location = results[0];

        Assert.assertEquals(LocationType.ADDRESS, location.type);
        Assert.assertEquals(52503520, location.getLatAs1E6());
        Assert.assertEquals(13409188, location.getLonAs1E6());
        Assert.assertEquals(
                "motion*s Tanz- und Bewegungsstudio - Stella Caric GmbH, Prinzenstraße 85, Aufgang B1 - Zugang von der Oranienstraße, 10969 Berlin, Germany",
                location.name);
    }

    @Test
    public void geoVariant() throws Exception {
        final Location[] results = LocationUriParser.parseLocations("geo:52.1333313,11.60000038?z=6");

        Assert.assertEquals(1, results.length);
        final Location location = results[0];

        Assert.assertEquals(LocationType.COORD, location.type);
        Assert.assertEquals(52133331, location.getLatAs1E6());
        Assert.assertEquals(11600000, location.getLonAs1E6());
        Assert.assertNull(location.name);
    }

    @Test(expected = RuntimeException.class)
    public void exceptionBecauseOfScheme() throws Exception {
        LocationUriParser.parseLocations("foo:bar");
    }
}
