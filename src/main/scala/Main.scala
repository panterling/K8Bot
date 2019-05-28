import java.io.IOException

import akka.actor.{ActorSystem, FSM, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.util.ByteString
import com.spotify.docker.client.DefaultDockerClient
import slack.rtm.{SlackRtmClient, SlackRtmConnectionActor}
import slack.models.{Hello, Message, SlackEvent}
import skuber._
import skuber.apps.v1.Deployment
import skuber.json.format._
import spray.json.DefaultJsonProtocol

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.io.{Source, StdIn}
import scala.util.{Failure, Success}




object Main {

  val SLACK_CHANNEL = "CK0C4AZUG"

  case class DockerImageDescription(layer: String, name: String)
  object MyJsonProtocol extends DefaultJsonProtocol {
    implicit val dockerImageDescriptionFormat = jsonFormat2(DockerImageDescription)
  }

  implicit val system = ActorSystem("slack")
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  def main(args: Array[String]): Unit = {

    val token = sys.env("SLACK_TOKEN")
    val client = SlackRtmClient(token)

    val state = client.state
    val selfId = state.self.id


    // Kuberenetes Stuff
    val k8s = k8sInit
    /*
    val listPodsRequest = k8s.listInNamespace[PodList]("kube-system")
    listPodsRequest.onComplete {
      case Success(pods) => pods.items.foreach { p => println(p.name) }
      case Failure(e) => throw e
    }
    */

    // Watch Deployment: test

    val frontendReplicaCountMonitor = Sink.foreach[K8SWatchEvent[Deployment]] { frontendEvent =>
      val msg = "Current frontend replicas: " + frontendEvent._object.status.get.replicas
      println(msg)

      client.sendMessage(SLACK_CHANNEL, s"<!here> ${msg}")
    }
    for {
      frontend <- k8s.get[Deployment]("test")
      frontendWatch <- k8s.watch(frontend)
      done <- frontendWatch.runWith(frontendReplicaCountMonitor)
    } yield done



    // WebHook stuff
    /*
    val route =
      path("payload_travis") {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done

    */


    // Manager Stuff
    val deploymentDirectorActor = system.actorOf(Props(new DeploymentDirectorActor(k8s, client)))

    // Slack Stuff


    def processMessageFunc(msg: Message): Unit = {
      println(msg)

      val channel = msg.channel

      if(channel != SLACK_CHANNEL){
        println(s"Ignoring Message from Channel (${channel}) User (${state.getUserById(msg.user)})")
      }

      val msgPayload = msg.text.split("> ")(1)

      if(msgPayload == "deploy") {
        deploymentDirectorActor ! DeploymentRequest()

      } else if(msgPayload == "update") {
        /*
        #### Get sha of newest image

        dc = docker.APIClient()
        imageInfo = dc.inspect_image(f"cdevelop/cd:develop__b{latestBuildId}")
        repoDigest = imageInfo["RepoDigests"][0]
        print(repoDigest)

        #sha256:9c20a1bf6161cc726e1ed29cdc93bf32dce973e854e9b977eb020b4199073184
        */

        import HttpProtocols._
        import HttpMethods._
        val authorization = headers.Authorization(BasicHttpCredentials("cdevelop", "cDEV20191"))
        val request = HttpRequest(
          GET,
          uri = "https://registry.hub.docker.com/v1/repositories/cdevelop/cd/tags",
          //entity = HttpEntity(`text/plain` withCharset `UTF-8`, userData),
          headers = List(authorization),
          protocol = `HTTP/1.0`)

        val responseFuture: Future[HttpResponse] = Http().singleRequest(request)

        responseFuture
          .onComplete {
            case Success(response) => {
              println(response)

              val timeout = 10.seconds

              val bs: Future[ByteString] = response.entity.toStrict(timeout).map { _.data }
              val s: Future[String] = bs.map(_.utf8String) // if you indeed need a `String`

              s onComplete {
                case Success(value) => {

                  import spray.json._

                  import MyJsonProtocol._

                  def parseJson(str: String): List[DockerImageDescription]  = {
                    // parse using spray-json
                    str.parseJson.convertTo[List[DockerImageDescription]]
                  }

                  def latestBuildId(imageDescriptions: List[DockerImageDescription]): Int = {
                    imageDescriptions.map(m => Integer.parseInt(m.name.split("__b")(1))).max
                  }

                  val jsonPayload = parseJson(value)
                  val latestBuildInt = latestBuildId(jsonPayload)
                  println(s"Latest Build Id: ${latestBuildInt}")

                  // Get Digest/Tag

                  val docker = DefaultDockerClient.fromEnv.build
                  val info = docker.inspectImage(s"cdevelop/cd:develop__b${latestBuildInt}")


                  val quxImages = docker.listImages()

                  // Compare with Current

                  // Trigger rollout/rolling update

                  val i = 0
                }
                case Failure(value) => {
                  print(value)
                }
              }

              val strictEntity: Future[HttpEntity.Strict] = response.entity.toStrict(3.seconds)

              import akka.http.scaladsl.unmarshalling.Unmarshal
              import spray.json._

              /*
              def parseJson(str: String): /*List[com.thenewmotion.geocode.Result]*/ JsValue = {
                // parse using spray-json
                str.parseJson//.convertTo[List[com.thenewmotion.geocode.Result]]
              }

              strictEntity onComplete {
                case Success(value) => {
                  Unmarshal(value).to[String] onComplete {
                    case Success(strJson) => {
                      val i = 0
                      val jsonPayload = parseJson(strJson)
                    }
                    case Failure(exception) => {
                      val i = 0
                    }
                  }
                }
                case Failure(exception) => {

                }
              }

              val i = 0
              */
            }
            case Failure(_)   => {
              sys.error("something wrong")
            }
          }

        val i = 0


      }


      val userName = state.getUserById(msg.user)

      val responseText = s"Hi ${userName.get.name}\nGot: ${msgPayload}"

      client.sendMessage(channel, responseText, thread_ts = Some(msg.ts))
    }


    client.onEvent {
      case e: Hello => client.sendMessage(SLACK_CHANNEL, s"<!here> I'm Back!")
      case msg: Message => processMessageFunc(msg)
      case e: SlackEvent => {
        println(e)
      }
    }

  }

}
