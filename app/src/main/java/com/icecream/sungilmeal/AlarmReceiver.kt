package com.icecream.sungilmeal

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.content.Context.MODE_PRIVATE
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class AlarmReceiver : BroadcastReceiver() {
    private var mealResult = ""
    override fun onReceive(context: Context, intent: Intent?) {
        getTodayMeal(context, intent)
    }

    private fun getTodayMeal(context: Context, intent: Intent?) {
        mealResult = ""
        val currentTime : Long = System.currentTimeMillis() // ms로 반환
        val yyyyMMddFormatter = SimpleDateFormat("yyyyMMdd") // 년 월 일
        val dFormatter = SimpleDateFormat("d")
        val apiDateRequeest = yyyyMMddFormatter.format(currentTime)
        val todayD = dFormatter.format(currentTime)

        val url = "https://sungil-school-api.vercel.app/api/"+apiDateRequeest
        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()
        with(client) {
            newCall(request).enqueue(object : Callback {
                @RequiresApi(Build.VERSION_CODES.S)
                override fun onResponse(call: Call, response: Response){

                    val str = response.body()!!.string()
                    val mealArr = JSONObject(str).getJSONObject("meal").getString(todayD.toString())

                    if(mealArr.length < 5) { //급식 없음 -> 전송 안함
                        mealResult = "급식이 없어요"
                    } else {
                        val re = Regex("[0-9]")
                        mealResult = mealArr.replace("[중식]", "").replace(":", "").replace("\'", "").replace(".", "")
                        mealResult = re.replace(mealResult, "")
                        sendNotification(context, intent, mealResult.replace("\n", ", ").substring(2))
                    }

                }

                override fun onFailure(call: Call, e: IOException) {
                    println("API execute failed")
                }
            })
        }


    }

    private fun sendNotification(context: Context, intent: Intent?, content: String){

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationIntent = Intent(context, MainActivity::class.java)
        notificationIntent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TOP
                or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingI = PendingIntent.getActivity(
            context, 0,
            notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val builder: Notification.Builder = Notification.Builder(context, "default")


        //OREO API 26 이상에서는 채널 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            builder.setSmallIcon(com.icecream.sungilmeal.R.drawable.app_icon); //mipmap 사용시 Oreo 이상에서 시스템 UI 에러남

            val channelName = "오늘의 급식 알림"
            val description = "매일 3교시 전에 오늘의 급식 메뉴를 푸시 알림으로 보내줄게요."
            val importance = NotificationManager.IMPORTANCE_HIGH //소리와 알림메시지를 같이 보여줌
            val channel = NotificationChannel("default", channelName, importance)
            channel.description = description
            notificationManager.createNotificationChannel(channel)
        }

        builder.setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setWhen(System.currentTimeMillis())
            .setTicker("{Time to watch some cool stuff!}")
            .setContentTitle("오늘의 급식")
            .setContentText(content)
            .setContentInfo("INFO")
            .setContentIntent(pendingI)

        notificationManager.notify(1234, builder.build())

        // 내일 같은 시간으로 알람시간 결정
        val nextNotifyTime = Calendar.getInstance()
        nextNotifyTime.timeInMillis = System.currentTimeMillis()
        nextNotifyTime[Calendar.HOUR_OF_DAY] = 10
        nextNotifyTime[Calendar.MINUTE] = 50
        nextNotifyTime[Calendar.SECOND] = 0
        nextNotifyTime.add(Calendar.DATE, 1)

        //  Preference에 설정한 값 저장
        val editor: SharedPreferences.Editor =
            context.getSharedPreferences("daily alarm", MODE_PRIVATE).edit()
        editor.putLong("nextNotifyTime", nextNotifyTime.timeInMillis)
        editor.apply()
        val currentDateTime: Date = nextNotifyTime.time
        val date_text: String =
            SimpleDateFormat("yyyy년 MM월 dd일 EE요일 a hh시 mm분 ", Locale.getDefault()).format(
                currentDateTime
            )
        Log.e("taein", "다음 알림 : "+date_text)
    }
}