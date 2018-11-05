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
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

import de.schildbach.oeffi.directions.QueryHistoryProvider;
import de.schildbach.oeffi.stations.FavoriteStationsProvider;
import de.schildbach.oeffi.util.Downloader;
import de.schildbach.oeffi.util.ErrorReporter;
import de.schildbach.pte.NetworkId;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
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

    public static final OkHttpClient OKHTTP_CLIENT;
    static {
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
        OKHTTP_CLIENT = builder.build();
    }

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

        final Stopwatch watch = Stopwatch.createStarted();

        // clean up databases
        QueryHistoryProvider.cleanupQueryHistory(this);

        // 2017-01-03: migrate OOEVV to use OEBB
        migrateSelectedNetwork(NetworkId.OOEVV.name(), NetworkId.OEBB);
        Downloader.deleteDownload(new File(getFilesDir(), NetworkId.OOEVV.name().toLowerCase(Locale.ENGLISH) + ".db"));

        // 2017-01-13: remove Melbourne
        final String MET = "MET";
        Downloader.deleteDownload(new File(getFilesDir(), MET.toLowerCase(Locale.ENGLISH) + ".db"));

        // 2017-02-06: remove Philadelphia
        final String SEPTA = "SEPTA";
        Downloader.deleteDownload(new File(getFilesDir(), SEPTA.toLowerCase(Locale.ENGLISH) + ".db"));

        // 2017-02-16: remove Provence-Alpes-Côte d'Azur
        final String PACA = "PACA";
        migrateSelectedNetwork(PACA, NetworkId.RT);
        Downloader.deleteDownload(new File(getFilesDir(), PACA.toLowerCase(Locale.ENGLISH) + ".db"));

        // 2017-08-31: migrate BVB to use NVBW
        final String BVB = "BVB";
        migrateSelectedNetwork(BVB, NetworkId.NVBW);
        Downloader.deleteDownload(new File(getFilesDir(), BVB.toLowerCase(Locale.ENGLISH) + ".db"));

        // 2017-09-19: migrate VBB to new station IDs
        FavoriteStationsProvider.migrateFavoriteStationIds(this, NetworkId.VBB, "9000000", "9600000", 891000000);
        QueryHistoryProvider.migrateQueryHistoryIds(this, NetworkId.VBB, "9000000", "9600000", 891000000);

        // 2017-09-19: migrate BVG to new station IDs
        FavoriteStationsProvider.migrateFavoriteStationIds(this, NetworkId.BVG, "9000000", "9600000", 891000000);
        QueryHistoryProvider.migrateQueryHistoryIds(this, NetworkId.BVG, "9000000", "9600000", 891000000);

        // 2018-07-06: migrate IVB to use OEBB
        final String IVB = "IVB";
        migrateSelectedNetwork(IVB, NetworkId.OEBB);
        Downloader.deleteDownload(new File(getFilesDir(), IVB.toLowerCase(Locale.ENGLISH) + ".db"));
        FavoriteStationsProvider.deleteFavoriteStations(this, IVB);
        QueryHistoryProvider.deleteQueryHistory(this, IVB);

        // 2018-11-05: migrate NRI to use RT
        final String NRI = "NRI";
        migrateSelectedNetwork(NRI, NetworkId.RT);
        Downloader.deleteDownload(new File(getFilesDir(), NRI.toLowerCase(Locale.ENGLISH) + ".db"));
        FavoriteStationsProvider.deleteFavoriteStations(this, NRI);
        QueryHistoryProvider.deleteQueryHistory(this, NRI);

        log.info("Migrations took {}", watch);
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

    private void migrateSelectedNetwork(final String fromName, final NetworkId to) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (fromName.equals(prefs.getString(Constants.PREFS_KEY_NETWORK_PROVIDER, null)))
            prefs.edit().putString(Constants.PREFS_KEY_NETWORK_PROVIDER, to.name()).commit();
    }

    public PackageInfo packageInfo() {
        return packageInfo;
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
