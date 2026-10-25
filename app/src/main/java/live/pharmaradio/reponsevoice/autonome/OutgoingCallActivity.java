package live.pharmaradio.reponsevoice.autonome;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;

public class OutgoingCallActivity extends Activity {
    private EditText number;
    private EditText brief;
    private EditText questions;
    private EditText limits;
    private EditText handoff;
    private android.widget.CheckBox report;
    private TextView status;
    private Button dictate;
    private Button human;
    private Button ai;
    private MediaRecorder recorder;
    private File recording;
    private PhoneAgent transcription;
    private boolean working;
    private boolean placing;
    private MissionDossier dossier = MissionDossier.empty();
    private TextView dossierStatus;

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        File[] leftovers = getCacheDir().listFiles((directory, name) -> name.startsWith("call-brief-") && name.endsWith(".m4a"));
        if (leftovers != null) for (File file : leftovers) file.delete();
        LinearLayout content = Ui.page(this, "Préparer une mission", "Un contact, un objectif, des limites claires");
        LinearLayout layout = Ui.card(content, "1 · Votre correspondant", "Aucun appel ne part avant confirmation.");
        button(layout, "Choisir un contact", () -> {
            if (working || recorder != null || placing) return;
            try { startActivityForResult(new Intent(Intent.ACTION_PICK).setType(Phone.CONTENT_TYPE), 10); }
            catch (RuntimeException error) { status.setText("Sélecteur indisponible ; saisissez le numéro."); }
        });
        number = Ui.field(layout, "Numéro de téléphone", false, 35);
        number.setInputType(InputType.TYPE_CLASS_PHONE);
        number.setSaveEnabled(false);
        number.setText(getIntent().getStringExtra("number") == null ? "" : getIntent().getStringExtra("number"));
        layout = Ui.card(content, "2 · La mission", "Vous pouvez dicter l’objectif puis préciser les autres champs.");
        text(layout, "2. Donnez le sujet et les questions. La dictée est transmise à OpenAI, puis son fichier audio est supprimé. Vérifiez le texte avant d’appeler.", 16);
        brief = Ui.field(layout, "Objectif : que doit obtenir l’IA ?", true, 1600);
        brief.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        brief.setMinLines(3);
        brief.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1600)});
        brief.setSaveEnabled(false);
        dictate = button(layout, "Dicter mes consignes (60 s maximum)", () -> {
            if (recorder == null) startDictation();
            else stopDictation();
        });
        questions = Ui.field(layout, "Questions indispensables (facultatif)", true, 900);
        limits = Ui.field(layout, "Ce que l’IA ne doit pas accepter", true, 450);
        limits.setText("Aucun engagement, achat ou paiement.");
        handoff = Ui.field(layout, "Quand me rendre la conversation ?", true, 450);
        report = new android.widget.CheckBox(this);
        report.setText("Proposer un bilan structuré, uniquement si le correspondant accepte sa conservation 24 h");
        report.setTextColor(Ui.INK);
        report.setChecked(true);
        layout.addView(report);
        button(layout, "Choisir un exemple de mission", () -> {
            if (working || recorder != null || placing) return;
            MissionTemplate[] examples = MissionTemplate.examples();
            String[] labels = new String[examples.length];
            for (int index = 0; index < examples.length; index++) labels[index] = examples[index].title;
            new AlertDialog.Builder(this).setTitle("Modèles prêts à personnaliser")
                    .setItems(labels, (dialog, which) -> confirmTemplate(examples[which])).show();
        });
        button(layout, "Charger une mission favorite", () -> {
            if (!working && recorder == null && !placing) startActivityForResult(new Intent(this, MissionLibraryActivity.class).putExtra("picker", true), 82);
        });
        button(layout, "Enregistrer comme favori", () -> {
            if (working || recorder != null || placing) return;
            try {
                MissionTemplate template = new MissionTemplate("Brouillon", brief.getText().toString(), questions.getText().toString(),
                        limits.getText().toString(), handoff.getText().toString(), report.isChecked());
                startActivity(new Intent(this, MissionLibraryActivity.class).putExtra("draft", MissionLibrary.encode(template).toString()));
            } catch (Exception error) { status.setText(error instanceof IllegalArgumentException ? error.getMessage() : "Préparation du favori impossible."); }
        });
        LinearLayout documents = Ui.card(content, "3 · Dossier de mission", "Informations et documents choisis, jamais un accès libre à vos fichiers.");
        dossierStatus = text(documents, "Aucune pièce jointe à cette mission.", 15);
        button(documents, "Préparer ou relire le dossier", () -> {
            if (working || recorder != null || placing) return;
            try { startActivityForResult(new Intent(this, MissionDossierActivity.class).putExtra("dossier", DossierPayload.encode(dossier)), 83); }
            catch (Exception error) { status.setText("Dossier indisponible. Aucun document ajouté ni appel lancé."); }
        });
        text(documents, "Les pièces validées seront transmises à OpenAI avec l’appel IA confirmé, ce qui peut augmenter les tokens. Elles ne sont pas conservées dans les favoris ni les fiches d’appel.", 14);
        layout = Ui.card(content, "4 · Vérifier et choisir", "L’IA se présente comme telle. Un seul appel confirmé, jamais de rappel automatique.");
        status = text(layout, "L’IA se présente comme telle. Informez votre correspondant avant l’appel.", 16);
        human = button(layout, "Appeler moi-même", () -> {
            String destination = destination();
            if (destination == null) return;
            OutgoingPlan.clear();
            try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", destination, null))); }
            catch (RuntimeException error) { status.setText("L’application Téléphone n’est pas disponible."); }
        });
        ai = button(layout, "Faire appeler ChatGPT…", this::confirmCall);
        button(layout, "Effacer les consignes", () -> { if (!working && recorder == null && !placing) brief.setText(""); });
        text(layout, "Double appel : le répondeur automatique pendant une conversation n’est pas activé. Sur cette ligne, répondre au second appel risque de mettre le premier en attente ; deux conversations IA/humain indépendantes ne sont pas validées.", 15);
        String templateId = getIntent().getStringExtra("template-id");
        if (templateId != null) loadTemplate(templateId);
    }

    private void loadTemplate(String id) {
        try { confirmTemplate(MissionLibrary.find(this, id)); }
        catch (Exception error) { status.setText("Ce favori n’est plus disponible. Aucun appel lancé."); }
    }

    private void confirmTemplate(MissionTemplate template) {
        if (working || recorder != null || placing) return;
        new AlertDialog.Builder(this).setTitle("Charger « " + template.title + " » ?")
                .setMessage("Cela remplace l’objectif, les questions, les limites et les conditions de reprise affichés. Le numéro ne change pas. Aucun appel ne sera lancé.")
                .setNegativeButton("Annuler", null).setPositiveButton("Charger", (dialog, which) -> {
                    if (working || recorder != null || placing) return;
                    brief.setText(template.objective);
                    questions.setText(template.questions);
                    limits.setText(template.limits);
                    handoff.setText(template.handoff);
                    report.setChecked(template.report);
                    status.setText("Modèle chargé. Personnalisez-le et vérifiez le destinataire avant de confirmer un appel.");
                }).show();
    }

    private TextView text(LinearLayout layout, String value, int size) {
        return Ui.text(layout, value, size);
    }

    private Button button(LinearLayout layout, String label, Runnable action) {
        return Ui.button(layout, label, label.startsWith("Faire appeler"), action);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 83) {
            if (result == RESULT_OK && data != null) {
                try {
                    dossier = DossierPayload.decode(data.getStringExtra("dossier"));
                    dossierStatus.setText(dossierSummary(dossier));
                } catch (Exception error) { status.setText("Dossier invalide. La sélection précédente est conservée."); }
            }
            return;
        }
        if (request == 82) {
            if (result == RESULT_OK && data != null) loadTemplate(data.getStringExtra("template-id"));
            return;
        }
        if (request != 10 || result != RESULT_OK || data == null || data.getData() == null) return;
        try (Cursor cursor = getContentResolver().query(data.getData(), new String[]{Phone.NUMBER}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) number.setText(cursor.getString(0));
        } catch (RuntimeException error) {
            status.setText("Impossible de lire le contact sélectionné ; saisissez le numéro.");
        }
    }

    private String destination() {
        String raw = number.getText().toString().trim();
        String normalized = PhoneNumberUtils.normalizeNumber(raw);
        if (!raw.matches("[+0-9 ()-]+") || !normalized.matches("\\+?[0-9]{6,15}")) {
            status.setText("Saisissez un numéro complet, sans code spécial ni extension.");
            return null;
        }
        if (PhoneNumberUtils.isEmergencyNumber(normalized)) {
            status.setText("Utilisez directement Téléphone pour un appel d’urgence, jamais l’IA.");
            return null;
        }
        return normalized;
    }

    private String dossierSummary(MissionDossier selected) {
        if (selected.documents.isEmpty()) return "Aucune pièce jointe à cette mission.";
        StringBuilder result = new StringBuilder("Pièces transmises à OpenAI avec la mission :");
        for (MissionDocument document : selected.documents) result.append("\n• ").append(document.title)
                .append(document.shareable ? " · communicable" : " · privé pour l’IA");
        return result.toString();
    }

    private boolean audioFree() {
        if (getSystemService(AudioManager.class).getMode() != AudioManager.MODE_NORMAL) {
            status.setText("Terminez l’appel ou la session vocale en cours avant de préparer un appel IA.");
            return false;
        }
        return true;
    }

    private void confirmCall() {
        if (working || recorder != null || placing || !audioFree()) return;
        if (BridgeProvider.getHelper() == null) {
            status.setText("Le service audio n’est pas connecté. Lancez le diagnostic depuis Appels réels avant d’appeler avec l’IA.");
            return;
        }
        String destination = destination();
        if (destination == null) return;
        String instructions;
        try {
            instructions = CallMission.build(brief.getText().toString(), questions.getText().toString(),
                    limits.getText().toString(), handoff.getText().toString(), report.isChecked());
        } catch (IllegalArgumentException error) { status.setText(error.getMessage()); return; }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, 12);
            return;
        }
        MissionDossier confirmedDossier = dossier;
        new AlertDialog.Builder(this).setTitle("Confirmer l’appel IA")
                .setMessage("Appeler maintenant " + destination + " avec ChatGPT ?\n\nConsignes :\n" + instructions
                        + "\n\n" + dossierSummary(confirmedDossier)
                        + "\n\nAppel facturé selon votre forfait et utilisation de l’API OpenAI. Si l’IA échoue, reprenez ou raccrochez l’appel.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Oui, appeler maintenant", (dialog, which) -> {
                    if (placing || !audioFree()) return;
                    placing = true;
                    updateButtons();
                    OutgoingPlan.arm(destination, instructions, confirmedDossier);
                    try {
                        getSystemService(TelecomManager.class).placeCall(Uri.fromParts("tel", destination, null), new Bundle());
                        status.setText("Appel confirmé. Si la sélection de SIM apparaît, choisissez votre ligne. Reprenez dans Téléphone si l’IA ne se connecte pas.");
                        brief.setText("");
                        number.setText("");
                        dossier = MissionDossier.empty();
                        dossierStatus.setText("Aucune pièce jointe à cette mission.");
                    } catch (RuntimeException error) {
                        OutgoingPlan.clear();
                        placing = false;
                        updateButtons();
                        status.setText("Appel non lancé (" + error.getClass().getSimpleName() + "). Aucun nouvel essai automatique.");
                    }
                }).show();
    }

    private void startDictation() {
        if (working || placing || !audioFree()) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 11);
            return;
        }
        try {
            recording = File.createTempFile("call-brief-", ".m4a", getCacheDir());
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);
            recorder.setAudioSamplingRate(24000);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setMaxDuration(60000);
            recorder.setMaxFileSize(900000);
            recorder.setOutputFile(recording.getAbsolutePath());
            recorder.setOnInfoListener((source, what, extra) -> {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) stopDictation();
            });
            recorder.prepare();
            recorder.start();
            status.setText("Enregistrement de vos consignes… Touchez Arrêter pour les transcrire. Quitter cet écran annule l’enregistrement.");
            updateButtons();
        } catch (Exception error) {
            discardRecording();
            status.setText("Microphone indisponible ; vous pouvez écrire les consignes.");
        }
    }

    private void stopDictation() {
        if (recorder == null) return;
        try { recorder.stop(); }
        catch (RuntimeException error) {
            discardRecording();
            status.setText("Dictée trop courte ou interrompue ; réessayez.");
            return;
        }
        recorder.release();
        recorder = null;
        File audio = recording;
        recording = null;
        working = true;
        updateButtons();
        status.setText("Transcription OpenAI en cours… Aucun appel n’est lancé.");
        transcription = PhoneAgent.transcribe(audio, value -> {
            if (isFinishing() || isDestroyed()) return;
            working = false;
            brief.setText(value);
            status.setText(value.length() > 1600 ? "Dictée écourtée à 1 600 caractères : vérifiez l’objectif et complétez les autres champs avant d’appeler."
                    : "Vérifiez et corrigez les consignes, puis choisissez qui appelle.");
            updateButtons();
        }, error -> {
            if (isFinishing() || isDestroyed()) return;
            working = false;
            status.setText(error);
            updateButtons();
        });
    }

    private void updateButtons() {
        if (dictate == null) return;
        dictate.setEnabled(!working && !placing);
        dictate.setText(recorder == null ? "Dicter mes consignes (60 s maximum)" : "Arrêter et transcrire");
        human.setEnabled(!working && !placing && recorder == null);
        ai.setEnabled(!working && !placing && recorder == null);
        brief.setEnabled(!working && !placing && recorder == null);
        number.setEnabled(!working && !placing && recorder == null);
        questions.setEnabled(!working && !placing && recorder == null);
        limits.setEnabled(!working && !placing && recorder == null);
        handoff.setEnabled(!working && !placing && recorder == null);
        report.setEnabled(!working && !placing && recorder == null);
    }

    private void discardRecording() {
        if (recorder != null) { recorder.release(); recorder = null; }
        if (recording != null) { recording.delete(); recording = null; }
        updateButtons();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        status.setText(grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED
                ? "Autorisation accordée. Touchez à nouveau le bouton pour continuer."
                : "Autorisation refusée. Aucun appel ou enregistrement lancé.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (placing && getSystemService(AudioManager.class).getMode() == AudioManager.MODE_NORMAL) {
            CallBridgeService service = CallBridgeService.instance;
            if (service == null || service.incoming == null || service.incoming.getState() == android.telecom.Call.STATE_DISCONNECTED) {
                placing = false;
                OutgoingPlan.clear();
                updateButtons();
            }
        }
    }

    @Override
    protected void onPause() {
        if (recorder != null) {
            discardRecording();
            status.setText("Dictée annulée en quittant l’écran. Aucun audio envoyé.");
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        discardRecording();
        if (transcription != null) transcription.close();
        super.onDestroy();
    }
}
