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

package de.schildbach.oeffi.network;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.Uninterruptibles;

import de.schildbach.oeffi.Constants;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.Point;
import de.schildbach.pte.exception.NotFoundException;

import android.os.Handler;

public abstract class GetAreaRunnable implements Runnable {
    private final NetworkProvider networkProvider;
    private final Handler handler;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private static final Logger log = LoggerFactory.getLogger(GetAreaRunnable.class);

    public GetAreaRunnable(final NetworkProvider networkProvider, final Handler handler) {
        this.networkProvider = networkProvider;
        this.handler = handler;
    }

    public void run() {
        int tries = 0;

        while (!cancelled.get()) {
            tries++;

            try {
                final Point[] area = networkProvider.getArea();

                if (!cancelled.get())
                    postOnResult(area);

                break;
            } catch (final IOException x) {
                final String message = "IO problem while processing " + this + " on " + networkProvider + " (try "
                        + tries + ")";
                log.info(message, x);
                if (tries >= Constants.MAX_TRIES_ON_IO_PROBLEM) {
                    if (x instanceof SocketTimeoutException || x instanceof UnknownHostException
                            || x instanceof SocketException || x instanceof NotFoundException
                            || x instanceof SSLException) {
                        break;
                    } else {
                        throw new RuntimeException(message, x);
                    }
                }

                Uninterruptibles.sleepUninterruptibly(tries, TimeUnit.SECONDS);

                // try again
                continue;
            }
        }
    }

    private void postOnResult(final Point[] area) {
        handler.post(() -> onResult(area));
    }

    protected abstract void onResult(final Point[] area);
}
