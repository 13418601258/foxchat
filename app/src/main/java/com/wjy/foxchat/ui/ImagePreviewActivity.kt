package com.wjy.foxchat.ui

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.wjy.foxchat.databinding.ActivityImagePreviewBinding
import java.io.File

class ImagePreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImagePreviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
        val parsed = Uri.parse(path)
        binding.ivImage.setImageURI(
            if (parsed.scheme.isNullOrBlank()) Uri.fromFile(File(path)) else parsed
        )
    }

    companion object {
        const val EXTRA_PATH = "image_path"
    }
}
