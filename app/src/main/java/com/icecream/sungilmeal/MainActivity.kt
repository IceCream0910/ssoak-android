package com.icecream.sungilmeal

import android.annotation.SuppressLint
import android.content.*
import android.content.ContentValues.TAG
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.icecream.sungilmeal.databinding.ActivityMainBinding


open class MainActivity : AppCompatActivity() {
    private var isLoaded: Boolean = false
    private var doubleBackToExitPressedOnce = false
    private var webURL = "https://sungil.vercel.app"
    private lateinit var appUpdateManager : AppUpdateManager
    private lateinit var googleSignInClient : GoogleSignInClient


    private lateinit var binding: ActivityMainBinding
    val getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Log.e(TAG, it.toString())
    }

    @SuppressLint("SetJavaScriptEnabled", "ResourceAsColor")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val context = applicationContext
        appUpdateManager = AppUpdateManagerFactory.create(context)
// Returns an intent object that you use to check for an update.
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        Glide.with(this).load(R.drawable.loader).into(binding.progressBar);

        val gso =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        googleSignInClient = GoogleSignIn.getClient(applicationContext, gso)
        googleSignInClient.signOut()

// Checks that the platform will allow the specified type of update.
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                // This example applies an immediate update. To apply a flexible update
                // instead, pass in AppUpdateType.FLEXIBLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                // Request the update.
                Log.e(TAG, "업데이트 있음")
                appUpdateManager.startUpdateFlowForResult(
                    // Pass the intent that is returned by 'getAppUpdateInfo()'.
                    appUpdateInfo,
                    // Or 'AppUpdateType.FLEXIBLE' for flexible updates.
                    AppUpdateType.FLEXIBLE,
                    // The current activity making the update request.
                    this,
                    // Include a request code to later monitor this update request.
                    1)
            } else {
                Log.e(TAG, "업데이트 없음")
            }
        }

        binding.webView.setBackgroundColor(resources.getColor(R.color.webviewBackground, null))
        val settings = binding.webView.settings
        settings.javaScriptEnabled = true
        settings.allowFileAccess = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.supportMultipleWindows()
        binding.webView.isHapticFeedbackEnabled = false
        binding.webView.isFocusable = true
        binding.webView.isFocusableInTouchMode = true
        binding.webView.addJavascriptInterface(WebAppInterface(this), "Android")
        val userAgent = binding.webView.settings.userAgentString
        binding.webView.settings.userAgentString = "${userAgent}/hybridApp"
        WebView.setWebContentsDebuggingEnabled(true)


        if (!isOnline()) {
            showNoNetSnackBar()
            return
        }


        fun onJsConfirm(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult
        ): Boolean {
            AlertDialog.Builder(this@MainActivity)
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
            return true
        }

        fun onJsAlert(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult
        ): Boolean {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("")
                .setMessage(message)
                .setPositiveButton(
                    android.R.string.ok
                ) { dialog, which -> result.confirm() }
                .setCancelable(false)
                .create()
                .show()
            return true
        }

    }

    /** Instantiate the interface and set the context  */
    class WebAppInterface(private val mContext: Context) : MainActivity() {
        @JavascriptInterface
        fun logoutAndroidApp() {
            Log.e(TAG, "logout 요청")
        }

        @JavascriptInterface
        fun sendUserIdForFCM(userId : String) {
            Log.e(TAG, "receive user id from javascript : $userId")
            updateFcmToken(userId)
        }

        private fun updateFcmToken(userId : String) { //fcm 토큰 user DB에 업데이트
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@OnCompleteListener
                }

                val token = task.result
                Log.e(TAG, "get fcm token : $token")
                val db = Firebase.firestore
                val usersRef = db.collection("users").document(userId)
                usersRef
                    .update("fcmToken", token)
                    .addOnSuccessListener { Log.e(TAG, "update fcm token : $token") }
                    .addOnFailureListener { e -> Log.e(TAG, "Error updating document", e) }
            })
        }
    }

    override fun onResume() {
        if (isOnline() && !isLoaded) loadWebView()
        super.onResume()

        appUpdateManager
            .appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                // If the update is downloaded but not installed,
                // notify the user to complete the update.
                if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    Snackbar.make(binding.rootView, "업데이트가 완료되었습니다.", Snackbar.LENGTH_SHORT).show()
                }
            }
    }

    // Displays the snackbar notification and call to action.
    fun popupSnackbarForCompleteUpdate() {
        Snackbar.make(
            findViewById(R.id.rootView),
            "앱 업데이트가 완료되었습니다.",
            Snackbar.LENGTH_INDEFINITE
        ).apply {
            setAction("다시 시작") { appUpdateManager.completeUpdate() }
            show()
        }
    }

    private fun loadWebView() {
        showProgress(true)
        binding.infoTV.text = ""
        binding.webView.loadUrl(webURL)

        binding.webView.webViewClient = object : WebViewClient() {
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
                        val signInIntent = googleSignInClient.signInIntent;
                        startActivityForResult(signInIntent, 1024)
                        return true
                    } else {
                        val builder = CustomTabsIntent.Builder()
                        builder.setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                        val intent = builder.build()
                        intent.intent.flags = FLAG_ACTIVITY_NEW_TASK
                        intent.launchUrl(applicationContext, Uri.parse(url))
                        return true
                    }
                } else { //내부 url
                    if(url.startsWith("https://sungil.vercel.app/board.html")) {
                        val intent = Intent(applicationContext, BoardActivity::class.java)
                        intent.putExtra("url", url)
                        startActivity(intent)
                        overridePendingTransition(R.anim.slide_left_enter, R.anim.slide_left_exit)
                        return true
                    } else {
                        view?.loadUrl(url)
                    }
                }
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                showProgress(true)
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                isLoaded = true
                binding.progressBar.visibility = View.INVISIBLE
                binding.webView.visibility = View.VISIBLE
                super.onPageFinished(view, url)
            }


            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                isLoaded = false
                val errorMessage = "오류: $error"
                Log.e("error", errorMessage)
                showProgress(false)
                super.onReceivedError(view, request, error)
            }

        }

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    showToastToExit()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showToastToExit() {
        when {
            doubleBackToExitPressedOnce -> {
                onBackPressed()
            }
            else -> {
                doubleBackToExitPressedOnce = true
                val snack =
                    Snackbar.make(binding.rootView, "뒤로가기 버튼을 한 번 더 누르면 앱이 종료됩니다.", Snackbar.LENGTH_SHORT)
                setSnackBarOption(snack) // 스낵바 옵션 설정
                snack.show()
                Handler(Looper.myLooper()!!).postDelayed(
                    { doubleBackToExitPressedOnce = false },
                    2000
                )
            }
        }
    }

    // 스낵바 옵션 설정
    private fun setSnackBarOption(snackBar: Snackbar) {
        snackBar.animationMode = BaseTransientBottomBar.ANIMATION_MODE_SLIDE
    }

    private fun showProgress(visible: Boolean) {
        //progress?.apply { if (visible) show() else dismiss() }
    }

    private fun isOnline(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (capabilities != null) {
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_CELLULAR")
                    return true
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_WIFI")
                    return true
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_ETHERNET")
                    return true
                }
            }
        }
        return false
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showNoNetSnackBar() {
        val snack =
            Snackbar.make(binding.rootView, getString(R.string.no_internet), Snackbar.LENGTH_INDEFINITE)
        snack.setAction(getString(R.string.settings)) {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        snack.show()
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == 1024) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val acct = completedTask.getResult(ApiException::class.java)
            if (acct != null) {
                val personName = acct.displayName
                val credential = GoogleAuthProvider.getCredential(acct.idToken!!, null)
                val idToken = acct.idToken!!
                val snack = Snackbar.make(binding.rootView, "$personName 님의 구글 계정으로 로그인합니다.", Snackbar.LENGTH_SHORT);
                setSnackBarOption(snack) // 스낵바 옵션 설정
                snack.show()
                binding.webView.loadUrl("javascript:pushWebviewGoogleLoginToken('$idToken')");
            }
        } catch (e: ApiException) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.e("signInResult", "signInResult:failed code=" + e.statusCode)
        }
    }





}