package com.icecream.sungilmeal

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.RequiresApi
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.android.synthetic.main.activity_main.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class WebAppInterface(val mContext: Context, var mWebView: WebView) : MainActivity() {
    @JavascriptInterface
    fun setNotiEnable(result: Boolean) {
        if (result) { //급식 푸시 알림 on
            addFcmTokenToMealPush()
        } else { //급식 푸시 알림 off
            deleteFcmTokenFromMealPush()
        }
    }

    @JavascriptInterface
    fun logoutAndroidApp() {
        Log.e(ContentValues.TAG, "logout 요청")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @JavascriptInterface
    fun openDatePicker(initDate : String) {
        runOnUiThread {
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            val date = LocalDate.parse(initDate, formatter)
            val datePicker = DatePickerDialog(
                mContext,
                DatePickerDialog.OnDateSetListener { view, year, month, dayOfMonth ->
                    val result =
                        "${year}${(month + 1).toString().padStart(2, '0')}${dayOfMonth.toString().padStart(2, '0')}"
                    Log.e("teain", result)
                    mWebView.loadUrl("javascript:androidDatePickerCallback('${result}')")
                },
                date.year,
                date.monthValue-1,
                date.dayOfMonth
            );
            datePicker.show()
            datePicker.getButton(DatePickerDialog.BUTTON_POSITIVE)
                .setTextColor(Color.parseColor("#5271FF"));
            datePicker.getButton(DatePickerDialog.BUTTON_NEGATIVE)
                .setTextColor(Color.parseColor("#5271FF"));
        }
    }

    @JavascriptInterface
    fun sendUserIdForFCM(userId: String) {
        updateFcmToken(userId)
    }


    private fun updateFcmToken(userId: String) { //fcm 토큰 user DB에 업데이트
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(ContentValues.TAG, "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            val token = task.result
            Log.e(ContentValues.TAG, "get fcm token : $token")
            val db = Firebase.firestore
            val usersRef = db.collection("users").document(userId)
            usersRef
                .update("fcmToken", token)
                .addOnSuccessListener { Log.e(ContentValues.TAG, "update fcm token$token") }
                .addOnFailureListener { e -> Log.e(ContentValues.TAG, "Error updating document", e) }
        })
    }

    private fun addFcmTokenToMealPush() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(ContentValues.TAG, "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }
            val token = task.result
            Log.e(ContentValues.TAG, "get fcm token for meal push: $token")
            val db = Firebase.database
            val myRef = db.getReference("pushTokens")
            val map: MutableMap<String, Any> = HashMap()
            token.toString().also { map[token.substring(0, 10)] = it }
            myRef.child("meal").updateChildren(map)
        })
    }

    private fun deleteFcmTokenFromMealPush() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(ContentValues.TAG, "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }
            val token = task.result
            Log.e(ContentValues.TAG, "get fcm token for meal push: $token")
            val db = Firebase.database
            val myRef = db.getReference("pushTokens")
            myRef.child("meal").child(token.substring(0, 10)).removeValue()
        })
    }
}