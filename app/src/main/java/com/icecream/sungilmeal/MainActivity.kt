package com.icecream.sungilmeal

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.icecream.sungilmeal.databinding.ActivityMainBinding
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


open class MainActivity : AppCompatActivity() {
    private var isLoaded: Boolean = false
    private var doubleBackToExitPressedOnce = false
    private var webURL = "https://sungil.vercel.app"
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var googleSignInClient: GoogleSignInClient
    private var cameraPath = ""
    private var mWebViewImageUpload: ValueCallback<Array<Uri>>? = null

    lateinit var binding: ActivityMainBinding
    val getResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    }


    @RequiresApi(33)
    private fun checkPermissions() {
        val notiPermission =
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
        if (notiPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.size > 0) {
                grantResults.forEach {
                    if (it != PackageManager.PERMISSION_GRANTED) {
                        MaterialAlertDialogBuilder(this@MainActivity, R.style.AlertDialogTheme)
                            .setTitle("권한을 허용해주세요")
                            .setMessage("앱 기능 사용을 위해 알림 권한을 허용해주세요.")
                            .setPositiveButton(
                                android.R.string.ok
                            ) { dialog, which ->
                                val intent = Intent()
                                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                val uri = Uri.fromParts("package", packageName, null)
                                intent.data = uri
                                startActivity(intent)
                            }
                            .setCancelable(false)
                            .create()
                            .show()
                    }
                }
            }
        }
    }


    //파일첨부 결과
    val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val intent = result.data

                if (intent == null) { //바로 사진을 찍어서 올리는 경우
                    val results = arrayOf(Uri.parse(cameraPath))
                    mWebViewImageUpload!!.onReceiveValue(results)
                } else { //사진 앱을 통해 사진을 가져온 경우
                    val results = intent!!.data!!
                    mWebViewImageUpload!!.onReceiveValue(arrayOf(results))
                }
            } else { //취소 한 경우 초기화
                mWebViewImageUpload!!.onReceiveValue(null)
                mWebViewImageUpload = null
            }
        }

    @SuppressLint("SetJavaScriptEnabled", "ResourceAsColor")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val context = applicationContext

        if (Build.VERSION.SDK_INT >= 33) {
            checkPermissions()
        }

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
                    1
                )
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
        binding.webView.addJavascriptInterface(WebAppInterface(this, binding.webView), "Android")
        val userAgent = binding.webView.settings.userAgentString
        binding.webView.settings.userAgentString =
            "${userAgent}/hybridApp${BuildConfig.VERSION_CODE}"
        loadWebView()

        if (!isOnline()) {
            showNoNetSnackBar()
            return
        }
    }


    fun updateSelectedDate(result : String) {
        Log.e("taein", result)
        webView.evaluateJavascript("javascript:alert('hi')", null)
    }

    override fun onResume() {
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
                        if (customUrlEndIndex < 0) {
                            return false
                        } else {
                            try {
                                val resultUrl =
                                    url.substring(customUrlStartIndex, customUrlEndIndex)
                                val intent = Intent.parseUri(resultUrl, Intent.URI_INTENT_SCHEME)
                                startActivity(intent)
                                return true
                            } catch (e: ActivityNotFoundException) {
                                // 카카오 링크가 포함된 경우
                                if (url.contains("kakaolink://send")) {
                                    (Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(GOOGLE_PLAY_STORE_PREFIX + "com.kakao.talk")
                                    ))
                                    return true
                                }
                                val packageStartIndex = customUrlEndIndex + PROTOCOL_INTENT.length
                                val packageEndIndex = url.indexOf(PROTOCOL_END)

                                val packageName = url.substring(
                                    packageStartIndex,
                                    if (packageEndIndex < 0) url.length else packageEndIndex
                                )
                                startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(GOOGLE_PLAY_STORE_PREFIX + packageName)
                                    )
                                )
                            }
                            return true
                        }
                    } else if (url.startsWith("tel:")) {
                        //tel:01000000000
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse(url))
                        startActivity(intent)
                        return true
                    } else if (url.startsWith("mailto:")) {
                        val i = Intent(Intent.ACTION_SENDTO, Uri.parse(url))
                        startActivity(i)
                        return true
                    } else if (url.startsWith("https://ssoak-72f93") || url.startsWith("https://accounts.google.com/")) { //로그인
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
                    if (url.startsWith("https://sungil.vercel.app/board.html")) {
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

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                onJsAlert(message!!, result!!)
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                onJsConfirm(message!!, result!!)
                return true
            }

            override fun onProgressChanged(view: WebView?, progress: Int) {
                binding.progressBar2.setProgress(progress)
                if (progress == 100) {
                    binding.progressBar2.visibility = View.INVISIBLE
                    binding.progressBar.visibility = View.INVISIBLE
                    binding.webView.visibility = View.VISIBLE
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                try {
                    mWebViewImageUpload = filePathCallback!!
                    var takePictureIntent: Intent?
                    takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    if (takePictureIntent.resolveActivity(packageManager) != null) {

                        val photoFile: File? = createImageFile()
                        takePictureIntent.putExtra("PhotoPath", cameraPath)

                        if (photoFile != null) {
                            cameraPath = "file:${photoFile.absolutePath}"
                            takePictureIntent.putExtra(
                                MediaStore.EXTRA_OUTPUT,
                                Uri.fromFile(photoFile)
                            )
                        } else takePictureIntent = null
                    }
                    val contentSelectionIntent =
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    contentSelectionIntent.type = "image/*"

                    val intentArray: Array<Intent?> =
                        if (takePictureIntent != null) arrayOf(takePictureIntent)
                        else takePictureIntent?.get(0)!!

                    val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                    chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                    chooserIntent.putExtra(Intent.EXTRA_TITLE, "첨부할 사진을 선택해주세요.")
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
                    launcher.launch(chooserIntent)
                } catch (e: Exception) {
                }
                return true
            }
        }
    }

    fun createImageFile(): File? {
        @SuppressLint("SimpleDateFormat")
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "img_" + timeStamp + "_"
        val storageDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }


    fun onJsAlert(message: String, result: JsResult): Unit {
        MaterialAlertDialogBuilder(this@MainActivity, R.style.AlertDialogTheme)
            .setTitle("")
            .setMessage(message)
            .setPositiveButton(
                android.R.string.ok
            ) { dialog, which -> result.confirm() }
            .setCancelable(true)
            .create()
            .show()
    }


    fun onJsConfirm(message: String, result: JsResult): Unit {
        MaterialAlertDialogBuilder(this@MainActivity, R.style.AlertDialogTheme)
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
                finish();
            }
            else -> {
                doubleBackToExitPressedOnce = true
                if (binding.webView.url == "https://sungil.vercel.app/" || binding.webView.url == "https://sungil.vercel.app" || binding.webView.url == "https://sungil.vercel.app/index.html") {
                    binding.webView.loadUrl("javascript:toast('앱을 종료하려면 뒤로가기를 한 번 더 눌러주세요')");
                } else {
                    val snack =
                        Snackbar.make(
                            binding.rootView,
                            "뒤로가기 버튼을 한 번 더 누르면 앱이 종료됩니다.",
                            Snackbar.LENGTH_SHORT
                        )
                    setSnackBarOption(snack) // 스낵바 옵션 설정
                    snack.show()
                }
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
        MaterialAlertDialogBuilder(this@MainActivity, R.style.AlertDialogTheme)
            .setTitle("인터넷 연결 오류")
            .setMessage("인터넷 연결 상태가 원활하지 않아 서비스 이용이 불가능합니다. 잠시 후 다시 시도해주세요.")
            .setPositiveButton(
                "설정"
            ) { dialog, which ->
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                finish()
            }
            .setNegativeButton(
                "종료"
            ) { dialog, which -> finish() }
            .setCancelable(true)
            .create()
            .show()
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
                val snack = Snackbar.make(
                    binding.rootView,
                    "$personName 님의 구글 계정으로 로그인합니다.",
                    Snackbar.LENGTH_SHORT
                );
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