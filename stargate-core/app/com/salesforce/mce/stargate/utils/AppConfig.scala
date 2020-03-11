package com.salesforce.mce.stargate.utils

import com.typesafe.config.ConfigFactory
import play.api.Configuration

object AppConfig {
  val config: Configuration = new Configuration(ConfigFactory.load())
  val cookieName = config.get[String]("play.http.session.cookieName")
  val secure = config.get[Boolean]("play.http.session.secure")
  val sameSite = config.get[String]("play.http.session.sameSite")
}
