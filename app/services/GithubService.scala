package services

import play.api.libs.json.{JsValue, JsArray}
import scala.util.Success
import play.api.libs.ws.WSRequestHolder
import scala.concurrent.{Promise, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex
import play.Play.{application}

object GithubService {

  val approved: String = application().configuration().getString("cyril.label")
  val color: String = application().configuration().getString("cyril.color")
  val ready: String = application().configuration().getString("cyril.ready")

  def startAsyncTasks(pullrequest_event: JsValue) = {
    val labels_url = (pullrequest_event \ "repository" \ "labels_url").as[String]

    var comments_url: String = null
    var issue: String = null
    var body: String = null

    try {
      comments_url = (pullrequest_event \ "issue" \ "comments_url").as[String]
      issue = (pullrequest_event \ "issue" \ "url").as[String]
      body = (pullrequest_event \ "issue" \ "body").as[String]
    }
    catch {
      case e: Exception => {
        comments_url = (pullrequest_event \ "pull_request" \ "comments_url").as[String]
        issue = (pullrequest_event \ "pull_request" \ "_links" \ "issue" \ "href").as[String]
        body = (pullrequest_event \ "pull_request" \ "body").as[String]
      }
    }

    LabelService.labelExists(labels_url.replace("{/name}", "/" + approved)).onComplete({
      exists =>
        val labels = issue + "/labels"
        if (!exists.get) {
          LabelService.createLabel(labels_url.replace("{/name}", ""), approved, color).onComplete(value => {
            this.fetchComments(comments_url).onComplete {
              case Success(comments: List[JsValue]) => {
                handleApproval(body, comments, labels)
              }
            }
          })
        } else {
          this.fetchComments(comments_url).onComplete {
            case Success(comments: List[JsValue]) => {
              handleApproval(body, comments, labels)
            }
          }
        }
    })

  }

  def findApprovals(comments: List[JsValue]): List[JsValue] = {
    comments.filter(value => (value \ "body").as[String].contains(ready))
  }

  def extractUsersFromText(body: String): String = {
    val approvals: Regex = """\[approve: *((@[a-z0-9]+),? *)+\]""".r
    val parsed = approvals.findFirstIn(body.toCharArray)

    if (parsed.isDefined) {
      parsed.get
    }
    else {
      null
    }
  }

  def findUsers(body: String, comments: List[JsValue]): Array[String] = {
    var users: Array[String] = Array()
    val clean = (s: String) => s.replace("[approve: ", "").replace("]", "").replace("@", "").trim()

    val parsed = extractUsersFromText(body)
    if (parsed != null) {
      users = parsed.split(",").map(clean) ++ users
    }
    comments
      .filter(value => extractUsersFromText((value \ "body").as[String]) != null)
      .map(value => clean(extractUsersFromText((value \ "body").as[String])))
      .foreach(requested => users = requested.split(",").map(s => s.trim()) ++ users)

    users
  }

  def issueHasBeenApproved(users: Array[String], comments: List[JsValue]): Boolean = {
    var hasBeenApproved = true
    for (user <- users) {
      val approved = findApprovals(comments)
      if (approved.length >= 1) {
        for (approvee <- approved) {
          if (user != (approvee \ "user" \ "login").as[String]) {
            hasBeenApproved = false
          }
        }
      }
      else {
        hasBeenApproved = false
      }
    }
    hasBeenApproved
  }

  def handleApproval(body: String, comments: List[JsValue], labels: String) = {
    val users = findUsers(body, comments)

    if (users.length >= 1) {
      val isApproved = issueHasBeenApproved(users, comments)

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
  }

  def fetchComments(comments: String): Future[List[JsValue]] = {
    val prom = Promise[List[JsValue]]
    val req: WSRequestHolder = AuthService.authenticateRequest(comments)

    req.get().map {
      response =>
        prom.success(response.json.as[JsArray].as[List[JsValue]])
    }
    prom.future
  }
}