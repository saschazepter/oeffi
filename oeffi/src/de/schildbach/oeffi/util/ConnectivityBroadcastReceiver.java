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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ConnectivityBroadcastReceiver extends BroadcastReceiver {

    private ConnectivityManager connectivityManager;

    private static final Logger log = LoggerFactory.getLogger(ConnectivityBroadcastReceiver.class);

    public ConnectivityBroadcastReceiver(final ConnectivityManager connectivityManager) {
        this.connectivityManager = connectivityManager;
        dispatchCallback();
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        dispatchCallback();
    }

    private void dispatchCallback() {
        final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        final boolean hasConnectivity = networkInfo != null && networkInfo.isConnected();

        final StringBuilder s = new StringBuilder("active network is ").append(hasConnectivity ? "up" : "down");
        if (networkInfo != null) {
            s.append(", type: ").append(networkInfo.getTypeName());
            s.append(", state: ").append(networkInfo.getState()).append('/').append(networkInfo.getDetailedState());
            final String extraInfo = networkInfo.getExtraInfo();
            if (extraInfo != null)
                s.append(", extraInfo: ").append(extraInfo);
            final String reason = networkInfo.getReason();
            if (reason != null)
                s.append(", reason: ").append(reason);
        }
        log.info(s.toString());

        if (hasConnectivity)
            onConnected();
        else
            onDisconnected();
    }

    protected abstract void onConnected();

    protected abstract void onDisconnected();
}
