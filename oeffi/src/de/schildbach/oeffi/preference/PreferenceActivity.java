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

import android.app.Activity;
import android.content.Intent;
import android.view.MenuItem;
import de.schildbach.oeffi.R;

import java.util.List;

public class PreferenceActivity extends android.preference.PreferenceActivity {
    public static void start(final Activity activity) {
        activity.startActivity(new Intent(activity, PreferenceActivity.class));
    }

    public static void start(final Activity activity, final String fragmentName) {
        final Intent intent = new Intent(activity, PreferenceActivity.class);
        intent.putExtra(EXTRA_SHOW_FRAGMENT, fragmentName);
        activity.startActivity(intent);
    }

    @Override
    public void onBuildHeaders(final List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected boolean isValidFragment(final String fragmentName) {
        return CommonFragment.class.getName().equals(fragmentName)
                || DirectionsFragment.class.getName().equals(fragmentName)
                || AboutFragment.class.getName().equals(fragmentName);
    }
}
