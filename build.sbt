import play.sbt.PlayScala
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.SbtAutoBuildPlugin

import bloop.integrations.sbt.BloopDefaults

lazy val appName = "api-definition"
scalaVersion := "2.13.8"

lazy val ComponentTest = config("component") extend Test

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
inThisBuild(
    List(
      semanticdbEnabled := true,
      semanticdbVersion := scalafixSemanticdb.revision
  )
)
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(playSettings: _*)
  .settings(scalaSettings: _*)
  .settings(ScoverageSettings())
  .settings(defaultSettings(): _*)
  .settings(
    name := appName,
    majorVersion := 1,
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
  .settings(
      routesImport ++= Seq(
      "uk.gov.hmrc.apidefinition.controllers.binders._",
      "uk.gov.hmrc.apiplatform.modules.common.domain.models._",
      "uk.gov.hmrc.apiplatform.modules.apis.domain.models._"
    )
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

def onPackageName(rootPackage: String): String => Boolean = {
  testName => testName startsWith rootPackage
}

Global / bloopAggregateSourceDependencies := true
