package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.FinancialNode
import com.example.ui.FinanceViewModel
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceMapScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val wallets by viewModel.wallets.collectAsStateWithLifecycle()
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()

    val totalIncome by viewModel.totalIncome.collectAsStateWithLifecycle()
    val totalBudgetLimit by viewModel.totalBudgetLimit.collectAsStateWithLifecycle()
    val totalSpent by viewModel.totalSpent.collectAsStateWithLifecycle()
    val budgetSpentMap by viewModel.budgetSpentMap.collectAsStateWithLifecycle()

    // Node layout mapping scaling
    var zoom by remember { mutableStateOf(1.0f) }
    var pan by remember { mutableStateOf(Offset(0f, 0f)) }

    // Floating UI States
    var selectedNodeForEdit by remember { mutableStateOf<FinancialNode?>(null) }
    var showAddBudgetDialog by remember { mutableStateOf<Long?>(null) } // parent wallet ID
    var showAddExpenseDialog by remember { mutableStateOf<Long?>(null) } // parent budget ID
    
    // Bottom Reports sheet state inside single view
    var isReportsSheetExpanded by remember { mutableStateOf(false) }
    var reportSearchQuery by remember { mutableStateOf("") }

    // Drag tracking maps to support in-memory 120fps node dragging
    val draggedNodePositions = remember { mutableStateMapOf<Long, Pair<Float, Float>>() }

    // Money Flow Animated Connection Stroke Dash Effect
    val infiniteTransition = rememberInfiniteTransition(label = "edges_flow")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -100f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_shift"
    )

    // Sophisticated M3 Dark theme layout values
    val workspaceBg = Color(0xFF1C1B1F) // Deep solid obsidian space background
    val cardBg = Color(0xFF25232A) // Sleek dark charcoal container base style
    val ringBorder = Color(0xFF49454F) // Polished lavender-charcoal border

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = Color(0xFFD0BCFF) // Sophisticated Purple
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NetFinance Workspace",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    // Reset Viewport button
                    IconButton(onClick = {
                        zoom = 1.0f
                        pan = Offset(0f, 0f)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Camera",
                            tint = Color.LightGray
                        )
                    }
                    IconButton(onClick = {
                        // Quick-seed custom wallet node if all cleared
                        if (wallets.isEmpty()) {
                            viewModel.addNode(
                                name = "Personal Vault",
                                amount = 4000.0,
                                type = "WALLET",
                                parentId = null,
                                colorHex = "#D0BCFF",
                                x = 500f,
                                y = 450f
                            )
                        } else {
                            // Link a budget category
                            showAddBudgetDialog = wallets.firstOrNull()?.id
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Add Budget Category",
                            tint = Color(0xFFD0BCFF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = workspaceBg,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(workspaceBg)
        ) {
            
            // ------------------ WORKSPACE INTERACTIVE GRID CANVAS ------------------
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panAmount, zoomAmount, _ ->
                            zoom = (zoom * zoomAmount).coerceIn(0.4f, 2.5f)
                            pan += panAmount
                        }
                    }
                    .testTag("workspace_canvas")
            ) {
                // Ground Grid Lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridSpacing = 80f * zoom
                    val startX = pan.x % gridSpacing
                    val startY = pan.y % gridSpacing
                    
                    var x = startX
                    while (x < size.width) {
                        drawLine(
                            color = Color(0xFF1E293B).copy(alpha = 0.4f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.2f
                        )
                        x += gridSpacing
                    }
                    
                    var y = startY
                    while (y < size.height) {
                        drawLine(
                            color = Color(0xFF1E293B).copy(alpha = 0.4f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.2f
                        )
                        y += gridSpacing
                    }
                }

                // 2D Money-Pipe Connectors
                Canvas(modifier = Modifier.fillMaxSize()) {
                    nodes.forEach { node ->
                        if (node.parentId != null) {
                            val parent = nodes.find { it.id == node.parentId }
                            if (parent != null) {
                                val parentX = draggedNodePositions[parent.id]?.first ?: parent.x
                                val parentY = draggedNodePositions[parent.id]?.second ?: parent.y
                                val childX = draggedNodePositions[node.id]?.first ?: node.x
                                val childY = draggedNodePositions[node.id]?.second ?: node.y

                                val startX = (parentX * zoom) + pan.x
                                val startY = (parentY * zoom) + pan.y
                                val endX = (childX * zoom) + pan.x
                                val endY = (childY * zoom) + pan.y

                                val edgePath = Path().apply {
                                    moveTo(startX, startY)
                                    val ctrlX1 = startX
                                    val ctrlY1 = (startY + endY) / 2
                                    val ctrlX2 = endX
                                    val ctrlY2 = (startY + endY) / 2
                                    cubicTo(ctrlX1, ctrlY1, ctrlX2, ctrlY2, endX, endY)
                                }

                                val rawColor = try {
                                    Color(android.graphics.Color.parseColor(node.colorHex))
                                } catch (e: Exception) {
                                    Color(0xFF38BDF8)
                                }

                                // Flow connection pipeline lines
                                drawPath(
                                    path = edgePath,
                                    color = rawColor.copy(alpha = 0.8f),
                                    style = Stroke(
                                        width = 3.dp.toPx() * zoom,
                                        pathEffect = PathEffect.dashPathEffect(
                                            floatArrayOf(18f * zoom, 12f * zoom), 
                                            phaseShift * zoom
                                        ),
                                        cap = StrokeCap.Round
                                    )
                                )
                            }
                        }
                    }
                }

                // Interactive Nodes Layer
                val density = LocalDensity.current
                nodes.forEach { node ->
                    val currentPos = draggedNodePositions[node.id] ?: Pair(node.x, node.y)
                    val nodeScreenX = (currentPos.first * zoom) + pan.x
                    val nodeScreenY = (currentPos.second * zoom) + pan.y
                    
                    val xDp = with(density) { nodeScreenX.toDp() }
                    val yDp = with(density) { nodeScreenY.toDp() }

                    Box(
                        modifier = Modifier
                            .offset(x = xDp, y = yDp)
                            .pointerInput(node.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggedNodePositions[node.id] = Pair(node.x, node.y)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val temp = draggedNodePositions[node.id] ?: Pair(node.x, node.y)
                                        val scaledX = temp.first + (dragAmount.x / zoom)
                                        val scaledY = temp.second + (dragAmount.y / zoom)
                                        draggedNodePositions[node.id] = Pair(scaledX, scaledY)
                                    },
                                    onDragEnd = {
                                        draggedNodePositions[node.id]?.let { final ->
                                            viewModel.updateNodePosition(node.id, final.first, final.second)
                                        }
                                        draggedNodePositions.remove(node.id)
                                    }
                                )
                            }
                    ) {
                        RenderNodeCard(
                            node = node,
                            zoom = zoom,
                            budgetSpentMap = budgetSpentMap,
                            onAddChild = { parentId ->
                                if (node.type == "WALLET") {
                                    showAddBudgetDialog = parentId
                                } else if (node.type == "BUDGET") {
                                    showAddExpenseDialog = parentId
                                }
                            },
                            onEdit = { selectedNodeForEdit = it }
                        )
                    }
                }
            }

            // ------------------ TOP SPENDING METRICS PANEL ------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg.copy(alpha = 0.95f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(16.dp))
                        .border(1.dp, ringBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monthly Pipeline Health",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                            val remainingVault = totalIncome - totalSpent
                            Text(
                                text = "Available: ${formatCurrency(remainingVault)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (remainingVault >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Monthly Income",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                                Text(
                                    text = formatCurrency(totalIncome),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Max Budget",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                                Text(
                                    text = formatCurrency(totalBudgetLimit),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF60A5FA)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Total Spent",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                                Text(
                                    text = formatCurrency(totalSpent),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF87171)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Joint Linear Progress meter
                        val pct = if (totalIncome > 0) (totalSpent / totalIncome).toFloat() else 0f
                        LinearProgressIndicator(
                            progress = { pct.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = when {
                                pct > 1.0f -> Color(0xFFE53935)
                                pct > 0.8f -> Color(0xFFFB8C00)
                                else -> Color(0xFF10B981)
                            },
                            trackColor = Color(0xFF334155)
                        )
                    }
                }
            }

            // ------------------ FLOATING INSTRUCTION TIP ------------------
            if (nodes.isEmpty() || (wallets.isEmpty())) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "NetFinance Mind Map",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create your income sources (Wallets), connect expense budgets, and pin transactions visually. Drag nodes to reshape and design your direct flow pipeline!",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.addNode(
                                    name = "Primary Vault",
                                    amount = 4500.0,
                                    type = "WALLET",
                                    parentId = null,
                                    colorHex = "#D0BCFF",
                                    x = 540f,
                                    y = 450f
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD0BCFF),
                                contentColor = Color(0xFF381E72)
                            )
                        ) {
                            Text("Spawn Primary Wallet ($4,500)")
                        }
                    }
                }
            } else {
                // Helpful tiny cue hint in corner
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 80.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "💡 Drag nodes to rearrange • Zoom with double-finger pinch",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // ------------------ REPORTS BOTTOM sliding deck ------------------
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .heightIn(min = 64.dp, max = 550.dp)
                    .animateContentSize(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = cardBg,
                shadowElevation = 16.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, ringBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    
                    // Drag/Tab indicator handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isReportsSheetExpanded = !isReportsSheetExpanded }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 4.dp)
                                    .background(Color.Gray.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isReportsSheetExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Monthly Spending Reports",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    if (isReportsSheetExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            
                            // 1. Double column: Donut Visual Report & Category spendings
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left part of deck: Custom Donut Canvas representation
                                Box(
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val validBudgets = budgets.filter { (budgetSpentMap[it.id] ?: 0.0) > 0.0 }
                                    
                                    Canvas(
                                        modifier = Modifier
                                            .size(130.dp)
                                            .testTag("report_pie_chart")
                                    ) {
                                        if (validBudgets.isEmpty()) {
                                            // Empty state donut ring
                                            drawArc(
                                                color = Color(0xFF334155),
                                                startAngle = 0f,
                                                sweepAngle = 360f,
                                                useCenter = false,
                                                style = Stroke(width = 24f, cap = StrokeCap.Round)
                                            )
                                        } else {
                                            var currentAngle = -90f
                                            val categoryTotalsSum = validBudgets.sumOf { budgetSpentMap[it.id] ?: 0.0 }
                                            
                                            validBudgets.forEach { budget ->
                                                val spentVal = budgetSpentMap[budget.id] ?: 0.0
                                                val sweepAngle = ((spentVal / categoryTotalsSum) * 360f).toFloat()
                                                
                                                val col = try {
                                                    Color(android.graphics.Color.parseColor(budget.colorHex))
                                                } catch (e: Exception) {
                                                    Color(0xFF38BDF8)
                                                }
                                                
                                                drawArc(
                                                    color = col,
                                                    startAngle = currentAngle,
                                                    sweepAngle = sweepAngle,
                                                    useCenter = false,
                                                    style = Stroke(width = 24f, cap = StrokeCap.Round)
                                                )
                                                currentAngle += sweepAngle
                                            }
                                        }
                                    }
                                    
                                    // Donut Inner Summary Text
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = formatCurrency(totalSpent),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Spent Total",
                                            fontSize = 9.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Right part: Category progress bars scrolling panel inside the row
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .fillMaxHeight()
                                ) {
                                    items(budgets) { budget ->
                                        val spent = budgetSpentMap[budget.id] ?: 0.0
                                        val percent = if (budget.amount > 0.0) (spent / budget.amount).toFloat() else 0f
                                        val col = try {
                                            Color(android.graphics.Color.parseColor(budget.colorHex))
                                        } catch (e: Exception) {
                                            Color(0xFF38BDF8)
                                        }

                                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = budget.name,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "${(percent * 100).toInt()}%",
                                                    fontSize = 10.sp,
                                                    color = col,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = "${formatCurrency(spent)} of ${formatCurrency(budget.amount)}",
                                                fontSize = 9.sp,
                                                color = Color.LightGray
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            LinearProgressIndicator(
                                                progress = { percent.coerceIn(0f, 1f) },
                                                color = col,
                                                trackColor = Color(0xFF334155),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                            )
                                        }
                                    }
                                }
                            }

                            Divider(color = ringBorder, modifier = Modifier.padding(vertical = 12.dp))

                            // 2. Search & Transaction Logs
                            OutlinedTextField(
                                value = reportSearchQuery,
                                onValueChange = { reportSearchQuery = it },
                                label = { Text("Filter expenses...", color = Color.Gray) },
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.LightGray) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF10B981),
                                    unfocusedBorderColor = ringBorder,
                                    focusedLabelColor = Color(0xFF10B981),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val filteredExpenses = expenses.filter {
                                reportSearchQuery.isEmpty() || it.name.lowercase().contains(reportSearchQuery.lowercase())
                            }

                            if (filteredExpenses.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No matching expenses recorded.",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    items(filteredExpenses) { expense ->
                                        val parentCategory = budgets.find { it.id == expense.parentId }
                                        val color = try {
                                            Color(android.graphics.Color.parseColor(parentCategory?.colorHex ?: "#94A3B8"))
                                        } catch (e: Exception) {
                                            Color(0xFF94A3B8)
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp)
                                                .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(color, CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = expense.name,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        text = parentCategory?.name ?: "Category",
                                                        color = Color.LightGray,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                            
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = formatCurrency(expense.amount),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 14.sp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                IconButton(
                                                    onClick = { viewModel.deleteNode(expense) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Expense",
                                                        tint = Color(0xFFF87171),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ------------------ MODAL DIALOGS INTERFACE ------------------

        // 1. Add Category Dialog
        showAddBudgetDialog?.let { parentId ->
            var categoryName by remember { mutableStateOf("") }
            var budgetLimit by remember { mutableStateOf("") }
            var selectedColorHex by remember { mutableStateOf("#FF9800") } // Amber Default

            val colorsOption = listOf(
                "#FF9800", // Amber Orange
                "#E53935", // Coral Red
                "#0288D1", // Ocean Blue
                "#9C27B0", // Amethyst Purple
                "#00897B", // Emerald Teal
                "#FDD835"  // Golden Yellow
            )

            Dialog(onDismissRequest = { showAddBudgetDialog = null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(18.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Add Budget Category",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = categoryName,
                            onValueChange = { categoryName = it },
                            label = { Text("Category Name", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = ringBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.testTag("budget_name_input")
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = budgetLimit,
                            onValueChange = { budgetLimit = it },
                            label = { Text("Monthly Budget Limit ($)", color = Color.Gray) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = ringBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.testTag("budget_amount_input")
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Select Accent Identity Paint",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            colorsOption.forEach { colorString ->
                                val rgbColor = Color(android.graphics.Color.parseColor(colorString))
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(rgbColor)
                                        .border(
                                            width = if (selectedColorHex == colorString) 3.dp else 0.dp,
                                            color = Color.White,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColorHex = colorString }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showAddBudgetDialog = null }) {
                                Text("Cancel", color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val limitVal = budgetLimit.toDoubleOrNull() ?: 0.0
                                    if (categoryName.isNotEmpty() && limitVal > 0.0) {
                                        // Spawn category node positioned offsets below wallet
                                        viewModel.addNode(
                                            name = categoryName,
                                            amount = limitVal,
                                            type = "BUDGET",
                                            parentId = parentId,
                                            colorHex = selectedColorHex,
                                            x = 540f + (-150..150).random().toFloat(),
                                            y = 650f + (-50..150).random().toFloat()
                                        )
                                        showAddBudgetDialog = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD0BCFF),
                                    contentColor = Color(0xFF381E72)
                                ),
                                modifier = Modifier.testTag("save_budget_button")
                            ) {
                                Text("Connect Node")
                            }
                        }
                    }
                }
            }
        }

        // 2. Add Expense Dialog
        showAddExpenseDialog?.let { parentCategoryId ->
            var expenseName by remember { mutableStateOf("") }
            var expenseAmount by remember { mutableStateOf("") }
            val parentCategoryNode = budgets.find { it.id == parentCategoryId }

            val themeColorString = parentCategoryNode?.colorHex ?: "#94A3B8"

            Dialog(onDismissRequest = { showAddExpenseDialog = null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(18.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Record Expense under ${parentCategoryNode?.name ?: "Category"}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = expenseName,
                            onValueChange = { expenseName = it },
                            label = { Text("Expense Item / Merchant", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(android.graphics.Color.parseColor(themeColorString)),
                                unfocusedBorderColor = ringBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.testTag("expense_name_input")
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = expenseAmount,
                            onValueChange = { expenseAmount = it },
                            label = { Text("Amount Spent ($)", color = Color.Gray) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(android.graphics.Color.parseColor(themeColorString)),
                                unfocusedBorderColor = ringBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.testTag("expense_amount_input")
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showAddExpenseDialog = null }) {
                                Text("Cancel", color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val amountVal = expenseAmount.toDoubleOrNull() ?: 0.0
                                    if (expenseName.isNotEmpty() && amountVal > 0.0) {
                                        // Spawn expense node nearby parent budget and link
                                        val parentX = parentCategoryNode?.x ?: 540f
                                        val parentY = parentCategoryNode?.y ?: 700f
                                        viewModel.addNode(
                                            name = expenseName,
                                            amount = amountVal,
                                            type = "EXPENSE",
                                            parentId = parentCategoryId,
                                            colorHex = themeColorString,
                                            x = parentX + (-120..120).random().toFloat(),
                                            y = parentY + (100..180).random().toFloat()
                                        )
                                        showAddExpenseDialog = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(android.graphics.Color.parseColor(themeColorString))
                                ),
                                modifier = Modifier.testTag("save_expense_button")
                            ) {
                                Text("Link Money")
                            }
                        }
                    }
                }
            }
        }

        // 3. Edit / Delete Node Dialog
        selectedNodeForEdit?.let { node ->
            var nodeName by remember { mutableStateOf(node.name) }
            var nodeAmountString by remember { mutableStateOf(node.amount.toString()) }
            var selectedColorHex by remember { mutableStateOf(node.colorHex) }

            val colorsOption = listOf(
                "#10B981", // Wallet Green
                "#FF9800", // Amber Orange
                "#E53935", // Coral Red
                "#0288D1", // Ocean Blue
                "#9C27B0", // Amethyst Purple
                "#00897B", // Emerald Teal
                "#FDD835"  // Golden Yellow
            )

            Dialog(onDismissRequest = { selectedNodeForEdit = null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(18.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Modify ${node.type.lowercase().replaceFirstChar { it.uppercase() }} Node",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = nodeName,
                            onValueChange = { nodeName = it },
                            label = { Text("Display Name", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = ringBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = nodeAmountString,
                            onValueChange = { nodeAmountString = it },
                            label = { 
                                Text(
                                    text = when (node.type) {
                                        "WALLET" -> "Vault Income Pool ($)"
                                        "BUDGET" -> "Budget limit allocated ($)"
                                        else -> "Spent amount ($)"
                                    },
                                    color = Color.Gray
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = ringBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        if (node.type != "EXPENSE") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Adjust Color Identity",
                                fontSize = 11.sp,
                                color = Color.LightGray,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                colorsOption.forEach { colorString ->
                                    val rgbColor = Color(android.graphics.Color.parseColor(colorString))
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(rgbColor)
                                            .border(
                                                width = if (selectedColorHex == colorString) 3.dp else 0.dp,
                                                color = Color.White,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedColorHex = colorString }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Delete trigger button
                            Button(
                                onClick = {
                                    viewModel.deleteNode(node)
                                    selectedNodeForEdit = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                modifier = Modifier.testTag("delete_node_button")
                            ) {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete")
                            }

                            Row {
                                TextButton(onClick = { selectedNodeForEdit = null }) {
                                    Text("Dismiss", color = Color.Gray)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val amt = nodeAmountString.toDoubleOrNull() ?: 0.0
                                        if (nodeName.isNotEmpty() && amt >= 0.0) {
                                            viewModel.updateNodeDetails(node.id, nodeName, amt, selectedColorHex)
                                            selectedNodeForEdit = null
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD0BCFF),
                                        contentColor = Color(0xFF381E72)
                                    ),
                                    modifier = Modifier.testTag("save_node_edit")
                                ) {
                                    Text("Apply")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenderNodeCard(
    node: FinancialNode,
    zoom: Float,
    budgetSpentMap: Map<Long, Double>,
    onAddChild: (Long) -> Unit,
    onEdit: (FinancialNode) -> Unit
) {
    val nodePaintColor = try {
        Color(android.graphics.Color.parseColor(node.colorHex))
    } catch (e: Exception) {
        Color(0xFF6B7280)
    }

    val dynamicPadding = (8 * zoom).coerceAtLeast(4f).dp
    val dynamicRadius = (16 * zoom).coerceAtLeast(8f).dp
    val textFontSize = (12 * zoom).coerceAtLeast(8f).sp
    val amountFontSize = (14 * zoom).coerceAtLeast(10f).sp

    // Interactive custom container depending on types
    Card(
        shape = RoundedCornerShape(dynamicRadius),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B).copy(alpha = 0.9f) // Semi transparent slate
        ),
        modifier = Modifier
            .widthIn(max = (180 * zoom).dp)
            .shadow(
                elevation = (8 * zoom).coerceAtLeast(2f).dp,
                shape = RoundedCornerShape(dynamicRadius)
            )
            .border(
                width = (1.5f * zoom).coerceAtLeast(1f).dp,
                color = nodePaintColor,
                shape = RoundedCornerShape(dynamicRadius)
            )
            .clickable { onEdit(node) }
            .testTag("node_${node.type.lowercase()}_${node.id}")
    ) {
        Column(
            modifier = Modifier.padding(dynamicPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Header: Category Icon & Action Plus Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getCategoryIcon(node.name, node.type),
                    contentDescription = null,
                    tint = nodePaintColor,
                    modifier = Modifier.size((16f * zoom).coerceAtLeast(11f).dp)
                )
                
                // Add sub-node plus trigger (For Wallet or Budget)
                if (node.type != "EXPENSE") {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(nodePaintColor.copy(alpha = 0.2f))
                            .clickable { onAddChild(node.id) }
                            .padding((3 * zoom).coerceAtLeast(2f).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Child Connecting Node",
                            tint = nodePaintColor,
                            modifier = Modifier.size((12f * zoom).coerceAtLeast(8f).dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height((4 * zoom).coerceAtLeast(2f).dp))

            // Body: Title & Amount label
            Text(
                text = node.name,
                fontWeight = FontWeight.Bold,
                fontSize = textFontSize,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = formatCurrency(node.amount),
                color = if (node.type == "EXPENSE") Color(0xFFF87171) else Color(0xFFE2E8F0),
                fontWeight = FontWeight.ExtraBold,
                fontSize = amountFontSize,
                textAlign = TextAlign.Center
            )

            // Category progress tracking indicators
            if (node.type == "BUDGET") {
                val spent = budgetSpentMap[node.id] ?: 0.0
                val percent = if (node.amount > 0) (spent / node.amount).toFloat() else 0f
                
                Spacer(modifier = Modifier.height((6 * zoom).coerceAtLeast(3f).dp))

                // Custom linear gauge on node
                LinearProgressIndicator(
                    progress = { percent.coerceIn(0f, 1f) },
                    color = when {
                        percent > 1.0f -> Color(0xFFEF4444)
                        percent > 0.8f -> Color(0xFFF59E0B)
                        else -> nodePaintColor
                    },
                    trackColor = Color(0xFF334155),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((3 * zoom).coerceAtLeast(2f).dp)
                        .clip(RoundedCornerShape(1.5f.dp))
                )

                Text(
                    text = "Spent: ${formatCurrency(spent)}",
                    fontSize = (9 * zoom).coerceAtLeast(7f).sp,
                    color = if (spent > node.amount) Color(0xFFEF4444) else Color.LightGray,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ------------------ UTILS HELPERS ------------------

private fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    return format.format(amount)
}

private fun getCategoryIcon(name: String, type: String) = when (type) {
    "WALLET" -> Icons.Default.AccountBalanceWallet
    else -> {
        val lower = name.lowercase()
        when {
            lower.contains("food") || lower.contains("grocery") || lower.contains("eat") || lower.contains("pizza") || lower.contains("dining") -> Icons.Default.Fastfood
            lower.contains("transport") || lower.contains("car") || lower.contains("fuel") || lower.contains("gas") || lower.contains("taxi") -> Icons.Default.DirectionsCar
            lower.contains("fun") || lower.contains("leisure") || lower.contains("movie") || lower.contains("drink") || lower.contains("cinema") -> Icons.Default.LocalPlay
            lower.contains("bill") || lower.contains("rent") || lower.contains("utility") || lower.contains("phone") || lower.contains("internet") -> Icons.Default.Lightbulb
            lower.contains("shop") || lower.contains("clothe") || lower.contains("mall") -> Icons.Default.ShoppingCart
            else -> Icons.Default.AttachMoney
        }
    }
}
