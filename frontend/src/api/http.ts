import axios from 'axios'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  withCredentials: true
})

http.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401 && !location.pathname.endsWith('/login')) {
      location.href = `${import.meta.env.BASE_URL}login`
    }
    return Promise.reject(error)
  }
)

export const mediaUrl = (path: string) =>
  `${import.meta.env.VITE_MEDIA_BASE || '/media'}/${path.replace(/^\/+/, '')}`
