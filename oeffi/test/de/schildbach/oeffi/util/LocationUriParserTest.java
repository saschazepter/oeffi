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

import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import org.junit.Assert;
import org.junit.Test;

public class LocationUriParserTest {
    @Test
    public void contacts() throws Exception {
        final Location[] results = LocationUriParser.parseLocations("geo:0,0?q=Karl-Marx-Allee+84,+Berlin");

        Assert.assertEquals(1, results.length);
        final Location location = results[0];

        Assert.assertEquals(LocationType.ANY, location.type);
        Assert.assertFalse(location.hasCoord());
        Assert.assertEquals("Karl-Marx-Allee 84, Berlin", location.name);
    }

    @Test
    public void contactsMultiline() throws Exception {
        final Location[] resultsNewline = LocationUriParser.parseLocations("geo:0,0?q=Karl-Marx-Allee+84\nBerlin");

        Assert.assertEquals(1, resultsNewline.length);
        final Location locationNewline = resultsNewline[0];

        Assert.assertEquals(LocationType.ANY, locationNewline.type);
        Assert.assertFalse(locationNewline.hasCoord());
        Assert.assertEquals("Karl-Marx-Allee 84, Berlin", locationNewline.name);

        final Location[] resultsEncodedNewline = LocationUriParser
                .parseLocations("geo:0,0?q=Karl-Marx-Allee+84%0aBerlin");

        Assert.assertEquals(1, resultsEncodedNewline.length);
        final Location locationEncodedNewline = resultsEncodedNewline[0];

        Assert.assertEquals(LocationType.ANY, locationEncodedNewline.type);
        Assert.assertFalse(locationEncodedNewline.hasCoord());
        Assert.assertEquals("Karl-Marx-Allee 84, Berlin", locationEncodedNewline.name);

        final Location[] resultsComma = LocationUriParser.parseLocations("geo:0,0?q=Karl-Marx-Allee+84,%0aBerlin");

        Assert.assertEquals(1, resultsComma.length);
        final Location locationComma = resultsComma[0];

        Assert.assertEquals(LocationType.ANY, locationComma.type);
        Assert.assertFalse(locationComma.hasCoord());
        Assert.assertEquals("Karl-Marx-Allee 84, Berlin", locationComma.name);
    }

    @Test
    public void oldCalendar() throws Exception {
        final Location[] results = LocationUriParser.parseLocations("geo:0,0?q=Prinzenstraße 85, Berlin");

        Assert.assertEquals(1, results.length);
        final Location location = results[0];

        Assert.assertEquals(LocationType.ANY, location.type);
        Assert.assertFalse(location.hasCoord());
        Assert.assertEquals("Prinzenstraße 85, Berlin", location.name);
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
