/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.util.FuzzyBoolean;
import org.aspectj.weaver.ResolvedType;
import org.aspectj.weaver.Shadow;
import org.aspectj.weaver.internal.tools.PointcutDesignatorHandlerBasedPointcut;
import org.aspectj.weaver.reflect.ReflectionFastMatchInfo;
import org.aspectj.weaver.reflect.ReflectionWorld;
import org.aspectj.weaver.tools.ContextBasedMatcher;
import org.aspectj.weaver.tools.MatchingContext;
import org.junit.jupiter.api.Test;

public class PointcutDesignatorHandlerBasedPointcutTest {
    @Test
    void fastMatchLoadsResolvedTypeAndDelegatesToContextMatcher() {
        ReflectionWorld world = new ReflectionWorld(
                PointcutDesignatorHandlerBasedPointcutTest.class.getClassLoader());
        ResolvedType resolvedType = world.resolve(FastMatchFixture.class);
        TestMatchingContext context = new TestMatchingContext();
        RecordingContextBasedMatcher matcher = new RecordingContextBasedMatcher(true);
        PointcutDesignatorHandlerBasedPointcut pointcut = new PointcutDesignatorHandlerBasedPointcut(matcher, world);

        FuzzyBoolean result = pointcut.fastMatch(
                new ReflectionFastMatchInfo(resolvedType, Shadow.MethodExecution, context, world));

        assertThat(result).isEqualTo(FuzzyBoolean.YES);
        assertThat(matcher.matchedType).isEqualTo(FastMatchFixture.class);
        assertThat(matcher.matchingContext).isSameAs(context);
    }

    @Test
    void fastMatchReturnsNoWhenContextMatcherRejectsResolvedType() {
        ReflectionWorld world = new ReflectionWorld(
                PointcutDesignatorHandlerBasedPointcutTest.class.getClassLoader());
        ResolvedType resolvedType = world.resolve(FastMatchFixture.class);
        RecordingContextBasedMatcher matcher = new RecordingContextBasedMatcher(false);
        PointcutDesignatorHandlerBasedPointcut pointcut = new PointcutDesignatorHandlerBasedPointcut(matcher, world);

        FuzzyBoolean result = pointcut.fastMatch(
                new ReflectionFastMatchInfo(resolvedType, Shadow.MethodExecution, new TestMatchingContext(), world));

        assertThat(result).isEqualTo(FuzzyBoolean.NO);
        assertThat(matcher.matchedType).isEqualTo(FastMatchFixture.class);
    }

    private static final class RecordingContextBasedMatcher implements ContextBasedMatcher {
        private final boolean couldMatch;
        private Class<?> matchedType;
        private MatchingContext matchingContext;

        private RecordingContextBasedMatcher(boolean couldMatch) {
            this.couldMatch = couldMatch;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean couldMatchJoinPointsInType(Class aClass) {
            return couldMatch;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean couldMatchJoinPointsInType(Class aClass, MatchingContext context) {
            matchedType = aClass;
            matchingContext = context;
            return couldMatch;
        }

        @Override
        public boolean mayNeedDynamicTest() {
            return false;
        }

        @Override
        public org.aspectj.weaver.tools.FuzzyBoolean matchesStatically(MatchingContext context) {
            return org.aspectj.weaver.tools.FuzzyBoolean.MAYBE;
        }

        @Override
        public boolean matchesDynamically(MatchingContext context) {
            return false;
        }
    }

    private static final class TestMatchingContext implements MatchingContext {
        @Override
        public boolean hasContextBinding(String contextParameterName) {
            return false;
        }

        @Override
        public Object getBinding(String contextParameterName) {
            return null;
        }
    }

    private static final class FastMatchFixture {
    }
}
