const templates = {
  'complaint-received': 'Your complaint has been received.',
  'complaint-assigned': 'A complaint was assigned.',
  'complaint-status-updated': 'A complaint status changed.',
  'complaint-resolved': 'A complaint was resolved.',
  'complaint-closed': 'A complaint was closed.',
  'complaint-reopened': 'A complaint was reopened.',
  'sla-warning': 'A complaint is approaching its SLA.',
  'complaint-overdue': 'A complaint is overdue.',
  'user-invited': 'You have been invited to the complaint platform.',
};

export async function sendEmail({ template, to, data }) {
  // Integration point: connect SMTP, Resend, Postmark, or SES here.
  console.info('[email:skipped]', {
    template,
    to,
    subject: templates[template] || template,
    data,
  });
}
