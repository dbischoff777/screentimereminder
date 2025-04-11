import { Button, Group, Text } from '@mantine/core';
import { useState, useEffect, useRef } from 'react';

interface CustomDropdownProps {
  options: { value: string; label: string }[];
  value: string | number;
  onChange: (value: string | number) => void;
  label: string;
}

const CustomDropdown = ({ 
  options, 
  value, 
  onChange, 
  label
}: CustomDropdownProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const [currentValue, setCurrentValue] = useState(value.toString());
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Update internal value when prop changes
  useEffect(() => {
    setCurrentValue(value.toString());
  }, [value]);

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

  // Find the selected option's label
  const selectedOption = options.find(opt => opt.value === currentValue);

  const handleOptionSelect = (optionValue: string) => {
    console.log('CustomDropdown: Selected value:', optionValue, 'Current value:', currentValue);
    if (optionValue !== currentValue) {
      console.log('CustomDropdown: Value changed, updating to:', optionValue);
      setCurrentValue(optionValue);
      onChange(optionValue);
    } else {
      console.log('CustomDropdown: Value unchanged:', optionValue);
    }
    setIsOpen(false);
  };

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
        {selectedOption ? selectedOption.label : `${currentValue} minutes`}
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
                variant={currentValue === option.value ? "filled" : "outline"}
                onClick={() => handleOptionSelect(option.value)}
                style={{
                  flex: 1,
                  backgroundColor: currentValue === option.value 
                    ? '#FF00FF' 
                    : 'transparent',
                  borderColor: '#FF00FF',
                  color: currentValue === option.value 
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