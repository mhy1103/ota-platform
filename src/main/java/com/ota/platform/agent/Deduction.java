package com.ota.platform.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 扣量档位：当天的有效激活落在哪个档位，就按该档位的比例扣 */
public class Deduction {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final Deduction NONE = new Deduction(List.of());

    private final List<Tier> tiers;

    private Deduction(List<Tier> tiers) {
        this.tiers = tiers;
    }

    public static Deduction parse(String json) {
        if (json == null || json.isBlank()) return NONE;
        try {
            List<Tier> list = MAPPER.readValue(json, new TypeReference<>() {});
            list.sort(Comparator.comparingInt(t -> t.min));
            return new Deduction(list);
        } catch (Exception e) {
            return NONE;
        }
    }

    /** 统一写法的 JSON（「到」不限一律写 null），用来比较两次的档位是不是一样；不扣量是 null */
    public static String normalize(String json) {
        Deduction d = parse(json);
        if (!d.hasTiers()) return null;
        List<Tier> out = new ArrayList<>();
        for (Tier t : d.tiers) out.add(new Tier(t.min, t.max == null || t.max <= 0 ? null : t.max, Math.max(0, Math.min(100, t.ratio))));
        return toJson(out);
    }

    /** 表单里的档位：「到」留空表示不限；「从」或比例没填的行忽略。没有档位返回 null（不扣量） */
    public static String fromForm(String[] mins, String[] maxes, String[] ratios) {
        if (mins == null || ratios == null) return null;
        List<Tier> tiers = new ArrayList<>();
        for (int i = 0; i < mins.length && i < ratios.length; i++) {
            int min = toInt(mins[i]);
            int max = maxes != null && i < maxes.length ? toInt(maxes[i]) : 0;
            int ratio = toInt(ratios[i]);
            if (min <= 0 || ratio < 0) continue;
            tiers.add(new Tier(min, max > 0 ? max : null, Math.min(100, ratio)));
        }
        return tiers.isEmpty() ? null : toJson(tiers);
    }

    private static int toInt(String s) {
        try {
            return s == null || s.isBlank() ? -1 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String toJson(List<Tier> tiers) {
        try {
            return MAPPER.writeValueAsString(tiers);
        } catch (Exception e) {
            return "[]";
        }
    }

    public boolean hasTiers() { return !tiers.isEmpty(); }

    /** 给定当天真实数，返回扣量后的数（代理看到的） */
    public int credited(int realCount) {
        return realCount - deducted(realCount, realCount);
    }

    /** 这 n 条里扣几条：档位按 basis 选（当天到这时候一共有多少），比例乘在 n 上，向下取整 */
    public int deducted(int n, int basis) {
        if (n <= 0 || tiers.isEmpty()) return 0;
        return n * ratioFor(basis) / 100;
    }

    /** 给定当天的数，落在哪个档位，返回扣量百分比；哪个档位都不在就不扣 */
    public int ratioFor(int count) {
        for (Tier t : tiers) {
            if (count >= t.min && (t.max == null || t.max == 0 || count <= t.max)) return t.ratio;
        }
        return 0;
    }

    public List<Tier> getTiers() { return tiers; }

    /** 人类可读的扣量说明 */
    public String describe() {
        if (tiers.isEmpty()) return "不扣量";
        StringBuilder sb = new StringBuilder();
        for (Tier t : tiers) {
            if (sb.length() > 0) sb.append("；");
            sb.append(t.min);
            if (t.max != null && t.max > 0) sb.append("–").append(t.max);
            else sb.append("+");
            sb.append("人 扣").append(t.ratio).append("%");
        }
        return sb.toString();
    }

    public static class Tier {
        private int min;
        private Integer max;
        private int ratio;

        public Tier() {}
        public Tier(int min, Integer max, int ratio) { this.min = min; this.max = max; this.ratio = ratio; }

        public int getMin() { return min; }
        public void setMin(int min) { this.min = min; }
        public Integer getMax() { return max; }
        public void setMax(Integer max) { this.max = max; }
        public int getRatio() { return ratio; }
        public void setRatio(int ratio) { this.ratio = ratio; }
    }
}
