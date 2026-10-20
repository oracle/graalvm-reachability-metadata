/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

@ImportedAnnotation(
        name = "fixture module",
        types = {String.class, Integer.class},
        nested = {@NestedAnnotation("first"), @NestedAnnotation("second")})
public class AnnotatedFixture {
    public String value;

    public AnnotatedFixture() {
        value = "constructed";
    }

    public String greet(String name) {
        return "Hello, " + name;
    }
}
