import { useState, useEffect, useRef } from 'react';
import { Paper, Text, Progress, Stack, ActionIcon, Badge, Title } from '@mantine/core';
import { FiPlay, FiPause, FiSkipForward, FiRotateCcw, FiChevronDown } from 'react-icons/fi';
import '../styles/FocusTimer.css'; // Add this import for custom dropdown styling
import '../styles/Mobile.css'; // Mobile-specific styling for Android
import '../styles/Buttons.css';

interface FocusTimerProps {
  onSessionComplete: (category: string, duration: number) => void;
}

const categories = [
  { value: 'Productivity', label: 'Productivity', color: '#33FF57' },
  { value: 'Education', label: 'Education', color: '#3357FF' },
  { value: 'Creative', label: 'Creative', color: '#FF00FF' },
  { value: 'Reading', label: 'Reading', color: '#00FFFF' },
  { value: 'Meditation', label: 'Meditation', color: '#FF5733' },
];

const durations = Array.from({ length: 24 }, (_, i) => {
  const minutes = (i + 1) * 5;
  return { value: minutes.toString(), label: `${minutes} min` };
});

// Custom dropdown component
const CustomDropdown = ({ 
  options, 
  value, 
  onChange, 
  label 
}: { 
  options: { value: string, label: string, color?: string }[], 
  value: string, 
  onChange: (val: string) => void,
  label: string
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const toggleRef = useRef<HTMLDivElement>(null);
  
  // Force category dropdown to open upward
  const openUpward = label === "Focus Category";
  
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
          // Position above the toggle button for category
          dropdown.style.bottom = `${window.innerHeight - rect.top}px`;
          dropdown.style.top = 'auto';
        } else {
          // Position below for duration
          dropdown.style.top = `${rect.bottom}px`;
          dropdown.style.bottom = 'auto';
        }
      }
    }
  }, [isOpen, openUpward]);

  const selectedOption = options.find(opt => opt.value === value);
  
  const dropdownClass = openUpward ? "focus-category-dropdown" : "";

  return (
    <div className={`dropdown-wrapper ${dropdownClass}`} style={{ marginBottom: '1rem' }} ref={dropdownRef}>
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
          className={`custom-dropdown-menu ${openUpward ? 'position-top' : 'position-bottom'}`}
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

const FocusTimer: React.FC<FocusTimerProps> = ({ onSessionComplete }) => {
  const [timeLeft, setTimeLeft] = useState(25 * 60); // 25 minutes in seconds
  const [isRunning, setIsRunning] = useState(false);
  const [selectedDuration, setSelectedDuration] = useState('25');
  const [selectedCategory, setSelectedCategory] = useState('Productivity');
  const [showCompleteMessage, setShowCompleteMessage] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let timer: NodeJS.Timeout;

    if (isRunning && timeLeft > 0) {
      timer = setInterval(() => {
        setTimeLeft(prev => {
          if (prev <= 1) {
            setIsRunning(false);
            setShowCompleteMessage(true);
            onSessionComplete(selectedCategory, parseInt(selectedDuration));
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    }

    return () => {
      if (timer) clearInterval(timer);
    };
  }, [isRunning, timeLeft, selectedCategory, selectedDuration, onSessionComplete]);

  const handleDurationChange = (value: string | null) => {
    if (!value) return;
    setSelectedDuration(value);
    setTimeLeft(parseInt(value) * 60);
    setIsRunning(false);
    setShowCompleteMessage(false);
  };

  const handleCategoryChange = (value: string | null) => {
    if (!value) return;
    setSelectedCategory(value);
    setShowCompleteMessage(false);
  };

  const toggleTimer = () => {
    setIsRunning(!isRunning);
    setShowCompleteMessage(false);
  };

  const resetTimer = () => {
    setTimeLeft(parseInt(selectedDuration) * 60);
    setIsRunning(false);
    setShowCompleteMessage(false);
  };

  const skipTimer = () => {
    setTimeLeft(0);
    setIsRunning(false);
    setShowCompleteMessage(true);
    onSessionComplete(selectedCategory, parseInt(selectedDuration));
  };

  const formatTime = (seconds: number) => {
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
  };

  const getProgress = () => {
    const totalSeconds = parseInt(selectedDuration) * 60;
    return ((totalSeconds - timeLeft) / totalSeconds) * 100;
  };

  return (
    <Paper
      ref={containerRef}
      style={{
        background: 'rgba(0, 0, 32, 0.3)',
        padding: '2rem',
        paddingBottom: '3rem',
        borderRadius: '12px',
        border: '1px solid rgba(255, 0, 255, 0.1)',
        marginBottom: '1rem',
        position: 'relative',
        zIndex: 10,
      }}
      className="focus-timer-container"
    >
      <Title
        order={2}
        style={{
          fontSize: '1.5rem',
          marginBottom: '1.5rem',
          color: '#00FFFF',
          textAlign: 'center',
          textShadow: '0 0 10px rgba(0, 255, 255, 0.5)'
        }}
      >
        Focus Timer
      </Title>

      <Stack gap="xl">
        {/* Timer Display and Duration Picker */}
        <div style={{ textAlign: 'center' }}>
          {!isRunning && (
            <CustomDropdown
              options={durations}
              value={selectedDuration}
              onChange={handleDurationChange}
              label="Duration"
            />
          )}

          <Text 
            size="4rem" 
            style={{ 
              color: '#FFFFFF',
              fontWeight: 700,
              fontFamily: 'monospace',
              textShadow: '0 0 20px rgba(255, 255, 255, 0.5)',
              marginBottom: '1rem'
            }}
          >
            {formatTime(timeLeft)}
          </Text>

          {showCompleteMessage && (
            <Badge 
              size="lg"
              style={{
                backgroundColor: categories.find(c => c.value === selectedCategory)?.color,
                color: '#000000',
                marginBottom: '1rem'
              }}
            >
              Session Complete!
            </Badge>
          )}
        </div>

        {/* Progress Bar */}
        <Progress
          value={getProgress()}
          size="lg"
          radius="xl"
          styles={() => ({
            root: { 
              backgroundColor: 'rgba(255, 255, 255, 0.1)',
              height: '12px' 
            },
            section: { 
              backgroundColor: categories.find(c => c.value === selectedCategory)?.color || '#FFFFFF',
              transition: 'width 1s linear',
              boxShadow: '0 0 10px rgba(255, 255, 255, 0.5)'
            }
          })}
        />

        {/* Controls */}
        <div className="controls-wrapper" style={{
          padding: '1.2rem 0',
          borderRadius: '8px',
          backgroundColor: 'rgba(0, 0, 32, 0.5)',
          backdropFilter: 'blur(10px)',
          border: '1px solid rgba(255, 0, 255, 0.1)',
          marginTop: '0.5rem',
          marginBottom: '1.5rem',
          display: 'flex',
          justifyContent: 'center'
        }}>
          <div style={{ 
            display: 'flex', 
            width: '240px',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}>
            <div className="cyber-button-wrapper">
              <ActionIcon
                variant="filled"
                size="xl"
                radius="xl"
                onClick={resetTimer}
                className="reset-btn"
                style={{
                  backgroundColor: 'rgba(255, 255, 255, 0.1)',
                  color: '#FFFFFF',
                  border: '2px solid rgba(255, 255, 255, 0.5)',
                  boxShadow: '0 0 10px rgba(255, 255, 255, 0.2)',
                  transition: 'all 0.3s ease',
                  width: '50px',
                  height: '50px'
                }}
              >
                <FiRotateCcw size={24} />
              </ActionIcon>
            </div>

            <div className="cyber-button-wrapper">
              <ActionIcon
                variant="filled"
                size="xl"
                radius="xl"
                onClick={toggleTimer}
                className={`play-pause-btn ${isRunning ? 'pause-btn' : ''}`}
                style={{
                  backgroundColor: isRunning ? 'rgba(255, 0, 0, 0.2)' : 'rgba(0, 255, 0, 0.2)',
                  color: isRunning ? '#FF0000' : '#00FF00',
                  border: `2px solid ${isRunning ? '#FF0000' : '#00FF00'}`,
                  boxShadow: `0 0 15px ${isRunning ? 'rgba(255, 0, 0, 0.3)' : 'rgba(0, 255, 0, 0.3)'}`,
                  transition: 'all 0.3s ease',
                  width: '50px',
                  height: '50px'
                }}
              >
                {isRunning ? <FiPause size={24} /> : <FiPlay size={24} />}
              </ActionIcon>
            </div>

            <div className="cyber-button-wrapper">
              <ActionIcon
                variant="filled"
                size="xl"
                radius="xl"
                onClick={skipTimer}
                className="skip-btn"
                style={{
                  backgroundColor: 'rgba(0, 255, 255, 0.1)',
                  color: '#00FFFF',
                  border: '2px solid #00FFFF',
                  boxShadow: '0 0 10px rgba(0, 255, 255, 0.3)',
                  transition: 'all 0.3s ease',
                  width: '50px',
                  height: '50px'
                }}
              >
                <FiSkipForward size={24} />
              </ActionIcon>
            </div>
          </div>
        </div>

        {/* Category Selection */}
        <CustomDropdown
          options={categories}
          value={selectedCategory}
          onChange={handleCategoryChange}
          label="Focus Category"
        />
      </Stack>
    </Paper>
  );
};

export default FocusTimer; 