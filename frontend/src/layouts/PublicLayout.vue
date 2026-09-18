<template>
  <div class="min-h-screen bg-slate-50">
    <header class="border-b border-slate-200 bg-white">
      <div class="mx-auto flex max-w-6xl items-center justify-between px-4 py-4">
        <router-link to="/" class="font-semibold text-slate-900">Complaint Management</router-link>
        <nav class="flex items-center gap-4 text-sm">
          <router-link to="/submit-complaint">Submit</router-link>
          <router-link to="/track-complaint">Track</router-link>
          <router-link to="/help">Help</router-link>
          <router-link to="/login" class="font-semibold text-blue-700">Sign in</router-link>
        </nav>
      </div>
    </header>
    <router-view />
  </div>
</template>
