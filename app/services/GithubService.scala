package services

import play.api.Logger
import play.api.libs.json.{JsArray, JsValue}
import play.api.libs.ws.{WSRequestHolder, WSAuthScheme, WS}
import scala.concurrent._
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex

object GithubService {

  def startAsyncTasks(pullrequest_event: JsValue) = {
    val comments: String = (pullrequest_event \ "pull_request" \ "comments_url").as[String]

    LabelService.labelExists("", "").onComplete({
      exists =>
        if (!exists.get) {
          LabelService.createLabel("", "", "")
        } else {
          this.fetchComments(comments)
        }
    })

  }

  def fetchComments(comments: String): Future[String] = {
    val prom = Promise[String]
    val req: WSRequestHolder = AuthService.authenticateRequest(comments)

    val approvals: Regex = """\[approve: *((@[a-z0-9]+),? *)+\]""".r

    req.get().map {
      response =>
        prom.success("tests")
        var users: Array[String] = Array()
        val comments: List[JsValue] = response.json.as[JsArray].as[List[JsValue]]
        comments
          .filter( value => approvals.findFirstIn((value \ "body").as[String])
          .isDefined)
          .map(value => (value \ "body").as[String].replace("[approve: ", "").replace("]", "").replace("@", "").trim())
          .foreach(requested => users = requested.split(",").map(s => s.trim()) ++ users)

        var isApproved = true

        for (user <- users) {
          val approved = comments.filter(value => (value \ "body").as[String].contains("+1"))
          if (approved.length >= 1) {
            for (approvee <- approved) {
              if (user != (approvee \ "user" \ "login").as[String]) {
                isApproved = false
              }
            }
          }
          else {
            isApproved = false
          }
        }

        LabelService.isLinked("", "").onComplete(isLinked => {
          val linked = isLinked.get
          if (isApproved && !linked) {
            LabelService.linkLabel("", "")
          }
          else if (!isApproved && linked) {
            LabelService.removeLabel("", "")
          }
        })
    }
    prom.future
  }
}