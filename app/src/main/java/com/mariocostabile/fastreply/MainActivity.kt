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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
        enableEdgeToEdge()
        store = Store(File(filesDir, "data.json"))
        data = store.load()
        if (data.current() == null && savedInstanceState == null) openPicker()
        setContent {
            GlassTheme {
                GlassBackground {
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
        if (send(this, c.phone, c.name, text)) finish()
        else Toast.makeText(this, "WhatsApp non installato", Toast.LENGTH_SHORT).show()
    }

    @Composable
    private fun MessagesScreen() {
        val c = data.current()
        var adding by remember { mutableStateOf(false) }
        var deleting by remember { mutableStateOf<String?>(null) }
        val p = LocalGlass.current

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!serviceOn) {
                Text(
                    "Attiva invio automatico (tocca qui).\nSu Android 13+ prima: Info app → ⋮ → Consenti impostazioni con limitazioni.",
                    Modifier.fillMaxWidth()
                        .pressable({ startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
                        .glass(RoundedCornerShape(20.dp), Danger)
                        .padding(16.dp),
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
            if (c == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassButton("Scegli un contatto", { openPicker() }, Modifier.fillMaxWidth(), tint = Accent)
                }
            } else {
                Row(
                    Modifier.pressable({ screen = "contacts" }).glass(CircleShape).padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Avatar(c.name, 40)
                    Text(c.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("▾", fontSize = 16.sp, color = p.textDim)
                }
                LazyVerticalGrid(
                    GridCells.Adaptive(110.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(c.messages) { msg ->
                        Tile(msg, Modifier.pressable(onClick = { onSend(c, msg) }, onLongClick = { deleting = msg }))
                    }
                    item {
                        Tile("+", Modifier.pressable({ adding = true }), dim = true)
                    }
                }
            }
        }

        if (adding && c != null) {
            var text by remember { mutableStateOf("") }
            val focus = remember { FocusRequester() }
            GlassDialog(
                title = "Nuovo messaggio",
                onDismiss = { adding = false },
                actions = {
                    GlassButton("Annulla", { adding = false }, Modifier.weight(1f))
                    GlassButton("Salva", {
                        if (text.isNotBlank()) replace(c.copy(messages = c.messages + text.trim()))
                        adding = false
                    }, Modifier.weight(1f), tint = Accent)
                },
            ) {
                // inside the dialog's composition, so the field is attached when focus is requested
                LaunchedEffect(Unit) { focus.requestFocus() }
                BasicTextField(
                    text,
                    { text = it },
                    Modifier.fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .glass(RoundedCornerShape(18.dp))
                        .padding(14.dp)
                        .focusRequester(focus),
                    textStyle = TextStyle(color = p.text, fontSize = 17.sp),
                    cursorBrush = SolidColor(Accent),
                )
            }
        }
        deleting?.let { msg ->
            if (c != null) ConfirmDelete("\"$msg\"", onYes = { replace(c.copy(messages = c.messages - msg)) }, onClose = { deleting = null })
        }
    }

    @Composable
    private fun ContactsScreen() {
        var deleting by remember { mutableStateOf<Contact?>(null) }
        BackHandler { screen = "messages" }
        val p = LocalGlass.current
        val current = data.current()

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).pressable({ screen = "messages" }).glass(CircleShape), contentAlignment = Alignment.Center) {
                    Text("‹", fontSize = 28.sp, fontWeight = FontWeight.Medium)
                }
                Text("Contatti", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            GlassButton("Aggiungi dalla rubrica", { openPicker() }, Modifier.fillMaxWidth(), tint = Accent)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(data.contacts, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth()
                            .pressable(
                                onClick = { update(data.copy(lastContactId = c.id)); screen = "messages" },
                                onLongClick = { deleting = c },
                            )
                            .glass(RoundedCornerShape(22.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(c.name, 44)
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("+${c.phone}", fontSize = 13.sp, color = p.textDim)
                        }
                        if (c.id == current?.id) Text("✓", fontSize = 20.sp, color = Accent, fontWeight = FontWeight.Bold)
                    }
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
private fun Tile(label: String, modifier: Modifier, dim: Boolean = false) {
    Box(
        Modifier.aspectRatio(1f).then(modifier).glass(RoundedCornerShape(26.dp)).padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            textAlign = TextAlign.Center,
            fontSize = if (dim) 40.sp else 16.sp,
            fontWeight = if (dim) FontWeight.Light else FontWeight.Medium,
            color = if (dim) LocalGlass.current.textDim else LocalGlass.current.text,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConfirmDelete(what: String, onYes: () -> Unit, onClose: () -> Unit) {
    GlassDialog(
        title = "Elimina?",
        onDismiss = onClose,
        actions = {
            GlassButton("Annulla", onClose, Modifier.weight(1f))
            GlassButton("Elimina", { onYes(); onClose() }, Modifier.weight(1f), tint = Danger)
        },
    ) {
        Text(what, fontSize = 16.sp, color = LocalGlass.current.textDim)
    }
}

/** Arms SendService and opens the WhatsApp chat with [text] prefilled. False if WhatsApp is missing. */
private fun send(context: Context, phone: String, name: String, text: String): Boolean {
    Pending.value = PendingSend(phone, name, text, System.currentTimeMillis())
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
