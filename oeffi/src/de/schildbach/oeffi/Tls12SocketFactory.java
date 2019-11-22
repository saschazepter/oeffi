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

import androidx.annotation.Nullable;
import okhttp3.TlsVersion;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

public final class Tls12SocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;
    private final TrustManager[] trustManagers;

    public Tls12SocketFactory() {
        try {
            final TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            trustManagers = trustManagerFactory.getTrustManagers();
            final SSLContext context = SSLContext.getInstance(TlsVersion.TLS_1_2.javaName());
            context.init(null, trustManagers, null);
            delegate = context.getSocketFactory();
        } catch (final NoSuchAlgorithmException | KeyManagementException | KeyStoreException x) {
            throw new RuntimeException(x);
        }
    }

    @Nullable
    public X509TrustManager getTrustManager() {
        for (final TrustManager tm : trustManagers)
            if (tm instanceof X509TrustManager)
                return (X509TrustManager) tm;
        return null;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return patchForTls12(delegate.createSocket());
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return patchForTls12(delegate.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return patchForTls12(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException,
            UnknownHostException {
        return patchForTls12(delegate.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return patchForTls12(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return patchForTls12(delegate.createSocket(address, port, localAddress, localPort));
    }

    private Socket patchForTls12(final Socket socket) {
        if (socket != null && (socket instanceof SSLSocket)) {
            final SSLSocket sslSocket = (SSLSocket) socket;
            final Set<String> protocols = new TreeSet<>();
            protocols.addAll(Arrays.asList(sslSocket.getEnabledProtocols()));
            protocols.add(TlsVersion.TLS_1_2.javaName());
            sslSocket.setEnabledProtocols(protocols.toArray(new String[0]));
        }
        return socket;
    }
}
