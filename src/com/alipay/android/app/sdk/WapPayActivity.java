package com.alipay.android.app.sdk;

import java.lang.reflect.Method;

import me.gall.totalpay.android.UtilLegacy;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.alipay.android.app.lib.ResourceMap;
import com.alipay.android.app.util.LogUtils;
import com.alipay.android.app.util.Utils;
import com.alipay.android.app.widget.Loading;

@SuppressLint("JavascriptInterface")
public class WapPayActivity /*extends Activity*/
{
  private static final String PAY_RESULT_TAG = "sdk_result_code:";
  private WebView mWebView;
  private Button mRefreshButton;
  private int mTimeout;
  private Loading mLoading;
  private Handler mHandler = new Handler();
  private Activity mActivity;
  public WapPayActivity(Activity activity) {
	  mActivity = activity;
  }
  
  private Runnable mDelayRunnable = new Runnable()
  {
    public void run()
    {
      if (!WapPayActivity.this.mRefreshButton.isEnabled()) {
        WapPayActivity.this.mRefreshButton.setEnabled(true);
      }
      WapPayActivity.this.dismissLoading();
    }
  };

  protected void onCreate(Intent intent)
  {
//    super.onCreate(savedInstanceState);
//    Intent intent = getIntent();
    String url = intent.getExtras().getString("url");
    this.mTimeout = intent.getExtras().getInt("timeout", 15);

    this.mActivity.setContentView(getMainLayout());

//    this.mWebView = ((WebView)findViewById(ResourceMap.getId_webView()));

    WebSettings settings = this.mWebView.getSettings();
    settings.setUserAgentString(Utils.getUserAgent(mActivity));
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
    this.mWebView.setWebViewClient(new MyWebViewClient());
    this.mWebView.setWebChromeClient(new MyWebChromeClient());

    this.mWebView.addJavascriptInterface(new InJavaScriptLocalObj(), "local_obj");
    settings.setMinimumFontSize(settings.getMinimumFontSize() + 8);
    this.mWebView.loadUrl(url);

//    this.mRefreshButton = ((Button)findViewById(ResourceMap.getId_btn_refresh()));
    this.mRefreshButton.setOnClickListener(new View.OnClickListener()
    {
      public void onClick(View v) {
        WapPayActivity.this.mWebView.reload();
      }
    });
    this.mRefreshButton.setEnabled(false);
    try
    {
      Method removeJavascriptInterface = this.mWebView.getClass().getMethod(
        "removeJavascriptInterface", new Class[0]);
      if (removeJavascriptInterface != null)
        removeJavascriptInterface.invoke(this.mWebView, new Object[] { 
          "searchBoxJavaBridge_" });
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void onBackPressed()
  {
    if (this.mWebView.canGoBack()) {
      this.mWebView.goBack();
    } else {
      ResultStatus status = ResultStatus.getResultState(6001);
      Result.setPayResult(Result.parseResult(status.getStatus(), 
        status.getMsg(), ""));
      finish();
    }
  }

  public void finish()
  {
    notifyCaller();
//    super.finish();
  }

  public void notifyCaller() {
    synchronized (AliPay.sLock) {
      try {
    	  AliPay.sLock.notify();
      } catch (Exception e) {
        LogUtils.printExceptionStackTrace(e);
      }
    }
  }

 /* public void onConfigurationChanged(Configuration newConfig)
  {
    super.onConfigurationChanged(newConfig);
  }*/

  private void showLoading()
  {
    if (this.mLoading == null)
      this.mLoading = new Loading(mActivity);
    this.mLoading.show();
  }

  private void dismissLoading() {
    if ((this.mLoading != null) && (this.mLoading.isShowing()))
      this.mLoading.dismiss();
    this.mLoading = null;
  }

  final class InJavaScriptLocalObj
  {
    InJavaScriptLocalObj()
    {
    }

    public void showSource(String html)
    {
      LogUtils.d(html);

      if (html.contains("sdk_result_code:")) {
        int startPos = html.indexOf("sdk_result_code:");
        int endPos = html.indexOf("-->", startPos);

        String statusString = html.substring(
          startPos + "sdk_result_code:".length(), endPos).trim();

        Result.setPayResult(statusString);

        Runnable action = new Runnable()
        {
          public void run() {
            WapPayActivity.this.finish();
          }
        };
        mActivity.runOnUiThread(action);
      }
    }
  }

  private class MyWebChromeClient extends WebChromeClient
  {
    private MyWebChromeClient()
    {
    }

    public boolean onJsAlert(WebView view, String url, String message, final JsResult result)
    {
      AlertDialog.Builder alert = new AlertDialog.Builder(
    		  mActivity);
      alert.setTitle(ResourceMap.getString_confirm_title())
        .setMessage(message)
        .setPositiveButton(ResourceMap.getString_ensure(), 
        new DialogInterface.OnClickListener()
      {
        public void onClick(DialogInterface dialog, int which)
        {
          result.confirm();
        }
      }).setNegativeButton(ResourceMap.getString_cancel(), 
        new DialogInterface.OnClickListener()
      {
        public void onClick(DialogInterface dialog, int which)
        {
          result.cancel();
        }
      }).show();

      return true;
    }

    public boolean onJsConfirm(WebView view, String url, String message, final JsResult result)
    {
      AlertDialog.Builder alert = new AlertDialog.Builder(
    		  mActivity);
      alert.setTitle(ResourceMap.getString_confirm_title())
        .setMessage(message)
        .setPositiveButton(ResourceMap.getString_ensure(), 
        new DialogInterface.OnClickListener()
      {
        public void onClick(DialogInterface dialog, int which)
        {
          result.confirm();
        }
      }).setNegativeButton(ResourceMap.getString_cancel(), 
        new DialogInterface.OnClickListener( )
      {
        public void onClick(DialogInterface dialog, int which)
        {
          result.cancel();
        }
      }).show();
      return true;
    }

    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue,final JsPromptResult result)
    {
      AlertDialog.Builder alert = new AlertDialog.Builder(
    		  mActivity);
      alert.setTitle(ResourceMap.getString_confirm_title())
        .setMessage(message)
        .setPositiveButton(ResourceMap.getString_ensure(), 
        new DialogInterface.OnClickListener()
      {
        public void onClick(DialogInterface dialog, int which)
        {
          result.confirm();
        }
      }).setNegativeButton(ResourceMap.getString_cancel(), 
        new DialogInterface.OnClickListener()
      {
        public void onClick(DialogInterface dialog, int which)
        {
          result.cancel();
        }
      }).show();
      return true;
    }
  }

  private class MyWebViewClient extends WebViewClient
  {
    private MyWebViewClient()
    {
    }

    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error)
    {
      handler.proceed();
    }

    public void onFormResubmission(WebView view, Message dontResend, Message resend)
    {
    }

    public boolean shouldOverrideUrlLoading(WebView view, String url)
    {
      view.loadUrl(url);
      return true;
    }

    public void onLoadResource(WebView view, String url)
    {
    }

    public void onPageStarted(WebView view, String url, Bitmap favicon) {
      WapPayActivity.this.showLoading();
      WapPayActivity.this.mRefreshButton.setEnabled(false);
      WapPayActivity.this.mHandler.postDelayed(WapPayActivity.this.mDelayRunnable, WapPayActivity.this.mTimeout * 1000);
      super.onPageStarted(view, url, favicon);
    }

    public void onPageFinished(WebView view, String url)
    {
      WapPayActivity.this.dismissLoading();
      WapPayActivity.this.mRefreshButton.setEnabled(true);
      WapPayActivity.this.mHandler.removeCallbacks(WapPayActivity.this.mDelayRunnable);
      view.loadUrl("javascript:window.local_obj.showSource('<head>'+document.getElementsByTagName('html')[0].innerHTML+'</head>');");
    }
  }
}