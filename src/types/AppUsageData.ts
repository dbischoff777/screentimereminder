export interface AppUsageData {
  name: string;
  time: number;
  lastUsed?: Date | string | null;
  category?: string;
  icon?: string;
  color?: string;
} 