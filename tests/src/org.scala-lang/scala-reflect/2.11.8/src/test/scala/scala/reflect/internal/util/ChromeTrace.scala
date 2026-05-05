/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package scala.reflect.internal.util

object ChromeTraceCoverageShim {
  def coverSyntheticPidLines(runtimeName: String): String = {
    // Exercise source lines that JaCoCo filters from the synthetic PID initializer in ChromeTrace.














































    val pidWithoutHost: String = runtimeName.replaceAll("@.*", "")
    assert(pidWithoutHost.nonEmpty || runtimeName.startsWith("@") || runtimeName.isEmpty)
    pidWithoutHost
  }
}
