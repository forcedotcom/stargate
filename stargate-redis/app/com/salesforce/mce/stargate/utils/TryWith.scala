/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.mce.stargate.utils

import java.io.Closeable

import scala.util.{Failure, Try}
import scala.util.control.NonFatal

import play.api.Logger

object TryWith {
  def logger = Logger(this.getClass)

  def apply[C <: Closeable, R](resource: => C)(f: C => R): Try[R] =
    Try(resource).flatMap(res => {
      try {
        val retVal = f(res)
        Try(res.close()).map(_ => retVal)
      } catch {
        case NonFatal(e) =>
          logger.error(e.getMessage, e)
          try {
            res.close()
            Failure(e)
          } catch {
            case NonFatal(ex) =>
              logger.error(ex.getMessage, ex)
              e.addSuppressed(ex)
              Failure(e)
          }
      }
    })
}
