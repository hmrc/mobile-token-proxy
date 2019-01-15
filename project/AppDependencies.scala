import sbt._

object AppDependencies {
  import play.core.PlayVersion

  val compile = Seq(
    "uk.gov.hmrc" %% "bootstrap-play-26" % "0.35.0",
    "uk.gov.hmrc" %% "govuk-template"    % "5.27.0-play-26",
    "uk.gov.hmrc" %% "play-ui"           % "7.27.0-play-26",
    "uk.gov.hmrc" %% "play-hmrc-api"     % "3.4.0-play-26",
    "uk.gov.hmrc" %% "domain"            % "5.3.0"
  )

  trait TestDependencies {
    lazy val scope: String        = "test"
    lazy val test:  Seq[ModuleID] = ???
  }

  private val scalatestPlusPlayVersion = "3.1.2"

  object Test {
    def apply(): Seq[ModuleID] =
      new TestDependencies {

        override lazy val test = Seq(
          "org.scalatestplus.play" %% "scalatestplus-play"       % scalatestPlusPlayVersion % scope,
          "com.typesafe.play"      %% "play-test"                % PlayVersion.current      % scope,
          "org.scalamock"          %% "scalamock"                % "4.1.0"                  % "test",
          "org.pegdown"            % "pegdown"                   % "1.6.0"                  % scope
        )
      }.test
  }

  object IntegrationTest {
    def apply(): Seq[ModuleID] =
      new TestDependencies {

        override lazy val scope = "it"

        override lazy val test = Seq(
          "uk.gov.hmrc"            %% "service-integration-test" % "0.4.0-play-26"          % scope,
          "com.typesafe.play"      %% "play-test"                % PlayVersion.current      % scope,
          "org.scalatestplus.play" %% "scalatestplus-play"       % scalatestPlusPlayVersion % scope,
          "com.github.tomakehurst" % "wiremock"                  % "2.20.0"                 % scope,
          "org.pegdown"            % "pegdown"                   % "1.6.0"                  % scope
        )
      }.test
  }

  def apply(): Seq[ModuleID] = compile ++ Test() ++ IntegrationTest()
}
