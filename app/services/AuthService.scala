package services

import play.api.libs.ws.{WSAuthScheme, WS,WSRequestHolder}

import play.api.Play.current

object AuthService {
  def authenticateRequest(url: String): WSRequestHolder = {
    return WS.url(url)
      .withAuth(System.getenv("github_user"), System.getenv("github_pat"), WSAuthScheme.BASIC)
      .withHeaders(("User-Agent", "webstorm"))
  }
}