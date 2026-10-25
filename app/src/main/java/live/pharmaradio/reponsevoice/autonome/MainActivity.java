package live.pharmaradio.reponsevoice.autonome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private String tab = "Accueil";
    private boolean renderedFresh;
    private boolean renderedHelper;
    private boolean renderedActivation;
    private final android.os.Handler ticker = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshStatus = new Runnable() {
        @Override
        public void run() {
            if (tab.equals("Accueil") && (renderedFresh != Readiness.fresh()
                    || renderedHelper != (BridgeProvider.getHelper() != null)
                    || renderedActivation != LocalActivationService.running())) render();
            ticker.postDelayed(this, 3000);
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if ("Réglages".equals(getIntent().getStringExtra("tab"))) tab = "Réglages";
        if (state == null && AppSettings.prefs(this).getInt("setup-version", 0) < SetupPolicy.VERSION) {
            startActivity(new Intent(this, SetupActivity.class));
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 2);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activateLocalRelay();
        render();
        ticker.postDelayed(refreshStatus, 3000);
    }

    @Override
    protected void onPause() { ticker.removeCallbacks(refreshStatus); super.onPause(); }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 2) activateLocalRelay();
    }

    private void activateLocalRelay() {
        if (!KeyVault.configured(this) || !LocalActivationService.paired(this)) return;
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        if (getSystemService(android.media.AudioManager.class).getMode() != android.media.AudioManager.MODE_NORMAL) return;
        if (BridgeProvider.getHelper() != null || LocalActivationService.running()) return;
        LocalActivationService.start(this, false);
    }

    private void render() {
        renderedFresh = Readiness.fresh();
        renderedHelper = BridgeProvider.getHelper() != null;
        renderedActivation = LocalActivationService.running();
        LinearLayout root = Ui.root(this);
        brandHeader(root);
        LinearLayout content = tab.equals("Accueil") ? homeSurface(root) : Ui.content(root);
        if (tab.equals("Accueil")) home(content);
        else if (tab.equals("Appeler")) calls(content);
        else if (tab.equals("Historique")) history(content);
        else settings(content);
        Ui.navigation(root, tab, selected -> { tab = selected; render(); });
    }

    private void brandHeader(LinearLayout root) {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 24), Ui.dp(this, 19), Ui.dp(this, 24), Ui.dp(this, 8));
        root.addView(top);
        TextView brand = new TextView(this);
        brand.setText(getString(R.string.app_name));
        brand.setTextSize(22);
        brand.setTextColor(Ui.INK);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brand.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(brand, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
        TextView menu = new TextView(this);
        menu.setText("···");
        menu.setTextSize(26);
        menu.setTextColor(Ui.INK);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(Ui.rounded(Color.WHITE, 100, this));
        menu.setContentDescription("Ouvrir les réglages");
        menu.setOnClickListener(view -> { tab = "Réglages"; render(); });
        top.addView(menu, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
    }

    private LinearLayout homeSurface(LinearLayout root) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 24), Ui.dp(this, 30), Ui.dp(this, 24), Ui.dp(this, 28));
        scroll.addView(page);
        return page;
    }

    private void home(LinearLayout content) {
        TextView greeting = Ui.text(content, "Bonjour.", 48);
        greeting.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        greeting.setLineSpacing(0, 0.93f);
        TextView invitation = Ui.text(content, "Votre assistant peut appeler un contact et accomplir votre mission par téléphone.", 19);
        invitation.setTextColor(Ui.MUTED);
        LinearLayout facts = new LinearLayout(this);
        LinearLayout.LayoutParams factsSpace = new LinearLayout.LayoutParams(-1, -2);
        factsSpace.topMargin = Ui.dp(this, 27);
        content.addView(facts, factsSpace);
        fact(facts, "⌕", "Contact", "À choisir");
        fact(facts, "✎", "Mission", "À décrire");
        fact(facts, "✦", "Assistant", Readiness.fresh() ? "Prêt" : renderedHelper ? "Relais actif"
                : renderedActivation ? "Connexion…" : "À vérifier");
        LinearLayout actions = new LinearLayout(this);
        LinearLayout.LayoutParams actionsSpace = new LinearLayout.LayoutParams(-1, Ui.dp(this, 236));
        actionsSpace.topMargin = Ui.dp(this, 34);
        content.addView(actions, actionsSpace);
        tile(actions, 1.55f, Ui.rounded(Ui.LIME, 28, this), "↗", "Préparer\nun appel IA", () -> open(OutgoingCallActivity.class), "Préparer un appel téléphonique avec IA");
        tile(actions, 1f, Ui.rounded(Ui.YELLOW, 28, this), "▤", "Historique", () -> open(HistoryActivity.class), "Voir les appels et résultats");
        tile(actions, 1f, Ui.stripes(this), "?", "Guide", () -> open(SetupActivity.class), "Voir les étapes d’installation");
        Ui.text(content, "Vous choisissez le contact et confirmez avant l’appel.", 15).setTextColor(Ui.MUTED);

        LinearLayout connection = Ui.card(content, Readiness.checking() ? "Vérification en cours…" : Readiness.fresh() ? "Services accessibles"
                        : renderedHelper ? "Relais local actif" : renderedActivation ? "Connexion en cours…" : "Connexion à vérifier",
                renderedHelper ? "Le service audio est actif. L’accès OpenAI sera vérifié lors du prochain appel."
                        : "Hors appel, l’assistant relance automatiquement son service local à l’ouverture.");
        Ui.button(connection, BridgeProvider.getHelper() == null ? "Réactiver mon téléphone" : "Configurer mon téléphone", false,
                () -> startActivity(new Intent(this, SetupActivity.class).putExtra("step", BridgeProvider.getHelper() == null ? 2 : 0)));
        Ui.text(connection, Readiness.fresh() ? "Vérification récente · aucun appel composé" : "Vérifiez avant votre prochain appel IA.", 16)
                .setTextColor(Readiness.fresh() ? Ui.PRIMARY : Ui.AMBER);
        Ui.button(connection, "Vérifier la connexion", true, () -> diagnose(connection));
        Ui.button(connection, KeyVault.configured(this) ? "Clé OpenAI configurée · gérer" : "Configurer OpenAI direct", false, () -> open(CredentialsActivity.class));
        Ui.text(connection, AppSettings.prefs(this).getBoolean("incoming-enabled", false) ? "Commandes des appels entrants activées."
                : "Vous pouvez activer les commandes des appels entrants dans les réglages.", 15);
        CallBridgeService service = CallBridgeService.instance;
        if (service != null && service.incoming != null && service.incoming.getState() != android.telecom.Call.STATE_DISCONNECTED) {
            LinearLayout active = Ui.card(content, "Un appel est en cours", service.status);
            Ui.button(active, "Ouvrir les commandes de l’appel", true, () -> open(CallMenuActivity.class));
        }
        LinearLayout more = Ui.card(content, "Votre assistant", "Personnalisez sa voix et retrouvez les messages confirmés.");
        Ui.button(more, "Mon profil et le style de l’IA", false, () -> open(OwnerProfileActivity.class));
        Ui.button(more, "Messages par contact", false, () -> open(MessagesActivity.class));
        Ui.text(content, getString(R.string.app_version, BuildConfig.VERSION_NAME), 14);
    }

    private void calls(LinearLayout content) {
        LinearLayout mission = Ui.card(content, "Une mission claire pour l’IA", "Objectif, questions, limites et conditions de reprise : vous décidez avant de composer.");
        Ui.button(mission, "Préparer un appel", true, () -> open(OutgoingCallActivity.class));
        Ui.button(mission, "Mes missions favorites", false, () -> open(MissionLibraryActivity.class));
        Ui.button(mission, "Préférences par correspondant", false, () -> open(ProfilesActivity.class));
        LinearLayout ongoing = Ui.card(content, "Pendant une conversation", "Reprenez la main, envoyez une consigne écrite privée ou raccrochez depuis les commandes de l’appel.");
        Ui.button(ongoing, "Commandes et dernier état", false, () -> open(CallMenuActivity.class));
    }

    private void history(LinearLayout content) {
        LinearLayout records = Ui.card(content, "Retrouver l’essentiel", "Les fiches distinguent fin normale, reprise et interruption. Les messages nécessitent l’accord du correspondant.");
        Ui.button(records, "Fiches d’appel et coûts estimés", true, () -> open(HistoryActivity.class));
        Ui.button(records, "Messages par contact", false, () -> open(MessagesActivity.class));
        Ui.text(content, "Les anciennes conversations non enregistrées ne sont pas reconstituées. Aucun enregistrement audio n’est conservé par l’application.", 15);
    }

    private void settings(LinearLayout content) {
        LinearLayout preferences = Ui.card(content, "Personnaliser", "Version " + BuildConfig.VERSION_NAME);
        Ui.button(preferences, "Modèle OpenAI et tarifs", true, () -> open(ModelsActivity.class));
        Ui.button(preferences, "Mon profil propriétaire et les styles", true, () -> open(OwnerProfileActivity.class));
        Ui.button(preferences, "Langue et profils par contact", false, () -> open(ProfilesActivity.class));
        Ui.button(preferences, "Confidentialité, alertes et répondeur", true, () -> open(SettingsActivity.class));
        Ui.button(preferences, "Clé OpenAI chiffrée", false, () -> open(CredentialsActivity.class));
        LinearLayout support = Ui.card(content, "Assistance", "Configuration et connexion");
        Ui.button(support, "Configurer mon téléphone", false, () -> open(SetupActivity.class));
        Ui.button(support, "Agent APK · partager et recevoir", false, () -> open(AgentShareActivity.class));
        Ui.button(support, "Diagnostic détaillé sans appel", false, () -> diagnose(support));
        Ui.button(support, "À propos", false, () -> open(AboutActivity.class));
        Ui.text(content, "Les doubles appels ne sont pas pris automatiquement.", 15);
    }

    private void diagnose(LinearLayout target) {
        if (Readiness.checking()) return;
        Readiness.begin();
        TextView result = Ui.text(target, "Diagnostic en cours — aucun appel ne sera décroché…", 16);
        PhoneAgent.diagnose(message -> {
            Readiness.result(message);
            if (!isDestroyed() && !isFinishing()) {
                result.setText(Readiness.fresh() ? "Le service audio et OpenAI sont accessibles." : "La connexion n’est pas prête.");
                String displayed = tab.equals("Réglages") ? message : Readiness.fresh()
                        ? "Le service audio et OpenAI répondent. Aucun appel n’a été décroché. Le son réel reste à vérifier pendant un appel."
                        : "Vérifiez Internet, votre clé API OpenAI et l’activation du service audio. Consultez le détail pour identifier le problème.";
                new AlertDialog.Builder(this).setTitle(Readiness.fresh() ? "Services accessibles" : "Vérification à reprendre")
                        .setMessage(displayed).setNeutralButton("Détail", (dialog, which) -> new AlertDialog.Builder(this)
                                .setMessage(Readiness.detail()).setPositiveButton("Fermer", (detail, button) -> render()).show())
                        .setPositiveButton("Compris", (dialog, which) -> render()).show();
            }
        });
    }

    private void open(Class<?> activity) { startActivity(new Intent(this, activity)); }

    private void fact(LinearLayout parent, String symbol, String label, String value) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        parent.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
        TextView icon = new TextView(this);
        icon.setText(symbol);
        icon.setTextSize(19);
        icon.setTextColor(Ui.INK);
        icon.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable circle = Ui.rounded(Color.TRANSPARENT, 100, this);
        circle.setStroke(Ui.dp(this, 1), 0x668CABB0);
        icon.setBackground(circle);
        column.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        Ui.text(column, label, 13).setTextColor(Ui.MUTED);
        Ui.text(column, value, 15).setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    private void tile(LinearLayout parent, float weight, android.graphics.drawable.Drawable background,
                      String symbol, String label, Runnable action, String description) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.TOP);
        panel.setPadding(Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 15));
        panel.setBackground(background);
        panel.setContentDescription(description);
        panel.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams placement = new LinearLayout.LayoutParams(0, -1, weight);
        placement.rightMargin = Ui.dp(this, 5);
        parent.addView(panel, placement);
        TextView icon = new TextView(this);
        icon.setText(symbol);
        icon.setTextSize(19);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(Ui.INK);
        android.graphics.drawable.GradientDrawable circle = Ui.rounded(0x33FFFFFF, 100, this);
        circle.setStroke(Ui.dp(this, 1), 0x55738786);
        icon.setBackground(circle);
        panel.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        panel.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        TextView title = Ui.text(panel, label, 16);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setMinHeight(Ui.dp(this, 56));
    }
}
