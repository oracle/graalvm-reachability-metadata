/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax.jmdns;

import java.util.Hashtable;

public class ServiceInfo {
    private final String zone;
    private final String name;
    private final int port;
    private final int weight;
    private final int priority;
    private final Hashtable<?, ?> properties;

    public ServiceInfo(String zone, String name, int port, int weight, int priority, Hashtable<?, ?> properties) {
        this.zone = zone;
        this.name = name;
        this.port = port;
        this.weight = weight;
        this.priority = priority;
        this.properties = properties;
    }

    public String zone() {
        return zone;
    }

    public String name() {
        return name;
    }

    public int port() {
        return port;
    }

    public int weight() {
        return weight;
    }

    public int priority() {
        return priority;
    }

    public Hashtable<?, ?> properties() {
        return properties;
    }
}
