package com.dicoding.picodiploma.loginwithanimation.view.adStory

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModelProvider
import com.dicoding.picodiploma.loginwithanimation.BuildConfig
import com.dicoding.picodiploma.loginwithanimation.R
import com.dicoding.picodiploma.loginwithanimation.createCustomTempFile
import com.dicoding.picodiploma.loginwithanimation.data.pref.UserPreference
import com.dicoding.picodiploma.loginwithanimation.databinding.ActivityAddStoryBinding
import com.dicoding.picodiploma.loginwithanimation.reduceFileImage
import com.dicoding.picodiploma.loginwithanimation.remote.ApiConfig
import com.dicoding.picodiploma.loginwithanimation.showToast
import com.dicoding.picodiploma.loginwithanimation.uriToFile
import com.dicoding.picodiploma.loginwithanimation.ViewModelFactory
import com.dicoding.picodiploma.loginwithanimation.getImageUri
import com.dicoding.picodiploma.loginwithanimation.remote.response.AddStoriesResponse
import com.dicoding.picodiploma.loginwithanimation.view.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.lang.Boolean
import java.util.concurrent.Executors

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AddStoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddStoryBinding
    private var currentImageUri: Uri? = null

    private lateinit var addStoryViewModel: AddStoryViewModel
    private var file: File? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddStoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupView()
        setupViewModel()
        if (!allPermissionGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        binding.apply {
            btnCamera.setOnClickListener {
                startTakePhoto()
            }
            btnGallery.setOnClickListener {
                startGallery()
            }
            btnUploadImage.isEnabled = Boolean.TRUE
            btnUploadImage.setOnClickListener {
                uploadImage()
            }
        }
    }

    private fun setupViewModel() {
        addStoryViewModel = ViewModelProvider(
            this,
            ViewModelFactory(UserPreference.getInstance(dataStore))
        )[AddStoryViewModel::class.java]
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
        supportActionBar?.hide()
    }

    private val launcherIntentCamera = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { isSuccess ->
        if (isSuccess) {
            currentImageUri?.let{
                val myFile = uriToFile(it, this@AddStoryActivity)
                file = myFile
                binding.imgUploadStory.setImageURI(it)
            }
        }
    }

    private fun startTakePhoto() {
        currentImageUri = getImageUri(this)
        launcherIntentCamera.launch(currentImageUri)
    }

    private val launcherIntentGallery = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val selectedImg: Uri = uri
            val myFile = uriToFile(selectedImg, this@AddStoryActivity)
            file = myFile
            binding.imgUploadStory.setImageURI(selectedImg)
        } else {
            Log.d("Photo Picker", "No media selected")
        }
    }


    private fun startGallery() {
        launcherIntentGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun uploadImage() {
        if (file != null) {
            val file = reduceFileImage(file as File)
            val requestImageFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val imageMultipart: MultipartBody.Part = MultipartBody.Part.createFormData(
                "photo",
                file.name,
                requestImageFile
            )
            uploadToServer(imageMultipart)

        } else {
            Toast.makeText(
                this@AddStoryActivity,
                getString(R.string.please_enter_picture),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionGranted()) {
                this.showToast(getString(R.string.not_getting_permission))
                finish()
            }
        }
    }

    private fun allPermissionGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
    }

    private fun uploadToServer(img: MultipartBody.Part) {
        binding.apply {
            val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            val scope = CoroutineScope(dispatcher)
            val description =
                edAddDescription.text.trim().toString().toRequestBody("text/plain".toMediaType())
            scope.launch {
                val token = "Bearer ${addStoryViewModel.getUserToken()}"
                withContext(Dispatchers.Main) {
                    val service = ApiConfig().getApiService().postStory(token, img, description)
                    service.enqueue(object : Callback<AddStoriesResponse> {
                        override fun onResponse(
                            call: Call<AddStoriesResponse>,
                            response: Response<AddStoriesResponse>
                        ) {
                            if (response.isSuccessful) {
                                val responseBody = response.body()
                                if (responseBody != null && !responseBody.error!!) {
                                    Intent(
                                        this@AddStoryActivity,
                                        MainActivity::class.java
                                    ).also { intent ->
                                        intent.putExtra(MainActivity.SUCCESS_UPLOAD_STORY, true)
                                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        startActivity(intent)
                                    }
                                }
                            } else {
                                Toast.makeText(
                                    this@AddStoryActivity,
                                    response.message(),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onFailure(call: Call<AddStoriesResponse>, t: Throwable) {
                            Toast.makeText(
                                this@AddStoryActivity,
                                getString(R.string.network_unavailable),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    })
                }
            }
        }
    }
}