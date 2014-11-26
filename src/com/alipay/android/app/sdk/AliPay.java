package com.alipay.android.app.sdk;

import org.json.JSONObject;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.alipay.android.app.lib.ResourceMap;
import com.alipay.android.app.net.HttpClient;
import com.alipay.android.app.net.RequestData;
import com.alipay.android.app.net.ResponseData;
import com.alipay.android.app.util.FileDownloader;
import com.alipay.android.app.util.LogUtils;
import com.alipay.android.app.util.PayHelper;
import com.alipay.android.app.util.StoreUtils;
import com.alipay.android.app.util.Utils;
import com.alipay.android.app.widget.Loading;

public class AliPay
{
  private final String URL = "https://mclient.alipay.com/gateway.do";

  private final String URL_SANDBOX = "https://mobiletestabc.alipaydev.com/mobileclientgw/net/gateway.do";
  private boolean isSandbox;
  private final String MSP_APK_NAME = "alipay_msp.apk";
  private final String ALIPAY_APK_NAME = "alipay.apk";
  private Activity mContext;
  private Handler mHandler;
  private AlertDialog mInstallMessageAlert;
  private String mDownloadUrl;
  private String mDownloadType;
  private int mTimeout;
  private String mUrl;
  private boolean isInstall;
  private FileDownloader fileDownloader;
  private String cachePath;
  protected static final Object sLock = new Object();
  private static int lowMemoryThresholds = 300;
  
  private Runnable mDownloadFailRunnable = new Runnable()
  {
    public void run()
    {
      AliPay.this.fileDownloader.stop();

      AlertDialog.Builder alertDialog = new AlertDialog.Builder(
        AliPay.this.mContext);
      alertDialog.setTitle(ResourceMap.getString_confirm_title());
      alertDialog.setMessage(ResourceMap.getString_download_fail());

      alertDialog.setNegativeButton(ResourceMap.getString_cancel(), 
        new DialogInterface.OnClickListener()
      {
        public void onClick(DialogInterface dialog, int which)
        {
          synchronized (AliPay.sLock) {
            ResultStatus status = 
              ResultStatus.getResultState(6001);
            Result.setPayResult(Result.parseResult(
              status.getStatus(), status.getMsg(), ""));
            try {
              AliPay.sLock.notify();
            } catch (Exception e) {
              LogUtils.printExceptionStackTrace(e);
            }
          }
        }
      });
      alertDialog.setPositiveButton(ResourceMap.getString_redo(), 
        new DialogInterface.OnClickListener()
      {
        public void onClick(DialogInterface dialog, int which)
        {
          if (!Utils.isVerifyURL(AliPay.this.mDownloadUrl)) {
            Intent intent = new Intent(mContext, 
              WapPayActivity.class);
            Bundle extras = new Bundle();
            extras.putString("url", AliPay.this.mUrl);
            extras.putInt("timeout", AliPay.this.mTimeout);
            intent.putExtras(extras);
            
            mContext.startActivity(intent);
          }
          else {
            AliPay.this.downloadFile();
          }
        }
      });
      alertDialog.show();
    }
  };

  private BroadcastReceiver mReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      if (intent.getAction()
        .equalsIgnoreCase("android.intent.action.PACKAGE_ADDED"))
      {
        Runnable r = new Runnable()
        {
          public void run() {
            if (AliPay.this.mInstallMessageAlert != null)
              AliPay.this.mInstallMessageAlert.dismiss();
            AliPay.this.isInstall = true;
            AliPay.this.mContext.unregisterReceiver(AliPay.this.mReceiver);
          }
        };
        AliPay.this.mHandler.post(r);
      }
    }
  };

  public void setSandBox(boolean sandbox)
  {
    this.isSandbox = sandbox;
  }

  public AliPay(Activity context, Handler handler) {
    this.mContext = context;
    this.cachePath = (this.mContext.getCacheDir().getAbsolutePath() + "/temp.apk");
    if (handler != null)
      this.mHandler = handler;
    else
      this.mHandler = new Handler();
  }

  public String pay(String orderInfo)
  {
    Loading loading = new Loading(this.mContext);
    loading.show();

    //内存不够的时候不能让它直接告诉服务器能安全支付，要不然服务器不返回网页计费的url了
    boolean memoryOK = isMemoryLargeEnough();
    
    String payResult = "";

    String clientKey = Utils.getClientKey(this.mContext);
    String clientId = Utils.getClientId(this.mContext);
    String alixTid = Utils.getAlixTid(this.mContext);
    String network = Utils.getNetConnectionType(this.mContext).getName();

//    Log.e("twtw","clientKey:"+clientKey);
//    Log.e("twtw","clientId:"+clientId);
//    Log.e("twtw","alixTid:"+alixTid);
//    Log.e("twtw","network:"+network);
    
    StringBuilder sb = new StringBuilder("");
    if (Utils.isExistMsp(this.mContext)) {
    	if(memoryOK){
    		sb.append("safepay|");
    	}
    }
    if (Utils.isExistClient(this.mContext)) {
    	if(memoryOK){
    		sb.append("alipay");
    	}
    }
    else if (sb.indexOf("|") != -1) {
      sb.deleteCharAt(sb.indexOf("|"));
    }

    PayHelper payHelper = new PayHelper(this.mContext);

    RequestData requestData = new RequestData(clientKey, clientId, alixTid, 
      sb.toString(), network, orderInfo);

    HttpClient httpClient = new HttpClient(this.mContext);

    if (this.isSandbox)
      httpClient.setUrl("https://mobiletestabc.alipaydev.com/mobileclientgw/net/gateway.do");
    else {
      httpClient.setUrl("https://mclient.alipay.com/gateway.do");
    }

    LogUtils.i("sdk request:" + requestData.toString());

    String response = httpClient.sendSynchronousRequest(
      requestData.toString(), null);

    
    loading.dismiss();
    LogUtils.i("sdk response:" + response);
    if (TextUtils.isEmpty(response))
    {
      String payConfig = StoreUtils.getValue(this.mContext, "config");
      if (TextUtils.equals(payConfig, "safepay")) {
        if (Utils.isExistMsp(this.mContext)) {
          payResult = payHelper.pay4Msp(orderInfo);
          return payResult;
        }
      } else if (TextUtils.equals(payConfig, "alipay")) {
        if (Utils.isExistClient(this.mContext)) {
          payResult = payHelper.pay4Client(orderInfo);
          return payResult;
        }
      } else if (TextUtils.isEmpty(payConfig)) {
        if (Utils.isExistMsp(this.mContext)) {
          payResult = payHelper.pay4Msp(orderInfo);
          return payResult;
        }
        if (Utils.isExistClient(this.mContext)) {
          payResult = payHelper.pay4Client(orderInfo);
          return payResult;
        }

        if (Utils.retrieveApkFromAssets(this.mContext, "alipay_msp.apk", 
          this.cachePath))
        {
          doInstall(false, this.cachePath, this.mContext.getString(ResourceMap.getString_install_msp()));
          synchronized (sLock) {
            try {
              sLock.wait();
            } catch (InterruptedException e) {
              LogUtils.printExceptionStackTrace(e);
            }
          }

          if (this.isInstall)
            payResult = payHelper.pay4Msp(orderInfo);
          else {
            payResult = Result.getPayResult();
          }
          return payResult;
        }

        if (Utils.retrieveApkFromAssets(this.mContext, 
          "alipay.apk", this.cachePath)) {
          doInstall(false, this.cachePath, this.mContext.getString(ResourceMap.getString_install_alipay()));
          synchronized (sLock) {
            try {
              sLock.wait();
            } catch (InterruptedException e) {
              LogUtils.printExceptionStackTrace(e);
            }
          }

          if (this.isInstall)
            payResult = payHelper.pay4Client(orderInfo);
          else {
            payResult = Result.getPayResult();
          }
          return payResult;
        }
        ResultStatus status = ResultStatus.getResultState(6002);
        payResult = Result.parseResult(status.getStatus(), 
          status.getMsg(), "");

        return payResult;
      }

    }

    ResponseData responseData = new ResponseData(response);

    final JSONObject jsonParams = responseData.getParams();
    if (jsonParams == null) {
      ResultStatus status = ResultStatus.getResultState(4000);
      payResult = Result.parseResult(status.getStatus(), status.getMsg(), 
        "");
      return payResult;
    }

    //当前内存不够的时候还是需要使用wap页面计费
    if(!memoryOK){
    	mHandler.post(new Runnable(){
			@Override
			public void run() {
				 int timeout = jsonParams.optInt("timeout", 15);
		         String url = jsonParams.optString("url");
		        
		        Intent intent = new Intent(mContext, WapPayActivity.class);
		        Bundle extras = new Bundle();
		        extras.putString("url", url);
		        extras.putInt("timeout", timeout);
		        intent.putExtras(extras);
		        mContext.startActivity(intent);
			}
    	});
    	

        synchronized (sLock) {
          try {
            sLock.wait();
          } catch (InterruptedException e) {
            LogUtils.printExceptionStackTrace(e);
          }
        }

        payResult = Result.getPayResult();
        if (TextUtils.isEmpty(payResult)) {
          ResultStatus status = ResultStatus.getResultState(6001);
          payResult = Result.parseResult(status.getStatus(), 
            status.getMsg(), "");
        }

        return payResult;
    }
    String state = jsonParams.optString("state");

    if (TextUtils.equals(state, "7001")) {
      String errorMsg = jsonParams.optString("errorMessage");
      payResult = Result.parseResult(Integer.parseInt(state), errorMsg, 
        "");
      return payResult;
    }
    if (TextUtils.equals(state, "9000"))
    {
      alixTid = jsonParams.optString("alixtid");
      if (!TextUtils.equals(alixTid, Utils.getAlixTid(this.mContext))) {
        StoreUtils.putValue(this.mContext, "alix_tid", alixTid);
      }
      String payConfig = jsonParams.optString("config");
      if (TextUtils.equals(payConfig, "safepay")) {
        StoreUtils.putValue(this.mContext, payConfig, "safepay");
        payResult = payHelper.pay4Msp(orderInfo);
        return payResult;
      }
      if (TextUtils.equals(payConfig, "alipay")) {
        StoreUtils.putValue(this.mContext, payConfig, "alipay");
        payResult = payHelper.pay4Client(orderInfo);
        return payResult;
      }
      if (TextUtils.equals(payConfig, "wap")) {
        int timeout = jsonParams.optInt("timeout", 15);
        String url = jsonParams.optString("url");

        
        Intent intent = new Intent(mContext, WapPayActivity.class);
        Bundle extras = new Bundle();
        extras.putString("url", url);
        extras.putInt("timeout", timeout);
        intent.putExtras(extras);
        mContext.startActivity(intent);

        synchronized (sLock) {
          try {
            sLock.wait();
          } catch (InterruptedException e) {
            LogUtils.printExceptionStackTrace(e);
          }
        }

        payResult = Result.getPayResult();
        if (TextUtils.isEmpty(payResult)) {
          ResultStatus status = ResultStatus.getResultState(6001);
          payResult = Result.parseResult(status.getStatus(), 
            status.getMsg(), "");
        }

        return payResult;
      }
      if (TextUtils.equals(payConfig, "wap_sdk"))
      {
        this.mTimeout = jsonParams.optInt("timeout", 15);
        this.mDownloadUrl = jsonParams.optString("downloadUrl");
        String downloadMessage = jsonParams
          .optString("downloadMessage");
        this.mUrl = jsonParams.optString("url");
        this.mDownloadType = jsonParams.optString("downloadType");

        processInstall(true, downloadMessage, jsonParams);

        synchronized (sLock) {
          try {
            sLock.wait();
          } catch (InterruptedException e) {
            LogUtils.printExceptionStackTrace(e);
          }
        }

        if (this.isInstall) {
          if (TextUtils.equals(this.mDownloadType, "safepay")) {
            payResult = payHelper.pay4Msp(orderInfo);
            return payResult;
          }if (TextUtils.equals(this.mDownloadType, "alipay")) {
            payResult = payHelper.pay4Client(orderInfo);
            return payResult;
          }
        }
      }
      else if (TextUtils.equals(payConfig, "download")) {
        this.mDownloadUrl = jsonParams.optString("downloadUrl");
        String downloadMessage = jsonParams
          .optString("downloadMessage");

        this.mDownloadType = jsonParams.optString("downloadType");
        processInstall(false, downloadMessage, jsonParams);

        synchronized (sLock) {
          try {
            sLock.wait();
          } catch (InterruptedException e) {
            LogUtils.printExceptionStackTrace(e);
          }
        }

        if (this.isInstall) {
          if (TextUtils.equals(this.mDownloadType, "safepay")) {
            payResult = payHelper.pay4Msp(orderInfo);
            return payResult;
          }if (TextUtils.equals(this.mDownloadType, "alipay")) {
            payResult = payHelper.pay4Client(orderInfo);
            return payResult;
          }
        }
      }
      else if (TextUtils.equals(payConfig, "exit")) {
        ResultStatus status = ResultStatus.getResultState(4000);
        payResult = Result.parseResult(status.getStatus(), 
          status.getMsg(), "");
        return payResult;
      }
    } else {
      if (TextUtils.equals(state, "4001")) {
        ResultStatus status = ResultStatus.getResultState(
          Integer.parseInt(state));
        payResult = Result.parseResult(status.getStatus(), status.getMsg(), 
          "");
        return payResult;
      }if (TextUtils.equals(state, "5001")) {
        return pay(orderInfo);
      }
    }
    if (Result.getPayResult() != null) {
      payResult = Result.getPayResult();
    }
    LogUtils.i("sdk result:" + payResult);

    return payResult;
  }

  private void processInstall(boolean isWap, String downloadMessage, JSONObject jsonParams)
  {
    if (TextUtils.equals(this.mDownloadType, "safepay")) {
      if (Utils.retrieveApkFromAssets(this.mContext, "alipay_msp.apk", this.cachePath)) {
        if (Utils.is2G(this.mContext)) {
          doInstall(isWap, this.cachePath, downloadMessage);
        } else {
          PackageInfo apkInfo = Utils.getApkInfo(this.mContext, this.cachePath);
          String apk_version = apkInfo.versionName;
          String download_version = jsonParams.optString(
            "downloadVersion", "3.5.4");
          if (Utils.compareVersion(apk_version, download_version) < 0)
            doDownLoad(isWap, downloadMessage);
          else {
            doInstall(isWap, this.cachePath, downloadMessage);
          }
        }
      }
      else
        doDownLoad(isWap, downloadMessage);
    }
    else if (TextUtils.equals(this.mDownloadType, "alipay"))
    {
      if (Utils.retrieveApkFromAssets(this.mContext, "alipay.apk", 
        this.cachePath)) {
        if (Utils.is2G(this.mContext)) {
          doInstall(isWap, this.cachePath, downloadMessage);
        } else {
          PackageInfo apkInfo = Utils.getApkInfo(this.mContext, this.cachePath);
          String apk_version = apkInfo.versionName;
          String download_version = jsonParams.optString(
            "downloadVersion", "7.1.0.0701");
          if (Utils.compareVersion(apk_version, download_version) < 0)
            doDownLoad(isWap, downloadMessage);
          else {
            doInstall(isWap, this.cachePath, downloadMessage);
          }
        }
      }
      else
        doDownLoad(isWap, downloadMessage);
    }
  }

  private void doInstall(final boolean isWap, String cachePath, final String downloadMessage)
  {
    Runnable runnable = new Runnable()
    {
      public void run()
      {
    	  if (isWap) {
              Intent intent = new Intent(mContext, 
                WapPayActivity.class);
              Bundle extras = new Bundle();
              extras.putString("url", AliPay.this.mUrl);
//              Log.e("twtw", "url:"+AliPay2.this.mUrl);
              extras.putInt("timeout", AliPay.this.mTimeout);
              intent.putExtras(extras);
              mContext.startActivity(intent);
              
            }
            else {
              synchronized (AliPay.sLock) {
                ResultStatus status = 
                  ResultStatus.getResultState(6001);
                Result.setPayResult(Result.parseResult(
                  status.getStatus(), 
                  status.getMsg(), ""));
                try {
                  AliPay.sLock.notify();
                } catch (Exception e) {
                  LogUtils.printExceptionStackTrace(e);
                }
              }
            }
      }
    };
    this.mHandler.post(runnable);
  }

  private void doDownLoad(final boolean isWap, final String downloadMessage) {
    Runnable runnable = new Runnable()
    {
      public void run()
      {
    	  
          if (isWap) {
              Intent intent = new Intent(mContext, 
                WapPayActivity.class);
              Bundle extras = new Bundle();
              extras.putString("url", AliPay.this.mUrl);
              extras.putInt("timeout", AliPay.this.mTimeout);
              intent.putExtras(extras);
              mContext.startActivity(intent);
            } else {
              synchronized (AliPay.sLock) {
                ResultStatus status = 
                  ResultStatus.getResultState(6001);
                Result.setPayResult(Result.parseResult(
                  status.getStatus(), 
                  status.getMsg(), ""));
                try {
                  AliPay.sLock.notify();
                } catch (Exception e) {
                  LogUtils.printExceptionStackTrace(e);
                }
              }
            }
      }
    };
    this.mHandler.post(runnable);
  }

  private void downloadFile() {
    final Loading loading = new Loading(this.mContext);
    loading.show(this.mContext.getText(ResourceMap.getString_processing()), 
      true, new DialogInterface.OnCancelListener()
    {
      public void onCancel(DialogInterface dialog)
      {
        loading.dismiss();
        AliPay.this.fileDownloader.stop();
        AliPay.this.mContext.unregisterReceiver(AliPay.this.mReceiver);
        AliPay.this.mHandler.removeCallbacks(AliPay.this.mDownloadFailRunnable);
        synchronized (AliPay.sLock) {
          ResultStatus status = 
            ResultStatus.getResultState(6001);
          Result.setPayResult(Result.parseResult(
            status.getStatus(), status.getMsg(), ""));
          try {
            AliPay.sLock.notify();
          } catch (Exception e) {
            LogUtils.printExceptionStackTrace(e);
          }
        }
      }
    });
    this.fileDownloader = new FileDownloader();
    this.fileDownloader.setFileUrl(this.mDownloadUrl);
    this.fileDownloader.setSavePath(this.cachePath);
    this.fileDownloader.setProgressOutput(new FileDownloader.IDownloadProgress()
    {
      public void downloadSucess() {
        loading.dismiss();
        AliPay.this.mHandler.removeCallbacks(AliPay.this.mDownloadFailRunnable);
        AliPay.this.showInstallMessage();
      }

      public void downloadProgress(float progress)
      {
      }

      public void downloadFail()
      {
        loading.dismiss();
        AliPay.this.mHandler.post(AliPay.this.mDownloadFailRunnable);
      }
    });
    this.fileDownloader.start();

    IntentFilter filter = new IntentFilter();
    filter.addAction("android.intent.action.PACKAGE_ADDED");
    filter.addDataScheme("package");
    this.mContext.registerReceiver(this.mReceiver, filter);

    this.mHandler.postDelayed(this.mDownloadFailRunnable, 180000L);
  }

  private void showInstallMessage()
  {
    Runnable r = new Runnable()
    {
      public void run() {
        if (Utils.getUninatllApkInfo(AliPay.this.mContext, AliPay.this.cachePath)) {
          Utils.installApk(AliPay.this.mContext, AliPay.this.cachePath);

          AlertDialog.Builder dialog = new AlertDialog.Builder(
            AliPay.this.mContext);
          dialog.setTitle(ResourceMap.getString_confirm_title());
          if (TextUtils.equals(AliPay.this.mDownloadType, "safepay"))
            dialog.setMessage(
              ResourceMap.getString_cancelInstallTips());
          else if (TextUtils.equals(AliPay.this.mDownloadType, "alipay")) {
            dialog.setMessage(
              ResourceMap.getString_cancelInstallAlipayTips());
          }
          dialog.setPositiveButton(ResourceMap.getString_ensure(), 
            new DialogInterface.OnClickListener()
          {
            public void onClick(DialogInterface dialog, int which)
            {
              AliPay.this.mContext.unregisterReceiver(AliPay.this.mReceiver);
              AliPay.this.isInstall = false;
              ResultStatus status = 
                ResultStatus.getResultState(6001);
              Result.setPayResult(Result.parseResult(
                status.getStatus(), 
                status.getMsg(), ""));

              synchronized (AliPay.sLock) {
                try {
                  AliPay.sLock.notify();
                } catch (Exception e) {
                  LogUtils.printExceptionStackTrace(e);
                }
              }
            }
          });
          AliPay.this.mInstallMessageAlert = dialog.show();
        } else {
          synchronized (AliPay.sLock) {
            ResultStatus status = ResultStatus.getResultState(4000);
            Result.setPayResult(Result.parseResult(
              status.getStatus(), status.getMsg(), ""));
            try {
              AliPay.sLock.notify();
            } catch (Exception e) {
              LogUtils.printExceptionStackTrace(e);
            }
          }
        }
      }
    };
    this.mHandler.postDelayed(r, 500L);
  }
  
  //设置低内存模式的阈值
  public static void setMemoryThreadshold(int count){
	 lowMemoryThresholds  = count;
  }
  
  private boolean isMemoryLargeEnough(){
	  //当前内存不够的时候还是需要使用wap页面计费
	    ActivityManager activityManager = (ActivityManager)mContext.getSystemService(Activity.ACTIVITY_SERVICE);
	    ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
	    activityManager.getMemoryInfo(memoryInfo);
	    Log.e("TWTW", "availMem:"+ (memoryInfo.availMem >> 20));
	    if(memoryInfo.availMem>>20 < lowMemoryThresholds){
	    	return false;
	    }
	    return true;
  }
}