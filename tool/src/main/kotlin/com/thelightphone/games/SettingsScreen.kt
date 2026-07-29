package com.thelightphone.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeColors
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsScreenViewModel(
    private val settingsStore: SettingsStore,
    private val gameVisibilityStore: GameVisibilityStore,
) : LightViewModel<Unit>() {

    private val _gameVisibility = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val gameVisibility: StateFlow<Map<String, Boolean>> = _gameVisibility

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch {
            _gameVisibility.value = ALL_GAME_KEYS.associateWith { gameVisibilityStore.isVisible(it) }
        }
    }

    fun toggleInvertColors() {
        LightThemeController.toggle()
        viewModelScope.launch {
            settingsStore.setColorInverted(!LightThemeController.isDarkTheme)
        }
    }

    fun toggleGameVisibility(gameKey: String) {
        viewModelScope.launch {
            val newValue = !(_gameVisibility.value[gameKey] ?: true)
            gameVisibilityStore.setVisible(gameKey, newValue)
            _gameVisibility.value = _gameVisibility.value + (gameKey to newValue)
        }
    }
}

class SettingsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SettingsScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<SettingsScreenViewModel>
        get() = SettingsScreenViewModel::class.java

    override fun createViewModel(): SettingsScreenViewModel =
        SettingsScreenViewModel(
            settingsStore = SettingsStore(lightContext.dataStore),
            gameVisibilityStore = GameVisibilityStore(lightContext.dataStore),
        )

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val isInverted = themeColors != LightThemeColors.Dark
        val gameVisibility by viewModel.gameVisibility.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Settings"),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // Small margin so the scroll area (and its scrollbar) doesn't run
                        // flush to the very bottom edge of the screen.
                        .padding(bottom = 2f.gridUnitsAsDp()),
                ) {
                    SettingsRow(
                        title = "Invert Colors",
                        isOn = isInverted,
                        onClick = { viewModel.toggleInvertColors() },
                    )

                    LightText(
                        text = "Show on home screen",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(
                            horizontal = 2f.gridUnitsAsDp(),
                            vertical = 0.75f.gridUnitsAsDp(),
                        ),
                    )

                    ALL_GAME_KEYS.forEach { gameKey ->
                        SettingsRow(
                            title = gameDisplayName(gameKey),
                            isOn = gameVisibility[gameKey] ?: true,
                            onClick = { viewModel.toggleGameVisibility(gameKey) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    isOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1.25f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Note: LightIcons.TOGGLE_ON renders its knob on the LEFT, TOGGLE_OFF on the RIGHT -
        // the reverse of what the names suggest. So "off" (isOn = false, left) uses TOGGLE_ON,
        // and "on" (isOn = true, right) uses TOGGLE_OFF.
        LightIcon(
            icon = if (isOn) LightIcons.TOGGLE_OFF else LightIcons.TOGGLE_ON,
            modifier = Modifier.padding(end = 1.5f.gridUnitsAsDp()),
        )
        LightText(
            text = title,
            variant = LightTextVariant.Copy,
            modifier = Modifier.weight(1f),
        )
    }
}
