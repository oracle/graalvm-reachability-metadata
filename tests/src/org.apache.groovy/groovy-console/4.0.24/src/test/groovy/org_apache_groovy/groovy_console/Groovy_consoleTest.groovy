/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_console

import groovy.console.TextNode
import groovy.console.TextTreeNodeMaker
import groovy.console.ui.AstNodeToScriptAdapter
import groovy.console.ui.CompilePhaseAdapter
import groovy.console.ui.HistoryRecord
import groovy.console.ui.OutputTransforms
import groovy.console.ui.ScriptToTreeNodeAdapter
import groovy.console.ui.SystemOutputInterceptor
import groovy.console.ui.TreeNodeWithProperties
import groovy.console.ui.text.GroovyFilter
import groovy.console.ui.text.MatchingHighlighter
import groovy.console.ui.text.SmartDocumentFilter
import groovy.console.ui.text.TextEditor
import org.codehaus.groovy.control.CompilePhase
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

import javax.swing.ImageIcon
import javax.swing.JTextPane
import javax.swing.SwingUtilities
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.StyleConstants
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import static org.assertj.core.api.Assertions.assertThat

public class Groovy_consoleTest {
    @Test
    void createsPlainTextNodesHistoryRecordsAndCompilePhaseNavigation() {
        TextTreeNodeMaker maker = new TextTreeNodeMaker()
        TextNode root = maker.makeNode('root')
        List<List<?>> propertyTable = [['name', 'demo', 'String', 'demo']]
        TextNode child = maker.makeNodeWithProperties(null, propertyTable)

        child.parent = root
        root.add(child)

        assertThat(root.toString()).isEqualTo('root')
        assertThat(child.toString()).isEqualTo('null')
        assertThat(child.parent).isSameAs(root)
        assertThat(root.children).containsExactly(child)
        assertThat(child.properties).containsExactlyElementsOf(propertyTable)

        TreeNodeWithProperties astNode = new TreeNodeWithProperties('ClassNode - Demo', [
                ['class', 'class org.codehaus.groovy.ast.ClassNode', 'Class', Object],
                ['name', 'Demo', 'String', 'Demo']
        ])
        assertThat(astNode.getPropertyValue('name')).isEqualTo('Demo')
        assertThat(astNode.getPropertyValue('missing')).isNull()
        assertThat(astNode.classNode).isTrue()
        assertThat(astNode.methodNode).isFalse()

        String script = '''import java.time.LocalDate

println 'before'
println LocalDate.now()
println 'after'
'''
        int selectionStart = script.indexOf("println LocalDate")
        int selectionEnd = selectionStart + "println LocalDate.now()".length()
        HistoryRecord selectedRecord = new HistoryRecord(
                allText: script,
                selectionStart: selectionStart,
                selectionEnd: selectionEnd,
                result: 42)

        assertThat(selectedRecord.getTextToRun(true))
                .isEqualTo('import java.time.LocalDate\nprintln LocalDate.now()')
        assertThat(selectedRecord.getTextToRun(false)).isEqualTo(script)
        assertThat(selectedRecord.value).isEqualTo(42)

        IllegalStateException failure = new IllegalStateException('boom')
        HistoryRecord failedRecord = new HistoryRecord(allText: '1 / 0', result: 'ignored', exception: failure)
        assertThat(failedRecord.value).isSameAs(failure)

        assertThat(CompilePhaseAdapter.PARSING.next()).isEqualTo(CompilePhaseAdapter.CONVERSION)
        assertThat(CompilePhaseAdapter.CONVERSION.previous()).isEqualTo(CompilePhaseAdapter.PARSING)
        assertThat(CompilePhaseAdapter.PARSING.phaseId).isEqualTo(CompilePhase.PARSING.phaseNumber)
        assertThat(CompilePhaseAdapter.PARSING.toString()).containsIgnoringCase('parsing')
    }

    @Test
    void buildsTextTreeForGroovySourceAst() {
        try {
            TextTreeNodeMaker maker = new TextTreeNodeMaker()
            ScriptToTreeNodeAdapter adapter = new ScriptToTreeNodeAdapter(
                    new GroovyClassLoader(getClass().classLoader),
                    true,
                    true,
                    true,
                    maker)
            String source = '''import java.util.concurrent.atomic.AtomicInteger

class Greeter {
    String prefix = 'Hello'

    String greet(String name) {
        return "${prefix}, ${name}"
    }
}

def counter = new AtomicInteger(1)
def doubled = [1, 2, 3].collect { value -> value * 2 }
assert doubled.sum() == 12
counter.incrementAndGet()
'''

            TextNode root = adapter.compile(source, CompilePhase.SEMANTIC_ANALYSIS.phaseNumber) as TextNode
            List<TextNode> nodes = flatten(root)
            String renderedTree = nodes.collect { it.toString() }.join('\n')

            assertThat(root.toString()).isEqualTo('root')
            assertThat(renderedTree)
                    .contains('BlockStatement')
                    .contains('Greeter')
                    .contains('Methods')
                    .contains('greet')
                    .contains('Fields')
                    .contains('Properties')
            List<TextNode> nodesWithProperties = nodes.findAll { it.properties }
            assertThat(nodesWithProperties).isNotEmpty()
            boolean namedAstNodeFound = nodesWithProperties.any { TextNode node ->
                node.properties.any { List<?> property ->
                    property[0] == 'name' && property[1].toString().contains('Greeter')
                }
            }
            assertThat(namedAstNodeFound).isTrue()
        } catch (Error e) {
            verifyUnsupportedDynamicCompilation(e)
        }
    }

    @Test
    void decompilesAstToScriptAndReportsCompilationFailures() {
        try {
            AstNodeToScriptAdapter adapter = new AstNodeToScriptAdapter()
            String source = '''package sample

import static java.lang.Math.max

class Calculator<T extends Number> {
    int twice(int value) {
        return max(value, value) * 2
    }
}

assert new Calculator<Integer>().twice(21) == 42
'''

            String decompiled = adapter.compileToScript(
                    source,
                    CompilePhase.CONVERSION.phaseNumber,
                    new GroovyClassLoader(getClass().classLoader),
                    true,
                    true)

            assertThat(decompiled)
                    .contains('package sample')
                    .contains('import static java.lang.Math.max')
                    .contains('Calculator')
                    .contains('twice')
                    .contains('return')
                    .contains('max')

            String failed = adapter.compileToScript('def broken = ', CompilePhase.SEMANTIC_ANALYSIS.phaseNumber)
            assertThat(failed)
                    .contains('Unable to produce AST for this phase due to earlier compilation error')
                    .contains('Fix the above error(s) and then press Refresh')
        } catch (Error e) {
            verifyUnsupportedDynamicCompilation(e)
        }
    }

    @Test
    void highlightsGroovySyntaxAndInstallsAutoIndentAction() {
        DefaultStyledDocument document = new DefaultStyledDocument()
        GroovyFilter filter = new GroovyFilter(document)
        ((AbstractDocument) document).documentFilter = filter

        String source = '''class Demo {
    // comment
    def answer = 42
    String text = 'ok'
}
'''
        document.insertString(0, source, null)

        assertThat(StyleConstants.isBold(attributesAt(document, source.indexOf('class')))).isTrue()
        assertThat(StyleConstants.isItalic(attributesAt(document, source.indexOf('// comment') + 1))).isTrue()
        assertThat(StyleConstants.getForeground(attributesAt(document, source.indexOf('42')))).isNotNull()
        assertThat(StyleConstants.getForeground(attributesAt(document, source.indexOf("'ok'") + 1))).isNotNull()

        document.remove(source.indexOf('42'), 2)
        document.insertString(source.indexOf('42'), '0x2A', null)
        assertThat(document.getText(0, document.length)).contains('0x2A')

        JTextPane pane = new JTextPane(document)
        GroovyFilter.installAutoTabAction(pane)
        assertThat(pane.actionMap.get('GroovyFilter-autoTab')).isNotNull()
        boolean enterKeyBound = pane.inputMap.allKeys().any { it.toString().contains('ENTER') }
        assertThat(enterKeyBound).isTrue()
    }

    @Test
    void highlightsMatchingDelimitersAndClearsStaleHighlights() {
        DefaultStyledDocument document = new DefaultStyledDocument()
        SmartDocumentFilter filter = new SmartDocumentFilter(document)
        ((AbstractDocument) document).documentFilter = filter
        String source = 'def values = [1, (2 + 3)]\n'
        document.insertString(0, source, null)
        JTextPane pane = new JTextPane(document)
        MatchingHighlighter highlighter = new MatchingHighlighter(filter, pane)

        int openParen = source.indexOf('(')
        int closeParen = source.indexOf(')')
        pane.caretPosition = openParen + 1
        highlighter.highlight()
        waitForPendingSwingEvents()

        assertHighlightedDelimiter(document, openParen)
        assertHighlightedDelimiter(document, closeParen)

        pane.caretPosition = source.length()
        highlighter.highlight()
        waitForPendingSwingEvents()

        assertPlainDelimiter(document, openParen)
        assertPlainDelimiter(document, closeParen)
    }

    @Test
    void editsTextWithTextEditorPublicApi() {
        TextEditor editor = new TextEditor(true, true, true)
        editor.text = 'alpha\nbeta'

        editor.caretPosition = 0
        editor.setOvertypeMode(true)
        editor.replaceSelection('Z')
        assertThat(editor.text).isEqualTo('Zlpha\nbeta')

        editor.setOvertypeMode(false)
        editor.caretPosition = editor.text.length()
        editor.replaceSelection('\nnext')
        assertThat(editor.text).endsWith('beta\nnext')

        editor.setUnwrapped(false)
        editor.isTabsAsSpaces(false)
        editor.isMultiLineTabbed(false)

        assertThat(editor.tabsAsSpaces).isFalse()
        assertThat(editor.multiLineTabbed).isFalse()
        assertThat(editor.unwrapped).isFalse()
        assertThat(editor.actionMap.get('TextEditor-tabAction')).isNotNull()
        assertThat(editor.actionMap.get('TextEditor-shiftTabAction')).isNotNull()
        assertThat(editor.getPageFormat(0)).isNotNull()
        assertThat(editor.getPrintable(0)).isSameAs(editor)
    }

    @Test
    void transformsConsoleResultsIntoDisplayableValues() {
        String originalUserHome = System.getProperty('user.home')
        Path tempHome = Files.createTempDirectory('groovy-console-output-transforms')

        try {
            System.setProperty('user.home', tempHome.toString())
            List<Closure> transforms = OutputTransforms.loadOutputTransforms()

            BufferedImage image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB)
            ImageIcon icon = OutputTransforms.transformResult(image, transforms) as ImageIcon
            assertThat(icon.iconWidth).isEqualTo(2)
            assertThat(icon.iconHeight).isEqualTo(3)

            String inspected = OutputTransforms.transformResult([answer: 42], transforms) as String
            assertThat(inspected).contains('answer', '42')
            assertThat(OutputTransforms.transformResult(null, transforms)).isNull()

            List<Closure> customTransforms = [
                    { Object value -> value instanceof Number ? null : 'not-a-number' },
                    { Object value -> value instanceof Number ? value * 2 : null }
            ]
            assertThat(OutputTransforms.transformResult(21, customTransforms)).isEqualTo(42)
        } finally {
            if (originalUserHome == null) {
                System.clearProperty('user.home')
            } else {
                System.setProperty('user.home', originalUserHome)
            }
            Files.deleteIfExists(tempHome)
        }
    }

    @Test
    void interceptsSystemOutputAndRestoresOriginalPrintStreams() {
        PrintStream originalOut = System.out
        PrintStream originalErr = System.err
        List<List<Object>> capturedOut = []
        List<List<Object>> capturedErr = []
        SystemOutputInterceptor outInterceptor = new SystemOutputInterceptor({ Integer consoleId, String text ->
            capturedOut << [consoleId, text]
            false
        })
        SystemOutputInterceptor errInterceptor = new SystemOutputInterceptor({ Integer consoleId, String text ->
            capturedErr << [consoleId, text]
            false
        }, false)

        try {
            outInterceptor.setConsoleId(7)
            outInterceptor.start()
            System.out.print('hello')
            System.out.write('!'.charAt(0) as int)
            System.out.flush()
        } finally {
            outInterceptor.stop()
            outInterceptor.removeConsoleId()
        }

        try {
            errInterceptor.setConsoleId(9)
            errInterceptor.start()
            System.err.print('problem')
            System.err.flush()
        } finally {
            errInterceptor.stop()
            errInterceptor.removeConsoleId()
        }

        assertThat(System.out).isSameAs(originalOut)
        assertThat(System.err).isSameAs(originalErr)
        assertThat(capturedOut).contains([7, 'hello'], [7, '!'])
        assertThat(capturedErr).contains([9, 'problem'])
    }

    private static List<TextNode> flatten(TextNode root) {
        List<TextNode> result = []
        Deque<TextNode> remaining = new ArrayDeque<>()
        remaining.add(root)
        while (!remaining.isEmpty()) {
            TextNode current = remaining.removeFirst()
            result << current
            current.children.findAll { it instanceof TextNode }.reverseEach { TextNode child ->
                remaining.addFirst(child)
            }
        }
        result
    }

    private static AttributeSet attributesAt(DefaultStyledDocument document, int offset) {
        document.getCharacterElement(offset).attributes
    }

    private static void assertHighlightedDelimiter(DefaultStyledDocument document, int offset) {
        AttributeSet attributes = attributesAt(document, offset)
        assertThat(StyleConstants.isBold(attributes)).isTrue()
        assertThat(StyleConstants.getForeground(attributes)).isEqualTo(Color.YELLOW.darker())
    }

    private static void assertPlainDelimiter(DefaultStyledDocument document, int offset) {
        AttributeSet attributes = attributesAt(document, offset)
        assertThat(StyleConstants.isBold(attributes)).isFalse()
        assertThat(StyleConstants.getForeground(attributes)).isNotEqualTo(Color.YELLOW.darker())
    }

    private static void waitForPendingSwingEvents() {
        if (SwingUtilities.isEventDispatchThread()) {
            return
        }
        CountDownLatch latch = new CountDownLatch(1)
        SwingUtilities.invokeLater({ latch.countDown() } as Runnable)
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
    }

    private static void verifyUnsupportedDynamicCompilation(Error e) {
        if (!NativeImageSupport.isUnsupportedFeatureError(e)) {
            throw e
        }
    }
}
