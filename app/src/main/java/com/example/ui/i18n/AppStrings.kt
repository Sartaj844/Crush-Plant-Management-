package com.example.ui.i18n

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.LayoutDirection
import com.example.domain.ExpenseCategories
import com.example.domain.MaterialCategories
import com.example.domain.PaymentMethods
import com.example.domain.ReportDateFilter

enum class AppLanguage(val code: String, val displayLabel: String) {
    EN("en", "English"),
    UR("ur", "اردو");

    val layoutDirection: LayoutDirection
        get() = if (this == UR) LayoutDirection.Rtl else LayoutDirection.Ltr

    companion object {
        fun fromCode(code: String?): AppLanguage =
            if (code.equals("ur", ignoreCase = true)) UR else EN
    }
}

object LanguageStore {
    private const val PREFS_NAME = "crusher_erp_prefs"
    private const val KEY_LANG = "app_language"

    fun getLanguage(context: Context): AppLanguage {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppLanguage.fromCode(prefs.getString(KEY_LANG, AppLanguage.EN.code))
    }

    fun saveLanguage(context: Context, language: AppLanguage) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, language.code).apply()
    }
}

data class AppStrings(
    val language: AppLanguage,
    val appTitle: String,
    val crusherPlantManagement: String,
    val loadingWorkspace: String,
    val dataSyncNotice: String,
    val switchLanguageLabel: String,
    val languageName: String,
    // Auth
    val authBadge: String,
    val authSubtitle: String,
    val authFeat1Title: String,
    val authFeat1Sub: String,
    val authFeat2Title: String,
    val authFeat2Sub: String,
    val authFeat3Title: String,
    val authFeat3Sub: String,
    val authFeat4Title: String,
    val authFeat4Sub: String,
    val signInWithGoogle: String,
    val checkingSession: String,
    val signingIn: String,
    val signInCancelled: String,
    // Sync Status
    val offlineQueued: String,
    val offlineCache: String,
    val syncingNow: String,
    val cloudSynced: String,
    // Navigation
    val navDashboard: String,
    val navSales: String,
    val navPurchases: String,
    val navStock: String,
    val navAccounts: String,
    val navExpenses: String,
    val navReports: String,
    val navSettings: String,
    // Dashboard
    val todaysSales: String,
    val todaysNetPnl: String,
    val quickNewSale: String,
    val quickNewPurchase: String,
    val quickNewExpense: String,
    val quickReports: String,
    val lowStockAlertTitleSuffix: String,
    val lowStockAlertSubtitle: String,
    val salesAndProcurement: String,
    val monthlySales: String,
    val yearlyPrefix: String,
    val totalPurchases: String,
    val vouchersCountSuffix: String,
    val stockValuation: String,
    val qtyPrefix: String,
    val unitsSuffix: String,
    val totalExpenses: String,
    val nonStockEntriesSuffix: String,
    val accountsAndCashFlow: String,
    val customerReceivable: String,
    val supplierPayable: String,
    val accountsSuffix: String,
    val cashReceived: String,
    val cashReceivedSub: String,
    val cashPaid: String,
    val cashPaidSub: String,
    val pnlSectionTitle: String,
    val todaysPnl: String,
    val monthlyPnl: String,
    val yearlyPnl: String,
    val profitBadge: String,
    val lossBadge: String,
    val totalSalesInvoiced: String,
    val cashVsCredit: String,
    val costOfGoodsSold: String,
    val grossProfit: String,
    val operatingExpenses: String,
    val netProfitLoss: String,
    // Common
    val save: String,
    val cancel: String,
    val close: String,
    val print: String,
    val pdf: String,
    val savePdf: String,
    val excelCsv: String,
    val share: String,
    val all: String,
    val receipt: String,
    val printReceipt: String,
    val saveReceiptPdf: String,
    val paymentReceiptTitle: String,
    val recoveryReceiptTitle: String,
    val voucher: String,
    // Sales & Purchases
    val searchSalesPlaceholder: String,
    val noSalesTitle: String,
    val noSalesMessage: String,
    val createSaleInvoice: String,
    val newSaleInvoice: String,
    val grandTotal: String,
    val paidAndBalance: String,
    val searchPurchasesPlaceholder: String,
    val noPurchasesTitle: String,
    val noPurchasesMessage: String,
    val createPurchaseVoucher: String,
    val newPurchaseVoucher: String,
    val totalPurchase: String,
    val paidAndPayable: String,
    // Stock & Materials
    val materialsAndStockTab: String,
    val movementsTab: String,
    val searchMaterialsPlaceholder: String,
    val lowStockFilterPrefix: String,
    val adjustStock: String,
    val addMaterial: String,
    // Accounts & Receipts
    val customersTab: String,
    val suppliersTab: String,
    val totalReceivableLabel: String,
    val totalPayableLabel: String,
    val searchCustomersPlaceholder: String,
    val searchSuppliersPlaceholder: String,
    val addCustomer: String,
    val addSupplier: String,
    val outstandingBalanceUpper: String,
    val recordRecovery: String,
    val recordPayment: String,
    val backToAccounts: String,
    // Expenses
    val expenseOnlyBanner: String,
    val filteredExpensesTotal: String,
    val addExpense: String,
    // Reports
    val reportDailySales: String,
    val reportWeeklySales: String,
    val reportMonthlySales: String,
    val reportYearlySales: String,
    val reportPurchases: String,
    val reportStock: String,
    val reportCustomerLedger: String,
    val reportSupplierLedger: String,
    val reportExpenses: String,
    val reportCashFlow: String,
    val reportProfitLoss: String,
    // Settings & Admin Controls
    val signOutLabel: String,
    val businessAndInvoiceSettings: String,
    val saveBusinessSettings: String,
    val languageSettingsTitle: String,
    val languageSettingsSub: String,
    val adminControlsTitle: String,
    val adminControlsSub: String,
    val operatorNameLabel: String,
    val requireAdminPinLabel: String,
    val requireAdminPinSub: String,
    val adminPinLabel: String,
    val adminPinHint: String,
    val verifyBalancesButton: String,
    val enterAdminPinTitle: String,
    val enterAdminPinMessage: String,
    val unlockWithPin: String,
    val incorrectAdminPin: String,
    val backupRestoreAuditTitle: String,
    val backupJson: String,
    val restoreJson: String,
    val viewAuditHistoryPrefix: String
) {
    fun dateFilterLabel(filter: ReportDateFilter): String = when (language) {
        AppLanguage.EN -> filter.label
        AppLanguage.UR -> when (filter) {
            ReportDateFilter.TODAY -> "آج"
            ReportDateFilter.WEEK -> "اس ہفتے"
            ReportDateFilter.MONTH -> "اس ماہ"
            ReportDateFilter.YEAR -> "اس سال"
            ReportDateFilter.ALL -> "مکمل ریکارڈ"
            ReportDateFilter.CUSTOM -> "مخصوص تاریخ"
        }
    }

    fun materialCategoryLabel(category: String): String = when (language) {
        AppLanguage.EN -> category
        AppLanguage.UR -> when (category) {
            MaterialCategories.CEMENT -> "سیمنٹ"
            MaterialCategories.SAND_AGGREGATE -> "ریت اور بجری"
            MaterialCategories.BLOCKS -> "بلاک"
            MaterialCategories.STEEL_OTHER -> "سریا اور دیگر"
            else -> category
        }
    }

    fun expenseCategoryLabel(category: String): String = when (language) {
        AppLanguage.EN -> category
        AppLanguage.UR -> when (category) {
            ExpenseCategories.DIESEL -> "ڈیزل"
            ExpenseCategories.LABOUR -> "مزدوری"
            ExpenseCategories.SALARY -> "تنخواہ"
            ExpenseCategories.ELECTRICITY -> "بجلی"
            ExpenseCategories.TRANSPORT -> "ٹرانسپورٹ"
            ExpenseCategories.MACHINE_MAINTENANCE -> "مشین مینٹیننس"
            ExpenseCategories.MACHINE_REPAIRING -> "مشین مرمت"
            ExpenseCategories.OTHER_EXPENSES -> "دیگر اخراجات"
            else -> category
        }
    }

    fun paymentMethodLabel(method: String): String = when (language) {
        AppLanguage.EN -> method
        AppLanguage.UR -> when (method) {
            PaymentMethods.CASH -> "نقد (کیش)"
            PaymentMethods.BANK_TRANSFER -> "بینک ٹرانسفر"
            PaymentMethods.CHEQUE -> "چیک"
            PaymentMethods.CREDIT -> "ادھار"
            "Adjustment" -> "ایڈجسٹمنٹ"
            else -> method
        }
    }
}

val EnglishStrings = AppStrings(
    language = AppLanguage.EN,
    appTitle = "Crusher ERP",
    crusherPlantManagement = "Crusher Plant Management",
    loadingWorkspace = "Loading Crusher Plant Ledger...",
    dataSyncNotice = "Data Sync Notice",
    switchLanguageLabel = "اردو",
    languageName = "English",
    authBadge = "PAKISTAN CRUSHER ERP",
    authSubtitle = "Complete Offline-Capable Plant Accounting",
    authFeat1Title = "PKR Invoices, Receipts & A4 Print",
    authFeat1Sub = "Sales, purchases, payment receipts, COGS & A4 PDFs",
    authFeat2Title = "Material Stock & Khata Ledgers",
    authFeat2Sub = "Cement, Rait, Bajri, Blocks, Saria & party balances",
    authFeat3Title = "Offline Queue & Cloud Sync",
    authFeat3Sub = "Works without internet and syncs automatically to Firestore",
    authFeat4Title = "Admin Controls & Audit Trail",
    authFeat4Sub = "PIN-protected voids/restores, Urdu/English & JSON backup",
    signInWithGoogle = "Sign in with Google",
    checkingSession = "Checking Session...",
    signingIn = "Signing In...",
    signInCancelled = "Sign-in was cancelled. Tap Sign in with Google to continue.",
    offlineQueued = "Offline • Queued",
    offlineCache = "Offline Cache",
    syncingNow = "Syncing...",
    cloudSynced = "Cloud Synced",
    navDashboard = "Dashboard",
    navSales = "Sales",
    navPurchases = "Purchases",
    navStock = "Stock",
    navAccounts = "Accounts",
    navExpenses = "Expenses",
    navReports = "Reports",
    navSettings = "Settings",
    todaysSales = "TODAY'S SALES",
    todaysNetPnl = "TODAY'S NET P&L",
    quickNewSale = "New Sale",
    quickNewPurchase = "New Purchase",
    quickNewExpense = "Add Expense",
    quickReports = "Reports",
    lowStockAlertTitleSuffix = "Materials Below Minimum Stock Alert",
    lowStockAlertSubtitle = "Tap to review stock levels and replenish inventory",
    salesAndProcurement = "Sales & Procurement",
    monthlySales = "Monthly Sales",
    yearlyPrefix = "Yearly",
    totalPurchases = "Total Purchases",
    vouchersCountSuffix = "vouchers",
    stockValuation = "Stock Valuation",
    qtyPrefix = "Qty",
    unitsSuffix = "units",
    totalExpenses = "Total Expenses",
    nonStockEntriesSuffix = "entries (non-stock)",
    accountsAndCashFlow = "Accounts & Cash Flow",
    customerReceivable = "Customer Receivable",
    supplierPayable = "Supplier Payable",
    accountsSuffix = "accounts",
    cashReceived = "Cash Received",
    cashReceivedSub = "Sales paid + recoveries",
    cashPaid = "Cash Paid",
    cashPaidSub = "Purchases + exp + suppliers",
    pnlSectionTitle = "Profit & Loss Statement (COGS Weighted Avg)",
    todaysPnl = "Today's P&L",
    monthlyPnl = "Monthly P&L",
    yearlyPnl = "Yearly P&L",
    profitBadge = "PROFIT",
    lossBadge = "LOSS",
    totalSalesInvoiced = "Total Sales (Invoiced)",
    cashVsCredit = "Cash Collected vs Credit",
    costOfGoodsSold = "Cost of Goods Sold (COGS)",
    grossProfit = "Gross Profit",
    operatingExpenses = "Operating Expenses",
    netProfitLoss = "Net Profit / Loss",
    save = "Save",
    cancel = "Cancel",
    close = "Close",
    print = "Print",
    pdf = "PDF",
    savePdf = "Save PDF",
    excelCsv = "Excel CSV",
    share = "Share",
    all = "All",
    receipt = "Receipt",
    printReceipt = "Print Receipt",
    saveReceiptPdf = "Save Receipt PDF",
    paymentReceiptTitle = "Payment Receipt",
    recoveryReceiptTitle = "Recovery Receipt",
    voucher = "Voucher",
    searchSalesPlaceholder = "Search invoice #, customer, or material...",
    noSalesTitle = "No Sales Invoices",
    noSalesMessage = "Create your first sales invoice to track PKR revenue, customer balances, and automatic stock deduction.",
    createSaleInvoice = "Create Sale Invoice",
    newSaleInvoice = "New Sales Invoice",
    grandTotal = "Grand Total",
    paidAndBalance = "Paid / Balance",
    searchPurchasesPlaceholder = "Search voucher #, supplier, or material...",
    noPurchasesTitle = "No Purchase Vouchers",
    noPurchasesMessage = "Record material purchases from crushers, cement dealers, or steel suppliers to increase stock and update average cost.",
    createPurchaseVoucher = "Create Purchase Voucher",
    newPurchaseVoucher = "New Purchase Voucher",
    totalPurchase = "Total Purchase",
    paidAndPayable = "Paid / Payable",
    materialsAndStockTab = "Materials & Stock",
    movementsTab = "Movements",
    searchMaterialsPlaceholder = "Search cement, rait, bajri, blocks, saria...",
    lowStockFilterPrefix = "Low Stock",
    adjustStock = "Adjust Stock",
    addMaterial = "Add Material",
    customersTab = "Customers",
    suppliersTab = "Suppliers",
    totalReceivableLabel = "Total Receivable from Customers",
    totalPayableLabel = "Total Payable to Suppliers",
    searchCustomersPlaceholder = "Search customers by name or phone...",
    searchSuppliersPlaceholder = "Search suppliers by name or phone...",
    addCustomer = "Add Customer",
    addSupplier = "Add Supplier",
    outstandingBalanceUpper = "OUTSTANDING BALANCE",
    recordRecovery = "Record Recovery",
    recordPayment = "Record Payment",
    backToAccounts = "Back to Accounts",
    expenseOnlyBanner = "Expense-Only Ledger: Diesel, Labour, Salary, Electricity, Transport & Maintenance never alter material stock.",
    filteredExpensesTotal = "Filtered Operating Expenses",
    addExpense = "Add Expense",
    reportDailySales = "Daily Sales",
    reportWeeklySales = "Weekly Sales",
    reportMonthlySales = "Monthly Sales",
    reportYearlySales = "Yearly Sales",
    reportPurchases = "Purchases",
    reportStock = "Material Stock",
    reportCustomerLedger = "Customer Ledger",
    reportSupplierLedger = "Supplier Ledger",
    reportExpenses = "Expenses",
    reportCashFlow = "Cash Flow",
    reportProfitLoss = "Profit & Loss",
    signOutLabel = "Sign Out",
    businessAndInvoiceSettings = "Business & Invoice Settings",
    saveBusinessSettings = "Save Business Settings",
    languageSettingsTitle = "Language / زبان",
    languageSettingsSub = "Switch instantly between English and Urdu (RTL)",
    adminControlsTitle = "Admin & Security Controls",
    adminControlsSub = "Protect voids, stock adjustments, and backup restores with an Owner PIN",
    operatorNameLabel = "Operator / Manager Name",
    requireAdminPinLabel = "Require Admin PIN for Sensitive Actions",
    requireAdminPinSub = "Locks voiding transactions, stock adjustments & restore",
    adminPinLabel = "Admin PIN (4-8 digits)",
    adminPinHint = "Leave empty or enter 4-8 digit PIN",
    verifyBalancesButton = "Verify & Reconcile Party Balances",
    enterAdminPinTitle = "Admin Authorization Required",
    enterAdminPinMessage = "Enter the Owner Admin PIN to authorize this sensitive operation.",
    unlockWithPin = "Authorize",
    incorrectAdminPin = "Incorrect Admin PIN. Please try again.",
    backupRestoreAuditTitle = "Backup, Restore & Audit Log",
    backupJson = "Backup JSON",
    restoreJson = "Restore JSON",
    viewAuditHistoryPrefix = "View Financial Audit History"
)

val UrduStrings = AppStrings(
    language = AppLanguage.UR,
    appTitle = "کریشر پلانٹ ای آر پی",
    crusherPlantManagement = "کریشر پلانٹ مینجمنٹ سسٹم",
    loadingWorkspace = "پلانٹ کھاتہ لوڈ ہو رہا ہے...",
    dataSyncNotice = "ڈیٹا سنک اطلاع",
    switchLanguageLabel = "EN",
    languageName = "اردو",
    authBadge = "پاکستان کریشر ای آر پی",
    authSubtitle = "مکمل آف لائن اور آن لائن پلانٹ اکاؤنٹنگ",
    authFeat1Title = "روپے انوائس، رسید اور A4 پرنٹ",
    authFeat1Sub = "فروخت، خریداری، ادائیگی رسید اور A4 پی ڈی ایف",
    authFeat2Title = "مواد کا اسٹاک اور کھاتہ لیجر",
    authFeat2Sub = "سیمنٹ، ریت، بجری، بلاک، سریا اور پارٹی بیلنس",
    authFeat3Title = "آف لائن محفوظ اور کلاؤڈ سنک",
    authFeat3Sub = "انٹرنیٹ کے بغیر کام اور خودکار کلاؤڈ مطابقت پذیری",
    authFeat4Title = "ایڈمن کنٹرولز اور آڈٹ ریکارڈ",
    authFeat4Sub = "پن لاک، اردو/انگریزی اور مکمل JSON بیک اپ",
    signInWithGoogle = "گوگل سے سائن ان کریں",
    checkingSession = "سیشن چیک ہو رہا ہے...",
    signingIn = "سائن ان جاری ہے...",
    signInCancelled = "سائن ان منسوخ کر دیا گیا۔ جاری رکھنے کے لیے دوبارہ کوشش کریں۔",
    offlineQueued = "آف لائن • محفوظ",
    offlineCache = "آف لائن کیش",
    syncingNow = "سنک ہو رہا ہے...",
    cloudSynced = "کلاؤڈ سے منسلک",
    navDashboard = "ڈیش بورڈ",
    navSales = "فروخت",
    navPurchases = "خریداری",
    navStock = "اسٹاک",
    navAccounts = "کھاتہ",
    navExpenses = "اخراجات",
    navReports = "رپورٹس",
    navSettings = "ترتیبات",
    todaysSales = "آج کی فروخت",
    todaysNetPnl = "آج کا خالص منافع/نقصان",
    quickNewSale = "نئی فروخت",
    quickNewPurchase = "نئی خریداری",
    quickNewExpense = "نیا خرچ",
    quickReports = "رپورٹس",
    lowStockAlertTitleSuffix = "مواد کم از کم اسٹاک سے نیچے ہیں",
    lowStockAlertSubtitle = "اسٹاک چیک کرنے اور خریداری کے لیے ٹیپ کریں",
    salesAndProcurement = "فروخت اور خریداری",
    monthlySales = "ماہانہ فروخت",
    yearlyPrefix = "سالانہ",
    totalPurchases = "کل خریداری",
    vouchersCountSuffix = "واؤچرز",
    stockValuation = "اسٹاک کی مالیت",
    qtyPrefix = "مقدار",
    unitsSuffix = "یونٹس",
    totalExpenses = "کل اخراجات",
    nonStockEntriesSuffix = "اندراجات (بغیر اسٹاک)",
    accountsAndCashFlow = "کھاتہ اور کیش فلو",
    customerReceivable = "گاہکوں سے وصولی",
    supplierPayable = "سپلائرز کی ادائیگی",
    accountsSuffix = "اکاؤنٹس",
    cashReceived = "وصول شدہ نقد",
    cashReceivedSub = "فروخت + وصولی",
    cashPaid = "ادائیگی نقد",
    cashPaidSub = "خریداری + اخراجات + سپلائرز",
    pnlSectionTitle = "منافع اور نقصان کا بیان (اوسط لاگت)",
    todaysPnl = "آج کا منافع اور نقصان",
    monthlyPnl = "ماہانہ منافع اور نقصان",
    yearlyPnl = "سالانہ منافع اور نقصان",
    profitBadge = "منافع",
    lossBadge = "نقصان",
    totalSalesInvoiced = "کل فروخت (بل)",
    cashVsCredit = "نقد وصولی بمقابلہ ادھار",
    costOfGoodsSold = "فروخت شدہ مال کی لاگت",
    grossProfit = "مجموعی منافع",
    operatingExpenses = "پلانٹ اخراجات",
    netProfitLoss = "خالص منافع / نقصان",
    save = "محفوظ کریں",
    cancel = "منسوخ",
    close = "بند کریں",
    print = "پرنٹ",
    pdf = "پی ڈی ایف",
    savePdf = "پی ڈی ایف محفوظ",
    excelCsv = "ایکسل CSV",
    share = "شیئر",
    all = "سب",
    receipt = "رسید",
    printReceipt = "رسید پرنٹ",
    saveReceiptPdf = "رسید پی ڈی ایف",
    paymentReceiptTitle = "ادائیگی کی رسید",
    recoveryReceiptTitle = "وصولی کی رسید",
    voucher = "واؤچر",
    searchSalesPlaceholder = "انوائس نمبر، گاہک یا مواد تلاش کریں...",
    noSalesTitle = "کوئی فروخت انوائس نہیں",
    noSalesMessage = "فروخت، گاہک کھاتہ اور خودکار اسٹاک کٹوتی کے لیے پہلا بل بنائیں۔",
    createSaleInvoice = "نیا فروخت بل بنائیں",
    newSaleInvoice = "نئی فروخت انوائس",
    grandTotal = "کل رقم",
    paidAndBalance = "ادائیگی / بقایا",
    searchPurchasesPlaceholder = "واؤچر نمبر، سپلائر یا مواد تلاش کریں...",
    noPurchasesTitle = "کوئی خریداری واؤچر نہیں",
    noPurchasesMessage = "اسٹاک بڑھانے اور اوسط قیمت اپ ڈیٹ کرنے کے لیے خریداری واؤچر درج کریں۔",
    createPurchaseVoucher = "نیا خریداری واؤچر",
    newPurchaseVoucher = "نیا خریداری واؤچر",
    totalPurchase = "کل خریداری",
    paidAndPayable = "ادا شدہ / واجب الادا",
    materialsAndStockTab = "مواد اور اسٹاک",
    movementsTab = "اسٹاک ریکارڈ",
    searchMaterialsPlaceholder = "سیمنٹ، ریت، بجری، بلاک، سریا تلاش کریں...",
    lowStockFilterPrefix = "کم اسٹاک",
    adjustStock = "اسٹاک درست کریں",
    addMaterial = "نیا مواد شامل کریں",
    customersTab = "گاہک",
    suppliersTab = "سپلائرز",
    totalReceivableLabel = "گاہکوں سے کل وصولی",
    totalPayableLabel = "سپلائرز کو کل ادائیگی",
    searchCustomersPlaceholder = "نام یا فون سے گاہک تلاش کریں...",
    searchSuppliersPlaceholder = "نام یا فون سے سپلائر تلاش کریں...",
    addCustomer = "نیا گاہک",
    addSupplier = "نیا سپلائر",
    outstandingBalanceUpper = "کل بقایا رقم",
    recordRecovery = "وصولی درج کریں",
    recordPayment = "ادائیگی درج کریں",
    backToAccounts = "کھاتوں پر واپس",
    expenseOnlyBanner = "صرف اخراجات: ڈیزل، مزدوری، تنخواہ، بجلی، ٹرانسپورٹ اور مرمت سے مواد کا اسٹاک تبدیل نہیں ہوتا۔",
    filteredExpensesTotal = "منتخب اخراجات کا کل",
    addExpense = "نیا خرچ",
    reportDailySales = "روزانہ فروخت",
    reportWeeklySales = "ہفتہ وار فروخت",
    reportMonthlySales = "ماہانہ فروخت",
    reportYearlySales = "سالانہ فروخت",
    reportPurchases = "خریداری",
    reportStock = "مواد اسٹاک",
    reportCustomerLedger = "گاہک کھاتہ",
    reportSupplierLedger = "سپلائر کھاتہ",
    reportExpenses = "اخراجات",
    reportCashFlow = "کیش فلو",
    reportProfitLoss = "منافع اور نقصان",
    signOutLabel = "لاگ آؤٹ",
    businessAndInvoiceSettings = "کاروبار اور انوائس کی ترتیبات",
    saveBusinessSettings = "ترتیبات محفوظ کریں",
    languageSettingsTitle = "زبان / Language",
    languageSettingsSub = "انگریزی اور اردو (RTL) کے درمیان فوری تبدیلی کریں",
    adminControlsTitle = "ایڈمن اور سیکیورٹی کنٹرولز",
    adminControlsSub = "بل منسوخ کرنے، اسٹاک تبدیلی اور بیک اپ بحالی کو ایڈمن پن سے محفوظ بنائیں",
    operatorNameLabel = "آپریٹر / منیجر کا نام",
    requireAdminPinLabel = "حساس کاموں کے لیے ایڈمن پن لازمی کریں",
    requireAdminPinSub = "واؤچر منسوخی، اسٹاک ایڈجسٹمنٹ اور ری اسٹور پر پن لگائیں",
    adminPinLabel = "ایڈمن پن (4 سے 8 ہندسے)",
    adminPinHint = "4 سے 8 ہندسوں کا خفیہ پن درج کریں",
    verifyBalancesButton = "پارٹی کھاتوں کی مطابقت چیک کریں",
    enterAdminPinTitle = "ایڈمن کی اجازت درکار ہے",
    enterAdminPinMessage = "یہ حساس تبدیلی کرنے کے لیے مالک کا ایڈمن پن درج کریں۔",
    unlockWithPin = "تصدیق کریں",
    incorrectAdminPin = "غلط ایڈمن پن۔ براہ کرم دوبارہ کوشش کریں۔",
    backupRestoreAuditTitle = "بیک اپ، بحالی اور آڈٹ لاگ",
    backupJson = "بیک اپ JSON",
    restoreJson = "ری اسٹور JSON",
    viewAuditHistoryPrefix = "مالیاتی آڈٹ ہسٹری دیکھیں"
)

fun stringsFor(language: AppLanguage): AppStrings =
    if (language == AppLanguage.UR) UrduStrings else EnglishStrings

val LocalAppStrings = staticCompositionLocalOf { EnglishStrings }
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.EN }
