package uk.gov.hmrc.mobiletokenproxy.config

import play.twirl.api.Html

case class MainConfig(maybeMainClass: Option[String] = None,
                      maybeMainDataAttributes: Option[Html] = None) {

  def mainClass: Html = maybeMainClass.map(asClassAttr).getOrElse(HtmlConst.empty)

  def mainDataAttributes: Html = maybeMainDataAttributes.getOrElse(HtmlConst.empty)

  private def asClassAttr(clazz: String): Html = Html(s"""class="$clazz"""")
}
