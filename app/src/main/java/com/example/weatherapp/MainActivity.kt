package com.example.weatherapp

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient : FusedLocationProviderClient
    private var mProgressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if(!isLocationEnabled()){
            Toast.makeText(this, "Your location provider is turned off. Please turn it on.", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withContext(this)
                .withPermissions(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {

                            requestLocationData()

                        }
                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(this@MainActivity, "You have denied location permission. Please enable them as it is mandatory for the app to work.", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissions: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                       showRationalDialogForPermissions()
                    }

                }).onSameThread()
                .check()

        }
    }
    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permission required for the feature. It can be enabled under Application Settings")
            .setPositiveButton("GO TO SETTINGS"){_, _ ->
                try{
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package",packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e : ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("CANCEL"){dialog,_ ->
                dialog.dismiss()
            }.show()
    }


    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            val mLastLocation : Location = locationResult!!.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("current Latitude","$latitude")
            val longitude = mLastLocation.longitude

            Log.i("current Latitude","$longitude")
            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun isLocationEnabled(): Boolean{
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }


    private fun getLocationWeatherDetails(latitude: Double, longitude: Double){
        if(Constants.isNetworkAvailable(this)){
          val retrofit : Retrofit = Retrofit.Builder()
              .baseUrl(Constants.BASE_URL)
              .addConverterFactory(GsonConverterFactory.create())
              .build()

          val service : WeatherService = retrofit
              .create<WeatherService>(WeatherService::class.java)

          val listCall : Call<WeatherResponse> = service.getWeather(
              latitude , longitude, Constants.METRIC_UNIT, Constants.APP_ID
          )

          showCustomProgressDialog()

          listCall.enqueue(object : Callback<WeatherResponse> {
              override fun onResponse(
                  call: Call<WeatherResponse>,
                  response: Response<WeatherResponse>) {

                  if(response!!.isSuccessful){

                      hideCustomProgressDialog()
                      val weatherList: WeatherResponse = response.body()!!
                      setupUI(weatherList)
                      Log.i("Response Result ","$weatherList")
                  }else{
                      val rc = response.code()
                      when(rc){
                          400 -> Log.e("Error 400", "Bad Connection")
                          404 -> Log.e("Error 404", "Not found")
                          else ->{
                              Log.e("Error", "Generic Error")
                          }
                      }
                  }
              }

              override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                Log.e("Errorrrr", t!!.message.toString())
                  hideCustomProgressDialog()
              }

          })

        }else{
            Toast.makeText(this@MainActivity, "You haven't connected with the internet.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun hideCustomProgressDialog(){
        if(mProgressDialog!= null){
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI(weatherList: WeatherResponse?){
        for(i in weatherList!!.weather.indices){
            Log.e("Weather NAME",weatherList.weather.toString())

            tv_main.text = weatherList.weather[i].main
            tv_main_description.text = weatherList.weather[i].description
            tv_temp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString() )
            tv_sunrise_time.text = unixTime(weatherList.sys.sunrise)
            tv_sunset_time.text = unixTime(weatherList.sys.sunset)
            tv_humidity.text = weatherList.main.humidity.toString() + "per cent"
            tv_min.text = weatherList.main.temp_min.toString()+" min"
            tv_max.text = weatherList.main.temp_max.toString()+" max"
            tv_speed.text = weatherList.wind.speed.toString()
            tv_name.text = weatherList.name
            tv_country.text = weatherList.sys.country

            when(weatherList.weather[i].icon){
                "01d" -> iv_main.setImageResource(R.drawable.sunny)
                "02d" -> iv_main.setImageResource(R.drawable.cloud)
                "03d" -> iv_main.setImageResource(R.drawable.cloud)
                "04d" -> iv_main.setImageResource(R.drawable.cloud)
                "04n" -> iv_main.setImageResource(R.drawable.cloud)
                "10d" -> iv_main.setImageResource(R.drawable.rain)
                "11d" -> iv_main.setImageResource(R.drawable.storm)
                "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                "01n" -> iv_main.setImageResource(R.drawable.cloud)
                "02n" -> iv_main.setImageResource(R.drawable.cloud)
                "03n" -> iv_main.setImageResource(R.drawable.cloud)
                "10n" -> iv_main.setImageResource(R.drawable.cloud)
                "11n" -> iv_main.setImageResource(R.drawable.rain)
                "13n" -> iv_main.setImageResource(R.drawable.snowflake)
                else ->  iv_main.setImageResource(R.drawable.sunny)
            }


        }
    }

    private fun getUnit(value: String): String? {
        var value = "°C"
        if( "US" == value || "LR" == value ||  "MM" == value){
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String?{
        val date = Date(timex *1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

}