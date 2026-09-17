package com.hassangarage.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HassanGarageApp() }
    }
}

data class SaleItem(val name: String, val price: Double)

data class GarageJob(
    val customer: String,
    val vehicle: String,
    val plate: String,
    val repairNote: String,
    val parkingFee: Double = 100.0
)

@Composable
fun HassanGarageApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var sales by remember { mutableStateOf(listOf<SaleItem>()) }
    var jobs by remember { mutableStateOf(listOf<GarageJob>()) }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Hassan Garage") }) },
            bottomBar = {
                NavigationBar {
                    listOf("بيع", "سيارات", "بحث", "مخزون").forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Text(if (selectedTab == index) "●" else "○") },
                            label = { Text(title) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (selectedTab) {
                    0 -> SalesScreen(sales) { item -> sales = sales + item }
                    1 -> JobsScreen(jobs) { job -> jobs = jobs + job }
                    2 -> SearchScreen(jobs, sales)
                    else -> InventoryScreen()
                }
            }
        }
    }
}

@Composable
private fun SalesScreen(sales: List<SaleItem>, onSale: (SaleItem) -> Unit) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var showDone by remember { mutableStateOf(false) }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("بيع قطعة", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { name = it }, label = { Text("اسم القطعة") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(price, { price = it }, label = { Text("السعر") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                val amount = price.toDoubleOrNull()
                if (name.isNotBlank() && amount != null) {
                    onSale(SaleItem(name.trim(), amount))
                    name = ""
                    price = ""
                    showDone = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("تم البيع") }

        if (showDone) {
            AssistChip(onClick = { showDone = false }, label = { Text("✓ تم تسجيل البيع") })
        }

        if (sales.isNotEmpty()) {
            Text("آخر المبيعات", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sales.reversed()) { sale ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(sale.name)
                            Text("D ${"%.2f".format(sale.price)}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobsScreen(jobs: List<GarageJob>, onAdd: (GarageJob) -> Unit) {
    var customer by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("دخول سيارة", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(customer, { customer = it }, label = { Text("اسم الزبون") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(vehicle, { vehicle = it }, label = { Text("نوع السيارة") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(plate, { plate = it }, label = { Text("رقم اللوحة") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("ملاحظات التصليح") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            if (vehicle.isNotBlank()) {
                onAdd(GarageJob(customer, vehicle, plate, note))
                customer = ""; vehicle = ""; plate = ""; note = ""
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("حفظ - رسم الدخول D 100") }

        if (jobs.isNotEmpty()) {
            HorizontalDivider()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(jobs.reversed()) { job ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(job.vehicle, style = MaterialTheme.typography.titleMedium)
                            Text("${job.customer}  •  ${job.plate}")
                            if (job.repairNote.isNotBlank()) Text(job.repairNote)
                            Text("Parking: D ${job.parkingFee.toInt()}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(jobs: List<GarageJob>, sales: List<SaleItem>) {
    var query by remember { mutableStateOf("") }
    val foundJobs = jobs.filter {
        it.customer.contains(query, true) || it.vehicle.contains(query, true) || it.plate.contains(query, true)
    }
    val foundSales = sales.filter { it.name.contains(query, true) }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("بحث", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(query, { query = it }, label = { Text("اسم، سيارة، لوحة أو قطعة") }, modifier = Modifier.fillMaxWidth())
        if (query.isNotBlank()) {
            Text("السيارات: ${foundJobs.size} | المبيعات: ${foundSales.size}")
        }
    }
}

@Composable
private fun InventoryScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("المخزون - سنضيفه في الخطوة التالية")
    }
}
