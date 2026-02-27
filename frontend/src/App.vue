<template>
  <div class="app-layout">
    <AppSidebar />
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import AppSidebar from './components/AppSidebar.vue'
</script>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
  position: relative;
  z-index: 1;
}

.app-main {
  flex: 1;
  margin-left: var(--sidebar-width);
  padding: 28px 32px;
  min-width: 0;
}
</style>
