package dev.minimalist.ui.search

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.minimalist.container
import dev.minimalist.data.InstalledApp
import dev.minimalist.domain.AppTier
import dev.minimalist.ui.t
import dev.minimalist.ui.AppLauncher
import dev.minimalist.ui.noRippleClickable
import dev.minimalist.ui.theme.Dim
import dev.minimalist.ui.theme.Faint
import dev.minimalist.ui.theme.Fainter
import dev.minimalist.ui.theme.Backdrop
import dev.minimalist.ui.theme.MinimalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The drawer, such as it is. Nothing is listed until you have typed two characters, so there is
 * no grid to graze and no way to end up somewhere by scrolling.
 */
class SearchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MinimalTheme {
                SearchScreen(onLaunched = { finish() })
            }
        }
    }
}

private const val MIN_QUERY_LENGTH = 2

@Composable
private fun SearchScreen(onLaunched: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val apps by produceState(initialValue = emptyList<InstalledApp>()) {
        value = withContext(Dispatchers.IO) { context.container.catalog.installedApps() }
    }
    // Every rule, not just the blocked ones: the drawer says what an app will cost before it is
    // tapped, and "gated" is the marker that matters most — it is the one that means a countdown.
    val rules by context.container.repository.allRules.collectAsState(initial = emptyList())
    val tiers = remember(rules) { rules.associate { it.packageName to it.tier } }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val results = remember(query, apps) {
        if (query.length < MIN_QUERY_LENGTH) {
            emptyList()
        } else {
            val needle = query.lowercase(Locale.getDefault())
            apps.filter { it.label.lowercase(Locale.getDefault()).contains(needle) }
                .sortedBy { !it.label.lowercase(Locale.getDefault()).startsWith(needle) }
                .take(20)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Backdrop)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 26.dp)
            .padding(top = 32.dp, bottom = 16.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.headlineMedium)
                .copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Search,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .focusable(),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = t("type to find"),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Faint,
                    )
                }
                inner()
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (query.length < MIN_QUERY_LENGTH) {
                Text(
                    text = t("at least %s letters", MIN_QUERY_LENGTH),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Faint,
                )
                return@Column
            }

            if (results.isEmpty()) {
                Text(
                    text = t("nothing matches"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Faint,
                )
                return@Column
            }

            results.forEach { app ->
                val tier = tiers[app.packageName]
                val blocked = tier == AppTier.BLOCKED
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable {
                            scope.launch {
                                AppLauncher.open(context, app.packageName)
                                onLaunched()
                            }
                        }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // The same chevron the home screen uses, and the same indent, so a name in
                    // the drawer and a name on the home screen read as the same kind of thing.
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Fainter,
                        modifier = Modifier.padding(start = 32.dp),
                    )
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (blocked) Dim else MaterialTheme.colorScheme.onBackground,
                    )
                    // What it will cost, said before the tap rather than after it.
                    val marker = when {
                        blocked -> t("blocked")
                        tier == AppTier.GATED -> t("gated")
                        else -> null
                    }
                    if (marker != null) {
                        Text(
                            text = marker,
                            style = MaterialTheme.typography.bodySmall,
                            color = Dim,
                        )
                    }
                }
            }
        }
    }
}
