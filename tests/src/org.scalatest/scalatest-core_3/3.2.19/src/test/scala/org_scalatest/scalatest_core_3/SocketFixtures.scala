/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import java.io.{ObjectInputStream, WriteAbortedException}
import java.net.{InetAddress, ServerSocket}
import java.util.concurrent.{Callable, Executors, TimeUnit}
import org.scalatest.events.{Event, RunCompleted}
import org.scalatest.exceptions.PayloadField
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.tools.Runner
import scala.collection.mutable.ArrayBuffer

class PayloadFailure extends RuntimeException("fixture failure") with PayloadField {
  override val payload: Option[Any] = Some("serializable context")
  val attachment: Object = new Object
}

class FailingPayloadSuite extends AnyFunSuite {
  test("reports a failure with diagnostic context") {
    throw new PayloadFailure
  }
}

object SocketFixtures {
  def receive(suite: Class[?]): (Boolean, Vector[Event]) = {
    val server: ServerSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress)
    server.setSoTimeout(30000)
    val executor = Executors.newSingleThreadExecutor()
    try {
      val received = executor.submit(new Callable[Vector[Event]] {
        override def call(): Vector[Event] = {
          val events: ArrayBuffer[Event] = ArrayBuffer.empty
          var completed: Boolean = false
          while (!completed) {
            val socket = server.accept()
            socket.setSoTimeout(30000)
            try {
              val input: ObjectInputStream = new ObjectInputStream(socket.getInputStream)
              try {
                var connected: Boolean = true
                while (connected && !completed) {
                  try {
                    val event: Event = input.readObject().asInstanceOf[Event]
                    events += event
                    completed = event.isInstanceOf[RunCompleted]
                  } catch {
                    // SocketReporter reconnects after replacing an unserializable event.
                    case _: WriteAbortedException => connected = false
                  }
                }
              } finally input.close()
            } finally socket.close()
          }
          events.toVector
        }
      })
      val passed: Boolean = Runner.run(Array("-s", suite.getName, "-K",
        server.getInetAddress.getHostAddress, server.getLocalPort.toString))
      (passed, received.get(60, TimeUnit.SECONDS))
    } finally {
      server.close()
      executor.shutdownNow()
      executor.awaitTermination(30, TimeUnit.SECONDS)
    }
  }
}
