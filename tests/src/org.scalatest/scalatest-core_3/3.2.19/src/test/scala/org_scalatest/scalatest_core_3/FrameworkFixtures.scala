/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import java.util.concurrent.CopyOnWriteArrayList
import org.scalatest.events.Event
import org.scalatest.tools.Framework
import sbt.testing.{EventHandler, Logger, Status, SubclassFingerprint, SuiteSelector, Task, TaskDef}
import scala.collection.mutable.Queue
import scala.jdk.CollectionConverters.*

object FrameworkFixtures {
  case class RemoteResult(summary: String, events: Vector[Event], statuses: Vector[Status])

  def runRemote(): RemoteResult = {
    val framework: Framework = new Framework
    val loader: ClassLoader = classOf[PassingSuite].getClassLoader
    CollectingReporter.events.clear()
    val parent = framework.runner(Array("-C", classOf[CollectingReporter].getName), Array.empty, loader)
    val statuses: CopyOnWriteArrayList[Status] = new CopyOnWriteArrayList[Status]()
    val handler: EventHandler = new EventHandler {
      override def handle(event: sbt.testing.Event): Unit = statuses.add(event.status())
    }
    val logger: Logger = new Logger {
      override def ansiCodesSupported(): Boolean = false
      override def error(message: String): Unit = System.err.println(message)
      override def warn(message: String): Unit = System.err.println(message)
      override def info(message: String): Unit = ()
      override def debug(message: String): Unit = ()
      override def trace(error: Throwable): Unit = throw new AssertionError(error)
    }
    var summary: String = ""
    try {
      val child = framework.runner(Array.empty, parent.remoteArgs(), loader)
      try {
        val fingerprint = framework.fingerprints().collectFirst {
          case fingerprint: SubclassFingerprint => fingerprint
        }.get
        val task = new TaskDef(classOf[PassingSuite].getName, fingerprint, true,
          Array(new SuiteSelector))
        val pending: Queue[Task] = Queue.from(child.tasks(Array(task)))
        while (pending.nonEmpty) {
          pending.enqueueAll(pending.dequeue().execute(handler, Array(logger)))
        }
      } finally child.done()
    } finally {
      summary = parent.done()
    }
    RemoteResult(summary, CollectingReporter.events.asScala.toVector, statuses.asScala.toVector)
  }
}
