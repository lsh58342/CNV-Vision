package com.example.cnv.ui.screens

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.cnv.R
import com.example.cnv.ui.navigation.CnvDestination

class SplashFragment : BaseScreenFragment() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_splash, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        siteVm.bootstrap()
        view.findViewById<TextView>(R.id.splash_status).text =
            getString(R.string.splash_restoring)
        handler.postDelayed({
            if (!isAdded) return@postDelayed
            nav().navigateClearTo(CnvDestination.FACTORY_SELECT)
        }, 600L)
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }
}
