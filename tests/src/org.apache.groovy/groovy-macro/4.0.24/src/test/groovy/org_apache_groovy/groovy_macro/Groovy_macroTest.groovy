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
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.macro.matcher.ASTMatcher
import org.codehaus.groovy.macro.matcher.TreeContext
import org.codehaus.groovy.macro.runtime.MacroBuilder
import org.codehaus.groovy.macro.runtime.MacroContext
import org.codehaus.groovy.macro.transform.MacroClass
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

public class Groovy_macroTest {
    @Test
    void macroBuilderCreatesExpressionBlocksClassesAndSubstitutesPlaceholders() {
        Expression expression = MacroBuilder.INSTANCE.macro('{ 1 + 2 }', [], Expression)

        assertThat(expression).isInstanceOf(BinaryExpression)
        BinaryExpression binary = expression as BinaryExpression
        assertThat((binary.leftExpression as ConstantExpression).value).isEqualTo(1)
        assertThat(binary.operation.text).isEqualTo('+')
        assertThat((binary.rightExpression as ConstantExpression).value).isEqualTo(2)

        BlockStatement block = MacroBuilder.INSTANCE.macro(true, '{ 1 + 2 }', [], BlockStatement)

        assertThat(block.statements).hasSize(1)
        assertThat(block.statements[0]).isInstanceOf(ExpressionStatement)

        ClassNode classNode = MacroBuilder.INSTANCE.macro('''class MacroGeneratedGreeter {
            String greet(String name) {
                "Hello, $name"
            }
        }''', [], ClassNode)

        assertThat(classNode.nameWithoutPackage).isEqualTo('MacroGeneratedGreeter')
        assertThat(classNode.methods*.name).contains('greet')

        Closure<Expression> substitutedValue = { -> new ConstantExpression(7) }
        Expression substituted = MacroBuilder.INSTANCE.macro(
                CompilePhase.CONVERSION,
                '{ $v() + 5 }',
                [substitutedValue],
                Expression)

        assertThat(substituted).isInstanceOf(BinaryExpression)
        BinaryExpression substitutedBinary = substituted as BinaryExpression
        assertThat((substitutedBinary.leftExpression as ConstantExpression).value).isEqualTo(7)
        assertThat((substitutedBinary.rightExpression as ConstantExpression).value).isEqualTo(5)
    }

    @Test
    void compileTimeMacroExtensionProducesAstNodeInsteadOfRuntimeCall() {
        Expression expression = macro { 'groovy'.toUpperCase() }

        assertThat(expression).isInstanceOf(MethodCallExpression)
        MethodCallExpression call = expression as MethodCallExpression
        assertThat(call.methodAsString).isEqualTo('toUpperCase')
        assertThat((call.objectExpression as ConstantExpression).value).isEqualTo('groovy')
    }

    @Test
    void astMatcherMatchesWildcardsPlaceholdersAndTokenConstraints() {
        Expression candidate = MacroBuilder.INSTANCE.macro('{ service.user.name }', [], Expression)
        Expression identical = MacroBuilder.INSTANCE.macro('{ service.user.name }', [], Expression)
        Expression wildcard = MacroBuilder.INSTANCE.macro('{ _.user.name }', [], Expression)
        Expression different = MacroBuilder.INSTANCE.macro('{ service.account.name }', [], Expression)

        assertThat(ASTMatcher.matches(candidate, identical)).isTrue()
        assertThat(ASTMatcher.matches(candidate, wildcard)).isTrue()
        assertThat(ASTMatcher.matches(candidate, different)).isFalse()

        Expression repeatedPlaceholder = ASTMatcher.withConstraints(
                MacroBuilder.INSTANCE.macro('{ value + value }', [], Expression),
                { placeholder 'value' }) as Expression
        Expression sameOperands = MacroBuilder.INSTANCE.macro('{ total + total }', [], Expression)
        Expression differentOperands = MacroBuilder.INSTANCE.macro('{ total + count }', [], Expression)

        assertThat(ASTMatcher.matches(sameOperands, repeatedPlaceholder)).isTrue()
        assertThat(ASTMatcher.matches(differentOperands, repeatedPlaceholder)).isFalse()

        Expression anyOperatorPattern = ASTMatcher.withConstraints(
                MacroBuilder.INSTANCE.macro('{ left + right }', [], Expression),
                {
                    placeholder 'left', 'right'
                    anyToken()
                }) as Expression
        Expression subtraction = MacroBuilder.INSTANCE.macro('{ first - second }', [], Expression)

        assertThat(ASTMatcher.matches(subtraction, anyOperatorPattern)).isTrue()
    }

    @Test
    void astMatcherFindsNestedMatchesAndTreeContextCarriesTraversalState() {
        BlockStatement block = MacroBuilder.INSTANCE.macro(true, '''{
            int total = 0
            for (item in [1, 2, 3]) {
                total += item
            }
            total
        }''', [], BlockStatement)
        Expression pattern = MacroBuilder.INSTANCE.macro('{ total += _ }', [], Expression)

        List<TreeContext> matches = ASTMatcher.find(block, pattern)

        assertThat(matches).hasSize(1)
        TreeContext context = matches[0]
        assertThat(context.node).isInstanceOf(BinaryExpression)
        assertThat(context.matches { operation.text == '+=' }).isTrue()
        assertThat(context.toString()).contains('BinaryExpression')

        context.putUserdata('role', 'accumulator')
        assertThat(context.getUserdata('role')).containsExactly('accumulator')

        ConstantExpression replacement = new ConstantExpression(42)
        context.replacement = replacement
        assertThat(context.replacement).isSameAs(replacement)

        TreeContext child = context.fork(new VariableExpression('item'))
        assertThat(child.parent).isSameAs(context)
        assertThat(context.siblings).contains(child)
        assertThat(child.getUserdata('role')).containsExactly('accumulator')
    }

    @Test
    void macroClassAnonymousBodyProducesClassNode() {
        ClassNode classNode = new MacroClass() {
            class MacroClassGeneratedModel {
                String name

                String describe() {
                    "model:$name"
                }
            }
        }

        assertThat(classNode.nameWithoutPackage).isEqualTo('MacroClassGeneratedModel')
        assertThat(classNode.getField('name').type.nameWithoutPackage).isEqualTo('String')
        assertThat(classNode.getDeclaredMethods('describe')).hasSize(1)
        assertThat(classNode.getDeclaredMethods('describe')[0].returnType.nameWithoutPackage).isEqualTo('String')
    }

    @Test
    void macroContextExposesCompilationSourceAndCallObjects() {
        MethodCallExpression call = MacroBuilder.INSTANCE.macro('{ service.run(1) }', [], MethodCallExpression)
        MacroContext context = new MacroContext(null, null, call)

        assertThat(context.call).isSameAs(call)
        assertThat(context.sourceUnit).isNull()
        assertThat(context.compilationUnit).isNull()
    }

    @Test
    void macroBuilderReturnsRequestedAstNodeShapesForControlFlow() {
        ASTNode statement = MacroBuilder.INSTANCE.macro('''{
            if (enabled) {
                result = 1
            } else {
                result = 2
            }
        }''', [], ASTNode)

        assertThat(statement.class.simpleName).isEqualTo('IfStatement')

        BlockStatement multiStatementBlock = MacroBuilder.INSTANCE.macro('''{
            def values = [1, 2, 3]
            values.collect { it * 2 }
        }''', [], BlockStatement)

        assertThat(multiStatementBlock.statements).hasSize(2)
    }
}
