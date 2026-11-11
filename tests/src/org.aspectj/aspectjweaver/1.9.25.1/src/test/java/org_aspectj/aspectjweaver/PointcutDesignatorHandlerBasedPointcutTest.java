/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import org.aspectj.weaver.tools.ContextBasedMatcher;
import org.aspectj.weaver.tools.DefaultMatchingContext;
import org.aspectj.weaver.tools.FuzzyBoolean;
import org.aspectj.weaver.tools.MatchingContext;
import org.aspectj.weaver.tools.PointcutDesignatorHandler;
import org.aspectj.weaver.tools.PointcutExpression;
import org.aspectj.weaver.tools.PointcutParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PointcutDesignatorHandlerBasedPointcutTest {
    @Test
    void customDesignatorFastMatchLoadsCandidateTypeThroughParserApi() {
        PointcutParser parser = PointcutParser
                .getPointcutParserSupportingAllPrimitivesAndUsingSpecifiedClassloaderForResolution(
                        getClass().getClassLoader());
        RecordingPointcutDesignatorHandler handler = new RecordingPointcutDesignatorHandler();
        parser.registerPointcutDesignatorHandler(handler);
        DefaultMatchingContext context = new DefaultMatchingContext();
        context.addContextBinding("enabled", Boolean.TRUE);

        PointcutExpression expression = parser.parsePointcutExpression("recorded(enabled)");
        expression.setMatchingContext(context);

        boolean couldMatch = expression.couldMatchJoinPointsInType(PointcutParser.class);

        assertThat(couldMatch).isTrue();
        assertThat(handler.matcher().candidateType()).isEqualTo(PointcutParser.class);
        assertThat(handler.matcher().context()).isSameAs(context);
        assertThat(handler.matcher().expression()).isEqualTo("enabled");
    }

    private static final class RecordingPointcutDesignatorHandler implements PointcutDesignatorHandler {
        private RecordingContextBasedMatcher matcher;

        @Override
        public String getDesignatorName() {
            return "recorded";
        }

        @Override
        public ContextBasedMatcher parse(String expression) {
            matcher = new RecordingContextBasedMatcher(expression);
            return matcher;
        }

        RecordingContextBasedMatcher matcher() {
            return matcher;
        }
    }

    private static final class RecordingContextBasedMatcher implements ContextBasedMatcher {
        private final String expression;
        private Class<?> candidateType;
        private MatchingContext context;

        private RecordingContextBasedMatcher(String expression) {
            this.expression = expression;
        }

        @Override
        public boolean couldMatchJoinPointsInType(Class aClass) {
            return false;
        }

        @Override
        public boolean couldMatchJoinPointsInType(Class aClass, MatchingContext matchContext) {
            candidateType = aClass;
            context = matchContext;
            return matchContext.hasContextBinding(expression)
                    && Boolean.TRUE.equals(matchContext.getBinding(expression));
        }

        @Override
        public boolean mayNeedDynamicTest() {
            return false;
        }

        @Override
        public FuzzyBoolean matchesStatically(MatchingContext matchContext) {
            return FuzzyBoolean.NO;
        }

        @Override
        public boolean matchesDynamically(MatchingContext matchContext) {
            return false;
        }

        String expression() {
            return expression;
        }

        Class<?> candidateType() {
            return candidateType;
        }

        MatchingContext context() {
            return context;
        }
    }
}
