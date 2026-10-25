package live.pharmaradio.reponsevoice.autonome;

import android.os.Bundle;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

final class SyntheticVoiceCheck {
    static Bundle run(DirectOpenAi direct, OkHttpClient http) throws Exception {
        JSONObject token = new JSONObject(direct.localRequest("/api/call-token", new JSONObject()));
        CountDownLatch finished = new CountDownLatch(1);
        AtomicLong bytes = new AtomicLong();
        AtomicReference<String> failure = new AtomicReference<>();
        Bundle result = new Bundle();
        result.putString("requested_model", token.getString("model"));
        Request request = new Request.Builder().url("wss://api.openai.com/v1/realtime?model=" + token.getString("model"))
                .header("Authorization", "Bearer " + token.getString("value")).build();
        WebSocket socket = http.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onMessage(WebSocket connection, String text) {
                try {
                    JSONObject event = new JSONObject(text);
                    String type = event.getString("type");
                    if (type.equals("session.created")) {
                        String actualModel = event.getJSONObject("session").getString("model");
                        result.putString("confirmed_model", actualModel);
                        if (!actualModel.equals(token.getString("model"))) {
                            failure.set("Modèle confirmé différent");
                            finished.countDown();
                            return;
                        }
                        connection.send(new JSONObject().put("type", "response.create").put("response", new JSONObject()
                                .put("instructions", "Ceci est un test synthétique sans appel ni interlocuteur. Dis uniquement : Test autonome réussi. Ne déclenche aucun outil.")).toString());
                    } else if (type.equals("response.output_audio.delta")) {
                        bytes.addAndGet(android.util.Base64.decode(event.getString("delta"), android.util.Base64.DEFAULT).length);
                    } else if (type.equals("response.done")) {
                        JSONObject response = event.getJSONObject("response");
                        if (!response.optString("status").equals("completed")) failure.set("Réponse non terminée");
                        JSONObject usage = response.optJSONObject("usage");
                        if (usage != null) {
                            result.putLong("synthetic_input_tokens", usage.optLong("input_tokens"));
                            result.putLong("synthetic_output_tokens", usage.optLong("output_tokens"));
                        }
                        finished.countDown();
                    } else if (type.equals("error")) {
                        failure.set("Erreur Realtime");
                        finished.countDown();
                    }
                } catch (Exception error) {
                    failure.set("Événement invalide");
                    finished.countDown();
                }
            }

            @Override
            public void onFailure(WebSocket connection, Throwable error, Response response) {
                failure.set("Connexion interrompue");
                finished.countDown();
            }
        });
        try {
            if (!finished.await(40, TimeUnit.SECONDS) || failure.get() != null || bytes.get() == 0) {
                throw new IllegalStateException("Test audio synthétique non confirmé");
            }
            result.putLong("synthetic_audio_received_bytes", bytes.get());
            result.putString("synthetic_audio", "Received and discarded; no playback, microphone or phone call");
            return result;
        } finally { socket.cancel(); }
    }
}
