/**
 * Format a duration in milliseconds to a human-readable string
 * @param milliseconds Duration in milliseconds
 * @returns Formatted string like "2h 30m" or "45m 20s"
 */
export const formatDuration = (milliseconds: number): string => {
  if (!milliseconds || milliseconds <= 0) {
    return '0s';
  }

  const seconds = Math.floor(milliseconds / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  
  if (hours > 0) {
    return `${hours}h ${minutes % 60}m`;
  } else if (minutes > 0) {
    return `${minutes}m ${seconds % 60}s`;
  } else {
    return `${seconds}s`;
  }
};

/**
 * Format a timestamp to a human-readable time string
 * @param timestamp Timestamp in milliseconds
 * @returns Formatted time string like "3:45 PM"
 */
export const formatTime = (timestamp: number): string => {
  const date = new Date(timestamp);
  return date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
};

/**
 * Format a timestamp to a human-readable date string
 * @param timestamp Timestamp in milliseconds
 * @returns Formatted date string like "Jan 15"
 */
export const formatDate = (timestamp: number): string => {
  const date = new Date(timestamp);
  return date.toLocaleDateString([], { month: 'short', day: 'numeric' });
};

/**
 * Get a relative time string (e.g., "2 hours ago", "just now")
 * @param timestamp Timestamp in milliseconds
 * @returns Relative time string
 */
export const getRelativeTimeString = (timestamp: number): string => {
  const now = Date.now();
  const diffInSeconds = Math.floor((now - timestamp) / 1000);
  
  if (diffInSeconds < 60) {
    return 'just now';
  }
  
  const diffInMinutes = Math.floor(diffInSeconds / 60);
  if (diffInMinutes < 60) {
    return `${diffInMinutes} minute${diffInMinutes > 1 ? 's' : ''} ago`;
  }
  
  const diffInHours = Math.floor(diffInMinutes / 60);
  if (diffInHours < 24) {
    return `${diffInHours} hour${diffInHours > 1 ? 's' : ''} ago`;
  }
  
  const diffInDays = Math.floor(diffInHours / 24);
  if (diffInDays < 7) {
    return `${diffInDays} day${diffInDays > 1 ? 's' : ''} ago`;
  }
  
  return formatDate(timestamp);
};

/**
 * Convert minutes to a formatted duration string
 * @param minutes Duration in minutes
 * @returns Formatted string like "2h 30m"
 */
export const minutesToDuration = (minutes: number): string => {
  return formatDuration(minutes * 60 * 1000);
};

/**
 * Get the start and end timestamps for today
 * @returns Object with start and end timestamps
 */
export const getTodayTimeRange = (): { start: number; end: number } => {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0).getTime();
  const end = Date.now();
  return { start, end };
};

/**
 * Get the start and end timestamps for yesterday
 * @returns Object with start and end timestamps
 */
export const getYesterdayTimeRange = (): { start: number; end: number } => {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1, 0, 0, 0).getTime();
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0).getTime() - 1;
  return { start, end };
};

/**
 * Get the start and end timestamps for the last 7 days
 * @returns Object with start and end timestamps
 */
export const getLastWeekTimeRange = (): { start: number; end: number } => {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 6, 0, 0, 0).getTime();
  const end = Date.now();
  return { start, end };
}; 