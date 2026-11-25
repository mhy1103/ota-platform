<template>
<dialog class="sc-pick sc-sharer" ref="el" aria-labelledby="ss-title" @close="onClose" @click="onBackdrop">
    <header class="ss-head">
        <span class="ss-mark"><svg class="i"><use href="#i-share"></use></svg></span>
        <span class="ss-head-tx"><b id="ss-title">分享数据大屏</b><small>对方不用登录就能看，和你看到的一模一样，只是不能操作</small></span>
        <button class="ss-x" type="button" title="关闭（esc）" aria-label="关闭" @click="close"><svg class="i"><use href="#i-x"></use></svg></button>
    </header>
    <div class="ss-tabs" role="tablist" aria-label="分享">
        <button type="button" role="tab" :aria-selected="tab === 'new'" :class="{ on: tab === 'new' }" @click="tab = 'new'">新建分享</button>
        <button type="button" role="tab" :aria-selected="tab === 'list'" :class="{ on: tab === 'list' }" @click="showList">分享记录<em v-if="activeCount > 0">{{ activeCount }}</em></button>
        <i class="ss-ink" :class="{ r: tab === 'list' }" aria-hidden="true"></i>
    </div>

    <form class="ss-body" v-if="tab === 'new' && !made" novalidate @submit.prevent="create">
        <div class="ss-field">
            <span class="ss-l">分享内容</span>
            <div class="ss-seg" role="radiogroup" aria-label="分享内容" v-if="!all && count > 1">
                <button type="button" role="radio" :aria-checked="!form.all" :class="{ on: !form.all }" @click="form.all = false"><b translate="no">{{ name }}</b><small>这个 App</small></button>
                <button type="button" role="radio" :aria-checked="form.all" :class="{ on: form.all }" @click="form.all = true"><b>全部 App</b><small>{{ count }} 个合在一起</small></button>
            </div>
            <div class="ss-static" v-if="all || !(count > 1)"><b translate="no">{{ all ? '全部 App' : name }}</b><small v-if="all">{{ count }} 个 App 合在一起 · 要分享单个 App，先切换到那个 App</small></div>
        </div>
        <div class="ss-field">
            <span class="ss-l">有效期</span>
            <div class="ss-chips" role="radiogroup" aria-label="有效期">
                <button v-for="o in HOURS" :key="o.h" type="button" role="radio" :aria-checked="form.hours === o.h" :class="{ on: form.hours === o.h }" @click="form.hours = o.h">{{ o.label }}</button>
            </div>
            <small class="ss-hint">{{ untilText }}</small>
        </div>
        <div class="ss-field">
            <label class="ss-toggle">
                <span class="ss-l">访问密码<small>选填</small></span>
                <span class="ss-switch"><input type="checkbox" v-model="form.lock" @change="onLock"><i></i></span>
            </label>
            <div class="ss-pass" v-if="form.lock">
                <input ref="passEl" class="mono" :type="form.show ? 'text' : 'password'" v-model.trim="form.password" maxlength="32" autocomplete="new-password" spellcheck="false" placeholder="4–32 位" aria-label="访问密码">
                <button type="button" class="ss-ico" :title="form.show ? '隐藏' : '显示'" :aria-label="form.show ? '隐藏密码' : '显示密码'" @click="form.show = !form.show"><svg class="i"><use :href="form.show ? '#i-eye-off' : '#i-eye'"></use></svg></button>
                <button type="button" class="ss-ico" title="随机换一个" aria-label="随机换一个" @click="randomPass"><svg class="i"><use href="#i-refresh"></use></svg></button>
            </div>
            <small class="ss-hint" v-if="!form.lock">不设密码时，拿到链接的人都能看</small>
        </div>
        <div class="ss-field">
            <span class="ss-l">备注<small>选填，只在分享记录里看得到</small></span>
            <input class="ss-input" v-model="form.note" maxlength="40" placeholder="比如：给张总看的" aria-label="备注">
        </div>
        <p class="ss-err" role="alert" v-if="error">{{ error }}</p>
        <div class="ss-foot">
            <button type="submit" class="ss-go" :disabled="busy"><span class="ss-spin" v-if="busy"></span><svg class="i" v-if="!busy"><use href="#i-link"></use></svg><span>生成分享链接</span></button>
        </div>
    </form>

    <div class="ss-body ss-made" v-if="tab === 'new' && made">
        <div class="ss-ok">
            <span class="ss-ok-ic"><svg class="i"><use href="#i-tick"></use></svg></span>
            <span><b>分享链接已生成</b><small>{{ madeMeta }}</small></span>
        </div>
        <div class="ss-made-in">
            <figure class="ss-qr"><img :src="qrSrc" alt="分享链接的二维码" width="128" height="128"><figcaption>手机扫码打开</figcaption></figure>
            <div class="ss-made-tx">
                <span class="ss-l">链接</span>
                <div class="ss-copy"><input class="ss-input mono" :value="made.url" readonly aria-label="分享链接" @focus="$event.target.select()"><button type="button" :class="['ss-ico', { done: copied === 'link' }]" title="复制链接" aria-label="复制链接" @click="copy(made.url, 'link')"><svg class="i"><use :href="copied === 'link' ? '#i-tick' : '#i-copy'"></use></svg></button></div>
                <template v-if="made.password">
                    <span class="ss-l">访问密码</span>
                    <div class="ss-copy"><input class="ss-input mono" :value="made.password" readonly aria-label="访问密码" @focus="$event.target.select()"><button type="button" :class="['ss-ico', { done: copied === 'pass' }]" title="复制密码" aria-label="复制密码" @click="copy(made.password, 'pass')"><svg class="i"><use :href="copied === 'pass' ? '#i-tick' : '#i-copy'"></use></svg></button></div>
                    <small class="ss-hint">密码只在这里显示这一次，记得和链接一起发给对方</small>
                </template>
            </div>
        </div>
        <div class="ss-foot">
            <button type="button" class="ss-ghost" @click="again">再建一个</button>
            <a class="ss-ghost" :href="made.url" target="_blank" rel="noopener"><svg class="i"><use href="#i-external"></use></svg><span>打开看看</span></a>
            <button type="button" class="ss-go" @click="copy(shareText, 'all')"><svg class="i"><use :href="copied === 'all' ? '#i-tick' : '#i-copy'"></use></svg><span>{{ copied === 'all' ? '已复制' : made.password ? '复制链接和密码' : '复制链接' }}</span></button>
        </div>
    </div>

    <div class="ss-body ss-list" v-if="tab === 'list'">
        <div class="ss-filter" v-if="rows.length">
            <button type="button" :class="{ on: filter === 'active' }" @click="filter = 'active'">有效<em>{{ activeCount }}</em></button>
            <button type="button" :class="{ on: filter === 'all' }" @click="filter = 'all'">全部<em>{{ rows.length }}</em></button>
            <span class="ss-filter-note" v-if="owner">超级管理员能看到、取消所有人的分享</span>
        </div>
        <p class="ss-err" role="alert" v-if="listError">{{ listError }}</p>
        <div class="ss-skel" v-if="!loaded && !listError" aria-hidden="true"><i></i><i></i><i></i></div>
        <div class="ss-none" v-if="loaded && !shown.length && !listError">
            <span class="ss-none-ic"><svg class="i"><use href="#i-share"></use></svg></span>
            <b>{{ rows.length ? '没有有效的分享' : '还没有分享过' }}</b>
            <small>{{ rows.length ? '过期了的、取消了的都在「全部」里' : '生成的分享链接都会列在这里，随时可以取消' }}</small>
        </div>
        <ul class="ss-rows" v-if="shown.length">
            <li v-for="r in shown" :key="r.id" :class="['ss-row', r.state]">
                <div class="ss-row-main">
                    <div class="ss-row-top">
                        <b translate="no">{{ r.scope === 'all' ? '全部 App' : r.appName }}</b>
                        <em :class="['ss-state', r.state]">{{ STATE[r.state] }}</em>
                        <span class="ss-lock" v-if="r.locked" title="要输访问密码"><svg class="i"><use href="#i-lock"></use></svg></span>
                    </div>
                    <span class="ss-row-note" v-if="r.note">{{ r.note }}</span>
                    <small class="ss-row-meta">
                        <span :class="{ soon: soon(r) }">{{ expiryText(r) }}</span>
                        <span>{{ r.views ? '打开过 ' + r.views + ' 次' : '还没人打开' }}<template v-if="r.lastViewedAt"> · 最近 {{ ago(r.lastViewedAt) }}</template></span>
                        <span>{{ r.mine ? '' : (r.createdName || r.createdBy) + ' · ' }}{{ dt(r.createdAt) }} 生成</span>
                    </small>
                </div>
                <div class="ss-row-act" v-if="r.state === 'active'">
                    <button type="button" :class="['ss-ico', { done: copied === 'r' + r.id }]" title="复制链接" aria-label="复制链接" @click="copy(origin + r.path, 'r' + r.id)"><svg class="i"><use :href="copied === 'r' + r.id ? '#i-tick' : '#i-copy'"></use></svg></button>
                    <a class="ss-ico" :href="r.path" target="_blank" rel="noopener" title="打开" aria-label="打开"><svg class="i"><use href="#i-external"></use></svg></a>
                    <button type="button" :class="['ss-revoke', { sure: sure === r.id }]" :disabled="revoking === r.id" @click="revoke(r)">{{ sure === r.id ? '确定取消' : '取消分享' }}</button>
                </div>
            </li>
        </ul>
    </div>
</dialog>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue';

const props = defineProps({ all: Boolean, slug: String, name: String, appId: [Number, String], icon: [Number, String], count: Number, light: Boolean });

const HOURS = [{ h: 1, label: '1 小时' }, { h: 24, label: '1 天' }, { h: 168, label: '7 天' }, { h: 720, label: '30 天' }, { h: 0, label: '长期' }];
const STATE = { active: '有效', expired: '已过期', revoked: '已取消' };
const PASS_CHARS = 'abcdefghjkmnpqrstuvwxyz23456789';

const el = ref(null);
const passEl = ref(null);
const tab = ref('new');
const form = reactive({ all: false, hours: 24, lock: false, password: '', show: true, note: '' });
const busy = ref(false);
const error = ref('');
const made = ref(null);
const rows = ref([]);
const loaded = ref(false);
const listError = ref('');
const owner = ref(false);
const filter = ref('active');
const sure = ref(null);
const revoking = ref(null);
const copied = ref('');
const now = ref(Date.now());
const origin = typeof location === 'undefined' ? '' : location.origin;
let sureTimer = 0, copyTimer = 0, clock = 0;

const activeCount = computed(() => rows.value.filter(r => r.state === 'active').length);
const shown = computed(() => {
    const list = filter.value === 'active' ? rows.value.filter(r => r.state === 'active') : rows.value.slice();
    return list.sort((a, b) => (a.state === 'active' ? 0 : 1) - (b.state === 'active' ? 0 : 1) || b.createdAt - a.createdAt);
});
const untilText = computed(() => form.hours > 0 ? dt(now.value + form.hours * 3600000) + ' 失效，之后打开会提示链接已过期' : '一直有效，直到你在分享记录里取消');
const qrSrc = computed(() => made.value && made.value.qr ? 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(made.value.qr) : '');
const madeMeta = computed(() => {
    const m = made.value;
    if (!m) return '';
    const s = m.share;
    return [s.scope === 'all' ? '全部 App' : s.appName, s.expiresAt ? dt(s.expiresAt) + ' 失效' : '长期有效', m.password ? '要输访问密码' : '不用密码'].join(' · ');
});
const shareText = computed(() => {
    const m = made.value;
    if (!m) return '';
    const s = m.share;
    const lines = [(s.scope === 'all' ? '全部 App' : '「' + s.appName + '」') + '数据大屏：' + m.url];
    if (m.password) lines.push('访问密码：' + m.password);
    lines.push(s.expiresAt ? dt(s.expiresAt) + ' 前有效' : '长期有效');
    return lines.join('\n');
});

function pad(n) {
    return (n < 10 ? '0' : '') + n;
}
function dt(ms) {
    if (!ms) return '';
    const d = new Date(ms), t = new Date(now.value);
    const hm = pad(d.getHours()) + ':' + pad(d.getMinutes());
    if (d.toDateString() === t.toDateString()) return '今天 ' + hm;
    if (d.toDateString() === new Date(now.value + 86400000).toDateString()) return '明天 ' + hm;
    if (d.toDateString() === new Date(now.value - 86400000).toDateString()) return '昨天 ' + hm;
    return (d.getFullYear() === t.getFullYear() ? '' : d.getFullYear() + '-') + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + hm;
}
function ago(ms) {
    const s = Math.max(0, (now.value - ms) / 1000);
    if (s < 60) return '刚刚';
    if (s < 3600) return Math.floor(s / 60) + ' 分钟前';
    if (s < 86400) return Math.floor(s / 3600) + ' 小时前';
    if (s < 86400 * 7) return Math.floor(s / 86400) + ' 天前';
    return dt(ms);
}
function expiryText(r) {
    if (r.state === 'revoked') return dt(r.revokedAt) + ' 取消了';
    if (r.state === 'expired') return dt(r.expiresAt) + ' 过期了';
    if (!r.expiresAt) return '长期有效';
    const left = (r.expiresAt - now.value) / 1000;
    if (left < 3600) return '还剩 ' + Math.max(1, Math.ceil(left / 60)) + ' 分钟';
    if (left < 86400 * 2) return '还剩 ' + Math.ceil(left / 3600) + ' 小时';
    return '还剩 ' + Math.floor(left / 86400) + ' 天 · ' + dt(r.expiresAt) + ' 失效';
}
function soon(r) {
    return r.state === 'active' && !!r.expiresAt && r.expiresAt - now.value < 3600000;
}

function call(url, body) {
    return fetch(url, {
        method: body ? 'POST' : 'GET', body: body || undefined, cache: 'no-store', credentials: 'same-origin',
        headers: { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' }
    }).then(r => {
        const json = (r.headers.get('Content-Type') || '').indexOf('application/json') >= 0;
        if (!json || r.status === 401) return { ok: false, message: '登录已失效，刷新页面重新登录' };
        return r.json().then(d => d && d.ok ? d : { ok: false, message: (d && (d.message || d.msg)) || (r.status === 403 ? '没有权限' : '') });
    }).catch(() => ({ ok: false, message: '网络不通，稍后再试' }));
}

function load() {
    listError.value = '';
    return call('/admin/screen/shares').then(d => {
        loaded.value = true;
        if (!d || !d.ok) {
            listError.value = (d && d.message) || '分享记录没取到';
            return;
        }
        rows.value = d.shares || [];
        owner.value = !!d.owner;
    });
}

function open() {
    const d = el.value;
    if (!d) return;
    tab.value = 'new';
    made.value = null;
    error.value = '';
    sure.value = null;
    form.all = props.all;
    form.hours = 24;
    form.lock = false;
    form.password = '';
    form.show = true;
    form.note = '';
    filter.value = 'active';
    now.value = Date.now();
    clearInterval(clock);
    clock = setInterval(() => { now.value = Date.now(); }, 30000);
    if (!d.open) d.showModal();
    load();
}
function close() {
    if (el.value && el.value.open) el.value.close();
}
function onClose() {
    clearInterval(clock);
    clearTimeout(sureTimer);
    sure.value = null;
}
function onBackdrop(e) {
    if (e.target === el.value) close();
}
function showList() {
    tab.value = 'list';
    now.value = Date.now();
    if (!loaded.value) load();
}

function randomPass() {
    const b = new Uint32Array(6);
    (window.crypto || window.msCrypto).getRandomValues(b);
    let s = '';
    for (let i = 0; i < b.length; i++) s += PASS_CHARS[b[i] % PASS_CHARS.length];
    form.password = s;
    form.show = true;
}
function onLock() {
    if (!form.lock) return;
    if (!form.password) randomPass();
    nextTick(() => { if (passEl.value) passEl.value.focus(); });
}

function create() {
    if (busy.value) return;
    error.value = '';
    const pwd = form.lock ? form.password.trim() : '';
    if (form.lock && (pwd.length < 4 || pwd.length > 32)) {
        error.value = '访问密码 4–32 位';
        if (passEl.value) passEl.value.focus();
        return;
    }
    const all = props.all || (form.all && props.count > 1);
    const body = new URLSearchParams();
    body.set('scope', all ? 'all' : 'app');
    if (!all && props.slug) body.set('app', props.slug);
    body.set('hours', String(form.hours));
    body.set('password', pwd);
    body.set('note', form.note.trim());
    body.set('theme', props.light ? 'light' : '');
    body.set('origin', origin);
    busy.value = true;
    call('/admin/screen/shares', body).then(d => {
        busy.value = false;
        if (!d || !d.ok) {
            error.value = (d && d.message) || '没有生成成功，稍后再试';
            return;
        }
        now.value = Date.now();
        made.value = { url: d.url, qr: d.qr, share: d.share, password: pwd };
        rows.value = [d.share].concat(rows.value.filter(r => r.id !== d.share.id));
        loaded.value = true;
    });
}
function again() {
    made.value = null;
    form.password = '';
    form.lock = false;
    form.note = '';
}

function revoke(r) {
    if (sure.value !== r.id) {
        sure.value = r.id;
        clearTimeout(sureTimer);
        sureTimer = setTimeout(() => { sure.value = null; }, 4000);
        return;
    }
    clearTimeout(sureTimer);
    sure.value = null;
    revoking.value = r.id;
    call('/admin/screen/shares/' + r.id + '/revoke', new URLSearchParams()).then(d => {
        revoking.value = null;
        if (!d || !d.ok) {
            listError.value = (d && d.message) || '没有取消成功，稍后再试';
            return;
        }
        rows.value = rows.value.map(x => x.id === r.id ? d.share : x);
        if (window.otaToast) window.otaToast('已取消分享，打开链接会提示分享已取消');
    });
}

function copy(text, key) {
    const done = () => {
        copied.value = key;
        clearTimeout(copyTimer);
        copyTimer = setTimeout(() => { copied.value = ''; }, 1600);
    };
    const fallback = () => {
        const t = document.createElement('textarea');
        t.value = text;
        t.setAttribute('readonly', '');
        t.style.cssText = 'position:fixed;top:0;left:0;opacity:0;pointer-events:none';
        (el.value || document.body).appendChild(t);
        t.select();
        let ok = false;
        try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
        t.remove();
        if (ok) done();
        else if (window.otaToast) window.otaToast('复制不了，请手动选中复制', 'err');
    };
    if (navigator.clipboard && window.isSecureContext) navigator.clipboard.writeText(text).then(done, fallback);
    else fallback();
}

onBeforeUnmount(() => {
    clearInterval(clock);
    clearTimeout(sureTimer);
    clearTimeout(copyTimer);
});

defineExpose({ open });
</script>
