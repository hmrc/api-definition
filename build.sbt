import play.core.PlayVersion
import play.sbt.PlayScala
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.SbtAutoBuildPlugin

import bloop.integrations.sbt.BloopDefaults

lazy val appName = "api-definition"

lazy val ComponentTest = config("component") extend Test

lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(playSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(ScoverageSettings())
  .settings(defaultSettings(): _*)
  .settings(
    name := appName,
    majorVersion := 1,
    scalaVersion := "2.12.15",
    scalacOptions += "-Ypartial-unification",
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true
    )
  .settings(inConfig(Test)(BloopDefaults.configSettings))
  .settings(
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-oDT")),
    Test / unmanagedSourceDirectories += baseDirectory.value / "test",
    Test / unmanagedSourceDirectories += baseDirectory.value / "testcommon",
    Test / unmanagedResourceDirectories += baseDirectory.value / "test" / "resources",
    addTestReportOption(Test, "test-reports")
  )
  .configs(ComponentTest)
  .settings(inConfig(ComponentTest)(Defaults.testSettings): _*)
  .settings(inConfig(ComponentTest)(BloopDefaults.configSettings))
  .settings(
    ComponentTest / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-oDT")),
    ComponentTest / unmanagedSourceDirectories += baseDirectory.value / "component",
    ComponentTest / parallelExecution := false,
    ComponentTest / fork := false,
    addTestReportOption(ComponentTest, "component-reports")
  )
  .settings(
    resolvers ++= Seq(
      Resolver.sonatypeRepo("releases")
    )
  )

def onPackageName(rootPackage: String): String => Boolean = {
  testName => testName startsWith rootPackage
}
