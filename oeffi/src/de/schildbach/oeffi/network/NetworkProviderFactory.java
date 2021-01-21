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

package de.schildbach.oeffi.network;

import android.os.Build;
import com.google.common.base.Charsets;
import de.schildbach.pte.AbstractNetworkProvider;
import de.schildbach.pte.AvvAachenProvider;
import de.schildbach.pte.AvvProvider;
import de.schildbach.pte.BartProvider;
import de.schildbach.pte.BayernProvider;
import de.schildbach.pte.BsvagProvider;
import de.schildbach.pte.BvgProvider;
import de.schildbach.pte.CmtaProvider;
import de.schildbach.pte.CzechRepublicProvider;
import de.schildbach.pte.DbProvider;
import de.schildbach.pte.DingProvider;
import de.schildbach.pte.DsbProvider;
import de.schildbach.pte.DubProvider;
import de.schildbach.pte.FinlandProvider;
import de.schildbach.pte.GvhProvider;
import de.schildbach.pte.InvgProvider;
import de.schildbach.pte.ItalyProvider;
import de.schildbach.pte.KvvProvider;
import de.schildbach.pte.LinzProvider;
import de.schildbach.pte.LuProvider;
import de.schildbach.pte.MerseyProvider;
import de.schildbach.pte.MvgProvider;
import de.schildbach.pte.MvvProvider;
import de.schildbach.pte.NasaProvider;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.NicaraguaProvider;
import de.schildbach.pte.NsProvider;
import de.schildbach.pte.NvbwProvider;
import de.schildbach.pte.NvvProvider;
import de.schildbach.pte.OebbProvider;
import de.schildbach.pte.ParisProvider;
import de.schildbach.pte.PlProvider;
import de.schildbach.pte.RtProvider;
import de.schildbach.pte.RtaChicagoProvider;
import de.schildbach.pte.SbbProvider;
import de.schildbach.pte.SeProvider;
import de.schildbach.pte.ShProvider;
import de.schildbach.pte.SncbProvider;
import de.schildbach.pte.SpainProvider;
import de.schildbach.pte.StvProvider;
import de.schildbach.pte.SydneyProvider;
import de.schildbach.pte.TfiProvider;
import de.schildbach.pte.TlemProvider;
import de.schildbach.pte.VbbProvider;
import de.schildbach.pte.VblProvider;
import de.schildbach.pte.VbnProvider;
import de.schildbach.pte.VgnProvider;
import de.schildbach.pte.VmsProvider;
import de.schildbach.pte.VmtProvider;
import de.schildbach.pte.VmvProvider;
import de.schildbach.pte.VrnProvider;
import de.schildbach.pte.VrrProvider;
import de.schildbach.pte.VrsProvider;
import de.schildbach.pte.VvmProvider;
import de.schildbach.pte.VvoProvider;
import de.schildbach.pte.VvsProvider;
import de.schildbach.pte.VvvProvider;
import de.schildbach.pte.WienProvider;
import de.schildbach.pte.ZvvProvider;
import okhttp3.HttpUrl;

import java.util.HashMap;
import java.util.Map;

public final class NetworkProviderFactory {
    private static Map<NetworkId, NetworkProvider> providerCache = new HashMap<>();

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36";
    private static final String NAVITIA_AUTHORIZATION = "577e5781-23ee-4ff0-a5b3-92e5b04887e5"; // oeffi@schildbach.de

    public static synchronized NetworkProvider provider(final NetworkId networkId) {
        final NetworkProvider cachedNetworkProvider = providerCache.get(networkId);
        if (cachedNetworkProvider != null)
            return cachedNetworkProvider;

        final AbstractNetworkProvider networkProvider = forId(networkId);
        networkProvider.setUserAgent(USER_AGENT);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            networkProvider.setTrustAllCertificates(true);
        providerCache.put(networkId, networkProvider);
        return networkProvider;
    }

    private static AbstractNetworkProvider forId(final NetworkId networkId) {
        if (networkId.equals(NetworkId.RT))
            return new RtProvider();
        else if (networkId.equals(NetworkId.DB))
            return new DbProvider("{\"type\":\"AID\",\"aid\":\"n91dB8Z77MLdoR0K\"}",
                    "bdI8UVj40K5fvxwf".getBytes(Charsets.UTF_8));
        else if (networkId.equals(NetworkId.BVG))
            return new BvgProvider("{\"aid\":\"1Rxs112shyHLatUX4fofnmdxK\",\"type\":\"AID\"}");
        else if (networkId.equals(NetworkId.VBB))
            return new VbbProvider("{\"type\":\"AID\",\"aid\":\"hafas-vbb-apps\"}",
                    "RCTJM2fFxFfxxQfI".getBytes(Charsets.UTF_8));
        else if (networkId.equals(NetworkId.NVV))
            return new NvvProvider("{\"type\":\"AID\",\"aid\":\"Kt8eNOH7qjVeSxNA\"}");
        else if (networkId.equals(NetworkId.BAYERN))
            return new BayernProvider();
        else if (networkId.equals(NetworkId.MVV))
            return new MvvProvider();
        else if (networkId.equals(NetworkId.INVG))
            return new InvgProvider("{\"type\":\"AID\",\"aid\":\"GITvwi3BGOmTQ2a5\"}",
                    "ERxotxpwFT7uYRsI".getBytes(Charsets.UTF_8));
        else if (networkId.equals(NetworkId.AVV))
            return new AvvProvider();
        else if (networkId.equals(NetworkId.VGN))
            return new VgnProvider(HttpUrl.parse("https://efa.vgn.de/vgnExt_oeffi/"));
        else if (networkId.equals(NetworkId.VVM))
            return new VvmProvider();
        else if (networkId.equals(NetworkId.VMV))
            return new VmvProvider();
        else if (networkId.equals(NetworkId.SH))
            return new ShProvider("{\"aid\":\"r0Ot9FLFNAFxijLW\",\"type\":\"AID\"}");
        else if (networkId.equals(NetworkId.GVH))
            return new GvhProvider(HttpUrl.parse("https://www.efa.de/app_oeffi/"));
        else if (networkId.equals(NetworkId.BSVAG))
            return new BsvagProvider();
        else if (networkId.equals(NetworkId.VBN))
            return new VbnProvider("{\"aid\":\"rnOHBWhesvc7gFkd\",\"type\":\"AID\"}",
                    "SP31mBufSyCLmNxp".getBytes(Charsets.UTF_8));
        else if (networkId.equals(NetworkId.NASA))
            return new NasaProvider("{\"type\":\"AID\",\"aid\":\"nasa-apps\"}");
        else if (networkId.equals(NetworkId.VMT))
            return new VmtProvider("{\"aid\":\"vj5d7i3g9m5d7e3\",\"type\":\"AID\"}");
        else if (networkId.equals(NetworkId.VVO))
            return new VvoProvider();
        else if (networkId.equals(NetworkId.VMS))
            return new VmsProvider(HttpUrl.parse("https://www.vms.de/Oeffi/"));
        else if (networkId.equals(NetworkId.VRR))
            return new VrrProvider();
        else if (networkId.equals(NetworkId.VRS))
            return new VrsProvider();
        else if (networkId.equals(NetworkId.AVV_AACHEN))
            return new AvvAachenProvider("{\"id\":\"AVV_AACHEN\",\"l\":\"vs_oeffi\",\"type\":\"WEB\"}",
                    "{\"type\":\"AID\",\"aid\":\"4vV1AcH3N511icH\"}");
        else if (networkId.equals(NetworkId.MVG))
            return new MvgProvider();
        else if (networkId.equals(NetworkId.VRN))
            return new VrnProvider();
        else if (networkId.equals(NetworkId.VVS))
            return new VvsProvider(HttpUrl.parse("https://www2.vvs.de/oeffi/"));
        else if (networkId.equals(NetworkId.DING))
            return new DingProvider();
        else if (networkId.equals(NetworkId.KVV))
            return new KvvProvider(HttpUrl.parse("https://projekte.kvv-efa.de/oeffi/"));
        else if (networkId.equals(NetworkId.NVBW))
            return new NvbwProvider();
        else if (networkId.equals(NetworkId.VVV))
            return new VvvProvider();
        else if (networkId.equals(NetworkId.OEBB))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                return new OebbProvider("{\"type\":\"AID\",\"aid\":\"OWDL4fE4ixNiPBBm\"}");
            else
                return new OebbProvider(HttpUrl.parse("http://fahrplan.oebb.at/bin/"), "{\"type\":\"AID\"," +
                        "\"aid\":\"OWDL4fE4ixNiPBBm\"}");
        else if (networkId.equals(NetworkId.WIEN))
            return new WienProvider();
        else if (networkId.equals(NetworkId.LINZ))
            return new LinzProvider();
        else if (networkId.equals(NetworkId.STV))
            return new StvProvider();
        else if (networkId.equals(NetworkId.CZECH_REPUBLIC))
            return new CzechRepublicProvider(NAVITIA_AUTHORIZATION);
        else if (networkId.equals(NetworkId.SBB))
            return new SbbProvider();
        else if (networkId.equals(NetworkId.VBL))
            return new VblProvider();
        else if (networkId.equals(NetworkId.ZVV))
            return new ZvvProvider("{\"type\":\"AID\",\"aid\":\"hf7mcf9bv3nv8g5f\"}");
        else if (networkId.equals(NetworkId.IT))
            return new ItalyProvider(NAVITIA_AUTHORIZATION);
        else if (networkId.equals(NetworkId.PARIS))
            return new ParisProvider(NAVITIA_AUTHORIZATION);
        else if (networkId.equals(NetworkId.SPAIN))
            return new SpainProvider(NAVITIA_AUTHORIZATION);
        else if (networkId.equals(NetworkId.SNCB))
            return new SncbProvider("{\"type\":\"AID\",\"aid\":\"sncb-mobi\"}");
        else if (networkId.equals(NetworkId.LU))
            return new LuProvider("{\"type\":\"AID\",\"aid\":\"SkC81GuwuzL4e0\"}");
        else if (networkId.equals(NetworkId.NS))
            return new NsProvider();
        else if (networkId.equals(NetworkId.DSB))
            return new DsbProvider("{\"type\":\"AID\",\"aid\":\"irkmpm9mdznstenr-android\"}");
        else if (networkId.equals(NetworkId.SE))
            return new SeProvider("{\"type\":\"AID\",\"aid\":\"h5o3n7f4t2m8l9x1\"}");
        else if (networkId.equals(NetworkId.FINLAND))
            return new FinlandProvider(NAVITIA_AUTHORIZATION);
        else if (networkId.equals(NetworkId.TLEM))
            return new TlemProvider();
        else if (networkId.equals(NetworkId.MERSEY))
            return new MerseyProvider();
        else if (networkId.equals(NetworkId.TFI))
            return new TfiProvider();
        else if (networkId.equals(NetworkId.PL))
            return new PlProvider();
        else if (networkId.equals(NetworkId.DUB))
            return new DubProvider();
        else if (networkId.equals(NetworkId.BART))
            return new BartProvider("{\"type\":\"AID\",\"aid\":\"kEwHkFUCIL500dym\"}");
        else if (networkId.equals(NetworkId.RTACHICAGO))
            return new RtaChicagoProvider();
        else if (networkId.equals(NetworkId.CMTA))
            return new CmtaProvider("{\"type\":\"AID\",\"aid\":\"web9j2nak29uz41irb\"}");
        else if (networkId.equals(NetworkId.SYDNEY))
            return new SydneyProvider();
        else if (networkId.equals(NetworkId.NICARAGUA))
            return new NicaraguaProvider(NAVITIA_AUTHORIZATION);
        else
            throw new IllegalArgumentException(networkId.name());
    }
}
