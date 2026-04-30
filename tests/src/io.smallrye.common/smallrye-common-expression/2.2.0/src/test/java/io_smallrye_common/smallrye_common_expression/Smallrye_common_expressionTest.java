/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.smallrye.common.expression.Expression;
import io.smallrye.common.expression.ResolveContext;
import org.junit.jupiter.api.Test;

public class Smallrye_common_expressionTest {
    private static final String SYSTEM_PROPERTY_KEY = "smallrye.expression.test.value";
    private static final String MISSING_SYSTEM_PROPERTY_KEY = "smallrye.expression.test.missing";
    private static final String MISSING_ENVIRONMENT_KEY = "SMALLRYE_EXPRESSION_TEST_MISSING_4B53E942";

    @Test
    void literalsAreTrimmedByDefaultAndCanPreserveWhitespace() {
        final Expression empty = Expression.compile("   ");
        final Expression literal = Expression.compile("  SmallRye expression literal  ");
        final Expression untrimmed = Expression.compile("  SmallRye expression literal  ", Expression.Flag.NO_TRIM);

        assertThat(evaluateWithoutResolutions(empty)).isEmpty();
        assertThat(evaluateWithoutResolutions(literal)).isEqualTo("SmallRye expression literal");
        assertThat(evaluateWithoutResolutions(untrimmed)).isEqualTo("  SmallRye expression literal  ");
        assertThat(literal.getReferencedStrings()).isEmpty();
    }

    @Test
    void expressionsResolveInsideLiteralTextAndExposeReferencedStrings() {
        final Expression expression = Expression.compile("http://${host}:${port}/service");
        final List<String> resolvedKeys = new ArrayList<>();

        final String result = expression.evaluate((context, builder) -> {
            final String key = context.getKey();
            resolvedKeys.add(key);
            switch (key) {
                case "host":
                    builder.append("localhost");
                    return;
                case "port":
                    builder.append("8080");
                    return;
                default:
                    throw new IllegalArgumentException("Unexpected key: " + key);
            }
        });

        assertThat(result).isEqualTo("http://localhost:8080/service");
        assertThat(resolvedKeys).containsExactly("host", "port");
        assertThat(expression.getReferencedStrings()).containsExactlyInAnyOrder("host", "port");
        assertThatThrownBy(() -> expression.getReferencedStrings().add("path"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void defaultValuesCanBeExpandedIntoCurrentOrSeparateBuilders() {
        final Expression expression = Expression.compile(
                "primary=${primary:${secondary:fallback}};optional=${optional:}");
        final List<String> resolvedKeys = new ArrayList<>();

        final String result = expression.evaluate((context, builder) -> {
            final String key = context.getKey();
            resolvedKeys.add(key);
            switch (key) {
                case "primary":
                    assertThat(context.hasDefault()).isTrue();
                    assertThat(context.getExpandedDefault()).isEqualTo("resolved-secondary");
                    context.expandDefault();
                    return;
                case "secondary":
                    assertThat(context.hasDefault()).isTrue();
                    assertThat(context.getExpandedDefault()).isEqualTo("fallback");
                    builder.append("resolved-secondary");
                    return;
                case "optional":
                    assertThat(context.hasDefault()).isTrue();
                    assertThat(context.getExpandedDefault()).isEmpty();
                    context.expandDefault(builder);
                    return;
                default:
                    throw new IllegalArgumentException("Unexpected key: " + key);
            }
        });

        assertThat(result).isEqualTo("primary=resolved-secondary;optional=");
        assertThat(resolvedKeys).containsExactly("primary", "secondary", "secondary", "optional");
        assertThat(expression.getReferencedStrings()).containsExactlyInAnyOrder("primary", "secondary", "optional");
    }

    @Test
    void recursiveKeyAndDefaultExpansionCanBeEnabledOrDisabled() {
        final Expression recursive = Expression.compile("${${tenant}.url:${fallback.value}}");
        final String recursiveResult = recursive.evaluate((context, builder) -> {
            final String key = context.getKey();
            switch (key) {
                case "tenant":
                    builder.append("acme");
                    return;
                case "acme.url":
                    context.expandDefault();
                    return;
                case "fallback.value":
                    builder.append("https://fallback.example");
                    return;
                default:
                    throw new IllegalArgumentException("Unexpected key: " + key);
            }
        });

        final Expression nonRecursiveKey = Expression.compile("${${tenant}.url}", Expression.Flag.NO_RECURSE_KEY);
        final String keyResult = nonRecursiveKey.evaluate((context, builder) -> {
            assertThat(context.getKey()).isEqualTo("${tenant}.url");
            builder.append("literal-key");
        });

        final Expression nonRecursiveDefault = Expression.compile("${missing:${fallback}}",
                Expression.Flag.NO_RECURSE_DEFAULT);
        final String defaultResult = nonRecursiveDefault.evaluate((context, builder) -> {
            assertThat(context.getKey()).isEqualTo("missing");
            assertThat(context.getExpandedDefault()).isEqualTo("${fallback}");
            context.expandDefault();
        });

        assertThat(recursiveResult).isEqualTo("https://fallback.example");
        assertThat(recursive.getReferencedStrings()).containsExactlyInAnyOrder("tenant", "fallback.value");
        assertThat(keyResult).isEqualTo("literal-key");
        assertThat(defaultResult).isEqualTo("${fallback}");
    }

    @Test
    void checkedExceptionsPropagateFromEvaluateException() {
        final Expression expression = Expression.compile("token=${secret}");

        assertThatThrownBy(() -> expression.<TestExpansionException>evaluateException((context, builder) -> {
            throw new TestExpansionException("Refusing key " + context.getKey());
        })).isInstanceOf(TestExpansionException.class)
                .hasMessage("Refusing key secret");
    }

    @Test
    void builtInPropertyAndEnvironmentResolversUseValuesDefaultsAndFailures() {
        final String previousProperty = System.getProperty(SYSTEM_PROPERTY_KEY);
        final String previousMissingProperty = System.getProperty(MISSING_SYSTEM_PROPERTY_KEY);
        try {
            System.setProperty(SYSTEM_PROPERTY_KEY, "configured");
            System.clearProperty(MISSING_SYSTEM_PROPERTY_KEY);

            final Expression properties = Expression.compile(
                    "value=${" + SYSTEM_PROPERTY_KEY + "};missing=${" + MISSING_SYSTEM_PROPERTY_KEY + ":fallback}");
            final Expression combined = Expression.compile("property=${" + SYSTEM_PROPERTY_KEY + "};environment=${env."
                    + MISSING_ENVIRONMENT_KEY + ":env-fallback}");
            final Expression emptyMissing = Expression.compile("before-${" + MISSING_SYSTEM_PROPERTY_KEY + "}-after");
            final Expression failingProperty = Expression.compile("${" + MISSING_SYSTEM_PROPERTY_KEY + "}");
            final Expression failingEnvironment = Expression.compile("${" + MISSING_ENVIRONMENT_KEY + "}");

            assertThat(properties.evaluateWithProperties(true)).isEqualTo("value=configured;missing=fallback");
            assertThat(combined.evaluateWithPropertiesAndEnvironment(true))
                    .isEqualTo("property=configured;environment=env-fallback");
            assertThat(emptyMissing.evaluateWithProperties(false)).isEqualTo("before--after");
            assertThat(Expression.compile("${" + MISSING_ENVIRONMENT_KEY + ":env-fallback}")
                    .evaluateWithEnvironment(true)).isEqualTo("env-fallback");
            assertThatThrownBy(() -> failingProperty.evaluateWithProperties(true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(MISSING_SYSTEM_PROPERTY_KEY);
            assertThatThrownBy(() -> failingEnvironment.evaluateWithEnvironment(true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(MISSING_ENVIRONMENT_KEY);
        } finally {
            restoreSystemProperty(SYSTEM_PROPERTY_KEY, previousProperty);
            restoreSystemProperty(MISSING_SYSTEM_PROPERTY_KEY, previousMissingProperty);
        }
    }

    @Test
    void environmentResolverExpandsExistingRuntimeVariables() {
        final String path = System.getenv("PATH");
        assertThat(path).isNotBlank();

        final Expression environment = Expression.compile("path=${PATH}");
        final Expression combined = Expression.compile("path=${env.PATH}");

        assertThat(environment.evaluateWithEnvironment(true)).isEqualTo("path=" + path);
        assertThat(combined.evaluateWithPropertiesAndEnvironment(true)).isEqualTo("path=" + path);
    }

    @Test
    void miniExpressionsEscapesAndLenientSyntaxChangeParsingRules() {
        final Expression miniExpressions = Expression.compile("price=$$;close=$};colon=$:;letter=$x",
                Expression.Flag.MINI_EXPRS);
        final Expression literalDollar = Expression.compile("price=$$");
        final Expression escaped = Expression.compile("line\\ncol\\tpath\\\\end", Expression.Flag.ESCAPES,
                Expression.Flag.NO_TRIM);
        final Expression lenient = Expression.compile("broken=$ and escaped=\\q", Expression.Flag.LENIENT_SYNTAX,
                Expression.Flag.ESCAPES, Expression.Flag.NO_TRIM);

        final String miniResult = miniExpressions.evaluate((context, builder) -> builder.append('<')
                .append(context.getKey()).append('>'));

        assertThat(miniResult).isEqualTo("price=<$>;close=<}>;colon=<:>;letter=<x>");
        assertThat(evaluateWithoutResolutions(literalDollar)).isEqualTo("price=$");
        assertThat(evaluateWithoutResolutions(escaped)).isEqualTo("line\ncol\tpath\\end");
        assertThat(evaluateWithoutResolutions(lenient)).isEqualTo("broken=$ and escaped=q");
    }

    @Test
    void escapesAreAppliedInsideDefaultValues() {
        final String expectedDefault = "line\rnext\btab\fslash\\end";
        final Expression expression = Expression.compile("${missing:line\\rnext\\btab\\fslash\\\\end}",
                Expression.Flag.ESCAPES);

        final String result = expression.evaluate((context, builder) -> {
            assertThat(context.getKey()).isEqualTo("missing");
            assertThat(context.hasDefault()).isTrue();
            assertThat(context.getExpandedDefault()).isEqualTo(expectedDefault);
            context.expandDefault(builder);
        });

        assertThat(result).isEqualTo(expectedDefault);
    }

    @Test
    void smartBracesKeepBracedDefaultContentTogetherUnlessDisabled() {
        final Expression smartBraces = Expression.compile("${missing:{one:two}}");
        final Expression noSmartBraces = Expression.compile("${missing:{one:two}}", Expression.Flag.NO_SMART_BRACES);
        final List<String> expandedDefaults = new ArrayList<>();

        final String smartResult = smartBraces.evaluate((context, builder) -> {
            assertThat(context.getKey()).isEqualTo("missing");
            expandedDefaults.add(context.getExpandedDefault());
            context.expandDefault();
        });
        final String noSmartResult = noSmartBraces.evaluate((context, builder) -> {
            assertThat(context.getKey()).isEqualTo("missing");
            expandedDefaults.add(context.getExpandedDefault());
            context.expandDefault();
        });

        assertThat(smartResult).isEqualTo("{one:two}");
        assertThat(noSmartResult).isEqualTo("{one:two}");
        assertThat(expandedDefaults).containsExactly("{one:two}", "{one:two");
    }

    @Test
    void generalExpansionDoubleColonAndEnumSetCompilationAreSupported() {
        final Expression general = Expression.compile("${{policy.key}}/${name}", Expression.Flag.GENERAL_EXPANSION);
        final Expression doubleColonKey = Expression.compile("${handler::method:ignored}",
                Expression.Flag.DOUBLE_COLON);
        final Expression doubleColonDefault = Expression.compile("${handler:method::name}",
                EnumSet.of(Expression.Flag.DOUBLE_COLON));

        final String generalResult = general.evaluate((context, builder) -> {
            final String key = context.getKey();
            if (key.equals("policy.key")) {
                builder.append("allowed");
            } else if (key.equals("name")) {
                builder.append("alice");
            } else {
                throw new IllegalArgumentException("Unexpected key: " + key);
            }
        });
        final String keyResult = doubleColonKey.evaluate((context, builder) -> {
            assertThat(context.getKey()).isEqualTo("handler::method:ignored");
            assertThat(context.hasDefault()).isFalse();
            builder.append("resolved-handler");
        });
        final String defaultResult = doubleColonDefault.evaluate((context, builder) -> {
            assertThat(context.getKey()).isEqualTo("handler");
            assertThat(context.hasDefault()).isTrue();
            context.expandDefault();
        });

        assertThat(generalResult).isEqualTo("allowed/alice");
        assertThat(keyResult).isEqualTo("resolved-handler");
        assertThat(defaultResult).isEqualTo("method::name");
    }

    @Test
    void invalidSyntaxThrowsUnlessLenientSyntaxIsEnabled() {
        assertThatThrownBy(() -> Expression.compile("$")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid expression syntax");
        assertThatThrownBy(() -> Expression.compile("${unterminated")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid expression syntax");
        assertThatThrownBy(() -> Expression.compile("bad\\", Expression.Flag.ESCAPES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid expression syntax");

        final Expression lenientExpression = Expression.compile("${unterminated", Expression.Flag.LENIENT_SYNTAX);
        final String result = lenientExpression.evaluate((context, builder) -> {
            assertThat(context.getKey()).isEqualTo("unterminated");
            builder.append("resolved");
        });

        assertThat(result).isEqualTo("resolved");
    }

    @Test
    void resolveContextIsOnlyUsableDuringResolution() {
        final AtomicReference<ResolveContext<RuntimeException>> capturedContext = new AtomicReference<>();
        final Expression expression = Expression.compile("${key:default}");

        final String result = expression.evaluate((context, builder) -> {
            capturedContext.set(context);
            assertThat(context.getKey()).isEqualTo("key");
            assertThat(context.getExpandedDefault()).isEqualTo("default");
            builder.append("resolved");
        });

        assertThat(result).isEqualTo("resolved");
        assertThat(capturedContext.get()).isNotNull();
        assertThatThrownBy(() -> capturedContext.get().getKey()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> capturedContext.get().expandDefault()).isInstanceOf(IllegalStateException.class);
    }

    private static String evaluateWithoutResolutions(final Expression expression) {
        return expression.evaluate((context, builder) -> {
            throw new AssertionError("No expressions should be resolved");
        });
    }

    private static void restoreSystemProperty(final String key, final String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }

    private static final class TestExpansionException extends Exception {
        private static final long serialVersionUID = 1L;

        private TestExpansionException(final String message) {
            super(message);
        }
    }
}
