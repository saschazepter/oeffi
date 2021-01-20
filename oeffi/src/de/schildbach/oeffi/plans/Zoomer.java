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

package de.schildbach.oeffi.plans;

import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

public class Zoomer {
    private final Interpolator interpolator = new DecelerateInterpolator();

    private long zoomStart = 0;
    private final long DEFAULT_ZOOM_DURATION = 250;
    private float zoomFrom = 1;
    private float zoomSpan = 0;

    private float currentValue;
    private float zoomFocusX, zoomFocusY; // in screen coordinates

    public void zoom(final float current, final float target, final float focusX, final float focusY) {
        zoomStart = System.currentTimeMillis();
        zoomFrom = current;
        zoomSpan = target - current;
        zoomFocusX = focusX;
        zoomFocusY = focusY;
    }

    public boolean isFinished() {
        return zoomStart == 0;
    }

    public boolean computeZoomValue() {
        if (zoomStart != 0) {
            final float time = (float) (System.currentTimeMillis() - zoomStart) / DEFAULT_ZOOM_DURATION;

            currentValue = interpolator.getInterpolation(Math.min(time, 1.0f)) * zoomSpan + zoomFrom;

            if (time >= 1.0f)
                zoomStart = 0;

            return true;
        } else {
            return false;
        }
    }

    public float currentValue() {
        return currentValue;
    }

    public float getFocusX() {
        return zoomFocusX;
    }

    public float getFocusY() {
        return zoomFocusY;
    }
}
