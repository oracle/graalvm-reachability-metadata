/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_macro

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.macro.matcher.ASTMatcher
import org.codehaus.groovy.macro.matcher.TreeContext
import org.codehaus.groovy.macro.methods.MacroGroovyMethods
import org.codehaus.groovy.macro.runtime.MacroBuilder
import org.codehaus.groovy.macro.transform.MacroClass
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX

public class Groovy_macroTest {
    @Test
    void macroCreatesTypedAstAndAppliesExpressionSubstitutions() {
        VariableExpression replacementArgument = varX('replacementArgument')

        ReturnStatement result = macro {
            return new MissingType($v { replacementArgument }, 'fixed')
        }

        assertThat(result).isInstanceOf(ReturnStatement)
        assertThat(result.expression).isInstanceOf(ConstructorCallExpression)

        ConstructorCallExpression constructorCall = (ConstructorCallExpression) result.expression
        assertThat(constructorCall.type.name).isEqualTo('MissingType')

        TupleExpression arguments = (TupleExpression) constructorCall.arguments
        assertThat(arguments.expressions).hasSize(2)
        assertThat(arguments.expressions[0]).isSameAs(replacementArgument)
        assertThat(((ConstantExpression) arguments.expressions[1]).value).isEqualTo('fixed')
    }

    @Test
    void macroCanKeepFullClosureBlockInsteadOfOnlyLastExpression() {
        BlockStatement block = macro(true) {
            println 'alpha'
            println 'beta'
        }

        assertThat(block.statements).hasSize(2)
        assertPrintlnStatement(block.statements[0], 'alpha')
        assertPrintlnStatement(block.statements[1], 'beta')
    }

    @Test
    void macroSupportsExplicitCompilePhase() {
        BlockStatement block = macro(CompilePhase.CLASS_GENERATION, true) {
            println 'before'
            println 'after'
        }

        assertThat(block.statements).hasSize(2)
        assertPrintlnStatement(block.statements[0], 'before')
        assertThat(block.statements[1]).isInstanceOf(ReturnStatement)

        ReturnStatement returnStatement = (ReturnStatement) block.statements[1]
        assertThat(returnStatement.expression).isInstanceOf(MethodCallExpression)
        MethodCallExpression call = (MethodCallExpression) returnStatement.expression
        assertThat(call.methodAsString).isEqualTo('println')
    }

    @Test
    void runtimeMacroBuilderBuildsExpressionsBlocksAndClassesFromSource() {
        BinaryExpression expression = MacroBuilder.INSTANCE.macro(
                '{ left + $v { ignoredAtParseTime } }',
                [{ -> constX(7) }],
                BinaryExpression
        )

        assertThat(expression.leftExpression).isInstanceOf(VariableExpression)
        assertThat(((VariableExpression) expression.leftExpression).name).isEqualTo('left')
        assertThat(expression.rightExpression).isInstanceOf(ConstantExpression)
        assertThat(((ConstantExpression) expression.rightExpression).value).isEqualTo(7)

        BlockStatement block = MacroBuilder.INSTANCE.macro(
                CompilePhase.CONVERSION,
                true,
                '{ firstCall()\nsecondCall() }',
                [],
                BlockStatement
        )
        assertThat(block.statements).hasSize(2)
        assertThat(methodCallFromExpressionStatement(block.statements[0]).methodAsString).isEqualTo('firstCall')
        assertThat(methodCallFromExpressionStatement(block.statements[1]).methodAsString).isEqualTo('secondCall')

        ClassNode generatedClass = MacroBuilder.INSTANCE.macro(
                'class GeneratedMacroType { String name\n int nameSize() { name.size() } }',
                [],
                ClassNode
        )
        assertThat(generatedClass.nameWithoutPackage).isEqualTo('GeneratedMacroType')
        assertThat(generatedClass.getField('name')).isNotNull()
        assertThat(generatedClass.getMethods('nameSize')).hasSize(1)
    }

    @Test
    void macroClassCapturesClassDefinitionAsClassNode() {
        ClassNode classNode = new MacroClass() {
            class Book {
                String title

                int titleLength() {
                    title.length()
                }
            }
        }

        assertThat(classNode).isInstanceOf(ClassNode)
        assertThat(classNode.nameWithoutPackage).isEqualTo('Book')
        assertThat(classNode.getField('title')).isNotNull()
        assertThat(classNode.getMethods('titleLength')).hasSize(1)
    }

    @Test
    void astMatcherMatchesWildcardsAndRejectsDifferentShapes() {
        Expression actual = macro { calculator.total(items[0], 42) + 5 }
        Expression matchingPattern = macro { calculator.total(_, _) + _ }
        Expression differentMethodPattern = macro { calculator.average(_, _) + _ }
        Expression differentShapePattern = macro { calculator.total(_) + _ }

        assertThat(ASTMatcher.matches(actual, matchingPattern)).isTrue()
        assertThat(ASTMatcher.matches(actual, differentMethodPattern)).isFalse()
        assertThat(ASTMatcher.matches(actual, differentShapePattern)).isFalse()
    }

    @Test
    void astMatcherFindsNestedMethodCallsAndReportsTheirTreeContexts() {
        BlockStatement block = macro(true) {
            audit('started')
            worker.run()
            audit('finished')
        }
        MethodCallExpression auditCallPattern = macro { audit(_) }

        List<TreeContext> matches = ASTMatcher.find(block, auditCallPattern)

        assertThat(matches).hasSize(2)
        assertThat(matches.collect { TreeContext context -> ((MethodCallExpression) context.node).methodAsString })
                .containsExactly('audit', 'audit')
        assertThat(matches.every { TreeContext context -> context.parent != null }).isTrue()
    }

    @Test
    void astMatcherSupportsPlaceholdersAndTokenConstraints() {
        Expression repeatedOperandPattern = (Expression) ASTMatcher.withConstraints(macro { marker + marker }) {
            placeholder 'marker'
        }
        Expression anyOperatorPattern = (Expression) ASTMatcher.withConstraints(macro { left + right }) {
            anyToken()
        }

        assertThat(ASTMatcher.matches(macro { value + value }, repeatedOperandPattern)).isTrue()
        assertThat(ASTMatcher.matches(macro { value + other }, repeatedOperandPattern)).isFalse()
        assertThat(ASTMatcher.matches(macro { left - right }, anyOperatorPattern)).isTrue()
        assertThat(ASTMatcher.matches(macro { left * right }, anyOperatorPattern)).isTrue()
        assertThat(ASTMatcher.matches(macro { other - right }, anyOperatorPattern)).isFalse()
    }

    @Test
    void directRuntimeMacroExtensionStubsFailFastWhenNotTransformed() {
        assertThatThrownBy { MacroGroovyMethods.macro(new Object(), { 1 }) }
                .isInstanceOf(IllegalStateException)
                .hasMessageContaining('should never be called at runtime')

        assertThatThrownBy { MacroGroovyMethods.macro(new Object(), true, { 1 }) }
                .isInstanceOf(IllegalStateException)
                .hasMessageContaining('should never be called at runtime')
    }

    private static void assertPrintlnStatement(Object statement, String expectedArgument) {
        MethodCallExpression call = methodCallFromExpressionStatement(statement)

        assertThat(call.methodAsString).isEqualTo('println')
        TupleExpression arguments = (TupleExpression) call.arguments
        assertThat(arguments.expressions).hasSize(1)
        assertThat(((ConstantExpression) arguments.expressions[0]).value).isEqualTo(expectedArgument)
    }

    private static MethodCallExpression methodCallFromExpressionStatement(Object statement) {
        assertThat(statement).isInstanceOf(ExpressionStatement)
        ExpressionStatement expressionStatement = (ExpressionStatement) statement
        assertThat(expressionStatement.expression).isInstanceOf(MethodCallExpression)
        return (MethodCallExpression) expressionStatement.expression
    }
}
