package com.plcoding.androidstorage

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.plcoding.androidstorage.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding
    private lateinit var internalStoragePhotoAdapter: InternalStoragePhotoAdapter
    private lateinit var externalStoragePhotoAdapter: SharedPhotoAdapter

    private var readPermissionGranted = false
    private var writePermissionGranted = false

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        internalStoragePhotoAdapter = InternalStoragePhotoAdapter {
            val deleteSuccessful = deletePhotoFromInternalStorage(it.name)
            if (deleteSuccessful){
                loadPhotosFromInternalStorageToRecyclerView()
                Toast.makeText(this, "photo successfully deleted.", Toast.LENGTH_SHORT).show()
            }else
                Toast.makeText(this, "Failed to delete photo.", Toast.LENGTH_SHORT).show()
        }

        val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()){
            val isPrivate = binding.switchPrivate.isChecked
            val saveSuccessfully = when{
                isPrivate -> savePhotoToInternalStorage(UUID.randomUUID().toString(), it)
                writePermissionGranted -> savePhotoToExternalStorage(UUID.randomUUID().toString(), it)
                else -> false
            }

            if (isPrivate)
                loadPhotosFromInternalStorageToRecyclerView()

            if (saveSuccessfully){
                Toast.makeText(this, "photo save successfully", Toast.LENGTH_SHORT).show()
            }else{
                Toast.makeText(this, "Failed to save photo", Toast.LENGTH_SHORT).show()
            }

        }

        externalStoragePhotoAdapter = SharedPhotoAdapter {

        }

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ permission ->
            readPermissionGranted = permission[Manifest.permission.READ_EXTERNAL_STORAGE]?: readPermissionGranted
            writePermissionGranted = permission[Manifest.permission.WRITE_EXTERNAL_STORAGE]?: writePermissionGranted
        }

        updateOrRequestPermission()

        binding.btnTakePhoto.setOnClickListener {
            takePhoto.launch()
        }

        setupInternalStorageRecyclerView()
        loadPhotosFromInternalStorageToRecyclerView()
    }

    private fun setupInternalStorageRecyclerView() = binding.rvPrivatePhotos.apply {
        adapter = internalStoragePhotoAdapter
        layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }

    private fun loadPhotosFromInternalStorageToRecyclerView(){
        lifecycleScope.launch {
            val photos = loadPhotosFromInternalStorage()
            internalStoragePhotoAdapter.submitList(photos)
        }
    }

    private fun deletePhotoFromInternalStorage(filename: String): Boolean{
        return try {
            deleteFile(filename)
        }catch (e: IOException){
            e.printStackTrace()
            false
        }
    }

    private suspend fun loadPhotosFromInternalStorage(): List<InternalStoragePhoto>{
        return withContext(Dispatchers.IO){
            val files = filesDir.listFiles()
            files?.filter { it.canRead() && it.isFile && it.name.endsWith(".jpg") }?.map {
                val bytes = it.readBytes()
                val btm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                InternalStoragePhoto(it.name, btm)
            }?: listOf()
        }
    }

    private fun savePhotoToInternalStorage(filename: String, btm:Bitmap): Boolean{
        return try {
            openFileOutput("$filename.jpg", MODE_PRIVATE).use { stream ->
                if (!btm.compress(Bitmap.CompressFormat.JPEG, 95, stream)){
                    throw IOException("Couldn't save bitmap.")
                }
            }
            true
        }catch (e: IOException){
            e.printStackTrace()
            false
        }
    }

    private fun savePhotoToExternalStorage(displayName: String, btm: Bitmap): Boolean{
        val imageCollection = sdk29AndUp {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val contentValue = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, btm.width)
            put(MediaStore.Images.Media.HEIGHT, btm.height)
        }

        return try {
            contentResolver.insert(imageCollection, contentValue)?.also { uri ->
                contentResolver.openOutputStream(uri).use { outputStream ->
                    if(!btm.compress(Bitmap.CompressFormat.JPEG, 95, outputStream))
                        throw IOException("couldn't save bitmap")
                }
            }?: throw IOException("couldn't create mediaStore entry")
            true
        }catch (e: IOException){
            e.printStackTrace()
            false
        }

    }

    private fun updateOrRequestPermission(){
        val hasReadPermission = ContextCompat.checkSelfPermission(
            this,
        Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        val hasWritePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val minSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        readPermissionGranted = hasReadPermission
        writePermissionGranted = hasWritePermission || minSdk29

        val permissionToRequest = mutableListOf<String>()
        if(!writePermissionGranted)
            permissionToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (!readPermissionGranted)
            permissionToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (permissionToRequest.isNotEmpty())
            permissionLauncher.launch(permissionToRequest.toTypedArray())

    }
}