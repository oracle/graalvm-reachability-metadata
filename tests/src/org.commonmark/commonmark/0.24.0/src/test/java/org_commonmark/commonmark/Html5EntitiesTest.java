/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_commonmark.commonmark;

import org.commonmark.node.Document;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.text.TextContentRenderer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Html5EntitiesTest {

    private final Parser parser = Parser.builder().build();

    @Test
    void parsesNamedCharacterReferencesInParagraphText() {
        Node document = parser.parse("Caf&eacute; &copy; &AElig;lfred &amp; friends");

        assertThat(document).isInstanceOf(Document.class);
        Node paragraph = document.getFirstChild();
        assertThat(paragraph).isInstanceOf(Paragraph.class);
        assertThat(literalText(paragraph)).isEqualTo("Caf\u00e9 \u00a9 \u00c6lfred & friends");
    }

    @Test
    void rendersNamedCharacterReferencesAsHtml() {
        HtmlRenderer renderer = HtmlRenderer.builder().build();

        String html = renderer.render(parser.parse("&nbsp;&frac34; of &quot;&hearts;&quot; &lt;3"));

        assertThat(html).isEqualTo("<p>\u00a0\u00be of &quot;\u2665&quot; &lt;3</p>\n");
    }

    @Test
    void resolvesNamedCharacterReferencesInLinkDestinationAndTitle() {
        Node document = parser.parse("[home](/f&ouml;&ouml; \"f&ouml;&ouml; &mdash; bar\")");

        Node node = document.getFirstChild().getFirstChild();
        assertThat(node).isInstanceOf(Link.class);
        Link link = (Link) node;
        assertThat(link.getDestination()).isEqualTo("/f\u00f6\u00f6");
        assertThat(link.getTitle()).isEqualTo("f\u00f6\u00f6 \u2014 bar");

        String html = HtmlRenderer.builder().percentEncodeUrls(true).build().render(document);
        assertThat(html).isEqualTo("<p><a href=\"/f%C3%B6%C3%B6\" title=\"f\u00f6\u00f6 \u2014 bar\">home</a></p>\n");
    }

    @Test
    void resolvesNamedCharacterReferencesInLinkReferenceDefinitions() {
        String markdown = "[docs][ref]\n\n[ref]: /caf&eacute; \"&laquo;Docs&raquo;\"\n";

        String html = HtmlRenderer.builder().build().render(parser.parse(markdown));

        assertThat(html).isEqualTo("<p><a href=\"/caf\u00e9\" title=\"\u00abDocs\u00bb\">docs</a></p>\n");
    }

    @Test
    void resolvesNamedCharacterReferencesInFencedCodeInfoString() {
        Node document = parser.parse("``` f&ouml;&ouml;\nprint(1)\n```\n");

        Node node = document.getFirstChild();
        assertThat(node).isInstanceOf(FencedCodeBlock.class);
        FencedCodeBlock codeBlock = (FencedCodeBlock) node;
        assertThat(codeBlock.getInfo()).isEqualTo("f\u00f6\u00f6");
        assertThat(codeBlock.getLiteral()).isEqualTo("print(1)\n");
    }

    @Test
    void keepsUnknownNamedReferencesAndDecodesNumericReferences() {
        String text = TextContentRenderer.builder().build()
                .render(parser.parse("&MadeUpEntity; &#35; &#x22; &#0; &ngE;"));

        assertThat(text).isEqualTo("&MadeUpEntity; # \" \ufffd \u2267\u0338");
    }

    private static String literalText(Node parent) {
        StringBuilder builder = new StringBuilder();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            assertThat(child).isInstanceOf(Text.class);
            builder.append(((Text) child).getLiteral());
        }
        return builder.toString();
    }
}
