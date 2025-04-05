import { Button, Group, Text } from '@mantine/core';
import { useState, useEffect, useRef } from 'react';

interface CustomDropdownProps {
  options: { value: string; label: string }[];
  value: number;
  onChange: (value: number) => void;
  label: string;
}

const CustomDropdown = ({ 
  options, 
  value, 
  onChange, 
  label
}: CustomDropdownProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <div ref={dropdownRef} style={{ position: 'relative', width: '100%' }}>
      <Text style={{ color: '#00FFFF', marginBottom: '0.5rem' }}>
        {label}
      </Text>
      <Button
        onClick={() => setIsOpen(!isOpen)}
        style={{
          width: '100%',
          background: 'rgba(0, 0, 40, 0.3)',
          borderColor: '#FF00FF',
          color: '#FFFFFF',
          '&:hover': {
            backgroundColor: 'rgba(255, 0, 255, 0.2)',
          }
        }}
      >
        {value} minutes
      </Button>

      {isOpen && (
        <div
          style={{
            position: 'absolute',
            top: '100%',
            left: 0,
            right: 0,
            zIndex: 1000,
            background: 'rgba(0, 0, 40, 0.9)',
            border: '1px solid #FF00FF',
            borderRadius: '4px',
            marginTop: '4px',
            padding: '8px',
            maxHeight: '200px',
            overflowY: 'auto',
          }}
        >
          <Group style={{ display: 'flex', justifyContent: 'space-between', gap: '8px' }}>
            {options.map((option) => (
              <Button
                key={option.value}
                variant={value === parseInt(option.value) ? "filled" : "outline"}
                onClick={() => {
                  onChange(parseInt(option.value));
                  setIsOpen(false);
                }}
                style={{
                  flex: 1,
                  backgroundColor: value === parseInt(option.value) 
                    ? '#FF00FF' 
                    : 'transparent',
                  borderColor: '#FF00FF',
                  color: value === parseInt(option.value) 
                    ? '#000020' 
                    : '#00FFFF',
                  fontWeight: 'bold',
                }}
              >
                {option.label}
              </Button>
            ))}
          </Group>
        </div>
      )}
    </div>
  );
};

export default CustomDropdown; 