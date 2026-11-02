# FastReply — Design

Data: 2026-11-02
Stato: bozza da approvare

## Intento

Mandare messaggi di servizio ricorrenti ("tutto ok", "sono a casa", "non posso
rispondere") a poche persone fisse in circa un secondo, senza entrare in
WhatsApp e perderci tempo. Uso personale: l'app viene installata a mano (APK),
non va sul Play Store.

## Obiettivi

- Aprendo l'app si arriva direttamente sui messaggi dell'ultimo contatto usato.
- Un tap su un bottone → il messaggio parte su WhatsApp → si esce da WhatsApp da soli.
- Ogni contatto ha i propri messaggi.
- Aggiunta e cancellazione dei messaggi direttamente dalla schermata del contatto.
- I contatti si scelgono dalla rubrica del telefono.

## Non obiettivi

- Pubblicazione sul Play Store.
- Canali diversi da WhatsApp (SMS, Telegram) e WhatsApp Business.
- Notifica di esito: la conferma è l'uscita da WhatsApp.
- Conferma prima dell'invio.
- Modifica e riordino dei messaggi (si cancella e si ricrea).
- Backup, sincronizzazione, account, cloud.
- Avvio dal tasto di accensione: si apre dall'icona sulla home.
- Funzionamento a telefono bloccato (Android non lo consente al servizio di accessibilità).

## Anti-obiettivi (fallimento anche se "funziona")

- Il servizio di accessibilità preme "invia" su chat che l'utente sta usando
  normalmente su WhatsApp. Deve agire **solo** subito dopo un invio partito da FastReply.
- Un messaggio fallisce in silenzio. Se l'invio automatico non riesce, l'utente
  resta nella chat col testo già scritto e preme invio da solo.
- Un tap normale cancella qualcosa per sbaglio.

## Vincoli

- WhatsApp non ha API per inviare messaggi in silenzio da un account personale.
  L'unica strada è aprire la chat col testo già scritto e far premere invio a
  un servizio di accessibilità.
- Il servizio dipende dall'interfaccia di WhatsApp: si rompe se WhatsApp cambia
  l'id o l'etichetta del tasto invio. Bisogna accettarlo e mantenerlo.
- Le condizioni d'uso di WhatsApp vietano l'automazione. Per pochi messaggi
  personali il rischio è basso, ma esiste.

## Approcci considerati

Canale di invio:
- **A. Testo già scritto, invio manuale.** Solido, ma si resta dentro WhatsApp. Scartato come modalità principale, tenuto come ripiego.
- **B. Testo già scritto + invio automatico via accessibilità.** ← **scelto dall'utente.** L'unico vero "un tap e fatto".
- **C. Risposta dalla notifica.** Funziona solo se c'è una notifica in attesa. Scartato.
- **WhatsApp Business API.** Richiede un numero aziendale. Scartato.

Struttura dell'app:
- **1. Nativa minima: Kotlin + Compose, un file JSON, una sola Activity.** ← **scelto dall'utente.** Pochissimo codice, nessuna dipendenza in più.
- **2. Room + ViewModel + repository.** Il triplo del codice per una decina di righe di dati. Scartato.
- **Flutter / React Native.** Il servizio va comunque scritto in Kotlin: sarebbe un secondo linguaggio senza alcun guadagno. Scartato.

## Cosa ha deciso l'utente

- Canale: WhatsApp, invio automatico (B) con ripiego manuale (A).
- Apertura sull'ultimo contatto a cui si è scritto *tramite l'app*.
- Cambio contatto: tap sul nome in alto → elenco dei contatti salvati.
- Contatti presi dalla rubrica di Android.
- Messaggi diversi per ogni contatto.
- Schermata del contatto: griglia di bottoni quadrati + un bottone vuoto "+" per aggiungere.
- I messaggi si possono cancellare.
- Nessuna notifica e nessuna conferma prima dell'invio.
- Struttura 1 (nativa minima).

## Comportamento

### Schermata Messaggi (iniziale)
- In alto: il nome del contatto corrente. Il tap apre la schermata Contatti.
- Griglia di bottoni quadrati, un messaggio ciascuno, più "+" in fondo.
- Tap su un messaggio → invio (vedi Flusso di invio).
- Pressione lunga su un messaggio → finestra "Elimina?" → sì/no. *(scelta di Claude)*
- "+" → finestra con un campo di testo → Salva.
- Primo avvio, nessun contatto salvato: si apre direttamente il selettore della rubrica.
- Servizio di accessibilità non attivo: banner in alto "Attiva invio automatico"
  che apre `Settings.ACTION_ACCESSIBILITY_SETTINGS`. L'invio funziona lo stesso
  (in modalità manuale A). Il banner spiega anche che su Android 13+ un'APK
  installata a mano va prima sbloccata da Info app → ⋮ → "Consenti impostazioni
  con limitazioni". *(scelta di Claude)*

### Schermata Contatti
- Elenco dei contatti salvati. Il tap rende corrente il contatto e torna a Messaggi.
- "Aggiungi" → selettore di sistema `Intent.ACTION_PICK` su
  `ContactsContract.CommonDataKinds.Phone.CONTENT_URI`. Si leggono `DISPLAY_NAME`
  e `NUMBER`. Non serve il permesso `READ_CONTACTS`.
- Pressione lunga su un contatto → "Elimina?". *(scelta di Claude: senza, i contatti vecchi si accumulano)*

### Flusso di invio
1. L'app salva `lastContactId` e "arma" il servizio: invio in attesa = `{numero, testo, timestamp}`.
2. Apre `https://wa.me/<cifre>?text=<testo codificato>` con `setPackage("com.whatsapp")`.
   WhatsApp non installato → toast di errore, nient'altro.
3. Il servizio (filtrato su `com.whatsapp`) riceve gli eventi della finestra.
   Se l'invio è armato e sono passati meno di **5 s**: cerca il tasto invio
   (id `com.whatsapp:id/send`, in alternativa la content description "Invia"/"Send").
   Preme solo se il campo di testo della chat (`com.whatsapp:id/entry`) contiene
   esattamente il testo armato: così non invia mai su una chat diversa. Poi
   esegue `ACTION_CLICK` e disarma. *(scelta di Claude, dalla revisione)*
4. Dopo il click: `performGlobalAction(GLOBAL_ACTION_HOME)`, quindi si torna alla
   home, da dove l'utente ha aperto l'app. FastReply chiude la sua Activity prima
   di aprire WhatsApp. *(scelta di Claude: "back" porterebbe all'elenco chat di WhatsApp)*
5. Tasto non trovato entro 5 s → si disarma e non si fa nulla: l'utente si ritrova nella chat col testo pronto.

Il "riarmo" passa da un oggetto in memoria condiviso tra Activity e servizio
(stesso processo). *(scelta di Claude)*

### Normalizzazione del numero
- Si tengono solo le cifre e un `+` iniziale.
- `+39...` → `39...`; `0039...` → `39...`; nessun prefisso → si antepone `39`.
- *(shortcut: prefisso di default fisso a 39; da rendere configurabile se servono contatti esteri senza prefisso)*

## Dati

Un file `filesDir/data.json`, letto e scritto con `org.json` (incluso in Android, nessuna dipendenza):

```json
{
  "lastContactId": "1",
  "contacts": [
    { "id": "1", "name": "Mamma", "phone": "393331234567",
      "messages": ["Tutto ok", "Sono a casa"] }
  ]
}
```

Lo si riscrive per intero a ogni modifica: con una manciata di contatti va benissimo.

## Tecnico

- Kotlin, Jetpack Compose, una Activity, nessuna libreria di navigazione: le due schermate si alternano con una variabile di stato.
- `minSdk 26`. *(scelta di Claude)*
- `AccessibilityService` con `packageNames="com.whatsapp"`, `canRetrieveWindowContent=true`,
  eventi `typeWindowStateChanged|typeWindowContentChanged`.

## Test

- Unit test per la normalizzazione del numero.
- Unit test per salvataggio e rilettura del JSON.
- Prova manuale su un telefono vero: invio, ripiego col servizio spento, WhatsApp aperto a mano (il servizio non deve premere invio).

## Lasciato a chi costruisce

- Layout e stile dei bottoni (dimensione della griglia, colori).
- Icona e nome mostrato sulla home.
- Testi dei dialoghi.
- Setup del progetto Gradle.

## Rischi aperti

- Id ed etichetta del tasto invio di WhatsApp vanno verificati sulla versione installata.
- Comportamento di `wa.me` con WhatsApp Business installato in parallelo: non verificato.
