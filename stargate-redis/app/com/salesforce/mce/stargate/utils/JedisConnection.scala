/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.mce.stargate.utils

import java.net.URI

import scala.collection.JavaConverters

import com.typesafe.config.ConfigFactory
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import play.api.Configuration
import redis.clients.jedis._

object JedisConnection {
  // follow the default values at
  // https://github.com/xetorthio/jedis/blob/master/src/main/java/redis/clients/jedis/BinaryJedisCluster.java#L26
  val defaultTimeoutMilis = 2000
  val defaultMaxAttempts = 5
  val config = Configuration(ConfigFactory.load())
  val clusterNodeUrls = config.get[Seq[String]]("com.salesforce.mce.stargate.redis.clusterNodeUrls")

  println(clusterNodeUrls)

  val cluster: JedisCluster = {
    val hostsAndPortsAndPasswords: Seq[(HostAndPort, Option[String])] = clusterNodeUrls.map { url =>
      val uri = new URI(url)
      val password = Option(uri.getUserInfo).map(_.split(":")(1))
      (new HostAndPort(uri.getHost, uri.getPort), password)
    }
    val clusterNodes = JavaConverters.setAsJavaSet(hostsAndPortsAndPasswords.map(_._1).toSet)
    hostsAndPortsAndPasswords(0)._2 match {
      case Some(password) => new JedisCluster(
        clusterNodes,
        defaultTimeoutMilis,
        defaultTimeoutMilis,
        defaultMaxAttempts,
        password,
        new GenericObjectPoolConfig[Connection]()
      )
      case _ => new JedisCluster(
        clusterNodes,
        defaultTimeoutMilis,
        defaultTimeoutMilis,
        defaultMaxAttempts,
        new GenericObjectPoolConfig[Connection]()
      )
    }
  }

  // Jedis cluster does not implement flushDB so this calls .flushDB on each of individual node.
  // This is a convenient method for resetting redis in tests only, not for production.
  def flushDB(): Unit = {
    clusterNodeUrls.foreach { url =>
      val result = new Jedis(new URI(url)).flushDB()
    }
  }
}
