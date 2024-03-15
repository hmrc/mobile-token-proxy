import sbt._

object AppDependencies {

  private val bootstrapPlayVersion = "7.19.0"
  private val playHmrcApiVersion   = "7.2.0-play-28"
  private val domainVersion        = "8.1.0-play-28"
  private val flexmarkAllVersion   = "0.36.8"

  private val scalaMockVersion = "4.4.0"
  private val pegdownVersion   = "1.6.0"
  private val wiremockVersion  = "2.27.2"
  private val refinedVersion   = "0.9.26"

  lazy val appDependencies: Seq[ModuleID] =
    compile ++ test ++ integrationTest

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-frontend-play-28" % bootstrapPlayVersion,
    "uk.gov.hmrc" %% "play-hmrc-api"              % playHmrcApiVersion,
    "uk.gov.hmrc" %% "domain"                     % domainVersion,
    "eu.timepit"  %% "refined"                    % refinedVersion
  )

  private def testCommon(scope: String) = Seq(
    "org.pegdown"          % "pegdown"                 % pegdownVersion       % scope,
    "com.vladsch.flexmark" % "flexmark-all"            % flexmarkAllVersion   % scope,
    "uk.gov.hmrc"          %% "bootstrap-test-play-28" % bootstrapPlayVersion % scope
  )

  val test: Seq[ModuleID] = testCommon("test") ++ Seq(
      "org.scalamock" %% "scalamock" % scalaMockVersion % "test"
    )

  val integrationTest: Seq[ModuleID] = testCommon("it") ++ Seq(
      "com.github.tomakehurst" % "wiremock" % wiremockVersion % "it"
    )

}
