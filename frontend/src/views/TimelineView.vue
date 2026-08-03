<script setup lang="ts">
import { computed, nextTick, onActivated, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { http, mediaUrl } from '../api/http'

type Album = { key: string; label: string }
type Day = {
  date: number
  startDate: number
  endDate: number
  info: string
  count: number
  covers: { previewPath: string }[]
  coverCount: number
  coverLayout: number
}
const router = useRouter()
const route = useRoute()
const albums = ref<Album[]>([])
const current = ref(sessionStorage.getItem('currentAlbum') || 'peachwuhu')
const label = computed(() => albums.value.find(a => a.key === current.value)?.label || current.value)
const days = ref<Day[]>([])
const menuOpen = ref(false)
const search = ref('')
const years = computed(() => [...new Set(days.value.map(d => String(d.startDate).slice(0,4)))])
const groups = computed(() => years.value.map(year => ({
  year,
  days: days.value.filter(d => String(d.startDate).startsWith(year))
})))
const scrollKey = computed(() => `timeline-scroll:${current.value}`)
const timelinePage=ref<HTMLElement>()

async function load() {
  albums.value = (await http.get('/albums')).data
  const response = await http.get(`/albums/${current.value}/timeline`)
  days.value = response.data.days
}
async function selectAlbum(key:string) {
  await http.post(`/albums/${key}/select`)
  current.value = key
  sessionStorage.setItem('currentAlbum', key)
  menuOpen.value = false
  await load()
}
async function logout() { await http.post('/auth/logout'); await router.replace('/login') }
function jump(year:string) { document.getElementById(`year-${year}`)?.scrollIntoView({behavior:'smooth'}) }
function doSearch(event:KeyboardEvent) {
  if (event.key !== 'Enter' || !search.value.trim()) return
  const row = [...document.querySelectorAll<HTMLElement>('.day-row')].find(el =>
    el.querySelector('.col-info')?.textContent?.toLowerCase().includes(search.value.toLowerCase().trim()))
  if (!row) return alert('未找到相关回忆 🍃')
  row.scrollIntoView({behavior:'smooth',block:'center'}); row.classList.add('highlight-anim')
  setTimeout(() => row.classList.remove('highlight-anim'), 1500)
}
function openDay(day:Day) {
  sessionStorage.setItem(scrollKey.value, String(window.scrollY))
  sessionStorage.setItem('timeline-return-ready', 'true')
  document.getElementById('timeline-swipe-backdrop')?.remove()
  const backdrop=document.createElement('div')
  backdrop.id='timeline-swipe-backdrop'
  const snapshot=timelinePage.value?.cloneNode(true) as HTMLElement|undefined
  if(snapshot){
    backdrop.style.cssText='position:fixed;inset:0;overflow:hidden;background:#fff;pointer-events:none;z-index:0'
    snapshot.style.cssText=`position:absolute;left:0;top:${-window.scrollY}px;width:100%;min-height:100vh`
    backdrop.appendChild(snapshot)
    document.body.appendChild(backdrop)
  }
  router.push(`/day/${day.date}`)
}
function datePart(date:number, start:number, end:number) { return Number(String(date).slice(start,end)) }
function isDateRange(day: Day) {
  return day.startDate !== day.endDate
}
function rangeEnd(day: Day) {
  const start = String(day.startDate)
  const end = String(day.endDate)
  const endDay = Number(end.slice(6, 8))
  const sameYear = start.slice(0, 4) === end.slice(0, 4)
  const sameMonth = start.slice(4, 6) === end.slice(4, 6)
  if (sameYear && sameMonth) return `${endDay}日`

  const endMonth = Number(end.slice(4, 6))
  if (sameYear) return `${endMonth}月${endDay}日`
  return `${end.slice(0, 4)}年${endMonth}月${endDay}日`
}
async function loadAndRestorePosition() {
  await load()
  const focus = Number(route.query.focus)
  if (!focus) return
  await nextTick()
  const rows = [...document.querySelectorAll<HTMLElement>('.day-row')]
  const target = rows.find(row => Number(row.dataset.date) === focus) ||
    rows.reduce<HTMLElement|null>((nearest, row) => {
      if (!nearest) return row
      return Math.abs(Number(row.dataset.date) - focus) < Math.abs(Number(nearest.dataset.date) - focus) ? row : nearest
    }, null)
  target?.scrollIntoView({block:'center'})
}
onMounted(loadAndRestorePosition)
onActivated(async()=>{
  const saved = sessionStorage.getItem(scrollKey.value)
  if (saved === null) return
  await nextTick()
  if(timelinePage.value)timelinePage.value.scrollTop=Number(saved)
  window.setTimeout(()=>{
    window.scrollTo({top:Number(saved),behavior:'instant'})
    document.getElementById('timeline-swipe-backdrop')?.remove()
  },280)
  sessionStorage.removeItem('timeline-return-ready')
})
</script>

<template>
  <main ref="timelinePage" class="timeline-page" @click="menuOpen=false">
    <div class="header-hero">
      <div class="menu-container" @click.stop>
        <div class="menu-btn" @click="menuOpen=!menuOpen">
          <svg class="user-icon" viewBox="0 0 24 24"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>{{ label }}
        </div>
        <div class="dropdown-menu" :class="{show:menuOpen}">
          <button v-for="album in albums" :key="album.key" class="menu-item" :class="{active:current===album.key}" @click="selectAlbum(album.key)">
            {{ album.label }} <span class="check-icon">✓</span>
          </button>
          <div class="menu-divider"></div>
          <router-link to="/admin" class="menu-item">管理面板</router-link>
          <router-link to="/admin_trans" class="menu-item">转移图片</router-link>
          <router-link to="/recycle" class="menu-item">回收站 ♻️</router-link>
          <button class="menu-item logout" @click="logout">退出登录</button>
        </div>
      </div>
      <div class="header-title">MEMORIES</div><div class="header-subtitle">PEACH &amp; WUHU</div>
      <div class="year-selector-container"><select id="year-select" @change="jump(($event.target as HTMLSelectElement).value)"><option value="" disabled selected>Year</option><option v-for="year in years" :key="year">{{ year }}</option></select></div>
      <div class="search-container"><input v-model="search" type="search" id="search-input" placeholder="🔍" @keypress="doSearch"></div>
      <router-link to="/upload" class="upload-btn">📷 记录回忆</router-link>
    </div>
    <div class="timeline-container">
      <template v-for="group in groups" :key="group.year">
        <div class="year-separator" :id="`year-${group.year}`">{{ group.year }}</div>
        <div v-for="day in group.days" :key="day.date" class="day-row" :data-date="day.date" @click="openDay(day)">
          <div class="col-date">
            <span class="date-month">{{ datePart(day.startDate,4,6) }}月</span>
            <div v-if="isDateRange(day)" class="date-range-lines">
              <span class="date-day">{{ datePart(day.startDate,6,8) }}日</span>
              <span class="date-day">-</span>
              <span class="date-day">{{ rangeEnd(day) }}</span>
            </div>
            <span v-else class="date-day">{{ datePart(day.startDate,6,8) }}日</span>
          </div>
          <div class="col-imgs"><div class="cover-box" :class="`cover-grid-${day.coverLayout}`">
            <div v-for="cover in day.covers" :key="cover.previewPath" class="cover-tile"><img :src="mediaUrl(cover.previewPath)" loading="lazy"></div>
            <div v-if="[3,8].includes(day.coverCount)" class="cover-tile cover-tile-empty"></div>
            <div v-if="day.count>day.coverCount" class="count-badge">+{{ day.count-day.coverCount }}</div>
          </div></div>
          <div class="col-info">{{ day.info?.length>50 ? day.info.slice(0,47)+'...' : day.info }}</div>
        </div>
      </template>
    </div>
  </main>
</template>

<style scoped>
.timeline-page{min-height:100vh;padding-bottom:80px}.header-hero{height:28vh;background:linear-gradient(120deg,#e0c3fc 0%,#8ec5fc 100%);display:flex;flex-direction:column;justify-content:center;align-items:center;position:relative}.header-title{font-size:28px;font-weight:800;color:#fff;text-shadow:0 4px 10px rgba(0,0,0,.1);letter-spacing:4px;font-family:Georgia,serif}.header-subtitle{font-size:12px;color:rgba(255,255,255,.9);margin-top:8px;letter-spacing:1px}
.menu-container{position:absolute;top:20px;left:20px;z-index:50}.menu-btn{height:32px;padding:0 12px;border-radius:16px;background:rgba(255,255,255,.25);backdrop-filter:blur(5px);display:flex;align-items:center;color:#fff;cursor:pointer;font-size:13px;font-weight:bold}.user-icon{width:14px;height:14px;fill:currentColor;margin-right:6px}.dropdown-menu{position:absolute;top:40px;left:0;background:rgba(255,255,255,.95);backdrop-filter:blur(10px);border-radius:8px;padding:5px;min-width:120px;box-shadow:0 4px 15px rgba(0,0,0,.15);display:none;flex-direction:column;animation:scaleIn .2s ease}.dropdown-menu.show{display:flex}.menu-item{border:0;background:none;padding:10px 15px;font-size:14px;color:#333;text-decoration:none;border-radius:6px;display:flex;justify-content:space-between;white-space:nowrap;cursor:pointer}.menu-item.active{background:#eef2ff;color:#6a11cb;font-weight:bold}.check-icon{visibility:hidden}.active .check-icon{visibility:visible}.menu-divider{height:1px;background:#eee;margin:4px 0}.menu-item.logout{color:#ff4757}
.year-selector-container{position:absolute;top:20px;right:20px}#year-select{padding:6px 12px;border-radius:20px;border:none;background:rgba(255,255,255,.25);color:#fff;font-size:13px;font-weight:bold;outline:none;appearance:none;text-align:center;min-width:60px}.search-container{position:absolute;top:60px;right:20px}#search-input{width:32px;height:32px;border-radius:50%;border:none;background:rgba(255,255,255,.25);color:#fff;font-size:14px;outline:none;text-align:center;padding:0;transition:width .4s cubic-bezier(.18,.89,.32,1.28),background .3s,border-radius .3s}#search-input::placeholder{color:#fff}#search-input:focus{width:160px;border-radius:20px;background:rgba(255,255,255,.4);padding-left:15px;text-align:left}.upload-btn{position:absolute;bottom:-20px;background:#333;color:#fff;text-decoration:none;padding:12px 30px;border-radius:50px;font-size:14px;font-weight:bold;box-shadow:0 8px 20px rgba(0,0,0,.2);z-index:20}
.timeline-container{max-width:600px;margin:0 auto;padding-top:30px}.year-separator{position:sticky;top:0;z-index:10;background:rgba(255,255,255,.9);backdrop-filter:blur(10px);padding:15px 20px 10px;font-size:32px;font-weight:900;color:#000;letter-spacing:-1px}.year-separator:after{content:'';display:block;width:40px;height:4px;background:#000;margin-top:5px;border-radius:2px}.day-row{display:flex;align-items:flex-start;padding:25px 15px;cursor:pointer}.col-date{width:18%;padding-right:12px;text-align:right;flex-shrink:0;display:flex;flex-direction:column;align-items:flex-end}.date-month{font-size:24px;font-weight:bold;line-height:1}.date-day{font-size:12px;color:#999;font-weight:500;margin-top:4px}.date-range-lines{display:flex;flex-direction:column;align-items:flex-end;height:36px;margin-top:4px}.date-range-lines .date-day{line-height:12px;margin-top:0}.col-imgs{width:32%;padding-right:12px;flex-shrink:0}.cover-box{position:relative;width:100%;aspect-ratio:1;border-radius:6px;overflow:hidden;background:#f0f0f2;box-shadow:0 2px 8px rgba(0,0,0,.05)}.cover-grid-4,.cover-grid-9{display:grid;gap:2px;background:#fff}.cover-grid-4{grid-template-columns:repeat(2,1fr)}.cover-grid-9{grid-template-columns:repeat(3,1fr)}.cover-tile{width:100%;height:100%;overflow:hidden;background:#f0f0f2;aspect-ratio:1}.cover-tile-empty{background:transparent}.cover-box img{width:100%;height:100%;object-fit:cover;display:block}.count-badge{position:absolute;bottom:5px;right:5px;background:rgba(0,0,0,.38);color:#fff;font-size:10px;padding:1px 5px;border-radius:4px}.col-info{width:50%;font-size:15px;color:#444;line-height:1.6;word-wrap:break-word;text-align:justify}.highlight-anim{animation:flashHighlight 1.5s ease-out}@keyframes flashHighlight{20%{background:rgba(255,240,100,.4)}}@keyframes scaleIn{from{opacity:0;transform:scale(.9)}}
</style>
