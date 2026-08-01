package com.example.cnv.ui.calibration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.cnv.R

class CalibrationFragment : Fragment() {

    private val viewModel: CalibrationViewModel by activityViewModels()

    private lateinit var pixelDistanceText: TextView
    private lateinit var mmPerPixelText: TextView
    private lateinit var realDistanceInput: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_calibration, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pixelDistanceText = view.findViewById(R.id.text_pixel_distance)
        mmPerPixelText = view.findViewById(R.id.text_mm_per_pixel)
        realDistanceInput = view.findViewById(R.id.input_real_distance)

        view.findViewById<Button>(R.id.button_save_calibration).setOnClickListener {
            val realMm = realDistanceInput.text.toString().toFloatOrNull() ?: 0f
            viewModel.saveCalibration(realMm)
        }
        view.findViewById<Button>(R.id.button_reset_calibration).setOnClickListener {
            viewModel.resetCalibration()
        }

        viewModel.pixelDistance.observe(viewLifecycleOwner) { px ->
            pixelDistanceText.text = getString(R.string.calibration_pixel_distance_value, px)
        }
        viewModel.mmPerPixel.observe(viewLifecycleOwner) { scale ->
            mmPerPixelText.text = getString(R.string.calibration_mm_per_pixel_value, scale)
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
    }
}
