import sbt._

object Dependencies {
  lazy val jwt = "com.pauldijou" %% "jwt-core" % "5.0.0"
  lazy val jedis = "redis.clients" % "jedis" % "4.2.3"
  lazy val mockito = "org.mockito" % "mockito-core" % "[4.6.1,5)"
  lazy val playScalaTest = "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0"
}
