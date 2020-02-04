package utils

import org.scalatestplus.play.PortNumber
import play.api.libs.ws.{WSClient, WSRequest}

trait WsScalaTestClient {

  def wsUrl(
    url:                 String
  )(implicit portNumber: PortNumber,
    wsClient:            WSClient
  ): WSRequest =
    doCall(url, wsClient, portNumber)

  private def doCall(
    url:        String,
    wsClient:   WSClient,
    portNumber: PortNumber
  ) =
    wsClient.url("http://localhost:" + portNumber.value + url)
}
