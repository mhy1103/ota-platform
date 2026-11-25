<template><div class="pop" data-pop>
    <button class="top-btn" type="button" data-pop-toggle data-guide="look" aria-haspopup="true" aria-expanded="false" title="外观" aria-label="外观">
        <svg class="i"><use href="#i-sun" data-look-icon></use></svg>
    </button>
    <div class="menu" data-pop-menu hidden>
        <div class="menu-label">主题</div>
        <div class="choice" data-look="mode">
            <button type="button" data-v="light"><svg class="i"><use href="#i-sun"></use></svg>浅色</button>
            <button type="button" data-v="dark"><svg class="i"><use href="#i-moon"></use></svg>深色</button>
            <button type="button" data-v="system"><svg class="i"><use href="#i-monitor"></use></svg>跟随系统</button>
        </div>
        <template v-if="$h.t($h.eq(m.base, null))">
            <div class="menu-label">侧栏</div>
            <div class="choice" data-look="sidebar">
                <button type="button" data-v="theme"><svg class="i"><use href="#i-side-theme"></use></svg>主题色</button>
                <button type="button" data-v="dark"><svg class="i"><use href="#i-side-dark"></use></svg>深色</button>
                <button type="button" data-v="light"><svg class="i"><use href="#i-side-light"></use></svg>浅色</button>
            </div>
        </template>
        <div class="menu-label">语言</div>
        <div class="choice" data-lang-choice>
            <button type="button" data-lang="zh"><span class="lang-mark">中</span>中文</button>
            <button type="button" data-lang="en"><span class="lang-mark">A</span>English</button>
        </div>
        <div class="menu-label">信息密度</div>
        <div class="choice" data-look="density">
            <button type="button" data-v="comfortable"><svg class="i"><use href="#i-rows"></use></svg>标准</button>
            <button type="button" data-v="compact"><svg class="i"><use href="#i-rows-tight"></use></svg>紧凑</button>
        </div>
        <template v-if="$h.t($h.eq(m.base, null))">
            <hr>
            <a class="menu-item" :href.attr="$h.ak('/admin/settings#appearance')"><svg class="i"><use href="#i-palette"></use></svg>主题色、侧栏和圆角<span class="tail">{{ $h.s(($h.t($h.access.owner()) ? '默认外观' : '只对你')) }}</span></a>
        </template>
        <div class="menu-note">这里的选择只对你自己生效</div>
    </div>
</div></template>

<script setup>
defineProps({ m: Object, c: Object });
</script>
