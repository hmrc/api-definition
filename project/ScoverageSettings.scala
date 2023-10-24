import scoverage.ScoverageKeys._

object ScoverageSettings {
  def apply() = Seq(
    coverageMinimumStmtTotal := 94.00,
    coverageMinimumBranchTotal := 89.00,
    coverageFailOnMinimum := true,
    coverageHighlighting := true,

    // Semicolon-separated list of regexs matching classes to exclude
    coverageExcludedPackages := Seq(
      "<empty>",
      """.*\.controllers\.binders""",
      "com.kenshoo.play.metrics.*",
      "prod.*",
      "testOnlyDoNotUseInAppConf.*",
      "app.*",
      "uk.gov.hmrc.apidefinition.config",
      "uk.gov.hmrc.BuildInfo"
    ).mkString(";")
  )
}
