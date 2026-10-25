package live.pharmaradio.reponsevoice.autonome;

import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;

public class CredentialsActivity extends PrivateActivity {
    private EditText input;

    @Override
    protected void showPrivateContent() { render(); }

    private void render() {
        LinearLayout content = Ui.page(this, "Clé API OpenAI", "Votre compte personnel");
        LinearLayout card = Ui.card(content, KeyVault.configured(this) ? "Clé configurée" : "Clé requise",
                "Votre clé est conservée chiffrée sur ce téléphone et n’est jamais affichée après sauvegarde. Préférez une clé dédiée et révocable.");
        input = Ui.field(card, "Votre clé API OpenAI", false, 512);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSaveEnabled(false);
        input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        Ui.button(card, "Enregistrer la clé", true, () -> {
            try {
                KeyVault.save(this, input.getText().toString());
                input.setText("");
                if (getIntent().getBooleanExtra("return-to-setup", false)) finish();
                else render();
            } catch (Exception error) { notice("Clé non enregistrée : vérifiez son format ou réessayez."); }
        });
        Ui.button(card, "Effacer la clé", false, () -> new AlertDialog.Builder(this)
                .setMessage("Sans clé API, les prochains appels IA ne pourront pas démarrer.")
                .setNegativeButton("Annuler", null).setPositiveButton("Effacer", (dialog, which) -> {
                    try { KeyVault.clear(this); render(); }
                    catch (Exception error) { notice("Effacement impossible."); }
                }).show());
        Ui.text(content, "La voix et la dictée sont traitées par OpenAI. L’utilisation de l’API est facturée sur votre compte.", 15);
    }

    private void notice(String message) {
        new AlertDialog.Builder(this).setMessage(message).setPositiveButton("Compris", null).show();
    }

    @Override
    protected void onPause() {
        if (input != null) input.setText("");
        super.onPause();
    }
}
