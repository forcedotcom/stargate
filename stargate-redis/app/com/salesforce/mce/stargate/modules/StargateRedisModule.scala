/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.mce.stargate.modules

import com.google.inject.AbstractModule

import com.salesforce.mce.stargate.services.SessionTrackingService
import com.salesforce.mce.stargate.services.impl.SessionTrackingServiceRedisImpl

class StargateRedisModule extends AbstractModule {

  override def configure = {
    bind(classOf[SessionTrackingService]).to(classOf[SessionTrackingServiceRedisImpl])
    ()
  }

}
