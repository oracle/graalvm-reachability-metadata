/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatra.scalatra_common_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNull, assertSame, assertTrue}
import org.junit.jupiter.api.Test
import org.scalatra.Handler
import org.scalatra.servlet.{HasMultipartConfig, MountConfig, MultipartConfig, ScalatraAsyncSupport}

import java.io.InputStream
import java.net.URL
import java.util
import java.util.EventListener
import javax.servlet.descriptor.JspConfigDescriptor
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import javax.servlet.{Filter, FilterRegistration, RequestDispatcher, Servlet, ServletContext, ServletRegistration, SessionCookieConfig, SessionTrackingMode}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

class Scalatra_common_3Test {
  @Test
  def multipartConfigDefaultsMapToServletMultipartDefaults(): Unit = {
    val config: MultipartConfig = MultipartConfig()
    val element = config.toMultipartConfigElement

    assertEquals(None, config.location)
    assertEquals(None, config.maxFileSize)
    assertEquals(None, config.maxRequestSize)
    assertEquals(None, config.fileSizeThreshold)
    assertEquals("", element.getLocation)
    assertEquals(-1L, element.getMaxFileSize)
    assertEquals(-1L, element.getMaxRequestSize)
    assertEquals(0, element.getFileSizeThreshold)
    assertEquals(HasMultipartConfig.DefaultMultipartConfig, config)
  }

  @Test
  def multipartConfigPreservesConfiguredLimitsAndCaseClassSemantics(): Unit = {
    val config: MultipartConfig = MultipartConfig(
      location = Some("/var/tmp/uploads"),
      maxFileSize = Some(1024L),
      maxRequestSize = Some(4096L),
      fileSizeThreshold = Some(128)
    )
    val element = config.toMultipartConfigElement

    assertEquals("/var/tmp/uploads", element.getLocation)
    assertEquals(1024L, element.getMaxFileSize)
    assertEquals(4096L, element.getMaxRequestSize)
    assertEquals(128, element.getFileSizeThreshold)
    assertEquals("/var/tmp/uploads", config._1.get)
    assertEquals(1024L, config._2.get)
    assertEquals(4, config.productArity)
    assertEquals("MultipartConfig", config.productPrefix)
    assertEquals(List("location", "maxFileSize", "maxRequestSize", "fileSizeThreshold"), config.productElementNames.toList)

    val copied: MultipartConfig = config.copy(maxFileSize = Some(2048L), fileSizeThreshold = None)
    assertEquals(Some("/var/tmp/uploads"), copied.location)
    assertEquals(Some(2048L), copied.maxFileSize)
    assertEquals(Some(4096L), copied.maxRequestSize)
    assertEquals(None, copied.fileSizeThreshold)
    assertFalse(config == copied)
  }

  @Test
  def mountConfigStoresMultipartConfigInServletContextAttributes(): Unit = {
    val context = new RecordingServletContext
    val config: MultipartConfig = MultipartConfig(Some("/mnt/uploads"), Some(10L), Some(20L), Some(2))
    val mountConfig: MountConfig = config

    mountConfig(context)

    assertSame(config, context.getAttribute(HasMultipartConfig.MultipartConfigKey))
    assertTrue(context.getAttributeNames.asScala.toSet.contains(HasMultipartConfig.MultipartConfigKey))
  }

  @Test
  def hasMultipartConfigReadsContextConfigOrFallsBackToDefault(): Unit = {
    val emptyContext = new RecordingServletContext
    val defaultAware = new MultipartAware(emptyContext)
    assertEquals(HasMultipartConfig.DefaultMultipartConfig, defaultAware.multipartConfig)

    val contextConfig: MultipartConfig = MultipartConfig(Some("/context"), Some(100L), Some(200L), Some(8))
    contextConfig(emptyContext)

    val contextAware = new MultipartAware(emptyContext)
    assertEquals(contextConfig, contextAware.multipartConfig)

    emptyContext.removeAttribute(HasMultipartConfig.MultipartConfigKey)
    assertNull(emptyContext.getAttribute(HasMultipartConfig.MultipartConfigKey))
    assertEquals(HasMultipartConfig.DefaultMultipartConfig, contextAware.multipartConfig)
  }

  @Test
  def configureMultipartHandlingOverridesContextAndMountsDuringInitialization(): Unit = {
    val context = new RecordingServletContext
    val contextConfig: MultipartConfig = MultipartConfig(Some("/context"), Some(1L), Some(2L), Some(3))
    contextConfig(context)

    val providedConfig: MultipartConfig = MultipartConfig(Some("/provided"), Some(11L), Some(22L), Some(4))
    val aware = new MultipartAware(context)
    aware.configureMultipartHandling(providedConfig)

    assertEquals(providedConfig, aware.multipartConfig)
    assertEquals(None, aware.initializedWith)

    val initConfig = TestConfig(context, Map("environment" -> "native-image"))
    aware.initialize(initConfig)

    assertEquals(Some(initConfig), aware.initializedWith)
    assertSame(providedConfig, context.getAttribute(HasMultipartConfig.MultipartConfigKey))
    assertEquals(Some("native-image"), aware.readInitParameter(initConfig, "environment"))
    assertEquals(None, aware.readInitParameter(initConfig, "missing"))
  }

  @Test
  def hasMultipartConfigFallsBackWhenServletContextLookupFails(): Unit = {
    val failingContext = new RecordingServletContext {
      override def getAttribute(name: String): Object = throw new IllegalStateException("context not ready")
    }
    val aware = new MultipartAware(failingContext)

    assertEquals(HasMultipartConfig.DefaultMultipartConfig, aware.multipartConfig)
  }

  @Test
  def initializableProvidesNoOpShutdownLifecycleHook(): Unit = {
    val initializable = new ShutdownLifecycleComponent

    initializable.runShutdownTwice()

    assertEquals(None, initializable.initializedWith)
  }

  @Test
  def handlerImplementationsReceiveRequestAndResponseReferences(): Unit = {
    val handler = new RecordingHandler

    handler.handle(null, null)

    assertEquals(1, handler.calls)
    assertNull(handler.lastRequest)
    assertNull(handler.lastResponse)
  }

  @Test
  def asyncSupportIsUsableAsAMarkerTrait(): Unit = {
    val component = new AsyncComponent

    assertTrue(component.isInstanceOf[ScalatraAsyncSupport])
    assertEquals("async-ready", component.name)
  }

  @Test
  def configureMultipartHandlingUsesMostRecentConfiguration(): Unit = {
    val context = new RecordingServletContext
    val firstConfig: MultipartConfig = MultipartConfig(Some("/first"), Some(1L), Some(2L), Some(3))
    val replacementConfig: MultipartConfig = MultipartConfig(Some("/replacement"), Some(10L), Some(20L), Some(4))
    val aware = new MultipartAware(context)

    aware.configureMultipartHandling(firstConfig)
    aware.configureMultipartHandling(replacementConfig)
    aware.initialize(TestConfig(context, Map.empty))

    assertEquals(replacementConfig, aware.multipartConfig)
    assertSame(replacementConfig, context.getAttribute(HasMultipartConfig.MultipartConfigKey))
  }

  private final case class TestConfig(context: ServletContext, initParameters: Map[String, String])

  private abstract class BaseInitializable extends org.scalatra.Initializable {
    type ConfigT = TestConfig

    var initializedWith: Option[TestConfig] = None

    override protected implicit def configWrapper(config: TestConfig): Config = new Config {
      override def context: ServletContext = config.context

      override def getInitParameterOption(key: String): Option[String] = config.initParameters.get(key)
    }

    override def initialize(config: TestConfig): Unit = {
      initializedWith = Some(config)
    }

    def readInitParameter(config: TestConfig, key: String): Option[String] = configWrapper(config).getInitParameterOption(key)
  }

  private final class MultipartAware(override val servletContext: ServletContext) extends BaseInitializable with HasMultipartConfig

  private final class ShutdownLifecycleComponent extends BaseInitializable {
    def runShutdownTwice(): Unit = {
      shutdown()
      shutdown()
    }
  }

  private final class RecordingHandler extends Handler {
    var calls: Int = 0
    var lastRequest: HttpServletRequest = _
    var lastResponse: HttpServletResponse = _

    override def handle(request: HttpServletRequest, res: HttpServletResponse): Unit = {
      calls += 1
      lastRequest = request
      lastResponse = res
    }
  }

  private final class AsyncComponent extends ScalatraAsyncSupport {
    val name: String = "async-ready"
  }

  private class RecordingServletContext extends ServletContext {
    private val attributes: mutable.Map[String, Object] = mutable.LinkedHashMap.empty
    private val initParameters: mutable.Map[String, String] = mutable.LinkedHashMap.empty

    override def getContextPath: String = "/test"

    override def getContext(uripath: String): ServletContext = this

    override def getMajorVersion: Int = 4

    override def getMinorVersion: Int = 0

    override def getEffectiveMajorVersion: Int = 4

    override def getEffectiveMinorVersion: Int = 0

    override def getMimeType(file: String): String = null

    override def getResourcePaths(path: String): util.Set[String] = util.Collections.emptySet[String]()

    override def getResource(path: String): URL = null

    override def getResourceAsStream(path: String): InputStream = null

    override def getRequestDispatcher(path: String): RequestDispatcher = null

    override def getNamedDispatcher(name: String): RequestDispatcher = null

    override def getServlet(name: String): Servlet = unsupported

    override def getServlets: util.Enumeration[Servlet] = util.Collections.emptyEnumeration[Servlet]()

    override def getServletNames: util.Enumeration[String] = util.Collections.emptyEnumeration[String]()

    override def log(msg: String): Unit = ()

    override def log(exception: Exception, msg: String): Unit = ()

    override def log(message: String, throwable: Throwable): Unit = ()

    override def getRealPath(path: String): String = null

    override def getServerInfo: String = "recording-servlet-context"

    override def getInitParameter(name: String): String = initParameters.getOrElse(name, null)

    override def getInitParameterNames: util.Enumeration[String] = util.Collections.enumeration(initParameters.keys.toList.asJava)

    override def setInitParameter(name: String, value: String): Boolean = {
      if (initParameters.contains(name)) false
      else {
        initParameters.update(name, value)
        true
      }
    }

    override def getAttribute(name: String): Object = attributes.getOrElse(name, null)

    override def getAttributeNames: util.Enumeration[String] = util.Collections.enumeration(attributes.keys.toList.asJava)

    override def setAttribute(name: String, obj: Object): Unit = attributes.update(name, obj)

    override def removeAttribute(name: String): Unit = attributes.remove(name)

    override def getServletContextName: String = "recording-context"

    override def addServlet(servletName: String, className: String): ServletRegistration.Dynamic = unsupported

    override def addServlet(servletName: String, servlet: Servlet): ServletRegistration.Dynamic = unsupported

    override def addServlet(servletName: String, servletClass: Class[? <: Servlet]): ServletRegistration.Dynamic = unsupported

    override def addJspFile(servletName: String, jspFile: String): ServletRegistration.Dynamic = unsupported

    override def createServlet[T <: Servlet](clazz: Class[T]): T = unsupported

    override def getServletRegistration(servletName: String): ServletRegistration = null

    override def getServletRegistrations: util.Map[String, ? <: ServletRegistration] = util.Collections.emptyMap[String, ServletRegistration]()

    override def addFilter(filterName: String, className: String): FilterRegistration.Dynamic = unsupported

    override def addFilter(filterName: String, filter: Filter): FilterRegistration.Dynamic = unsupported

    override def addFilter(filterName: String, filterClass: Class[? <: Filter]): FilterRegistration.Dynamic = unsupported

    override def createFilter[T <: Filter](clazz: Class[T]): T = unsupported

    override def getFilterRegistration(filterName: String): FilterRegistration = null

    override def getFilterRegistrations: util.Map[String, ? <: FilterRegistration] = util.Collections.emptyMap[String, FilterRegistration]()

    override def getSessionCookieConfig: SessionCookieConfig = null

    override def setSessionTrackingModes(sessionTrackingModes: util.Set[SessionTrackingMode]): Unit = ()

    override def getDefaultSessionTrackingModes: util.Set[SessionTrackingMode] = util.Collections.emptySet[SessionTrackingMode]()

    override def getEffectiveSessionTrackingModes: util.Set[SessionTrackingMode] = util.Collections.emptySet[SessionTrackingMode]()

    override def addListener(className: String): Unit = ()

    override def addListener[T <: EventListener](t: T): Unit = ()

    override def addListener(listenerClass: Class[? <: EventListener]): Unit = ()

    override def createListener[T <: EventListener](clazz: Class[T]): T = unsupported

    override def getJspConfigDescriptor: JspConfigDescriptor = null

    override def getClassLoader: ClassLoader = Thread.currentThread().getContextClassLoader

    override def declareRoles(roleNames: String*): Unit = ()

    override def getVirtualServerName: String = "recording-server"

    override def getSessionTimeout: Int = 0

    override def setSessionTimeout(sessionTimeout: Int): Unit = ()

    override def getRequestCharacterEncoding: String = null

    override def setRequestCharacterEncoding(encoding: String): Unit = ()

    override def getResponseCharacterEncoding: String = null

    override def setResponseCharacterEncoding(encoding: String): Unit = ()

    private def unsupported[A]: A = throw new UnsupportedOperationException("not required by these tests")
  }
}
