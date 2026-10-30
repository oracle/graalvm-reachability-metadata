/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_testsupport.org_apache_aries_testsupport_unit;

import org.apache.aries.unittest.mocks.MethodCall;
import org.apache.aries.unittest.mocks.Skeleton;
import org.apache.aries.unittest.mocks.annotations.InjectSkeleton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SkeletonTest {
    @Test
    void configuresReturnValuesAndRecordsCalls() {
        Greeting greeting = Skeleton.newMock(Greeting.class);
        Skeleton skeleton = Skeleton.getSkeleton(greeting);
        MethodCall call = new MethodCall(Greeting.class, "greet", "visitor");
        skeleton.setReturnValue(call, "Welcome, visitor");

        assertEquals("Welcome, visitor", greeting.greet("visitor"));
        skeleton.assertCalledExactNumberOfTimes(call, 1);
        assertTrue(Skeleton.isSkeleton(greeting));
    }

    @Test
    void sharesConfiguredBehaviorAcrossMultipleInterfaces() {
        Object mock = Skeleton.newMock(new Class<?>[]{Greeting.class, Activity.class});
        Skeleton skeleton = Skeleton.getSkeleton(mock);
        skeleton.setReturnValue(new MethodCall(Greeting.class, "greet", String.class), "hello");

        assertEquals("hello", ((Greeting) mock).greet("guest"));
        ((Activity) mock).perform();
        skeleton.assertCalled(new MethodCall(Activity.class, "perform"));
    }

    @Test
    void retainsBeanPropertiesWithPrimitiveAndWrapperAccessors() {
        Bean bean = Skeleton.newMock(Bean.class);
        assertFalse(bean.isEnabled());
        assertFalse(bean.isActive());

        bean.setName("sample");
        bean.setCount(12);
        bean.setEnabled(true);
        bean.setActive(Boolean.TRUE);

        assertEquals("sample", bean.getName());
        assertEquals(12, bean.getCount());
        assertTrue(bean.isEnabled());
        assertTrue(bean.isActive());
    }

    @Test
    void recordsWriteOnlyPropertiesAndSuppliesReadOnlyDefaults() {
        Bean bean = Skeleton.newMock(Bean.class);

        bean.setOnly(Boolean.TRUE);

        Skeleton.getSkeleton(bean).assertCalled(new MethodCall(Bean.class, "setOnly", Boolean.TRUE));
        assertEquals(0, bean.getRevision());
    }

    @Test
    void injectsTheBackingSkeletonIntoATemplate() {
        ActivityTemplate template = new ActivityTemplate();
        Activity activity = Skeleton.newMock(template, Activity.class);

        activity.perform();

        assertEquals(1, template.invocations);
        assertSame(Skeleton.getSkeleton(activity), template.skeleton);
        assertSame(template, template.skeleton.getTemplateObject());
        template.skeleton.assertCalled(new MethodCall(Activity.class, "perform"));
    }

    public interface Greeting {
        String greet(String name);
    }

    public interface Activity {
        void perform();
    }

    public interface Bean {
        String getName();

        void setName(String name);

        int getCount();

        void setCount(Integer count);

        Boolean isEnabled();

        void setEnabled(boolean enabled);

        boolean isActive();

        void setActive(Boolean active);

        void setOnly(Boolean value);

        int getRevision();
    }

    public static class ActivityTemplate {
        @InjectSkeleton
        public Skeleton skeleton;
        private int invocations;

        public void perform() {
            invocations++;
        }
    }
}
