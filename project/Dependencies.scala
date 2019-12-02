import sbt._

object Dependencies {
  lazy val jwt = "com.pauldijou" %% "jwt-core" % "0.14.1"
  lazy val jedis = "redis.clients" % "jedis" % "2.9.0"
  lazy val mockito = "org.mockito" % "mockito-core" % "[2.15,3)"
  lazy val playScalaTest = "org.scalatestplus.play" %% "scalatestplus-play" % "[3.1,3.2)"
}
