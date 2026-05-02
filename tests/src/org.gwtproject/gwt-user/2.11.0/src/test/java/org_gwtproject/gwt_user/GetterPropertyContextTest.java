/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.web.bindery.autobean.shared.AutoBean;
import com.google.web.bindery.autobean.shared.AutoBeanFactory;
import com.google.web.bindery.autobean.shared.AutoBeanVisitor.PropertyContext;
import com.google.web.bindery.autobean.vm.AutoBeanFactorySource;
import com.google.web.bindery.autobean.vm.impl.ProxyAutoBean;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

public class GetterPropertyContextTest {
    private static final String CONTEXT_CLASS_NAME =
            "com.google.web.bindery.autobean.vm.impl.GetterPropertyContext";

    @Test
    void setterBackedContextInvokesWrappedBeanShimSetter() throws Exception {
        SampleFactory factory = AutoBeanFactorySource.create(SampleFactory.class);
        MutableProfileImpl delegate = new MutableProfileImpl("Ada");
        AutoBean<MutableProfile> bean = factory.profile(delegate);
        Method setter = MutableProfile.class.getMethod("setName", String.class);

        PropertyContext context = createGetterPropertyContext(
                bean, setter, String.class, String.class);

        assertThat(context.canSet()).isTrue();
        assertThat(context.getType()).isEqualTo(String.class);

        context.set("Grace");

        assertThat(delegate.getName()).isEqualTo("Grace");
    }

    private static PropertyContext createGetterPropertyContext(
            AutoBean<MutableProfile> bean,
            Method setter,
            Type genericType,
            Class<?> type) throws Exception {
        Class<?> contextType = Class.forName(CONTEXT_CLASS_NAME);
        Constructor<?> constructor = contextType.getDeclaredConstructor(
                ProxyAutoBean.class,
                Method.class,
                Type.class,
                Class.class,
                Class.class,
                Class.class,
                Class.class);
        constructor.setAccessible(true);
        return (PropertyContext) constructor.newInstance(
                bean, setter, genericType, type, null, null, null);
    }

    public interface SampleFactory extends AutoBeanFactory {
        AutoBean<MutableProfile> profile(MutableProfile profile);
    }

    public interface MutableProfile {
        String getName();

        void setName(String name);
    }

    public static final class MutableProfileImpl implements MutableProfile {
        private String name;

        MutableProfileImpl(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }
    }
}
