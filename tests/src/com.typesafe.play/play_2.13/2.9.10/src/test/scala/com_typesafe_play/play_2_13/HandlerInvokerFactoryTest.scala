/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import java.util.concurrent.CompletionStage

import akka.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.http.HttpConfiguration
import play.api.routing.HandlerDef
import play.core.j.JavaAction
import play.core.j.JavaHandler
import play.core.j.MappedJavaHandlerComponents
import play.core.routing.HandlerInvokerFactory
import play.http.ActionCreator
import play.libs.F
import play.libs.streams.Accumulator
import play.mvc.Action
import play.mvc.BodyParser
import play.mvc.Http
import play.mvc.Result
import play.mvc.Results

import scala.concurrent.ExecutionContext

class HandlerInvokerFactoryTest {
  @Test
  def loadsJavaControllerClassFromControllerName(): Unit = {
    val javaAction: JavaAction = javaActionFor(
      HandlerDef(
        classLoader = getClass.getClassLoader,
        routerPackage = "",
        controller = "_root_." + classOf[DirectHandlerInvokerFactoryController].getName,
        method = "index",
        parameterTypes = Seq.empty,
        verb = "GET",
        path = "/direct"
      )
    )

    assertThat(javaAction.annotations.controller).isEqualTo(classOf[DirectHandlerInvokerFactoryController])
    assertThat(javaAction.annotations.method.getName).isEqualTo("index")
  }

  @Test
  def loadsJavaControllerClassRelativeToRouterPackageWhenControllerNameIsNotAbsolute(): Unit = {
    val javaAction: JavaAction = javaActionFor(
      HandlerDef(
        classLoader = getClass.getClassLoader,
        routerPackage = getClass.getPackageName,
        controller = "RelativeHandlerInvokerFactoryController",
        method = "show",
        parameterTypes = Seq(classOf[String]),
        verb = "GET",
        path = "/relative/:id"
      )
    )

    assertThat(javaAction.annotations.controller).isEqualTo(classOf[RelativeHandlerInvokerFactoryController])
    assertThat(javaAction.annotations.method.getName).isEqualTo("show")
  }

  private def javaActionFor(handlerDef: HandlerDef): JavaAction = {
    val handler: JavaHandler = HandlerInvokerFactory.wrapJava
      .createInvoker(Results.ok("unused"), handlerDef)
      .call(Results.ok("created"))
      .asInstanceOf[JavaHandler]

    handler.withComponents(handlerComponents).asInstanceOf[JavaAction]
  }

  private def handlerComponents: MappedJavaHandlerComponents = {
    new MappedJavaHandlerComponents(actionCreator, HttpConfiguration(), ExecutionContext.global)
      .addBodyParser(
        classOf[HandlerInvokerFactoryNoOpBodyParser],
        () => new HandlerInvokerFactoryNoOpBodyParser
      )
  }

  private def actionCreator: ActionCreator = (_: Http.Request, _: java.lang.reflect.Method) =>
    new Action.Simple {
      override def call(request: Http.Request): CompletionStage[Result] = delegate.call(request)
    }
}

class DirectHandlerInvokerFactoryController {
  @BodyParser.Of(classOf[HandlerInvokerFactoryNoOpBodyParser])
  def index(): Result = Results.ok("direct")
}

class RelativeHandlerInvokerFactoryController {
  @BodyParser.Of(classOf[HandlerInvokerFactoryNoOpBodyParser])
  def show(id: String): Result = Results.ok(id)
}

class HandlerInvokerFactoryNoOpBodyParser extends BodyParser[AnyRef] {
  override def apply(request: Http.RequestHeader): Accumulator[ByteString, F.Either[Result, AnyRef]] = {
    throw new AssertionError("This test only initializes the Java action annotations")
  }
}
