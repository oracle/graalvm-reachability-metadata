/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax.jmdns;

public final class JmDNS {
    private static final JmDNS INSTANCE = new JmDNS();

    private ServiceInfo registeredService;

    public JmDNS() {
    }

    public static JmDNS create() {
        return INSTANCE;
    }

    public void registerService(ServiceInfo serviceInfo) {
        registeredService = serviceInfo;
    }

    public void unregisterService(ServiceInfo serviceInfo) {
        if (registeredService == serviceInfo) {
            registeredService = null;
        }
    }

    public ServiceInfo getRegisteredService() {
        return registeredService;
    }
}
