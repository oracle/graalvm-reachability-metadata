/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_shrinkwrap.shrinkwrap_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.jboss.shrinkwrap.api.Assignable;
import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.impl.base.ServiceExtensionLoader;
import org.junit.jupiter.api.Test;

public class ServiceExtensionLoaderTest {
    @Test
    void loadCreatesExtensionWhoseConstructorRequiresArchiveView() {
        GenericArchive baseArchive = ShrinkWrap.create(GenericArchive.class, "service-loader-base.jar");
        ServiceExtensionLoader extensionLoader = new ServiceExtensionLoader(
            Collections.singletonList(Thread.currentThread().getContextClassLoader()));
        extensionLoader.addOverride(SpecificArchiveExtension.class, SpecificArchiveExtensionImpl.class);

        SpecificArchiveExtension extension = extensionLoader.load(SpecificArchiveExtension.class, baseArchive);

        assertThat(extension).isInstanceOf(SpecificArchiveExtensionImpl.class);
        assertThat(extension.getDelegateArchive()).isInstanceOf(JavaArchive.class);
        assertThat(extension.getDelegateArchive().getName()).isEqualTo(baseArchive.getName());
    }

    public interface SpecificArchiveExtension extends Assignable {
        JavaArchive getDelegateArchive();
    }

    public static final class SpecificArchiveExtensionImpl implements SpecificArchiveExtension {
        private final JavaArchive delegateArchive;

        public SpecificArchiveExtensionImpl(JavaArchive delegateArchive) {
            this.delegateArchive = delegateArchive;
        }

        @Override
        public JavaArchive getDelegateArchive() {
            return delegateArchive;
        }

        @Override
        public <TYPE extends Assignable> TYPE as(Class<TYPE> type) {
            throw new UnsupportedOperationException("Test extension is not assignable to other views");
        }
    }
}
