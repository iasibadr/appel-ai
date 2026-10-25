package live.pharmaradio.reponsevoice.autonome;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.AttributionSource;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.os.Looper;
import android.os.Process;
import org.json.JSONObject;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@android.annotation.TargetApi(33)
public final class ShellAudioBridge {
    private static final int RATE = 24000;
    private static final int FRAME = 960;

    public static void main(String[] arguments) throws Exception {
        if (Process.myUid() != 2000) throw new SecurityException("Shell requis");
        Looper.prepareMainLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context shellContext = ((Context) activityThread.getMethod("getSystemContext").invoke(thread))
                .createPackageContext("com.android.shell", 0);
        java.lang.reflect.Field operationPackage = shellContext.getClass().getDeclaredField("mOpPackageName");
        operationPackage.setAccessible(true);
        operationPackage.set(shellContext, "com.android.shell");
        java.lang.reflect.Field attribution = shellContext.getClass().getDeclaredField("mAttributionSource");
        attribution.setAccessible(true);
        attribution.set(shellContext, new AttributionSource.Builder(Process.myUid()).setPackageName("com.android.shell").build());
        Context context = new ContextWrapper(shellContext) {
            @Override
            public Context getApplicationContext() { return this; }
        };
        int appUid = context.getPackageManager().getApplicationInfo("live.pharmaradio.reponsevoice.autonome", 0).uid;
        AudioManager audio = AudioManager.class.getConstructor(Context.class).newInstance(context);
        if (Arrays.asList(arguments).contains("--activate-only")) {
            BootstrapService.activate(context);
            System.out.println("VOICE_LOCAL_ACTIVATED");
            System.exit(0);
        }
        if (Arrays.asList(arguments).contains("--check-context")) {
            if (audio.getMode() != AudioManager.MODE_NORMAL) throw new IllegalStateException("Audio actif ; controle annule");
            audio.setMode(AudioManager.MODE_NORMAL);
            System.out.println("Audio context verified: shell attribution, setMode NORMAL; no capture or playback");
            System.exit(0);
        }
        AtomicBoolean busy = new AtomicBoolean();
        Binder endpoint = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (Binder.getCallingUid() != appUid) throw new SecurityException("APK non autorisee");
                data.enforceInterface(BridgeProvider.DESCRIPTOR);
                long identity = Binder.clearCallingIdentity();
                try {
                    boolean available = (Boolean) AudioManager.class.getMethod("isPstnCallAudioInterceptable").invoke(audio);
                    if (code == 1) {
                        reply.writeNoException();
                        reply.writeString(new JSONObject().put("available", available).put("uid", Process.myUid()).toString());
                        return true;
                    }
                    if (code == 3) {
                        ParcelFileDescriptor[] pair = ParcelFileDescriptor.createSocketPair();
                        new Thread(() -> {
                            try (ParcelFileDescriptor connection = pair[1]) {
                                DataInputStream input = new DataInputStream(new ParcelFileDescriptor.AutoCloseInputStream(connection));
                                DataOutputStream output = new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(connection));
                                output.writeLong(input.readLong());
                                output.flush();
                            } catch (Exception error) {
                                System.err.println("FD check: " + error.getClass().getSimpleName());
                            }
                        }, "call-audio-fd-check").start();
                        reply.writeNoException();
                        reply.writeTypedObject(pair[0], Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
                        pair[0].close();
                        return true;
                    }
                    if (code != 2 || !available || audio.getMode() != AudioManager.MODE_IN_CALL || !busy.compareAndSet(false, true)) {
                        throw new IllegalStateException("Audio telephonique indisponible ou occupe");
                    }
                    ParcelFileDescriptor[] pair;
                    try { pair = ParcelFileDescriptor.createSocketPair(); }
                    catch (Exception error) { busy.set(false); throw error; }
                    new Thread(() -> {
                        try (ParcelFileDescriptor connection = pair[1]) {
                            bridge(context, audio, connection,
                                    new DataInputStream(new ParcelFileDescriptor.AutoCloseInputStream(connection)),
                                    new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(connection)));
                        } catch (Exception error) {
                            Throwable cause = error.getCause() == null ? error : error.getCause();
                            System.err.println("Audio bridge: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                            cause.printStackTrace(System.err);
                        } finally { busy.set(false); }
                    }, "call-audio-session").start();
                    reply.writeNoException();
                    reply.writeTypedObject(pair[0], Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
                    pair[0].close();
                    return true;
                } catch (Exception error) {
                    reply.writeException(new IllegalStateException(error.getClass().getSimpleName() + ": " + error.getMessage()));
                    return true;
                } finally { Binder.restoreCallingIdentity(identity); }
            }
        };
        Runnable publish = () -> {
            Object manager = null;
            IBinder providerToken = new Binder();
            try {
                Bundle extras = new Bundle();
                extras.putBinder("bridge", endpoint);
                Class<?> managerInterface = Class.forName("android.app.IActivityManager");
                manager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
                Object holder = managerInterface.getMethod("getContentProviderExternal", String.class, int.class, IBinder.class, String.class)
                        .invoke(manager, BridgeProvider.AUTHORITY, 0, providerToken, "reponsevoice");
                if (holder == null) throw new IllegalStateException("Provider introuvable");
                Object provider = holder.getClass().getField("provider").get(holder);
                Class.forName("android.content.IContentProvider")
                        .getMethod("call", AttributionSource.class, String.class, String.class, String.class, Bundle.class)
                        .invoke(provider, new AttributionSource.Builder(Process.myUid()).setPackageName("com.android.shell").build(),
                                BridgeProvider.AUTHORITY, "publish", null, extras);
            } catch (Exception error) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                System.err.println("Binder publication: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            } finally {
                if (manager != null) {
                    try {
                        Class.forName("android.app.IActivityManager")
                                .getMethod("removeContentProviderExternalAsUser", String.class, IBinder.class, int.class)
                                .invoke(manager, BridgeProvider.AUTHORITY, providerToken, 0);
                    } catch (Exception ignored) { }
                }
            }
        };
        publish.run();
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(5000); publish.run(); }
                catch (InterruptedException error) { return; }
            }
        }, "call-audio-publisher").start();
        System.out.println("Reponse Voice audio helper ready (Binder)");
        Class.forName("com.android.internal.os.BinderInternal").getMethod("joinThreadPool").invoke(null);
    }

    private static void shutdown(ParcelFileDescriptor connection) {
        try { Os.shutdown(connection.getFileDescriptor(), OsConstants.SHUT_RDWR); } catch (Exception ignored) { }
        try { connection.close(); } catch (Exception ignored) { }
    }

    private static void bridge(Context context, AudioManager audio, ParcelFileDescriptor socket,
            DataInputStream input, DataOutputStream output) throws Exception {
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Permission audio shell absente");
        }
        if (audio.getMode() != AudioManager.MODE_IN_CALL) {
            output.writeUTF("{\"error\":\"Aucun appel mobile actif\"}");
            output.flush();
            return;
        }
        AudioRecord record = null;
        AudioTrack track = null;
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong capturedBytes = new AtomicLong();
        AtomicLong writtenBytes = new AtomicLong();
        boolean redirected = false;
        AtomicLong lastCommand = new AtomicLong(android.os.SystemClock.elapsedRealtime());
        ArrayBlockingQueue<byte[]> playback = new ArrayBlockingQueue<>(1000);
        Thread captureThread = null;
        Thread playbackThread = null;
        Thread watchdog = null;
        try {
            AudioFormat inputFormat = new AudioFormat.Builder().setSampleRate(RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
            AudioFormat outputFormat = new AudioFormat.Builder().setSampleRate(RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
            AudioAttributes.Builder captureAttributes = new AudioAttributes.Builder();
            AudioAttributes.Builder.class.getMethod("setInternalCapturePreset", int.class)
                    .invoke(captureAttributes, MediaRecorder.AudioSource.VOICE_DOWNLINK);
            AudioRecord.Builder recordBuilder = new AudioRecord.Builder().setAudioFormat(inputFormat)
                    .setBufferSizeInBytes(Math.max(FRAME * 8, AudioRecord.getMinBufferSize(RATE,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)));
            AudioRecord.Builder.class.getMethod("setContext", Context.class).invoke(recordBuilder, context);
            AudioRecord.Builder.class.getMethod("setAudioAttributes", AudioAttributes.class)
                    .invoke(recordBuilder, captureAttributes.build());
            AudioRecord.Builder.class.getMethod("setCallRedirectionMode", int.class).invoke(recordBuilder, 1);
            record = recordBuilder.build();
            AudioAttributes.Builder playbackAttributes = new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
            AudioAttributes.Builder.class.getMethod("setSystemUsage", int.class).invoke(playbackAttributes, 17);
            AudioTrack.Builder trackBuilder = new AudioTrack.Builder().setAudioFormat(outputFormat)
                    .setAudioAttributes(playbackAttributes.build()).setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(Math.max(FRAME * 4, AudioTrack.getMinBufferSize(RATE,
                            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)));
            AudioTrack.Builder.class.getMethod("setContext", Context.class).invoke(trackBuilder, context);
            AudioTrack.Builder.class.getMethod("setCallRedirectionMode", int.class).invoke(trackBuilder, 1);
            track = trackBuilder.build();
            if (record.getState() != AudioRecord.STATE_INITIALIZED || track.getState() != AudioTrack.STATE_INITIALIZED
                    || record.getAudioSource() != MediaRecorder.AudioSource.VOICE_DOWNLINK) {
                throw new IllegalStateException("Routes telephoniques non initialisees");
            }
            record.startRecording();
            track.play();
            audio.setMode(AudioManager.MODE_CALL_REDIRECT);
            redirected = true;
            if (audio.getMode() != AudioManager.MODE_CALL_REDIRECT) throw new IllegalStateException("Mode de redirection refuse");
            System.out.println("Call redirection active: source=" + record.getAudioSource()
                    + ", usage=" + track.getAudioAttributes().getUsage());
            output.writeUTF("{\"ready\":true,\"rate\":24000,\"source\":3}");
            output.flush();
            AudioRecord activeRecord = record;
            AudioTrack activeTrack = track;
            captureThread = new Thread(() -> {
                byte[] buffer = new byte[FRAME];
                try {
                    while (running.get()) {
                        int length = activeRecord.read(buffer, 0, buffer.length);
                        if (length <= 0 || (length & 1) != 0) throw new IllegalStateException("Capture interrompue");
                        capturedBytes.addAndGet(length);
                        output.writeInt(length);
                        output.write(buffer, 0, length);
                        output.flush();
                    }
                } catch (Exception error) {
                    running.set(false);
                    shutdown(socket);
                }
            }, "call-downlink");
            playbackThread = new Thread(() -> {
                try {
                    while (running.get()) {
                        byte[] frame = playback.poll(1, TimeUnit.SECONDS);
                        if (frame == null) continue;
                        synchronized (activeTrack) {
                            int offset = 0;
                            while (offset < frame.length && running.get()) {
                                int written = activeTrack.write(frame, offset, frame.length - offset);
                                if (written <= 0) throw new IllegalStateException("Injection interrompue");
                                writtenBytes.addAndGet(written);
                                offset += written;
                            }
                        }
                    }
                } catch (Exception error) {
                    running.set(false);
                    shutdown(socket);
                }
            }, "call-uplink");
            captureThread.start();
            playbackThread.start();
            watchdog = new Thread(() -> {
                while (running.get()) {
                    try { Thread.sleep(1000); } catch (InterruptedException error) { return; }
                    if (android.os.SystemClock.elapsedRealtime() - lastCommand.get() > 15000) {
                        running.set(false);
                        shutdown(socket);
                    }
                }
            }, "call-audio-watchdog");
            watchdog.start();
            while (running.get()) {
                int length = input.readInt();
                lastCommand.set(android.os.SystemClock.elapsedRealtime());
                if (length == -1) break;
                if (length == -2) {
                    playback.clear();
                    synchronized (track) {
                        track.pause();
                        track.flush();
                        track.play();
                    }
                    continue;
                }
                if (length == -3) continue;
                if (length <= 0 || length > 65536 || (length & 1) != 0) throw new IllegalArgumentException("Trame invalide");
                byte[] buffer = new byte[length];
                input.readFully(buffer);
                for (int offset = 0; offset < length; offset += FRAME) {
                    if (!playback.offer(Arrays.copyOfRange(buffer, offset, Math.min(length, offset + FRAME)))) {
                        throw new IllegalStateException("Lecture saturee");
                    }
                }
            }
        } finally {
            running.set(false);
            if (record != null) {
                try { record.stop(); } catch (Exception ignored) { }
            }
            shutdown(socket);
            if (watchdog != null) watchdog.interrupt();
            if (playbackThread != null) playbackThread.interrupt();
            if (captureThread != null) captureThread.join(1500);
            if (playbackThread != null) playbackThread.join(1500);
            if (record != null) record.release();
            if (track != null) track.release();
            System.out.println("Call audio stopped: captured=" + capturedBytes.get() + ", written=" + writtenBytes.get());
            if (redirected) audio.setMode(AudioManager.MODE_NORMAL);
        }
    }
}
