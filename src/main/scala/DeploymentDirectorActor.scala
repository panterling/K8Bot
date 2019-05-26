import Main.SLACK_CHANNEL
import akka.actor.Actor
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import play.api.libs.json.Json
import skuber._
import skuber.json.format._
import skuber.apps.v1.Deployment
import skuber.K8SWatchEvent
import skuber.api.client.KubernetesClient
import skuber.apps.v1.Deployment
import slack.rtm.SlackRtmClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.util.{Failure, Success}


case class DeploymentRequest()

class DeploymentDirectorActor(val k8s: KubernetesClient, slackClient: SlackRtmClient) extends Actor {

  class NoLongerWatchMe(message: String) extends Exception(message)

  implicit val materializer = ActorMaterializer() // Should this be injected from the top-level scope instanciation?

  def yamlToJsonString(yamlStr: String): String = {
    import com.fasterxml.jackson.databind.ObjectMapper
    import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

    val yamlReader = new ObjectMapper(new YAMLFactory)
    val obj = yamlReader.readValue(yamlStr, classOf[Object])
    val jsonWriter = new ObjectMapper()
    jsonWriter.writeValueAsString(obj)
  }

  override def receive: Receive = {
    case dr: DeploymentRequest => {

      // Create Deployment
      println("Creating deployment")
      // Read and parse the deployment in a Skuber model
      val deploymentURL = "/home/chris/K8Bot/testDeployment.yaml"
      val deploymentYamlStr= Source.fromFile(deploymentURL).mkString
      val deploymentJsonStr=yamlToJsonString(deploymentYamlStr)
      val deployment = Json.parse(deploymentJsonStr).as[Deployment]

      val rcFut = k8s create deployment

      rcFut onComplete {
        case Success(rc) => {
          println(s"Created Deployment, Kubernetes assigned resource version is ${rc.metadata.resourceVersion}")

          // Watch Deployment
          println(s"Watching deployment: ${deployment.name}")
          val frontendReplicaCountMonitor = Sink.foreach[K8SWatchEvent[Deployment]] { frontendEvent =>
            val msg = "Current frontend replicas: " + frontendEvent._object.status.get.replicas
            println(msg)

            slackClient.sendMessage(SLACK_CHANNEL, s"<!here> ${msg}")

            var deploymentReady = frontendEvent._object.status.get.replicas == deployment.spec.get.replicas.get

            if (deploymentReady) {
              // Stop when deployment is 'up'
              println("Deployment Up - No Longer watching")

              throw new NoLongerWatchMe("Fail-but-success -> Deployed Successfully")
            }
          }
          val watcher = for {
            frontend <- k8s.get[Deployment](deployment.name)
            frontendWatch <- k8s.watch(frontend)
            done <- frontendWatch.runWith(frontendReplicaCountMonitor)
          } yield done

          watcher.onComplete {
            case Success(value) => {
              println(value)
            }
            case Failure(value) => {
              println(value)
            }
          }



        }
        case Failure(rc) => println(s"FAILED TO DEPLOY")
      }


    }
  }
}
