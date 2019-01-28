import _root_.play.core.PlayVersion
import _root_.play.sbt.PlayImport._
import _root_.play.sbt.PlayScala
import play.routes.compiler.StaticRoutesGenerator
import play.sbt.routes.RoutesKeys.routesGenerator
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

lazy val appName = "api-definition"

lazy val appDependencies: Seq[ModuleID] = compile ++ test

lazy val compile = Seq(
  ws,
  "uk.gov.hmrc" %% "microservice-bootstrap" % "9.1.0",
  "uk.gov.hmrc" %% "play-reactivemongo" % "6.2.0",
  "uk.gov.hmrc" %% "mongo-lock" % "5.1.0",
  "uk.gov.hmrc" %% "play-json-union-formatter" % "1.3.0",
  "org.typelevel" %% "cats-core" % "1.1.0"
)

lazy val test = Seq(
  "uk.gov.hmrc" %% "reactivemongo-test" % "3.1.0" % "test",
  "uk.gov.hmrc" %% "hmrctest" % "3.3.0" % "test",
  "org.scalaj" %% "scalaj-http" % "2.3.0" % "test",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test, component",
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % "test",
  "org.mockito" % "mockito-core" % "2.13.0" % "test",
  "com.typesafe.play" %% "play-test" % PlayVersion.current % "test",
  "com.github.tomakehurst" % "wiremock" % "2.8.0" % "test"
)

lazy val ComponentTest = config("component") extend Test
val testConfig = Seq(ComponentTest, Test)

lazy val plugins: Seq[Plugins] = Seq(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val microservice = (project in file("."))
  .enablePlugins(plugins: _*)
  .settings(playSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .configs(testConfig: _*)
  .settings(
    name := appName,
    majorVersion := 1,
    targetJvm := "jvm-1.8",
    scalaVersion := "2.11.11",
    scalacOptions += "-Ypartial-unification",
    libraryDependencies ++= appDependencies,
    retrieveManaged := true,
    routesGenerator := StaticRoutesGenerator
  )
  .settings(
    unitTestSettings,
    componentTestSettings
  )
  .settings(
    resolvers += Resolver.bintrayRepo("hmrc", "releases"),
    resolvers += Resolver.jcenterRepo)
  .settings(ivyScala := ivyScala.value map {
    _.copy(overrideScalaVersion = true)
  })

lazy val unitTestSettings =
  inConfig(Test)(Defaults.testTasks) ++
    Seq(
      testOptions in Test := Seq(Tests.Filter(onPackageName("unit"))),
      testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
      unmanagedSourceDirectories in Test := Seq((baseDirectory in Test).value / "test"),
      addTestReportOption(Test, "test-reports")
    )

lazy val componentTestSettings =
  inConfig(ComponentTest)(Defaults.testTasks) ++
    Seq(
      testOptions in ComponentTest := Seq(Tests.Filter(onPackageName("component"))),
      testOptions in ComponentTest += Tests.Argument(TestFrameworks.ScalaTest, "-oD"),
      fork in ComponentTest := false,
      parallelExecution in ComponentTest := false,
      addTestReportOption(ComponentTest, "component-reports")
    )

def onPackageName(rootPackage: String): String => Boolean = {
  testName => testName startsWith rootPackage
}

// Coverage configuration
coverageMinimum := 93
coverageFailOnMinimum := true
coverageExcludedPackages := "<empty>;com.kenshoo.play.metrics.*;prod.*;testOnlyDoNotUseInAppConf.*;app.*;uk.gov.hmrc.BuildInfo"
