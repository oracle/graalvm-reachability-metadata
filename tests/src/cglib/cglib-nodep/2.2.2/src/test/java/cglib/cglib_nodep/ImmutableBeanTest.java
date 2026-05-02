/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import net.sf.cglib.beans.ImmutableBean;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ImmutableBeanTest {
    @Test
    void createsImmutableViewThatDelegatesReadsAndRejectsWrites() {
        try {
            MutableSampleBean source = new MutableSampleBean();
            source.setName("Ada");
            source.setCount(7);
            source.setActive(true);

            MutableSampleBean immutable = (MutableSampleBean) ImmutableBean.create(source);

            assertThat(immutable).isNotSameAs(source);
            assertThat(immutable.getName()).isEqualTo("Ada");
            assertThat(immutable.getCount()).isEqualTo(7);
            assertThat(immutable.isActive()).isTrue();

            source.setName("Grace");
            source.setCount(11);
            source.setActive(false);

            assertThat(immutable.getName()).isEqualTo("Grace");
            assertThat(immutable.getCount()).isEqualTo(11);
            assertThat(immutable.isActive()).isFalse();
            assertThatIllegalStateException()
                    .isThrownBy(() -> immutable.setName("Katherine"))
                    .withMessageContaining("Bean is immutable");
            assertThatIllegalStateException()
                    .isThrownBy(() -> immutable.setCount(13))
                    .withMessageContaining("Bean is immutable");
            assertThatIllegalStateException()
                    .isThrownBy(() -> immutable.setActive(true))
                    .withMessageContaining("Bean is immutable");
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    private static boolean isUnsupportedNativeImageDynamicClassLoading(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static class MutableSampleBean {
        private String name;
        private int count;
        private boolean active;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}
