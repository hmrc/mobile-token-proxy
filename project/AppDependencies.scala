import sbt._

object AppDependencies {
  import play.core.PlayVersion

  private val bootstrapPlayVersion = "5.16.0"
  private val playHmrcApiVersion   = "6.4.0-play-28"
  private val domainVersion        = "6.2.0-play-28"
  private val flexmarkAllVersion   = "0.36.8"

  private val scalatestPlusPlayVersion = "4.0.3"
  private val scalaMockVersion         = "4.4.0"
  private val pegdownVersion           = "1.6.0"
  private val integrationTestVersion   = "1.2.0-play-28"
  private val wiremockVersion          = "2.27.2"
  private val refinedVersion           = "0.9.19"

  lazy val appDependencies: Seq[ModuleID] =
    compile ++ test ++ integrationTest

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-frontend-play-28" % bootstrapPlayVersion,
    "uk.gov.hmrc" %% "play-hmrc-api"              % playHmrcApiVersion,
    "uk.gov.hmrc" %% "domain"                     % domainVersion,
    "eu.timepit"  %% "refined"                    % refinedVersion
  )

  private def testCommon(scope: String) = Seq(
    "org.pegdown"            % "pegdown"             % pegdownVersion           % scope,
    "com.typesafe.play"      %% "play-test"          % PlayVersion.current      % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % scalatestPlusPlayVersion % scope,
    "com.vladsch.flexmark"   % "flexmark-all"        % flexmarkAllVersion       % scope
  )

  val test: Seq[ModuleID] = testCommon("test") ++ Seq(
      "org.scalamock" %% "scalamock" % scalaMockVersion % "test"
    )

  val integrationTest: Seq[ModuleID] = testCommon("it") ++ Seq(
      "uk.gov.hmrc"            %% "service-integration-test" % integrationTestVersion % "it",
      "com.github.tomakehurst" % "wiremock"                  % wiremockVersion        % "it"
    )

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
