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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.Point;

public class LocationUriParser {
    public static final Location[] parseLocations(final String encodedUriString) {
        URI uri = null;
        try {
            uri = new URI(encodedUriString);
        } catch (final URISyntaxException x) {
            try {
                // work around uri encoding bug in Google Calendar
                uri = new URI(encodedUriString.replace(' ', '+').replaceAll("\n", "%0a"));
            } catch (final URISyntaxException x2) {
                throw new RuntimeException(x2);
            }
        }

        final String scheme = uri.getScheme();
        final String host = uri.getHost();
        final String query = uri.getQuery();

        if ("http".equals(scheme) && "maps.google.com".equals(host)) {
            final String q = getQueryParameter(query, "q");

            final String saddr = getQueryParameter(query, "saddr");
            final String sll = getQueryParameter(query, "sll");
            final Location fromLocation;
            if (saddr != null)
                fromLocation = parseAddrParam(saddr, null);
            else if (sll != null)
                fromLocation = parseAddrParam(sll, q);
            else
                fromLocation = null;

            final String daddr = getQueryParameter(query, "daddr");
            if (daddr != null)
                return new Location[] { fromLocation, parseAddrParam(daddr, null) };
            else
                return new Location[] { fromLocation };
        } else if ("google.navigation".equals(scheme)) {
            final Location location;
            if (uri.isOpaque())
                location = parseGoogleNavigation(uri.getSchemeSpecificPart());
            else
                location = parseGoogleNavigation(uri.getQuery());

            return new Location[] { location };
        } else if ("geo".equals(scheme)) {
            final Location location = parseGeo(uri.getSchemeSpecificPart().replaceAll(",?\n+", ",+"));

            return new Location[] { location };
        }

        throw new IllegalArgumentException("cannot parse: '" + encodedUriString + "'");
    }

    private static Location parseGoogleNavigation(final String query) {
        final String q = getQueryParameter(query, "q");
        final String ll = getQueryParameter(query, "ll");
        final String title = getQueryParameter(query, "title");

        if (ll != null) {
            return parseAddrParam(ll, title != null ? title : q);
        } else if (q != null) {
            final Location location = parseAddrParam(q, title);

            if (location != null)
                return location;
            else
                return new Location(LocationType.ANY, null, null, q);
        }

        throw new IllegalArgumentException("cannot parse: '" + query + "'");
    }

    private static final Pattern P_URI_GEO = Pattern.compile("(-?\\d*\\.?\\d+),(-?\\d*\\.?\\d+)", Pattern.DOTALL);

    private static Location parseGeo(final String query) throws NumberFormatException {
        final int sepIndex = query.indexOf('?');
        final String coordStr;
        final String q;
        if (sepIndex != -1) {
            coordStr = query.substring(0, sepIndex);
            final String params = query.substring(sepIndex + 1);
            q = getQueryParameter(params, "q");
        } else {
            coordStr = query;
            q = null;
        }

        final Matcher m = P_URI_GEO.matcher(coordStr);
        final Point coord;
        if (m.matches()) {
            final Point c = Point.fromDouble(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)));
            if (c.lat != 0 || c.lon != 0)
                coord = c;
            else
                coord = null;
        } else {
            coord = null;
        }

        if (coord != null && q != null)
            return new Location(LocationType.ADDRESS, null, coord, null, q);
        else if (coord != null)
            return Location.coord(coord);
        else if (q != null)
            return new Location(LocationType.ANY, null, null, q);
        else
            throw new IllegalArgumentException("cannot parse: '" + query + "'");
    }

    private static String getQueryParameter(final String query, final String name) {
        final int nameIndex = findQueryParameterName(query, name);
        if (nameIndex < 0)
            return null;

        final int startIndex = nameIndex + name.length() + 1;
        final int endIndex = query.indexOf('&', startIndex);

        final String param;
        if (endIndex >= 0)
            param = query.substring(startIndex, endIndex);
        else
            param = query.substring(startIndex);

        return normalizeDecodeParam(param);
    }

    private static int findQueryParameterName(final String query, final String name) {
        if (query.startsWith(name + '='))
            return 0;

        final int index = query.indexOf('&' + name + '=');
        if (index >= 0)
            return index + 1;

        return -1;
    }

    private static final Pattern P_URI_LOCATION = Pattern
            .compile("(?:([^@]*)@)?(-?\\d*\\.\\d+),(-?\\d*\\.\\d+)(?:\\(([^\\)]*)\\))?", Pattern.DOTALL);

    private static final Location parseAddrParam(final String param, final String title) throws NumberFormatException {
        final Matcher m = P_URI_LOCATION.matcher(param);

        if (!m.matches())
            return null;

        final Point p = Point.fromDouble(Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)));

        if (title != null)
            return new Location(LocationType.ADDRESS, null, p, null, title);
        else if (m.group(1) != null)
            return new Location(LocationType.ADDRESS, null, p, null, m.group(1).length() > 0 ? m.group(1) : null);
        else if (m.group(4) != null)
            return new Location(LocationType.ADDRESS, null, p, null, m.group(4));
        else
            return Location.coord(p);
    }

    private static String normalizeDecodeParam(final String raw) {
        if (raw == null)
            return null;

        final String trimmed = raw.trim();
        if (raw.length() == 0)
            return null;

        return URLDecoder.decode(trimmed);
    }
}
