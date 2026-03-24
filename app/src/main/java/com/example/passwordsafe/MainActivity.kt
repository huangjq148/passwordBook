package com.example.passwordsafe

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.navigation.NavController
import androidx.navigation.findNavController
import com.example.passwordsafe.databinding.ActivityMainBinding
import com.example.passwordsafe.data.repository.SettingRepository
import com.example.passwordsafe.util.AutoLockManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.animation.AnimationUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var autoLockManager: AutoLockManager

    @Inject
    lateinit var settingRepository: SettingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeMode()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        setupBottomNav(navController)
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavCard) { view, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                bottomMargin = navigationBars.bottom + resources.getDimensionPixelSize(R.dimen.bottom_nav_margin_bottom)
                leftMargin = resources.getDimensionPixelSize(R.dimen.bottom_nav_margin_horizontal)
                rightMargin = resources.getDimensionPixelSize(R.dimen.bottom_nav_margin_horizontal)
            }
            insets
        }
        ViewCompat.requestApplyInsets(binding.bottomNavCard)
        autoLockManager.init()
        autoLockManager.setOnLockCallback {
            val currentDestinationId = navController.currentDestination?.id
            if (currentDestinationId != R.id.navigation_auth &&
                currentDestinationId != R.id.navigation_setup_password
            ) {
                navController.navigate(
                    R.id.navigation_auth,
                    null,
                    androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.mobile_navigation, false)
                .build()
                )
            }
        }
        
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavCard.isVisible = when (destination.id) {
                R.id.navigation_auth,
                R.id.navigation_setup_password,
                R.id.navigation_add_account,
                R.id.navigation_account_detail -> false
                else -> true
            }
            updateBottomNavSelection(destination.id)
        }
    }

    private fun setupBottomNav(navController: NavController) {
        binding.btnNavAccounts.setOnClickListener {
            if (navController.currentDestination?.id != R.id.navigation_accounts) {
                navController.navigate(R.id.navigation_accounts)
            }
        }
        binding.btnNavSettings.setOnClickListener {
            if (navController.currentDestination?.id != R.id.navigation_settings) {
                navController.navigate(R.id.navigation_settings)
            }
        }
        updateBottomNavSelection(navController.currentDestination?.id ?: R.id.navigation_accounts)
    }

    private fun updateBottomNavSelection(destinationId: Int) {
        setBottomNavTab(binding.btnNavAccounts, destinationId == R.id.navigation_accounts)
        setBottomNavTab(binding.btnNavSettings, destinationId == R.id.navigation_settings)
    }

    private fun setBottomNavTab(button: MaterialButton, selected: Boolean) {
        button.isCheckable = true
        button.isChecked = selected
        animateNavTab(button, selected)
    }

    private fun animateNavTab(button: MaterialButton, selected: Boolean) {
        val elevation = resources.displayMetrics.density * if (selected) 6f else 0f
        val scale = if (selected) 1f else 0.96f
        val alpha = if (selected) 1f else 0.9f
        button.animate()
            .scaleX(scale)
            .scaleY(scale)
            .alpha(alpha)
            .setDuration(180L)
            .setInterpolator(AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR)
            .start()
        button.elevation = elevation
        button.stateListAnimator = null
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        autoLockManager.onUserInteraction()
    }

    override fun onStart() {
        super.onStart()
        autoLockManager.onAppForegrounded()
    }

    override fun onStop() {
        autoLockManager.onAppBackgrounded()
        super.onStop()
    }

    override fun onDestroy() {
        autoLockManager.cleanup()
        super.onDestroy()
    }

    private fun applyThemeMode() {
        val nightMode = when (runBlocking { settingRepository.getThemeMode() }) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
