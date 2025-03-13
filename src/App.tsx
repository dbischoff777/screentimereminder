import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { MantineProvider, Text, Button, Stack, Paper, Code } from '@mantine/core';
import { useState, useEffect } from 'react';

// Simple debug component to display when in debug mode
const DebugMode = () => {
  const [logs, setLogs] = useState<string[]>([]);
  const [deviceInfo, setDeviceInfo] = useState<any>({});

  useEffect(() => {
    // Add startup log
    addLog('Debug mode started');
    
    // Collect device info
    const info = {
      userAgent: navigator.userAgent,
      platform: navigator.platform,
      language: navigator.language,
      screenWidth: window.screen.width,
      screenHeight: window.screen.height,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      timestamp: new Date().toISOString()
    };
    
    setDeviceInfo(info);
    addLog('Device info collected');
    
    // Check localStorage
    try {
      const testKey = 'debug_test';
      localStorage.setItem(testKey, 'test');
      const testValue = localStorage.getItem(testKey);
      localStorage.removeItem(testKey);
      addLog(`LocalStorage test: ${testValue === 'test' ? 'PASSED' : 'FAILED'}`);
    } catch (e) {
      addLog(`LocalStorage error: ${e instanceof Error ? e.message : String(e)}`);
    }
  }, []);
  
  const addLog = (message: string) => {
    setLogs(prev => [...prev, `[${new Date().toISOString()}] ${message}`]);
  };
  
  const clearLogs = () => {
    setLogs([]);
  };
  
  const testBackgroundMode = () => {
    addLog('Testing BackgroundMode availability...');
    try {
      if (typeof window !== 'undefined') {
        addLog(`window exists: ${!!window}`);
        addLog(`cordova exists: ${!!(window as any).cordova}`);
        addLog(`Capacitor exists: ${!!(window as any).Capacitor}`);
        
        // Try to access BackgroundMode
        try {
          const BackgroundMode = require('@ionic-native/background-mode');
          addLog(`BackgroundMode import: ${!!BackgroundMode ? 'SUCCESS' : 'FAILED'}`);
        } catch (e) {
          addLog(`BackgroundMode import error: ${e instanceof Error ? e.message : String(e)}`);
        }
      } else {
        addLog('window is undefined');
      }
    } catch (e) {
      addLog(`Test error: ${e instanceof Error ? e.message : String(e)}`);
    }
  };

  return (
    <div style={{ padding: 20, maxWidth: '100vw' }}>
      <Paper p="md" withBorder style={{ maxWidth: 600, margin: '0 auto' }}>
        <Stack>
          <Text size="xl" fw={700} color="blue">Debug Mode</Text>
          <Text>This is a minimal version of the app to diagnose startup crashes.</Text>
          
          <Text fw={600}>Device Information:</Text>
          <Code block style={{ maxHeight: 150, overflow: 'auto' }}>
            {JSON.stringify(deviceInfo, null, 2)}
          </Code>
          
          <Text fw={600}>Debug Logs:</Text>
          <Code block style={{ maxHeight: 300, overflow: 'auto' }}>
            {logs.join('\n')}
          </Code>
          
          <div style={{ display: 'flex', gap: '12px' }}>
            <Button color="blue" onClick={testBackgroundMode}>
              Test Background Mode
            </Button>
            <Button variant="outline" color="gray" onClick={clearLogs}>
              Clear Logs
            </Button>
          </div>
        </Stack>
      </Paper>
    </div>
  );
};

// Minimal App component without ScreenTimeProvider to diagnose crashes
function App() {
  // Check for debug mode in URL or localStorage
  const isDebugMode = window.location.search.includes('debug=true') || 
                      localStorage.getItem('debug_mode') === 'true';
  
  // If in debug mode, render minimal debug UI
  if (isDebugMode) {
    return (
      <MantineProvider theme={{ primaryColor: 'cyan' }}>
        <DebugMode />
      </MantineProvider>
    );
  }
  
  // Otherwise render a minimal version of the app without ScreenTimeProvider
  return (
    <MantineProvider theme={{ primaryColor: 'cyan' }}>
      <Router>
        <Routes>
          <Route path="/" element={
            <div style={{ padding: 20 }}>
              <Paper p="md" withBorder style={{ maxWidth: 500, margin: '0 auto' }}>
                <Stack>
                  <Text size="xl" fw={700}>Screen Time Reminder</Text>
                  <Text>Minimal version for crash diagnosis</Text>
                  <Button 
                    color="cyan" 
                    onClick={() => {
                      localStorage.setItem('debug_mode', 'true');
                      window.location.reload();
                    }}
                  >
                    Enable Debug Mode
                  </Button>
                </Stack>
              </Paper>
            </div>
          } />
        </Routes>
      </Router>
    </MantineProvider>
  );
}

export default App;