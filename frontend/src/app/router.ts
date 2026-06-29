import { createRouter, createWebHistory } from 'vue-router'
import AppShell from '@/components/layout/AppShell.vue'
import AdminShell from '@/components/layout/AdminShell.vue'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/auth', name: 'auth', component: () => import('@/views/AuthView.vue') },
    {
      path: '/', component: AppShell, children: [
        { path: '', name: 'home', component: () => import('@/views/user/HomeView.vue') },
        { path: 'discover', name: 'discover', component: () => import('@/views/user/DiscoverView.vue') },
        { path: 'activities/:id', name: 'activity-detail', component: () => import('@/views/user/ActivityDetailView.vue') },
        { path: 'create', name: 'create', component: () => import('@/views/user/CreateActivityView.vue') },
        { path: 'ai-planner', name: 'ai-planner', component: () => import('@/views/user/AiPlannerView.vue') },
        { path: 'check-in', name: 'check-in', component: () => import('@/views/user/CheckInView.vue') },
        { path: 'teams', name: 'teams', component: () => import('@/views/user/TeamsView.vue') },
        { path: 'messages', name: 'messages', component: () => import('@/views/user/MessagesView.vue') },
        { path: 'profile', name: 'profile', component: () => import('@/views/user/ProfileView.vue') },
      ],
    },
    {
      path: '/admin', component: AdminShell, children: [
        { path: '', name: 'admin-dashboard', component: () => import('@/views/admin/DashboardView.vue') },
        { path: 'reviews', name: 'admin-reviews', component: () => import('@/views/admin/ReviewsView.vue') },
        { path: 'users', name: 'admin-users', component: () => import('@/views/admin/UsersView.vue') },
        { path: 'content', name: 'admin-content', component: () => import('@/views/admin/ContentView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', component: () => import('@/views/NotFoundView.vue') },
  ],
})

export default router
