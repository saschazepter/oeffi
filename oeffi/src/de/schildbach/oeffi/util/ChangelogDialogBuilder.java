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

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.annotation.Nullable;
import de.schildbach.oeffi.Constants;
import de.schildbach.oeffi.R;
import okhttp3.HttpUrl;

public class ChangelogDialogBuilder extends AlertDialog.Builder {
    public static ChangelogDialogBuilder get(final Context context, final int versionCode, final String versionName,
            final String versionFlavor, final int lastVersionCode, @Nullable final String task) {
        return new ChangelogDialogBuilder(context, Constants.ALERT_DIALOG_THEME, versionCode, versionName,
                versionFlavor, lastVersionCode, task);
    }

    private ChangelogDialogBuilder(final Context context, final int theme, final int versionCode,
            final String versionName, final String versionFlavor, final int lastVersionCode,
            @Nullable final String task) {
        super(context, theme);
        init(context, versionCode, versionName, versionFlavor, lastVersionCode, task);
    }

    private void init(final Context context, final int versionCode, final String versionName,
            final String versionFlavor, final int lastVersionCode, @Nullable final String task) {
        final LayoutInflater inflater = LayoutInflater.from(context);

        final View view = inflater.inflate(R.layout.changelog_dialog, null);
        final View progressView = view.findViewById(R.id.changelog_dialog_progress);
        final WebView webView = (WebView) view.findViewById(R.id.changelog_dialog_webview);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(final WebView view, final int progress) {
                if (progress == 100)
                    progressView.setVisibility(View.GONE);
            }
        });
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        final HttpUrl.Builder url = HttpUrl.parse(context.getString(R.string.about_changelog_summary)).newBuilder();
        url.addQueryParameter("version", Integer.toString(versionCode));
        if (lastVersionCode > 0)
            url.addQueryParameter("lastVersion", Integer.toString(lastVersionCode));
        if (versionFlavor != null)
            url.addEncodedQueryParameter("flavor", versionFlavor);
        url.addQueryParameter("sdk", Integer.toString(Build.VERSION.SDK_INT));
        if (task != null)
            url.addEncodedQueryParameter("task", task);
        webView.loadUrl(url.build().toString());

        setIcon(R.mipmap.ic_oeffi_stations_color_48dp);
        setTitle((versionName != null ? context.getString(R.string.changelog_dialog_title_version, versionName) + '\n'
                : "") + context.getString(R.string.changelog_dialog_title));
        setView(view);
        setPositiveButton(context.getString(R.string.changelog_dialog_button_dismiss),
                (dialog, id) -> dialog.dismiss());
    }
}
