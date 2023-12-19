package com.dicoding.picodiploma.loginwithanimation.view.signup

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dicoding.picodiploma.loginwithanimation.R
import com.dicoding.picodiploma.loginwithanimation.databinding.ActivitySignupBinding
import com.dicoding.picodiploma.loginwithanimation.hideSoftKeyboard
import com.dicoding.picodiploma.loginwithanimation.remote.ApiConfig
import com.dicoding.picodiploma.loginwithanimation.remote.response.RegisterResponse
import com.dicoding.picodiploma.loginwithanimation.showAlertLoading
import com.dicoding.picodiploma.loginwithanimation.view.login.LoginActivity
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.Observable
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SignupActivity : AppCompatActivity() {

    private lateinit var loading: AlertDialog
    private lateinit var binding: ActivitySignupBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loading = showAlertLoading(this)
        processedAccount()
        setupView()
        playAnimation()
    }

    private fun playAnimation() {
        val title = ObjectAnimator.ofFloat(binding.titleTextView, View.ALPHA, 1f).setDuration(300)
        val nameTxtView =
            ObjectAnimator.ofFloat(binding.nameEditTextLayout, View.ALPHA, 1f).setDuration(300)
        val nameEditTxt =
            ObjectAnimator.ofFloat(binding.nameEditText, View.ALPHA, 1f).setDuration(300)
        val emailTxtView =
            ObjectAnimator.ofFloat(binding.emailEditTextLayout, View.ALPHA, 1f).setDuration(300)
        val emailEditTxt =
            ObjectAnimator.ofFloat(binding.emailEditText, View.ALPHA, 1f).setDuration(300)
        val passwordTxtView =
            ObjectAnimator.ofFloat(binding.passwordEditTextLayout, View.ALPHA, 1f).setDuration(300)
        val passwordEditTxt =
            ObjectAnimator.ofFloat(binding.passwordEditText, View.ALPHA, 1f).setDuration(300)
        val signUpButton =
            ObjectAnimator.ofFloat(binding.signupButton, View.ALPHA, 1f).setDuration(300)

        ObjectAnimator.ofFloat(binding.imageView, View.TRANSLATION_X, -30f, 30f).apply {
            duration = 5000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }.start()
        AnimatorSet().apply {
            playSequentially(
                title,
                nameTxtView,
                nameEditTxt,
                emailTxtView,
                emailEditTxt,
                passwordTxtView,
                passwordEditTxt,
                signUpButton,
            )
            startDelay = 600
            start()
        }
    }

    private fun setupView() {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
        binding.tvLogin.setOnClickListener {
            Intent(this, LoginActivity::class.java).also { intent ->
                startActivity(intent)
                finish()
            }
        }
        supportActionBar?.hide()
    }

    @SuppressLint("CheckResult")
    private fun processedAccount() {
        binding.apply {
            val nameStream = RxTextView.textChanges(nameEditText)
                .skipInitialValue()
                .map {
                    nameEditText.error != null
                }

            val emailStream = RxTextView.textChanges(emailEditText)
                .skipInitialValue()
                .map {
                    emailEditText.error != null
                }

            val passwordStream = RxTextView.textChanges(passwordEditText)
                .skipInitialValue()
                .map {
                    passwordEditText.error != null
                }

            val invalidFieldStream = Observable.combineLatest(
                nameStream,
                emailStream,
                passwordStream
            ) { nameInvalid, emailInvalid, passwordInvalid ->
                !nameInvalid && !emailInvalid && !passwordInvalid
            }
            invalidFieldStream.subscribe { isValid ->
                signupButton.isEnabled = isValid
            }
            signupButton.setOnClickListener { signUp() }
        }
    }

    private fun signUp() {
        binding.apply {
            hideSoftKeyboard(this@SignupActivity, binding.root)
            val name = nameEditText.text?.trim().toString()
            val email = emailEditText.text?.trim().toString()
            val password = passwordEditText.text?.trim().toString()
            when {
                name.isEmpty() -> {
                    nameEditTextLayout.error = getString(R.string.enter_your_name)
                }
                email.isEmpty() -> {
                    emailEditTextLayout.error = getString(R.string.enter_your_email)
                }
                password.isEmpty() -> {
                    passwordEditTextLayout.error = getString(R.string.enter_your_password)
                }
                else -> {
                    loading.show()
                    signUptoServer(name, email, password)
                }
            }
        }
    }

    private fun signUptoServer(name: String, email: String, password: String) {
        val service = ApiConfig().getApiService().postRegister(name, email, password)
        service.enqueue(object : Callback<RegisterResponse> {
            override fun onResponse(
                call: Call<RegisterResponse>,
                response: Response<RegisterResponse>
            ) {
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null && !responseBody.error) {
                        continueRegister()
                        loading.dismiss()
                    }
                } else {
                    Log.e(ContentValues.TAG, "onResponse: " + response.message())
                    Toast.makeText(
                        this@SignupActivity,
                        getString(R.string.signup_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    loading.dismiss()
                }
            }

            override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                Toast.makeText(
                    this@SignupActivity,
                    getString(R.string.network_unavailable),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
    private fun continueRegister() {

        AlertDialog.Builder(this).apply {
            setTitle("Yeah!")
            setMessage("Akun sudah jadi nih. Yuk, login dan belajar coding.")
            setPositiveButton("Lanjut") { _, _ ->
                finish()
            }
            create()
            show()
        }
    }
}
