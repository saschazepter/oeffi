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

import android.view.MotionEvent;

public class UpGestureDetector {
    public interface OnUpGestureListener {
        boolean onUp(MotionEvent event);
    }

    private final OnUpGestureListener listener;

    public UpGestureDetector(final OnUpGestureListener listener) {
        this.listener = listener;
    }

    public boolean onTouchEvent(final MotionEvent event) {
        switch (event.getActionMasked()) {
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
            return listener.onUp(event);

        default:
            return false;
        }
    }
}
