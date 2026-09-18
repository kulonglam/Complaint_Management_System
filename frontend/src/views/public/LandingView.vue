<template>
  <div>
    <section class="bg-slate-950 text-white">
      <div class="mx-auto grid max-w-6xl gap-10 px-4 py-20 lg:grid-cols-2 lg:items-center">
        <div>
          <p class="text-sm font-semibold uppercase tracking-[0.2em] text-blue-300">Accountability platform</p>
          <h1 class="mt-4 text-4xl font-semibold leading-tight sm:text-5xl">Manage complaints. Build trust. Improve accountability.</h1>
          <p class="mt-4 max-w-xl text-slate-300">
            A secure, multi-tenant case management system for organizations that need to receive, investigate, resolve, and monitor complaints with a complete audit trail.
          </p>
          <div class="mt-8 flex flex-wrap gap-3">
            <router-link to="/submit-complaint" class="rounded-lg bg-blue-600 px-4 py-2.5 font-semibold hover:bg-blue-500">Submit a complaint</router-link>
            <router-link to="/track-complaint" class="rounded-lg border border-white/20 px-4 py-2.5 font-semibold hover:bg-white/10">Track a complaint</router-link>
            <router-link to="/login" class="rounded-lg px-4 py-2.5 font-semibold text-blue-200">Sign in</router-link>
          </div>
        </div>
        <div class="rounded-3xl border border-white/10 bg-white/5 p-6">
          <p class="text-sm text-slate-400">Built for NGOs, government, universities, hospitals, and regulated organizations.</p>
          <ul class="mt-6 space-y-3 text-sm">
            <li>Tenant-isolated records with row-level security</li>
            <li>Role-based permissions, not hardcoded role names</li>
            <li>SLA monitoring, escalation, and audit history</li>
            <li>Public intake with private investigation workspaces</li>
          </ul>
        </div>
      </div>
    </section>
    <section class="mx-auto max-w-6xl px-4 py-16">
      <h2 class="text-2xl font-semibold">Capabilities</h2>
      <div class="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <article v-for="item in features" :key="item.title" class="rounded-2xl border border-slate-200 bg-white p-5">
          <h3 class="font-semibold">{{ item.title }}</h3>
          <p class="mt-2 text-sm text-slate-600">{{ item.body }}</p>
        </article>
      </div>
    </section>
    <footer class="border-t border-slate-200 bg-white">
      <div class="mx-auto flex max-w-6xl flex-col gap-2 px-4 py-8 text-sm text-slate-500 sm:flex-row sm:justify-between">
        <p>Complaint Management System</p>
        <p>Secure case handling for accountable institutions.</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
const features = [
  { title: 'Complaint intake', body: 'Public and internal submission, including anonymous reports with tracking codes.' },
  { title: 'Case management', body: 'Assignment, status workflow, comments, and evidence in one workspace.' },
  { title: 'Investigation', body: 'Findings, recommendations, and tasks that stay internal to authorized staff.' },
  { title: 'SLA monitoring', body: 'Configurable response and resolution times with overdue and escalation signals.' },
  { title: 'Reporting', body: 'Operational dashboards and exportable reports calculated from live data.' },
  { title: 'Audit trail', body: 'Immutable history of who changed what, and when.' },
];
</script>
