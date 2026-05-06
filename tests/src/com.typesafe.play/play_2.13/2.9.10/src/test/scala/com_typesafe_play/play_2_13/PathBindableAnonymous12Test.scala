/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.mvc.{PathBindable => ScalaPathBindable}

class PathBindableAnonymous12Test {
  @Test
  def bindsJavaPathBindableUsingNoArgumentConstructor(): Unit = {
    val binder: ScalaPathBindable[JavaBackedPathParameter] = implicitly[ScalaPathBindable[JavaBackedPathParameter]]

    val result: Either[String, JavaBackedPathParameter] = binder.bind("slug", "play-framework")

    assertThat(result.isRight).isTrue
    val parameter: JavaBackedPathParameter = result.toOption.get
    assertThat(parameter.boundKey).isEqualTo("slug")
    assertThat(parameter.value).isEqualTo("play-framework")
    assertThat(binder.unbind("slug", parameter)).isEqualTo("slug=play-framework")
  }

  @Test
  def obtainsJavascriptUnbindFromJavaPathBindableUsingNoArgumentConstructor(): Unit = {
    val binder: ScalaPathBindable[JavaBackedPathParameter] = implicitly[ScalaPathBindable[JavaBackedPathParameter]]

    val javascriptUnbind: String = binder.javascriptUnbind

    assertThat(javascriptUnbind).isEqualTo("function(k,v){return v.value;}")
  }
}

final class JavaBackedPathParameter extends play.mvc.PathBindable[JavaBackedPathParameter] {
  var boundKey: String = ""
  var value: String = ""

  override def bind(key: String, txt: String): JavaBackedPathParameter = {
    boundKey = key
    value = txt
    this
  }

  override def unbind(key: String): String = s"$key=$value"

  override def javascriptUnbind(): String = "function(k,v){return v.value;}"
}
