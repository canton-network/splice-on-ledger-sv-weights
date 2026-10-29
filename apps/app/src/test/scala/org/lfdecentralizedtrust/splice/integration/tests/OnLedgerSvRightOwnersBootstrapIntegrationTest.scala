// Copyright (c) 2024 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package org.lfdecentralizedtrust.splice.integration.tests

import org.lfdecentralizedtrust.splice.codegen.java.splice.amulet.svrightowner.SvRightOwner
import org.lfdecentralizedtrust.splice.codegen.java.splice.dso.svstate.SvRewardState
import org.lfdecentralizedtrust.splice.config.ConfigTransforms
import org.lfdecentralizedtrust.splice.integration.EnvironmentDefinition
import org.lfdecentralizedtrust.splice.integration.tests.SpliceTests.IntegrationTest
import org.lfdecentralizedtrust.splice.sv.automation.singlesv.ReceiveSvRewardCouponTrigger
import org.lfdecentralizedtrust.splice.sv.config.SvOnboardingConfig
import com.digitalasset.canton.logging.SuppressionRule
import org.slf4j.event.Level

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

abstract class OnLedgerSvRightOwnersBootstrapIntegrationTestBase extends IntegrationTest {

  protected def onLedgerSvRightOwners: Boolean

  override def environmentDefinition: EnvironmentDefinition = {
    val base = EnvironmentDefinition.simpleTopology1Sv(this.getClass.getSimpleName)
    if (onLedgerSvRightOwners)
      base
        .addConfigTransform((_, config) => ConfigTransforms.withOnLedgerSvRightOwners(config))
        .addConfigTransform((_, config) =>
          ConfigTransforms.updateAutomationConfig(ConfigTransforms.ConfigurableApp.Sv)(
            // run the legacy SV reward coupon trigger only on demand and without random scheduling
            _.withPausedTrigger[ReceiveSvRewardCouponTrigger]
              .copy(enableNewRewardTriggerScheduling = false)
          )(config)
        )
    else base
  }

  protected def sv1FoundDsoConfig(implicit env: FixtureParam): SvOnboardingConfig.FoundDso =
    inside(sv1Backend.config.onboarding) { case Some(foundDso: SvOnboardingConfig.FoundDso) =>
      foundDso
    }

  protected def svRightOwners(implicit env: FixtureParam) =
    sv1Backend.participantClientWithAdminToken.ledger_api_extensions.acs
      .filterJava(SvRightOwner.COMPANION)(sv1Backend.getDsoInfo().dsoParty, _ => true)

  protected def svRewardStates(implicit env: FixtureParam) =
    sv1Backend.participantClientWithAdminToken.ledger_api_extensions.acs
      .filterJava(SvRewardState.COMPANION)(sv1Backend.getDsoInfo().dsoParty, _ => true)
}

class OnLedgerSvRightOwnersBootstrapIntegrationTest
    extends OnLedgerSvRightOwnersBootstrapIntegrationTestBase {

  override protected def onLedgerSvRightOwners = true

  "sv1 bootstraps the DSO in on-ledger SV right owner mode" in { implicit env =>
    val dsoInfo = sv1Backend.getDsoInfo()
    val sv1Config = sv1FoundDsoConfig
    val sv1Name = sv1Config.name
    val dsoRules = dsoInfo.dsoRules.payload

    dsoRules.svRightOwners.toScala.map(_.map.keySet.asScala.toSet) shouldBe Some(Set(sv1Name))
    dsoRules.svs.get(dsoInfo.svParty.toProtoPrimitive).svRewardWeight shouldBe 0L

    inside(svRightOwners) { case Seq(rightOwner) =>
      rightOwner.data.name shouldBe sv1Name
      rightOwner.data.info.rightOwnerParty shouldBe dsoInfo.svParty.toProtoPrimitive
      rightOwner.data.info.rewardWeight shouldBe sv1Config.firstSvRewardWeightBps
      rightOwner.data.info.rewardNodeOperatorName shouldBe sv1Name
      rightOwner.data.info.beneficiaries shouldBe empty
    }
    inside(svRewardStates) { case Seq(rewardState) =>
      rewardState.data.svName shouldBe sv1Name
    }

    clue("the legacy SV reward coupon trigger finds nothing to claim") {
      loggerFactory.assertLogsSeq(SuppressionRule.LevelAndAbove(Level.WARN))(
        sv1Backend.dsoAutomation
          .trigger[ReceiveSvRewardCouponTrigger]
          .runOnce()
          .futureValue shouldBe false,
        _ shouldBe empty,
      )
    }
  }
}

class LegacySvRightOwnersBootstrapIntegrationTest
    extends OnLedgerSvRightOwnersBootstrapIntegrationTestBase {

  override protected def onLedgerSvRightOwners = false

  "sv1 bootstraps the DSO in legacy mode without on-ledger SV right owners" in { implicit env =>
    val dsoInfo = sv1Backend.getDsoInfo()
    val sv1Config = sv1FoundDsoConfig
    val dsoRules = dsoInfo.dsoRules.payload

    dsoRules.svRightOwners.toScala shouldBe None
    dsoRules.svs
      .get(dsoInfo.svParty.toProtoPrimitive)
      .svRewardWeight shouldBe sv1Config.firstSvRewardWeightBps
    svRightOwners shouldBe empty
    inside(svRewardStates) { case Seq(rewardState) =>
      rewardState.data.svName shouldBe sv1Config.name
    }
  }
}
