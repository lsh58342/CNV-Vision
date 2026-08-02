package com.example.cnv.ui.calibration

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.cnv.R
import com.example.cnv.core.config.CalibrationConfig

class CalibrationFragment : Fragment() {

    private val viewModel: CalibrationViewModel by activityViewModels()

    private lateinit var sessionStatusText: TextView
    private lateinit var pixelDistanceText: TextView
    private lateinit var mmPerPixelText: TextView
    private lateinit var realDistanceInput: EditText

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!isAdded) {
                return
            }
            viewModel.refresh()
            refreshHandler.postDelayed(this, SESSION_REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_calibration, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionStatusText = view.findViewById(R.id.text_session_status)
        pixelDistanceText = view.findViewById(R.id.text_pixel_distance)
        mmPerPixelText = view.findViewById(R.id.text_mm_per_pixel)
        realDistanceInput = view.findViewById(R.id.input_real_distance)

        val preview = view.findViewById<PreviewView>(R.id.calibration_preview)
        (requireActivity() as CalibrationActivity).startCameraPipeline(preview)

        view.findViewById<Button>(R.id.button_start_calibration).setOnClickListener {
            viewModel.startCalibration()
        }
        view.findViewById<Button>(R.id.button_save_calibration).setOnClickListener {
            val realMm = realDistanceInput.text.toString().toFloatOrNull() ?: 0f
            if (viewModel.finishCalibration(realMm)) {
                syncDrawingCalibrationReady()
            }
        }
        view.findViewById<Button>(R.id.button_cancel_calibration).setOnClickListener {
            viewModel.cancelCalibration()
        }
        view.findViewById<Button>(R.id.button_reset_calibration).setOnClickListener {
            viewModel.resetCalibration()
        }

        viewModel.sessionPixel.observe(viewLifecycleOwner) { px ->
            pixelDistanceText.text = getString(R.string.calibration_session_pixel_value, px)
        }
        viewModel.mmPerPixel.observe(viewLifecycleOwner) { scale ->
            mmPerPixelText.text = getString(R.string.calibration_mm_per_pixel_value, scale)
        }
        viewModel.sessionActive.observe(viewLifecycleOwner) { active ->
            sessionStatusText.text = getString(
                if (active) {
                    R.string.calibration_session_active
                } else {
                    R.string.calibration_session_inactive
                },
            )
        }
        viewModel.statusMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNotBlank()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.refresh()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        refreshHandler.post(refreshRunnable)
        view?.findViewById<PreviewView>(R.id.calibration_preview)?.let { preview ->
            (activity as? CalibrationActivity)?.startCameraPipeline(preview)
        }
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    override fun onDestroyView() {
        (activity as? CalibrationActivity)?.stopCameraPipeline()
        super.onDestroyView()
    }

    /**
     * Wire CalibrationManager finish → Drawing-scoped ready flag + mmPerPixel (STEP 20-5).
     */
    private fun syncDrawingCalibrationReady() {
        val catalog = com.example.cnv.factory.repository.FactoryCatalog.get()
        val ctx = com.example.cnv.factory.context.CurrentContext.get()
        val drawing = catalog.drawings.current(ctx) ?: return
        if (drawing.routeLocked || !drawing.originSet) return
        val mm = com.example.cnv.config.CalibrationManager
            .getInstance(requireContext())
            .getMmPerPixel()
            .takeIf { it > 0f }
        catalog.calibrations.put(
            com.example.cnv.factory.repository.CalibrationRepository.CalibrationRef(
                drawingId = drawing.id,
                calibrationVersion = 1,
                mmPerPixel = mm,
                ready = true,
            ),
        )
        catalog.drawings.upsert(
            drawing.copy(calibrationReady = true, updatedAtMs = System.currentTimeMillis()),
        )
    }

    companion object {
        private val SESSION_REFRESH_INTERVAL_MS =
            CalibrationConfig.DEFAULT_SESSION_UI_REFRESH_MS
    }
}
