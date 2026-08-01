package com.example.cnv.ui.screens

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.cnv.ui.navigation.NavHost
import com.example.cnv.ui.navigation.requireNavHost
import com.example.cnv.ui.vm.SiteNavigationViewModel

/** Shared helpers for skeleton screens. */
abstract class BaseScreenFragment : Fragment() {

    protected val siteVm: SiteNavigationViewModel by activityViewModels()

    protected fun nav(): NavHost = requireActivity().requireNavHost()
}
