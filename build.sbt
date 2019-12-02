import scalariform.formatter.preferences._
import Dependencies._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt.Keys.javaOptions

val ci = sys.env.getOrElse("CI", "false").toBoolean

val testEnv = if (ci) "ci" else "test"

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
)

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
