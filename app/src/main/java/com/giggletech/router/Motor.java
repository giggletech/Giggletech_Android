package com.giggletech.router;

/** Port of Leatherjacket's band, velocity deadzone, soft cap and EMA.
 * All output, including the starting pulse, is bounded by the configured power cap. */
public final class Motor {
    private float previous, ema;
    private long last;
    public long lastSignal;
    public int output;
    public float remotePower = 1;
    public int sample(Device d, float proximity, long now) {
        proximity = Math.max(0, Math.min(1, proximity));
        float dt = last == 0 ? 0 : (now - last) / 1_000_000_000f;
        float delta = proximity - previous;
        int max = Math.round(d.power / 100 * remotePower * .66f * d.scale / 100 * 255);
        int tx = 0;
        if (proximity == 0) ema = 0;
        else if (!d.velocity) {
            float normalized = Math.max(0, Math.min(1, (proximity - d.outer) / (d.inner - d.outer)));
            tx = Math.round(max * normalized);
            if (previous == 0 && normalized > 0) tx = Math.max(tx, Math.min(20, max));
        } else {
            boolean inBand = proximity >= d.outer && proximity <= d.inner;
            boolean retreat = delta < 0;
            boolean wasInBand = previous >= d.outer && previous <= d.inner;
            float velocity = 0;
            if (previous > 0 && (inBand || d.pullAway && retreat && wasInBand) && Math.abs(delta) >= .002f && (d.pullAway || delta > 0) && dt > 0) {
                float rate = Math.abs(delta) / Math.max(.001f, dt);
                if (!(retreat && rate > 4)) {
                    velocity = rate * d.sensitivity;
                    if (d.softcap < 100) velocity = d.softcap * velocity / (d.softcap + velocity);
                }
            }
            float alpha = dt <= 0 || d.smoothing <= 0 ? 1 : 1 - (float)Math.exp(-dt / (d.smoothing / 1000));
            ema += alpha * (velocity - ema);
            float cap = d.power / 100 * remotePower;
            tx = Math.round(Math.max(0, cap - .05f) * ema * .05f * .66f * d.scale / 100 * 255);
            if (tx == 0) ema = 0;
        }
        previous = proximity; last = lastSignal = now;
        return output = Math.max(0, Math.min(max, tx));
    }
    public boolean expired(long now, int seconds) { return lastSignal != 0 && now - lastSignal >= seconds * 1_000_000_000L; }
    public void reset() { previous = ema = 0; last = lastSignal = 0; output = 0; }
}
