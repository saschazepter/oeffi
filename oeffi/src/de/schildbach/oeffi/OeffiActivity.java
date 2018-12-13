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

package de.schildbach.oeffi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.schildbach.oeffi.network.NetworkResources;
import de.schildbach.oeffi.util.ErrorReporter;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.ResultHeader;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager.TaskDescription;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public abstract class OeffiActivity extends Activity {
    protected Application application;
    protected SharedPreferences prefs;

    private static final Logger log = LoggerFactory.getLogger(OeffiActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.application = (Application) getApplication();
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);

        ErrorReporter.getInstance().check(this, applicationVersionCode(), applicationVersionFlavor());
    }

    protected void updateFragments(final int listFrameResId, final int mapFrameResId) {
        final Resources res = getResources();

        final View listFrame = findViewById(listFrameResId);
        final boolean listShow = res.getBoolean(R.bool.layout_list_show);
        listFrame.setVisibility(isInMultiWindowMode() || listShow ? View.VISIBLE : View.GONE);

        final View mapFrame = findViewById(mapFrameResId);
        final boolean mapShow = res.getBoolean(R.bool.layout_map_show);
        mapFrame.setVisibility(!isInMultiWindowMode() && mapShow ? View.VISIBLE : View.GONE);

        listFrame.getLayoutParams().width = listShow && mapShow ? res.getDimensionPixelSize(R.dimen.layout_list_width)
                : LinearLayout.LayoutParams.MATCH_PARENT;

        final ViewGroup navigationDrawer = (ViewGroup) findViewById(R.id.navigation_drawer_layout);
        if (navigationDrawer != null) {
            final View child = navigationDrawer.getChildAt(1);
            child.getLayoutParams().width = res.getDimensionPixelSize(R.dimen.layout_navigation_drawer_width);
        }
    }

    protected String prefsGetNetwork() {
        return prefs.getString(Constants.PREFS_KEY_NETWORK_PROVIDER, null);
    }

    protected NetworkId prefsGetNetworkId() {
        final String id = prefsGetNetwork();
        if (id == null)
            return null;

        try {
            return NetworkId.valueOf(id);
        } catch (final IllegalArgumentException x) {
            log.warn("Ignoring unkown selected network: {}", id);
            return null;
        }
    }

    protected final String applicationVersionName() {
        return Application.versionName(application);
    }

    protected final int applicationVersionCode() {
        return Application.versionCode(application);
    }

    protected final String applicationVersionFlavor() {
        return Application.versionFlavor(application);
    }

    protected final long applicationFirstInstallTime() {
        return application.packageInfo().firstInstallTime;
    }

    protected final MyActionBar getMyActionBar() {
        return (MyActionBar) findViewById(R.id.action_bar);
    }

    protected final void setPrimaryColor(final int colorResId) {
        final int color = getResources().getColor(colorResId);
        getMyActionBar().setBackgroundColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            setTaskDescription(new TaskDescription(null, null, color));
    }

    protected void updateDisclaimerSource(final TextView disclaimerSourceView, final String network,
            final CharSequence defaultLabel) {
        final NetworkResources networkRes = NetworkResources.instance(this, network);
        final Drawable networkResIcon = networkRes.icon;
        if (networkRes.cooperation && networkResIcon != null) {
            final Drawable icon = networkResIcon.mutate();
            final int size = getResources().getDimensionPixelSize(R.dimen.disclaimer_network_icon_size);
            icon.setBounds(0, 0, size, size);
            disclaimerSourceView.setCompoundDrawables(icon, null, null, null);
            disclaimerSourceView.setText(getString(R.string.disclaimer_network, networkRes.label));
        } else {
            disclaimerSourceView.setCompoundDrawables(null, null, null, null);
            disclaimerSourceView.setText(defaultLabel);
        }
    }

    protected final CharSequence product(final ResultHeader header) {
        final StringBuilder str = new StringBuilder();

        // time delta
        if (header.serverTime != 0) {
            final long delta = (System.currentTimeMillis() - header.serverTime) / DateUtils.MINUTE_IN_MILLIS;
            if (Math.abs(delta) > 0)
                str.append("\u0394 ").append(delta).append(" min\n");
        }

        // name or product
        if (header.serverName != null)
            str.append(header.serverName);
        else
            str.append(header.serverProduct);

        // version
        if (header.serverVersion != null) {
            str.append(' ').append(header.serverVersion);
        }

        return str;
    }

    @TargetApi(24)
    @Override
    public boolean isInMultiWindowMode() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && super.isInMultiWindowMode();
    }
}
