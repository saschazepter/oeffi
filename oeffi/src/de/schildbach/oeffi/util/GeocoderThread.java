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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.pte.dto.Point;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;

public class GeocoderThread extends Thread {
    public interface Callback {
        void onGeocoderResult(Address address);

        void onGeocoderFail(Exception exception);
    }

    private final Geocoder geocoder;
    private final double latitude, longitude;
    private final Handler callbackHandler;
    private final Callback callback;
    private long startTime;

    private static final Logger log = LoggerFactory.getLogger(GeocoderThread.class);

    public GeocoderThread(final Context context, final Point coord, final Callback callback) {
        this(context, coord.getLatAsDouble(), coord.getLonAsDouble(), callback);
    }

    public GeocoderThread(final Context context, final double latitude, final double longitude,
            final Callback callback) {
        this.geocoder = new Geocoder(checkNotNull(context), Locale.GERMANY);
        checkArgument(latitude > -90);
        checkArgument(latitude < 90);
        this.latitude = latitude;
        checkArgument(longitude > -180);
        checkArgument(longitude < 180);
        this.longitude = longitude;
        this.callback = checkNotNull(callback);

        callbackHandler = new Handler(Looper.myLooper());

        start();
    }

    @Override
    public void run() {
        startTime = System.currentTimeMillis();

        try {
            if (Geocoder.isPresent()) {
                final List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

                if (addresses.size() >= 1)
                    onResult(addresses.get(0));
                else
                    onFail(new IllegalStateException("No addresses."));
            } else {
                onFail(new UnsupportedOperationException("Geocoder not implemented."));
            }
        } catch (final IOException x) {
            onFail(x);
        }
    }

    private void onResult(final Address address) {
        log.info("Geocoder took {} ms, returned {}", System.currentTimeMillis() - startTime, address);

        callbackHandler.post(new Runnable() {
            public void run() {
                callback.onGeocoderResult(address);
            }
        });
    }

    private void onFail(final Exception exception) {
        log.info("Geocoder failed: {}", exception.getMessage());

        callbackHandler.post(new Runnable() {
            public void run() {
                callback.onGeocoderFail(exception);
            }
        });
    }
}
