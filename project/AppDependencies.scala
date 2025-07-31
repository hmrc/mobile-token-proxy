import sbt._

object AppDependencies {

  private val bootstrapPlayVersion = "9.18.0"
  private val playHmrcApiVersion   = "8.2.0"
  private val domainVersion        = "12.1.0"

  private val scalaMockVersion = "7.4.0"
  private val refinedVersion   = "0.11.3"

  lazy val appDependencies: Seq[ModuleID] =
    compile ++ test ++ integrationTest

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-frontend-play-30" % bootstrapPlayVersion,
    "uk.gov.hmrc" %% "play-hmrc-api-play-30"      % playHmrcApiVersion,
    "uk.gov.hmrc" %% "domain-play-30"             % domainVersion,
    "eu.timepit"  %% "refined"                    % refinedVersion
  )

  private def testCommon(scope: String) = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapPlayVersion % scope
  )

  val test: Seq[ModuleID] = testCommon("test") ++ Seq(
    "org.scalamock" %% "scalamock" % scalaMockVersion % "test"
  )

  val integrationTest: Seq[ModuleID] = testCommon("it")

}
