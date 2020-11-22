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

package de.schildbach.oeffi;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

import de.schildbach.oeffi.directions.QueryHistoryProvider;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.oeffi.stations.NearestFavoriteStationWidgetService;
import de.schildbach.oeffi.util.ErrorReporter;
import de.schildbach.pte.NetworkId;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.preference.PreferenceManager;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

public class Application extends android.app.Application {
    private PackageInfo packageInfo;
    private OkHttpClient okHttpClient;

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    @Override
    public void onCreate() {
        super.onCreate();

        initLogging();

        ErrorReporter.getInstance().init(this);

        try {
            packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (final NameNotFoundException x) {
            throw new RuntimeException(x);
        }

        log.info("=== Starting app version {} ({})", packageInfo.versionName, packageInfo.versionCode);

        final OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.followRedirects(false);
        builder.followSslRedirects(true);
        builder.connectTimeout(5, TimeUnit.SECONDS);
        builder.writeTimeout(5, TimeUnit.SECONDS);
        builder.readTimeout(15, TimeUnit.SECONDS);
        final HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(final String message) {
                log.debug(message);
            }
        });
        interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        builder.addNetworkInterceptor(interceptor);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            log.info("manually enabling TLS 1.2 on API level {}", Build.VERSION.SDK_INT);
            if (safetyNetInsertProvider()) {
                final Tls12SocketFactory socketFactory = new Tls12SocketFactory();
                builder.sslSocketFactory(socketFactory, socketFactory.getTrustManager());
            }
        }
        okHttpClient = builder.build();

        initMaps();

        final Stopwatch watch = Stopwatch.createStarted();

        // 2018-07-06: migrate IVB to use OEBB
        final String IVB = "IVB";
        migrateSelectedNetwork(IVB, NetworkId.OEBB);
        FavoriteStationsProvider.deleteFavoriteStations(this, IVB);
        QueryHistoryProvider.deleteQueryHistory(this, IVB);

        // 2018-11-05: migrate NRI to use RT
        final String NRI = "NRI";
        migrateSelectedNetwork(NRI, NetworkId.RT);
        FavoriteStationsProvider.deleteFavoriteStations(this, NRI);
        QueryHistoryProvider.deleteQueryHistory(this, NRI);

        // 2018-12-06: migrate VAGFR to use NVBW
        final String VAGFR = "VAGFR";
        migrateSelectedNetwork(VAGFR, NetworkId.NVBW);
        FavoriteStationsProvider.migrateFavoriteStations(this, VAGFR, NetworkId.NVBW);
        QueryHistoryProvider.migrateQueryHistory(this, VAGFR, NetworkId.NVBW);

        // 2020-11-22: delete unused downloaded station databases
        final FilenameFilter filter = (dir, name) -> name.endsWith(".db") || name.endsWith(".db.meta");
        for (final File file : getFilesDir().listFiles(filter))
            file.delete();

        log.info("Migrations took {}", watch);

        initNotificationManager();
    }

    private boolean safetyNetInsertProvider() {
        // This piece of code uses SafetyNet (Google Play Services) to insert a recent version of Conscrypt, if
        // available. We use reflection to avoid the proprietary Google Play Services client library.
        try {
            final Stopwatch watch = Stopwatch.createStarted();
            final Context remoteContext = createPackageContext("com.google.android.gms", 3);
            final Method insertProvider = remoteContext.getClassLoader().loadClass("com.google.android.gms.common" +
                    ".security.ProviderInstallerImpl").getMethod("insertProvider", new Class[] { Context.class });
            insertProvider.invoke(null, new Object[] { remoteContext });
            log.info("insertProvider successful, took {}", watch.stop());
            return true;
        } catch (final Exception x) {
            log.warn("insertProvider failed", x);
            return false;
        }
    }

    private void initLogging() {
        final File logDir = new File(getFilesDir(), "log");
        final File logFile = new File(logDir, "oeffi.log");
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        final PatternLayoutEncoder filePattern = new PatternLayoutEncoder();
        filePattern.setContext(context);
        filePattern.setPattern("%d{HH:mm:ss,UTC} [%thread] %logger{0} - %msg%n");
        filePattern.start();

        final RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
        fileAppender.setContext(context);
        fileAppender.setFile(logFile.getAbsolutePath());

        final TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setContext(context);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setFileNamePattern(logDir.getAbsolutePath() + "/oeffi.%d{yyyy-MM-dd,UTC}.log.gz");
        rollingPolicy.setMaxHistory(7);
        rollingPolicy.start();

        fileAppender.setEncoder(filePattern);
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.start();

        final PatternLayoutEncoder logcatTagPattern = new PatternLayoutEncoder();
        logcatTagPattern.setContext(context);
        logcatTagPattern.setPattern("%logger{0}");
        logcatTagPattern.start();

        final PatternLayoutEncoder logcatPattern = new PatternLayoutEncoder();
        logcatPattern.setContext(context);
        logcatPattern.setPattern("[%thread] %msg%n");
        logcatPattern.start();

        final LogcatAppender logcatAppender = new LogcatAppender();
        logcatAppender.setContext(context);
        logcatAppender.setTagEncoder(logcatTagPattern);
        logcatAppender.setEncoder(logcatPattern);
        logcatAppender.start();

        final ch.qos.logback.classic.Logger log = context.getLogger(Logger.ROOT_LOGGER_NAME);
        log.addAppender(fileAppender);
        log.addAppender(logcatAppender);
        log.setLevel(Level.DEBUG);
    }

    private void initMaps() {
        final IConfigurationProvider config = Configuration.getInstance();
        config.setOsmdroidBasePath(new File(getCacheDir(), "org.osmdroid"));
        config.setUserAgentValue(getPackageName());
    }

    private void initNotificationManager() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final Stopwatch watch = Stopwatch.createStarted();
            final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            final NotificationChannel appwidget = new NotificationChannel(
                    NearestFavoriteStationWidgetService.NOTIFICATION_CHANNEL_ID_APPWIDGET,
                    getString(R.string.notification_channel_appwidget_name), NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(appwidget);

            log.info("created notification channels, took {}", watch);
        }
    }

    private void migrateSelectedNetwork(final String fromName, final NetworkId to) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (fromName.equals(prefs.getString(Constants.PREFS_KEY_NETWORK_PROVIDER, null)))
            prefs.edit().putString(Constants.PREFS_KEY_NETWORK_PROVIDER, to.name()).commit();
    }

    public PackageInfo packageInfo() {
        return packageInfo;
    }

    public OkHttpClient okHttpClient() {
        return okHttpClient;
    }

    public static final String versionName(final Application application) {
        return application.packageInfo().versionName;
    }

    public static final int versionCode(final Application application) {
        return application.packageInfo().versionCode;
    }

    public static final String versionFlavor(final Application application) {
        final String applicationVersion = versionName(application);
        final int applicationVersionSplit = applicationVersion.indexOf('-');
        if (applicationVersionSplit >= 0)
            return applicationVersion.substring(applicationVersionSplit + 1);
        else
            return null;
    }
}
