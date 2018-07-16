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

import de.schildbach.oeffi.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.LinearLayout;
import android.widget.ZoomButton;

public class ZoomControls extends LinearLayout {
    private final ZoomButton mZoomIn;
    private final ZoomButton mZoomOut;

    public ZoomControls(final Context context) {
        this(context, null);
    }

    public ZoomControls(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);

        final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.zoom_controls, this, true);

        mZoomIn = (ZoomButton) findViewById(R.id.zoomIn);
        mZoomOut = (ZoomButton) findViewById(R.id.zoomOut);

        setZoomSpeed(250);
    }

    public void setOnZoomInClickListener(final OnClickListener listener) {
        mZoomIn.setOnClickListener(listener);
    }

    public void setOnZoomOutClickListener(final OnClickListener listener) {
        mZoomOut.setOnClickListener(listener);
    }

    public void setZoomSpeed(final long speed) {
        mZoomIn.setZoomSpeed(speed);
        mZoomOut.setZoomSpeed(speed);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        return true;
    }

    public void show() {
        fade(View.VISIBLE, 0.0f, 1.0f);
    }

    public void hide() {
        fade(View.GONE, 1.0f, 0.0f);
    }

    private void fade(final int visibility, final float startAlpha, final float endAlpha) {
        final AlphaAnimation anim = new AlphaAnimation(startAlpha, endAlpha);
        anim.setDuration(500);
        startAnimation(anim);
        setVisibility(visibility);
    }

    public void setIsZoomInEnabled(final boolean isEnabled) {
        mZoomIn.setEnabled(isEnabled);
    }

    public void setIsZoomOutEnabled(final boolean isEnabled) {
        mZoomOut.setEnabled(isEnabled);
    }

    @Override
    public boolean hasFocus() {
        return (mZoomIn != null && mZoomIn.hasFocus()) || (mZoomOut != null && mZoomOut.hasFocus());
    }
}
