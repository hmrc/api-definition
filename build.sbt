import _root_.play.core.PlayVersion
import _root_.play.sbt.PlayImport._
import _root_.play.sbt.PlayScala
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import bloop.integrations.sbt.BloopDefaults

lazy val appName = "api-definition"

lazy val appDependencies: Seq[ModuleID] = compile ++ test


lazy val compile = Seq(
  ws,
  "uk.gov.hmrc" %% "bootstrap-play-26" % "2.1.0",
  "uk.gov.hmrc" %% "simple-reactivemongo" % "7.30.0-play-26",
  "uk.gov.hmrc" %% "play-json-union-formatter" % "1.12.0-play-26",
  "org.typelevel" %% "cats-core" % "1.1.0",
  "uk.gov.hmrc" %% "raml-tools" % "1.18.0",
  "org.raml" % "raml-parser-2" % "1.0.13",
  "com.beachape" %% "enumeratum-play-json" % "1.6.0"
)

lazy val test = Seq(
  "uk.gov.hmrc" %% "reactivemongo-test" % "4.21.0-play-26" % "test",
  "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-26" % "test",
  "org.scalaj" %% "scalaj-http" % "2.4.1" % "test",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test, component",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % "test",
  "org.mockito" % "mockito-core" % "2.13.0" % "test",
  "com.typesafe.play" %% "play-test" % PlayVersion.current % "test",
  "com.github.tomakehurst" % "wiremock" % "1.58" % "test",
  "de.leanovate.play-mockws" %% "play-mockws" % "2.6.6" % "test"
)

lazy val ComponentTest = config("component") extend Test
val testConfig = Seq(ComponentTest, Test)

lazy val plugins: Seq[Plugins] = Seq(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val microservice = (project in file("."))
  .enablePlugins(plugins: _*)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(playSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .configs(testConfig: _*)
  .settings(
    name := appName,
    majorVersion := 1,
    targetJvm := "jvm-1.8",
    scalaVersion := "2.12.12",
    scalacOptions += "-Ypartial-unification",
    libraryDependencies ++= appDependencies,
    retrieveManaged := true
  )
  .settings(
    unitTestSettings,
    componentTestSettings
  )
  .settings(
    resolvers ++= Seq(
      Resolver.sonatypeRepo("releases")
    ),
    resolvers += "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/"
  )

lazy val unitTestSettings =
  inConfig(Test)(BloopDefaults.configSettings) ++
  inConfig(Test)(Defaults.testTasks) ++
    Seq(
      testOptions in Test := Seq(Tests.Filter(onPackageName("unit"))),
      testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDT"),
      unmanagedSourceDirectories in Test := Seq((baseDirectory in Test).value / "test"),
      addTestReportOption(Test, "test-reports")
    )

lazy val componentTestSettings =
  inConfig(ComponentTest)(BloopDefaults.configSettings) ++
  inConfig(ComponentTest)(Defaults.testTasks) ++
    Seq(
      testOptions in ComponentTest := Seq(Tests.Filter(onPackageName("component"))),
      testOptions in ComponentTest += Tests.Argument(TestFrameworks.ScalaTest, "-oDT"),
      fork in ComponentTest := false,
      parallelExecution in ComponentTest := false,
      addTestReportOption(ComponentTest, "component-reports")
    )

def onPackageName(rootPackage: String): String => Boolean = {
  testName => testName startsWith rootPackage
}

// Coverage configuration
coverageMinimum := 90
coverageFailOnMinimum := true
coverageExcludedPackages := "<empty>;com.kenshoo.play.metrics.*;prod.*;testOnlyDoNotUseInAppConf.*;app.*;uk.gov.hmrc.BuildInfo;uk.gov.hmrc.apidefinition.config.*"
