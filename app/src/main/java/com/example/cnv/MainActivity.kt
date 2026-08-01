package com.example.cnv

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cnv.camera.CameraManager
import com.example.cnv.opencv.OpenCVManager

class MainActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var openCvManager: OpenCVManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val previewView = findViewById<PreviewView>(R.id.preview_view)
        val grayImageView = findViewById<ImageView>(R.id.opencv_gray_view)

        openCvManager = OpenCVManager(this, grayImageView)
        val analyzer = openCvManager.start()

        cameraManager = CameraManager(this, previewView)
        cameraManager.start(analyzer)
    }
}
