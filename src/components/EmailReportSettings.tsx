import { useState, useEffect, useRef } from 'react';
import { TextInput, Button, Text, Stack } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { FiChevronDown, FiSend } from 'react-icons/fi';
import { useScreenTime } from '../context/ScreenTimeContext';
import { ReportScheduler } from '../services/reportScheduler';

export interface EmailSettings {
  email: string;
  frequency: 'daily' | 'weekly' | 'monthly';
  preferredTime?: string;
  weeklyDay?: string;
  monthlyDate?: string;
  enabled: boolean;
}

interface EmailReportSettingsProps {
  onSave: (settings: EmailSettings) => void;
}

// Custom dropdown component
const CustomDropdown = ({ 
  options, 
  value, 
  onChange, 
  label 
}: { 
  options: { value: string, label: string }[], 
  value: string, 
  onChange: (val: string) => void,
  label: string
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const toggleRef = useRef<HTMLDivElement>(null);
  
  // Force these dropdowns to open upward
  const openUpward = label === "Day of Week" || label === "Day of Month";
  
  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);
  
  // Calculate dropdown position and dimensions
  useEffect(() => {
    if (isOpen && toggleRef.current) {
      const rect = toggleRef.current.getBoundingClientRect();
      
      // Find and position dropdown
      const dropdown = document.querySelector('.custom-dropdown-menu') as HTMLElement;
      if (dropdown) {
        dropdown.style.width = `${rect.width}px`;
        dropdown.style.left = `${rect.left}px`;
        
        if (openUpward) {
          // Position above the toggle button
          dropdown.style.bottom = `${window.innerHeight - rect.top}px`;
          dropdown.style.top = 'auto';
        } else {
          // Position below for other dropdowns
          dropdown.style.top = `${rect.bottom}px`;
          dropdown.style.bottom = 'auto';
        }
      }
    }
  }, [isOpen, openUpward]);

  const selectedOption = options.find(opt => opt.value === value);
  
  const dropdownClass = openUpward ? "position-top" : "position-bottom";

  return (
    <div className="dropdown-wrapper" style={{ marginBottom: '1rem' }} ref={dropdownRef}>
      <Text style={{ 
        color: '#AAAAAA', 
        marginBottom: '0.5rem', 
        fontSize: '0.9rem',
        textAlign: 'left'
      }}>
        {label}
      </Text>
      
      <div 
        ref={toggleRef}
        onClick={() => setIsOpen(!isOpen)}
        className={`dropdown-toggle ${isOpen ? 'dropdown-toggle-open' : ''}`}
        style={{
          backgroundColor: 'rgba(0, 0, 32, 0.5)',
          color: '#FFFFFF',
          border: '1px solid rgba(255, 255, 255, 0.1)',
          fontSize: '1.2rem',
          textAlign: 'center',
          cursor: 'pointer',
          padding: '8px 16px',
          borderRadius: '4px',
          width: '100%',
          boxShadow: '0 0 10px rgba(0, 255, 255, 0.1)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          transition: 'all 0.3s ease'
        }}
      >
        <span>{selectedOption?.label}</span>
        <FiChevronDown 
          size={16} 
          color={isOpen ? '#00FFFF' : '#AAAAAA'} 
          style={{ 
            transform: isOpen ? 'rotate(180deg)' : 'rotate(0deg)',
            transition: 'transform 0.3s ease'
          }} 
        />
      </div>
      
      {isOpen && (
        <div 
          className={`custom-dropdown-menu ${dropdownClass}`}
          style={{
            position: 'fixed',
            backgroundColor: 'rgba(0, 0, 32, 0.95)',
            border: '1px solid rgba(255, 0, 255, 0.3)',
            borderRadius: '4px',
            zIndex: 1000,
            maxHeight: '200px',
            overflowY: 'auto',
            boxShadow: openUpward ? '0 -8px 16px rgba(0, 0, 0, 0.6)' : '0 8px 16px rgba(0, 0, 0, 0.6)',
            backdropFilter: 'blur(10px)'
          }}
        >
          {options.map(option => (
            <div
              key={option.value}
              className="custom-dropdown-option"
              onClick={() => {
                onChange(option.value);
                setIsOpen(false);
              }}
              style={{
                padding: '10px 16px',
                cursor: 'pointer',
                backgroundColor: option.value === value ? 'rgba(0, 255, 255, 0.2)' : 'transparent',
                color: option.value === value ? '#00FFFF' : '#FFFFFF',
                borderLeft: option.value === value ? '3px solid #00FFFF' : 'none',
                textAlign: 'center',
                fontWeight: option.value === value ? 'bold' : 'normal'
              }}
            >
              {option.label}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export function EmailReportSettings({ onSave }: EmailReportSettingsProps) {
  const [email, setEmail] = useState('');
  const [frequency, setFrequency] = useState<'daily' | 'weekly' | 'monthly'>('daily');
  const [preferredTime, setPreferredTime] = useState('18:00');
  const [weeklyDay, setWeeklyDay] = useState('Monday');
  const [monthlyDate, setMonthlyDate] = useState('1');
  const [isSending, setIsSending] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  
  const { appUsageData } = useScreenTime();

  const validateEmail = (email: string) => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  };

  const handleSendNow = async () => {
    if (!email || !validateEmail(email)) {
      notifications.show({
        title: 'Error',
        message: 'Please enter a valid email address',
        color: 'red'
      });
      return;
    }

    setIsSending(true);
    try {
      const reportScheduler = ReportScheduler.getInstance();
      await reportScheduler.sendImmediateReport(email, appUsageData);
      
      notifications.show({
        title: 'Success',
        message: 'Report sent successfully! Please check your email.',
        color: 'teal'
      });
    } catch (error) {
      console.error('Failed to send report:', error);
      notifications.show({
        title: 'Error',
        message: error instanceof Error ? error.message : 'Failed to send report. Please check your email configuration.',
        color: 'red'
      });
    } finally {
      setIsSending(false);
    }
  };

  const handleSave = async () => {
    if (!email || !validateEmail(email)) {
      notifications.show({
        title: 'Error',
        message: 'Please enter a valid email address',
        color: 'red'
      });
      return;
    }

    setIsSaving(true);
    try {
      const settings: EmailSettings = {
        email,
        frequency,
        preferredTime,
        weeklyDay: frequency === 'weekly' ? weeklyDay : undefined,
        monthlyDate: frequency === 'monthly' ? monthlyDate : undefined,
        enabled: true
      };

      await onSave(settings);

      notifications.show({
        title: 'Success',
        message: 'Settings saved successfully! Your reports will be delivered according to your schedule.',
        color: 'teal'
      });
    } catch (error) {
      console.error('Failed to save settings:', error);
      notifications.show({
        title: 'Error',
        message: error instanceof Error ? error.message : 'Failed to save settings. Please try again.',
        color: 'red'
      });
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Stack gap="md">
      <TextInput
        label="Email Address"
        placeholder="Enter your email"
        value={email}
        onChange={(event) => setEmail(event.currentTarget.value)}
        error={email && !validateEmail(email) ? 'Please enter a valid email address' : null}
        styles={{
          label: { color: '#c4c4c4' },
          input: {
            backgroundColor: '#1a1b1e',
            color: '#c4c4c4',
            border: '1px solid #373A40',
            '&:focus': {
              borderColor: '#4DABF7'
            }
          }
        }}
      />

      <CustomDropdown
        label="Report Frequency"
        value={frequency}
        onChange={(value) => setFrequency(value as 'daily' | 'weekly' | 'monthly')}
        options={[
          { value: 'daily', label: 'Daily' },
          { value: 'weekly', label: 'Weekly' },
          { value: 'monthly', label: 'Monthly' }
        ]}
      />

      <TextInput
        label="Preferred Time"
        type="time"
        value={preferredTime}
        onChange={(e) => setPreferredTime(e.target.value)}
        styles={{
          label: { color: '#c4c4c4' },
          input: {
            backgroundColor: 'rgba(0, 0, 32, 0.5)',
            color: '#FFFFFF',
            border: '1px solid rgba(255, 255, 255, 0.1)',
            '&:focus': {
              borderColor: '#00FFFF'
            }
          }
        }}
      />

      {frequency === 'weekly' && (
        <CustomDropdown
          label="Day of Week"
          value={weeklyDay}
          onChange={setWeeklyDay}
          options={[
            'Monday',
            'Tuesday',
            'Wednesday',
            'Thursday',
            'Friday',
            'Saturday',
            'Sunday'
          ].map(day => ({ value: day, label: day }))}
        />
      )}

      {frequency === 'monthly' && (
        <CustomDropdown
          label="Day of Month"
          value={monthlyDate}
          onChange={setMonthlyDate}
          options={Array.from({ length: 31 }, (_, i) => {
            const day = (i + 1).toString();
            return { value: day, label: day };
          })}
        />
      )}

      <Stack gap="sm">
        <div style={{ display: 'flex', gap: '1rem' }}>
          <Button
            onClick={handleSave}
            loading={isSaving}
            disabled={!email || !validateEmail(email) || isSaving}
            styles={{
              root: {
                backgroundColor: '#4DABF7',
                '&:hover': {
                  backgroundColor: '#228BE6'
                },
                '&:disabled': {
                  backgroundColor: '#373A40',
                  color: '#909296'
                }
              }
            }}
          >
            {isSaving ? 'Saving...' : 'Save Settings'}
          </Button>
          
          <Button
            onClick={handleSendNow}
            loading={isSending}
            disabled={!email || !validateEmail(email) || isSending}
            leftSection={<FiSend size={16} />}
            styles={{
              root: {
                backgroundColor: '#20c997',
                '&:hover': {
                  backgroundColor: '#12b886'
                },
                '&:disabled': {
                  backgroundColor: '#373A40',
                  color: '#909296'
                }
              }
            }}
          >
            {isSending ? 'Sending...' : 'Send Now'}
          </Button>
        </div>
      </Stack>
    </Stack>
  );
} 