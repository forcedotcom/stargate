package com.salesforce.mce.stargate.services.impl

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration

import com.salesforce.mce.stargate.utils.JedisConnection

class SessionTrackingServiceRedisImplSpec extends PlaySpec with GuiceOneAppPerSuite with BeforeAndAfterEach {

  def flushRedisDb() = { JedisConnection.flushDB() }
  def exec[T](future: Future[T]): T = Await.result(future, 2.seconds)

  override protected def afterEach(): Unit = {
    super.afterEach()
    val _ = flushRedisDb()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    val _ = flushRedisDb()
  }

  val config = Configuration(ConfigFactory.load())
  val mid = 10773761L
  val userId = 11012446L
  val sid = "fake-sid-uuid"
  val sessionTimeoutInSeconds = config.get[FiniteDuration]("play.http.session.maxAge").toSeconds.toInt
  val jedis = JedisConnection.cluster

  def createService() = new SessionTrackingServiceRedisImpl(config)

  "create" should {

    "return a UUID" in {
      val service = createService()
      val sidOption = exec(service.create(mid, userId))
      sidOption.isDefined mustBe true
      sidOption.get.length mustBe 36
    }

    "set value in redis" in {
      val service = new SessionTrackingServiceRedisImpl(config)
      val sidOption = exec(service.create(mid, userId))
      val key = service.keyFor(mid, userId, sidOption.get)
      jedis.get(key) mustBe service.keyExists
    }

    "return unique session ID" in {
      val service = new SessionTrackingServiceRedisImpl(config)
      val sids = 1.to(100).map(_ => exec(service.create(mid, userId)).get)
      sids.length mustBe sids.distinct.length
    }

  }

  "destroy" should {

    "return true when the session exists in redis" in {
      val service = createService()
      // precondition -- session must be set in redis first
      val key = service.keyFor(mid, userId, sid)
      jedis.setex(key, sessionTimeoutInSeconds, "1") mustBe service.redisStatusOk
      // destroy
      exec(service.destroy(mid, userId, sid)) mustBe true
    }

    "delete key from redis" in {
      val service = createService()
      // precondition -- session must be set in redis first
      val key = service.keyFor(mid, userId, sid)
      jedis.setex(key, sessionTimeoutInSeconds, "1") mustBe service.redisStatusOk
      // destroy
      exec(service.destroy(mid, userId, sid))
      jedis.get(key) mustBe null
    }

  }

  "checkAndUpdateTimeout" should {

    "return true when the session exists in redis" in {
      val service = createService()
      // precondition -- session must be set in redis first
      val key = service.keyFor(mid, userId, sid)
      jedis.setex(key, sessionTimeoutInSeconds, "1") mustBe service.redisStatusOk
      exec(service.checkAndUpdateTimeout(mid, userId, sid)) mustBe true
    }

    "extend timeout" in {
      val service = createService()
      // precondition -- session must be set in redis first
      val key = service.keyFor(mid, userId, sid)
      // Deliberately set shorter initial timeout to simulate the previous session set.
      val initialTimeout = sessionTimeoutInSeconds - 60
      jedis.setex(key, initialTimeout, "1") mustBe service.redisStatusOk
      exec(service.checkAndUpdateTimeout(mid, userId, sid)) mustBe true
      // Verify that session timeout has been extended beyond the initial timeout.
      jedis.ttl(key) > initialTimeout mustBe true
    }

    "return false when the session does not exist in redis" in {
      exec(createService().checkAndUpdateTimeout(mid, userId, sid)) mustBe false
    }
  }

  "keyFor" should {

    "return the key to be set in redis" in {
      val service = createService()
      val key = service.keyFor(mid, userId, sid)
      key mustBe s"${service.keyPrefix}:$mid:$userId:$sid"
    }

  }

}
