import { createRouter, createWebHistory } from 'vue-router'
import DataAnnotation from '@/views/DataAnnotation.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/annotation',
    },
    {
      path: '/annotation/:taskId?',
      name: 'annotation',
      component: DataAnnotation,
    },
  ],
})

export default router
