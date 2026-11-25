package com.ota.platform.settings;

import java.util.List;

/**
 * 内置主题配色、侧栏样式和圆角预设。
 * 主题色用在按钮、选中态、图表上；侧栏默认也跟着主题色走（按主题色的色相算出深一档的渐变，白字在上面都看得清）。
 * 深色模式下强调色换成同色系更亮的一档，按钮文字改成深色；主题色侧栏也压暗一档。
 */
public final class ThemeCatalog {

    private ThemeCatalog() {}

    public record Theme(String id, String name, String accent, String accentHover, String darkAccent, String darkAccentHover) {
        public String getId() { return id; }
        public String getName() { return name; }
        public String getAccent() { return accent; }
        public String getAccentHover() { return accentHover; }
        public String getDarkAccent() { return darkAccent; }
        /** 主题色侧栏的颜色，设置页选主题时实时预览用 */
        public String getSideTop() { return sidebar(accent).top(); }
        public String getSideBottom() { return sidebar(accent).bottom(); }
        public String getSidePill() { return sidebar(accent).pillInk(); }
        public String getSideMenu() { return sidebar(accent).menu(); }
        public String getSideTopDark() { return darkSidebar(accent).top(); }
        public String getSideBottomDark() { return darkSidebar(accent).bottom(); }
        public String getSideMenuDark() { return darkSidebar(accent).menu(); }
    }

    public record Radius(String id, String name, int card, int large, int control, int small) {
        public String getId() { return id; }
        public String getName() { return name; }
        public int getCard() { return card; }
        public int getLarge() { return large; }
        public int getControl() { return control; }
        public int getSmall() { return small; }
    }

    public record SidebarStyle(String id, String name, String desc) {
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDesc() { return desc; }
    }

    /**
     * 卡片、页面背景、按钮、表格的样式。vars 是这一项要用到的全部变量（设置页预览时整组覆盖，切回默认也能还原），
     * darkVars 是深色模式另给的值；生成页面 CSS 时默认项不输出。
     */
    public record Look(String id, String name, String desc, java.util.Map<String, String> vars, java.util.Map<String, String> darkVars) {
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDesc() { return desc; }
        public String getVarsJson() { return json(vars); }
        public String getDarkVarsJson() { return json(darkVars); }
    }

    /** 一键风格：把主题色、侧栏、圆角和四项样式一起选好 */
    public record Preset(String id, String name, String desc, String theme, String sidebar, String radius,
                         String cards, String bg, String buttons, String tables) {
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDesc() { return desc; }
        public String getAccent() { return ThemeCatalog.theme(theme).accent(); }
        public String getSideBottom() {
            String side = ThemeCatalog.sidebarStyle(sidebar).id();
            return side.equals("dark") ? "#18181b" : side.equals("light") ? "#f4f4f5" : ThemeCatalog.theme(theme).getSideBottom();
        }
        public String getPickJson() {
            return json(java.util.Map.of("theme", theme, "sidebar", sidebar, "radius", radius, "cards", cards, "bg", bg, "buttons", buttons, "tables", tables));
        }
    }

    public static final String DEFAULT_WIDTH = "wide";
    public static final String DEFAULT_CARDS = "shadow";
    public static final String DEFAULT_BG = "gray";
    public static final String DEFAULT_BUTTONS = "raised";
    public static final String DEFAULT_TABLES = "lines";

    public static final List<Look> WIDTHS = List.of(
            new Look("wide", "宽", "两边留一点空，大屏也不空", java.util.Map.of("--content-max", "1560px"), java.util.Map.of()),
            new Look("standard", "标准", "内容居中，两边留白多", java.util.Map.of("--content-max", "1240px"), java.util.Map.of()),
            new Look("full", "铺满", "多宽的屏幕都铺满", java.util.Map.of("--content-max", "none"), java.util.Map.of()));

    public static final List<Look> CARDS = List.of(
            new Look("shadow", "轻阴影", "细边框加一点阴影", java.util.Map.of("--card-line", "var(--line)", "--card-shadow", "var(--shadow)"), java.util.Map.of()),
            new Look("line", "描边", "只有细边框，最干净", java.util.Map.of("--card-line", "var(--line)", "--card-shadow", "none"), java.util.Map.of()),
            new Look("float", "浮起", "去掉边框，阴影更立体",
                    java.util.Map.of("--card-line", "transparent", "--card-shadow", "0 1px 3px rgba(16,24,40,.05),0 14px 34px -18px rgba(16,24,40,.26)"),
                    java.util.Map.of("--card-line", "rgba(255,255,255,.06)", "--card-shadow", "0 14px 34px -18px rgba(0,0,0,.75)")));

    public static final List<Look> BACKGROUNDS = List.of(
            new Look("gray", "浅灰", "卡片和底色分得清", java.util.Map.of("--bg", "#f7f7f8", "--page-art", "none", "--page-art-size", "auto"), java.util.Map.of("--bg", "#0e0e10")),
            new Look("white", "纯白", "整页白底，更通透", java.util.Map.of("--bg", "#fff", "--page-art", "none", "--page-art-size", "auto"), java.util.Map.of("--bg", "#0e0e10")),
            new Look("dots", "点阵", "淡淡的点阵底纹",
                    java.util.Map.of("--bg", "#f7f7f8", "--page-art", "radial-gradient(rgba(15,15,20,.1) 1px,transparent 1.4px)", "--page-art-size", "18px 18px"),
                    java.util.Map.of("--bg", "#0e0e10", "--page-art", "radial-gradient(rgba(255,255,255,.07) 1px,transparent 1.4px)")),
            new Look("glow", "光晕", "顶部一层淡淡的主题色",
                    java.util.Map.of("--bg", "#f7f7f8", "--page-art", "radial-gradient(1100px 420px at 78% -160px,var(--accent-soft),transparent 70%),radial-gradient(900px 380px at -8% -140px,var(--accent-tint),transparent 70%)", "--page-art-size", "auto"),
                    java.util.Map.of("--bg", "#0e0e10")));

    public static final List<Look> BUTTONS = List.of(
            new Look("raised", "立体", "纯色带一点高光", java.util.Map.of("--btn-art", "linear-gradient(180deg,rgba(255,255,255,.13),rgba(255,255,255,0))", "--btn-shadow", "0 1px 2px rgba(15,15,20,.16),inset 0 1px 0 rgba(255,255,255,.14)"), java.util.Map.of()),
            new Look("flat", "扁平", "纯色，没有高光和阴影", java.util.Map.of("--btn-art", "none", "--btn-shadow", "none"), java.util.Map.of()),
            new Look("glow", "发光", "渐变加一圈主题色的光", java.util.Map.of("--btn-art", "linear-gradient(180deg,rgba(255,255,255,.26),rgba(255,255,255,0) 72%)", "--btn-shadow", "0 6px 16px -6px var(--accent-line),inset 0 1px 0 rgba(255,255,255,.22)"), java.util.Map.of()));

    public static final List<Look> TABLES = List.of(
            new Look("lines", "分隔线", "行和行之间一条细线", java.util.Map.of("--row-alt", "transparent"), java.util.Map.of()),
            new Look("zebra", "斑马纹", "隔一行加一点底色，长表格好对齐", java.util.Map.of("--row-alt", "rgba(15,15,20,.024)"), java.util.Map.of("--row-alt", "rgba(255,255,255,.028)")));

    /** 一行三个：通用的、稳重的、明快的、有个性的 */
    public static final List<Preset> PRESETS = List.of(
            new Preset("classic", "默认", "靛蓝 · 主题色侧栏 · 轻阴影卡片", "indigo", "theme", "medium", "shadow", "gray", "raised", "lines"),
            new Preset("fresh", "清爽", "天空蓝 · 浅色侧栏 · 描边卡片 · 纯白底", "sky", "light", "medium", "line", "white", "flat", "lines"),
            new Preset("minimal", "极简", "经典黑白 · 浅色侧栏 · 直角 · 纯白底", "mono", "light", "sharp", "line", "white", "flat", "lines"),
            new Preset("business", "商务", "午夜蓝 · 深色侧栏 · 小圆角 · 斑马纹表格", "navy", "dark", "small", "shadow", "gray", "raised", "zebra"),
            new Preset("tech", "科技", "青碧 · 深色侧栏 · 浮起卡片 · 点阵背景", "teal", "dark", "medium", "float", "dots", "glow", "lines"),
            new Preset("steady", "沉稳", "石板灰 · 小圆角 · 描边卡片 · 扁平按钮", "slate", "theme", "small", "line", "gray", "flat", "zebra"),
            new Preset("vivid", "活力", "活力橙 · 大圆角 · 浮起卡片 · 光晕背景", "orange", "theme", "large", "float", "glow", "glow", "lines"),
            new Preset("warm", "暖阳", "琥珀 · 浅色侧栏 · 圆润 · 光晕背景", "amber", "light", "round", "float", "glow", "raised", "lines"),
            new Preset("sweet", "甜美", "玫瑰粉 · 浅色侧栏 · 圆润 · 发光按钮", "pink", "light", "round", "float", "white", "glow", "lines"),
            new Preset("nature", "自然", "翡翠绿 · 大圆角 · 浅灰底 · 斑马纹表格", "green", "theme", "large", "shadow", "gray", "raised", "zebra"),
            new Preset("elegant", "典雅", "暮紫 · 深色侧栏 · 描边卡片 · 纯白底", "dusk", "dark", "medium", "line", "white", "flat", "lines"),
            new Preset("morandi", "莫兰迪", "雾霾蓝 · 浅色侧栏 · 点阵背景 · 扁平按钮", "haze", "light", "large", "line", "dots", "flat", "lines"));

    public static Look look(List<Look> list, String id) {
        return list.stream().filter(l -> l.id().equals(id)).findFirst().orElse(list.get(0));
    }

    public static boolean isLook(List<Look> list, String id) {
        return list.stream().anyMatch(l -> l.id().equals(id));
    }

    /** 四项样式里不是默认的那几项生成的 CSS，接在主题 CSS 后面 */
    public static String lookCss(String width, String cards, String bg, String buttons, String tables) {
        StringBuilder css = new StringBuilder();
        for (Look l : List.of(look(WIDTHS, width), look(CARDS, cards), look(BACKGROUNDS, bg), look(BUTTONS, buttons), look(TABLES, tables))) {
            if (l == WIDTHS.get(0) || l == CARDS.get(0) || l == BACKGROUNDS.get(0) || l == BUTTONS.get(0) || l == TABLES.get(0)) continue;
            css.append(":root{");
            l.vars().forEach((k, v) -> css.append(k).append(':').append(v).append(';'));
            css.append('}');
            if (!l.darkVars().isEmpty()) {
                css.append(":root[data-mode=dark]{");
                l.darkVars().forEach((k, v) -> css.append(k).append(':').append(v).append(';'));
                css.append('}');
            }
        }
        return css.toString();
    }

    private static String json(java.util.Map<String, String> m) {
        StringBuilder s = new StringBuilder("{");
        m.forEach((k, v) -> {
            if (s.length() > 1) s.append(',');
            s.append('"').append(k).append("\":\"").append(v.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        });
        return s.append('}').toString();
    }

    /** 主题色侧栏：上下渐变两端、当前菜单项（白底）上的文字颜色、下拉菜单底色 */
    record Side(String top, String bottom, String pillInk, String menu) {}

    public static final String DEFAULT_THEME = "indigo";
    public static final String DEFAULT_RADIUS = "medium";
    public static final String DEFAULT_SIDEBAR = "theme";

    /**
     * 24 种，一行六个、一行一个色系，顺着色环排：鲜明的冷色、鲜明的暖色、浓郁的深色、柔和的莫兰迪色和中性色。
     * 在 OKLCH 里配的：白字压在主题色上、主题色当文字压在白底上都看得清（对比度至少 3.7），深色模式那一档在深色卡片上至少 5.8；
     * 任意两个颜色的 OKLab 色差都在 0.066 以上（深色模式 0.06 以上），并排放着不会看成一个色。
     */
    public static final List<Theme> THEMES = List.of(
            new Theme("indigo", "靛蓝", "#4f46e5", "#4232ce", "#9396fe", "#aeb3ff"),
            new Theme("blue", "经典蓝", "#2563eb", "#1650d4", "#71affe", "#9dc7fe"),
            new Theme("sky", "天空蓝", "#0885bd", "#0673a3", "#53cefb", "#9ce1fe"),
            new Theme("teal", "青碧", "#058a7e", "#02766c", "#5ad6c0", "#8eead8"),
            new Theme("green", "翡翠绿", "#088e51", "#007a44", "#69d98d", "#97edaf"),
            new Theme("olive", "橄榄绿", "#6a8904", "#5b7604", "#b7d65f", "#d2ed91"),
            new Theme("amber", "琥珀", "#b87501", "#a16600", "#febd56", "#ffddae"),
            new Theme("orange", "活力橙", "#d35804", "#b84c05", "#ff9a67", "#febfa0"),
            new Theme("red", "中国红", "#d62526", "#be0013", "#fe7873", "#ffa39d"),
            new Theme("pink", "玫瑰粉", "#cd3380", "#b71d6f", "#f886bb", "#ffacd0"),
            new Theme("fuchsia", "品红", "#ad2eb6", "#9816a1", "#e48ef2", "#f4afff"),
            new Theme("violet", "紫罗兰", "#7b3de9", "#6a27d2", "#baa0ff", "#cfbffe"),
            new Theme("navy", "午夜蓝", "#193990", "#0c277c", "#849dd7", "#a0b5e5"),
            new Theme("ocean", "墨青", "#056569", "#035256", "#6bb6be", "#91cbd2"),
            new Theme("forest", "墨绿", "#0e653f", "#025332", "#74af92", "#95c4ac"),
            new Theme("brown", "咖啡棕", "#8b532f", "#78431f", "#c49777", "#d6b097"),
            new Theme("wine", "酒红", "#991b3d", "#83012e", "#d17c85", "#df9a9f"),
            new Theme("dusk", "暮紫", "#5a358c", "#4a2479", "#b38ac0", "#c6a5d1"),
            new Theme("haze", "雾霾蓝", "#537290", "#42617d", "#90b3db", "#aecaeb"),
            new Theme("sage", "灰豆绿", "#5a745a", "#4a624a", "#a5c4a5", "#c1dbc1"),
            new Theme("oat", "燕麦", "#8b7755", "#796645", "#d6c5a3", "#ecdec2"),
            new Theme("bean", "豆沙粉", "#995d6b", "#864d5a", "#e2a4b1", "#f3c0ca"),
            new Theme("slate", "石板灰", "#4d5a6d", "#3d4a5c", "#9ea5b0", "#b6bdc6"),
            new Theme("mono", "经典黑白", "#111111", "#2b2b2b", "#ededed", "#ffffff"));

    /** 以前有、现在去掉的主题色：存着旧 id 的按最接近的新颜色显示 */
    private static final java.util.Map<String, String> RETIRED = java.util.Map.of(
            "emerald", "forest", "purple", "violet", "rose", "red", "graphite", "slate");

    public static final List<Radius> RADII = List.of(
            new Radius("sharp", "直角", 0, 0, 0, 0),
            new Radius("small", "小圆角", 6, 8, 5, 4),
            new Radius("medium", "适中", 10, 14, 8, 6),
            new Radius("large", "大圆角", 16, 20, 11, 8),
            new Radius("round", "圆润", 22, 26, 16, 12));

    public static final List<SidebarStyle> SIDEBARS = List.of(
            new SidebarStyle("theme", "主题色", "跟着主题色走"),
            new SidebarStyle("dark", "深色", "黑色侧栏，最沉稳"),
            new SidebarStyle("light", "浅色", "白色侧栏，最清爽"));

    /** 侧栏用到的变量；三套配色在 ui.css 里分别以 --sbt- / --sbd- / --sbl- 开头 */
    private static final List<String> SIDE_VARS = List.of("", "-bg", "-2", "-3", "-line", "-ink", "-ink-2", "-hi",
            "-active", "-active-ink", "-shadow", "-mark", "-mark-in", "-menu", "-menu-line", "-indicator");

    public static Theme theme(String id) {
        String key = id == null ? null : RETIRED.getOrDefault(id, id);
        return THEMES.stream().filter(t -> t.id().equals(key)).findFirst()
                .orElse(THEMES.stream().filter(t -> t.id().equals(DEFAULT_THEME)).findFirst().orElseThrow());
    }

    public static Radius radius(String id) {
        return RADII.stream().filter(r -> r.id().equals(id)).findFirst()
                .orElse(RADII.stream().filter(r -> r.id().equals(DEFAULT_RADIUS)).findFirst().orElseThrow());
    }

    public static SidebarStyle sidebarStyle(String id) {
        return SIDEBARS.stream().filter(s -> s.id().equals(id)).findFirst().orElse(SIDEBARS.get(0));
    }

    public static boolean isTheme(String id) {
        return id != null && (RETIRED.containsKey(id) || THEMES.stream().anyMatch(t -> t.id().equals(id)));
    }

    public static boolean isRadius(String id) {
        return RADII.stream().anyMatch(r -> r.id().equals(id));
    }

    public static boolean isSidebar(String id) {
        return SIDEBARS.stream().anyMatch(s -> s.id().equals(id));
    }

    /** 生成覆盖到 :root 的 CSS 变量：主题色、圆角、主题色侧栏的颜色、默认用哪套侧栏；深色模式另给一套 */
    public static String css(String themeId, String radiusId, String sidebarId) {
        Theme t = theme(themeId);
        Radius r = radius(radiusId);
        Side light = sidebar(t.accent());
        Side dark = darkSidebar(t.accent());
        String prefix = switch (sidebarStyle(sidebarId).id()) {
            case "dark" -> "--sbd";
            case "light" -> "--sbl";
            default -> "--sbt";
        };
        StringBuilder css = new StringBuilder(":root{--accent:").append(t.accent()).append(";--accent-2:").append(t.accentHover())
                .append(";--on-accent:#fff;--accent-soft:").append(rgba(t.accent(), 0.14))
                .append(";--accent-tint:").append(rgba(t.accent(), 0.07)).append(";--accent-line:").append(rgba(t.accent(), 0.32))
                .append(";--accent-ink:").append(t.accent()).append(";--accent-night:").append(t.darkAccent()).append(";--accent-day:").append(t.accent())
                .append(";--r:").append(r.card()).append("px;--r-lg:").append(r.large()).append("px;--r-sm:").append(r.control())
                .append("px;--r-xs:").append(r.small()).append("px")
                .append(";--side-top:").append(light.top()).append(";--side-bottom:").append(light.bottom())
                .append(";--side-pill-ink:").append(light.pillInk()).append(";--side-menu-solid:").append(light.menu())
                .append(";--side-style:").append(sidebarStyle(sidebarId).id());
        for (String v : SIDE_VARS) css.append(";--side").append(v).append(":var(").append(prefix).append(v).append(")");
        css.append("}:root[data-mode=dark]{--accent:").append(t.darkAccent()).append(";--accent-2:").append(t.darkAccentHover())
                .append(";--on-accent:#0b0b0c;--accent-soft:").append(rgba(t.darkAccent(), 0.22))
                .append(";--accent-tint:").append(rgba(t.darkAccent(), 0.1)).append(";--accent-line:").append(rgba(t.darkAccent(), 0.4))
                .append(";--accent-ink:").append(t.darkAccent())
                .append(";--side-top:").append(dark.top()).append(";--side-bottom:").append(dark.bottom())
                .append(";--side-menu-solid:").append(dark.menu()).append("}");
        return css.toString();
    }

    public static String css(String themeId, String radiusId) {
        return css(themeId, radiusId, DEFAULT_SIDEBAR);
    }

    private static String rgba(String hex, double alpha) {
        int v = Integer.parseInt(hex.substring(1), 16);
        return "rgba(" + ((v >> 16) & 0xFF) + "," + ((v >> 8) & 0xFF) + "," + (v & 0xFF) + "," + alpha + ")";
    }

    // ---------- 主题色侧栏的颜色：在 OKLCH 里保留色相，统一压到一样的明度，任何主题色上白字都清楚 ----------

    /** 深一档、彩度收着一点的同色系（像午夜蓝、墨绿），不会喧宾夺主 */
    static Side sidebar(String accent) {
        double[] lch = oklch(accent);
        double l = Math.max(0.24, Math.min(0.33, lch[0] - 0.12));
        double c = Math.min(lch[1], 0.15) * 0.6;
        String top = hex(l, c, lch[2]);
        String bottom = hex(Math.max(0.17, l - 0.075), c * 0.85, lch[2]);
        String menu = hex(Math.max(0.15, l - 0.11), c * 0.75, lch[2]);
        String pillInk = lch[0] > 0.62 ? top : accent;
        return new Side(top, bottom, pillInk, menu);
    }

    static Side darkSidebar(String accent) {
        double[] lch = oklch(accent);
        double l = Math.max(0.19, Math.min(0.26, lch[0] - 0.16));
        double c = Math.min(lch[1], 0.15) * 0.45;
        return new Side(hex(l, c, lch[2]), hex(Math.max(0.14, l - 0.06), c * 0.85, lch[2]), "#ffffff",
                hex(Math.max(0.13, l - 0.08), c * 0.7, lch[2]));
    }

    /** #rrggbb → {L, C, h(弧度)} */
    static double[] oklch(String hex) {
        int v = Integer.parseInt(hex.substring(1), 16);
        double r = lin(((v >> 16) & 0xFF) / 255.0), g = lin(((v >> 8) & 0xFF) / 255.0), b = lin((v & 0xFF) / 255.0);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        double L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
        double A = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
        double B = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
        return new double[]{L, Math.hypot(A, B), Math.atan2(B, A)};
    }

    /** OKLCH → #rrggbb；超出 sRGB 时逐步降低彩度 */
    static String hex(double L, double C, double h) {
        for (double c = C; ; c -= 0.004) {
            double[] rgb = srgb(L, Math.max(0, c), h);
            if (c <= 0 || (inGamut(rgb))) {
                StringBuilder out = new StringBuilder("#");
                for (double x : rgb) out.append(String.format("%02x", (int) Math.round(Math.max(0, Math.min(1, x)) * 255)));
                return out.toString();
            }
        }
    }

    private static boolean inGamut(double[] rgb) {
        for (double x : rgb) if (x < -0.0005 || x > 1.0005) return false;
        return true;
    }

    private static double[] srgb(double L, double C, double h) {
        double A = C * Math.cos(h), B = C * Math.sin(h);
        double l = Math.pow(L + 0.3963377774 * A + 0.2158037573 * B, 3);
        double m = Math.pow(L - 0.1055613458 * A - 0.0638541728 * B, 3);
        double s = Math.pow(L - 0.0894841775 * A - 1.2914855480 * B, 3);
        return new double[]{
                gam(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
                gam(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
                gam(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)};
    }

    private static double lin(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double gam(double c) {
        return c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(Math.max(c, 0), 1 / 2.4) - 0.055;
    }
}
