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

    public void registerService(ServiceInfo serviceInfo) {
        registeredServices.add(serviceInfo);
    }

    public void unregisterService(ServiceInfo serviceInfo) {
        registeredServices.remove(serviceInfo);
    }

    public List<ServiceInfo> getRegisteredServices() {
        return Collections.unmodifiableList(registeredServices);
    }
}
