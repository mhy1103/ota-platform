<template>
    <span class="badge badge-solid" v-if="$h.t($h.p(P_r, 'confirmed'))"><span class="dot"></span>已确认</span>
    <span class="badge badge-danger" :title.attr="$h.cat('兑换后 ', $h.p(P_r, 'fastMs'), ' 毫秒就来确认，真手机要先下载 B 面包再重启，不会这么快，这条不算有效激活')" v-if="$h.t(($h.t(!$h.t($h.p(P_r, 'confirmed'))) &amp;&amp; $h.t(!$h.eq($h.p(P_r, 'fastMs'), null))))"><span class="dot"></span>非正常</span>
    <span class="badge badge-warn" :title.attr="($h.t($h.eq($h.p(P_r, 'dupBy'), 'device')) ? '同一个设备号在别的 App 已经激活过，所有 App 加起来只算一次，这条不算有效激活' : '这台手机（设备指纹）在别的 App 已经激活过，所有 App 加起来只算一次，这条不算有效激活')" v-if="$h.t(($h.t(!$h.t($h.p(P_r, 'confirmed'))) &amp;&amp; $h.t($h.eq($h.p(P_r, 'fastMs'), null)) &amp;&amp; $h.t(!$h.eq($h.p(P_r, 'dupOf'), null))))"><span class="dot"></span>重复</span>
    <span class="badge badge-line" v-if="$h.t(($h.t(!$h.t($h.p(P_r, 'confirmed'))) &amp;&amp; $h.t($h.eq($h.p(P_r, 'fastMs'), null)) &amp;&amp; $h.t($h.eq($h.p(P_r, 'dupOf'), null))))"><span class="dot"></span>待确认</span>
</template>

<script setup>
defineProps({ m: Object, c: Object, P_r: null });
</script>
