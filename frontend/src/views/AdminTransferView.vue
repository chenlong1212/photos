<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { http, mediaUrl } from '../api/http'

type Album = { key: string; label: string }
type Day = { date: number; imageCount: number; info?: string }
type Image = { id: number; previewPath: string; photoTime?: string; mediaType?: string; durationMs?: number }
type DayDetail = { info: string; images: Image[] }

const router = useRouter()
const albums = ref<Album[]>([])
const sourceAlbum = ref('')
const sourceDate = ref('')
const sourceDays = ref<Day[]>([])
const sourceDetail = ref<DayDetail>()
const targetAlbum = ref('')
const targetDate = ref('')
const targetDetail = ref<DayDetail>()
const description = ref('')
const loadingSource = ref(false)
const loadingTarget = ref(false)
const transferring = ref(false)
const message = ref('')
const error = ref('')

const targetEmpty = computed(() => !targetDetail.value?.images.length)
const sameLocation = computed(() =>
  sourceAlbum.value === targetAlbum.value
  && sourceDate.value
  && sourceDate.value === targetDate.value.replaceAll('-', '')
)
const canTransfer = computed(() =>
  !!sourceDetail.value?.images.length
  && !!targetAlbum.value
  && /^\d{4}-\d{2}-\d{2}$/.test(targetDate.value)
  && !sameLocation.value
  && (!targetEmpty.value || !!description.value.trim())
  && !transferring.value
)

onMounted(async () => {
  albums.value = (await http.get('/albums')).data
  if (albums.value.length) {
    sourceAlbum.value = albums.value[0].key
    targetAlbum.value = albums.value[0].key
  }
})

watch(sourceAlbum, async key => {
  sourceDate.value = ''
  sourceDetail.value = undefined
  sourceDays.value = []
  if (!key) return
  loadingSource.value = true
  error.value = ''
  try {
    const { data } = await http.get('/admin/days', { params: { scope: 'album', albumKey: key } })
    sourceDays.value = data.days.filter((day: Day) => day.imageCount > 0)
  } catch (exception: any) {
    error.value = exception.response?.data?.message || '原相册日期加载失败'
  } finally {
    loadingSource.value = false
  }
})

watch(sourceDate, async value => {
  sourceDetail.value = undefined
  if (!value || !sourceAlbum.value) return
  targetDate.value = formatInputDate(value)
  loadingSource.value = true
  error.value = ''
  try {
    sourceDetail.value = (await http.get(`/albums/${sourceAlbum.value}/days/${value}`)).data
    if (targetEmpty.value) description.value = sourceDetail.value?.info || ''
  } catch (exception: any) {
    error.value = exception.response?.data?.message || '原相册图片加载失败'
  } finally {
    loadingSource.value = false
  }
})

watch([targetAlbum, targetDate], loadTarget)

async function loadTarget() {
  targetDetail.value = undefined
  description.value = ''
  if (!targetAlbum.value || !/^\d{4}-\d{2}-\d{2}$/.test(targetDate.value)) return
  loadingTarget.value = true
  error.value = ''
  const date = targetDate.value.replaceAll('-', '')
  try {
    targetDetail.value = (await http.get('/admin/transfer/preview', {
      params: { albumKey: targetAlbum.value, date }
    })).data
    if (targetEmpty.value) description.value = sourceDetail.value?.info || ''
  } catch (exception: any) {
    error.value = exception.response?.data?.message || '目标日期加载失败'
  } finally {
    loadingTarget.value = false
  }
}

function formatDate(value: number) {
  const text = String(value)
  return text.length === 8
    ? `${text.slice(0, 4)}-${text.slice(4, 6)}-${text.slice(6, 8)}`
    : text
}

function formatInputDate(value: string) {
  return value.length === 8
    ? `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)}`
    : ''
}

async function transfer() {
  if (!canTransfer.value || !sourceDetail.value) return
  const sourceLabel = albums.value.find(album => album.key === sourceAlbum.value)?.label
  const targetLabel = albums.value.find(album => album.key === targetAlbum.value)?.label
  const count = sourceDetail.value.images.length
  if (!confirm(`确定把 ${sourceLabel} / ${formatDate(Number(sourceDate.value))} 的 ${count} 个媒体转移到 ${targetLabel} / ${targetDate.value} 吗？`)) return

  transferring.value = true
  error.value = ''
  message.value = ''
  try {
    const { data } = await http.post('/admin/transfer', {
      sourceAlbumKey: sourceAlbum.value,
      sourceDate: Number(sourceDate.value),
      targetAlbumKey: targetAlbum.value,
      targetDate: Number(targetDate.value.replaceAll('-', '')),
      description: description.value
    })
    message.value = `已成功转移 ${data.count} 个媒体`
    sourceDate.value = ''
    sourceDetail.value = undefined
    const { data: daysData } = await http.get('/admin/days', {
      params: { scope: 'album', albumKey: sourceAlbum.value }
    })
    sourceDays.value = daysData.days.filter((day: Day) => day.imageCount > 0)
    await loadTarget()
  } catch (exception: any) {
    error.value = exception.response?.data?.message || '转移失败，请重试'
  } finally {
    transferring.value = false
  }
}
</script>

<template>
  <main class="transfer-page">
    <div class="container">
      <header>
        <div>
          <button type="button" class="back" @click="router.push('/')">← 返回主页</button>
          <h1>转移媒体</h1>
        </div>
        <router-link to="/" class="album-link">返回相册</router-link>
      </header>

      <p v-if="error" class="notice error">{{ error }}</p>
      <p v-if="message" class="notice success">{{ message }}</p>

      <div class="sections">
        <section>
          <div class="section-title">
            <span class="step">1</span>
            <div>
              <h2>选择原相册</h2>
            </div>
          </div>
          <div class="fields">
            <label>
              <span>相册</span>
              <select v-model="sourceAlbum">
                <option v-for="album in albums" :key="album.key" :value="album.key">{{ album.label }}</option>
              </select>
            </label>
            <label>
              <span>日期</span>
              <select v-model="sourceDate" :disabled="loadingSource">
                <option value="">请选择日期</option>
                <option v-for="day in sourceDays" :key="day.date" :value="String(day.date)">
                  {{ formatDate(day.date) }}（{{ day.imageCount }} 个）
                </option>
              </select>
            </label>
          </div>
          <p v-if="loadingSource" class="placeholder">加载中…</p>
          <div v-else-if="sourceDetail?.images.length" class="preview-block">
            <p class="summary">{{ sourceDetail.images.length }} 个 · {{ sourceDetail.info || '无描述' }}</p>
            <div class="image-grid">
              <div v-for="image in sourceDetail.images" :key="image.id" class="thumb">
                <img :src="mediaUrl(image.previewPath)" loading="lazy">
                <b v-if="image.mediaType==='video'" class="play">▶</b>
                <span v-if="image.photoTime">{{ image.photoTime }}</span>
              </div>
            </div>
          </div>
          <p v-else class="placeholder">选择相册和日期后显示缩略图</p>
        </section>

        <section>
          <div class="section-title">
            <span class="step">2</span>
            <div>
              <h2>选择目标位置</h2>
            </div>
          </div>
          <div class="fields">
            <label>
              <span>相册</span>
              <select v-model="targetAlbum">
                <option v-for="album in albums" :key="album.key" :value="album.key">{{ album.label }}</option>
              </select>
            </label>
            <label>
              <span>日期</span>
              <input v-model="targetDate" type="date">
            </label>
          </div>

          <p v-if="sameLocation" class="target-state invalid">目标位置不能和原位置相同</p>
          <p v-else-if="loadingTarget" class="placeholder">加载中…</p>
          <div v-else-if="targetDetail?.images.length" class="preview-block">
            <p class="target-state existing">目标已有 {{ targetDetail.images.length }} 个媒体，将追加到末尾</p>
            <p class="summary">{{ targetDetail.info || '无描述' }}</p>
            <div class="image-grid">
              <div v-for="image in targetDetail.images" :key="image.id" class="thumb">
                <img :src="mediaUrl(image.previewPath)" loading="lazy">
                <b v-if="image.mediaType==='video'" class="play">▶</b>
              </div>
            </div>
          </div>
          <div v-else-if="/^\d{4}-\d{2}-\d{2}$/.test(targetDate)" class="new-day">
            <p class="target-state empty">此位置为空白，需创建</p>
            <label>
              <span>媒体描述</span>
              <textarea v-model="description" rows="3" placeholder="请输入这一天的描述"></textarea>
            </label>
          </div>
          <p v-else class="placeholder">选择目标相册和日期后显示状态</p>
        </section>
      </div>

      <button class="transfer-button" type="button" :disabled="!canTransfer" @click="transfer">
        {{ transferring ? '正在转移…' : '确认转移媒体' }}
      </button>
    </div>
  </main>
</template>

<style scoped>
.transfer-page{min-height:100vh;padding:24px 16px 60px;background:#f6f6f6;color:#222}
.container{width:min(1180px,100%);margin:0 auto}
header{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;margin-bottom:20px}
h1{margin-top:5px;font-size:26px}
.back{border:0;padding:0;background:none;color:#555;text-decoration:underline;text-underline-offset:3px;cursor:pointer}
.album-link{color:#555;font-size:14px}
.notice{margin-bottom:14px;padding:11px 14px;border-radius:4px;font-size:14px}
.error{background:#fff0f0;color:#b42318}.success{background:#ecfdf3;color:#067647}
.sections{display:grid;grid-template-columns:1fr 1fr;gap:18px}
section{min-width:0;border:1px solid #ddd;border-radius:6px;padding:20px;background:#fff}
.section-title{display:flex;align-items:flex-start;gap:12px;margin-bottom:18px}
.step{display:grid;flex:0 0 28px;height:28px;place-items:center;border-radius:50%;background:#222;color:#fff;font-weight:700}
h2{font-size:19px}
.fields{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:16px}
label{display:block}label>span{display:block;margin-bottom:6px;color:#555;font-size:13px}
select,input,textarea{width:100%;border:1px solid #ccc;border-radius:4px;background:#fff;color:#222;padding:10px 11px;outline:none}
select:focus,input:focus,textarea:focus{border-color:#555}
textarea{resize:vertical;line-height:1.5}
.placeholder{display:grid;min-height:180px;place-items:center;border:1px dashed #d7d7d7;color:#999;font-size:14px}
.summary{margin-bottom:10px;color:#666;font-size:13px}
.image-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:6px;max-height:380px;overflow:auto}
.thumb{position:relative;aspect-ratio:1;overflow:hidden;border-radius:3px;background:#eee}
.thumb img{width:100%;height:100%;object-fit:cover}
.thumb span{position:absolute;right:2px;bottom:2px;left:2px;padding:2px;background:rgba(0,0,0,.55);color:#fff;font-size:9px;text-align:center}.thumb .play{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);display:grid;place-items:center;width:30px;height:30px;border-radius:50%;background:rgba(0,0,0,.55);color:#fff;font-size:12px}
.target-state{margin-bottom:12px;padding:9px 11px;border-radius:3px;font-size:13px}
.existing{background:#eef5ff;color:#175cd3}.empty{background:#fff7e6;color:#a15c00}.invalid{background:#fff0f0;color:#b42318}
.new-day{min-height:180px}.transfer-button{display:block;width:min(420px,100%);margin:22px auto 0;border:0;border-radius:4px;padding:14px;background:#222;color:#fff;font-size:16px;font-weight:600;cursor:pointer}
.transfer-button:disabled{background:#bbb;cursor:not-allowed}
@media(max-width:800px){.sections{grid-template-columns:1fr}.fields{grid-template-columns:1fr 1fr}.image-grid{grid-template-columns:repeat(4,1fr)}}
@media(max-width:520px){.transfer-page{padding:18px 12px 40px}.fields{grid-template-columns:1fr}.image-grid{grid-template-columns:repeat(3,1fr)}section{padding:16px}h1{font-size:23px}}
</style>
