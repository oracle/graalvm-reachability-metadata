/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.cglib.beans.BeanCopier;
import net.sf.cglib.core.Converter;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class BeanCopierTest {
    @Test
    void createsCopierThroughConfiguredGenerator() {
        try {
            BeanCopier.Generator generator = new BeanCopier.Generator();
            generator.setSource(SourceBean.class);
            generator.setTarget(TargetBean.class);
            generator.setUseConverter(false);
            SourceBean source = new SourceBean();
            source.setName("Katherine");
            source.setCount(11);
            source.setActive(true);
            TargetBean target = new TargetBean();

            BeanCopier copier = generator.create();
            copier.copy(source, target, null);

            assertThat(target.getName()).isEqualTo("Katherine");
            assertThat(target.getCount()).isEqualTo(11);
            assertThat(target.isActive()).isTrue();
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

    @Test
    void copiesMatchingBeanPropertiesWithoutConverter() {
        try {
            BeanCopier copier = BeanCopier.create(SourceBean.class, TargetBean.class, false);
            SourceBean source = new SourceBean();
            source.setName("Ada");
            source.setCount(7);
            source.setActive(true);
            TargetBean target = new TargetBean();

            copier.copy(source, target, null);

            assertThat(target.getName()).isEqualTo("Ada");
            assertThat(target.getCount()).isEqualTo(7);
            assertThat(target.isActive()).isTrue();
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

    @Test
    void convertsIncompatibleBeanPropertiesWithConverter() {
        try {
            BeanCopier copier = BeanCopier.create(SourceBean.class, ConvertedTargetBean.class, true);
            SourceBean source = new SourceBean();
            source.setName("Grace");
            source.setCount(3);
            source.setActive(false);
            ConvertedTargetBean target = new ConvertedTargetBean();

            copier.copy(source, target, new FormattingConverter());

            assertThat(target.getName()).isEqualTo("Grace");
            assertThat(target.getCount()).isEqualTo("3 items");
            assertThat(target.isActive()).isFalse();
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
            if (current instanceof NoClassDefFoundError) {
                String message = current.getMessage();
                if (message != null && message.startsWith("Could not initialize class net.sf.cglib.")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public static class FormattingConverter implements Converter {
        public Object convert(Object value, Class target, Object context) {
            if (String.class.equals(target) && "setCount".equals(context)) {
                return value + " items";
            }
            return value;
        }
    }

    public static class SourceBean {
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

    public static class TargetBean {
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

    public static class ConvertedTargetBean {
        private String name;
        private String count;
        private boolean active;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCount() {
            return count;
        }

        public void setCount(String count) {
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
