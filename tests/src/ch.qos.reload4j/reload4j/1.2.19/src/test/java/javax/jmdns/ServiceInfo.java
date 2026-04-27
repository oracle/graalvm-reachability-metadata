/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax.jmdns;

import java.util.LinkedHashMap;
import java.util.Map;

public class ServiceInfo {
    private final String type;
    private final String name;
    private final int port;
    private final int weight;
    private final int priority;
    private final Map<Object, Object> properties;

    public ServiceInfo(String type, String name, int port, int weight, int priority, Map<?, ?> properties) {
        this.type = type;
        this.name = name;
        this.port = port;
        this.weight = weight;
        this.priority = priority;
        this.properties = new LinkedHashMap<>();
        this.properties.putAll(properties);
    }

    public static ServiceInfo create(String type, String name, int port, int weight, int priority,
            Map<?, ?> properties) {
        return new ServiceInfo(type, name, port, weight, priority, properties);
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

    public int getWeight() {
        return weight;
    }

    public int getPriority() {
        return priority;
    }

    public Map<Object, Object> getProperties() {
        return properties;
    }
}
