package com.example.weatherapp

import android.annotation.SuppressLint
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
import com.google.gson.internal.GsonBuildConfig
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.util.jar.Manifest

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient : FusedLocationProviderClient

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

          listCall.enqueue(object : Callback<WeatherResponse> {
              override fun onResponse(
                  call: Call<WeatherResponse>,
                  response: Response<WeatherResponse>) {

                  if(response!!.isSuccessful){
                      val weatherList : WeatherResponse? = response.body()
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
              }

          })

        }else{
            Toast.makeText(this@MainActivity, "You haven't connected with the internet.", Toast.LENGTH_SHORT).show()
        }
    }
}