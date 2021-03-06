package com.wavesplatform.it.activation

import com.typesafe.config.{Config, ConfigFactory}
import com.wavesplatform.features.BlockchainFeatureStatus
import com.wavesplatform.features.api.NodeFeatureStatus
import com.wavesplatform.it.Docker
import org.scalatest.{BeforeAndAfterAll, CancelAfterFailure, FreeSpec, Matchers}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


class VoteForFeatureByDefaultTestSuite extends FreeSpec with Matchers with BeforeAndAfterAll with CancelAfterFailure with ActivationStatusRequest {

  import VoteForFeatureByDefaultTestSuite._

  private val docker = Docker(getClass)
  private val nodes = Configs.map(docker.startNode)
  val defaultVotingFeatureNum: Short = 1


  override protected def beforeAll(): Unit = {
    super.beforeAll()
    Await.result(Future.traverse(nodes)(_.waitForPeers(NodesCount - 1)), 2.minute)
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    docker.close()
  }


  "supported blocks increased when voting starts, one node votes against, three by default" in {
    val checkHeight: Int = votingInterval * 2 / 3
    val supportedNodeActivationInfo = activationStatus(nodes.last, checkHeight, defaultVotingFeatureNum, 2.minute)

    val generatedBlocks = Await.result(nodes.last.blockSeq(1, checkHeight), 2.minute)
    val featuresMapInGeneratedBlocks = generatedBlocks.flatMap(b => b.features.getOrElse(Seq.empty)).groupBy(x => x)
    val votesForFeature1 = featuresMapInGeneratedBlocks.getOrElse(defaultVotingFeatureNum, Seq.empty).length

    assertVotingStatus(supportedNodeActivationInfo, votesForFeature1, BlockchainFeatureStatus.Undefined, NodeFeatureStatus.Voted)

    val nonSupportedNodeActivationInfo = activationStatus(nodes.head, checkHeight, defaultVotingFeatureNum, 2.minute)
    assertVotingStatus(nonSupportedNodeActivationInfo, votesForFeature1, BlockchainFeatureStatus.Undefined, NodeFeatureStatus.Implemented)
  }


  "blockchain status is APPROVED in second voting interval, one node votes against, three by default" in {

    val checkHeight: Int = votingInterval * 2 - blocksForActivation / 2

    val supportedNodeActivationInfo = activationStatus(nodes.last, checkHeight, defaultVotingFeatureNum, 3.minute)
    assertApprovedStatus(supportedNodeActivationInfo, votingInterval * 2, NodeFeatureStatus.Voted)
  }

  "blockchain status is ACTIVATED in the end of second voting interval, one node votes against, three by default" in {
    val checkHeight: Int = votingInterval * 2

    val supportedNodeActivationInfo = activationStatus(nodes.last, checkHeight, defaultVotingFeatureNum, 2.minute)

    supportedNodeActivationInfo.activationHeight.get shouldBe checkHeight
    supportedNodeActivationInfo.blockchainStatus shouldBe BlockchainFeatureStatus.Activated
    supportedNodeActivationInfo.nodeStatus shouldBe NodeFeatureStatus.Voted
  }


  object VoteForFeatureByDefaultTestSuite {

    private val dockerConfigs = Docker.NodeConfigs.getConfigList("nodes").asScala

    val votingInterval = 20
    val blocksForActivation = 15
    val defaultVotingFeatureNum: Short = 1
    val nonVotingFeatureNum: Short = 2

    val NodesCount: Int = 4


    private val supportedNodes = ConfigFactory.parseString(
      s"""
         |waves.blockchain.custom.functionality.feature-check-blocks-period = $votingInterval
         |waves.blockchain.custom.functionality.blocks-for-feature-activation = $blocksForActivation
         |waves {
         |   blockchain {
         |     custom {
         |        functionality{
         |          pre-activated-features = {}
         |        }
         |        genesis {
         |          signature: "zXBp6vpEHgtdsPjVHjSEwMeRiQTAu6DdX3qkJaCRKxgYJk26kazS2XguLYRvL9taHKxrZHNNA7X7LMVFavQzWpT"
         |          transactions = [
         |            {recipient: "3Hm3LGoNPmw1VTZ3eRA2pAfeQPhnaBm6YFC", amount: 250000000000000},
         |            {recipient: "3HZxhQhpSU4yEGJdGetncnHaiMnGmUusr9s", amount: 270000000000000},
         |            {recipient: "3HPG313x548Z9kJa5XY4LVMLnUuF77chcnG", amount: 260000000000000},
         |            {recipient: "3HVW7RDYVkcN5xFGBNAUnGirb5KaBSnbUyB", amount: 2000000000000}
         |          ]
         |       }
         |
         |
         |      }
         |   }
         |}
      """.stripMargin
    )
    private val nonSupportedNodes = ConfigFactory.parseString(
      s"""
         |waves.features{
         | supported=[$nonVotingFeatureNum]
         |}
         |waves.blockchain.custom.functionality.feature-check-blocks-period = $votingInterval
         |waves.blockchain.custom.functionality.blocks-for-feature-activation = $blocksForActivation
         |
         |waves {
         |   blockchain {
         |     custom {
         |        functionality{
         |          pre-activated-features = {}
         |        }
         |        genesis {
         |          signature: "zXBp6vpEHgtdsPjVHjSEwMeRiQTAu6DdX3qkJaCRKxgYJk26kazS2XguLYRvL9taHKxrZHNNA7X7LMVFavQzWpT"
         |          transactions = [
         |            {recipient: "3Hm3LGoNPmw1VTZ3eRA2pAfeQPhnaBm6YFC", amount: 250000000000000},
         |            {recipient: "3HZxhQhpSU4yEGJdGetncnHaiMnGmUusr9s", amount: 270000000000000},
         |            {recipient: "3HPG313x548Z9kJa5XY4LVMLnUuF77chcnG", amount: 260000000000000},
         |            {recipient: "3HVW7RDYVkcN5xFGBNAUnGirb5KaBSnbUyB", amount: 2000000000000}
         |          ]
         |       }
         |      }
         |   }
         |}
      """.stripMargin

    )


    val Configs: Seq[Config] = Seq(nonSupportedNodes.withFallback(dockerConfigs(3))) ++
      Seq(supportedNodes.withFallback(dockerConfigs(1))) ++
      Seq(supportedNodes.withFallback(dockerConfigs(2))) ++
      Seq(supportedNodes.withFallback(dockerConfigs.head))

  }

}