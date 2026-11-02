package com.mariocostabile.fastreply

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var store: Store
    private var data by mutableStateOf(AppData())
    private var screen by mutableStateOf("messages")
    private var serviceOn by mutableStateOf(true)

    private val pickContact = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { addContact(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(File(filesDir, "data.json"))
        data = store.load()
        if (data.current() == null && savedInstanceState == null) openPicker()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.safeDrawingPadding()) {
                        if (screen == "contacts") ContactsScreen() else MessagesScreen()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceOn = isServiceEnabled(this)
    }

    private fun update(new: AppData) {
        data = new
        store.save(new)
    }

    private fun openPicker() = pickContact.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI))

    private fun addContact(uri: Uri) {
        val (name, number) = contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)
            ?.use { if (it.moveToFirst()) (it.getString(0) ?: "") to (it.getString(1) ?: "") else null }
            ?: return
        val phone = normalizePhone(number)
        if (phone == null) {
            Toast.makeText(this, "Numero non valido", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = data.contacts.firstOrNull { it.phone == phone }
        if (existing != null) {
            update(data.copy(lastContactId = existing.id))
        } else {
            val c = Contact(System.currentTimeMillis().toString(), name.ifBlank { "+$phone" }, phone, emptyList())
            update(data.copy(lastContactId = c.id, contacts = data.contacts + c))
        }
        screen = "messages"
    }

    private fun replace(c: Contact) = update(data.copy(contacts = data.contacts.map { if (it.id == c.id) c else it }))

    private fun onSend(c: Contact, text: String) {
        update(data.copy(lastContactId = c.id))
        if (send(this, c.phone, text)) finish()
        else Toast.makeText(this, "WhatsApp non installato", Toast.LENGTH_SHORT).show()
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun MessagesScreen() {
        val c = data.current()
        var adding by remember { mutableStateOf(false) }
        var deleting by remember { mutableStateOf<String?>(null) }

        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!serviceOn) {
                Text(
                    "Attiva invio automatico (tocca qui).\nSu Android 13+ prima: Info app → ⋮ → Consenti impostazioni con limitazioni.",
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                        .clickable { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                        .padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (c == null) {
                Button(onClick = { openPicker() }, Modifier.fillMaxWidth()) { Text("Scegli un contatto") }
            } else {
            Text(
                "${c.name} ▾",
                Modifier.fillMaxWidth().clickable { screen = "contacts" }.padding(vertical = 8.dp),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            LazyVerticalGrid(
                GridCells.Adaptive(110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(c.messages) { msg ->
                    Tile(msg, MaterialTheme.colorScheme.primaryContainer, Modifier.combinedClickable(
                        onClick = { onSend(c, msg) },
                        onLongClick = { deleting = msg },
                    ))
                }
                item {
                    Tile("+", MaterialTheme.colorScheme.surfaceVariant, Modifier.clickable { adding = true })
                }
            }
            }
        }

        if (adding && c != null) {
            var text by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { adding = false },
                title = { Text("Nuovo messaggio") },
                text = { OutlinedTextField(text, { text = it }, singleLine = false) },
                confirmButton = {
                    TextButton(onClick = {
                        if (text.isNotBlank()) replace(c.copy(messages = c.messages + text.trim()))
                        adding = false
                    }) { Text("Salva") }
                },
                dismissButton = { TextButton(onClick = { adding = false }) { Text("Annulla") } },
            )
        }
        deleting?.let { msg ->
            if (c != null) ConfirmDelete("\"$msg\"", onYes = { replace(c.copy(messages = c.messages - msg)) }, onClose = { deleting = null })
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun ContactsScreen() {
        var deleting by remember { mutableStateOf<Contact?>(null) }
        BackHandler { screen = "messages" }

        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Contatti", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { openPicker() }, Modifier.fillMaxWidth()) { Text("Aggiungi dalla rubrica") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.contacts, key = { it.id }) { c ->
                    Text(
                        c.name,
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { update(data.copy(lastContactId = c.id)); screen = "messages" },
                                onLongClick = { deleting = c },
                            )
                            .padding(16.dp),
                        fontSize = 18.sp,
                    )
                }
            }
        }

        deleting?.let { c ->
            ConfirmDelete(c.name, onYes = {
                update(data.copy(contacts = data.contacts - c, lastContactId = data.lastContactId.takeIf { it != c.id }))
            }, onClose = { deleting = null })
        }
    }
}

@Composable
private fun Tile(label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Box(
        Modifier.aspectRatio(1f).background(color, RoundedCornerShape(16.dp)).then(modifier).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, textAlign = TextAlign.Center, fontSize = if (label == "+") 36.sp else 16.sp)
    }
}

@Composable
private fun ConfirmDelete(what: String, onYes: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Elimina?") },
        text = { Text(what) },
        confirmButton = { TextButton(onClick = { onYes(); onClose() }) { Text("Elimina") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Annulla") } },
    )
}

/** Arms SendService and opens the WhatsApp chat with [text] prefilled. False if WhatsApp is missing. */
private fun send(context: Context, phone: String, text: String): Boolean {
    Pending.value = PendingSend(phone, text, System.currentTimeMillis())
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(buildWaUri(phone, text)))
        .setPackage("com.whatsapp")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Pending.value = null
        false
    }
}
