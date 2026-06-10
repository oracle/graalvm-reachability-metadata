/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_3

import java.io.File
import java.net.URL

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.ILoggerFactory
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.Environment
import play.api.LoggerConfigurator
import play.api.Mode

class LoggerConfiguratorTest {
  @Test
  def instantiatesLoggerConfiguratorDeclaredInResource(): Unit = {
    val configurator: Option[LoggerConfigurator] = LoggerConfigurator(getClass.getClassLoader)

    assertThat(configurator.isDefined).isTrue()
    assertThat(configurator.get).isInstanceOf(classOf[TestLoggerConfigurator])
  }
}

final class TestLoggerConfigurator extends LoggerConfigurator {
  override def init(rootPath: File, mode: Mode): Unit = {}

  override def configure(env: Environment): Unit = {}

  override def configure(
      env: Environment,
      configuration: Configuration,
      optionalProperties: Map[String, String]
  ): Unit = {}

  override def configure(properties: Map[String, String], config: Option[URL]): Unit = {}

  override def loggerFactory: ILoggerFactory = LoggerFactory.getILoggerFactory

  override def shutdown(): Unit = {}
}
