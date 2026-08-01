package com.example.cnv.ui.screen

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.cnv.ui.navigation.NavHost
import com.example.cnv.ui.navigation.requireNavHost
import com.example.cnv.ui.vm.SiteNavigationViewModel

/** Base for rebuild screens — navigation + site ViewModel. */
abstract class BaseScreen : Fragment() {

    protected val siteVm: SiteNavigationViewModel by activityViewModels()

    protected fun nav(): NavHost = requireActivity().requireNavHost()
}
