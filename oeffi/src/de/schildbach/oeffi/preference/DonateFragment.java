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

package de.schildbach.oeffi.preference;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import androidx.annotation.Nullable;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import de.schildbach.wallet.integration.android.BitcoinIntegration;

public class DonateFragment extends PreferenceFragment {
    private static final String KEY_ABOUT_DONATE_BITCOIN = "about_donate_bitcoin";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference_donate);
        findPreference(KEY_ABOUT_DONATE_BITCOIN).setOnPreferenceClickListener(preference -> {
            BitcoinIntegration.request(getActivity(), Constants.BITCOIN_ADDRESS);
            return true;
        });
    }
}
