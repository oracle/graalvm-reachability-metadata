/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hamcrest.hamcrest_core;

import org.hamcrest.Description;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.StringDescription;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;

public class ReflectiveTypeFinderTest {
    @Test
    public void resolvesExpectedTypeFromSuperclassForTypeSafeMatcher() {
        InheritedContainsNeedleMatcher matcher = new InheritedContainsNeedleMatcher();

        assertThat(matcher.matches("needle in a haystack")).isTrue();
        assertThat(matcher.matches(42)).isFalse();

        StringDescription mismatch = new StringDescription();
        matcher.describeMismatch("haystack", mismatch);

        assertThat(mismatch.toString()).isEqualTo("was \"haystack\"");
    }

    @Test
    public void resolvesExpectedTypeForTypeSafeDiagnosingMatcher() {
        NonBlankMatcher matcher = new NonBlankMatcher();

        assertThat(matcher.matches("hamcrest")).isTrue();
        assertThat(matcher.matches(42)).isFalse();

        StringDescription mismatch = new StringDescription();
        matcher.describeMismatch("   ", mismatch);

        assertThat(mismatch.toString()).isEqualTo("was blank");
    }

    @Test
    public void resolvesFeatureTypeForFeatureMatcher() {
        NameFeatureMatcher matcher = new NameFeatureMatcher();

        assertThat(matcher.matches(new Person("Ada"))).isTrue();
        assertThat(matcher.matches("Ada")).isFalse();

        StringDescription mismatch = new StringDescription();
        matcher.describeMismatch(new Person("Grace"), mismatch);

        assertThat(mismatch.toString()).isEqualTo("name was \"Grace\"");
    }

    private static class ContainsNeedleMatcher extends TypeSafeMatcher<String> {
        @Override
        protected boolean matchesSafely(String item) {
            return item.contains("needle");
        }

        @Override
        protected void describeMismatchSafely(String item, Description mismatchDescription) {
            mismatchDescription.appendText("was ").appendValue(item);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("a string containing \"needle\"");
        }
    }

    private static final class InheritedContainsNeedleMatcher extends ContainsNeedleMatcher {
    }

    private static final class NonBlankMatcher extends TypeSafeDiagnosingMatcher<String> {
        @Override
        protected boolean matchesSafely(String item, Description mismatchDescription) {
            if (item.trim().isEmpty()) {
                mismatchDescription.appendText("was blank");
                return false;
            }
            return true;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("a non-blank string");
        }
    }

    private static final class NameFeatureMatcher extends FeatureMatcher<Person, String> {
        private NameFeatureMatcher() {
            super(equalTo("Ada"), "a person with name", "name");
        }

        @Override
        protected String featureValueOf(Person actual) {
            return actual.getName();
        }
    }

    private static final class Person {
        private final String name;

        private Person(String name) {
            this.name = name;
        }

        private String getName() {
            return this.name;
        }
    }
}
