/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_ws_standalone_xml_2_13

import java.io.ByteArrayInputStream
import java.io.StringReader
import java.net.URI
import java.util.Collections
import java.util.{List => JList}
import java.util.{Map => JMap}
import java.util.Optional

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

import scala.xml.Elem
import scala.xml.NodeBuffer

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import play.api.libs.ws.BodyWritable
import play.api.libs.ws.InMemoryBody
import play.api.libs.ws.StandaloneWSResponse
import play.api.libs.ws.{WSCookie => ScalaWSCookie}
import play.api.libs.ws.{XML => ScalaXML}
import play.api.libs.ws.{XMLBodyReadables => ScalaXMLBodyReadables}
import play.api.libs.ws.{XMLBodyWritables => ScalaXMLBodyWritables}
import play.libs.ws.{BodyReadable => JavaBodyReadable}
import play.libs.ws.{BodyWritable => JavaBodyWritable}
import play.libs.ws.{StandaloneWSResponse => JavaStandaloneWSResponse}
import play.libs.ws.{WSCookie => JavaWSCookie}
import play.libs.ws.{XML => JavaXML}
import play.libs.ws.{XMLBodyReadables => JavaXMLBodyReadables}
import play.libs.ws.{XMLBodyWritables => JavaXMLBodyWritables}

class Play_ws_standalone_xml_2_13Test {
  @Test
  def scalaParserAndBodyReadableParseNamespacedXmlResponses(): Unit = {
    val xmlText: String =
      """
        |<feed xmlns="urn:feeds" version="1">
        |  <entry id="a-1"><title>Native Image</title></entry>
        |  <entry id="a-2"><title>Play WS</title></entry>
        |</feed>
        |""".stripMargin

    val parsedWithPublicParser: Elem = ScalaXML.parser.load(new InputSource(new StringReader(xmlText)))
    assertEquals("feed", parsedWithPublicParser.label)
    assertEquals("urn:feeds", parsedWithPublicParser.namespace)
    assertEquals("1", parsedWithPublicParser.attribute("version").get.text)
    assertEquals(Seq("Native Image", "Play WS"), (parsedWithPublicParser \\ "title").map(_.text))

    val response: StandaloneWSResponse = ScalaResponse(ByteString.fromString(xmlText))
    val readableParsed: Elem = response.body[Elem](ScalaXMLBodyReadables.readableAsXml)
    assertEquals("feed", readableParsed.label)
    assertEquals(Seq("a-1", "a-2"), (readableParsed \\ "entry").map(node => (node \ "@id").text))
  }

  @Test
  def scalaBodyWritablesEncodeNodeSeqNodeBufferAndDomDocuments(): Unit = {
    val writables: ScalaXMLBodyWritables = ScalaXMLBodyWritables

    val xmlElement: Elem = <request><name>Ada &amp; Bob</name><active>true</active></request>
    val nodeSeqWritable: BodyWritable[Elem] = writables.writeableOf_NodeSeq[Elem]
    val nodeSeqBody: InMemoryBody = nodeSeqWritable.transform(xmlElement).asInstanceOf[InMemoryBody]
    assertEquals("text/xml", nodeSeqWritable.contentType)
    assertEquals(xmlElement.toString(), nodeSeqBody.bytes.utf8String)

    val buffer: NodeBuffer = new NodeBuffer
    buffer += <first>one</first>
    buffer += <second attr="two">2</second>
    val nodeBufferBody: InMemoryBody = writables.writeableOf_NodeBuffer.transform(buffer).asInstanceOf[InMemoryBody]
    assertEquals("text/xml", writables.writeableOf_NodeBuffer.contentType)
    assertEquals(buffer.toString(), nodeBufferBody.bytes.utf8String)
    assertTrue(nodeBufferBody.bytes.utf8String.contains("<first>one</first>"))
    assertTrue(nodeBufferBody.bytes.utf8String.contains("attr=\"two\""))

    val document: Document = newDocument()
    val root: Element = document.createElementNS("urn:requests", "req:request")
    root.setAttribute("id", "42")
    val payload: Element = document.createElementNS("urn:requests", "req:payload")
    payload.setTextContent("scala-dom")
    root.appendChild(payload)
    document.appendChild(root)

    val documentWritable: BodyWritable[Document] = writables.writeableOf_Document
    val documentBody: InMemoryBody = documentWritable.transform(document).asInstanceOf[InMemoryBody]
    val reparsed: Document = parseDocument(documentBody.bytes)
    assertEquals("text/xml", documentWritable.contentType)
    assertEquals("req:request", reparsed.getDocumentElement.getNodeName)
    assertEquals("urn:requests", reparsed.getDocumentElement.getNamespaceURI)
    assertEquals("42", reparsed.getDocumentElement.getAttribute("id"))
    assertEquals("scala-dom", reparsed.getElementsByTagNameNS("urn:requests", "payload").item(0).getTextContent)
  }

  @Test
  def javaXmlHelpersParseInputSourcesStreamsAndSerializeDocuments(): Unit = {
    val documentFromString: Document = JavaXML.fromString(
      """<catalog xmlns="urn:catalog"><book id="b1">Scala XML</book></catalog>"""
    )
    assertEquals("catalog", documentFromString.getDocumentElement.getLocalName)
    assertEquals("urn:catalog", documentFromString.getDocumentElement.getNamespaceURI)
    assertEquals("Scala XML", documentFromString.getElementsByTagNameNS("urn:catalog", "book").item(0).getTextContent)

    val utf8Bytes: Array[Byte] = "<city>München</city>".getBytes("UTF-8")
    val documentFromStream: Document = JavaXML.fromInputStream(new ByteArrayInputStream(utf8Bytes), "UTF-8")
    assertEquals("city", documentFromStream.getDocumentElement.getTagName)
    assertEquals("München", documentFromStream.getDocumentElement.getTextContent)

    val inputSource: InputSource = new InputSource(new StringReader("<source><value>input-source</value></source>"))
    val documentFromInputSource: Document = JavaXML.fromInputSource(inputSource)
    assertEquals("input-source", documentFromInputSource.getElementsByTagName("value").item(0).getTextContent)

    val serialized: ByteString = JavaXML.toBytes(documentFromString)
    assertTrue(serialized.length > 0)
    val reparsed: Document = JavaXML.fromInputStream(new ByteArrayInputStream(serialized.toArray), "UTF-8")
    assertEquals("catalog", reparsed.getDocumentElement.getLocalName)
    val reparsedBook: Element = reparsed.getElementsByTagNameNS("urn:catalog", "book").item(0).asInstanceOf[Element]
    assertEquals("b1", reparsedBook.getAttribute("id"))
  }

  @Test
  def javaXmlBodyReadablesAndWritablesProcessStandaloneResponses(): Unit = {
    val readables: JavaXMLBodyReadables = new JavaXMLBodyReadables {}
    val response: JavaStandaloneWSResponse = JavaResponse(ByteString.fromString("<response><value>42</value></response>"))

    val document: Document = response.getBody(readables.xml())
    assertEquals("response", document.getDocumentElement.getTagName)
    assertEquals("42", document.getElementsByTagName("value").item(0).getTextContent)

    val writables: JavaXMLBodyWritables = new JavaXMLBodyWritables {}
    val writable: JavaBodyWritable[ByteString] = writables.body(document)
    val bytes: ByteString = writable.body().get()
    val reparsed: Document = JavaXML.fromInputStream(new ByteArrayInputStream(bytes.toArray), "UTF-8")

    assertEquals("application/xml", writable.contentType())
    assertEquals("response", reparsed.getDocumentElement.getTagName)
    assertEquals("42", reparsed.getElementsByTagName("value").item(0).getTextContent)
  }

  @Test
  def secureScalaAndJavaParsersRejectDoctypeDeclarations(): Unit = {
    val documentWithDoctype: String =
      """
        |<!DOCTYPE root [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
        |<root>&secret;</root>
        |""".stripMargin

    assertThrows(
      classOf[RuntimeException],
      new Executable {
        override def execute(): Unit = JavaXML.fromString(documentWithDoctype)
      }
    )
    assertThrows(
      classOf[Exception],
      new Executable {
        override def execute(): Unit = {
          ScalaXML.parser.load(new InputSource(new StringReader(documentWithDoctype)))
          ()
        }
      }
    )
  }

  private def newDocument(): Document = newDocumentBuilder().newDocument()

  private def parseDocument(bytes: ByteString): Document = {
    newDocumentBuilder().parse(new ByteArrayInputStream(bytes.toArray))
  }

  private def newDocumentBuilder(): DocumentBuilder = {
    val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
    factory.setNamespaceAware(true)
    factory.newDocumentBuilder()
  }

  private final case class ScalaResponse(
      bytes: ByteString,
      headers: Map[String, Seq[String]] = Map("Content-Type" -> Seq("application/xml"))
  ) extends StandaloneWSResponse {
    override def uri: URI = URI.create("https://example.test/xml")

    override def underlying[T]: T = bytes.asInstanceOf[T]

    override def status: Int = 200

    override def statusText: String = "OK"

    override def cookies: Seq[ScalaWSCookie] = Seq.empty

    override def cookie(name: String): Option[ScalaWSCookie] = None

    override def body: String = bytes.utf8String

    override def bodyAsBytes: ByteString = bytes

    override def bodyAsSource: Source[ByteString, _] = Source.single(bytes)
  }

  private final case class JavaResponse(bytes: ByteString) extends JavaStandaloneWSResponse {
    override def getUri: URI = URI.create("https://example.test/xml")

    override def getHeaders: JMap[String, JList[String]] = Collections.singletonMap(
      "Content-Type",
      Collections.singletonList("application/xml")
    )

    override def getUnderlying: Object = bytes

    override def getStatus: Int = 200

    override def getStatusText: String = "OK"

    override def getCookies: JList[JavaWSCookie] = Collections.emptyList()

    override def getCookie(name: String): Optional[JavaWSCookie] = Optional.empty()

    override def getContentType: String = "application/xml"

    override def getBody[T](bodyReadable: JavaBodyReadable[T]): T = bodyReadable.apply(this)

    override def getBody: String = bytes.utf8String

    override def getBodyAsBytes: ByteString = bytes
  }
}
