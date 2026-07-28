/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_protobuf_v3_2_13

import akka.protobufv3.internal.GeneratedMessageLiteTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedMessageLiteTest {
  @Test
  def getDefaultInstanceInitializesGeneratedMessageClassByName(): Unit = {
    assertTrue(GeneratedMessageLiteTestSupport.lookupDefaultInstanceInitializesGeneratedMessageClass())
  }

  @Test
  def reflectiveGeneratedAccessorHelpersInvokePublicMethod(): Unit = {
    val value: String = GeneratedMessageLiteTestSupport.invokePublicAccessorThroughGeneratedMessageLite()

    assertEquals("invoked through GeneratedMessageLite", value)
  }
}
