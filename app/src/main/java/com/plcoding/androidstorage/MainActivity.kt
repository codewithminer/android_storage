package com.plcoding.androidstorage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
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
            if (isPrivate){
                val saveSuccessfully = savePhotoToInternalStorage(UUID.randomUUID().toString(), it)
                if (saveSuccessfully){
                    loadPhotosFromInternalStorageToRecyclerView()
                    Toast.makeText(this, "photo save successfully", Toast.LENGTH_SHORT).show()
                }else{
                    Toast.makeText(this, "Failed to save photo", Toast.LENGTH_SHORT).show()
                }

            }
        }

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
}