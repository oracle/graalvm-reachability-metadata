/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_macro

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

import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX

public class Groovy_macroTest {
    @Test
    void macroCreatesTypedAstAndAppliesExpressionSubstitutions() {
        VariableExpression replacementArgument = varX('replacementArgument')

        ReturnStatement result = macro {
            return new MissingType($v { replacementArgument }, 'fixed')
        }

        assert result instanceof ReturnStatement
        assert result.expression instanceof ConstructorCallExpression

        ConstructorCallExpression constructorCall = (ConstructorCallExpression) result.expression
        assert constructorCall.type.name == 'MissingType'

        TupleExpression arguments = (TupleExpression) constructorCall.arguments
        assert arguments.expressions.size() == 2
        assert arguments.expressions[0].is(replacementArgument)
        assert ((ConstantExpression) arguments.expressions[1]).value == 'fixed'
    }

    @Test
    void macroCanKeepFullClosureBlockInsteadOfOnlyLastExpression() {
        BlockStatement block = macro(true) {
            println 'alpha'
            println 'beta'
        }

        assert block.statements.size() == 2
        assertPrintlnStatement(block.statements[0], 'alpha')
        assertPrintlnStatement(block.statements[1], 'beta')
    }

    @Test
    void macroSupportsExplicitCompilePhase() {
        BlockStatement block = macro(CompilePhase.CLASS_GENERATION, true) {
            println 'before'
            println 'after'
        }

        assert block.statements.size() == 2
        assertPrintlnStatement(block.statements[0], 'before')
        assert block.statements[1] instanceof ReturnStatement

        ReturnStatement returnStatement = (ReturnStatement) block.statements[1]
        assert returnStatement.expression instanceof MethodCallExpression
        MethodCallExpression call = (MethodCallExpression) returnStatement.expression
        assert call.methodAsString == 'println'
    }

    @Test
    void runtimeMacroBuilderBuildsExpressionsBlocksAndClassesFromSource() {
        BinaryExpression expression = MacroBuilder.INSTANCE.macro(
                '{ left + $v { ignoredAtParseTime } }',
                [{ -> constX(7) }],
                BinaryExpression
        )

        assert expression.leftExpression instanceof VariableExpression
        assert ((VariableExpression) expression.leftExpression).name == 'left'
        assert expression.rightExpression instanceof ConstantExpression
        assert ((ConstantExpression) expression.rightExpression).value == 7

        BlockStatement block = MacroBuilder.INSTANCE.macro(
                CompilePhase.CONVERSION,
                true,
                '{ firstCall()\nsecondCall() }',
                [],
                BlockStatement
        )
        assert block.statements.size() == 2
        assert methodCallFromExpressionStatement(block.statements[0]).methodAsString == 'firstCall'
        assert methodCallFromExpressionStatement(block.statements[1]).methodAsString == 'secondCall'

        ClassNode generatedClass = MacroBuilder.INSTANCE.macro(
                'class GeneratedMacroType { String name\n int nameSize() { name.size() } }',
                [],
                ClassNode
        )
        assert generatedClass.nameWithoutPackage == 'GeneratedMacroType'
        assert generatedClass.getField('name') != null
        assert generatedClass.getMethods('nameSize').size() == 1
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

        assert classNode instanceof ClassNode
        assert classNode.nameWithoutPackage == 'Book'
        assert classNode.getField('title') != null
        assert classNode.getMethods('titleLength').size() == 1
    }

    @Test
    void astMatcherMatchesWildcardsAndRejectsDifferentShapes() {
        Expression actual = macro { calculator.total(items[0], 42) + 5 }
        Expression matchingPattern = macro { calculator.total(_, _) + _ }
        Expression differentMethodPattern = macro { calculator.average(_, _) + _ }
        Expression differentShapePattern = macro { calculator.total(_) + _ }

        assert ASTMatcher.matches(actual, matchingPattern)
        assert !ASTMatcher.matches(actual, differentMethodPattern)
        assert !ASTMatcher.matches(actual, differentShapePattern)
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

        assert matches.size() == 2
        assert matches.collect { TreeContext context -> ((MethodCallExpression) context.node).methodAsString } == ['audit', 'audit']
        assert matches.every { TreeContext context -> context.parent != null }
    }

    @Test
    void astMatcherSupportsPlaceholdersAndTokenConstraints() {
        Expression repeatedOperandPattern = (Expression) ASTMatcher.withConstraints(macro { marker + marker }) {
            placeholder 'marker'
        }
        Expression anyOperatorPattern = (Expression) ASTMatcher.withConstraints(macro { left + right }) {
            anyToken()
        }

        assert ASTMatcher.matches(macro { value + value }, repeatedOperandPattern)
        assert !ASTMatcher.matches(macro { value + other }, repeatedOperandPattern)
        assert ASTMatcher.matches(macro { left - right }, anyOperatorPattern)
        assert ASTMatcher.matches(macro { left * right }, anyOperatorPattern)
        assert !ASTMatcher.matches(macro { other - right }, anyOperatorPattern)
    }

    @Test
    void astMatcherSupportsContextPredicates() {
        Expression rightTenPattern = (Expression) ASTMatcher.withConstraints(macro { _ + _ }) {
            eventually { TreeContext context ->
                BinaryExpression binary = (BinaryExpression) context.node
                binary.rightExpression instanceof ConstantExpression &&
                        ((ConstantExpression) binary.rightExpression).value == 10
            }
        }

        assert ASTMatcher.matches(macro { total + 10 }, rightTenPattern)
        assert !ASTMatcher.matches(macro { total + 11 }, rightTenPattern)
        assert !ASTMatcher.matches(macro { total - 10 }, rightTenPattern)
    }

    @Test
    void treeContextStoresUserDataAndEvaluatesPredicatesAgainstItsNode() {
        BlockStatement block = macro(true) {
            record('created')
            ignore()
        }
        MethodCallExpression recordCallPattern = macro { record(_) }

        TreeContext context = ASTMatcher.find(block, recordCallPattern).first()
        TreeContext parent = context.parent
        parent.putUserdata('scope', 'statement')
        context.putUserdata('call', 'record')
        Expression replacement = constX('replacement')
        context.setReplacement(replacement)

        assert context.getUserdata('call') == ['record']
        assert context.getUserdata('scope') == ['statement']
        assert context.getUserdata('missing', false) == null
        assert context.matches { methodAsString == 'record' }
        assert !context.matches { methodAsString == 'ignore' }
        assert context.replacement.is(replacement)
        assert context.toString().contains('MethodCallExpression')
    }

    @Test
    void directRuntimeMacroExtensionStubsFailFastWhenNotTransformed() {
        try {
            MacroGroovyMethods.macro(new Object(), { 1 })
            assert false: 'Expected IllegalStateException'
        } catch (IllegalStateException exception) {
            assert exception.message.contains('should never be called at runtime')
        }

        try {
            MacroGroovyMethods.macro(new Object(), true, { 1 })
            assert false: 'Expected IllegalStateException'
        } catch (IllegalStateException exception) {
            assert exception.message.contains('should never be called at runtime')
        }
    }

    private static void assertPrintlnStatement(Object statement, String expectedArgument) {
        MethodCallExpression call = methodCallFromExpressionStatement(statement)

        assert call.methodAsString == 'println'
        TupleExpression arguments = (TupleExpression) call.arguments
        assert arguments.expressions.size() == 1
        assert ((ConstantExpression) arguments.expressions[0]).value == expectedArgument
    }

    private static MethodCallExpression methodCallFromExpressionStatement(Object statement) {
        assert statement instanceof ExpressionStatement
        ExpressionStatement expressionStatement = (ExpressionStatement) statement
        assert expressionStatement.expression instanceof MethodCallExpression
        return (MethodCallExpression) expressionStatement.expression
    }
}
