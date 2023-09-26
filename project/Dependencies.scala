import sbt._

object Dependencies {
  lazy val jwt = "com.pauldijou" %% "jwt-core" % "5.0.0"
  lazy val jedis = "redis.clients" % "jedis" % "5.0.1"
  lazy val mockito = "org.mockito" % "mockito-core" % "5.5.0"
  lazy val playScalaTest = "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0"
}
