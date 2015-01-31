package services

import play.api.libs.json.{JsArray, JsValue}
import play.api.libs.ws.WSRequestHolder
import scala.concurrent.{Promise, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex

object GithubService {

  val approved = "approved"

  def startAsyncTasks(pullrequest_event: JsValue) = {
    val labels_url = (pullrequest_event \ "repository" \ "labels_url").as[String]
    val comments_url = (pullrequest_event \ "pull_request" \ "comments_url").as[String]
    val issue = (pullrequest_event \ "pull_request" \ "_links" \ "issue" \ "href").as[String]

    LabelService.labelExists(labels_url.replace("{/name}", "/" + approved)).onComplete({
      exists =>
        if (!exists.get) {
          LabelService.createLabel(labels_url.replace("{/name}", ""), approved, "199c4b").onComplete(value => this.fetchComments(comments_url, issue + "/labels"))
        } else {
          this.fetchComments(comments_url, issue + "/labels")
        }
    })

  }

  def findApprovals(comments: List[JsValue]): List[JsValue] = {
    comments.filter(value => (value \ "body").as[String].contains("+1"))
  }

  def findUsers(comments: List[JsValue]): Array[String] = {
    val approvals: Regex = """\[approve: *((@[a-z0-9]+),? *)+\]""".r
    var users: Array[String] = Array()

    comments
      .filter( value => approvals.findFirstIn((value \ "body").as[String])
      .isDefined)
      .map(value => (value \ "body").as[String].replace("[approve: ", "").replace("]", "").replace("@", "").trim())
      .foreach(requested => users = requested.split(",").map(s => s.trim()) ++ users)

    users
  }

  def fetchComments(comments: String, labels: String): Future[String] = {
    val prom = Promise[String]
    val req: WSRequestHolder = AuthService.authenticateRequest(comments)

    req.get().map {
      response =>
        prom.success("tests")
        val comments: List[JsValue] = response.json.as[JsArray].as[List[JsValue]]
        val users = findUsers(comments)

        var isApproved = true

        for (user <- users) {
          val approved = findApprovals(comments)
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

        LabelService.isLinked(labels, approved).onComplete(isLinked => {
          val linked = isLinked.get
          if (isApproved && !linked) {
            LabelService.linkLabel(labels, approved)
          }
          else if (!isApproved && linked) {
            LabelService.removeLabel(labels + "/" + approved)
          }
        })
    }
    prom.future
  }
}