/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_xml;

import groovy.xml.MarkupBuilder;
import groovy.xml.MarkupBuilderHelper;
import groovy.xml.NamespaceBuilder;
import groovy.xml.NamespaceBuilderSupport;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class MarkupBuilderTest {
    @Test
    void buildsNestedMarkupWithEscapingAndHelpers() {
        StringWriter writer = new StringWriter();
        MarkupBuilder builder = new MarkupBuilder(writer);
        MarkupBuilderHelper mkp = builder.getMkp();

        builder.invokeMethod("catalog", new Object[] {Map.of("region", "EU"), new BuilderBody(this, () -> {
            mkp.comment("generated catalog");
            builder.invokeMethod("book", new Object[] {Map.of("id", "b1"), new BuilderBody(this, () -> {
                builder.invokeMethod("title", new Object[] {"Groovy & <XML>"});
                builder.invokeMethod("note", new Object[] {new BuilderBody(this, () -> {
                    mkp.yield("Fast & ");
                    mkp.yieldUnescaped("<b>safe</b>");
                })});
                builder.invokeMethod("empty", new Object[0]);
            })});
        })});

        assertThat(writer.toString()).isEqualToNormalizingNewlines("""
                <catalog region='EU'><!-- generated catalog -->
                  <book id='b1'>
                    <title>Groovy &amp; &lt;XML&gt;</title>
                    <note>Fast &amp; <b>safe</b></note>
                    <empty />
                  </book>
                </catalog>""");
    }

    @Test
    void appliesQuotingAndAttributeOptions() {
        StringWriter writer = new StringWriter();
        MarkupBuilder builder = new MarkupBuilder(writer);
        builder.setDoubleQuotes(true);
        builder.setOmitNullAttributes(true);
        builder.setOmitEmptyAttributes(true);
        builder.setExpandEmptyElements(true);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", "a\"b");
        attributes.put("missing", null);
        attributes.put("blank", "");

        builder.invokeMethod("entry", new Object[] {attributes});

        assertThat(builder.getDoubleQuotes()).isTrue();
        assertThat(builder.isOmitNullAttributes()).isTrue();
        assertThat(builder.isOmitEmptyAttributes()).isTrue();
        assertThat(builder.isExpandEmptyElements()).isTrue();
        assertThat(writer.toString()).isEqualTo("<entry name=\"a&quot;b\"></entry>");
    }

    @Test
    void writesDeclarationsAndProcessingInstructions() {
        StringWriter writer = new StringWriter();
        MarkupBuilder builder = new MarkupBuilder(writer);
        MarkupBuilderHelper mkp = builder.getMkp();

        mkp.xmlDeclaration(Map.of("version", "1.0"));
        mkp.pi(Map.of("xml-stylesheet", Map.of("href", "catalog.xsl")));
        builder.invokeMethod("catalog", new Object[] {"empty"});

        assertThat(writer.toString()).isEqualToNormalizingNewlines("""
                <?xml version='1.0'?>
                <?xml-stylesheet href='catalog.xsl'?>
                <catalog>empty</catalog>""");
    }

    @Test
    void appliesAdditionalCharacterFilters() {
        StringWriter writer = new StringWriter();
        MarkupBuilder builder = new MarkupBuilder(writer);
        builder.setEscapeAttributes(false);
        builder.setAdditionalFilters(List.of(ch -> ch == '\u00e9' ? Optional.of("&#233;") : Optional.empty()));

        builder.invokeMethod("word", new Object[] {Map.of("raw", "<kept>"), "caf\u00e9"});

        assertThat(builder.isEscapeAttributes()).isFalse();
        assertThat(writer.toString()).isEqualTo("<word raw='<kept>'>caf&#233;</word>");
    }

    @Test
    void prefixesElementsThroughNamespaceBuilder() {
        StringWriter writer = new StringWriter();
        MarkupBuilder markup = new MarkupBuilder(writer);
        NamespaceBuilderSupport books = NamespaceBuilder.newInstance(markup, "urn:books", "bk");

        books.invokeMethod("catalog", new Object[] {new LinkedHashMap<>(Map.of("region", "EU")), new BuilderBody(this, () -> {
            books.invokeMethod("book", new Object[] {new LinkedHashMap<>(Map.of("id", "b1")), "Groovy"});
        })});

        assertThat(writer.toString()).isEqualToNormalizingNewlines("""
                <bk:catalog region='EU'>
                  <bk:book id='b1'>Groovy</bk:book>
                </bk:catalog>""");
    }
}
