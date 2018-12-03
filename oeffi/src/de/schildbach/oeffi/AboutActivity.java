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

import de.schildbach.oeffi.util.ChangelogDialogBuilder;
import de.schildbach.wallet.integration.android.BitcoinIntegration;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

public class AboutActivity extends PreferenceActivity {
    private static final String KEY_ABOUT_VERSION = "about_version";
    private static final String KEY_ABOUT_TWITTER = "about_twitter";
    private static final String KEY_ABOUT_CHANGELOG = "about_changelog";
    private static final String KEY_ABOUT_FAQ = "about_faq";
    private static final String KEY_ABOUT_COMMUNITY_GOOGLEPLUS = "about_community_googleplus";
    private static final String KEY_ABOUT_DONATE_BITCOIN = "about_donate_bitcoin";
    private static final String KEY_ABOUT_DONATE_FLATTR = "about_donate_flattr";
    private static final String KEY_ABOUT_DONATE_EURO = "about_donate_euro";
    private static final String KEY_ABOUT_MARKET_APP = "about_market_rate";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getListView().setFitsSystemWindows(true);
        getListView().setClipToPadding(false);

        addPreferencesFromResource(R.xml.about);

        findPreference(KEY_ABOUT_VERSION).setSummary(((Application) getApplication()).packageInfo().versionName);
        findPreference(KEY_ABOUT_MARKET_APP).setSummary(String.format(Constants.MARKET_APP_URL, getPackageName()));
    }

    @Override
    public boolean onPreferenceTreeClick(final PreferenceScreen preferenceScreen, final Preference preference) {
        final String key = preference.getKey();

        if (KEY_ABOUT_TWITTER.equals(key)) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.TWITTER_URL)));
            finish();
        } else if (KEY_ABOUT_CHANGELOG.equals(key)) {
            final Application application = (Application) getApplication();
            ChangelogDialogBuilder.get(this, Application.versionCode(application), null,
                    Application.versionFlavor(application), 0, null).show();
        } else if (KEY_ABOUT_FAQ.equals(key)) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_faq_summary))));
            finish();
        } else if (KEY_ABOUT_COMMUNITY_GOOGLEPLUS.equals(key)) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.COMMUNITY_GOOGLEPLUS_URL)));
            finish();
        } else if (KEY_ABOUT_DONATE_BITCOIN.equals(key)) {
            BitcoinIntegration.request(this, Constants.BITCOIN_ADDRESS);
            finish();
        } else if (KEY_ABOUT_DONATE_FLATTR.equals(key)) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.FLATTR_THING_URL)));
            finish();
        } else if (KEY_ABOUT_DONATE_EURO.equals(key)) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_donate_euro_summary))));
            finish();
        } else if (KEY_ABOUT_MARKET_APP.equals(key)) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(String.format(Constants.MARKET_APP_URL, getPackageName()))));
            finish();
        }

        return false;
    }
}
