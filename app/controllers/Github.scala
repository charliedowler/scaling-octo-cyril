package controllers

import play.api.Logger
import play.api.libs.ws.{WSResponse, WSRequestHolder, WS}
import play.api.mvc._
import play.libs.F.Promise
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import services.GithubService

import scala.concurrent.Future
import scala.util.{Success, Failure}


object Github extends Controller {

  def poll = Action(parse.json) { request =>
    val EventType = request.headers.get("X-Github-Delivery").getOrElse("test")

    val action: String = (request.body \ "action").toString()

    if (action.equals("closed")) {
      Ok("Pull request was closed")
    }

    GithubService.startAsyncTasks(request.body)

    Logger.debug("Poll received from Github")
    Ok("Hello world")
  }

  def confirmation = Action(parse.json) { request =>

    val hook_id = request.headers.get("hook_id")

    // Store hooke id

    Ok("Hello world")
  }

  def subscribe = Action { request =>
    Ok("")
  }

}