package com.icecream.sungilmeal

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import android.widget.TextView
import androidx.annotation.RequiresApi
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat


/**
 * Implementation of App Widget functionality.
 */
class MealWidget : AppWidgetProvider() {


    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }


    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

var mealWidgetManger: AppWidgetManager? = null
var mealWidgetId:Int = 0


internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    mealWidgetManger = appWidgetManager
    mealWidgetId = appWidgetId
    getTodayMeal(context)
}

  var mealResult = ""

fun getTodayMeal(context: Context) {
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

                    if(mealArr.length < 5) { //급식 없음
                        mealResult = "급식이 없어요"
                    } else {
                        val re = Regex("[0-9]")
                        mealResult = mealArr.replace("[중식]", "").replace(":", "").replace("\'", "").replace(".", "")
                        mealResult = re.replace(mealResult, "")
                    }
                    Log.e(TAG, mealResult)
            if(mealResult == null) {
                mealResult = "급식이 없어요"
            }

            val views = RemoteViews(context.packageName, R.layout.meal_widget)
            views.setTextViewText(R.id.appwidget_text, mealResult)

            // Instruct the widget manager to update the widget
            mealWidgetManger?.updateAppWidget(mealWidgetId, views)
        }

        override fun onFailure(call: Call, e: IOException) {
            println("API execute failed")
        }
    })
    }


}