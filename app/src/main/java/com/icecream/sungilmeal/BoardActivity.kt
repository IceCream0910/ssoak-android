package com.icecream.sungilmeal

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentValues.TAG
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.browser.customtabs.CustomTabsIntent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.icecream.sungilmeal.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class BoardActivity : AppCompatActivity() {

    private var cameraPath = ""
    private var mWebViewImageUpload: ValueCallback<Array<Uri>>? = null

    //파일첨부 결과
    val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult())  { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = result.data

            if(intent == null){ //바로 사진을 찍어서 올리는 경우
                val results = arrayOf(Uri.parse(cameraPath))
                mWebViewImageUpload!!.onReceiveValue(results)
            }
            else{ //사진 앱을 통해 사진을 가져온 경우
                val results = intent!!.data!!
                mWebViewImageUpload!!.onReceiveValue(arrayOf(results))
            }
        }
        else{ //취소 한 경우 초기화
            mWebViewImageUpload!!.onReceiveValue(null)
            mWebViewImageUpload = null
        }
    }


    @SuppressLint("SetJavaScriptEnabled")
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

        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                onJsAlert(message!!, result!!)
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                onJsConfirm(message!!, result!!)
                return true
            }

            @SuppressLint("QueryPermissionsNeeded", "IntentReset")
            override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                try{
                    mWebViewImageUpload = filePathCallback!!
                    var takePictureIntent : Intent?
                    takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    if(takePictureIntent.resolveActivity(packageManager) != null){

                        val photoFile : File? = createImageFile()
                        takePictureIntent.putExtra("PhotoPath",cameraPath)

                        if(photoFile != null){
                            cameraPath = "file:${photoFile.absolutePath}"
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,Uri.fromFile(photoFile))
                        }
                        else takePictureIntent = null
                    }
                    val contentSelectionIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    contentSelectionIntent.type = "image/*"

                    val intentArray: Array<Intent?> = if(takePictureIntent != null) arrayOf(takePictureIntent)
                    else takePictureIntent?.get(0)!!

                    val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                    chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                    chooserIntent.putExtra(Intent.EXTRA_TITLE,"첨부할 사진을 선택해주세요.")
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
                    launcher.launch(chooserIntent)
                }
                catch (e : Exception){ }
                return true
            }
        }

    }


    fun createImageFile(): File? {
        @SuppressLint("SimpleDateFormat")
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "img_" + timeStamp + "_"
        val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }


    fun onJsAlert(message : String, result : JsResult) : Unit{
        MaterialAlertDialogBuilder(this@BoardActivity, R.style.AlertDialogTheme)
            .setTitle("")
            .setMessage(message)
            .setPositiveButton(
                android.R.string.ok
            ) { dialog, which -> result.confirm() }
            .setCancelable(true)
            .create()
            .show()
    }


    fun onJsConfirm(message : String, result : JsResult) : Unit {
        MaterialAlertDialogBuilder(this@BoardActivity, R.style.AlertDialogTheme)
            .setTitle("")
            .setMessage(message)
            .setPositiveButton(
                android.R.string.ok
            ) { dialog, which -> result.confirm() }
            .setNegativeButton(
                android.R.string.cancel
            ) { dialog, which -> result.cancel() }
            .setCancelable(false)
            .create()
            .show()
    }

    override fun onPause() {
        super.onPause()
    }

}