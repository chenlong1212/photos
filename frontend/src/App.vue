<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import TimelineView from './views/TimelineView.vue'
import DayDetailView from './views/DayDetailView.vue'
import PhotoView from './views/PhotoView.vue'

const route=useRoute()
const isMemoryRoute=computed(()=>route.path==='/'||route.path.startsWith('/day/')||route.path==='/view')
const memoryDate=computed(()=>route.path.startsWith('/day/')?String(route.params.date||''):String(route.query.fromDate||''))
const isDayRoute=computed(()=>Boolean(memoryDate.value))
const isViewerRoute=computed(()=>route.path==='/view')
watch(()=>route.path,path=>{if(path==='/')sessionStorage.removeItem('timeline-return-ready')})
</script>

<template>
  <div v-if="isMemoryRoute" class="memory-shell">
    <TimelineView :inert="isDayRoute || isViewerRoute" :aria-hidden="isDayRoute || isViewerRoute" />
    <transition name="detail-slide">
      <DayDetailView v-if="isDayRoute" :key="memoryDate" :date="memoryDate" :inert="isViewerRoute" :aria-hidden="isViewerRoute" />
    </transition>
    <PhotoView v-if="isViewerRoute" class="media-viewer-layer" />
  </div>
  <router-view v-else />
</template>

<style>
.memory-shell{position:fixed;inset:0;overflow:hidden;background:#fff}
.memory-shell>.timeline-page{position:absolute;inset:0;z-index:0;isolation:isolate;overflow-y:auto;overscroll-behavior-y:contain;-webkit-overflow-scrolling:touch}
.memory-shell>.detail-page{position:absolute;inset:0;z-index:100;isolation:isolate;overflow-y:auto;-webkit-overflow-scrolling:touch;background:#fff}
.memory-shell>.media-viewer-layer{z-index:200;isolation:isolate}
.detail-slide-enter-active,.detail-slide-leave-active{transition:transform .28s cubic-bezier(.22,.61,.36,1),box-shadow .28s ease;box-shadow:-10px 0 28px rgba(0,0,0,.16)}
.detail-slide-enter-from,.detail-slide-leave-to{transform:translate3d(100%,0,0)!important}
.detail-slide-enter-to,.detail-slide-leave-from{transform:translate3d(0,0,0)!important}
@media(prefers-reduced-motion:reduce){.detail-slide-enter-active,.detail-slide-leave-active{transition-duration:.01ms}}
</style>
