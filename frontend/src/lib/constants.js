export const STATUS_LABELS = {
  SUBMITTED: 'Submitted',
  RECEIVED: 'Received',
  UNDER_REVIEW: 'Under review',
  ASSIGNED: 'Assigned',
  UNDER_INVESTIGATION: 'Under investigation',
  PENDING_ACTION: 'Pending action',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
  REJECTED: 'Rejected',
  ESCALATED: 'Escalated',
  ON_HOLD: 'On hold',
  REOPENED: 'Reopened',
  DUPLICATE: 'Duplicate',
};

export const PRIORITY_LABELS = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  CRITICAL: 'Critical',
};

export const STATUS_OPTIONS = Object.entries(STATUS_LABELS).map(([value, label]) => ({ value, label }));
export const PRIORITY_OPTIONS = Object.entries(PRIORITY_LABELS).map(([value, label]) => ({ value, label }));

export const ALLOWED_TRANSITIONS = {
  SUBMITTED: ['RECEIVED', 'REJECTED', 'DUPLICATE'],
  RECEIVED: ['UNDER_REVIEW', 'REJECTED', 'DUPLICATE', 'ON_HOLD'],
  UNDER_REVIEW: ['ASSIGNED', 'REJECTED', 'DUPLICATE', 'ON_HOLD', 'ESCALATED'],
  ASSIGNED: ['UNDER_INVESTIGATION', 'ESCALATED', 'ON_HOLD', 'REJECTED'],
  UNDER_INVESTIGATION: ['PENDING_ACTION', 'ESCALATED', 'ON_HOLD'],
  PENDING_ACTION: ['RESOLVED', 'ON_HOLD', 'ESCALATED'],
  RESOLVED: ['CLOSED', 'REOPENED'],
  CLOSED: ['REOPENED'],
  REJECTED: ['REOPENED'],
  ESCALATED: ['ASSIGNED', 'UNDER_REVIEW', 'UNDER_INVESTIGATION', 'ON_HOLD'],
  ON_HOLD: ['UNDER_REVIEW', 'ASSIGNED', 'UNDER_INVESTIGATION', 'PENDING_ACTION'],
  REOPENED: ['UNDER_REVIEW', 'ASSIGNED'],
  DUPLICATE: ['CLOSED', 'REOPENED'],
};

export const ALLOWED_FILE_TYPES = [
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'image/jpeg',
  'image/png',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
];

export const MAX_FILE_SIZE = 10 * 1024 * 1024;
