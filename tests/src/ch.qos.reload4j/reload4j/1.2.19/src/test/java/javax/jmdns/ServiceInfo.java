/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax.jmdns;

import java.util.Hashtable;
import java.util.Map;

public final class ServiceInfo {
    private final String type;
    private final String name;
    private final int port;
    private final Map properties;

    public ServiceInfo(String type, String name, int port, int weight, int priority, Hashtable properties) {
        this(type, name, port, (Map) properties);
    }

    private ServiceInfo(String type, String name, int port, Map properties) {
        this.type = type;
        this.name = name;
        this.port = port;
        this.properties = properties;
    }

    public static ServiceInfo create(String type, String name, int port, int weight, int priority, Map properties) {
        return new ServiceInfo(type, name, port, properties);
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public int getPort() {
        return port;
    }

    public Map getProperties() {
        return properties;
    }
}
