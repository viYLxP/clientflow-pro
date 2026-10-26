package com.clientflow.deal;

/**
 * Pipeline stages, in order. Each stage carries the win probability used for the weighted forecast.
 * مراحل خط المبيعات بالترتيب. كل مرحلة معها احتمال الفوز المستخدم في التوقع المرجّح.
 *
 * <p>Weighted forecast = deal amount × probability. A deal worth 100,000 in PROPOSAL counts as 50,000.</p>
 * <p>التوقع المرجّح = قيمة الصفقة × الاحتمال. صفقة بـ 100,000 في مرحلة العرض تنحسب 50,000.</p>
 */
public enum DealStage {
    LEAD(10),
    QUALIFIED(25),
    PROPOSAL(50),
    NEGOTIATION(75),
    WON(100),
    LOST(0);

    private final int probability;

    DealStage(int probability) {
        this.probability = probability;
    }

    public int probability() {
        return probability;
    }

    // WON and LOST end the deal; the other four are "open" and appear on the pipeline board.
    // WON و LOST تنهي الصفقة؛ والأربع الباقية "مفتوحة" وتظهر في لوحة خط المبيعات.
    public boolean isClosed() {
        return this == WON || this == LOST;
    }
}
