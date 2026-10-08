/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.eclipse.jetty.alpn;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLSocket;

public final class ALPN {
    private static final Map<SSLSocket, Provider> PROVIDERS = new ConcurrentHashMap<>();

    private ALPN() {
    }

    public static void put(SSLSocket socket, Provider provider) {
        PROVIDERS.put(socket, provider);
    }

    public static Provider get(SSLSocket socket) {
        return PROVIDERS.get(socket);
    }

    public static void remove(SSLSocket socket) {
        PROVIDERS.remove(socket);
    }

    public interface Provider {
    }

    public interface ClientProvider extends Provider {
        boolean supports();

        void unsupported();

        List<String> protocols();

        String selectProtocol(List<String> protocols);

        void protocolSelected(String protocol);
    }

    public interface ServerProvider extends Provider {
        String select(List<String> protocols);

        void selected(String protocol);
    }
}
