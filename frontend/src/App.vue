<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const transitionName = computed(() => route.path.startsWith('/day/') ? 'page-push' : 'page-pop')
</script>

<template>
  <router-view v-slot="{Component,route:currentRoute}">
    <transition :name="transitionName">
      <keep-alive include="TimelineView">
        <component :is="Component" :key="currentRoute.path" />
      </keep-alive>
    </transition>
  </router-view>
</template>

<style>
#app{position:relative;z-index:1}
.page-push-enter-active,.page-push-leave-active,.page-pop-enter-active,.page-pop-leave-active{
  position:fixed!important;inset:0;width:100%;min-height:100vh;background:#fff;overflow-y:auto;
  transition:transform .26s cubic-bezier(.22,.61,.36,1),box-shadow .26s ease;
}
.page-push-enter-active,.page-pop-leave-active{z-index:1000;box-shadow:-10px 0 28px rgba(0,0,0,.16)}
.page-push-leave-active,.page-pop-enter-active{z-index:999}
.page-push-leave-active,.page-pop-enter-active{visibility:hidden}
.page-push-enter-from{transform:translate3d(100%,0,0)}
.page-push-enter-to,.page-push-leave-to,.page-pop-enter-from,.page-pop-enter-to{transform:translate3d(0,0,0)}
.page-pop-leave-to{transform:translate3d(100%,0,0)!important}
@media(prefers-reduced-motion:reduce){
  .page-push-enter-active,.page-push-leave-active,.page-pop-enter-active,.page-pop-leave-active{transition-duration:.01ms}
}
</style>
