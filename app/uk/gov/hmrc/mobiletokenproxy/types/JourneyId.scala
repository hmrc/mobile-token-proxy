/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.mobiletokenproxy.types

import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import eu.timepit.refined.string.MatchesRegex

type ValidJourneyId =
  MatchesRegex[
    "[A-Fa-f0-9]{8}\\-[A-Fa-f0-9]{4}\\-[A-Fa-f0-9]{4}\\-[A-Fa-f0-9]{4}\\-[A-Fa-f0-9]{12}"
  ]

type RefinedJourneyId = String Refined ValidJourneyId

final case class JourneyId private (value: RefinedJourneyId)

object JourneyId {
  def from(s: String): Either[String, JourneyId] =
    refineV[ValidJourneyId](s).map(JourneyId(_))

  import play.api.mvc.{PathBindable, QueryStringBindable}

  given QueryStringBindable[JourneyId] with {
    def bind(
      key: String,
      params: Map[String, Seq[String]]
    ): Option[Either[String, JourneyId]] =
      params.get(key).flatMap(_.headOption).map(from)

    def unbind(
      key: String,
      value: JourneyId
    ): String =
      value.value.value
  }

  given PathBindable[JourneyId] with {
    def bind(
      key: String,
      value: String
    ): Either[String, JourneyId] = from(value)

    def unbind(
      key: String,
      value: JourneyId
    ): String = value.value.value
  }
}
