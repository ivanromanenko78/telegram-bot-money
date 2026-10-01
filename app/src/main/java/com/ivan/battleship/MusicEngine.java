package com.ivan.battleship;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class MusicEngine {
    private static final int SAMPLE_RATE = 22050;
    private volatile boolean running = false;
    private volatile int trackIndex = 0;
    private volatile float volume = 0.16f;
    private Thread audioThread;
    private AudioTrack audioTrack;

    public void start() {
        if (running) return;
        running = true;
        audioThread = new Thread(this::runAudio, "BattleshipMusic");
        audioThread.start();
    }

    public void stop() {
        running = false;
        if (audioThread != null) {
            try { audioThread.join(400); } catch (InterruptedException ignored) {}
            audioThread = null;
        }
        releaseTrack();
    }

    public void setTrack(int index) {
        if (index < 0) index = 0;
        if (index > 4) index = 4;
        trackIndex = index;
    }

    public int getTrack() {
        return trackIndex;
    }

    public void setVolume(float value) {
        volume = Math.max(0f, Math.min(0.5f, value));
    }

    private void runAudio() {
        int min = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        int bufferSamples = Math.max(1024, min / 2);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();

        audioTrack = new AudioTrack(
                attributes,
                format,
                Math.max(min, bufferSamples * 2),
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );

        if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            releaseTrack();
            running = false;
            return;
        }

        audioTrack.play();
        short[] buffer = new short[bufferSamples];
        long sampleCursor = 0;

        while (running) {
            int currentTrack = trackIndex;
            for (int i = 0; i < buffer.length; i++) {
                double t = (sampleCursor + i) / (double) SAMPLE_RATE;
                double s = sampleForTrack(currentTrack, t);
                s = Math.max(-1.0, Math.min(1.0, s));
                buffer[i] = (short) (s * 32767.0 * volume);
            }
            audioTrack.write(buffer, 0, buffer.length, AudioTrack.WRITE_BLOCKING);
            sampleCursor += buffer.length;
        }

        releaseTrack();
    }

    private double sampleForTrack(int track, double t) {
        switch (track) {
            case 0: return ocean(t);
            case 1: return sonar(t);
            case 2: return storm(t);
            case 3: return battle(t);
            case 4: return calm(t);
            default: return ocean(t);
        }
    }

    private double ocean(double t) {
        double slow = 0.55 + 0.45 * Math.sin(2.0 * Math.PI * 0.09 * t);
        return slow * (
                0.45 * sin(110, t) +
                0.30 * sin(164.81, t) +
                0.20 * sin(220, t)
        );
    }

    private double sonar(double t) {
        double hum = 0.35 * sin(82.41, t) + 0.18 * sin(123.47, t);
        double cycle = t % 2.4;
        double pingEnv = cycle < 0.32 ? Math.exp(-cycle * 9.0) : 0.0;
        double ping = pingEnv * (0.85 * sin(880, t) + 0.35 * sin(1320, t));
        return hum + ping;
    }

    private double storm(double t) {
        double beat = 0.45 + 0.55 * Math.pow(Math.max(0.0, Math.sin(2.0 * Math.PI * 1.8 * t)), 5.0);
        double bass = 0.55 * sin(73.42, t) + 0.30 * sin(110, t);
        double wind = 0.16 * Math.sin(2.0 * Math.PI * 0.23 * t) * sin(293.66, t);
        return beat * bass + wind;
    }

    private double battle(double t) {
        double step = Math.floor((t * 4.0) % 8.0);
        double[] notes = {110, 130.81, 146.83, 164.81, 146.83, 196.00, 164.81, 130.81};
        double note = notes[(int) step];
        double local = (t * 4.0) % 1.0;
        double env = Math.exp(-local * 2.8);
        double kick = Math.pow(Math.max(0.0, Math.sin(2.0 * Math.PI * 2.0 * t)), 8.0);
        return env * (0.55 * sin(note, t) + 0.25 * sin(note * 2.0, t)) + 0.28 * kick * sin(55, t);
    }

    private double calm(double t) {
        double step = Math.floor((t * 1.5) % 5.0);
        double[] notes = {130.81, 146.83, 164.81, 196.00, 220.00};
        double note = notes[(int) step];
        double local = (t * 1.5) % 1.0;
        double env = 0.45 + 0.55 * Math.sin(Math.PI * Math.min(1.0, local));
        return env * (
                0.36 * sin(note, t) +
                0.22 * sin(note * 1.5, t) +
                0.12 * sin(note * 2.0, t)
        );
    }

    private double sin(double frequency, double t) {
        return Math.sin(2.0 * Math.PI * frequency * t);
    }

    private void releaseTrack() {
        if (audioTrack != null) {
            try { audioTrack.pause(); } catch (Exception ignored) {}
            try { audioTrack.flush(); } catch (Exception ignored) {}
            try { audioTrack.stop(); } catch (Exception ignored) {}
            try { audioTrack.release(); } catch (Exception ignored) {}
            audioTrack = null;
        }
    }
}
