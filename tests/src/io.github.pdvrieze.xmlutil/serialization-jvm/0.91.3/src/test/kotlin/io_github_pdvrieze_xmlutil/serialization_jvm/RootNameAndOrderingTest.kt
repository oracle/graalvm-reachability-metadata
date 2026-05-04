/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_pdvrieze_xmlutil.serialization_jvm

import kotlinx.serialization.builtins.serializer
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.XML
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.xml.namespace.QName

public class RootNameAndOrderingTest {
    @Test
    fun explicitRootQNameControlsPrimitiveElementNameAndNamespace(): Unit {
        val xml = XML {
            xmlDeclMode = XmlDeclMode.None
            repairNamespaces = true
        }
        val serializer = String.serializer()
        val rootName = QName(ROOT_NAMESPACE, "message", "msg")
        val value = "Hello <native> & XML"

        val encoded: String = xml.encodeToString(serializer, value, rootName)

        assertThat(encoded)
            .contains("<msg:message")
            .contains("xmlns:msg=\"$ROOT_NAMESPACE\"")
            .contains("Hello &lt;native&gt; &amp; XML")
            .contains("</msg:message>")
        assertThat(xml.decodeFromString(serializer, encoded, rootName)).isEqualTo(value)
    }

    @Test
    fun configuredXmlDeclarationAndGenericParserRoundTripPrimitiveRoot(): Unit {
        val xml = XML {
            xmlDeclMode = XmlDeclMode.Minimal
            defaultToGenericParser = true
            indentString = "  "
        }
        val serializer = Int.serializer()
        val rootName = QName("", "buildNumber")
        val value = 913

        val encoded: String = xml.encodeToString(serializer, value, rootName)

        assertThat(encoded).startsWith("<?xml version=")
        assertThat(encoded)
            .contains("<buildNumber>")
            .contains("913")
            .contains("</buildNumber>")
        assertThat(xml.decodeFromString(serializer, encoded, rootName)).isEqualTo(value)
    }

    private companion object {
        private const val ROOT_NAMESPACE: String = "urn:test:explicit-root"
    }
}
