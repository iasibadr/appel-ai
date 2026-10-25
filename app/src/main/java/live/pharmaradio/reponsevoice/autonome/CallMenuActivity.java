package live.pharmaradio.reponsevoice.autonome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.telecom.Call;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CallMenuActivity extends Activity {
    private TextView status;
    private Button human;
    private Button ai;
    private Button hangup;
    private Button send;
    private EditText instruction;

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout content = Ui.page(this, "Votre appel", "Vous gardez toujours la main");
        LinearLayout live = Ui.card(content, "État de la conversation", "L’IA s’annonce au correspondant.");
        status = Ui.text(live, "", 19);
        human = Ui.button(live, "Répondre moi-même", true, () -> { if (CallBridgeService.instance != null) CallBridgeService.instance.chooseHuman(); });
        ai = Ui.button(live, "Confier à ChatGPT", false, () -> { if (CallBridgeService.instance != null) CallBridgeService.instance.chooseAI(); });
        hangup = Ui.button(live, "Refuser / raccrocher", false, () -> { if (CallBridgeService.instance != null) CallBridgeService.instance.hangUp(); });
        Ui.danger(hangup);
        LinearLayout steering = Ui.card(content, "Consigne privée à l’IA", "Texte destiné à l’IA, pris en compte au prochain tour. Le correspondant ne voit pas ce texte ; ce n’est pas un SMS.");
        instruction = Ui.field(steering, "Exemple : demande aussi les horaires du samedi.", true, 600);
        send = Ui.button(steering, "Transmettre la consigne", true, () -> {
            if (getSystemService(android.app.KeyguardManager.class).isKeyguardLocked()) {
                status.setText("Déverrouillez le téléphone avant d’envoyer une consigne privée."); return;
            }
            CallBridgeService service = CallBridgeService.instance;
            if (service != null && service.agent != null && service.agent.steer(instruction.getText().toString())) {
                instruction.setText(""); status.setText("Consigne transmise pour le prochain tour de l’IA.");
            } else status.setText("Consigne non transmise : aucune conversation IA active.");
        });
        Ui.button(content, "Messages par contact", false, () -> startActivity(new Intent(this, MessagesActivity.class)));
        Ui.button(content, "Fiches d’appel et consommation", false, () -> startActivity(new Intent(this, HistoryActivity.class)));
        Ui.button(content, "Dernier état technique", false, () -> new AlertDialog.Builder(this).setTitle("Dernier état connu")
                .setMessage((CallBridgeService.lastError.isEmpty() ? "Aucune erreur récente conservée." : CallBridgeService.lastError)
                        + "\n\n" + getSharedPreferences("call-metrics", MODE_PRIVATE).getString("last-usage", "Aucun relevé API."))
                .setPositiveButton("Fermer", null).show());
        refresh();
    }

    private void refresh() {
        CallBridgeService service = CallBridgeService.instance;
        boolean present = service != null && service.incoming != null && service.incoming.getState() != Call.STATE_DISCONNECTED;
        boolean managed = present && service.agent != null;
        status.setText(present ? service.status : "Aucun appel à gérer pour le moment. Les messages et fiches restent consultables.");
        human.setEnabled(present);
        human.setText(managed ? "Reprendre la conversation" : "Répondre moi-même");
        ai.setEnabled(present && service.incoming.getState() == Call.STATE_RINGING && !managed);
        hangup.setEnabled(present);
        send.setEnabled(managed && service.incoming.getState() == Call.STATE_ACTIVE);
        instruction.setEnabled(managed && !getSystemService(android.app.KeyguardManager.class).isKeyguardLocked());
    }

    @Override
    protected void onResume() { super.onResume(); CallBridgeService.observer = this::refresh; refresh(); }
    @Override
    protected void onPause() { CallBridgeService.observer = null; instruction.setText(""); super.onPause(); }
}
