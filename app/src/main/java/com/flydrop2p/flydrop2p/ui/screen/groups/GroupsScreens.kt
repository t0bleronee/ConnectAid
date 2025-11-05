package com.flydrop2p.flydrop2p.ui.screen.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.flydrop2p.flydrop2p.ui.FlyDropTopAppBar
import androidx.compose.ui.text.input.TextFieldValue
import com.flydrop2p.flydrop2p.ui.components.TextMessageInput

object GroupsDestination : com.flydrop2p.flydrop2p.ui.navigation.NavigationDestination {
    override val route: String = "groups"
    override val titleRes: Int = 0
}

object GroupChatDestination : com.flydrop2p.flydrop2p.ui.navigation.NavigationDestination {
    override val route: String = "group_chat"
    override val titleRes: Int = 0
    const val groupArg = "group"
    val routeWithArgs = "$route/{$groupArg}"
}

@Composable
fun GroupsScreen(
    navController: NavHostController,
) {
    val networkManager = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.flydrop2p.flydrop2p.App).container.networkManager
    val groups by networkManager.getGroups().collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            FlyDropTopAppBar(
                title = "Groups",
                canNavigateBack = true,
                isSettingsScreen = false,
                onConnectionButtonClick = {},
                onSettingsButtonClick = { },
                navigateUp = { navController.popBackStack() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(painter = androidx.compose.ui.res.painterResource(id = com.flydrop2p.flydrop2p.R.drawable.add), contentDescription = "Create group")
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            groups.toList().sorted().forEachIndexed { index, name ->
                GroupCard(name) { navController.navigate("${GroupChatDestination.route}/$name") }
                if (index < groups.size - 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                confirmButton = {
                    Button(onClick = {
                        val n = groupName.trim()
                        if (n.isNotEmpty()) {
                            networkManager.addGroup(n)
                            showDialog = false
                            groupName = ""
                            navController.navigate("${GroupChatDestination.route}/$n")
                        }
                    }) { Text("Create") }
                },
                dismissButton = {
                    Button(onClick = { showDialog = false }) { Text("Cancel") }
                },
                title = { Text("Create group") },
                text = {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group name") },
                        singleLine = true
                    )
                }
            )
        }
    }
}

@Composable
private fun GroupCard(name: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Text(text = name.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge)
        Text(text = "Tap to join and chat", color = Color.Gray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    navController: NavHostController,
    group: String
) {
    val networkManager = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.flydrop2p.flydrop2p.App).container.networkManager
    val messages by networkManager.getGroupMessages(group).collectAsState()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.flydrop2p.flydrop2p.App
    val ownAccountFlow = app.container.ownAccountRepository.getAccountAsFlow()
    val ownAccount by ownAccountFlow.collectAsState(initial = com.flydrop2p.flydrop2p.domain.model.device.Account(0,0))
    val ownId = ownAccount.accountId

    Scaffold(
        topBar = {
            FlyDropTopAppBar(
                title = group.replaceFirstChar { it.uppercase() },
                canNavigateBack = true,
                isSettingsScreen = false,
                onConnectionButtonClick = {},
                onSettingsButtonClick = { },
                navigateUp = { navController.popBackStack() }
            )
        },
        bottomBar = {
            var textState by remember { mutableStateOf(TextFieldValue()) }
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                androidx.compose.material3.TextField(
                    value = textState,
                    onValueChange = { v -> textState = v },
                    placeholder = { androidx.compose.material3.Text("Send a message...", fontSize = 14.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(70.dp),
                    colors = androidx.compose.material3.TextFieldDefaults.textFieldColors(
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = Color.Transparent,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                )

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(0.dp))

                com.flydrop2p.flydrop2p.ui.components.SendButton(
                    onClick = {
                        val text = textState.text.trim()
                        if (text.isNotEmpty()) {
                            networkManager.sendGroupTextMessage(group, text)
                            textState = TextFieldValue()
                        }
                    }
                )
            }
        }
    ) { inner ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages.size) { idx ->
                val m = messages[idx]
                GroupMessageBubble(
                    isOwn = m.senderId == ownId,
                    message = m
                )
            }
        }
    }
}

@Composable
private fun GroupMessageBubble(isOwn: Boolean, message: com.flydrop2p.flydrop2p.network.model.message.NetworkGroupTextMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        Text(
            text = message.text,
            color = if (isOwn) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .background(if (isOwn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                .padding(12.dp)
        )
    }
}


