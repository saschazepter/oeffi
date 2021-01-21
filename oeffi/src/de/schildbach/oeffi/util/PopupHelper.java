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

import android.widget.PopupMenu;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PopupHelper {
    public static void setForceShowIcon(final PopupMenu popupMenu) {
        try {
            final Class<?> classPopupMenu = Class.forName(popupMenu.getClass().getName());
            final Field mPopup = classPopupMenu.getDeclaredField("mPopup");
            mPopup.setAccessible(true);
            final Object menuPopupHelper = mPopup.get(popupMenu);
            final Class<?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
            final Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
            setForceIcons.invoke(menuPopupHelper, true);
        } catch (final Exception x) {
            // Swallow
        }
    }
}
