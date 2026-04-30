/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_crac.crac;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.PlatformManagedObject;
import java.lang.reflect.Constructor;

import javax.management.ObjectName;

import org.crac.management.CRaCMXBean;
import org.junit.jupiter.api.Test;

public class CRaCImplTest {
    @Test
    void delegatesRestoreMetricsToPlatformBean() throws Exception {
        ObjectName objectName = new ObjectName("org.crac.test:type=CRaC");
        CRaCMXBean bean = newCRaCMXBean(new RecordingPlatformBean(123L, 456L, objectName));

        assertThat(bean.getUptimeSinceRestore()).isEqualTo(123L);
        assertThat(bean.getRestoreTime()).isEqualTo(456L);
        assertThat(bean.getObjectName()).isEqualTo(objectName);
    }

    @Test
    void reportsUnavailableMetricsWhenPlatformBeanThrows() throws Exception {
        CRaCMXBean bean = newCRaCMXBean(new FailingPlatformBean());

        assertThat(bean.getUptimeSinceRestore()).isEqualTo(-1L);
        assertThat(bean.getRestoreTime()).isEqualTo(-1L);
    }

    private static CRaCMXBean newCRaCMXBean(PlatformCRaCMXBean platformBean) throws Exception {
        Class<?> implementationClass = Class.forName("org.crac.management.CRaCImpl");
        Constructor<?> constructor = implementationClass.getDeclaredConstructor(Class.class, PlatformManagedObject.class);
        constructor.setAccessible(true);
        return (CRaCMXBean) constructor.newInstance(PlatformCRaCMXBean.class, platformBean);
    }

    public interface PlatformCRaCMXBean extends PlatformManagedObject {
        long getUptimeSinceRestore();

        long getRestoreTime();
    }

    public static final class RecordingPlatformBean implements PlatformCRaCMXBean {
        private final long uptimeSinceRestore;
        private final long restoreTime;
        private final ObjectName objectName;

        RecordingPlatformBean(long uptimeSinceRestore, long restoreTime, ObjectName objectName) {
            this.uptimeSinceRestore = uptimeSinceRestore;
            this.restoreTime = restoreTime;
            this.objectName = objectName;
        }

        @Override
        public long getUptimeSinceRestore() {
            return uptimeSinceRestore;
        }

        @Override
        public long getRestoreTime() {
            return restoreTime;
        }

        @Override
        public ObjectName getObjectName() {
            return objectName;
        }
    }

    public static final class FailingPlatformBean implements PlatformCRaCMXBean {
        @Override
        public long getUptimeSinceRestore() {
            throw new IllegalStateException("uptime unavailable");
        }

        @Override
        public long getRestoreTime() {
            throw new IllegalStateException("restore time unavailable");
        }

        @Override
        public ObjectName getObjectName() {
            return null;
        }
    }
}
