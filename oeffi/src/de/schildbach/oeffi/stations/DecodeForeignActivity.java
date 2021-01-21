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

package de.schildbach.oeffi.stations;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import de.schildbach.oeffi.Application;
import de.schildbach.oeffi.R;
import de.schildbach.oeffi.util.DialogBuilder;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DecodeForeignActivity extends Activity {
    private static final Pattern PATTERN_META_REFRESH = Pattern
            .compile("<meta\\s+http-equiv=\"refresh\"\\s+content=\"0;\\s+URL=([^\"]*)\"");

    private Application application;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.application = (Application) getApplication();

        final Intent intent = getIntent();
        final Uri uri = intent.getData();

        if (uri != null && uri.getScheme().equals("http")) {
            final String host = uri.getHost();
            final String path = uri.getPath().trim();

            if ("www.rmv.de".equals(host)) {
                final Matcher m = Pattern.compile("/t/d(\\d+)").matcher(path);
                if (m.matches()) {
                    final ProgressDialog progressDialog = ProgressDialog.show(DecodeForeignActivity.this, null,
                            getString(R.string.stations_decode_foreign_progress), true, true, dialog -> finish());
                    progressDialog.setCanceledOnTouchOutside(false);

                    final Request.Builder request = new Request.Builder();
                    request.url(HttpUrl.parse(uri.toString()));
                    final Call call = application.okHttpClient().newCall(request.build());
                    call.enqueue(new Callback() {
                        public void onResponse(final Call call, final Response r) throws IOException {
                            try (final Response response = r) {
                                if (response.isSuccessful()) {
                                    final Matcher mRefresh = PATTERN_META_REFRESH.matcher(response.body().string());
                                    if (mRefresh.find()) {
                                        final Uri refreshUri = Uri.parse(mRefresh.group(1));

                                        runOnUiThread(() -> {
                                            progressDialog.dismiss();
                                            if ("mobil.rmv.de".equals(refreshUri.getHost())
                                                    && "/mobile".equals(refreshUri.getPath())) {
                                                final String id = refreshUri.getQueryParameter("id");
                                                StationDetailsActivity.start(DecodeForeignActivity.this,
                                                        NetworkId.NVV, new Location(LocationType.STATION, id));
                                                finish();
                                            } else {
                                                errorDialog(R.string.stations_decode_foreign_failed);
                                            }
                                        });
                                    } else {
                                        onFail();
                                    }
                                } else {
                                    onFail();
                                }
                            }
                        }

                        public void onFailure(final Call call, final IOException x) {
                            onFail();
                        }

                        private void onFail() {
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                errorDialog(R.string.stations_decode_foreign_failed);
                            });
                        }
                    });
                } else {
                    throw new IllegalArgumentException("cannot handle path: '" + path + "'");
                }
            } else {
                throw new IllegalArgumentException("cannot handle host: '" + host + "'");
            }
        }
    }

    private void errorDialog(final int resId) {
        final DialogBuilder builder = DialogBuilder.warn(this, 0);
        builder.setMessage(resId);
        builder.setPositiveButton("Ok", (dialog, which) -> finish());
        builder.setOnCancelListener(dialog -> finish());
        builder.show();
    }
}
