<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Sortable from 'sortablejs'
import { http, mediaUrl } from '../api/http'
import { detectPhotoTime } from '../utils/photoTime'

type Image = {id:number;rawPath:string;previewPath:string;photoTime:string;isCover:boolean}
const route=useRoute(),router=useRouter(),date=Number(route.params.date)
const album=ref(sessionStorage.getItem('currentAlbum')||'peachwuhu')
const info=ref(''),images=ref<Image[]>([]),listMode=ref(localStorage.getItem('viewStyle')==='list')
const loading=ref(''),grid=ref<HTMLElement>(),fileInput=ref<HTMLInputElement>()
async function load(){const {data}=await http.get(`/albums/${album.value}/days/${date}`);info.value=data.info;images.value=data.images;await nextTick();if(grid.value)new Sortable(grid.value,{animation:150,filter:'.static-item',delay:150,delayOnTouchOnly:true})}
function toggleStyle(){listMode.value=!listMode.value;localStorage.setItem('viewStyle',listMode.value?'list':'grid')}
function covers(){return [...document.querySelectorAll<HTMLElement>('.grid-item.cover-selected:not(.static-item)')].map(x=>Number(x.dataset.id))}
function toggleCover(id:number){const image=images.value.find(x=>x.id===id)!;const count=images.value.filter(x=>x.isCover).length;if(!image.isCover&&count>=9)return alert('封面最多只能选择 9 张');image.isCover=!image.isCover}
function sort(){images.value.sort((a,b)=>a.photoTime&&b.photoTime?a.photoTime.localeCompare(b.photoTime):a.photoTime?-1:b.photoTime?1:0)}
async function save(){const selected=covers();if(selected.length&&![1,3,4,8,9].includes(selected.length))return alert('封面图数量只能是 1、3、4、8 或 9 张');loading.value='正在更新...';const order=[...document.querySelectorAll<HTMLElement>('.grid-item:not(.static-item)')].map(x=>Number(x.dataset.id));await http.put(`/albums/${album.value}/days/${date}`,{info:info.value,order,covers:selected});await router.push('/')}
async function remove(id:number){if(!confirm('确定要删除吗？'))return;loading.value='正在删除...';await http.delete(`/albums/${album.value}/images/${id}`);loading.value='';await load()}
async function appendPhotos(event:Event){
  const input=event.target as HTMLInputElement
  const files=[...(input.files||[])]
  if(!files.length)return
  const form=new FormData()
  form.append('date',String(date))
  form.append('info',info.value)
  form.append('updateInfo','true')
  for(const file of files){
    const photoTime=await detectPhotoTime(file)
    form.append('photoTimes',photoTime)
    form.append('photos',file)
  }
  loading.value='正在上传...'
  try{
    await http.post(`/albums/${album.value}/images`,form,{
      onUploadProgress:e=>loading.value=`正在上传... ${Math.round((e.loaded/(e.total||e.loaded))*100)}%`
    })
    loading.value='上传成功，刷新中...'
    await load()
  }finally{
    input.value=''
    loading.value=''
  }
}
function open(rawPath:string){router.push({path:'/view',query:{src:rawPath}})}
onMounted(load)
</script>
<template><main class="detail-page">
  <div class="loading-overlay" :class="{show:loading}"><div class="spinner"></div><div class="loading-text">{{loading}}</div></div>
  <div class="navbar"><a class="back-btn" @click="router.push('/')"><i/> 返回</a><div class="nav-center"><div class="nav-title">{{String(date).replace(/(....)(..)(..)/,'$1.$2.$3')}}</div><button class="style-toggle-btn" @click="toggleStyle">{{listMode?'≣':'⬜'}}</button></div><div class="nav-actions"><button class="sort-btn" @click="sort">整理</button><button class="save-btn" @click="save">保存</button></div></div>
  <div class="container"><div class="text-area-wrapper"><textarea v-model="info" class="story-input" placeholder="点击编辑文字..."/></div>
    <div ref="grid" class="grid" :class="{'list-mode':listMode}">
      <div v-for="image in images" :key="image.id" class="grid-item fade-in" :class="{'cover-selected':image.isCover}" :data-id="image.id" :data-photo-time="image.photoTime">
        <div class="bg-img" :style="{backgroundImage:`url(${mediaUrl(image.previewPath)})`}" @click="open(image.rawPath)"/>
        <img class="real-img" :src="mediaUrl(image.previewPath)" loading="lazy" @click="open(image.rawPath)">
        <div v-if="image.photoTime" class="photo-time-tag">{{image.photoTime}}</div>
        <div class="cover-btn-wrapper"><button class="cover-btn" @click.stop="toggleCover(image.id)">封面</button></div>
        <div class="delete-btn-wrapper"><button class="delete-btn" @click.stop="remove(image.id)">✕</button></div>
      </div><div class="grid-item add-photo-item static-item" @click="fileInput?.click()"><span class="add-icon">+</span><input ref="fileInput" class="hidden-file-input" type="file" multiple accept="image/*" @change="appendPhotos"></div>
    </div>
  </div>
</main></template>
<style scoped>
.detail-page{min-height:100vh;padding-top:50px;padding-bottom:80px}.navbar{position:fixed;top:0;left:0;width:100%;height:50px;background:rgba(255,255,255,.98);border-bottom:.5px solid #eee;display:flex;align-items:center;justify-content:space-between;padding:0 15px;z-index:100}.back-btn{color:#333;font-size:16px;display:flex;align-items:center;cursor:pointer}.back-btn i{width:10px;height:10px;border-top:2px solid #333;border-left:2px solid #333;transform:rotate(-45deg);margin-right:2px}.nav-center,.nav-actions{display:flex;align-items:center;gap:8px}.nav-title{font-size:16px;font-weight:600}.style-toggle-btn{background:none;border:1px solid #ddd;border-radius:12px;padding:2px 8px;font-size:12px}.sort-btn{border:1px solid #ddd;background:#fff;color:#555;padding:6px 10px;border-radius:4px;font-size:13px}.save-btn{border:0;background:#07c160;color:#fff;padding:6px 14px;border-radius:4px;font-size:14px;font-weight:bold}.container{padding:20px}.text-area-wrapper{width:100%;margin-bottom:20px}.story-input{width:100%;min-height:150px;border:0;background:transparent;font-size:16px;line-height:1.5;color:#333;resize:none;outline:0;padding:0}.grid{display:grid;gap:8px;grid-template-columns:repeat(3,1fr)}@media(min-width:600px){.grid{grid-template-columns:repeat(4,1fr)}}.grid-item{position:relative;aspect-ratio:1;border-radius:4px;overflow:hidden;background:#f2f2f2;user-select:none}.bg-img{width:100%;height:100%;background-size:cover;background-position:center;cursor:pointer}.real-img{display:none}.grid.list-mode{grid-template-columns:repeat(2,1fr);gap:15px;align-items:start}.grid.list-mode .grid-item{aspect-ratio:auto;height:auto;border-radius:8px;background:transparent;box-shadow:0 4px 12px rgba(0,0,0,.08);overflow:visible}.grid.list-mode .bg-img{display:none}.grid.list-mode .real-img{display:block;width:100%;height:auto;border-radius:8px}.photo-time-tag{position:absolute;bottom:4px;left:5px;color:rgba(255,255,255,.95);font-size:10px;font-weight:600;z-index:5;text-shadow:0 1px 2px rgba(0,0,0,.8)}.cover-btn-wrapper{position:absolute;top:2px;left:2px;z-index:10}.cover-btn{min-width:42px;height:20px;padding:0 6px;border:0;border-radius:10px;background:rgba(0,0,0,.4);color:#fff;font-size:11px}.cover-selected{box-shadow:inset 0 0 0 3px #2f7cff}.cover-selected .cover-btn{background:#2f7cff}.delete-btn-wrapper{position:absolute;top:2px;right:2px;z-index:10}.delete-btn{width:20px;height:20px;background:rgba(0,0,0,.4);color:#fff;border-radius:50%;border:0}.add-photo-item{background:#f7f7f7;display:flex;align-items:center;justify-content:center;cursor:pointer}.add-icon{font-size:40px;color:#ddd;font-weight:300}.list-mode .add-photo-item{height:200px;border:2px dashed #ddd}.loading-overlay{position:fixed;inset:0;background:rgba(255,255,255,.8);backdrop-filter:blur(5px);display:flex;flex-direction:column;align-items:center;justify-content:center;z-index:999;opacity:0;pointer-events:none}.loading-overlay.show{opacity:1;pointer-events:auto}.spinner{width:40px;height:40px;border:4px solid #07c160;border-top-color:#b2e6c6;border-radius:50%;animation:spin 1s linear infinite}.loading-text{margin-top:15px;color:#666;font-size:14px;font-weight:bold}@keyframes spin{to{transform:rotate(360deg)}}
.hidden-file-input{position:absolute;width:1px;height:1px;opacity:0;overflow:hidden;pointer-events:none}
</style>
