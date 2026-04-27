/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package werken_xpath.werken_xpath;

import com.werken.xpath.ContextSupport;
import com.werken.xpath.DefaultVariableContext;
import com.werken.xpath.ElementNamespaceContext;
import com.werken.xpath.XPath;
import org.jdom.Attribute;
import org.jdom.Comment;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.ProcessingInstruction;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class Werken_xpathTest {
    @Test
    void selectsElementsWithAbsoluteDescendantParentAndSelfPaths() {
        Document document = bookDocument();

        List<?> rootSelection = select("/", document);
        assertThat(rootSelection).hasSize(1);
        assertThat(rootSelection.get(0)).isSameAs(document);
        assertThat(elementIds(select("/book", document))).containsExactly("book-1");
        assertThat(elementIds(select("//title", document))).containsExactly(
                "book-1-title-1",
                "chapter-1-title-1",
                "chapter-2-title-1",
                "chapter-3-title-1",
                "chapter-4-title-1",
                "chapter-5-title-1",
                "chapter-6-title-1");
        assertThat(elementIds(select("/book/chapter/../chapter/.", document))).containsExactly(
                "chapter-1", "chapter-2", "chapter-3", "chapter-4", "chapter-5", "chapter-6");
    }

    @Test
    void filtersByPositionAttributesBooleanExpressionsAndBuiltInFunctions() {
        Document document = bookDocument();

        assertThat(elementIds(select("//chapter[2]/title", document))).containsExactly("chapter-2-title-1");
        assertThat(elementIds(select("//chapter[last()]", document))).containsExactly("chapter-6");
        assertThat(elementIds(select("/book/chapter[@author='bob']", document)))
                .containsExactly("chapter-1", "chapter-3");
        assertThat(elementIds(select("/book/chapter[@author='bob' or @author='rebecca']", document)))
                .containsExactly("chapter-1", "chapter-2", "chapter-3");
        assertThat(elementIds(select("/book/chapter[not(@author)]", document))).containsExactly("chapter-6");
        assertThat(elementIds(select("//chapter[contains(@id, 'chapter-3') or starts-with(@author, 'reb')]", document)))
                .containsExactly("chapter-2", "chapter-3");
    }

    @Test
    void selectsElementsWithWildcardNameTests() {
        Document document = bookDocument();

        assertThat(elementIds(select("/book/*", document))).containsExactly(
                "book-1-title-1",
                "chapter-1",
                "chapter-2",
                "chapter-3",
                "chapter-4",
                "chapter-5",
                "chapter-6");
        assertThat(elementIds(select("/book/chapter[@author='bob']/*", document)))
                .containsExactly("chapter-1-title-1", "chapter-3-title-1");
    }

    @Test
    void returnsAttributesCommentsAndProcessingInstructions() {
        Document document = bookDocument();

        assertThat(attributeValues(select("/book/chapter[@author='bob']/@id", document)))
                .containsExactly("chapter-1", "chapter-3");
        assertThat(elementIds(select("//chapter[@id='chapter-3']/title", document)))
                .containsExactly("chapter-3-title-1");
        List<?> comments = select("//chapter[@id='chapter-4']/comment()", document);
        assertThat(comments).hasSize(1);
        Comment comment = (Comment) comments.get(0);
        assertThat(comment.getText()).isEqualTo("reviewed");

        List<?> instructions = select("//processing-instruction(display)", document);
        assertThat(instructions).hasSize(1);
        ProcessingInstruction instruction = (ProcessingInstruction) instructions.get(0);
        assertThat(instruction.getTarget()).isEqualTo("display");
        assertThat(instruction.getData()).isEqualTo("mode='compact'");
    }

    @Test
    void evaluatesVariablesThroughContextSupport() {
        Document document = bookDocument();
        DefaultVariableContext variables = new DefaultVariableContext();
        variables.setVariableValue("wantedAuthor", "bob");
        variables.setVariableValue("wantedId", "chapter-5");

        ContextSupport support = new ContextSupport();
        support.setVariableContext(variables);

        assertThat(elementIds(select(support, "/book/chapter[@author=$wantedAuthor]", document)))
                .containsExactly("chapter-1", "chapter-3");
        assertThat(elementIds(select(support, "/book/chapter[@id=$wantedId]/title", document)))
                .containsExactly("chapter-5-title-1");
    }

    @Test
    void selectsTextNodesAndUsesStringLengthInPredicates() {
        Element library = new Element("library");
        library.addContent(new Element("book")
                .setAttribute("id", "short-title")
                .addContent(new Element("title").addContent("XPath")));
        library.addContent(new Element("book")
                .setAttribute("id", "long-title")
                .addContent(new Element("title").addContent("Native Image")));
        Document document = new Document(library);

        assertThat(stringValues(select("/library/book/title/text()", document))).containsExactly("XPath", "Native Image");
        assertThat(elementIds(select("/library/book[string-length(title/text()) > 5]", document)))
                .containsExactly("long-title");
    }

    @Test
    void resolvesNamespacePrefixesFromElementNamespaceContext() {
        Namespace libraryNamespace = Namespace.getNamespace("lib", "urn:library");
        Element catalog = new Element("catalog");
        catalog.addNamespaceDeclaration(libraryNamespace);
        Element namespacedBook = new Element("book", libraryNamespace)
                .setAttribute("id", "namespaced-book");
        namespacedBook.addContent(new Element("title", libraryNamespace).setAttribute("id", "namespaced-title")
                .addContent("Namespaced XPath"));
        catalog.addContent(namespacedBook);
        Document document = new Document(catalog);

        ContextSupport support = new ContextSupport();
        support.setNamespaceContext(new ElementNamespaceContext(catalog));

        assertThat(elementIds(select(support, "/catalog/lib:book", document))).containsExactly("namespaced-book");
        assertThat(elementIds(select(support, "/catalog/lib:book/lib:title", document)))
                .containsExactly("namespaced-title");
        assertThat(attributeValues(select(support, "/catalog/lib:book/@id", document)))
                .containsExactly("namespaced-book");
    }

    @Test
    void appliesRelativeExpressionsToElementsAndNodeSets() {
        Document document = bookDocument();
        Element book = document.getRootElement();
        List<?> chapters = select("/book/chapter[@author='bob']", document);

        assertThat(elementIds(new XPath("chapter/title").applyTo(book)))
                .containsExactly(
                        "chapter-1-title-1",
                        "chapter-2-title-1",
                        "chapter-3-title-1",
                        "chapter-4-title-1",
                        "chapter-5-title-1",
                        "chapter-6-title-1");
        assertThat(elementIds(new XPath("title").applyTo(chapters)))
                .containsExactly("chapter-1-title-1", "chapter-3-title-1");
    }

    @Test
    void exposesOriginalExpressionTextAndReadableRepresentation() {
        XPath xpath = new XPath("//chapter[@author='bob']/title");

        assertThat(xpath.getString()).isEqualTo("//chapter[@author='bob']/title");
        assertThat(xpath.toString()).contains("//chapter[@author='bob']/title");
    }

    private static List<?> select(String xpath, Document document) {
        return new XPath(xpath).applyTo(document);
    }

    private static List<?> select(ContextSupport support, String xpath, Document document) {
        return new XPath(xpath).applyTo(support, document);
    }

    private static List<String> elementIds(List<?> nodes) {
        return nodes.stream()
                .map(Element.class::cast)
                .map(element -> element.getAttributeValue("id"))
                .collect(Collectors.toList());
    }

    private static List<String> attributeValues(List<?> nodes) {
        return nodes.stream()
                .map(Attribute.class::cast)
                .map(Attribute::getValue)
                .collect(Collectors.toList());
    }

    private static List<String> stringValues(List<?> nodes) {
        return nodes.stream()
                .map(String.class::cast)
                .collect(Collectors.toList());
    }

    private static Document bookDocument() {
        Element book = new Element("book").setAttribute("id", "book-1");
        book.addContent(new ProcessingInstruction("display", "mode='compact'"));
        book.addContent(new Element("title").setAttribute("id", "book-1-title-1")
                .addContent("How to Test Some XPath Implementation"));
        chapters().forEach(book::addContent);
        return new Document(book);
    }

    private static List<Element> chapters() {
        Element chapterFour = chapter("chapter-4", "james", "This is chapter Four");
        chapterFour.addContent(new Comment("reviewed"));
        return Arrays.asList(
                chapter("chapter-1", "bob", "This is chapter One"),
                chapter("chapter-2", "rebecca", "This is chapter Two"),
                chapter("chapter-3", "bob", "This is chapter Three"),
                chapterFour,
                chapter("chapter-5", "fred", "This is chapter Five"),
                chapter("chapter-6", null, "This is chapter Six"));
    }

    private static Element chapter(String id, String author, String title) {
        Element chapter = new Element("chapter").setAttribute("id", id);
        if (author != null) {
            chapter.setAttribute("author", author);
        }
        chapter.addContent(new Element("title").setAttribute("id", id + "-title-1").addContent(title));
        return chapter;
    }
}
