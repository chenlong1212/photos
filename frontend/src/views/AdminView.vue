<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { http } from '../api/http'

type SummaryRow = {
  scope: 'all' | 'album' | 'recycle'
  key: string
  label: string
  dayCount: number
  imageCount: number
  photoCount: number
  videoCount: number
  videoSize: string
  rawCount: number
  rawSize: string
  rawAverage: string
  previewCount: number
  previewSize: string
  previewAverage: string
}

type DayRow = {
  date: number
  albumKey?: string
  album?: string
  imageCount: number
  photoCount: number
  videoCount: number
  info?: string
  draftInfo: string
  saving?: boolean
  saved?: boolean
}

const summary = ref<{ total: SummaryRow; albums: SummaryRow[]; recycle: SummaryRow }>()
const selected = ref<SummaryRow>()
const detailTitle = ref('')
const days = ref<DayRow[]>([])
const loading = ref(true)
const error = ref('')

const rows = computed(() => {
  if (!summary.value) return []
  return [summary.value.total, ...summary.value.albums, summary.value.recycle]
})

onMounted(async () => {
  try {
    summary.value = (await http.get('/admin/stats')).data
  } catch {
    error.value = '统计数据加载失败'
  } finally {
    loading.value = false
  }
})

async function open(row: SummaryRow) {
  loading.value = true
  error.value = ''
  try {
    const { data } = await http.get('/admin/days', {
      params: {
        scope: row.scope,
        albumKey: row.scope === 'album' ? row.key : undefined
      }
    })
    selected.value = row
    detailTitle.value = data.title
    days.value = data.days.map((day: DayRow) => ({
      ...day,
      draftInfo: day.info || ''
    }))
  } catch {
    error.value = '每日数据加载失败'
  } finally {
    loading.value = false
  }
}

function closeDetail() {
  selected.value = undefined
  days.value = []
  error.value = ''
}

function formatDate(value: number) {
  const text = String(value)
  if (text.length !== 8) return text
  return `${text.slice(0, 4)}-${text.slice(4, 6)}-${text.slice(6, 8)}`
}

async function saveDescription(day: DayRow) {
  if (!day.albumKey || day.saving || day.draftInfo === (day.info || '')) return
  day.saving = true
  day.saved = false
  error.value = ''
  try {
    await http.patch(`/admin/albums/${day.albumKey}/days/${day.date}/description`, {
      info: day.draftInfo
    })
    day.info = day.draftInfo
    day.saved = true
    window.setTimeout(() => {
      day.saved = false
    }, 1500)
  } catch {
    error.value = `${formatDate(day.date)} 的描述保存失败`
  } finally {
    day.saving = false
  }
}
</script>

<template>
  <main class="admin-page">
    <div class="container">
      <header class="header">
        <div>
          <button v-if="selected" class="text-button" type="button" @click="closeDetail">← 统计</button>
          <h1>{{ selected ? detailTitle : '媒体统计' }}</h1>
        </div>
        <router-link to="/" class="text-link">返回相册</router-link>
      </header>

      <p v-if="error" class="message">{{ error }}</p>
      <p v-else-if="loading" class="message">加载中…</p>

      <div v-else-if="!selected" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>相册</th>
              <th>媒体数量</th>
              <th>总天数</th>
              <th>原图占用</th>
              <th>视频占用</th>
              <th>缩略图占用</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in rows"
              :key="`${row.scope}-${row.key}`"
              tabindex="0"
              @click="open(row)"
              @keydown.enter="open(row)"
            >
              <th>{{ row.label }}</th>
              <td>
                {{ row.imageCount }}
                <small>照片 {{ row.photoCount }} / 视频 {{ row.videoCount }}</small>
              </td>
              <td>{{ row.dayCount }}</td>
              <td>
                {{ row.rawSize }}
                <small>原文件 {{ row.rawCount }} 个，平均 {{ row.rawAverage }}</small>
              </td>
              <td>{{ row.videoSize }}</td>
              <td>
                {{ row.previewSize }}
                <small>封面/缩略图 {{ row.previewCount }} 个，平均 {{ row.previewAverage }}</small>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>日期</th>
              <th v-if="selected.scope === 'all'">相册</th>
              <th>媒体数量</th>
              <th>描述</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(day, index) in days" :key="`${day.date}-${day.album || ''}-${index}`" class="plain-row">
              <td class="date-cell">{{ formatDate(day.date) }}</td>
              <td v-if="selected.scope === 'all'" class="album-cell">{{ day.album }}</td>
              <td class="count-cell">{{ day.imageCount }}<small>照片 {{day.photoCount||0}} / 视频 {{day.videoCount||0}}</small></td>
              <td class="description">
                <template v-if="selected.scope !== 'recycle'">
                  <textarea
                    v-model="day.draftInfo"
                    rows="1"
                    aria-label="媒体描述"
                    @keydown.meta.enter="saveDescription(day)"
                    @keydown.ctrl.enter="saveDescription(day)"
                  />
                  <button
                    class="save-button"
                    type="button"
                    :disabled="day.saving || day.draftInfo === (day.info || '')"
                    @click="saveDescription(day)"
                  >
                    {{ day.saving ? '保存中' : day.saved ? '已保存' : '保存' }}
                  </button>
                </template>
                <span v-else>—</span>
              </td>
            </tr>
            <tr v-if="!days.length" class="plain-row">
              <td :colspan="selected.scope === 'all' ? 4 : 3" class="empty">暂无记录</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </main>
</template>

<style scoped>
.admin-page {
  min-height: 100vh;
  padding: 24px 16px;
  background: #fff;
  color: #222;
}

.container {
  width: min(1100px, 100%);
  margin: 0 auto;
}

.header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
}

h1 {
  margin-top: 4px;
  font-size: 24px;
  font-weight: 600;
}

.text-link,
.text-button {
  border: 0;
  background: none;
  color: #555;
  font-size: 14px;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.text-button {
  padding: 0;
}

.message {
  padding: 40px 0;
  color: #666;
  text-align: center;
}

.table-wrap {
  overflow-x: auto;
  border: 1px solid #d9d9d9;
}

table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
  background: #fff;
  text-align: left;
}

th,
td {
  padding: 13px 16px;
  border-bottom: 1px solid #e5e5e5;
  vertical-align: top;
}

thead th {
  background: #f5f5f5;
  color: #444;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

tbody th {
  font-weight: 600;
}

tbody tr:not(.plain-row) {
  cursor: pointer;
}

tbody tr:not(.plain-row):hover,
tbody tr:not(.plain-row):focus {
  background: #f7f7f7;
  outline: none;
}

tbody tr:last-child th,
tbody tr:last-child td {
  border-bottom: 0;
}

small {
  display: block;
  margin-top: 3px;
  color: #777;
  font-size: 12px;
  white-space: nowrap;
}

.description {
  min-width: 320px;
  line-height: 1.6;
}

.date-cell,
.album-cell,
.count-cell {
  white-space: nowrap;
}

.description {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.description textarea {
  width: 100%;
  min-width: 260px;
  min-height: 34px;
  resize: vertical;
  border: 1px solid #cfcfcf;
  border-radius: 2px;
  padding: 6px 8px;
  background: #fff;
  color: #222;
  line-height: 1.4;
}

.description textarea:focus {
  border-color: #777;
  outline: 1px solid #777;
}

.save-button {
  flex: 0 0 auto;
  min-width: 58px;
  height: 34px;
  border: 1px solid #aaa;
  border-radius: 2px;
  background: #f5f5f5;
  color: #222;
  cursor: pointer;
}

.save-button:disabled {
  color: #999;
  cursor: default;
}

.empty {
  padding: 40px;
  color: #777;
  text-align: center;
}

@media (max-width: 640px) {
  .admin-page {
    padding: 18px 12px;
  }

  h1 {
    font-size: 21px;
  }

  th,
  td {
    padding: 11px 12px;
  }
}
</style>
