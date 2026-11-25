// 按 Java 的规则取值、比较、拼接、打印（沿用原来 Thymeleaf 模板的表达式规则）：页面组件都通过 $h 调到这里，
// 这样服务端给的数据显示出来和 Java 里的一个字符都不差（12.0 还是 12、null 打印成什么、整数除法……）

/** Java 的 double / BigDecimal：值用来算，打印用 Java 的格式 */
export class JNum {
    constructor(v, s) {
        this.v = v;
        this.s = s;
    }
    valueOf() { return this.v; }
    toString() { return this.s != null ? this.s : jdouble(this.v); }
}

/** LocalDateTime / Instant：ms 用来比较和换算时区，s 是 Java 的 toString */
export class JTime {
    constructor(ms, s) {
        this.ms = ms;
        this.s = s;
    }
    valueOf() { return this.ms; }
    toString() { return this.s; }
}

/** 带属性的枚举：比较、打印都按名字 */
export class JEnum {
    constructor(name, text, props) {
        Object.assign(this, props);
        this.$e = name;
        this.$s = text;
    }
    valueOf() { return this.$e; }
    toString() { return this.$s != null ? this.$s : this.$e; }
}

/** Java 的 Map：保持服务端的顺序，键一律按字符串比（Map<Long, X> 用数字 id 也能取到） */
export class JMap extends Map {
    get(k) { return super.get(String(k)); }
    has(k) { return super.has(String(k)); }
}

export function revive(key, v) {
    if (v === null || typeof v !== 'object' || Array.isArray(v)) return v;
    if ('$m' in v) return new JMap(v.$m.map(function (e) { return [String(e[0]), e[1]]; }));
    if ('$d' in v) return new JNum(Number(v.$d), v.$d);
    if ('$t' in v) return new JTime(v.$t, v.s);
    if ('$e' in v) {
        var props = {};
        for (var k in v) if (k !== '$e' && k !== '$s') props[k] = v[k];
        return new JEnum(v.$e, v.$s, props);
    }
    return v;
}

// ---------- 打印 ----------

/** Double.toString：整数也带 .0，很小、很大的用科学计数法 */
export function jdouble(v) {
    if (Number.isNaN(v)) return 'NaN';
    if (v === Infinity) return 'Infinity';
    if (v === -Infinity) return '-Infinity';
    if (v === 0) return Object.is(v, -0) ? '-0.0' : '0.0';
    var a = Math.abs(v);
    if (a >= 1e-3 && a < 1e7) {
        var s = String(v);
        return s.indexOf('.') >= 0 ? s : s + '.0';
    }
    var parts = v.toExponential().split('e');
    var mant = parts[0].indexOf('.') >= 0 ? parts[0] : parts[0] + '.0';
    return mant + 'E' + (parts[1].charAt(0) === '+' ? parts[1].slice(1) : parts[1]);
}

/** String.valueOf */
export function str(v) {
    if (v === null || v === undefined) return 'null';
    if (typeof v === 'string') return v;
    if (typeof v === 'number') return Number.isInteger(v) ? String(v) : jdouble(v);
    if (typeof v === 'boolean') return v ? 'true' : 'false';
    if (Array.isArray(v)) return '[' + v.map(str).join(', ') + ']';
    if (v instanceof JMap) {
        var out = [];
        v.forEach(function (x, k) { out.push(k + '=' + str(x)); });
        return '{' + out.join(', ') + '}';
    }
    return String(v);
}

/** th:text：null 不输出 */
export function text(v) {
    return v === null || v === undefined ? '' : str(v);
}

/** 属性值：null 和空字符串都去掉属性（Thymeleaf 的 th:title、th:data-* 这类） */
export function attrValue(v) {
    var s = v === null || v === undefined ? '' : str(v);
    return s === '' ? null : s;
}

/** th:href、th:value 这几个：null 输出空值，属性保留 */
export function attrKeep(v) {
    return v === null || v === undefined ? '' : str(v);
}

// ---------- 判断、比较 ----------

/** Thymeleaf 的真假：null 假；数字非 0 真；字符串除了 false / off / no 都真（空字符串也真） */
export function truthy(v) {
    if (v === null || v === undefined) return false;
    if (typeof v === 'boolean') return v;
    if (typeof v === 'number') return v !== 0;
    if (v instanceof JNum) return v.v !== 0;
    if (typeof v === 'string') {
        var s = v.toLowerCase();
        return !(s === 'false' || s === 'off' || s === 'no');
    }
    return true;
}

function isNum(v) {
    return typeof v === 'number' || v instanceof JNum;
}

export function eq(a, b) {
    if (a === b) return true;
    if (a === null || a === undefined || b === null || b === undefined) {
        return (a === null || a === undefined) && (b === null || b === undefined);
    }
    if (isNum(a) && isNum(b)) return +a === +b;
    if (a instanceof JTime && b instanceof JTime) return a.ms === b.ms;
    return str(a) === str(b);
}

function cmp(a, b) {
    var an = a === null || a === undefined, bn = b === null || b === undefined;
    if (an || bn) return an && bn ? 0 : an ? -1 : 1;
    if (typeof a === 'string' && typeof b === 'string') return a < b ? -1 : a > b ? 1 : 0;
    var x = +a, y = +b;
    return x < y ? -1 : x > y ? 1 : 0;
}
export function lt(a, b) { return cmp(a, b) < 0; }
export function le(a, b) { return cmp(a, b) <= 0; }
export function gt(a, b) { return cmp(a, b) > 0; }
export function ge(a, b) { return cmp(a, b) >= 0; }

/** SpEL 的 ?:：null 和空字符串都取后面的 */
export function elvis(a, b) {
    return a === null || a === undefined || a === '' ? b : a;
}

/** Thymeleaf 的 ?:：只有 null 取后面的 */
export function dflt(a, b) {
    return a === null || a === undefined ? b : a;
}

// ---------- 算术 ----------

function arith(a, b, f) {
    var r = f(+a, +b);
    return a instanceof JNum || b instanceof JNum ? new JNum(r) : r;
}

/** 有一边是字符串就拼接（null 拼成 "null"），否则相加 */
export function add(a, b) {
    if (isNum(a) && isNum(b)) return arith(a, b, function (x, y) { return x + y; });
    return str(a) + str(b);
}
export function sub(a, b) { return arith(a, b, function (x, y) { return x - y; }); }
export function mul(a, b) { return arith(a, b, function (x, y) { return x * y; }); }
export function mod(a, b) { return arith(a, b, function (x, y) { return x % y; }); }
export function neg(a) { return a instanceof JNum ? new JNum(-a.v) : -a; }

/** SpEL 的除法：两个整数就是整数除法 */
export function div(a, b) {
    if (typeof a === 'number' && typeof b === 'number') return b === 0 ? NaN : Math.trunc(a / b);
    return new JNum(+a / +b);
}

/** Thymeleaf 表达式层的除法用 BigDecimal：除得尽就是精确值，除不尽保留 10 位四舍五入 */
export function tdiv(a, b) {
    var x = +a, y = +b, r = x / y;
    if (Number.isInteger(r)) return r;
    var exact = Number(r.toPrecision(15));
    var s = String(exact);
    var decimals = s.indexOf('.') >= 0 ? s.length - s.indexOf('.') - 1 : 0;
    if (decimals <= 10 && Math.abs(exact * y - x) < 1e-9) return new JNum(exact, s);
    return new JNum(r, halfUp(String(r), 10));
}

export function double(text) {
    return new JNum(Number(text), text);
}

// ---------- 小数格式 ----------

function digitsOf(s) {
    // "123.456" → { neg, int: "123", frac: "456" }；不处理科学计数法以外的写法
    var neg = s.charAt(0) === '-';
    if (neg) s = s.slice(1);
    if (/e/i.test(s)) s = Number(s).toFixed(20).replace(/0+$/, '').replace(/\.$/, '');
    var dot = s.indexOf('.');
    return { neg: neg, int: dot < 0 ? s : s.slice(0, dot), frac: dot < 0 ? '' : s.slice(dot + 1) };
}

function roundDigits(d, places, up) {
    var frac = d.frac;
    if (frac.length <= places) return { neg: d.neg, int: d.int, frac: frac + '0'.repeat(places - frac.length) };
    var keep = d.int + frac.slice(0, places);
    if (up(frac.slice(places), keep)) {
        var arr = keep.split(''), i = arr.length - 1;
        while (i >= 0) {
            if (arr[i] === '9') { arr[i] = '0'; i--; } else { arr[i] = String(+arr[i] + 1); break; }
        }
        if (i < 0) arr.unshift('1');
        keep = arr.join('');
    }
    var intLen = keep.length - places;
    return { neg: d.neg, int: keep.slice(0, intLen) || '0', frac: keep.slice(intLen) };
}

function joinDigits(r, places) {
    var zero = /^0*$/.test(r.int + r.frac);
    return (r.neg && !zero ? '-' : '') + (r.int.replace(/^0+(?=\d)/, '') || '0') + (places > 0 ? '.' + r.frac : '');
}

/** 四舍五入（看最短的十进制表示，和 Java String.format("%.Nf") 一样） */
export function halfUp(s, places) {
    var r = roundDigits(digitsOf(s), places, function (rest) { return rest.charAt(0) >= '5'; });
    return joinDigits(r, places);
}

export function formatFixed(v, places) {
    return halfUp(String(+v), places);
}

/** 银行家舍入（看精确的二进制值，和 Java DecimalFormat 默认一样） */
export function halfEven(v, places) {
    var exact = (+v).toFixed(Math.min(100, places + 60)).replace(/0+$/, '');
    var r = roundDigits(digitsOf(exact), places, function (rest, keep) {
        if (rest.charAt(0) > '5') return true;
        if (rest.charAt(0) < '5') return false;
        if (/[1-9]/.test(rest.slice(1))) return true;
        return (+keep.charAt(keep.length - 1)) % 2 === 1;
    });
    return joinDigits(r, places);
}

// ---------- 网址转义（Thymeleaf 的 @{...}、AdminUi 的 URLEncoder） ----------

/** Thymeleaf 链接参数：保留 / ? : @ 和 & = + 以外的分隔符 */
export function escQuery(s) {
    return encodeURIComponent(s).replace(/%(24|2C|3B|3A|40|2F|3F)/g, function (m, h) { return String.fromCharCode(parseInt(h, 16)); });
}

/** Thymeleaf 路径变量 {id} */
export function escSegment(s) {
    return encodeURIComponent(s).replace(/%(24|26|2B|2C|3B|3D|3A|40)/g, function (m, h) { return String.fromCharCode(parseInt(h, 16)); });
}

/** java.net.URLEncoder（表单编码）：空格变 +，* 不转，~ ! ' ( ) 要转 */
export function formEncode(s) {
    return encodeURIComponent(s).replace(/%20/g, '+').replace(/[!'()~]/g, function (c) {
        return '%' + c.charCodeAt(0).toString(16).toUpperCase();
    });
}
