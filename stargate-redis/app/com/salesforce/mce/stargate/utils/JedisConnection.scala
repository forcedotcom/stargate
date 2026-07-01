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

/**
 * Pure parsing of the configured redis cluster node URLs. Kept separate from
 * [[JedisConnection]] so that it can be unit-tested without triggering the
 * eager live connection that `JedisConnection.cluster` opens at class init.
 */
object RedisClusterConfig {

  /** Parsed connection settings shared across every node of a cluster client. */
  case class ClusterConfig(
    nodes: Set[HostAndPort],
    useSsl: Boolean,
    password: Option[String]
  )

  // Extracts the password from a redis URI's userinfo. Userinfo is of the form
  // `user:password` (ElastiCache AUTH-token URLs use the empty-user form
  // `rediss://:token@host`). Split with a limit of 2 so that a password
  // containing ':' is preserved, and tolerate userinfo with no ':' at all
  // (returns None) rather than throwing.
  def passwordOf(uri: URI): Option[String] =
    Option(uri.getUserInfo).flatMap(_.split(":", 2) match {
      case Array(_, password) => Some(password)
      case _                  => None
    })

  def parse(urls: Seq[String]): ClusterConfig = {
    val hostsAndPortsAndPasswords: Seq[(HostAndPort, Option[String], Boolean)] = urls.map { url =>
      val uri = new URI(url)
      val password = passwordOf(uri)
      val ssl = uri.getScheme == "rediss"
      (new HostAndPort(uri.getHost, uri.getPort), password, ssl)
    }

    // All node URLs must agree on the scheme: a single JedisCluster client config
    // is shared across every node, so a mixed redis://+rediss:// config would
    // silently apply the head node's TLS setting to all of them. Fail fast
    // instead of producing an opaque plaintext-vs-TLS mismatch at runtime.
    val distinctSchemes = hostsAndPortsAndPasswords.map(_._3).distinct
    require(
      distinctSchemes.size <= 1,
      "com.salesforce.mce.stargate.redis.clusterNodeUrls mixes TLS (rediss://) and " +
        "non-TLS (redis://) schemes; all cluster nodes must use the same scheme."
    )

    ClusterConfig(
      nodes = hostsAndPortsAndPasswords.map(_._1).toSet,
      useSsl = hostsAndPortsAndPasswords.headOption.exists(_._3),
      password = hostsAndPortsAndPasswords.headOption.flatMap(_._2)
    )
  }
}

object JedisConnection {
  val defaultTimeoutMilis = 2000
  val defaultMaxAttempts = 5
  val config = Configuration(ConfigFactory.load())
  val clusterNodeUrls = config.get[Seq[String]]("com.salesforce.mce.stargate.redis.clusterNodeUrls")

  val cluster: JedisCluster = {
    val parsed = RedisClusterConfig.parse(clusterNodeUrls)

    val clientConfig = DefaultJedisClientConfig.builder()
      .connectionTimeoutMillis(defaultTimeoutMilis)
      .socketTimeoutMillis(defaultTimeoutMilis)
      .ssl(parsed.useSsl)

    parsed.password.foreach { password =>
      clientConfig.password(password)
    }

    new JedisCluster(
      JavaConverters.setAsJavaSet(parsed.nodes),
      clientConfig.build(),
      defaultMaxAttempts,
      new GenericObjectPoolConfig[Connection]()
    )
  }

  // For resetting redis in tests only, not for production use.
  def flushDB(): Unit = {
    clusterNodeUrls.foreach { url =>
      // new Jedis(URI) derives both the TLS setting (from a rediss:// scheme)
      // and the AUTH token (from the URI userinfo) on its own, so no manual
      // ssl/password wiring is needed here.
      val jedis = new Jedis(new URI(url))
      try {
        jedis.flushDB()
      } finally {
        jedis.close()
      }
    }
  }
}
