package com.k2fsa.sherpa.onnx.speaker.identification

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.k2fsa.sherpa.onnx.SpeakerRecognition
import com.k2fsa.sherpa.onnx.speaker.identification.SherpaOnnx.callStateManager
import com.k2fsa.sherpa.onnx.speaker.identification.fraud.ChatViewModel
import com.k2fsa.sherpa.onnx.speaker.identification.fraud.FraudScreen
import com.k2fsa.sherpa.onnx.speaker.identification.screens.HomeScreen
import com.k2fsa.sherpa.onnx.speaker.identification.screens.RegisterScreen
import com.k2fsa.sherpa.onnx.speaker.identification.screens.TranscriptScreen
import com.k2fsa.sherpa.onnx.speaker.identification.screens.ViewScreen
import com.k2fsa.sherpa.onnx.speaker.identification.screens.AntiSpoofScreen
import com.k2fsa.sherpa.onnx.speaker.identification.ui.theme.SherpaOnnxSpeakerIdentificationTheme

const val TAG = "sherpa-onnx-speaker"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

class MainActivity : ComponentActivity() {
    private val permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)
    val transcriptManager = TranscriptManager
    private lateinit var callStateManager: CallStateManager

    private var lastAudioSize = 0

    fun logAudioSize(size: Int) {
        if (size == 0) {
            Log.w(TAG, "Warning: Received empty audio input")
        }
        lastAudioSize = size
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ////
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 使用沉浸式状态栏，并统一为“浅色图标”（白色）以适配深色顶栏
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false  // false = 浅色图标（白色）
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CAPTURE_AUDIO_OUTPUT,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
        callStateManager = CallStateManager(this)
        SherpaOnnx.initModels(this)
        SpeakerRecognition.initDatabase(this)
        SpeakerRecognition.loadSpeakersFromDatabase()
        setContent {
            SherpaOnnxSpeakerIdentificationTheme {
                // 让内容绘制到状态栏和导航栏之下，由各自页面控制内边距
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }

        SpeakerRecognition.initExtractor(this.assets)
    }





    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        if (!permissionToRecordAccepted) {
            Log.e(TAG, "Audio record is disallowed")
            Toast.makeText(this, getString(R.string.permission_needed), Toast.LENGTH_SHORT)
                .show()
            finish()
        }
        Log.i(TAG, "Audio record is permitted")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    Scaffold(
        topBar = {
            // 渐变顶栏（公安蓝系）
            Box(
                modifier = Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                        )
                    )
                )
            ) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Text(stringResource(id = R.string.app_title), fontWeight = FontWeight.Bold, color = Color.White)
                    },
                    actions = {
                        IconButton(onClick = {
                            navController.navigate(NavRoutes.AntiSpoof.route) {
                                popUpTo(NavRoutes.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Security,
                                contentDescription = stringResource(id = R.string.nav_antispoof),
                                tint = Color.White
                            )
                        }
                    }
                )
            }
        },

        content = { padding ->
            Column(Modifier.padding(padding)) {
                NavigationHost(navController = navController)
            }
        },
        bottomBar = {
            PoliceBottomBar(navController = navController)
        }
    )
}

@Composable
fun NavigationHost(navController: NavHostController) {
    val chatViewModel: ChatViewModel = viewModel()

    NavHost(navController = navController, startDestination = NavRoutes.Home.route) {
        composable(NavRoutes.Home.route) {
            HomeScreen(callStateManager = callStateManager)
        }
        composable(NavRoutes.Register.route) {
            RegisterScreen()
        }
        composable(NavRoutes.View.route) {
            ViewScreen()
        }
        composable(NavRoutes.Transcript.route) {
            TranscriptScreen(navController = navController, chatViewModel = chatViewModel)
        }
        composable(NavRoutes.Fraud.route) {
            FraudScreen(chatViewModel = chatViewModel)
        }
        composable(NavRoutes.AntiSpoof.route) {
            AntiSpoofScreen()
        }
    }
}

@Composable
fun PoliceBottomBar(navController: NavHostController) {
    // 底栏 5 个项目（中置 FAB 专属：鉴伪 不放在底栏项中）
    val navItems = listOf(
        BarItem(title = stringResource(id = R.string.nav_home), image = NavBarItems.BarItems[0].image, route = NavRoutes.Home.route),
        BarItem(title = stringResource(id = R.string.nav_register), image = NavBarItems.BarItems[1].image, route = NavRoutes.Register.route),
        BarItem(title = stringResource(id = R.string.nav_view), image = NavBarItems.BarItems[2].image, route = NavRoutes.View.route),
        BarItem(title = stringResource(id = R.string.nav_transcript), image = NavBarItems.BarItems[4].image, route = NavRoutes.Transcript.route),
        BarItem(title = stringResource(id = R.string.nav_fraud), image = NavBarItems.BarItems[3].image, route = NavRoutes.Fraud.route),
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 胶囊底栏（去除外层矩形阴影）
    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(28.dp)
            )
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 均匀分布五个项（不再预留中间空位）
                navItems.forEach { item ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        NavItemCapsule(item, currentRoute, navController)
                    }
                }
            }
    }
}

@Composable
fun NavItemCapsule(
    item: BarItem,
    currentRoute: String?,
    navController: NavHostController,
) {
    val selected = currentRoute == item.route
    Column(
        modifier = Modifier
            .clickable {
                navController.navigate(item.route) {
                    // 直接回到 Home 的 route，确保从任意页面（含鉴伪页）都能切换
                    popUpTo(NavRoutes.Home.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
        Icon(
            imageVector = item.image,
            contentDescription = item.title,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = item.title,
            color = tint,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    SherpaOnnxSpeakerIdentificationTheme {
        MainScreen()
    }
}