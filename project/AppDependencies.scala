import sbt._

object AppDependencies {
  import play.core.PlayVersion

  private val bootstrapPlayVersion = "5.1.0"
  private val govTemplateVersion   = "5.66.0-play-27"
  private val playUiVersion        = "9.2.0-play-27"
  private val playHmrcApiVersion   = "6.2.0-play-27"
  private val domainVersion        = "5.11.0-play-27"

  private val scalatestPlusPlayVersion = "4.0.3"
  private val scalaMockVersion         = "4.4.0"
  private val pegdownVersion           = "1.6.0"
  private val integrationTestVersion   = "1.1.0-play-27"
  private val wiremockVersion          = "2.27.2"
  private val refinedVersion           = "0.9.19"

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-frontend-play-27" % bootstrapPlayVersion,
    "uk.gov.hmrc" %% "govuk-template"             % govTemplateVersion,
    "uk.gov.hmrc" %% "play-ui"                    % playUiVersion,
    "uk.gov.hmrc" %% "play-hmrc-api"              % playHmrcApiVersion,
    "uk.gov.hmrc" %% "domain"                     % domainVersion,
    "eu.timepit"  %% "refined"                    % refinedVersion
  )

  trait TestDependencies {
    lazy val scope: String        = "test"
    lazy val test:  Seq[ModuleID] = ???
  }

  private def testCommon(scope: String) = Seq(
    "org.pegdown"            % "pegdown"             % pegdownVersion           % scope,
    "com.typesafe.play"      %% "play-test"          % PlayVersion.current      % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % scalatestPlusPlayVersion % scope
  )

  object Test {

    def apply(): Seq[ModuleID] =
      new TestDependencies {

        override lazy val test = testCommon(scope) ++ Seq(
            "org.scalamock" %% "scalamock" % scalaMockVersion % scope
          )
      }.test
  }

  object IntegrationTest {

    def apply(): Seq[ModuleID] =
      new TestDependencies {

        override lazy val scope = "it"

        override lazy val test = testCommon(scope) ++ Seq(
            "uk.gov.hmrc"            %% "service-integration-test" % integrationTestVersion % scope,
            "com.github.tomakehurst" % "wiremock"                  % wiremockVersion        % scope
          )
      }.test

    // Transitive dependencies in scalatest/scalatestplusplay drag in a newer version of jetty that is not
    // compatible with wiremock, so we need to pin the jetty stuff to the older version.
    // see https://groups.google.com/forum/#!topic/play-framework/HAIM1ukUCnI
    val jettyVersion = "9.2.13.v20150730"

    def overrides(): Seq[ModuleID] = Seq(
      "org.eclipse.jetty"           % "jetty-server"       % jettyVersion,
      "org.eclipse.jetty"           % "jetty-servlet"      % jettyVersion,
      "org.eclipse.jetty"           % "jetty-security"     % jettyVersion,
      "org.eclipse.jetty"           % "jetty-servlets"     % jettyVersion,
      "org.eclipse.jetty"           % "jetty-continuation" % jettyVersion,
      "org.eclipse.jetty"           % "jetty-webapp"       % jettyVersion,
      "org.eclipse.jetty"           % "jetty-xml"          % jettyVersion,
      "org.eclipse.jetty"           % "jetty-client"       % jettyVersion,
      "org.eclipse.jetty"           % "jetty-http"         % jettyVersion,
      "org.eclipse.jetty"           % "jetty-io"           % jettyVersion,
      "org.eclipse.jetty"           % "jetty-util"         % jettyVersion,
      "org.eclipse.jetty.websocket" % "websocket-api"      % jettyVersion,
      "org.eclipse.jetty.websocket" % "websocket-common"   % jettyVersion,
      "org.eclipse.jetty.websocket" % "websocket-client"   % jettyVersion
    )
  }

  def apply():     Seq[ModuleID] = compile ++ Test() ++ IntegrationTest()
  def overrides(): Seq[ModuleID] = IntegrationTest.overrides()
}
