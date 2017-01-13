/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.mobiletokenproxy.controllers

trait StubApplicationConfiguration {

  val config = Map[String, Any](
    "auditing.enabled" -> false,
    "appName" -> "api-gateway-proxy",
    "client_id" -> "some_client_id",
    "client_secret" -> "some_client_secret",
    "redirect_uri" -> "urn:ietf:wg:oauth:2.0:oob:auto",
    "grant_type" -> "authorization_code",
    "pathToAPIGatewayTokenService" -> "http://localhost:8236/oauth/token",
    "pathToAPIGatewayAuthService" -> "http://localhost:8236/oauth/authorize",
    "scope" -> "read:personal-income+read:customer-profile+read:messages+read:submission-tracker+read:web-session",
    "response_type" -> "code"
  )
}
