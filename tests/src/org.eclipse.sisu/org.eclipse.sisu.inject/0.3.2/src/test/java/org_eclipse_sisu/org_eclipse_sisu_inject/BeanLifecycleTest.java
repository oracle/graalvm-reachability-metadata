/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.eclipse.sisu.bean.LifecycleManager;
import org.junit.jupiter.api.Test;

public class BeanLifecycleTest {
    @Test
    void lifecycleManagerInvokesAnnotatedStartAndStopMethods() {
        LifecycleManager lifecycleManager = new LifecycleManager();
        ManagedBean bean = new ManagedBean();

        boolean managedType = lifecycleManager.manage(ManagedBean.class);
        boolean managedBean = lifecycleManager.manage(bean);
        boolean unmanagedBean = lifecycleManager.unmanage(bean);

        assertThat(managedType).isTrue();
        assertThat(managedBean).isTrue();
        assertThat(unmanagedBean).isTrue();
        assertThat(bean.events()).containsExactly(
            "base-start",
            "managed-start",
            "managed-stop",
            "base-stop");
    }

    private static class BaseBean {
        private final List<String> events = new ArrayList<>();

        @PostConstruct
        private void startBase() {
            events.add("base-start");
        }

        @PreDestroy
        private void stopBase() {
            events.add("base-stop");
        }

        List<String> events() {
            return events;
        }
    }

    private static final class ManagedBean extends BaseBean {
        @PostConstruct
        private void startManaged() {
            events().add("managed-start");
        }

        @PreDestroy
        private void stopManaged() {
            events().add("managed-stop");
        }
    }
}
