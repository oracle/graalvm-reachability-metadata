/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ImportedAnnotation {
    String name();

    Class<?>[] types() default {};

    NestedAnnotation[] nested() default {};

    String[] allowedDependencies() default {};

    String[] exposedPackages() default {};
}
