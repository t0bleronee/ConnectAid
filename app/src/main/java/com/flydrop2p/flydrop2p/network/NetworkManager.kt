package com.flydrop2p.flydrop2p.network

import android.net.Uri
import com.flydrop2p.flydrop2p.HandlerFactory
import com.flydrop2p.flydrop2p.data.local.FileManager
import com.flydrop2p.flydrop2p.domain.model.device.Account
import com.flydrop2p.flydrop2p.domain.model.device.Device
import com.flydrop2p.flydrop2p.domain.model.device.Profile
import com.flydrop2p.flydrop2p.domain.model.device.toAccount
import com.flydrop2p.flydrop2p.domain.model.device.toNetworkDevice
import com.flydrop2p.flydrop2p.domain.model.message.AudioMessage
import com.flydrop2p.flydrop2p.domain.model.message.FileMessage
import com.flydrop2p.flydrop2p.domain.model.message.MessageState
import com.flydrop2p.flydrop2p.domain.model.message.TextMessage
import com.flydrop2p.flydrop2p.domain.model.message.toNetworkTextMessage
import com.flydrop2p.flydrop2p.domain.repository.ChatRepository
import com.flydrop2p.flydrop2p.domain.repository.ContactRepository
import com.flydrop2p.flydrop2p.domain.repository.OwnAccountRepository
import com.flydrop2p.flydrop2p.domain.repository.OwnProfileRepository
import com.flydrop2p.flydrop2p.network.model.call.NetworkCallEnd
import com.flydrop2p.flydrop2p.network.model.call.NetworkCallRequest
import com.flydrop2p.flydrop2p.network.model.call.NetworkCallResponse
import com.flydrop2p.flydrop2p.network.model.device.NetworkDevice
import com.flydrop2p.flydrop2p.network.model.device.NetworkProfile
import com.flydrop2p.flydrop2p.network.model.keepalive.NetworkKeepalive
import com.flydrop2p.flydrop2p.network.model.message.NetworkAudioMessage
import com.flydrop2p.flydrop2p.network.model.message.NetworkFileMessage
import com.flydrop2p.flydrop2p.network.model.message.NetworkGroupTextMessage
import com.flydrop2p.flydrop2p.network.model.message.NetworkMessageAck
import com.flydrop2p.flydrop2p.network.model.message.NetworkTextMessage
import com.flydrop2p.flydrop2p.network.model.sos.NetworkSosAlert
import com.flydrop2p.flydrop2p.network.model.profile.NetworkProfileRequest
import com.flydrop2p.flydrop2p.network.model.profile.NetworkProfileResponse
import com.flydrop2p.flydrop2p.network.service.ClientService
import com.flydrop2p.flydrop2p.network.service.ServerService
import com.flydrop2p.flydrop2p.network.wifidirect.WiFiDirectBroadcastReceiver
import com.flydrop2p.flydrop2p.notification.NotificationHelper
import com.flydrop2p.flydrop2p.data.local.LocationProvider
import com.flydrop2p.flydrop2p.data.local.outbox.OutboxRepository
import com.flydrop2p.flydrop2p.network.wifidirect.WiFiDirectBroadcastReceiver.Companion.IP_GROUP_OWNER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File


class NetworkManager(
    ownAccountRepository: OwnAccountRepository,
    ownProfileRepository: OwnProfileRepository,
    private val handlerFactory: HandlerFactory,
    val receiver: WiFiDirectBroadcastReceiver,
    private val chatRepository: ChatRepository,
    private val contactRepository: ContactRepository,
    private val fileManager: FileManager,
    private val notificationHelper: NotificationHelper,
    private val locationProvider: LocationProvider,
    private val outboxRepository: OutboxRepository
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var ownDevice = Device(null, 0, Account(0, 0), Profile(0, 0, "", null))

    private val _connectedDevices: MutableStateFlow<List<NetworkDevice>> = MutableStateFlow(listOf())
    val connectedDevices: StateFlow<List<NetworkDevice>>
        get() = _connectedDevices

    private val _callRequest: MutableStateFlow<NetworkCallRequest?> = MutableStateFlow(null)
    val callRequest: StateFlow<NetworkCallRequest?>
        get() = _callRequest

    private val _callResponse: MutableStateFlow<NetworkCallResponse?> = MutableStateFlow(null)
    val callResponse: StateFlow<NetworkCallResponse?>
        get() = _callResponse

    private val _callEnd: MutableStateFlow<NetworkCallEnd?> = MutableStateFlow(null)
    val callEnd: StateFlow<NetworkCallEnd?>
        get() = _callEnd


    private val _callFragment: MutableStateFlow<ByteArray?> = MutableStateFlow(null)
    val callFragment: StateFlow<ByteArray?>
        get() = _callFragment

    private val serverService = ServerService()
    private val clientService = ClientService()

    init {
        coroutineScope.launch {
            ownAccountRepository.getAccountAsFlow().collect {
                ownDevice = ownDevice.copy(account = it)
            }
        }

        coroutineScope.launch {
            ownProfileRepository.getProfileAsFlow().collect {
                ownDevice = ownDevice.copy(profile = it)
            }
        }
    }

    private val _groups: MutableStateFlow<Set<String>> = MutableStateFlow(setOf("food", "rescue"))
    fun getGroups(): StateFlow<Set<String>> = _groups

    fun addGroup(displayName: String) {
        val normalized = displayName.trim()
        if (normalized.isNotEmpty()) {
            _groups.value = _groups.value + normalized
        }
    }

    fun startDiscoverPeersHandler() {
        val handler = handlerFactory.buildHandler()

        val runnable = object : Runnable {
            override fun run() {
                receiver.discoverPeers()
                handler.postDelayed(this, 5000)
            }
        }

        handler.post(runnable)
    }

    fun startSendKeepaliveHandler() {
        val handler = handlerFactory.buildHandler()

        val runnable = object : Runnable {
            override fun run() {
                sendKeepalive()
                handler.postDelayed(this, 1000)
            }
        }

        handler.post(runnable)
    }

    fun startUpdateConnectedDevicesHandler() {
        val handler = handlerFactory.buildHandler()

        val runnable = object : Runnable {
            override fun run() {
                updateConnectedDevices()
                flushOutboxIfPossible()
                handler.postDelayed(this, 1000)
            }
        }

        handler.post(runnable)
    }

    private fun flushOutboxIfPossible() {
        coroutineScope.launch {
            // Best-effort: send due texts to connected receivers
            val now = System.currentTimeMillis()
            val due = try {
                (outboxRepository.getDue(now))
            } catch (_: Exception) { emptyList() }

            due.forEach { item ->
                val ip = connectedDevices.value.find { it.account.accountId == item.receiverAccountId }?.ipAddress
                if (ip == null) {
                    // receiver offline; set simple backoff
                    val next = now + 2000L
                    try { outboxRepository.bumpRetry(item.id, next) } catch (_: Exception) {}
                    return@forEach
                }

                try {
                    when (item.type) {
                        "TEXT" -> {
                            val msg = TextMessage(item.messageId, ownDevice.account.accountId, item.receiverAccountId, System.currentTimeMillis(), MessageState.MESSAGE_SENT, item.payloadText ?: "")
                            clientService.sendTextMessage(ip, ownDevice, msg.toNetworkTextMessage())
                        }
                    }
                    outboxRepository.deleteById(item.id)
                } catch (_: Exception) {
                    val next = now + (1000L * (1 shl (item.retryCount.coerceAtMost(5))))
                    try { outboxRepository.bumpRetry(item.id, next) } catch (_: Exception) {}
                }
            }
        }
    }

    fun updateConnectedDevices() {
        val currentTimestamp = System.currentTimeMillis()
        _connectedDevices.value = _connectedDevices.value.filter { currentTimestamp - it.keepalive <= 3000 }
    }

    fun resetCallStateFlows() {
        _callRequest.value = null
        _callResponse.value = null
        _callEnd.value = null
    }

    fun sendKeepalive() {
        coroutineScope.launch {
            ownDevice.keepalive = System.currentTimeMillis()
            val networkKeepalive = NetworkKeepalive(connectedDevices.value + ownDevice.toNetworkDevice())

            if(ownDevice.ipAddress != IP_GROUP_OWNER) {
                clientService.sendKeepalive(IP_GROUP_OWNER, ownDevice, networkKeepalive)
            }

            connectedDevices.value.forEach { device ->
                device.ipAddress?.let {
                    clientService.sendKeepalive(it, ownDevice, networkKeepalive)
                }
            }
        }
    }

    fun sendProfileRequest(accountId: Long) {
        val connectedDevice = connectedDevices.value.find { it.account.accountId == accountId }

        connectedDevice?.let { device ->
            device.ipAddress?.let { ipAddress ->
                coroutineScope.launch {
                    val networkProfileRequest = NetworkProfileRequest(ownDevice.account.accountId, accountId)
                    clientService.sendProfileRequest(ipAddress, ownDevice, networkProfileRequest)
                }
            }
        }
    }

    fun sendProfileResponse(accountId: Long) {
        val connectedDevice = connectedDevices.value.find { it.account.accountId == accountId }

        connectedDevice?.let { device ->
            device.ipAddress?.let { ipAddress ->
                coroutineScope.launch {
                    val imageBase64 = ownDevice.profile.imageFileName?.let { fileName ->
                        fileManager.getFileBase64(fileName)
                    }

                    val networkProfile = NetworkProfile(ownDevice.profile, imageBase64)
                    val networkProfileResponse = NetworkProfileResponse(ownDevice.account.accountId, accountId, networkProfile)
                    clientService.sendProfileResponse(ipAddress, ownDevice, networkProfileResponse)
                }
            }
        }
    }

    fun sendTextMessage(accountId: Long, text: String) {
        val connectedDevice = connectedDevices.value.find { it.account.accountId == accountId }
        coroutineScope.launch {
            var textMessage = TextMessage(0, ownDevice.account.accountId, accountId, System.currentTimeMillis(), MessageState.MESSAGE_SENT, text)
            textMessage = textMessage.copy(messageId = chatRepository.addMessage(textMessage))

            val ipAddress = connectedDevice?.ipAddress
            if (ipAddress != null) {
                clientService.sendTextMessage(ipAddress, ownDevice, textMessage.toNetworkTextMessage())
            } else {
                outboxRepository.enqueueText(accountId, textMessage.messageId, text)
            }
        }
    }

    fun sendFileMessage(accountId: Long, fileUri: Uri) {
        val connectedDevice = connectedDevices.value.find { it.account.accountId == accountId }

        connectedDevice?.let { device ->
            device.ipAddress?.let { ipAddress ->
                coroutineScope.launch {
                    fileManager.saveMessageFile(fileUri)?.let { fileName ->
                        var fileMessage = FileMessage(0, ownDevice.account.accountId, accountId, System.currentTimeMillis(), MessageState.MESSAGE_SENT, fileName)
                        fileMessage = fileMessage.copy(messageId = chatRepository.addMessage(fileMessage))

                        fileManager.getFileBase64(fileName)?.let { fileBase64 ->
                            clientService.sendFileMessage(ipAddress, ownDevice, NetworkFileMessage(fileMessage, fileBase64))

                        }
                    }
                }
            }
        }
    }

    fun sendAudioMessage(accountId: Long, file: File) {
        val connectedDevice = connectedDevices.value.find { it.account.accountId == accountId }

        connectedDevice?.let { device ->
            device.ipAddress?.let { ipAddress ->
                coroutineScope.launch {
                    val currentTimestamp = System.currentTimeMillis()

                    fileManager.saveMessageAudio(Uri.fromFile(file), accountId, currentTimestamp)?.let { fileName ->
                        var audioMessage = AudioMessage(0, ownDevice.account.accountId, accountId, currentTimestamp, MessageState.MESSAGE_SENT, fileName)
                        audioMessage = audioMessage.copy(messageId = chatRepository.addMessage(audioMessage))

                        fileManager.getFileBase64(fileName)?.let { audioBase64 ->
                            clientService.sendAudioMessage(ipAddress, ownDevice, NetworkAudioMessage(audioMessage, audioBase64))
                        }
                    }
                }
            }
        }
    }

    fun sendMessageReceivedAck(accountId: Long, messageId: Long) {
        val connectedDevice = connectedDevices.value.find { it.account.accountId == accountId }

        connectedDevice?.let { device ->
            device.ipAddress?.let { ipAddress ->
                coroutineScope.launch {
                    val networkMessageAck = NetworkMessageAck(messageId, ownDevice.account.accountId, accountId)
                    clientService.sendMessageReceivedAck(ipAddress, ownDevice, networkMessageAck)
                }
            }
        }
    }

    fun sendMessageReadAck(accountId: Long, messageId: Long) {
        val connectedDevice = connectedDevices.value.find { it.account.accountId == accountId }

        connectedDevice?.let { device ->
            device.ipAddress?.let { ipAddress ->
                coroutineScope.launch {
                    val networkMessageAck = NetworkMessageAck(messageId, ownDevice.account.accountId, accountId)
                    clientService.sendMessageReadAck(ipAddress, ownDevice, networkMessageAck)
                }
            }
        }
    }

    fun sendCallRequest(accountId: Long) {
        val connectedDevice = connectedDevices.value.find { it.account.accountId == accountId }

        connectedDevice?.let { device ->
            device.ipAddress?.let { ipAddress ->
                coroutineScope.launch {
                    val networkCallRequest = NetworkCallRequest(ownDevice.account.accountId, accountId)
                    clientService.sendCallRequest(ipAddress, ownDevice, networkCallRequest)
                }
            }
        }
    }

    fun sendCallResponse(accountId: Long, accepted: Boolean) {
        val connectedDevice = connectedDevices.value.find { it.account.accountId == accountId }

        connectedDevice?.let { device ->
            device.ipAddress?.let { ipAddress ->
                coroutineScope.launch {
                    val networkCallResponse = NetworkCallResponse(ownDevice.account.accountId, accountId, accepted)
                    clientService.sendCallResponse(ipAddress, ownDevice, networkCallResponse)
                }
            }
        }
    }

    fun sendCallEnd(accountId: Long) {
        val connectedDevice = connectedDevices.value.find { it.account.accountId == accountId }

        connectedDevice?.let { device ->
            device.ipAddress?.let { ipAddress ->
                coroutineScope.launch {
                    val networkCallEnd = NetworkCallEnd(ownDevice.account.accountId, accountId)
                    clientService.sendCallEnd(ipAddress, ownDevice, networkCallEnd)
                }
            }
        }
    }

    fun sendCallFragment(accountId: Long, callFragment: ByteArray) {
        val connectedDevice = connectedDevices.value.find { it.account.accountId == accountId }

        connectedDevice?.let { device ->
            device.ipAddress?.let { ipAddress ->
                coroutineScope.launch {
                    clientService.sendCallFragment(ipAddress, ownDevice, callFragment)
                }
            }
        }
    }

    fun startConnections() {
        startKeepaliveConnection()
        startTextMessageConnection()
        startFileMessageConnection()
        startProfileRequestConnection()
        startProfileResponseConnection()
        startAudioMessageConnection()
        startMessageReceivedAckConnection()
        startMessageReadAckConnection()
        startCallRequestConnection()
        startCallResponseConnection()
        startCallEndConnection()
        startCallFragmentConnection()
        startSosAlertConnection()
        startGroupTextMessageConnection()
    }

    private fun startKeepaliveConnection() {
        coroutineScope.launch {
            while(true) {
                val networkKeepalive = serverService.listenKeepalive()

                networkKeepalive?.networkDevices?.forEach { networkDevice ->
                    if(networkDevice.account.accountId != ownDevice.account.accountId) {
                        handleDeviceKeepalive(networkDevice)
                    }
                }
            }
        }
    }

    private fun startProfileRequestConnection() {
        coroutineScope.launch {
            while(true) {
                val networkProfileRequest = serverService.listenProfileRequest()

                if(networkProfileRequest?.receiverId == ownDevice.account.accountId) {
                    handleProfileRequest(networkProfileRequest)
                }
            }
        }
    }

    private fun startProfileResponseConnection() {
        coroutineScope.launch {
            while(true) {
                val networkProfileResponse = serverService.listenProfileResponse()

                if(networkProfileResponse?.receiverId == ownDevice.account.accountId) {
                    handleProfileResponse(networkProfileResponse)
                }
            }
        }
    }

    private fun startTextMessageConnection() {
        coroutineScope.launch {
            while(true) {
                val networkTextMessage = serverService.listenTextMessage()

                if(networkTextMessage?.receiverId == ownDevice.account.accountId) {
                    handleTextMessage(networkTextMessage)
                }
            }
        }
    }
    
    private fun startFileMessageConnection() {
        coroutineScope.launch { 
            while(true) {
                val networkFileMessage = serverService.listenFileMessage()

                if(networkFileMessage?.receiverId == ownDevice.account.accountId) {
                    handleFileMessage(networkFileMessage)
                }
            }
        }
    }

    private fun startAudioMessageConnection() {
        coroutineScope.launch {
            while(true) {
                val networkAudioMessage = serverService.listenAudioMessage()

                if(networkAudioMessage?.receiverId == ownDevice.account.accountId) {
                    handleAudioMessage(networkAudioMessage)
                }
            }
        }
    }

    private fun startMessageReceivedAckConnection() {
        coroutineScope.launch {
            while(true) {
                val networkMessageAck = serverService.listenMessageReceivedAck()

                if(networkMessageAck?.receiverId == ownDevice.account.accountId) {
                    handleMessageReceivedAck(networkMessageAck)
                }
            }
        }
    }

    private fun startMessageReadAckConnection() {
        coroutineScope.launch {
            while(true) {
                val networkMessageAck = serverService.listenMessageReadAck()

                if(networkMessageAck?.receiverId == ownDevice.account.accountId) {
                    handleMessageReadAck(networkMessageAck)
                }
            }
        }
    }

    private fun startCallRequestConnection() {
        coroutineScope.launch {
            while(true) {
                val networkCallRequest = serverService.listenCallRequest()

                if(networkCallRequest?.receiverId == ownDevice.account.accountId) {
                    handleCallRequest(networkCallRequest)
                }
            }
        }
    }

    private fun startCallResponseConnection() {
        coroutineScope.launch {
            while(true) {
                val networkCallResponse = serverService.listenCallResponse()

                if(networkCallResponse?.receiverId == ownDevice.account.accountId) {
                    handleCallResponse(networkCallResponse)
                }
            }
        }
    }


    private fun startCallEndConnection() {
        coroutineScope.launch {
            while(true) {
                val networkCallEnd = serverService.listenCallEnd()

                if(networkCallEnd?.receiverId == ownDevice.account.accountId) {
                    handleCallEnd(networkCallEnd)
                }
            }
        }
    }


    private fun startCallFragmentConnection() {
        coroutineScope.launch {
            while(true) {
                serverService.listenCallFragment()?.let { callFragment ->
                    handleCallFragment(callFragment)
                }
            }
        }
    }

    private val groupMessagesFlows: MutableMap<String, MutableStateFlow<List<NetworkGroupTextMessage>>> = mutableMapOf()

    fun getGroupMessages(group: String): StateFlow<List<NetworkGroupTextMessage>> {
        return synchronized(groupMessagesFlows) {
            groupMessagesFlows.getOrPut(group) { MutableStateFlow(emptyList()) }
        }
    }

    private fun startGroupTextMessageConnection() {
        coroutineScope.launch {
            while(true) {
                val msg = serverService.listenGroupTextMessage()
                if (msg != null) {
                    handleGroupTextMessage(msg)
                }
            }
        }
    }

    private fun startSosAlertConnection() {
        coroutineScope.launch {
            while(true) {
                val sos = serverService.listenSosAlert()
                if (sos != null && sos.senderId != ownDevice.account.accountId) {
                    handleSosAlert(sos)
                }
            }
        }
    }

    private fun handleDeviceKeepalive(networkDevice: NetworkDevice) {
        coroutineScope.launch {
            val lastAccount = contactRepository.getAccountByAccountId(networkDevice.account.accountId)
            contactRepository.addOrUpdateAccount(networkDevice.account.toAccount())

            val profile = contactRepository.getProfileByAccountId(networkDevice.account.accountId)

            if(profile == null || (lastAccount != null && lastAccount.profileUpdateTimestamp < networkDevice.account.profileUpdateTimestamp)) {
                sendProfileRequest(networkDevice.account.accountId)
            }

            _connectedDevices.value = _connectedDevices.value.filter { it.account.accountId != networkDevice.account.accountId } + networkDevice
        }
    }

    private fun handleProfileRequest(networkProfileRequest: NetworkProfileRequest) {
        coroutineScope.launch {
            sendProfileResponse(networkProfileRequest.senderId)
        }
    }

    private fun handleProfileResponse(networkProfileResponse: NetworkProfileResponse) {
        coroutineScope.launch {
            val imageFileName = networkProfileResponse.profile.imageBase64?.let {
                fileManager.saveNetworkProfileImage(networkProfileResponse.profile)
            }

            contactRepository.addOrUpdateProfile(Profile(networkProfileResponse.profile, imageFileName))
        }
    }

    private fun handleTextMessage(networkTextMessage: NetworkTextMessage) {
        coroutineScope.launch {
            chatRepository.addMessage(TextMessage(networkTextMessage, MessageState.MESSAGE_RECEIVED))
            sendMessageReceivedAck(networkTextMessage.senderId, networkTextMessage.messageId)

            // Show a notification for the incoming message
            val profile = contactRepository.getProfileByAccountId(networkTextMessage.senderId)
            val title = profile?.username ?: "New message"
            val body = networkTextMessage.text
            notificationHelper.showMessageNotification(networkTextMessage.senderId, title, body)
        }
    }
    
    private fun handleFileMessage(networkFileMessage: NetworkFileMessage) {
        coroutineScope.launch {
            fileManager.saveNetworkFile(networkFileMessage)?.let {
                chatRepository.addMessage(FileMessage(networkFileMessage, MessageState.MESSAGE_RECEIVED))
                sendMessageReceivedAck(networkFileMessage.senderId, networkFileMessage.messageId)
            }
        }
    }

    private fun handleAudioMessage(networkAudioMessage: NetworkAudioMessage) {
        coroutineScope.launch {
            fileManager.saveNetworkAudio(networkAudioMessage)?.let { fileName ->
                chatRepository.addMessage(AudioMessage(networkAudioMessage, MessageState.MESSAGE_RECEIVED, fileName))
                sendMessageReceivedAck(networkAudioMessage.senderId, networkAudioMessage.messageId)
            }
        }
    }

    private fun handleMessageReceivedAck(networkMessageAck: NetworkMessageAck) {
        coroutineScope.launch {
            chatRepository.getMessageByMessageId(networkMessageAck.messageId)?.let { message ->
                if(message.messageState < MessageState.MESSAGE_RECEIVED) {
                    chatRepository.updateMessageState(message.messageId, MessageState.MESSAGE_RECEIVED)
                }
            }
        }
    }

    private fun handleMessageReadAck(networkMessageAck: NetworkMessageAck) {
        coroutineScope.launch {
            chatRepository.getAllMessagesByReceiverAccountId(networkMessageAck.senderId).forEach { message ->
                if(message.messageState < MessageState.MESSAGE_READ) {
                    chatRepository.updateMessageState(message.messageId, MessageState.MESSAGE_READ)
                }
            }
        }
    }

    private fun handleCallRequest(networkCallRequest: NetworkCallRequest) {
        resetCallStateFlows()
        _callRequest.value = networkCallRequest
        coroutineScope.launch {
            val profile = contactRepository.getProfileByAccountId(networkCallRequest.senderId)
            val title = profile?.username ?: "Incoming call"
            notificationHelper.showIncomingCallNotification(networkCallRequest.senderId, title)
        }
    }

    private fun handleCallResponse(networkCallResponse: NetworkCallResponse) {
        resetCallStateFlows()
        _callResponse.value = networkCallResponse
        // In case a ringing notification exists on this device, cancel it
        notificationHelper.cancelIncomingCallNotification(networkCallResponse.senderId)
    }

    private fun handleCallEnd(networkCallEnd: NetworkCallEnd) {
        resetCallStateFlows()
        _callEnd.value = networkCallEnd
        // Cancel any lingering incoming call notification
        notificationHelper.cancelIncomingCallNotification(networkCallEnd.senderId)
    }

    private fun handleCallFragment(callFragment: ByteArray) {
        _callFragment.value = callFragment
    }

    private fun handleGroupTextMessage(message: NetworkGroupTextMessage) {
        val flow = getGroupMessages(message.group) as MutableStateFlow<List<NetworkGroupTextMessage>>
        flow.value = flow.value + message

        // Ensure the group exists locally in the list
        _groups.value = _groups.value + message.group

        // Notify if message from others
        if (message.senderId != ownDevice.account.accountId) {
            coroutineScope.launch {
                val profile = contactRepository.getProfileByAccountId(message.senderId)
                val sender = profile?.username ?: "New message"
                val title = "${message.group.replaceFirstChar { it.uppercase() }} • $sender"
                notificationHelper.showGroupMessageNotification(message.group, title, message.text)
            }
        }
    }

    fun sendGroupTextMessage(group: String, text: String) {
        val timestamp = System.currentTimeMillis()
        val message = NetworkGroupTextMessage(group, timestamp, ownDevice.account.accountId, text, timestamp)

        handleGroupTextMessage(message)

        coroutineScope.launch {
            if(ownDevice.ipAddress != WiFiDirectBroadcastReceiver.IP_GROUP_OWNER) {
                clientService.sendGroupTextMessage(WiFiDirectBroadcastReceiver.IP_GROUP_OWNER, ownDevice, message)
            }
            connectedDevices.value.forEach { device ->
                device.ipAddress?.let { ip ->
                    clientService.sendGroupTextMessage(ip, ownDevice, message)
                }
            }
        }
    }

    fun sendSosAlertToAll(durationMs: Long) {
        locationProvider.getCurrentLocation { last ->
            coroutineScope.launch {
                val sos = NetworkSosAlert(
                    senderId = ownDevice.account.accountId,
                    durationMs = durationMs,
                    timestamp = System.currentTimeMillis(),
                    latitude = last?.latitude,
                    longitude = last?.longitude
                )

                if(ownDevice.ipAddress != WiFiDirectBroadcastReceiver.IP_GROUP_OWNER) {
                    clientService.sendSosAlert(WiFiDirectBroadcastReceiver.IP_GROUP_OWNER, ownDevice, sos)
                }
                connectedDevices.value.forEach { device ->
                    device.ipAddress?.let { ip ->
                        clientService.sendSosAlert(ip, ownDevice, sos)
                    }
                }
            }
        }
    }

    private fun handleSosAlert(sos: NetworkSosAlert) {
        coroutineScope.launch {
            val profile = contactRepository.getProfileByAccountId(sos.senderId)
            val title = (profile?.username ?: "SOS Alert")
            notificationHelper.showSosNotification(
                senderAccountId = sos.senderId,
                title = title,
                body = if (sos.latitude != null && sos.longitude != null) "${sos.latitude}, ${sos.longitude}" else "Emergency alert",
                durationMs = sos.durationMs,
                latitude = sos.latitude,
                longitude = sos.longitude
            )
        }
    }
}