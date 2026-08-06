<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { mediaUrl } from '../api/http'

const route = useRoute()
const router = useRouter()
const path = String(route.query.src || '')
const src = mediaUrl(path)
const video = route.query.type === 'video'
const filename = String(route.query.filename || 'video')
const downloadUrl = `${import.meta.env.VITE_API_BASE || '/api'}/media/download?path=${encodeURIComponent(path)}`

const scale = ref(1)
const translateX = ref(0)
const translateY = ref(0)
const gesturing = ref(false)
let initialDistance = 0
let initialScale = 1
let panX = 0
let panY = 0
let panTranslateX = 0
let panTranslateY = 0
let moved = false
let lastTap = 0

const imageStyle = computed(() => ({
  transform: `translate3d(${translateX.value}px,${translateY.value}px,0) scale(${scale.value})`,
  transition: gesturing.value ? 'none' : 'transform .2s ease'
}))

function distance(touches: TouchList) {
  return Math.hypot(touches[0].clientX - touches[1].clientX, touches[0].clientY - touches[1].clientY)
}

function setScale(value: number) {
  scale.value = Math.min(5, Math.max(1, value))
  if (scale.value === 1) {
    translateX.value = 0
    translateY.value = 0
  }
}

function zoomBy(amount: number) {
  setScale(scale.value + amount)
}

function toggleZoom() {
  if (scale.value > 1) setScale(1)
  else setScale(2.5)
}

function touchStart(event: TouchEvent) {
  gesturing.value = true
  moved = false
  if (event.touches.length === 2) {
    initialDistance = distance(event.touches)
    initialScale = scale.value
  } else if (event.touches.length === 1) {
    panX = event.touches[0].clientX
    panY = event.touches[0].clientY
    panTranslateX = translateX.value
    panTranslateY = translateY.value
  }
}

function touchMove(event: TouchEvent) {
  if (event.touches.length === 2 && initialDistance) {
    event.preventDefault()
    moved = true
    setScale(initialScale * distance(event.touches) / initialDistance)
  } else if (event.touches.length === 1 && scale.value > 1) {
    event.preventDefault()
    const dx = event.touches[0].clientX - panX
    const dy = event.touches[0].clientY - panY
    if (Math.abs(dx) > 2 || Math.abs(dy) > 2) moved = true
    translateX.value = panTranslateX + dx
    translateY.value = panTranslateY + dy
  }
}

function touchEnd(event: TouchEvent) {
  if (event.touches.length === 1) {
    panX = event.touches[0].clientX
    panY = event.touches[0].clientY
    panTranslateX = translateX.value
    panTranslateY = translateY.value
    return
  }
  gesturing.value = false
  initialDistance = 0
  if (!moved) {
    const now = Date.now()
    if (now - lastTap < 300) {
      toggleZoom()
      lastTap = 0
    } else lastTap = now
  }
}

function wheel(event: WheelEvent) {
  event.preventDefault()
  zoomBy(event.deltaY < 0 ? 0.4 : -0.4)
}
</script>

<template>
  <main class="viewer" @click="router.back()">
    <button class="back" @click.stop="router.back()">‹</button>
    <a v-if="video" class="download" :href="downloadUrl" :download="filename" @click.stop>下载原视频</a>
    <video v-if="video" :src="src" controls autoplay playsinline @click.stop />
    <img
      v-else
      :src="src"
      :style="imageStyle"
      draggable="false"
      @click.stop
      @dblclick.stop="toggleZoom"
      @touchstart.stop="touchStart"
      @touchmove.stop="touchMove"
      @touchend.stop="touchEnd"
      @touchcancel.stop="touchEnd"
      @wheel.stop="wheel"
    >
    <div v-if="!video" class="zoom-controls" @click.stop>
      <button type="button" aria-label="缩小" :disabled="scale <= 1" @click="zoomBy(-0.5)">−</button>
      <button type="button" class="zoom-value" aria-label="还原缩放" @click="setScale(1)">{{ Math.round(scale * 100) }}%</button>
      <button type="button" aria-label="放大" :disabled="scale >= 5" @click="zoomBy(0.5)">+</button>
    </div>
  </main>
</template>

<style scoped>
.viewer{position:fixed;inset:0;background:#000;display:flex;align-items:center;justify-content:center;overflow:hidden}.viewer img,.viewer video{max-width:100%;max-height:100%;object-fit:contain}.viewer img{touch-action:none;user-select:none;will-change:transform}.back{position:fixed;top:18px;left:18px;z-index:3;width:40px;height:40px;border:0;border-radius:50%;background:rgba(255,255,255,.2);color:#fff;font-size:32px;line-height:30px}.download{position:fixed;top:20px;right:18px;z-index:3;padding:9px 14px;border-radius:20px;background:rgba(255,255,255,.2);color:#fff;text-decoration:none;font-size:14px;backdrop-filter:blur(8px)}.zoom-controls{position:fixed;left:50%;bottom:max(24px,env(safe-area-inset-bottom));z-index:3;transform:translateX(-50%);display:flex;align-items:center;gap:4px;padding:5px;border-radius:24px;background:rgba(30,30,30,.72);backdrop-filter:blur(8px)}.zoom-controls button{height:36px;min-width:38px;padding:0 10px;border:0;border-radius:18px;background:rgba(255,255,255,.14);color:#fff;font-size:20px}.zoom-controls button:disabled{opacity:.35}.zoom-controls .zoom-value{min-width:64px;font-size:13px}
</style>
