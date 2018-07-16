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

package de.schildbach.oeffi.network.list;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

public class SeparatorViewHolder extends RecyclerView.ViewHolder {
    private final TextView textView;

    public SeparatorViewHolder(final View itemView) {
        super(itemView);

        textView = ((TextView) itemView.findViewById(android.R.id.text1));
    }

    public void bind(final NetworkListEntry.Separator entry) {
        textView.setText(entry.label);
    }
}
