<template><header class="topbar">
    <button class="icon-btn menu-btn" type="button" data-nav-toggle aria-label="菜单"><svg class="i"><use href="#i-menu"></use></svg></button>
    <button class="icon-btn side-toggle" type="button" data-side-toggle aria-label="收起或展开侧栏" data-tip="收起 / 展开侧栏" data-tip-key="["><svg class="i"><use href="#i-side-light"></use></svg></button>
    <div class="crumb"><span>控制台</span><span>/</span><b :data-src.attr="$h.av(P_crumb)">{{ $h.s(P_crumb) }}</b></div>
    <span class="chip chip-demo" title="这里是演示环境：可以随便操作，数据都是演示用的，不影响正式数据，每天恢复默认" v-if="$h.t(m.demoMode)"><svg class="i i-sm"><use href="#i-play"></use></svg><span>演示环境</span></span>
    <div class="top-status" v-if="$h.t(!$h.eq(m.app, null))">
        <a class="chip chip-app" :href.attr="$h.ak('/admin/app')" title="当前 App，点击打开 App 设置"><img class="chip-ico" :src.attr="$h.ak($h.cat('/admin/apps/', $h.p(m.app, 'id'), '/icon?v=', $h.p(m.app, 'iconVersion')))" alt="" v-if="!$h.eq($h.p(m.app, 'iconVersion'), null)">{{ $h.s($h.p(m.app, 'name')) }}</a>
        <a :class="['chip chip-face', $h.av(($h.t($h.eq(m.face, 'B')) ? 'live' : null))]" :href.attr="$h.ak('/admin')" title="运行模式">
            <span class="dot"></span>
            <span>{{ $h.s(($h.t($h.eq(m.face, 'B')) ? 'B 面 · 上线中' : 'A 面 · 审核中')) }}</span>
        </a>
        <a :class="['chip chip-sign', $h.av(($h.t($h.eq($h.p(m.signing, 'level'), 'error')) ? 'alert' : null))]" :href.attr="$h.ak('/admin/signing')" :title.attr="$h.av($h.p(m.signing, 'headline'))" v-if="$h.t(!$h.eq(m.signing, null))">
            <svg class="i i-sm"><use :href.attr="$h.ak(($h.t($h.eq($h.p(m.signing, 'level'), 'ok')) ? '#i-shield' : '#i-alert'))"></use></svg>
            <span>{{ $h.s($h.p(m.signing, 'chip')) }}</span>
        </a>
    </div>
    <div class="top-actions">
        <button class="search-trigger" type="button" data-cmdk-open data-guide="search" title="搜索和快捷操作">
            <svg class="i i-sm"><use href="#i-search"></use></svg><span>搜索口令、代理、设备…</span><kbd data-kbd-mod>⌘K</kbd>
        </button>
        <button class="top-btn lang-btn" type="button" data-lang-toggle title="切换语言 · Language" aria-label="切换语言 · Language"><svg class="i"><use href="#i-globe"></use></svg><span class="lang-to only-zh">EN</span><span class="lang-to only-en">中</span></button>
        <FAdminLayoutLookMenu :m="m" :c="c"/>
        <template v-for="L_todos in [$h.elvis(m.todos, [])]"><div class="pop todo-pop" data-pop data-guide="todo">
            <button class="top-btn" type="button" data-pop-toggle aria-haspopup="true" aria-expanded="false" :title.attr="$h.av(($h.t($h.lists.isEmpty(L_todos)) ? '待处理：没有' : $h.cat('待处理：', $h.lists.size(L_todos), ' 件')))" aria-label="待处理">
                <svg class="i"><use href="#i-bell"></use></svg><i :class="['todo-count', $h.av($h.p($h.i(L_todos, 0), 'level'))]" v-if="!$h.t($h.lists.isEmpty(L_todos))">{{ $h.s($h.lists.size(L_todos)) }}</i>
            </button>
            <div class="menu todo-menu" data-pop-menu hidden>
                <div class="todo-head"><b>待处理</b><span>{{ $h.s(($h.t($h.lists.isEmpty(L_todos)) ? '' : $h.cat($h.lists.size(L_todos), ' 件'))) }}</span></div>
                <div class="todo-empty" v-if="$h.t($h.lists.isEmpty(L_todos))"><span class="todo-ok"><svg class="i"><use href="#i-tick"></use></svg></span><b>都处理完了</b><span>新的口令申请、启动失败告警会出现在这里</span></div>
                <div class="todo-items" v-if="!$h.t($h.lists.isEmpty(L_todos))"><template v-for="[L_t, L_tStat] in $h.each(L_todos)"><a :class="['todo-item', $h.av($h.p(L_t, 'level'))]" :href.attr="$h.ak($h.p(L_t, 'href'))">
                    <i class="todo-dot"></i>
                    <span class="todo-text"><b>{{ $h.s($h.p(L_t, 'title')) }}</b><small>{{ $h.s(($h.t(!$h.eq($h.p(L_t, 'app'), null)) ? $h.cat($h.p(L_t, 'app'), ' · ', $h.p(L_t, 'detail')) : $h.p(L_t, 'detail'))) }}</small></span>
                    <span class="todo-go">{{ $h.s($h.p(L_t, 'action')) }}</span>
                </a></template></div>
            </div>
        </div></template>
        <div class="pop" data-pop>
            <button class="top-btn" type="button" data-pop-toggle data-guide="help" aria-haspopup="true" aria-expanded="false" title="帮助和引导" aria-label="帮助和引导">
                <svg class="i"><use href="#i-help"></use></svg><i class="badge-dot" data-guide-dot hidden></i>
            </button>
            <div class="menu" data-pop-menu hidden>
                <div class="menu-label">引导</div>
                <button class="menu-item" type="button" data-guide-run="page" hidden><svg class="i"><use href="#i-compass"></use></svg><span>本页引导</span><i class="new-dot" data-guide-new hidden></i></button>
                <button class="menu-item" type="button" data-guide-run="welcome"><svg class="i"><use href="#i-sparkle"></use></svg>后台导览</button>
                <button class="menu-item" type="button" data-cmdk-open><svg class="i"><use href="#i-command"></use></svg>搜索和快捷操作<span class="tail"><kbd data-kbd-mod>⌘K</kbd></span></button>
                <button class="menu-item" type="button" data-open="keys-dialog"><svg class="i"><use href="#i-keyboard"></use></svg>快捷键<span class="tail"><kbd>?</kbd></span></button>
                <hr>
                <a class="menu-item" :href.attr="$h.ak('/admin/docs')"><svg class="i"><use href="#i-code"></use></svg>API 文档</a>
                <a class="menu-item" :href.attr="$h.ak('/admin/account')"><svg class="i"><use href="#i-user"></use></svg>我的账号</a>
            </div>
        </div>
    </div>
    <dialog class="cmdk" id="cmdk" aria-label="搜索和快捷操作">
        <div class="cmdk-input">
            <svg class="i"><use href="#i-search"></use></svg>
            <input type="text" placeholder="搜页面、操作，或者直接搜口令、代理、设备、版本…" autocomplete="off" spellcheck="false" aria-label="搜索" role="combobox" aria-expanded="true" aria-controls="cmdk-list">
            <kbd>esc</kbd>
        </div>
        <div class="cmdk-list" id="cmdk-list" role="listbox"></div>
        <div class="cmdk-foot"><span><kbd>↑</kbd><kbd>↓</kbd>选择</span><span><kbd>↵</kbd>打开</span><span><kbd>esc</kbd>关闭</span></div>
    </dialog>
    <dialog id="keys-dialog">
        <div class="dlg-head"><h3>快捷键</h3><p>后台任何页面都能用</p></div>
        <div class="dlg-body">
            <ul class="keys">
                <li><span>搜索页面、切换 App、执行操作</span><span><kbd data-kbd-mod>⌘K</kbd><i>或</i><kbd>/</kbd></span></li>
                <li><span>在搜索结果里上下选择</span><span><kbd>↑</kbd><kbd>↓</kbd></span></li>
                <li><span>打开选中的结果</span><kbd>↵</kbd></li>
                <li><span>关闭弹窗、菜单、引导</span><kbd>Esc</kbd></li>
                <li><span>引导上一步、下一步</span><span><kbd>←</kbd><kbd>→</kbd></span></li>
                <li><span>收起、展开侧栏</span><kbd>[</kbd></li>
                <li><span>查看快捷键</span><kbd>?</kbd></li>
            </ul>
        </div>
        <div class="dlg-foot"><button class="btn" type="button" data-close>知道了</button></div>
    </dialog>
    <template id="cmdk-actions">
        <a :href.attr="$h.ak('/admin/screen')" data-icon="i-monitor" data-k="screen dashboard daping kanban 数据大屏 大屏 看板 投屏" v-if="$h.t(!$h.eq(m.app, null))">数据大屏</a>
        <a :href.attr="$h.ak('/admin/releases#upload')" data-icon="i-upload" data-k="upload release shangchuan fabu 上传 发布 发版 更新包" v-if="$h.t(($h.t(!$h.eq(m.app, null)) &amp;&amp; $h.t($h.access.can('release'))))">上传更新包</a>
        <a :href.attr="$h.ak('/admin/codes#new')" data-icon="i-key" data-k="new code xinjian kouling 新建 口令" v-if="$h.t($h.access.can('codes'))">新建口令</a>
        <a :href.attr="$h.ak('/admin/codes#batch')" data-icon="i-layers" data-k="batch generate piliang kouling 批量 生成 口令" v-if="$h.t($h.access.can('codes'))">批量生成口令</a>
        <a :href.attr="$h.ak('/admin/devices#diagnose')" data-icon="i-stethoscope" data-k="diagnose check update zhenduan 诊断 检查更新 收不到更新" v-if="$h.t(!$h.eq(m.app, null))">模拟检查更新</a>
        <a :href.attr="$h.ak('/admin/app#channels')" data-icon="i-flask" data-k="channel tester ceshi qudao 渠道 测试设备" v-if="$h.t(($h.t(!$h.eq(m.app, null)) &amp;&amp; $h.t($h.access.can('settings'))))">渠道和测试设备</a>
        <a :href.attr="$h.ak('/admin/app#tokens')" data-icon="i-key" data-k="token publish cli lingpai 发布令牌 命令行" v-if="$h.t(($h.t(!$h.eq(m.app, null)) &amp;&amp; $h.t($h.access.can('release'))))">发布令牌</a>
        <a :href.attr="$h.ak('/admin/app#backup')" data-icon="i-database" data-k="backup restore beifen 备份 恢复" v-if="$h.t(($h.t(!$h.eq(m.app, null)) &amp;&amp; $h.t($h.access.can('signing'))))">备份与恢复</a>
        <a :href.attr="$h.ak('/admin/agents#new')" data-icon="i-users" data-k="new agent daili 新建 代理" v-if="$h.t($h.access.agents())">新建代理</a>
        <a :href.attr="$h.ak('/admin/apps#new')" data-icon="i-package" data-k="new app xinjian 新建 App 应用" v-if="$h.t($h.access.owner())">新建 App</a>
        <a :href.attr="$h.ak('/admin/apps#restore')" data-icon="i-refresh" data-k="restore backup huifu 恢复 备份 迁移" v-if="$h.t($h.access.owner())">从备份恢复 App</a>
        <a :href.attr="$h.ak('/admin/admins#new')" data-icon="i-user-cog" data-k="new admin member guanliyuan 新建 管理员 成员" v-if="$h.t($h.access.owner())">新建管理员</a>
        <a :href.attr="$h.ak('/admin/settings#appearance')" data-icon="i-palette" data-k="theme color radius zhuti waiguan 主题色 圆角 外观" v-if="$h.t($h.access.owner())">主题色和圆角</a>
    </template>
</header></template>

<script setup>
import FAdminLayoutLookMenu from '../frags/admin-layout--lookMenu.vue';
defineProps({ m: Object, c: Object, P_crumb: null });
</script>
