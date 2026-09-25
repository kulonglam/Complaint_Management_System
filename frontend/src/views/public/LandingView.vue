<template>
  <div>
    <section class="relative overflow-hidden bg-[#161310] text-[#f6f1e8]">
      <div class="pointer-events-none absolute -right-24 top-10 h-80 w-80 rounded-full bg-[var(--accent)]/20 blur-3xl" />
      <div class="mx-auto grid max-w-6xl gap-12 px-4 py-20 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
        <div>
          <p class="text-xs font-semibold uppercase tracking-[0.22em] text-[color-mix(in_srgb,var(--accent)_70%,white)]">Accountability, on the record</p>
          <h1 class="mt-4 font-display text-4xl leading-[1.1] sm:text-6xl">Hear every complaint. Close every loop.</h1>
          <p class="mt-5 max-w-xl text-lg leading-7 text-[#d8d0c3]">
            A case workspace for organizations that must receive, investigate, and resolve complaints with a complete audit trail.
          </p>
          <div class="mt-8 flex flex-wrap gap-3">
            <router-link to="/submit-complaint" class="rounded-xl px-5 py-2.5 text-sm font-semibold text-white" style="background: var(--accent)">
              Submit a complaint
            </router-link>
            <router-link to="/track-complaint" class="rounded-xl border border-white/15 px-5 py-2.5 text-sm font-semibold hover:bg-white/5">
              Track a case
            </router-link>
            <router-link to="/login" class="rounded-xl px-5 py-2.5 text-sm font-semibold text-[#dceee9]">Staff sign in</router-link>
          </div>
        </div>
        <article class="surface rounded-3xl p-6 text-ink">
          <p class="text-xs font-semibold uppercase tracking-[0.16em] text-muted">Live case card</p>
          <div class="mt-4 flex items-start justify-between gap-3">
            <div>
              <p class="text-xs text-muted">CMP-2026-000184</p>
              <h2 class="font-display text-2xl">Delayed clinic appointment</h2>
            </div>
            <span class="rounded-full bg-[color-mix(in_srgb,var(--warn)_16%,var(--surface))] px-2.5 py-1 text-xs font-semibold text-[var(--warn)]">Under investigation</span>
          </div>
          <dl class="mt-6 grid grid-cols-2 gap-4 text-sm">
            <div><dt class="text-muted">Due</dt><dd class="font-medium">2 days remaining</dd></div>
            <div><dt class="text-muted">Assignee</dt><dd class="font-medium">Amina Otieno</dd></div>
            <div><dt class="text-muted">Department</dt><dd class="font-medium">Outpatient</dd></div>
            <div><dt class="text-muted">SLA</dt><dd class="font-medium">On track</dd></div>
          </dl>
          <div class="mt-6 h-2 overflow-hidden rounded-full bg-[var(--paper)]">
            <div class="h-full w-2/3 rounded-full" style="background: var(--accent)" />
          </div>
          <p class="mt-3 text-xs text-muted">Investigation started · Evidence attached · Complainant update sent</p>
        </article>
      </div>
    </section>
    <section class="mx-auto max-w-6xl px-4 py-16">
      <div class="max-w-2xl">
        <p class="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--accent)]">Built for institutions</p>
        <h2 class="mt-2 font-display text-3xl">Everything a case needs, nothing extra on the page.</h2>
      </div>
      <div class="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <article v-for="item in features" :key="item.title" class="surface rounded-2xl p-5">
          <h3 class="font-display text-xl">{{ item.title }}</h3>
          <p class="mt-2 text-sm leading-6 text-muted">{{ item.body }}</p>
        </article>
      </div>
    </section>
    <footer class="border-t border-[var(--line)]">
      <div class="mx-auto flex max-w-6xl flex-col gap-2 px-4 py-8 text-sm text-muted sm:flex-row sm:justify-between">
        <p>Complaint Management System</p>
        <p>Secure case handling for accountable institutions.</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
const features = [
  { title: 'Public intake', body: 'Anonymous or named reports, with a tracking code the complainant keeps.' },
  { title: 'Case workspace', body: 'Assignment, comments, evidence, and status changes in one record.' },
  { title: 'Private investigation', body: 'Findings and tasks stay with authorized staff, never on the public track page.' },
  { title: 'SLA and escalation', body: 'Due dates, overdue signals, and a recorded handoff when a case stalls.' },
  { title: 'Live reporting', body: 'Dashboards and exports from the same records officers work every day.' },
  { title: 'Immutable audit', body: 'Who changed what, and when — including exports for review.' },
];
</script>
