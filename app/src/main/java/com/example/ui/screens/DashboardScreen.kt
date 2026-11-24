package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.AccountingEngine
import com.example.domain.ProfitAndLossStatement
import com.example.ui.CrusherWorkspaceState
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.SyncStatusChip
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonAlertBg
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.MonoNumberStyle
import com.example.ui.theme.QuarryAmber
import com.example.ui.theme.SkyInfo
import com.example.ui.theme.SlateNavy

@Composable
fun DashboardScreen(
    state: CrusherWorkspaceState,
    onNewSale: () -> Unit,
    onNewPurchase: () -> Unit,
    onNewExpense: () -> Unit,
    onOpenStock: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenReports: () -> Unit
) {
    val m = state.metrics
    val strings = LocalAppStrings.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Header Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_banner),
                        contentDescription = "Crusher Plant Header",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        SlateNavy.copy(alpha = 0.94f),
                                        SlateNavy.copy(alpha = 0.72f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.settings.businessName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "FY ${state.settings.financialYear} • PKR Accounting",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                            }
                            SyncStatusChip(syncInfo = state.syncInfo)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = strings.todaysSales,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFBBF24)
                                )
                                Text(
                                    text = AccountingEngine.formatPkr(m.todaySales),
                                    style = MonoNumberStyle.copy(fontSize = 24.sp),
                                    color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = strings.todaysNetPnl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                                Text(
                                    text = AccountingEngine.formatPkr(m.dailyPnl.netProfit),
                                    style = MonoNumberStyle.copy(fontSize = 16.sp),
                                    color = if (m.dailyPnl.netProfit >= 0) Color(0xFF34D399) else Color(0xFFF87171)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    QuickActionBtn(
                        label = strings.quickNewSale,
                        icon = Icons.Default.PointOfSale,
                        containerColor = SlateNavy,
                        contentColor = Color.White,
                        tag = "quick_new_sale",
                        onClick = onNewSale
                    )
                }
                item {
                    QuickActionBtn(
                        label = strings.quickNewPurchase,
                        icon = Icons.Default.AddShoppingCart,
                        containerColor = QuarryAmber,
                        contentColor = Color.White,
                        tag = "quick_new_purchase",
                        onClick = onNewPurchase
                    )
                }
                item {
                    QuickActionBtn(
                        label = strings.quickNewExpense,
                        icon = Icons.Default.Payments,
                        containerColor = CrimsonAlert,
                        contentColor = Color.White,
                        tag = "quick_new_expense",
                        onClick = onNewExpense
                    )
                }
                item {
                    QuickActionBtn(
                        label = strings.quickReports,
                        icon = Icons.Default.Assessment,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        tag = "quick_reports",
                        onClick = onOpenReports
                    )
                }
            }
        }

        // Low Stock Alert Banner
        if (m.lowStockCount > 0) {
            item {
                Card(
                    onClick = onOpenStock,
                    colors = CardDefaults.cardColors(containerColor = CrimsonAlertBg),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Low stock warning",
                            tint = CrimsonAlert
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${m.lowStockCount} ${strings.lowStockAlertTitleSuffix}",
                                style = MaterialTheme.typography.titleSmall,
                                color = CrimsonAlert,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.lowStockAlertSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF7F1D1D)
                            )
                        }
                    }
                }
            }
        }

        // Sales & Purchases Row
        item {
            Text(
                text = strings.salesAndProcurement,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMetricCard(
                    title = strings.monthlySales,
                    value = AccountingEngine.formatPkr(m.monthlySales),
                    subtitle = "${strings.yearlyPrefix}: ${AccountingEngine.formatPkr(m.yearlySales)}",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    accentColor = EmeraldProfit,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenReports
                )
                KpiMetricCard(
                    title = strings.totalPurchases,
                    value = AccountingEngine.formatPkr(m.totalPurchases),
                    subtitle = "${state.purchases.count { it.status != "VOID" }} ${strings.vouchersCountSuffix}",
                    icon = Icons.Default.ShoppingBag,
                    accentColor = SkyInfo,
                    modifier = Modifier.weight(1f),
                    onClick = onNewPurchase
                )
            }
        }

        // Stock & Expenses Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMetricCard(
                    title = strings.stockValuation,
                    value = AccountingEngine.formatPkr(m.stockValue),
                    subtitle = "${strings.qtyPrefix}: ${AccountingEngine.formatQty(m.totalStockQuantity)} ${strings.unitsSuffix}",
                    icon = Icons.Default.Inventory2,
                    accentColor = QuarryAmber,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenStock
                )
                KpiMetricCard(
                    title = strings.totalExpenses,
                    value = AccountingEngine.formatPkr(m.totalExpenses),
                    subtitle = "${state.expenses.count { it.status != "VOID" }} ${strings.nonStockEntriesSuffix}",
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    accentColor = CrimsonAlert,
                    modifier = Modifier.weight(1f),
                    onClick = onNewExpense
                )
            }
        }

        // Receivables, Payables & Cash Flow
        item {
            Text(
                text = strings.accountsAndCashFlow,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMetricCard(
                    title = strings.customerReceivable,
                    value = AccountingEngine.formatPkr(m.customerOutstanding),
                    subtitle = "${state.customers.size} ${strings.accountsSuffix}",
                    icon = Icons.Default.ArrowDownward,
                    accentColor = EmeraldProfit,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenAccounts
                )
                KpiMetricCard(
                    title = strings.supplierPayable,
                    value = AccountingEngine.formatPkr(m.supplierOutstanding),
                    subtitle = "${state.suppliers.size} ${strings.accountsSuffix}",
                    icon = Icons.Default.ArrowUpward,
                    accentColor = CrimsonAlert,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenAccounts
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMetricCard(
                    title = strings.cashReceived,
                    value = AccountingEngine.formatPkr(m.cashReceived),
                    subtitle = strings.cashReceivedSub,
                    icon = Icons.Default.AccountBalanceWallet,
                    accentColor = EmeraldProfit,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = strings.cashPaid,
                    value = AccountingEngine.formatPkr(m.cashPaid),
                    subtitle = strings.cashPaidSub,
                    icon = Icons.Default.Payments,
                    accentColor = QuarryAmber,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Profit & Loss Summary (Daily / Monthly / Yearly)
        item {
            Text(
                text = strings.pnlSectionTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            PnlSummaryCard(strings.todaysPnl, m.dailyPnl, strings)
        }
        item {
            PnlSummaryCard(strings.monthlyPnl, m.monthlyPnl, strings)
        }
        item {
            PnlSummaryCard(strings.yearlyPnl, m.yearlyPnl, strings)
        }
    }
}

@Composable
private fun QuickActionBtn(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    tag: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier
            .height(48.dp)
            .testTag(tag)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PnlSummaryCard(title: String, pnl: ProfitAndLossStatement, strings: AppStrings) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Surface(
                    color = if (pnl.netProfit >= 0) EmeraldProfit.copy(alpha = 0.14f) else CrimsonAlert.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (pnl.netProfit >= 0) strings.profitBadge else strings.lossBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pnl.netProfit >= 0) EmeraldProfit else CrimsonAlert,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            PnlRow(strings.totalSalesInvoiced, AccountingEngine.formatPkr(pnl.totalSales))
            PnlRow(strings.cashVsCredit, "${AccountingEngine.formatPkr(pnl.cashCollectedFromSales)} / ${AccountingEngine.formatPkr(pnl.creditOutstandingFromSales)}")
            PnlRow(strings.costOfGoodsSold, AccountingEngine.formatPkr(pnl.totalCogs))
            PnlRow(strings.grossProfit, AccountingEngine.formatPkr(pnl.grossProfit))
            PnlRow(strings.operatingExpenses, AccountingEngine.formatPkr(pnl.totalExpenses))
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(strings.netProfitLoss, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = AccountingEngine.formatPkr(pnl.netProfit),
                    style = MonoNumberStyle.copy(fontSize = 16.sp),
                    color = if (pnl.netProfit >= 0) EmeraldProfit else CrimsonAlert
                )
            }
        }
    }
}

@Composable
private fun PnlRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MonoNumberStyle.copy(fontSize = 13.sp))
    }
}
