import scalariform.formatter.preferences._
import Dependencies._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt.Keys.javaOptions

val ci = sys.env.getOrElse("CI", "false").toBoolean

val testEnv = if (ci) "ci" else "test"

lazy val publishSettings = Seq(
  organization := "com.salesforce.mce",
  organizationName := "Salesforce Marketing Cloud Einstein",
  organizationHomepage := Some(url("https://www.salesforce.com")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/forcedotcom/stargate"),
      "scm:git@github.com:forcedotcom/stargate.git"
    )
  ),
  developers := List(
    Developer(
      id    = "sushengloong",
      name  = "Sheng-Loong Su",
      email = "shengloong.su@salesforce.com",
      url   = url("http://github.com/sushengloong")
    ),
    Developer(
      id    = "",
      name  = "Trent Albright",
      email = "talbright@salesforce.com",
      url   = url("https://github.com/talbright")
    ),
    Developer(
      id    = "realstraw",
      name  = "Kexin Xie",
      email = "kexin.xie@salesforce.com",
      url   = url("http://github.com/realstraw")
    )
  ),
  licenses := List("BSD-3-Clause" -> new URL("https://opensource.org/licenses/BSD-3-Clause")),
  homepage := Some(url("https://github.com/forcedotcom/stargate")),
  pomIncludeRepository := { _ => false },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
    else Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  publishMavenStyle := true,
  credentials += Credentials(
    "Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    sys.env.getOrElse("SONATYPE_USERNAME",""),
    sys.env.getOrElse("SONATYPE_PASSWORD","")
  )
)

lazy val commonSettings = Seq(
  headerLicense := Some(HeaderLicense.Custom(
    """|Copyright (c) 2018, salesforce.com, inc.
       |All rights reserved.
       |SPDX-License-Identifier: BSD-3-Clause
       |For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
       |""".stripMargin
  )),
  libraryDependencies ++= Seq(
    guice,
    jwt,
    mockito % Test,
    playScalaTest % Test
  ),
  scalariformAutoformat := true,
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(MultilineScaladocCommentsStartOnFirstLine, true)
    .setPreference(NewlineAtEndOfFile, true)
    .setPreference(SingleCasePatternOnNewline, false)
    .setPreference(SpacesWithinPatternBinders, false)
    .setPreference(SpacesAroundMultiImports, false)
    .setPreference(DanglingCloseParenthesis, Force),
  scalaVersion := "2.12.4",
  scalacOptions ++= Seq(
    "-encoding", "UTF-8",
    "-unchecked",
    "-Xfuture",
    "-Xlint:-unused,_",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Ywarn-infer-any"
  ),
  doc in Compile := target.map(_ / "none").value
) ++ publishSettings

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(name := "stargate").
  aggregate(core, redis)

lazy val core = (project in file("stargate-core")).
  settings(commonSettings: _*).
  enablePlugins(PlayScala).
  settings(
  name := "stargate-core",
    description := "Scala Play module for integrating Marketing Cloud Single Sign On",

    javaOptions += "-Dconfig.resource=stargate-core.application.conf",

    javaOptions in Test ++= Seq(
      "-Dconfig.resource=stargate-core.test.conf",
      "-Dlogger.resource=logback-test.xml"
    )
  )

lazy val redis = (project in file("stargate-redis")).
  settings(commonSettings: _*).
  enablePlugins(PlayScala).
  settings(
    name := "stargate-redis",
    description := "Stargate Scala Play module with Redis as session store",

    libraryDependencies ++= Seq(
      jedis
    ),
    javaOptions in Test ++= Seq(
      s"-Dconfig.resource=stargate-redis.${testEnv}.conf",
      "-Dlogger.resource=logback-test.xml"
    )
  ).
  dependsOn(core)
