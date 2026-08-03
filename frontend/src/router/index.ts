import { createRouter, createWebHistory } from 'vue-router'
import { http } from '../api/http'
import LoginView from '../views/LoginView.vue'
import TimelineView from '../views/TimelineView.vue'
import DayDetailView from '../views/DayDetailView.vue'
import UploadView from '../views/UploadView.vue'
import RecycleView from '../views/RecycleView.vue'
import AdminView from '../views/AdminView.vue'
import AdminTransferView from '../views/AdminTransferView.vue'
import PhotoView from '../views/PhotoView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/', component: TimelineView },
    { path: '/day/:date', component: DayDetailView },
    { path: '/upload', component: UploadView },
    { path: '/recycle', component: RecycleView },
    { path: '/admin', component: AdminView },
    { path: '/admin_trans', component: AdminTransferView },
    { path: '/view', component: PhotoView }
  ],
  scrollBehavior: (to, from, saved) => {
    if (to.path === '/' && from.path.startsWith('/day/')) {
      const album = sessionStorage.getItem('currentAlbum') || 'peachwuhu'
      const top = Number(sessionStorage.getItem(`timeline-scroll:${album}`) || 0)
      return { top, behavior: 'auto' }
    }
    return saved || { top: 0 }
  }
})

router.beforeEach(async to => {
  if (to.meta.public) return true
  try {
    const { data } = await http.get('/auth/me')
    return data.authenticated ? true : '/login'
  } catch {
    return '/login'
  }
})

export default router
