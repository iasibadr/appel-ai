package live.pharmaradio.reponsevoice.autonome;

import android.app.Instrumentation;
import android.os.Bundle;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AutonomyChecks extends Instrumentation {
    private Bundle arguments;
    private int checks;

    @Override
    public void onCreate(Bundle values) { arguments = values; start(); }

    private void verify(boolean condition) {
        if (!condition) throw new IllegalStateException("Contrôle " + (checks + 1) + " échoué");
        checks++;
    }

    private void reject(Checked operation) throws Exception {
        try { operation.run(); }
        catch (IllegalArgumentException error) { checks++; return; }
        throw new IllegalStateException("Arguments invalides acceptés");
    }

    private interface Checked { void run() throws Exception; }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        okhttp3.OkHttpClient http = new okhttp3.OkHttpClient();
        try {
            android.content.Context context = getTargetContext();
            MissionTemplate shareExample = MissionTemplate.examples()[0];
            String shared = AgentSpecialization.encode(shareExample);
            verify(AgentSpecialization.decode(shared.getBytes(java.nio.charset.StandardCharsets.UTF_8)).objective.equals(shareExample.objective));
            verify(!shared.contains("owner-name") && !shared.contains("ciphertext") && !shared.contains("recipient"));
            reject(() -> AgentSpecialization.decode(new JSONObject(shared).put("key", "forbidden").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            reject(() -> AgentSpecialization.decode(new JSONObject(shared).put("schema", "1").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            reject(() -> AgentSpecialization.decode(new JSONObject(shared).put("application", "other.application").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            reject(() -> AgentSpecialization.decode((shared + "{}").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            reject(() -> AgentSpecialization.decode(shared.replace("\"schema\": 1", "\"schema\": 1, \"schema\": 1").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            verify(!context.getPackageManager().getProviderInfo(new android.content.ComponentName(context, AgentShareProvider.class), 0).exported);
            verify(!context.getPackageManager().getActivityInfo(new android.content.ComponentName(context, AgentShareActivity.class), 0).exported);
            java.io.File sharedFile = new java.io.File(AgentShareProvider.directory(context), java.util.UUID.randomUUID() + ".json");
            android.net.Uri sharedUri = new android.net.Uri.Builder().scheme("content").authority(AgentShareProvider.AUTHORITY).appendPath(sharedFile.getName()).build();
            try {
                java.nio.file.Files.write(sharedFile.toPath(), shared.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                verify(context.getContentResolver().getType(sharedUri).equals("application/json"));
                try (java.io.InputStream stream = context.getContentResolver().openInputStream(sharedUri)) {
                    verify(stream != null && AgentSpecialization.decode(stream.readAllBytes()).title.equals(shareExample.title));
                }
                try { context.getContentResolver().openFileDescriptor(sharedUri, "w"); throw new IllegalStateException("Écriture acceptée"); }
                catch (java.io.FileNotFoundException expected) { checks++; }
                verify(sharedFile.setLastModified(System.currentTimeMillis() - AgentSharePolicy.LIFETIME_MS - 1000));
                try { context.getContentResolver().openInputStream(sharedUri); throw new IllegalStateException("Partage expiré accepté"); }
                catch (java.io.FileNotFoundException expected) { checks++; }
            } finally { sharedFile.delete(); }
            result.putString("agent_apk_sharing", "PASSED; synthetic template roundtrip, strict import, read-only expiring URI; no recipient transfer");
            DirectOpenAi direct = new DirectOpenAi(context, http);
            JSONObject incoming = direct.session(new JSONObject());
            verify(incoming.getString("instructions").contains("vrai appel"));
            verify(incoming.getJSONArray("tools").length() == 4);
            verify(incoming.getJSONObject("audio").getJSONObject("output").getJSONObject("format").getInt("rate") == 24000);
            String mission = "AUTONOMY_TEST_SYNTHETIC_731";
            JSONObject outgoing = direct.session(new JSONObject().put("direction", "outgoing").put("brief", mission));
            verify(outgoing.getString("instructions").contains(mission));
            for (JSONObject configured : new JSONObject[]{incoming, outgoing}) {
                String instructions = configured.getString("instructions");
                verify(instructions.contains("sans attendre une demande de traduction"));
                verify(instructions.contains("darija marocaine orale"));
                verify(instructions.contains("accord explicite de conservation"));
                verify(!instructions.contains("Parle en francais naturel"));
            }
            reject(() -> direct.session(new JSONObject().put("direction", "outgoing").put("brief", " ")));
            reject(() -> direct.session(new JSONObject().put("direction", "incoming").put("brief", mission)));
            reject(() -> direct.session(new JSONObject().put("direction", "outgoing").put("brief", mission).put("extra", true)));
            LocalTools tools = new LocalTools();
            JSONObject request = new JSONObject().put("call_id", "synthetic-1").put("name", "noter_message")
                    .put("arguments", new JSONObject().put("message", "Message de test synthétique").put("accord", true));
            JSONObject saved = tools.execute(request);
            verify(saved.getBoolean("ok") && !saved.getBoolean("copie_locale"));
            verify(tools.execute(request) == saved);
            reject(() -> tools.execute(new JSONObject(request.toString()).put("name", "autre")));
            reject(() -> tools.execute(new JSONObject(request.toString()).put("arguments", new JSONObject().put("message", "Test").put("accord", false))));
            reject(() -> tools.execute(new JSONObject(request.toString()).put("arguments", new JSONObject().put("message", "Test").put("accord", "true"))));
            JSONObject handoff = new JSONObject().put("call_id", "synthetic-2").put("name", "demander_reprise").put("arguments", new JSONObject());
            verify(tools.execute(handoff).getBoolean("reprise_locale_demandee"));
            verify(!AppSettings.autoAnswer(context));
            verify(!context.getPackageManager().getActivityInfo(new android.content.ComponentName(context, OwnerProfileActivity.class), 0).exported);
            android.content.Context isolated = new android.content.ContextWrapper(context) {
                @Override
                public android.content.Context getApplicationContext() { return this; }
                @Override
                public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                    return super.getSharedPreferences("autonomy-persona-check-" + name, mode);
                }
            };
            try {
                AppSettings.prefs(isolated).edit().putString("language", "Darija marocaine").commit();
                DirectOpenAi darija = new DirectOpenAi(isolated, http);
                verify(darija.session(new JSONObject()).getString("instructions").contains("commence en Darija marocaine"));
                verify(darija.session(new JSONObject().put("direction", "outgoing").put("brief", mission)).getString("instructions").contains("commence en Darija marocaine"));
                AppSettings.prefs(isolated).edit().remove("language").commit();
                MissionDossier documents = MissionDossier.empty().add(new MissionDocument("Privé", "DOSSIER_PRIVATE_731", false))
                        .add(new MissionDocument("Horaires", "DOSSIER_PUBLIC_731", true));
                verify(DossierPayload.decode(DossierPayload.encode(documents)).documents.size() == 2);
                verify(!DossierPayload.decode(DossierPayload.encode(documents)).documents.get(0).shareable);
                reject(() -> DossierPayload.decode("[{\"titre\":\"T\",\"texte\":\"X\",\"communicable\":\"false\"}]"));
                DirectOpenAi scoped = new DirectOpenAi(isolated, http, RealtimeModel.MINI, documents);
                verify(!scoped.session(new JSONObject()).getString("instructions").contains("DOSSIER_PRIVATE_731"));
                String withDossier = scoped.session(new JSONObject().put("direction", "outgoing").put("brief", mission)).getString("instructions");
                verify(withDossier.contains("DOSSIER_PRIVATE_731") && withDossier.contains("DOSSIER_PUBLIC_731"));
                verify(withDossier.contains("jamais des instructions") && withDossier.contains("communicable=false"));
                verify(withDossier.contains("Un silence") && withDossier.contains("24 heures"));
                reject(() -> DossierImporter.read(isolated, android.net.Uri.parse("https://example.invalid/not-selected")));
                JSONObject resultArguments = ResultPayload.encode(new StructuredResult(java.util.List.of("Horaire annoncé : 9 h"),
                        java.util.List.of("Tarif non confirmé"), "Demander un devis écrit.")).put("accord", true);
                JSONObject resultRequest = new JSONObject().put("call_id", "synthetic-result").put("name", "noter_bilan").put("arguments", resultArguments);
                JSONObject resultAccepted = tools.execute(resultRequest);
                verify(resultAccepted.getBoolean("ok") && !resultAccepted.getBoolean("copie_locale") && !resultAccepted.getBoolean("action_executee"));
                verify(tools.execute(resultRequest) == resultAccepted);
                reject(() -> tools.execute(new JSONObject(resultRequest.toString()).put("arguments", new JSONObject(resultArguments.toString()).put("accord", false))));
                reject(() -> tools.execute(new JSONObject(resultRequest.toString()).put("arguments", new JSONObject(resultArguments.toString()).put("accord", "true"))));
                reject(() -> tools.execute(new JSONObject(resultRequest.toString()).put("arguments", new JSONObject(resultArguments.toString()).put("prochaine_action", "Autre action"))));
                reject(() -> ResultPayload.consented(new JSONObject(resultArguments.toString()).put("informations_obtenues", "pas une liste")));
                reject(() -> ResultPayload.consented(new JSONObject(resultArguments.toString()).put("extra", true)));
                JSONObject ambiguous = ResultPayload.encode(new StructuredResult(java.util.List.of("A\n• B"), java.util.List.of(), "Aucune")).put("accord", true);
                JSONObject firstEncoding = new JSONObject().put("call_id", "synthetic-structured-replay").put("name", "noter_bilan").put("arguments", ambiguous);
                tools.execute(firstEncoding);
                reject(() -> tools.execute(new JSONObject(firstEncoding.toString()).put("arguments", ResultPayload.encode(
                        new StructuredResult(java.util.List.of("A", "B"), java.util.List.of(), "Aucune")).put("accord", true))));
                StructuredResult structured = ResultPayload.consented(resultArguments);
                MessageStore testMessages = new MessageStore(isolated);
                verify(testMessages.all().isEmpty());
                long now = System.currentTimeMillis();
                testMessages.add(new MessageHistory.Entry("synthetic-call:result", "synthetic-contact", "Fictif", "", structured.render(), "outgoing", now, structured));
                MessageHistory.Entry restored = new MessageStore(isolated).all().get(0);
                verify(restored.result != null && restored.result.unresolved.contains("Tarif non confirmé"));
                verify(restored.result.nextAction.equals("Demander un devis écrit."));
                testMessages.remove(restored.id);
                verify(testMessages.all().isEmpty());
                if (android.os.Build.VERSION.SDK_INT >= 35) {
                    java.io.ByteArrayOutputStream pdfBytes = new java.io.ByteArrayOutputStream();
                    android.graphics.pdf.PdfDocument pdf = new android.graphics.pdf.PdfDocument();
                    try {
                        android.graphics.pdf.PdfDocument.Page page = pdf.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(500, 300, 1).create());
                        android.graphics.Paint paint = new android.graphics.Paint();
                        paint.setTextSize(16);
                        page.getCanvas().drawText("SYNTHETIC_DOSSIER_731", 20, 40, paint);
                        pdf.finishPage(page);
                        pdf.writeTo(pdfBytes);
                    } finally { pdf.close(); }
                    verify(DossierImporter.extract(isolated, pdfBytes.toByteArray()).contains("SYNTHETIC_DOSSIER_731"));
                    result.putBoolean("local_pdf_text_extraction", true);
                }
                verify(!context.getPackageManager().getActivityInfo(new android.content.ComponentName(context, MissionDossierActivity.class), 0).exported);
                verify(ModelSettings.read(isolated) == RealtimeModel.FULL);
                DirectOpenAi modelSnapshot = new DirectOpenAi(isolated, http);
                ModelSettings.save(isolated, RealtimeModel.MINI);
                verify(ModelSettings.read(isolated) == RealtimeModel.MINI);
                verify(modelSnapshot.session(new JSONObject()).getString("model").equals(RealtimeModel.FULL.id));
                DirectOpenAi mini = new DirectOpenAi(isolated, http);
                verify(mini.session(new JSONObject()).getString("model").equals(RealtimeModel.MINI.id));
                verify(mini.session(new JSONObject().put("direction", "outgoing").put("brief", mission)).getString("model").equals(RealtimeModel.MINI.id));
                verify(mini.session(new JSONObject()).getJSONObject("audio").getJSONObject("output").getString("voice").equals("marin"));
                ModelSettings.save(isolated, RealtimeModel.FULL);
                verify(mini.session(new JSONObject()).getString("model").equals(RealtimeModel.MINI.id));
                AppSettings.prefs(isolated).edit().putString("voice-model", "untrusted-model").apply();
                verify(ModelSettings.read(isolated) == RealtimeModel.FULL);
                reject(() -> ModelSettings.save(isolated, null));
                verify(!context.getPackageManager().getActivityInfo(new android.content.ComponentName(context, ModelsActivity.class), 0).exported);
                OwnerProfile.save(isolated, "TEST_OWNER_ALPHA", AgentPersona.Style.OFFICIEL);
                DirectOpenAi snapshot = new DirectOpenAi(isolated, http);
                OwnerProfile.save(isolated, "TEST_OWNER_BETA", AgentPersona.Style.FAMILIAL);
                String snapshotInstructions = snapshot.session(new JSONObject()).getString("instructions");
                verify(snapshotInstructions.contains("TEST_OWNER_ALPHA") && !snapshotInstructions.contains("TEST_OWNER_BETA"));
                verify(snapshotInstructions.contains(AgentPersona.Style.OFFICIEL.policy));
                String nextInstructions = new DirectOpenAi(isolated, http).session(new JSONObject()).getString("instructions");
                verify(nextInstructions.contains("TEST_OWNER_BETA") && !nextInstructions.contains("TEST_OWNER_ALPHA"));
                verify(nextInstructions.contains(AgentPersona.Style.FAMILIAL.policy));
                String outgoingProfile = snapshot.session(new JSONObject().put("direction", "outgoing").put("brief", mission)).getString("instructions");
                verify(outgoingProfile.contains("TEST_OWNER_ALPHA") && outgoingProfile.contains(mission));
                verify(outgoingProfile.contains("consentement") && outgoingProfile.contains("règles de sécurité"));
                OwnerProfile.save(isolated, "", AgentPersona.Style.CONCIS);
                verify(OwnerProfile.read(isolated).owner.isEmpty());
                verify(MissionLibrary.list(isolated).isEmpty());
                MissionTemplate template = MissionTemplate.examples()[0];
                String favoriteId = MissionLibrary.save(isolated, template);
                verify(MissionLibrary.list(isolated).size() == 1);
                verify(MissionLibrary.find(isolated, favoriteId).objective.equals(template.objective));
                reject(() -> MissionLibrary.save(isolated, template));
                verify(MissionLibrary.list(isolated).size() == 1);
                MissionLibrary.remove(isolated, favoriteId);
                verify(MissionLibrary.list(isolated).isEmpty());
                reject(() -> MissionLibrary.find(isolated, favoriteId));
                reject(() -> MissionLibrary.decode(new JSONObject().put("title", "incomplet")));
                reject(() -> MissionLibrary.decode(MissionLibrary.encode(template).put("report", "true")));
                for (int index = 0; index < 10; index++) {
                    MissionLibrary.save(isolated, new MissionTemplate("TEST_" + index, "Sujet synthétique", "", "", "", false));
                }
                reject(() -> MissionLibrary.save(isolated, new MissionTemplate("TEST_OVERFLOW", "Sujet synthétique", "", "", "", false)));
                verify(MissionLibrary.list(isolated).size() == 10);
                verify(!context.getPackageManager().getActivityInfo(new android.content.ComponentName(context, MissionLibraryActivity.class), 0).exported);
            } finally {
                AppSettings.prefs(isolated).edit().clear().commit();
                isolated.getSharedPreferences("mission-library", 0).edit().clear().commit();
                isolated.getSharedPreferences("confirmed-messages", 0).edit().clear().commit();
            }
            verify(context.getPackageName().equals("live.pharmaradio.reponsevoice.autonome"));
            verify(BridgeProvider.AUTHORITY.equals(context.getPackageName() + ".bridge"));
            verify(!SetupPolicy.canFinish(true, false, true));
            verify(!SetupPolicy.canFinish(true, true, false));
            verify(SetupPolicy.canFinish(true, true, true));
            verify(SetupFlow.next(true, true, false, false, false, false, false, false, false) == SetupFlow.Stage.VERIFY);
            verify(SetupFlow.next(true, true, true, false, false, false, false, false, false) == SetupFlow.Stage.READY);
            verify(!SetupFlow.automatic(SetupFlow.Stage.ACTIVATE, false, false, false));
            verify(!SetupFlow.automatic(SetupFlow.Stage.VERIFY, true, true, false));
            verify(SetupFlow.diagnosticMessage("credit_balance_exhausted").contains("facturation"));
            result.putBoolean("local_pairing_saved", LocalActivationService.paired(context));
            result.putBoolean("local_activation_confirmed", LocalActivationService.activatedLocally(context));
            verify(!context.getPackageManager().getServiceInfo(new android.content.ComponentName(context, LocalActivationService.class), 0).exported);
            verify(!context.getPackageManager().getReceiverInfo(new android.content.ComponentName(context, LocalPairingReceiver.class), 0).exported);
            try { Class.forName("rikka.shizuku.Shizuku"); throw new IllegalStateException("Dépendance supplémentaire présente"); }
            catch (ClassNotFoundException expected) { checks++; }
            LocalAdbIdentity identity = LocalAdbIdentity.load(context);
            verify(identity.privateKey.getAlgorithm().equals("RSA"));
            verify(java.util.Arrays.equals(identity.certificate.getEncoded(), LocalAdbIdentity.load(context).certificate.getEncoded()));
            verify(!context.getSharedPreferences("local-activation-identity", 0).getString("ciphertext", "")
                    .contains(android.util.Base64.encodeToString(identity.privateKey.getEncoded(), android.util.Base64.NO_WRAP)));
            verify(org.conscrypt.Conscrypt.isAvailable());
            io.github.muntashirakon.crypto.spake2.Spake2Context alice = new io.github.muntashirakon.crypto.spake2.Spake2Context(
                    io.github.muntashirakon.crypto.spake2.Spake2Role.Alice, new byte[]{1}, new byte[]{2});
            io.github.muntashirakon.crypto.spake2.Spake2Context bob = new io.github.muntashirakon.crypto.spake2.Spake2Context(
                    io.github.muntashirakon.crypto.spake2.Spake2Role.Bob, new byte[]{2}, new byte[]{1});
            try {
                byte[] first = alice.generateMessage(new byte[]{3, 4, 5});
                byte[] second = bob.generateMessage(new byte[]{3, 4, 5});
                verify(java.util.Arrays.equals(alice.processMessage(second), bob.processMessage(first)));
                result.putBoolean("local_crypto_smoke", true);
            } finally { alice.destroy(); bob.destroy(); }
            if (!KeyVault.configured(context)) {
                try {
                    String sample = "sk-synthetic-not-a-real-api-key-731000000000";
                    KeyVault.save(context, sample);
                    verify(KeyVault.load(context).equals(sample));
                    verify(!context.getSharedPreferences("openai-vault", 0).getString("ciphertext", "").contains(sample));
                } finally { KeyVault.clear(context); }
            } else verify(KeyVault.load(context).startsWith("sk-"));
            if (arguments != null && "true".equals(arguments.getString("live"))) {
                CountDownLatch finished = new CountDownLatch(1);
                String[] message = new String[1];
                runOnMainSync(() -> PhoneAgent.diagnose(value -> { message[0] = value; finished.countDown(); }));
                verify(finished.await(45, TimeUnit.SECONDS));
                result.putString("direct_openai_diagnostic", message[0]);
                verify(message[0] != null && message[0].startsWith("Diagnostic réussi"));
            }
            if (arguments != null && "true".equals(arguments.getString("synthetic"))) {
                result.putAll(SyntheticVoiceCheck.run(direct, http));
                checks++;
            }
            if (arguments != null && "true".equals(arguments.getString("models"))) {
                for (RealtimeModel candidate : RealtimeModel.values()) {
                    Bundle synthetic = SyntheticVoiceCheck.run(new DirectOpenAi(context, http, candidate), http);
                    String prefix = "model_" + candidate.name().toLowerCase(java.util.Locale.ROOT);
                    result.putString(prefix + "_confirmed", synthetic.getString("confirmed_model"));
                    result.putLong(prefix + "_audio_bytes_discarded", synthetic.getLong("synthetic_audio_received_bytes"));
                    result.putLong(prefix + "_input_tokens", synthetic.getLong("synthetic_input_tokens"));
                    result.putLong(prefix + "_output_tokens", synthetic.getLong("synthetic_output_tokens"));
                    checks++;
                }
            }
            if (arguments != null && "true".equals(arguments.getString("workflow"))) {
                for (RealtimeModel candidate : RealtimeModel.values()) {
                    Bundle workflow = SyntheticMissionCheck.run(context, isolated, http, candidate);
                    verify(workflow.getBoolean("readback_before_storage"));
                    verify(workflow.getBoolean("consented_structured_result_saved_in_isolated_store"));
                    verify(workflow.getBoolean("private_marker_not_disclosed_in_this_case"));
                    result.putString("workflow_" + candidate.name().toLowerCase(java.util.Locale.ROOT), "PASSED; synthetic text, private marker withheld, consent before isolated storage; no real call");
                }
            }
            if (arguments != null && "true".equals(arguments.getString("activate"))) {
                verify(LocalActivationService.paired(context));
                runOnMainSync(() -> LocalActivationService.start(context, false));
                for (int attempt = 0; attempt < 180; attempt++) {
                    Thread.sleep(250);
                    if (!LocalActivationService.running() && !LocalActivationService.status().isEmpty()) break;
                }
                result.putString("local_activation", LocalActivationService.status());
                verify(LocalActivationService.activatedLocally(context));
                verify(BridgeProvider.getHelper() != null);
                result.putBoolean("local_activation_confirmed", LocalActivationService.activatedLocally(context));
            }
            result.putInt("checks_passed", checks);
            result.putString("physical_call", "NOT_TESTED; no call, microphone or cellular audio opened");
            finish(android.app.Activity.RESULT_OK, result);
        } catch (Exception error) {
            result.putInt("checks_passed", checks);
            result.putString("failure", error.getClass().getSimpleName());
            finish(android.app.Activity.RESULT_CANCELED, result);
        } finally { http.connectionPool().evictAll(); http.dispatcher().executorService().shutdown(); }
    }
}
