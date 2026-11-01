/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.apache.aries.blueprint.container.BlueprintContainerImpl;
import org.apache.aries.blueprint.container.ServiceListener;
import org.apache.aries.blueprint.utils.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {
    @Test
    void constructsAndInvokesLifecycleCallback() throws Exception {
        assertThat(ReflectionUtils.hasDefaultConstructor(Lifecycle.class)).isTrue();
        Lifecycle bean = (Lifecycle) ReflectionUtils.newInstance(null, Lifecycle.class);
        Method start = ReflectionUtils.getLifecycleMethod(Lifecycle.class, "start");
        ReflectionUtils.invoke(null, start, bean);
        assertThat(bean.started).isTrue();
        assertThat(ReflectionUtils.getLifecycleMethod(Lifecycle.class, "status")).isNull();
    }

    @Test
    void dispatchesCompatibleServiceListenerCallbacks() {
        BlueprintContainerImpl container = RecipeSupport.container();
        RegistrationListener callback = new RegistrationListener();
        ServiceListener listener = new ServiceListener();
        listener.setBlueprintContainer(container);
        listener.setListener(callback);
        listener.setRegisterMethod("registered");
        listener.setUnregisterMethod("unregistered");
        List<String> service = List.of("blueprint");
        Map<String, Object> properties = Map.of("region", "west");
        listener.register(service, properties);
        assertThat(callback.service).isSameAs(service);
        assertThat(callback.region).isEqualTo("west");
        listener.unregister(service, properties);
        assertThat(callback.removed).isTrue();
    }

    public static class Lifecycle {
        private boolean started;

        public void start() {
            started = true;
        }

        public String status() {
            return started ? "started" : "stopped";
        }
    }

    public static class RegistrationListener {
        private Object service;
        private Object region;
        private boolean removed;

        public void registered(List<?> value, Map<String, Object> properties) {
            service = value;
            region = properties.get("region");
        }

        public void unregistered(List<?> value, Map<String, Object> properties) {
            removed = service == value && region.equals(properties.get("region"));
        }
    }
}
