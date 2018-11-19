package org.tron.core.db.api;

import com.google.protobuf.ByteString;
import java.io.File;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.testng.annotations.Test;
import org.tron.common.application.Application;
import org.tron.common.application.ApplicationFactory;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.FileUtil;
import org.tron.core.capsule.AccountCapsule;
import org.tron.core.capsule.AssetIssueCapsule;
import org.tron.core.capsule.ExchangeCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.protos.Contract.AssetIssueContract;
import org.tron.protos.Protocol.Account;
import org.tron.protos.Protocol.Exchange;

public class AssetUpdateHelperTest {

  private static Manager dbManager;
  private static TronApplicationContext context;
  private static String dbPath = "output_AssetUpdateHelperTest_test";
  private static Application AppT;

  private static ByteString assetName = ByteString.copyFrom("assetIssueName".getBytes());

  static {
    Args.setParam(new String[]{"-d", dbPath, "-w"}, "config-test-index.conf");
    Args.getInstance().setSolidityNode(true);
    context = new TronApplicationContext(DefaultConfig.class);
    AppT = ApplicationFactory.create(context);
  }

  @BeforeClass
  public static void init() {

    dbManager = context.getBean(Manager.class);

    AssetIssueCapsule assetIssueCapsule =
        new AssetIssueCapsule(
            AssetIssueContract.newBuilder().setName(assetName).setNum(12581).build());
    dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

    ExchangeCapsule exchangeCapsule =
        new ExchangeCapsule(
            Exchange.newBuilder()
                .setExchangeId(1L)
                .setFirstTokenId(assetName)
                .setSecondTokenId(ByteString.copyFrom("_".getBytes()))
                .build());
    dbManager.getExchangeStore().put(exchangeCapsule.createDbKey(), exchangeCapsule);

    AccountCapsule accountCapsule =
        new AccountCapsule(
            Account.newBuilder()
                .setAssetIssuedName(assetName)
                .putAsset("assetIssueName", 100)
                .putFreeAssetNetUsage("assetIssueName", 20000)
                .putLatestAssetOperationTime("assetIssueName", 30000000)
                .setAddress(ByteString.copyFrom(ByteArray.fromHexString("121212abc")))
                .build());
    dbManager.getAccountStore().put(ByteArray.fromHexString("121212abc"), accountCapsule);
  }

  @AfterClass
  public static void removeDb() {
    Args.clearParam();
    AppT.shutdownServices();
    AppT.shutdown();
    context.destroy();
    FileUtil.deleteDir(new File(dbPath));
  }

  @Test
  public void test() {

    if (dbManager == null) {
      init();
    }
    AssetUpdateHelper assetUpdateHelper = new AssetUpdateHelper(dbManager);
    assetUpdateHelper.init();
    {
      assetUpdateHelper.updateAsset();

      long idNum = 1000001L;

      AssetIssueCapsule assetIssueCapsule =
          dbManager.getAssetIssueStore().get(assetName.toByteArray());
      Assert.assertEquals(idNum, assetIssueCapsule.getId());

      AssetIssueCapsule assetIssueCapsule2 =
          dbManager.getAssetIssueV2Store().get(ByteArray.fromString(String.valueOf(idNum)));

      Assert.assertEquals(idNum, assetIssueCapsule2.getId());
      Assert.assertEquals(assetName, assetIssueCapsule2.getName());
    }

    {
      assetUpdateHelper.updateExchange();

      try {
        ExchangeCapsule exchangeCapsule =
            dbManager.getExchangeV2Store().get(ByteArray.fromLong(1L));
        Assert.assertEquals("1000001", ByteArray.toStr(exchangeCapsule.getFirstTokenId()));
        Assert.assertEquals("_", ByteArray.toStr(exchangeCapsule.getSecondTokenId()));
      } catch (Exception ex) {
        throw new RuntimeException("testUpdateExchange error");
      }
    }

    {
      assetUpdateHelper.updateAccount();

      AccountCapsule accountCapsule =
          dbManager.getAccountStore().get(ByteArray.fromHexString("121212abc"));

      Assert.assertEquals(
          ByteString.copyFrom(ByteArray.fromString("1000001")), accountCapsule.getAssetIssuedID());

      Assert.assertEquals(1, accountCapsule.getAssetMapV2().size());

      Assert.assertEquals(100L, accountCapsule.getAssetMapV2().get("1000001").longValue());

      Assert.assertEquals(1, accountCapsule.getAllFreeAssetNetUsageV2().size());

      Assert.assertEquals(20000L,
          accountCapsule.getAllFreeAssetNetUsageV2().get("1000001").longValue());

      Assert.assertEquals(1, accountCapsule.getLatestAssetOperationTimeMapV2().size());

      Assert.assertEquals(30000000L,
          accountCapsule.getLatestAssetOperationTimeMapV2().get("1000001").longValue());
    }

    removeDb();
  }
}