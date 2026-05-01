/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax.jmdns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JmDNS {
    private final List<ServiceInfo> registeredServices = new ArrayList<>();
    private final List<ServiceInfo> unregisteredServices = new ArrayList<>();

    public static JmDNS create() {
        return new JmDNS();
    }

    public void registerService(ServiceInfo serviceInfo) {
        if (serviceInfo == null) {
            throw new IllegalArgumentException("serviceInfo must not be null");
        }
        registeredServices.add(serviceInfo);
    }

    public void unregisterService(ServiceInfo serviceInfo) {
        if (serviceInfo == null) {
            throw new IllegalArgumentException("serviceInfo must not be null");
        }
        registeredServices.remove(serviceInfo);
        unregisteredServices.add(serviceInfo);
    }

    public List<ServiceInfo> getRegisteredServices() {
        return Collections.unmodifiableList(registeredServices);
    }

    public List<ServiceInfo> getUnregisteredServices() {
        return Collections.unmodifiableList(unregisteredServices);
    }

    public void clear() {
        registeredServices.clear();
        unregisteredServices.clear();
    }
}
