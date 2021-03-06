package com.example.cnchat.screens

import android.app.ProgressDialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.cnchat.R
import android.content.Context;
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.cnchat.constants
import com.example.cnchat.retrofit.model.fcmTokenResponse
import com.example.cnchat.retrofit.model.loginRegisterResponse
import com.example.cnchat.retrofit.retrofitClient
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.android.synthetic.main.layout_login.*
import org.jetbrains.anko.share
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.lang.Exception


class login : AppCompatActivity() {

    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Redirecting to the chatList activity if user is already logged in
        val sharedPref = this@login.getSharedPreferences(constants.sharedPrefName, Context.MODE_PRIVATE)
        if(!sharedPref.getString(constants.token_name,"").equals("")){
            constants.token = sharedPref.getString(constants.token_name,"")!!
            constants.usersEmail = sharedPref.getString(constants.email,"")!!
            constants.usersFirstName = sharedPref.getString(constants.first_name,"")!!
            constants.usersLastName =  sharedPref.getString(constants.last_name,"")!!
            startActivity(Intent(this,chatList::class.java))
            finish()
        }

        //Now setting up the activity
        setContentView(R.layout.activity_login)

        cirLoginButton.setOnClickListener {
            if(editTextEmail.text.toString().isEmpty() || editTextPassword.text.toString().isEmpty()){
                Toast.makeText(this,"Enter both email and password ",Toast.LENGTH_SHORT).show()
            }
            else{
                performLogin()
            }
        }
        signUpTextView.setOnClickListener {
            startActivity(Intent(this,register::class.java))
        }
    }


    fun performLogin(){

        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Logging in..")
        progressDialog.show()

        val email = editTextEmail.text.toString()
        val pass = editTextPassword.text.toString()

        retrofitClient.retrofitService.loginUser(email,pass).enqueue(

                object : Callback<loginRegisterResponse> {
                    override fun onFailure(call: Call<loginRegisterResponse>, t: Throwable) {
                        progressDialog.dismiss()
                        t.printStackTrace()
                        Toast.makeText(this@login, t.message, Toast.LENGTH_SHORT).show()
                    }

                    override fun onResponse(call: Call<loginRegisterResponse>, response: Response<loginRegisterResponse>) {

                        if (response.code() == 201) {
                            val res = response.body()
                            if (res?.user != null) {

                                val sharedPref = this@login.getSharedPreferences(constants.sharedPrefName, Context.MODE_PRIVATE).edit()

                                //Saving the user's data in your local machine
                                constants.token = res.token!!
                                constants.usersEmail = res.user?.email
                                constants.usersFirstName = res?.user?.firstName
                                constants.usersLastName =  res?.user?.lastName
                                sharedPref.putString(constants.token_name, res.token)
                                sharedPref.putString(constants.first_name, res.user?.firstName)
                                sharedPref.putString(constants.last_name, res.user?.lastName)
                                sharedPref.putString(constants.email, res.user?.email)
                                sharedPref.apply()
                                sharedPref.commit()

                                //Also make sure that the user FCM token of the user is registered on the server
                                try {
                                    FirebaseMessaging.getInstance().token
                                            .addOnCompleteListener {
                                                if (!it.isSuccessful) {
                                                    progressDialog.dismiss()
                                                    Log.i("err Fetching FCM token", it.exception.toString())
                                                } else {
                                                    // Get new FCM registration token
                                                    val token = it.result
                                                    Log.i("FCM token", token.toString())
                                                    retrofitClient.retrofitService.updateFCMToken(constants.bearer + constants.token, token!!).enqueue(object : Callback<fcmTokenResponse> {
                                                        override fun onFailure(call: Call<fcmTokenResponse>, t: Throwable) {
                                                            Log.i("error in fcm login", t.message.toString())
                                                            progressDialog.dismiss()
                                                            Toast.makeText(applicationContext, t.message, Toast.LENGTH_SHORT).show()
                                                        }

                                                        override fun onResponse(call: Call<fcmTokenResponse>, response: Response<fcmTokenResponse>) {
                                                            progressDialog.dismiss()
                                                            if (response.isSuccessful) {
                                                                Toast.makeText(applicationContext, "Login Successful", Toast.LENGTH_SHORT).show()
                                                                startActivity(Intent(this@login, chatList::class.java))
                                                                finish()
                                                            } else {
                                                                Toast.makeText(applicationContext,"Error in saving fcm token",Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    })
                                                }
                                            }.addOnFailureListener {
                                                progressDialog.dismiss()
                                                Log.i("err Fetching FCM token", it.message.toString())
                                            }
                                } catch (e: Exception) {
                                    progressDialog.dismiss()
                                    Log.i("myException", e.message.toString())
                                }
                            } else {
                                progressDialog.dismiss()
                                Toast.makeText(this@login, "Error!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            progressDialog.dismiss()
                            val jsonObject = JSONObject(response.errorBody()?.string())
                            Toast.makeText(this@login, jsonObject.getString("message"), Toast.LENGTH_SHORT).show()
                        }
                    }
                })
    }
}