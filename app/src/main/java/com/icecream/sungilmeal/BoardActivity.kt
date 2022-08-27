package com.icecream.sungilmeal

import android.content.ActivityNotFoundException
import android.content.ContentValues.TAG
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.browser.customtabs.CustomTabsIntent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.snackbar.Snackbar

class BoardActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_board)
        val webView: WebView = findViewById(R.id.boardWebView)
        val rootView: LinearLayoutCompat = findViewById(R.id.rootView2)

        webView.setBackgroundColor(resources.getColor(R.color.webviewBackground, null))
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.allowFileAccess = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.supportMultipleWindows()
        webView.setHapticFeedbackEnabled(false)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        val userAgent = webView.settings.userAgentString
        webView.settings.userAgentString = "${userAgent}/hybridApp"
        WebView.setWebContentsDebuggingEnabled(true)

        webView.setWebChromeClient(object : WebChromeClient() {
            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window);
                finish();
                // 웹뷰 화면을 닫는다.
            }
        })

        val url = intent.getStringExtra("url")
        if (url != null) {
            webView.loadUrl(url)
        } else {
            Snackbar.make(rootView, "게시글을 불러오는 데 실패했습니다.", Snackbar.LENGTH_SHORT).show()
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url.toString()
                if (!url.startsWith("https://sungil.vercel.app")) { //외부 url
                    val PROTOCOL_START = "intent:"
                    val PROTOCOL_END = ";end;"
                    val PROTOCOL_INTENT = "#Intent;"
                    val GOOGLE_PLAY_STORE_PREFIX = "market://details?id="

                    if (url.startsWith(PROTOCOL_START)) {
                        val customUrlStartIndex = PROTOCOL_START.length
                        val customUrlEndIndex = url.indexOf(PROTOCOL_INTENT)
                        if(customUrlEndIndex< 0 ){
                            return false
                        }else{
                            try {
                                val resultUrl = url.substring(customUrlStartIndex, customUrlEndIndex)
                                val intent = Intent.parseUri(resultUrl, Intent.URI_INTENT_SCHEME)
                                startActivity(intent)
                                return true
                            } catch (e: ActivityNotFoundException) {
                                // 카카오 링크가 포함된 경우
                                if(url.contains("kakaolink://send")){
                                    (Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_PLAY_STORE_PREFIX + "com.kakao.talk")))
                                    return true
                                }
                                val packageStartIndex = customUrlEndIndex + PROTOCOL_INTENT.length
                                val packageEndIndex = url.indexOf(PROTOCOL_END)

                                val packageName = url.substring(packageStartIndex,if(packageEndIndex< 0) url.length else packageEndIndex)
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_PLAY_STORE_PREFIX + packageName)))
                            }
                            return true
                        }
                    } else if (url.startsWith("tel:")) {
                        //tel:01000000000
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse(url))
                        startActivity(intent)
                        return true
                    }else if (url.startsWith("mailto:")) {
                        val i = Intent(Intent.ACTION_SENDTO, Uri.parse(url))
                        startActivity(i)
                        return true
                    } else if (url.startsWith("https://ssoak-72f93")  || url.startsWith("https://accounts.google.com/")) { //로그인
                        Log.e(TAG, "login")
                        val gso =
                            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(getString(R.string.default_web_client_id))
                                .requestEmail()
                                .build()
                        val googleSignInClient = GoogleSignIn.getClient(applicationContext, gso)
                        val signInIntent = googleSignInClient.getSignInIntent();
                        startActivityForResult(signInIntent, 1024)
                        return true
                    } else {
                        val builder = CustomTabsIntent.Builder()
                        builder.setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                        val intent = builder.build()
                        intent.intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        intent.launchUrl(applicationContext, Uri.parse(url))
                        return true
                    }
                } else { //내부 url
                    view?.loadUrl(url)
                }
                return super.shouldOverrideUrlLoading(view, request)
            }
        }

    }


    override fun onPause() {
        super.onPause()
    }

}