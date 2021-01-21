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

import android.content.Context;
import android.graphics.PorterDuff;
import android.widget.TextView;
import de.schildbach.oeffi.R;

public class Toast {
    private final Context context;

    public Toast(final Context context) {
        this.context = context;
    }

    public final void toast(final int textResId, final Object... formatArgs) {
        customToast(0, textResId, android.widget.Toast.LENGTH_SHORT, formatArgs);
    }

    public final void toast(final CharSequence text) {
        customToast(0, text, android.widget.Toast.LENGTH_SHORT);
    }

    public final void longToast(final int textResId, final Object... formatArgs) {
        customToast(0, textResId, android.widget.Toast.LENGTH_LONG, formatArgs);
    }

    public final void longToast(final CharSequence text) {
        customToast(0, text, android.widget.Toast.LENGTH_LONG);
    }

    public final void imageToast(final int imageResId, final int textResId, final Object... formatArgs) {
        customToast(imageResId, textResId, android.widget.Toast.LENGTH_LONG, formatArgs);
    }

    private void customToast(final int imageResId, final int textResId, final int duration,
            final Object... formatArgs) {
        customToast(imageResId, context.getString(textResId, formatArgs), duration);
    }

    private void customToast(final int imageResId, final CharSequence text, final int duration) {
        final android.widget.Toast toast = android.widget.Toast.makeText(context, text, duration);
        final TextView toastText = toast.getView().findViewById(android.R.id.message);
        if (imageResId != 0 && toastText != null) {
            toastText.setCompoundDrawablesWithIntrinsicBounds(imageResId, 0, 0, 0);
            toastText.setCompoundDrawablePadding(
                    context.getResources().getDimensionPixelOffset(R.dimen.list_entry_padding_horizontal_verylax));
            // tint
            final int textColor = toastText.getTextColors().getDefaultColor();
            toastText.getCompoundDrawables()[0].setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
        }
        toast.show();
    }
}
