<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import TimelineView from './views/TimelineView.vue'
import DayDetailView from './views/DayDetailView.vue'

const route=useRoute()
const isMemoryRoute=computed(()=>route.path==='/'||route.path.startsWith('/day/'))
const isDayRoute=computed(()=>route.path.startsWith('/day/'))
watch(()=>route.path,path=>{if(path==='/')sessionStorage.removeItem('timeline-return-ready')})
</script>

<template>
  <div v-if="isMemoryRoute" class="memory-shell">
    <TimelineView />
    <transition name="detail-slide">
      <DayDetailView v-if="isDayRoute" :key="String(route.params.date)" />
    </transition>
  </div>
  <router-view v-else />
</template>

<style>
.memory-shell{position:fixed;inset:0;overflow:hidden;background:#fff}
.memory-shell>.timeline-page{position:absolute;inset:0;overflow-y:auto;overscroll-behavior-y:contain;-webkit-overflow-scrolling:touch}
.memory-shell>.detail-page{position:absolute;inset:0;z-index:10;overflow-y:auto;-webkit-overflow-scrolling:touch;background:#fff}
.detail-slide-enter-active,.detail-slide-leave-active{transition:transform .28s cubic-bezier(.22,.61,.36,1),box-shadow .28s ease;box-shadow:-10px 0 28px rgba(0,0,0,.16)}
.detail-slide-enter-from,.detail-slide-leave-to{transform:translate3d(100%,0,0)!important}
.detail-slide-enter-to,.detail-slide-leave-from{transform:translate3d(0,0,0)!important}
@media(prefers-reduced-motion:reduce){.detail-slide-enter-active,.detail-slide-leave-active{transition-duration:.01ms}}
</style>
