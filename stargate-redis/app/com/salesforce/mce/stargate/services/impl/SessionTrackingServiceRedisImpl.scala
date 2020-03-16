/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.mce.stargate.services.impl

import java.util.UUID
import javax.inject.Inject

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

import play.api.{Configuration, Logger}

import com.salesforce.mce.stargate.services.SessionTrackingService
import com.salesforce.mce.stargate.utils.JedisConnection

class SessionTrackingServiceRedisImpl @Inject() (config: Configuration)(implicit ec: ExecutionContext)
  extends SessionTrackingService {

  val keyPrefix = "session"
  val keyExists = "1"
  val redisStatusOk = "OK"

  val logger = Logger(this.getClass())
  val sessionTimeoutInSeconds = config.get[FiniteDuration]("play.http.session.maxAge").toSeconds.toInt
  val jedisCluster = JedisConnection.cluster

  override def create(mid: Long, userId: Long): Future[Option[String]] = {
    val sid = UUID.randomUUID().toString
    val key = keyFor(mid, userId, sid)

    logger.info(s"""Setting key "$key" in redis.""")
    val status = jedisCluster.setex(key, sessionTimeoutInSeconds, keyExists)
    logger.info(s"""Status reply when setting key "$key": $status""")
    if (status == redisStatusOk) {
      logger.info(s"""Successfully created session for "$key" in redis.""")
      Future.successful(Some(sid))
    } else {
      logger.error(s"""Failed to create session for key "$key". Invalid status "$status returned."""")
      Future.successful(None)
    }
  }

  override def destroy(mid: Long, userId: Long, sid: String): Future[Boolean] = {
    val key = keyFor(mid, userId, sid)

    logger.info(s"""Deleting key "$key" in redis.""")
    val status = jedisCluster.del(key)
    logger.info(s"""Status reply when deleting key "$key": $status""")
    if (status > 0) {
      logger.info(s"""Successfully cleared session for key "$key".""")
      Future.successful(true)
    } else {
      logger.info(s"""No session was cleared for key "$key".""")
      Future.successful(false)
    }
  }

  override def checkAndUpdateTimeout(mid: Long, userId: Long, sid: String): Future[Boolean] = {
    val key = keyFor(mid, userId, sid)

    logger.info(s"""Retrieving key "$key" from redis.""")
    val value = jedisCluster.get(key)
    logger.info(s"""Value retrieved for key "$key": $value""")
    if (value == keyExists) {
      // extend timeout in redis
      logger.info(s"""Updating expiration for "$key" in redis.""")
      val expireStatus = jedisCluster.expire(key, sessionTimeoutInSeconds)
      logger.info(s"""Status reply when updating expiration for "$key": $expireStatus""")
      if (expireStatus != 1) {
        // Do not raise exception because failure of updating TTL does not mean that
        // the session is invalid
        logger.error(s"""Invalid status "$expireStatus" when updating TTL for key "$key".""")
      }
      logger.info(s"""Successfully checked and updated session for key "$key".""")
      // The session is valid irrespective of status of updating TTL in redis
      Future.successful(true)
    } else {
      logger.error(s"""Failed to check and update session for key "$key". Invalid value "$value" retrieved.""")
      Future.successful(false)
    }
  }

  /** @param mid MC MID / Account ID
   *  @param userId MC User ID
   *  @param sid unique 36-character UUID
   *  @return key associated with the user's session stored in Redis, eg.
   *         "session:8214177:10901756:2d8643f3-e4c3-47b0-a66c-f9af1af6b21a"
   *
   *  Such key format allows easy retrieval and invalidation for
   *  1) a session
   *  2) all sessions for a user
   *  3) all sessions for all users of an organization
   *
   *  The MID and User ID stored in Redis are mostly for audit purposes.
   *  As far as session is concerned, Stargate firstly reads MID and User ID
   *  from user's cookie (see StargateAuthAction.scala) and then only checks
   *  against Redis to ensure that the server-side session is valid.
   *
   */
  def keyFor(mid: Long, userId: Long, sid: String): String =
    s"$keyPrefix:$mid:$userId:$sid"

}
