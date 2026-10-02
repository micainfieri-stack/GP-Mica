# GP-Mica – Gestione Presenze Fabbrica

App Android (Kotlin + Jetpack Compose) 100% offline: timbratura con badge QR, ore, ritardi, straordinari, report PDF, backup .db.

## Generare l'APK con GitHub
1. Crea un repository su GitHub e carica **tutto il contenuto** di questa cartella (compresa `.github/`).
2. Vai su **Actions → Build APK** (parte da solo a ogni push su `main`/`master`; oppure *Run workflow*).
3. A build finita scarica l'artifact **GP-Mica-APK** (contiene `app-debug.apk`) e installalo sul tablet
   (abilita "Installa app da origini sconosciute").

## Primo avvio
PIN admin predefinito `000000` (icona lucchetto in alto a destra nella schermata di timbratura).
Crea i dipendenti, stampa i badge (PDF/JPEG) e imposta orari in *Impostazioni*.
