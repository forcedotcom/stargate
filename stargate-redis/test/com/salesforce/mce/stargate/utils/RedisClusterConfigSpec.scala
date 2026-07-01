/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.mce.stargate.utils

import java.net.URI

import org.scalatestplus.play.PlaySpec

class RedisClusterConfigSpec extends PlaySpec {

  "passwordOf" should {

    "extract the password from the empty-user ElastiCache AUTH form" in {
      RedisClusterConfig.passwordOf(new URI("rediss://:my-token@host:6379")) mustBe Some("my-token")
    }

    "extract the password from the user:password form" in {
      RedisClusterConfig.passwordOf(new URI("redis://user:secret@host:6379")) mustBe Some("secret")
    }

    "preserve a password that itself contains a colon" in {
      RedisClusterConfig.passwordOf(new URI("rediss://:tok:en:123@host:6379")) mustBe Some("tok:en:123")
    }

    "return None when there is no userinfo" in {
      RedisClusterConfig.passwordOf(new URI("redis://host:6379")) mustBe None
    }

    "return None (rather than throw) when userinfo has no colon" in {
      RedisClusterConfig.passwordOf(new URI("redis://tokenonly@host:6379")) mustBe None
    }
  }

  "parse" should {

    "detect TLS from a rediss:// scheme" in {
      val parsed = RedisClusterConfig.parse(Seq("rediss://:token@h1:6379"))
      parsed.useSsl mustBe true
      parsed.password mustBe Some("token")
    }

    "leave TLS off for a plain redis:// scheme (backward compatibility)" in {
      val parsed = RedisClusterConfig.parse(Seq("redis://h1:6379"))
      parsed.useSsl mustBe false
      parsed.password mustBe None
    }

    "collect every node into the host set" in {
      val parsed = RedisClusterConfig.parse(
        Seq("rediss://:t@h1:6379", "rediss://:t@h2:6380", "rediss://:t@h3:6381")
      )
      parsed.nodes.map(hp => (hp.getHost, hp.getPort)) mustBe
        Set(("h1", 6379), ("h2", 6380), ("h3", 6381))
    }

    "accept a uniformly rediss:// cluster" in {
      val parsed = RedisClusterConfig.parse(Seq("rediss://:t@h1:6379", "rediss://:t@h2:6380"))
      parsed.useSsl mustBe true
    }

    "reject a cluster that mixes redis:// and rediss:// schemes" in {
      val thrown = the[IllegalArgumentException] thrownBy {
        RedisClusterConfig.parse(Seq("rediss://:t@h1:6379", "redis://h2:6380"))
      }
      thrown.getMessage must include("same scheme")
    }
  }
}
