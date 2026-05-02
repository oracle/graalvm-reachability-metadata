/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package bitronix.tm.resource.ehcache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.transaction.xa.XAResource;

public final class EhCacheXAResourceProducer {
    private static final List<String> REGISTERED_UNIQUE_NAMES = new ArrayList<>();
    private static final List<XAResource> REGISTERED_RESOURCES = new ArrayList<>();
    private static final List<String> UNREGISTERED_UNIQUE_NAMES = new ArrayList<>();
    private static final List<XAResource> UNREGISTERED_RESOURCES = new ArrayList<>();

    private EhCacheXAResourceProducer() {
    }

    public static void registerXAResource(String uniqueName, XAResource resource) {
        REGISTERED_UNIQUE_NAMES.add(uniqueName);
        REGISTERED_RESOURCES.add(resource);
    }

    public static void unregisterXAResource(String uniqueName, XAResource resource) {
        UNREGISTERED_UNIQUE_NAMES.add(uniqueName);
        UNREGISTERED_RESOURCES.add(resource);
    }

    public static List<String> getRegisteredUniqueNames() {
        return Collections.unmodifiableList(REGISTERED_UNIQUE_NAMES);
    }

    public static List<XAResource> getRegisteredResources() {
        return Collections.unmodifiableList(REGISTERED_RESOURCES);
    }

    public static List<String> getUnregisteredUniqueNames() {
        return Collections.unmodifiableList(UNREGISTERED_UNIQUE_NAMES);
    }

    public static List<XAResource> getUnregisteredResources() {
        return Collections.unmodifiableList(UNREGISTERED_RESOURCES);
    }

    public static void reset() {
        REGISTERED_UNIQUE_NAMES.clear();
        REGISTERED_RESOURCES.clear();
        UNREGISTERED_UNIQUE_NAMES.clear();
        UNREGISTERED_RESOURCES.clear();
    }
}
