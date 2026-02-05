package dev.pikaia.android.navigation.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import dev.pikaia.android.R
import dev.pikaia.android.navigation.graphs.MainGraph
import dev.pikaia.android.theme.PurpleGrey40
import dev.pikaia.android.theme.spacings

@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    currentScreen: MainGraph,
    modifier: Modifier = Modifier,
    currentScreenClickAction: () -> Unit = {}
) {
    val items = navigationItems()

    val navSurfaceColor = MaterialTheme.colorScheme.background
    val selectedColor = MaterialTheme.colorScheme.secondary
    val unselectedColor = MaterialTheme.colorScheme.primary
    val pillCorner = 100.dp
    val pillShape = remember(pillCorner) { RoundedCornerShape(pillCorner) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = MaterialTheme.spacings.d16)
            .padding(bottom = MaterialTheme.spacings.d16),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(72.dp)
                .shadow(elevation = 8.dp, shape = pillShape)
                .background(color = navSurfaceColor, shape = pillShape)
                .padding(horizontal = MaterialTheme.spacings.d4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            items.forEach { item ->
                key(item.screen) {
                    val selected = item.screen == currentScreen
                    val backgroundColor = if (selected) {
                        PurpleGrey40
                    } else {
                        Color.Transparent
                    }

                    val onClick = remember(item.screen, currentScreen) {
                        {
                            if (item.screen == currentScreen) {
                                currentScreenClickAction()
                            } else {
                                navController.navigate(item.screen) {
                                    popUpTo(currentScreen) { inclusive = true }
                                }
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .background(color = backgroundColor, shape = pillShape)
                            .clip(pillShape)
                            .clickable(onClick = onClick)
                            .padding(vertical = MaterialTheme.spacings.d8)
                    ) {
                        BadgedBox(badge = {
                            if (item.counter > 0) {
                                Badge { Text("${item.counter}") }
                            }
                        }) {
                            Icon(
                                modifier = Modifier.sizeIn(minWidth = 22.dp, minHeight = 22.dp),
                                painter = painterResource(item.iconResource),
                                tint = if (selected) selectedColor else unselectedColor,
                                contentDescription = item.title
                            )
                        }

                        Text(
                            text = item.title,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) selectedColor else unselectedColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun navigationItems() = listOf(
    NavigationItem(
        title = stringResource(R.string.menu_home),
        iconResource = R.drawable.ic_home,
        screen = MainGraph.Home
    ),
    NavigationItem(
        title = stringResource(R.string.menu_profile),
        iconResource = R.drawable.ic_profile,
        screen = MainGraph.Profile
    )
)