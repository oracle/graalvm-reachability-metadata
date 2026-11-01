/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.annotation.AnnotationBeanUtils;
import org.springframework.core.annotation.AnnotationUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotationBeanUtilsTest {
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Profile {
        String name();
        int age();
    }

    @Profile(name = "annotated", age = 38)
    public static class Configuration {
    }

    @Test
    void copiesAnnotationAttributesToBeanExceptExcludedProperties() {
        TestBeans.Person person = new TestBeans.Person();
        AnnotationBeanUtils.copyPropertiesToBean(AnnotationUtils.findAnnotation(Configuration.class, Profile.class), person, "age");
        assertEquals("annotated", person.getName());
        assertEquals(0, person.getAge());
    }
}
