package views.helpers

case class HeaderNav(title: Option[String],
                     titleLink: Option[play.api.mvc.Call] = None,
                     showBetaLink: Boolean,
                     links: Option[play.twirl.api.Html] = None)