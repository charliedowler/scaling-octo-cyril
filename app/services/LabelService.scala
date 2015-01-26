package services


import play.api.libs.json.JsObject
import play.api.libs.ws.{WSAuthScheme, WS, WSRequestHolder}

import scala.concurrent.{Promise, Future}

import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global

object LabelService {

  def labelExists(path: String, name: String): Future[Boolean] = {
    val promise = Promise[Boolean]

    AuthService.authenticateRequest("https://api.github.com/repos/charliedowler/scaling-octo-cyril/labels/approved")
      .get().map {
      request =>
        promise.success(if (request.status == 200) true else false)
    }

    return promise.future
  }

  def createLabel(path: String, name: String, colour: String): Future[Any] = {
    val promise = Promise[Any]

    AuthService.authenticateRequest("https://api.github.com/repos/charliedowler/scaling-octo-cyril/labels")
      .post("{\"name\": \"approved\", \"color\": \"199c4b\"}").map {
      request =>
        promise.success()
    }

    return promise.future
  }

  def isLinked(path: String, name: String): Future[Boolean] = {
    val promise = Promise[Boolean]

    AuthService.authenticateRequest("https://api.github.com/repos/charliedowler/scaling-octo-cyril/issues/1/labels")
      .get().map {
      request =>
        promise.success(request.json.as[List[JsObject]].exists(obj => {
          (obj \ "name").as[String] == "approved"
        }))
    }
    return promise.future
  }

  def linkLabel(path: String, name: String): Future[Any] = {
    val promise = Promise[Any]
    AuthService.authenticateRequest("https://api.github.com/repos/charliedowler/scaling-octo-cyril/issues/1/labels")
      .post("[\"approved\"]").map {
      request =>
        promise.success()
    }
    return promise.future
  }

  def removeLabel(path: String, name: String): Future[Any] = {
    val promise = Promise[Any]

    AuthService.authenticateRequest("https://api.github.com/repos/charliedowler/scaling-octo-cyril/issues/1/labels/approved")
      .delete().map {
      request =>
        promise.success()
    }

    return promise.future
  }
}