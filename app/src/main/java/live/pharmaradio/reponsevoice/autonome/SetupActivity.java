package live.pharmaradio.reponsevoice.autonome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;

public class SetupActivity extends Activity {
    private boolean accepted;
    private boolean resumed;
    private String result = "";
    private String signature = "";
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            refresh();
            main.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) { accepted = saved.getBoolean("accepted"); result = saved.getString("result", ""); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true; signature = "";
        main.removeCallbacks(ticker);
        main.post(ticker);
    }

    @Override
    protected void onPause() { resumed = false; main.removeCallbacks(ticker); super.onPause(); }

    @Override
    protected void onSaveInstanceState(Bundle saved) {
        saved.putBoolean("accepted", accepted); saved.putString("result", result);
        super.onSaveInstanceState(saved);
    }

    private boolean setting(String name) {
        try { return Settings.Global.getInt(getContentResolver(), name, 0) != 0; }
        catch (SecurityException error) { return false; }
    }

    private LocalActivationPolicy.Stage stage() {
        return LocalActivationPolicy.next(KeyVault.configured(this), LocalActivationService.paired(this),
                LocalActivationService.activatedLocally(this), BridgeProvider.getHelper() != null, Readiness.fresh(),
                setting(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED), setting("adb_wifi_enabled"));
    }

    private void refresh() {
        if (!resumed || isDestroyed() || isFinishing()) return;
        LocalActivationPolicy.Stage current = stage();
        String changed = current.name() + accepted + LocalActivationService.running() + Readiness.checking()
                + LocalActivationService.status() + result;
        if (!changed.equals(signature)) { signature = changed; render(current); }
    }

    private void render(LocalActivationPolicy.Stage current) {
        LinearLayout root = Ui.root(this);
        Ui.header(root, "Configurer mon téléphone", "Guide de première configuration");
        LinearLayout content = Ui.content(root);
        if (!accepted) {
            LinearLayout welcome = Ui.card(content, "Bienvenue dans Appel AI",
                    "Ce guide vous accompagne pour préparer votre assistant d’appels. Aucune connaissance technique n’est nécessaire pour suivre les explications.");
            Ui.text(welcome, "1 · Votre compte OpenAI\n2 · Activer le mode développeur Android\n3 · Activer le débogage sans fil et associer le téléphone\n4 · Activer et vérifier l’assistant", 18);
            Ui.button(welcome, "Commencer la configuration", true, () -> { accepted = true; result = ""; signature = ""; refresh(); });
            Ui.text(welcome, "Vous confirmez chaque étape. Aucun appel n’est passé par ce guide. Android impose quelques réglages et une association initiale avec un code à saisir uniquement sur ce téléphone.", 15);
        } else if (LocalActivationService.running()) {
            LinearLayout running = Ui.card(content, "Configuration en cours", LocalActivationService.status());
            Ui.text(running, "Pour l’association : ouvrez Débogage sans fil → Associer l’appareil avec un code. Déroulez complètement les notifications, développez la notification Appel AI avec sa flèche, puis touchez « Entrer le code ». Le champ est dans cette notification, pas dans Android. Si l’étape expire, revenez ici et relancez-la.", 16);
            Ui.button(running, "Ouvrir les options développeur", false, () -> openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            Ui.button(running, "Annuler cette étape", false, () -> startService(new Intent(this, LocalActivationService.class).setAction("cancel")));
        } else {
            Ui.text(content, "Compte OpenAI : " + (KeyVault.configured(this) ? "configuré ✓" : "à configurer")
                    + "\nAssociation locale : " + (LocalActivationService.paired(this) ? "enregistrée ✓" : "à faire"), 16);
            LinearLayout action = Ui.card(content, title(current), description(current));
            showAction(action, current);
            if (!result.isEmpty()) Ui.text(action, SetupFlow.diagnosticMessage(result), 16);
            if (!LocalActivationService.status().isEmpty() && current != LocalActivationPolicy.Stage.READY) Ui.text(action, LocalActivationService.status(), 15);
        }
        Ui.button(content, "Aide · activer le mode développeur", false, this::showDeveloperGuide);
        Ui.button(content, "Plus tard · revenir à l’application", false, this::finish);
    }

    private String title(LocalActivationPolicy.Stage current) {
        switch (current) {
            case KEY: return "Votre compte OpenAI";
            case DEVELOPER: return "Activer le mode développeur Android";
            case WIRELESS: return "Activer le débogage sans fil";
            case PAIR: return "Associer ce téléphone";
            case ACTIVATE: return "Activer votre assistant";
            case VERIFY: return Readiness.checking() ? "Vérification en cours…" : "Vérifier la connexion";
            default: return "Votre téléphone est prêt ✓";
        }
    }

    private String description(LocalActivationPolicy.Stage current) {
        switch (current) {
            case KEY: return "Enregistrez votre clé API personnelle. Elle restera chiffrée sur votre téléphone. Vous reviendrez automatiquement dans ce guide.";
            case DEVELOPER: return getString(R.string.developer_setup_guide);
            case WIRELESS: return "Ouvrez les options développeur avec le bouton ci-dessous. Sur votre téléphone, cherchez « Options pour les développeurs » dans les Paramètres, parfois dans « Système ». Activez « Débogage sans fil » et validez les avertissements Android si vous acceptez. Connectez-vous à un réseau Wi-Fi si Android le demande, puis revenez dans Appel AI. Ne modifiez pas les autres options.";
            case PAIR: return "L’association autorise Appel AI à démarrer son propre service audio. Le code Android reste sur ce téléphone ; ne le transmettez jamais à un assistant ou à un contact.";
            case ACTIVATE: return "L’activation utilise l’autorisation locale que vous venez d’accorder. Elle ne modifie pas votre application Téléphone et ne passe aucun appel.";
            case VERIFY: return "Vérifiez le service audio et OpenAI sans appel ni microphone. Le son avec un correspondant restera à tester ensuite.";
            default: return "Le service audio et OpenAI sont accessibles. Aucun appel n’a été passé. Vos réglages d’appels entrants sont conservés ; le décrochage automatique reste désactivé.";
        }
    }

    private void showAction(LinearLayout action, LocalActivationPolicy.Stage current) {
        switch (current) {
            case KEY:
                Ui.button(action, "Enregistrer ma clé API", true, () -> startActivity(new Intent(this, CredentialsActivity.class).putExtra("return-to-setup", true)));
                Ui.button(action, "Je n’ai pas encore de clé", false, () -> SetupLinks.open(this, SetupLinks.OPENAI_KEYS));
                break;
            case DEVELOPER:
                Ui.button(action, "Ouvrir les informations du téléphone", true, () -> openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS));
                Ui.button(action, "Options déjà activées ? Continuer", false, () -> openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                break;
            case WIRELESS:
                Ui.button(action, "Ouvrir les options développeur", true, () -> openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                Ui.button(action, "Débogage déjà activé ? Associer", false, () -> confirmLocal(true));
                break;
            case PAIR:
                Ui.button(action, "Associer ce téléphone", true, () -> confirmLocal(true));
                break;
            case ACTIVATE:
                Ui.button(action, "Activer l’assistant", true, () -> confirmLocal(false));
                Ui.button(action, "Refaire l’association si nécessaire", false, () -> confirmLocal(true));
                break;
            case VERIFY:
                Ui.button(action, Readiness.checking() ? "Vérification en cours…" : "Vérifier ma connexion", true, this::confirmDiagnostic).setEnabled(!Readiness.checking());
                if ((result + Readiness.detail()).contains("credit_balance_exhausted") || (result + Readiness.detail()).contains("insufficient_quota")) {
                    Ui.button(action, "Ouvrir la facturation OpenAI", false, () -> SetupLinks.open(this, SetupLinks.OPENAI_BILLING));
                }
                Ui.button(action, "Modifier ma clé API", false, () -> startActivity(new Intent(this, CredentialsActivity.class).putExtra("return-to-setup", true)));
                break;
            case READY:
                Ui.button(action, "Terminer", true, () -> {
                    if (!SetupPolicy.canFinish(KeyVault.configured(this), BridgeProvider.getHelper() != null, Readiness.fresh())) { refresh(); return; }
                    AppSettings.prefs(this).edit().putInt("setup-version", SetupPolicy.VERSION).apply();
                    finish();
                });
                break;
        }
    }

    private boolean phoneBusy() {
        return getSystemService(android.media.AudioManager.class).getMode() != android.media.AudioManager.MODE_NORMAL;
    }

    private void confirmLocal(boolean pairing) {
        if (phoneBusy()) { result = "Terminez toute communication audio avant de continuer."; signature = ""; refresh(); return; }
        if (!getSystemService(android.app.NotificationManager.class).areNotificationsEnabled()) {
            new AlertDialog.Builder(this).setTitle("Autoriser les notifications")
                    .setMessage("Une notification permet de saisir le code Android sans quitter l’écran d’association. Elle ne contient aucun code enregistré.")
                    .setNegativeButton("Annuler", null).setPositiveButton("Autoriser", (dialog, which) -> {
                        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 6505);
                        } else {
                            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                        }
                    }).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle(pairing ? "Autoriser l’association locale ?" : "Activer l’assistant ?")
                .setMessage(pairing
                        ? "Appel AI va rechercher uniquement le service de ce téléphone. Dans Android, ouvrez Débogage sans fil → Associer avec un code, puis saisissez les 6 chiffres dans la notification Appel AI. Aucun code n’est envoyé à OpenAI. L’association donne un accès privilégié au service local ; acceptez uniquement pour cette application de confiance."
                        : "L’application va démarrer son service audio et activer ses commandes d’appel depuis ce téléphone. Aucun appel ni microphone ne sera ouvert. Aucun autre assistant ne sera supprimé. Vous gardez le choix de répondre vous-même.")
                .setNegativeButton("Annuler", null).setPositiveButton(pairing ? "Associer" : "Activer", (dialog, which) -> {
                    try {
                        result = "";
                        LocalActivationService.start(this, pairing);
                        if (pairing) openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                        signature = "";
                        refresh();
                    } catch (RuntimeException error) { result = "Démarrage indisponible. Gardez l’application ouverte, vérifiez les notifications et réessayez."; signature = ""; refresh(); }
                }).show();
    }

    private void confirmDiagnostic() {
        if (Readiness.checking()) return;
        new AlertDialog.Builder(this).setTitle("Vérifier OpenAI ?")
                .setMessage("Le diagnostic utilise votre clé API et peut être facturé. Aucun appel, microphone ni document de mission n’est ouvert ou transmis.")
                .setNegativeButton("Annuler", null).setPositiveButton("Vérifier", (dialog, which) -> {
                    if (phoneBusy()) { result = "Terminez toute communication audio avant de vérifier."; signature = ""; refresh(); return; }
                    Readiness.begin(); result = ""; signature = ""; refresh();
                    PhoneAgent.diagnose(message -> {
                        Readiness.result(message);
                        if (isDestroyed() || isFinishing()) return;
                        result = message; signature = ""; refresh();
                    });
                }).show();
    }

    private void showDeveloperGuide() {
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.TextView instructions = new android.widget.TextView(this);
        instructions.setText(R.string.developer_setup_guide);
        instructions.setTextSize(17);
        instructions.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
        scroll.addView(instructions);
        new AlertDialog.Builder(this).setTitle("Activer le mode développeur")
                .setView(scroll).setNegativeButton("Fermer", null)
                .setPositiveButton("Ouvrir les paramètres", (dialog, which) -> openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS)).show();
    }

    private void openSettings(String action) {
        try { startActivity(new Intent(action)); }
        catch (android.content.ActivityNotFoundException error) {
            new AlertDialog.Builder(this).setMessage("Ouvrez les paramètres Android manuellement. Les noms de menus varient selon la marque.")
                    .setPositiveButton("Compris", null).show();
        }
    }

    @Override protected void onDestroy() { main.removeCallbacksAndMessages(null); super.onDestroy(); }
}
