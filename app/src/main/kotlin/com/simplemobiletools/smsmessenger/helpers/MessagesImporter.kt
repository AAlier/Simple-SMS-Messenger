package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.util.JsonToken
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_FAIL
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_NOTHING_NEW
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_OK
import com.simplemobiletools.smsmessenger.helpers.MessagesImporter.ImportResult.IMPORT_PARTIAL
import com.simplemobiletools.smsmessenger.models.MmsBackup
import com.simplemobiletools.smsmessenger.models.SmsBackup
import java.io.File

class MessagesImporter(private val context: Context) {
    enum class ImportResult {
        IMPORT_FAIL, IMPORT_OK, IMPORT_PARTIAL, IMPORT_NOTHING_NEW
    }

    private val gson = Gson()
    private val messageWriter = MessagesWriter(context)
    private val config = context.config
    private var messagesImported = 0
    private var messagesFailed = 0

    fun importMessages(path: String, onProgress: (total: Int, current: Int) -> Unit = { _, _ -> }, callback: (result: ImportResult) -> Unit) {
        ensureBackgroundThread {
            try {
                val inputStream = if (path.contains("/")) {
                    File(path).inputStream()
                } else {
                    context.assets.open(path)
                }

                inputStream.bufferedReader().use { reader ->
                    val jsonReader = gson.newJsonReader(reader)
                    val smsMessageType = object : TypeToken<SmsBackup>() {}.type
                    val mmsMessageType = object : TypeToken<MmsBackup>() {}.type

                    jsonReader.beginArray()
                    while (jsonReader.hasNext()) {
                        jsonReader.beginObject()
                        while (jsonReader.hasNext()) {
                            val nextToken = jsonReader.peek()
                            if (nextToken.ordinal == JsonToken.NAME.ordinal) {
                                val msgType = jsonReader.nextName()

                                if ((!msgType.equals("sms") && !msgType.equals("mms")) ||
                                    (msgType.equals("sms") && !config.importSms) ||
                                    (msgType.equals("mms") && !config.importMms)
                                ) {
                                    jsonReader.skipValue()
                                    continue
                                }

                                jsonReader.beginArray()
                                while (jsonReader.hasNext()) {
                                    try {
                                        if (msgType.equals("sms")) {
                                            val message = gson.fromJson<SmsBackup>(jsonReader, smsMessageType)
                                            messageWriter.writeSmsMessage(message)
                                        } else {
                                            val message = gson.fromJson<MmsBackup>(jsonReader, mmsMessageType)
                                            messageWriter.writeMmsMessage(message)
                                        }

                                        messagesImported++
                                    } catch (e: Exception) {
                                        context.showErrorToast(e)
                                        messagesFailed++

                                    }
                                }
                                jsonReader.endArray()
                            } else {
                                jsonReader.skipValue()
                            }
                        }

                        jsonReader.endObject()
                        refreshMessages()
                    }

                    jsonReader.endArray()
                }
            } catch (e: Exception) {
                context.showErrorToast(e)
                messagesFailed++
            }

            callback.invoke(
                when {
                    messagesImported == 0 && messagesFailed == 0 -> IMPORT_NOTHING_NEW
                    messagesFailed > 0 && messagesImported > 0 -> IMPORT_PARTIAL
                    messagesFailed > 0 -> IMPORT_FAIL
                    else -> IMPORT_OK
                }
            )
        }
    }
}
