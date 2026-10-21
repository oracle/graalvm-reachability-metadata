/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_plokhotnyuk_jsoniter_scala.jsoniter_scala_core_3

import com.github.plokhotnyuk.jsoniter_scala.core.JsonReader
import com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.core.JsonWriter
import com.github.plokhotnyuk.jsoniter_scala.core.readFromArray
import com.github.plokhotnyuk.jsoniter_scala.core.readFromArrayReentrant
import com.github.plokhotnyuk.jsoniter_scala.core.readFromString
import com.github.plokhotnyuk.jsoniter_scala.core.readFromStringReentrant
import com.github.plokhotnyuk.jsoniter_scala.core.scanJsonValuesFromStream
import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray
import com.github.plokhotnyuk.jsoniter_scala.core.writeToArrayReentrant
import com.github.plokhotnyuk.jsoniter_scala.core.writeToStream
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.github.plokhotnyuk.jsoniter_scala.core.writeToStringReentrant
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64

import scala.collection.mutable.ListBuffer

import QueueEnvelopeNestedJsonTest.*

/**
 * Reentrant nested serialization with `writeToStringReentrant`, `readFromStringReentrant`,
 * `writeToArrayReentrant` and `readFromArrayReentrant` called from inside running codecs: a
 * queue consumer unwraps order events that a topic delivers as a JSON document inside a JSON
 * string, wrapped again by a queue record, or as base64 bytes in a log record, and a listing
 * API hands out its paging cursor as an unpadded base64url token of a JSON document.
 */
class QueueEnvelopeNestedJsonTest {

  private val order: OrderPlaced = OrderPlaced("o-1001", BigDecimal("59.90"), List("sku-1", "sku-2"), "EUR")

  private val notification: Notification =
    Notification("arn:aws:sns:eu-west-1:123456789012:orders", "m-7", order)

  @Test
  def topicNotificationCarriesTheOrderAsAJsonString(): Unit = {
    val json: String = writeToString(notification)

    assertEquals(
      """{"topicArn":"arn:aws:sns:eu-west-1:123456789012:orders","messageId":"m-7",""" +
        """"message":"{\"orderId\":\"o-1001\",\"total\":59.90,\"skus\":[\"sku-1\",\"sku-2\"],""" +
        """\"currency\":\"EUR\"}","sentBy":"order-service"}""",
      json
    )
    assertEquals(notification, readFromString[Notification](json))

    val publishedByAnotherClient: String =
      """{
        |  "sentBy": "legacy-publisher",
        |  "message": "{ \"currency\": \"EUR\", \"skus\": [\"sku-1\", \"sku-2\"], \"total\": 59.9, \"orderId\": \"o-1001\" }",
        |  "messageId": "m-7",
        |  "topicArn": "arn:aws:sns:eu-west-1:123456789012:orders"
        |}""".stripMargin
    assertEquals(notification, readFromString[Notification](publishedByAnotherClient))
  }

  @Test
  def queueRecordWrapsTheNotificationASecondTime(): Unit = {
    val record: QueueRecord = QueueRecord("rh-42", notification, Map("ApproximateReceiveCount" -> "1"))
    val json: String = writeToString(record)

    assertTrue(json.startsWith("""{"receiptHandle":"rh-42","body":"{\"topicArn\":"""), json)
    assertTrue(json.contains("""\"message\":\"{\\\"orderId\\\":\\\"o-1001\\\","""), json)
    assertTrue(json.endsWith(""""attributes":{"ApproximateReceiveCount":"1"}}"""), json)
    assertEquals(record, readFromString[QueueRecord](json))

    val body: String = readFromString[RawQueueRecord](json).body
    assertEquals(writeToString(notification), body)
    assertEquals(notification, readFromString[Notification](body))
  }

  @Test
  def queueRecordsAreScannedFromAnNdjsonStream(): Unit = {
    val records: List[QueueRecord] = (1 to 4).toList.map { n =>
      val placed: OrderPlaced = OrderPlaced(s"o-$n", BigDecimal(n) * BigDecimal("10.25"), List.fill(n)(s"sku-$n"), "USD")
      QueueRecord(s"rh-$n", Notification("arn:aws:sns:us-east-1:1:orders", s"m-$n", placed), Map("attempt" -> n.toString))
    }
    val out: ByteArrayOutputStream = new ByteArrayOutputStream()
    records.foreach { record =>
      writeToStream(record, out)
      out.write('\n')
    }

    val received: ListBuffer[QueueRecord] = ListBuffer.empty
    scanJsonValuesFromStream[QueueRecord](new ByteArrayInputStream(out.toByteArray)) { record =>
      received += record
      true
    }

    assertEquals(records, received.toList)
    assertEquals(BigDecimal("102.50"), received.map(_.body.message.total).sum)
  }

  @Test
  def logRecordCarriesTheOrderAsBase64Bytes(): Unit = {
    val record: LogRecord = LogRecord("orders", 3, 1048576L, order)
    val json: String = writeToString(record)

    val expectedValue: String = Base64.getEncoder.encodeToString(writeToArray(order))
    assertEquals(s"""{"topic":"orders","partition":3,"offset":1048576,"value":"$expectedValue"}""", json)
    assertEquals(
      """{"orderId":"o-1001","total":59.90,"skus":["sku-1","sku-2"],"currency":"EUR"}""",
      new String(Base64.getDecoder.decode(expectedValue), StandardCharsets.UTF_8)
    )
    assertEquals(record, readFromArray[LogRecord](json.getBytes(StandardCharsets.UTF_8)))
  }

  @Test
  def pagingCursorIsAnOpaqueUnpaddedBase64UrlToken(): Unit = {
    val page: OrderPage = OrderPage(List(order), Some(Cursor("o-1001", 25, ascending = false)))
    val json: String = writeToString(page)

    val token: String = readFromString[RawOrderPage](json).next.get
    assertFalse(token.contains("="), token)
    assertFalse(token.contains("+") || token.contains("/"), token)
    assertEquals(
      """{"afterId":"o-1001","pageSize":25,"ascending":false}""",
      new String(Base64.getUrlDecoder.decode(token), StandardCharsets.UTF_8)
    )
    assertEquals(page, readFromString[OrderPage](json))

    val lastPage: OrderPage = OrderPage(List(order), None)
    val lastJson: String = writeToString(lastPage)
    assertFalse(lastJson.contains("next"), lastJson)
    assertEquals(lastPage, readFromString[OrderPage](lastJson))
  }

  @Test
  def malformedNestedDocumentsAreRejected(): Unit = {
    val truncatedMessage: String =
      """{"topicArn":"t","messageId":"m-1","message":"{\"orderId\":\"o-1\",\"total\":","sentBy":"x"}"""
    assertThrows(classOf[JsonReaderException], () => readFromString[Notification](truncatedMessage))

    val missingMessage: String = """{"topicArn":"t","messageId":"m-1"}"""
    val missing: JsonReaderException =
      assertThrows(classOf[JsonReaderException], () => readFromString[Notification](missingMessage))
    assertTrue(missing.getMessage.contains("missing required field \"message\""), missing.getMessage)

    val notBase64: String = """{"topic":"orders","partition":0,"offset":1,"value":"not*base64"}"""
    assertThrows(classOf[JsonReaderException], () => readFromString[LogRecord](notBase64))

    val forgedCursor: String = """{"orders":[],"next":"bm90LWpzb24"}"""
    assertThrows(classOf[JsonReaderException], () => readFromString[OrderPage](forgedCursor))
  }
}

object QueueEnvelopeNestedJsonTest {

  final case class OrderPlaced(orderId: String, total: BigDecimal, skus: List[String], currency: String)

  /** A topic notification whose `message` field holds the order as a JSON document in a string. */
  final case class Notification(topicArn: String, messageId: String, message: OrderPlaced)

  /** A queue record whose `body` field holds the whole notification as a JSON document in a string. */
  final case class QueueRecord(receiptHandle: String, body: Notification, attributes: Map[String, String])

  /** The same queue record as a consumer that does not unwrap the body sees it. */
  final case class RawQueueRecord(receiptHandle: String, body: String, attributes: Map[String, String])

  /** A log record whose `value` field holds the order's JSON bytes as base64. */
  final case class LogRecord(topic: String, partition: Int, offset: Long, value: OrderPlaced)

  final case class Cursor(afterId: String, pageSize: Int, ascending: Boolean)

  final case class OrderPage(orders: List[OrderPlaced], next: Option[Cursor])

  final case class RawOrderPage(orders: List[OrderPlaced], next: Option[String])

  given orderPlacedCodec: JsonValueCodec[OrderPlaced] = JsonCodecMaker.make

  given rawQueueRecordCodec: JsonValueCodec[RawQueueRecord] = JsonCodecMaker.make

  given rawOrderPageCodec: JsonValueCodec[RawOrderPage] = JsonCodecMaker.make

  /** Visits every key of the object whose `{` was just consumed; returns after its `}`. */
  private def readFields(in: JsonReader)(field: String => Unit): Unit =
    if (!in.isNextToken('}')) {
      in.rollbackToken()
      var more: Boolean = true
      while (more) {
        field(in.readKeyAsString())
        more = in.isNextToken(',')
      }
      if (!in.isCurrentToken('}')) in.objectEndOrCommaError()
    }

  given notificationCodec: JsonValueCodec[Notification] = new JsonValueCodec[Notification] {
    override def nullValue: Notification = null

    override def encodeValue(x: Notification, out: JsonWriter): Unit = {
      out.writeObjectStart()
      out.writeKey("topicArn")
      out.writeVal(x.topicArn)
      out.writeKey("messageId")
      out.writeVal(x.messageId)
      out.writeKey("message")
      out.writeVal(writeToStringReentrant(x.message))
      out.writeKey("sentBy")
      out.writeVal("order-service")
      out.writeObjectEnd()
    }

    override def decodeValue(in: JsonReader, default: Notification): Notification =
      if (!in.isNextToken('{')) in.readNullOrTokenError(default, '{')
      else {
        var topicArn: String = null
        var messageId: String = null
        var message: OrderPlaced = null
        readFields(in) {
          case "topicArn" => topicArn = in.readString(null)
          case "messageId" => messageId = in.readString(null)
          case "message" => message = readFromStringReentrant[OrderPlaced](in.readString(null))
          case _ => in.skip()
        }
        if (topicArn == null) in.requiredFieldError("topicArn")
        if (messageId == null) in.requiredFieldError("messageId")
        if (message == null) in.requiredFieldError("message")
        Notification(topicArn, messageId, message)
      }
  }

  given queueRecordCodec: JsonValueCodec[QueueRecord] = new JsonValueCodec[QueueRecord] {
    override def nullValue: QueueRecord = null

    override def encodeValue(x: QueueRecord, out: JsonWriter): Unit = {
      out.writeObjectStart()
      out.writeKey("receiptHandle")
      out.writeVal(x.receiptHandle)
      out.writeKey("body")
      out.writeVal(writeToStringReentrant(x.body))
      out.writeKey("attributes")
      out.writeObjectStart()
      x.attributes.foreach { (name, value) =>
        out.writeKey(name)
        out.writeVal(value)
      }
      out.writeObjectEnd()
      out.writeObjectEnd()
    }

    override def decodeValue(in: JsonReader, default: QueueRecord): QueueRecord =
      if (!in.isNextToken('{')) in.readNullOrTokenError(default, '{')
      else {
        var receiptHandle: String = null
        var body: Notification = null
        var attributes: Map[String, String] = Map.empty
        readFields(in) {
          case "receiptHandle" => receiptHandle = in.readString(null)
          case "body" => body = readFromStringReentrant[Notification](in.readString(null))
          case "attributes" =>
            if (!in.isNextToken('{')) in.objectStartOrNullError()
            readFields(in) { name =>
              attributes += name -> in.readString(null)
            }
          case _ => in.skip()
        }
        if (receiptHandle == null) in.requiredFieldError("receiptHandle")
        if (body == null) in.requiredFieldError("body")
        QueueRecord(receiptHandle, body, attributes)
      }
  }

  given logRecordCodec: JsonValueCodec[LogRecord] = new JsonValueCodec[LogRecord] {
    override def nullValue: LogRecord = null

    override def encodeValue(x: LogRecord, out: JsonWriter): Unit = {
      out.writeObjectStart()
      out.writeKey("topic")
      out.writeVal(x.topic)
      out.writeKey("partition")
      out.writeVal(x.partition)
      out.writeKey("offset")
      out.writeVal(x.offset)
      out.writeKey("value")
      out.writeBase64Val(writeToArrayReentrant(x.value), true)
      out.writeObjectEnd()
    }

    override def decodeValue(in: JsonReader, default: LogRecord): LogRecord =
      if (!in.isNextToken('{')) in.readNullOrTokenError(default, '{')
      else {
        var topic: String = null
        var partition: Int = -1
        var offset: Long = -1L
        var value: OrderPlaced = null
        readFields(in) {
          case "topic" => topic = in.readString(null)
          case "partition" => partition = in.readInt()
          case "offset" => offset = in.readLong()
          case "value" => value = readFromArrayReentrant[OrderPlaced](in.readBase64AsBytes(null))
          case _ => in.skip()
        }
        if (topic == null) in.requiredFieldError("topic")
        if (value == null) in.requiredFieldError("value")
        LogRecord(topic, partition, offset, value)
      }
  }

  /** Encodes the cursor as an unpadded base64url token, so clients treat it as opaque. */
  given cursorCodec: JsonValueCodec[Cursor] = new JsonValueCodec[Cursor] {
    private val documentCodec: JsonValueCodec[Cursor] = JsonCodecMaker.make

    override def nullValue: Cursor = null

    override def encodeValue(x: Cursor, out: JsonWriter): Unit =
      out.writeBase64UrlVal(writeToArrayReentrant(x)(using documentCodec), false)

    override def decodeValue(in: JsonReader, default: Cursor): Cursor =
      readFromArrayReentrant(in.readBase64UrlAsBytes(null))(using documentCodec)
  }

  given orderPageCodec: JsonValueCodec[OrderPage] = JsonCodecMaker.make
}
