/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.mce.stargate.services

import scala.concurrent.Future

trait SessionTrackingService {

  def create(mid: Long, userId: Long): Future[Option[String]]

  def destroy(mid: Long, userId: Long, sid: String): Future[Boolean]

  def checkAndUpdateTimeout(mid: Long, userId: Long, sid: String): Future[Boolean]

}
