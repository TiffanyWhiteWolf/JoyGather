import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './app/router'
import './lib/design-tokens.css'
import './style.css'

createApp(App).use(createPinia()).use(router).mount('#app')
