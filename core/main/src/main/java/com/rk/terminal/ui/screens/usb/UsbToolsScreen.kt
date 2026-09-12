package com.rk.terminal.ui.screens.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.resources.strings
import com.rk.terminal.ui.screens.settings.SettingsCard
import com.rk.terminal.usb.UsbAdbFastbootManager
import com.rk.terminal.usb.UsbProtocol
import kotlinx.coroutines.launch

@Composable
fun UsbToolsScreen(navController: NavController) {
    val context = LocalContext.current
    val manager = remember { UsbAdbFastbootManager(context) }
    var targets by remember { mutableStateOf(manager.targets()) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(manager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == UsbAdbFastbootManager.ACTION_USB_PERMISSION) {
                    targets = manager.targets()
                    status = if (intent.getBooleanExtra(android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        context.getString(strings.usb_permission_granted)
                    } else {
                        context.getString(strings.usb_permission_denied)
                    }
                }
            }
        }
        manager.registerReceiver(receiver)
        onDispose {
            manager.unregisterReceiver(receiver)
            manager.close()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(strings.usb_tools), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(strings.usb_tools_desc))
        Button(onClick = { targets = manager.targets(); status = null }) {
            Text(stringResource(strings.usb_scan))
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(targets, key = { it.device.deviceName }) { target ->
                SettingsCard(
                    title = { Text(target.description) },
                    description = {
                        Text(
                            if (target.protocol == UsbProtocol.ADB) {
                                stringResource(strings.usb_adb)
                            } else {
                                stringResource(strings.usb_fastboot)
                            }
                        )
                    },
                    onClick = {
                        if (!manager.hasPermission(target)) {
                            manager.requestPermission(target)
                        } else if (target.protocol == UsbProtocol.FASTBOOT) {
                            scope.launch {
                                status = runCatching {
                                    manager.fastbootGetvar(target, "product")
                                }.getOrElse { it.message ?: "Fastboot command failed" }
                            }
                        } else {
                            status = context.getString(strings.usb_adb_detected)
                        }
                    }
                )
            }
        }
    }
}
