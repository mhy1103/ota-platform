package com.ota.platform.common;

/** 页面上显示的百分比 */
public final class Percent {

    private Percent() {}

    /**
     * part 占 total 的百分比，四舍五入到整数；没到全部不写 100（99.7% 写 99），有一点就不写 0（0.3% 写 1），
     * 免得「有效 9,664 · 提交 9,694」显示成 100%。part 超过 total 时是 100，total ≤ 0 时是 0
     */
    public static int of(long part, long total) {
        if (total <= 0) return 0;
        long v = Math.round(part * 100.0 / total);
        if (v >= 100 && part < total) return 99;
        if (v <= 0 && part > 0) return 1;
        return (int) Math.max(0, Math.min(100, v));
    }
}
