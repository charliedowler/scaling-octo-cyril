package services

import play.api.libs.ws.{WSAuthScheme, WS,WSRequestHolder}

import play.api.Play.current

object AuthService {
  def authenticateRequest(url: String): WSRequestHolder = {
    return WS.url(url)
      .withAuth("username", "password", WSAuthScheme.BASIC)
      .withHeaders(("User-Agent", "webstorm"))
  }
}