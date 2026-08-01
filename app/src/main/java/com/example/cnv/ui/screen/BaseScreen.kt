package com.example.cnv.ui.screen

import androidx.fragment.app.Fragment
import com.example.cnv.ui.navigation.NavHost
import com.example.cnv.ui.navigation.requireNavHost

/** Base for rebuild screens — navigation helpers only (no feature wiring). */
abstract class BaseScreen : Fragment() {
    protected fun nav(): NavHost = requireActivity().requireNavHost()
}
