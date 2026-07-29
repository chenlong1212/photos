<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { http } from '../api/http'

const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')
const submit = async () => {
  error.value = ''
  try {
    await http.post('/auth/login', { username: username.value, password: password.value })
    await router.replace('/')
  } catch (e: any) {
    error.value = e.response?.data?.message || '账号或密码错误'
  }
}
</script>

<template>
  <main class="login-page">
    <div class="login-box">
      <div class="brand-title"></div>
      <div class="sub-title">Memories</div>
      <div v-if="error" class="error-msg">{{ error }}</div>
      <form @submit.prevent="submit">
        <div class="input-group"><input v-model="username" type="text" placeholder="Username" required autocomplete="off"></div>
        <div class="input-group"><input v-model="password" type="password" placeholder="Password" required></div>
        <button type="submit">登 录</button>
      </form>
    </div>
  </main>
</template>

<style scoped>
.login-page { background:linear-gradient(135deg,#e0c3fc 0%,#8ec5fc 100%);height:100vh;display:flex;justify-content:center;align-items:center;overflow:hidden }
.login-box { background:rgba(255,255,255,.75);backdrop-filter:blur(20px);padding:40px 30px;border-radius:24px;width:85%;max-width:360px;box-shadow:0 20px 40px rgba(0,0,0,.1);border:1px solid rgba(255,255,255,.6);animation:slideUp .6s ease-out }
.brand-title { font-family:Georgia,serif;font-size:28px;font-weight:800;text-align:center;color:#333;margin-bottom:5px;letter-spacing:1px }
.sub-title { text-align:center;font-size:13px;color:#777;margin-bottom:35px;letter-spacing:2px;text-transform:uppercase }
.input-group { margin-bottom:20px }
input { width:100%;padding:15px 20px;border-radius:12px;border:2px solid transparent;background:rgba(255,255,255,.6);font-size:16px;color:#333;outline:none;transition:all .3s }
input::placeholder{color:#aaa} input:focus{background:#fff;border-color:#a18cd1;box-shadow:0 0 0 4px rgba(161,140,209,.1)}
button { width:100%;padding:16px;border:none;border-radius:50px;background:linear-gradient(120deg,#a18cd1 0%,#fbc2eb 100%);color:#fff;font-size:18px;font-weight:bold;cursor:pointer;margin-top:10px;box-shadow:0 10px 20px rgba(161,140,209,.3);transition:transform .1s }
button:active{transform:scale(.96);opacity:.9}.error-msg{color:#ff3b30;text-align:center;font-size:14px;margin-bottom:15px}
@keyframes slideUp{from{opacity:0;transform:translateY(30px)}to{opacity:1;transform:translateY(0)}}
</style>
