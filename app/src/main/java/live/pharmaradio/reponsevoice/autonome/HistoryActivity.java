package live.pharmaradio.reponsevoice.autonome;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class HistoryActivity extends PrivateActivity {
    private String selected;

    @Override
    protected void showPrivateContent() { render(); }

    private void render() {
        LinearLayout content = Ui.page(this, "Historique", "Vos missions, messages confirmés et consommations");
        Ui.button(content, "Messages par contact", true, () -> startActivity(new Intent(this, MessagesActivity.class)));
        if (selected != null) Ui.button(content, "Toutes les fiches", false, () -> { selected = null; render(); });
        try {
            List<JSONObject> entries = new CallStore(this).all();
            if (entries.isEmpty()) Ui.text(content, "Aucune fiche pour le moment. Elles apparaîtront lors des prochains appels IA ; les anciens appels ne sont pas reconstruits.", 17);
            for (JSONObject entry : entries) {
                if (selected != null && !selected.equals(entry.getString("id"))) continue;
                LinearLayout card = Ui.card(content, label(entry), date(entry.getLong("created")) + " · " + (entry.optString("direction").equals("outgoing") ? "Sortant" : "Entrant"));
                Ui.text(card, stateLabel(entry.optString("state")), 18).setTextColor(entry.optString("state").equals("INTERRUPTED") ? Ui.DANGER : Ui.PRIMARY);
                if (selected == null) Ui.button(card, "Ouvrir la fiche", false, () -> { selected = entry.optString("id"); render(); });
                else detail(card, entry);
            }
            Ui.button(content, "Effacer les fiches…", false, () -> new AlertDialog.Builder(this).setTitle("Effacer les fiches ?")
                    .setMessage("Les messages confirmés ont leur propre boîte et ne seront pas supprimés.")
                    .setNegativeButton("Annuler", null).setPositiveButton("Effacer", (dialog, which) -> {
                        try { new CallStore(this).clear(); selected = null; render(); }
                        catch (RuntimeException error) { Ui.text(content, "Effacement impossible.", 16); }
                    }).show());
        } catch (Exception error) { Ui.text(content, "Les fiches ne peuvent pas être lues. Aucun historique n’a été remplacé par une liste vide.", 17); }
    }

    private void detail(LinearLayout card, JSONObject entry) throws Exception {
        Ui.text(card, "État final connu\n" + entry.optString("reason"), 16);
        if (!entry.optString("model").isEmpty()) Ui.text(card, "Modèle : " + entry.optString("model"), 16);
        Ui.text(card, "Mission\n" + (entry.optString("mission").isEmpty() ? "Accueil d’un appel entrant" : entry.optString("mission")), 17);
        boolean confirmed = false;
        for (MessageHistory.Entry message : new MessageStore(this).all()) {
            if (message.id.startsWith(entry.getString("id") + ":")) {
                confirmed = true;
                if (message.result == null) Ui.text(card, "Message confirmé\n" + message.message, 17);
                else ResultView.show(card, message.result);
                Ui.text(card, "Conservation consentie : 24 h à partir de l’enregistrement.", 14);
            }
        }
        if (!confirmed) Ui.text(card, "Pas de bilan confirmé encore conservé. Ce n’est pas une transcription de l’appel.", 15);
        Ui.text(card, entry.optString("usage", "Aucun relevé API reçu."), 15);
        if (!entry.optString("number").isEmpty()) Ui.button(card, "Préparer un nouvel appel", false, () -> {
            Intent prepare = new Intent(this, OutgoingCallActivity.class).putExtra("number", entry.optString("number"));
            startActivity(prepare);
        });
        Ui.button(card, "Préparer un rappel dans mon agenda", false, () -> {
            Intent reminder = new Intent(Intent.ACTION_INSERT, android.provider.CalendarContract.Events.CONTENT_URI)
                    .putExtra(android.provider.CalendarContract.Events.TITLE, "Rappeler " + label(entry));
            try { startActivity(reminder); }
            catch (RuntimeException error) { new AlertDialog.Builder(this).setMessage("Aucun agenda compatible disponible. Aucun rappel créé.").setPositiveButton("Compris", null).show(); }
        });
        Ui.button(card, "Supprimer cette fiche…", false, () -> new AlertDialog.Builder(this).setTitle("Supprimer la fiche ?")
                .setMessage("Ses messages restent consultables jusqu’à expiration ou suppression dans Messages par contact.")
                .setNegativeButton("Annuler", null).setPositiveButton("Supprimer", (dialog, which) -> {
                    try { new CallStore(this).remove(entry.optString("id")); selected = null; render(); }
                    catch (Exception error) { Ui.text(card, "Suppression impossible.", 16); }
                }).show());
    }

    private String label(JSONObject entry) { return entry.optString("name").isEmpty() ? entry.optString("number", "").isEmpty() ? "Numéro masqué" : entry.optString("number") : entry.optString("name"); }
    private String date(long created) { return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(created)); }
    private String stateLabel(String state) {
        switch (state) {
            case "FINISHED": return "Appel terminé";
            case "HANDOFF": return "Conversation rendue à l’utilisateur";
            case "INTERRUPTED": return "IA interrompue — consulter le motif";
            default: return "Suivi en cours ou fin non confirmée";
        }
    }
}
