/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import java.util.Optional
import java.util.{Map => JavaMap}

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.mvc.{QueryStringBindable => ScalaQueryStringBindable}

class QueryStringBindableAnonymous11Test {
  @Test
  def bindsJavaQueryStringBindableUsingNoArgumentConstructor(): Unit = {
    val binder: ScalaQueryStringBindable[JavaBackedQueryStringParameter] =
      implicitly[ScalaQueryStringBindable[JavaBackedQueryStringParameter]]

    val result: Option[Either[String, JavaBackedQueryStringParameter]] =
      binder.bind("term", Map("term" -> Seq("graalvm")))

    assertThat(result.isDefined).isTrue
    assertThat(result.get.isRight).isTrue
    val parameter: JavaBackedQueryStringParameter = result.get.toOption.get
    assertThat(parameter.boundKey).isEqualTo("term")
    assertThat(parameter.value).isEqualTo("graalvm")
    assertThat(binder.unbind("term", parameter)).isEqualTo("term=graalvm")
  }

  @Test
  def obtainsJavascriptUnbindFromJavaQueryStringBindableUsingNoArgumentConstructor(): Unit = {
    val binder: ScalaQueryStringBindable[JavaBackedQueryStringParameter] =
      implicitly[ScalaQueryStringBindable[JavaBackedQueryStringParameter]]

    val javascriptUnbind: String = binder.javascriptUnbind

    assertThat(javascriptUnbind).isEqualTo("function(k,v){return k + '=' + v.value;}")
  }
}

final class JavaBackedQueryStringParameter extends play.mvc.QueryStringBindable[JavaBackedQueryStringParameter] {
  var boundKey: String = ""
  var value: String = ""

  override def bind(
      key: String,
      data: JavaMap[String, Array[String]]
  ): Optional[JavaBackedQueryStringParameter] = {
    val values: Array[String] = data.get(key)
    if (values == null || values.isEmpty) {
      Optional.empty()
    } else {
      boundKey = key
      value = values(0)
      Optional.of(this)
    }
  }

  override def unbind(key: String): String = s"$key=$value"

  override def javascriptUnbind(): String = "function(k,v){return k + '=' + v.value;}"
}
