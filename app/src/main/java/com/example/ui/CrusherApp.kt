package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.CrusherRepository
import com.example.data.NetworkMonitor
import com.example.data.TransactionRepository
import com.example.ui.components.SyncStatusChip
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.PurchasesScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SalesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StockMaterialsScreen
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class ErpSection(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val showInBottomNav: Boolean
) {
    DASHBOARD("dashboard", "Dashboard", Icons.Default.Dashboard, true),
    SALES("sales", "Sales", Icons.Default.PointOfSale, true),
    PURCHASES("purchases", "Purchases", Icons.Default.ShoppingBag, true),
    STOCK("stock", "Stock", Icons.Default.Inventory2, true),
    ACCOUNTS("accounts", "Accounts", Icons.Default.People, true),
    EXPENSES("expenses", "Expenses", Icons.Default.Payments, false),
    REPORTS("reports", "Reports", Icons.Default.Assessment, false),
    SETTINGS("settings", "Settings", Icons.Default.Settings, false);

    fun localizedLabel(strings: AppStrings): String = when (this) {
        DASHBOARD -> strings.navDashboard
        SALES -> strings.navSales
        PURCHASES -> strings.navPurchases
        STOCK -> strings.navStock
        ACCOUNTS -> strings.navAccounts
        EXPENSES -> strings.navExpenses
        REPORTS -> strings.navReports
        SETTINGS -> strings.navSettings
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrusherErpApp(
    currentUserId: String,
    onLanguageChange: (AppLanguage) -> Unit = {}
) {
    val strings = LocalAppStrings.current
    val currentLang = LocalAppLanguage.current
    val viewModel: CrusherViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repo = CrusherRepository(db)
                val txRepo = TransactionRepository(repo)
                val net = NetworkMonitor(app)
                CrusherViewModel(repo, txRepo, net, currentUserId)
            }
        }
    )

    val uiState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentSection by remember { mutableStateOf(ErpSection.DASHBOARD) }
    var openNewSale by remember { mutableStateOf(false) }
    var openNewPurchase by remember { mutableStateOf(false) }
    var openNewExpense by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (currentSection != ErpSection.DASHBOARD) {
        BackHandler {
            currentSection = ErpSection.DASHBOARD
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        val mainScaffold: @Composable () -> Unit = {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = currentSection.localizedLabel(strings),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            if (!isWideScreen) {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("drawer_menu_button")
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open Navigation Menu")
                                }
                            }
                        },
                        actions = {
                            val success = uiState as? UiState.Success
                            TextButton(
                                onClick = {
                                    val nextLang = if (currentLang == AppLanguage.EN) AppLanguage.UR else AppLanguage.EN
                                    onLanguageChange(nextLang)
                                    if (success != null) {
                                        viewModel.updateSettings(success.data.settings.copy(languageCode = nextLang.code))
                                    }
                                },
                                modifier = Modifier.testTag("top_language_toggle")
                            ) {
                                Text(strings.switchLanguageLabel, fontWeight = FontWeight.Bold)
                            }
                            if (success != null) {
                                SyncStatusChip(
                                    syncInfo = success.data.syncInfo,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                            IconButton(
                                onClick = { currentSection = ErpSection.EXPENSES },
                                modifier = Modifier.testTag("top_expenses_button")
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = strings.navExpenses)
                            }
                            IconButton(
                                onClick = { currentSection = ErpSection.REPORTS },
                                modifier = Modifier.testTag("top_reports_button")
                            ) {
                                Icon(Icons.Default.Assessment, contentDescription = strings.navReports)
                            }
                            IconButton(
                                onClick = { currentSection = ErpSection.SETTINGS },
                                modifier = Modifier.testTag("top_settings_button")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = strings.navSettings)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    if (!isWideScreen) {
                        NavigationBar {
                            ErpSection.entries.filter { it.showInBottomNav }.forEach { section ->
                                val locLabel = section.localizedLabel(strings)
                                NavigationBarItem(
                                    selected = currentSection == section,
                                    onClick = { currentSection = section },
                                    icon = { Icon(section.icon, contentDescription = locLabel) },
                                    label = { Text(locLabel, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_${section.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isWideScreen) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Spacer(Modifier.height(8.dp))
                            ErpSection.entries.forEach { section ->
                                val locLabel = section.localizedLabel(strings)
                                NavigationRailItem(
                                    selected = currentSection == section,
                                    onClick = { currentSection = section },
                                    icon = { Icon(section.icon, contentDescription = locLabel) },
                                    label = { Text(locLabel) },
                                    modifier = Modifier.testTag("rail_${section.route}")
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 1100.dp)
                        ) {
                            when (val state = uiState) {
                                is UiState.Loading -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator()
                                        Spacer(Modifier.height(12.dp))
                                        Text(strings.loadingWorkspace)
                                    }
                                }
                                is UiState.Error -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = strings.dataSyncNotice,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                is UiState.Success -> {
                                    val data = state.data
                                    when (currentSection) {
                                        ErpSection.DASHBOARD -> DashboardScreen(
                                            state = data,
                                            onNewSale = {
                                                openNewSale = true
                                                currentSection = ErpSection.SALES
                                            },
                                            onNewPurchase = {
                                                openNewPurchase = true
                                                currentSection = ErpSection.PURCHASES
                                            },
                                            onNewExpense = {
                                                openNewExpense = true
                                                currentSection = ErpSection.EXPENSES
                                            },
                                            onOpenStock = { currentSection = ErpSection.STOCK },
                                            onOpenAccounts = { currentSection = ErpSection.ACCOUNTS },
                                            onOpenReports = { currentSection = ErpSection.REPORTS }
                                        )
                                        ErpSection.SALES -> SalesScreen(
                                            state = data,
                                            viewModel = viewModel,
                                            openNewTrigger = openNewSale,
                                            onConsumeNewTrigger = { openNewSale = false }
                                        )
                                        ErpSection.PURCHASES -> PurchasesScreen(
                                            state = data,
                                            viewModel = viewModel,
                                            openNewTrigger = openNewPurchase,
                                            onConsumeNewTrigger = { openNewPurchase = false }
                                        )
                                        ErpSection.STOCK -> StockMaterialsScreen(
                                            state = data,
                                            viewModel = viewModel
                                        )
                                        ErpSection.ACCOUNTS -> AccountsScreen(
                                            state = data,
                                            viewModel = viewModel
                                        )
                                        ErpSection.EXPENSES -> ExpensesScreen(
                                            state = data,
                                            viewModel = viewModel,
                                            openNewTrigger = openNewExpense,
                                            onConsumeNewTrigger = { openNewExpense = false }
                                        )
                                        ErpSection.REPORTS -> ReportsScreen(state = data)
                                        ErpSection.SETTINGS -> SettingsScreen(
                                            state = data,
                                            viewModel = viewModel,
                                            onLanguageChange = onLanguageChange
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isWideScreen) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                        Text(
                            text = strings.appTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(18.dp)
                        )
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        ErpSection.entries.forEach { section ->
                            val locLabel = section.localizedLabel(strings)
                            NavigationDrawerItem(
                                label = { Text(locLabel) },
                                selected = currentSection == section,
                                icon = { Icon(section.icon, contentDescription = locLabel) },
                                onClick = {
                                    currentSection = section
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                content = mainScaffold
            )
        } else {
            mainScaffold()
        }
    }
}
