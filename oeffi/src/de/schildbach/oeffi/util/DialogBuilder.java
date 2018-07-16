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

import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;

import android.app.AlertDialog;
import android.content.Context;

public class DialogBuilder extends AlertDialog.Builder {
    public static DialogBuilder get(final Context context) {
        return new DialogBuilder(context, Constants.ALERT_DIALOG_THEME);
    }

    public static DialogBuilder warn(final Context context, final int titleResId) {
        final DialogBuilder builder = get(context);
        builder.setIcon(R.drawable.ic_warning_amber_24dp);
        builder.setTitle(titleResId);
        return builder;
    }

    private DialogBuilder(final Context context, final int theme) {
        super(context, theme);
    }
}
