/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testng.annotations.ITestAnnotation;
import org.testng.internal.annotations.DefaultAnnotationTransformer;
import org.testng.internal.annotations.JDK15AnnotationFinder;

public class JDK15TagFactoryTest {
    @Test
    void createsTestTagByInvokingInheritedAnnotationAttributes() {
        JDK15AnnotationFinder finder = new JDK15AnnotationFinder(new DefaultAnnotationTransformer());

        ITestAnnotation annotation = finder.findAnnotation(ChildAnnotatedTestCase.class, ITestAnnotation.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.getGroups()).containsExactly("child-group", "base-group");
        assertThat(annotation.getDependsOnGroups()).containsExactly("base-dependency");
        assertThat(annotation.getDependsOnMethods()).containsExactly("childDependency");
        assertThat(annotation.getDataProviderClass()).isEqualTo(BaseDataProvider.class);
        assertThat(annotation.getDescription()).isEqualTo("base description");
    }

    @org.testng.annotations.Test(
            groups = "base-group",
            dependsOnGroups = "base-dependency",
            dataProviderClass = BaseDataProvider.class,
            description = "base description")
    public static class BaseAnnotatedTestCase {
    }

    @org.testng.annotations.Test(
            groups = "child-group",
            dependsOnMethods = "childDependency")
    public static class ChildAnnotatedTestCase extends BaseAnnotatedTestCase {
    }

    public static class BaseDataProvider {
    }
}
