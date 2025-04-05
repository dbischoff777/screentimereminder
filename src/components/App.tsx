import { HashRouter as Router, Routes, Route } from 'react-router-dom';
import { MantineProvider, createTheme, Text, Button, Stack, Paper, Code } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import '../styles/App.css';
import { useState, useEffect } from 'react';
import BackgroundService from '../services/BackgroundService';

// Pages
import Home from '../pages/Home';
import Statistics from '../pages/Statistics';
import Settings from '../pages/Settings';
import NotificationPermission from '../pages/NotificationPermission';
import DetailedAnalytics from '../pages/DetailedAnalytics';
import PermissionDebug from '../pages/PermissionDebug';

// Components
import Navigation from './Navigation';

// Create cyberpunk theme
const cyberpunkTheme = createTheme({
  colors: {
    neon: [
      '#E0FFFF', // lightest
      '#B0E0E6',
      '#87CEEB',
      '#00FFFF', // cyan
      '#00CED1',
      '#FF00FF', // magenta
      '#FF1493',
      '#FF00FF', // fuchsia
      '#9400D3',
      '#8A2BE2'  // darkest
    ],
  },
  primaryColor: 'neon',
  primaryShade: 5,
  black: '#000020',
  white: '#f0f0f0',
  fontFamily: 'Orbitron, sans-serif',
  components: {
    Button: {
      defaultProps: {
        color: 'neon.5',
      },
      styles: {
        root: {
          boxShadow: '0 0 10px #FF00FF',
          border: '1px solid #FF00FF',
        },
      },
    },
    Container: {
      defaultProps: {
        bg: '#000020',
      },
    },
    Paper: {
      defaultProps: {
        bg: 'transparent',
      },
    },
  },
});

// Debug mode component
const DebugMode = () => {
  const [logs, setLogs] = useState<string[]>([]);
  const [deviceInfo, setDeviceInfo] = useState<any>({});

  useEffect(() => {
    addLog('Debug mode started');
    
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
            <Button color="cyan" component="a" href="/permission-debug">
              Permission Debug
            </Button>
          </div>
        </Stack>
      </Paper>
    </div>
  );
};

function App() {
  // Check for debug mode
  const isDebugMode = window.location.search.includes('debug=true') || 
                      localStorage.getItem('debug_mode') === 'true';

  // Initialize background service
  useEffect(() => {
    const initializeBackgroundService = async () => {
      try {
        const isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
        
        if (isMobile) {
          console.log('Initializing background service for mobile device');
          const backgroundService = BackgroundService.getInstance();
          backgroundService.initialize();
        } else {
          console.log('Background service not initialized (not a mobile device)');
        }
      } catch (error) {
        console.error('Error initializing background service:', error);
      }
    };
    
    initializeBackgroundService();
  }, []);

  // Render debug mode or full app
  return (
    <MantineProvider theme={cyberpunkTheme} defaultColorScheme="dark">
      <Notifications position="top-right" />
      <Router>
        {isDebugMode ? (
          <Routes>
            <Route path="/" element={<DebugMode />} />
            <Route path="/permission-debug" element={<PermissionDebug />} />
            <Route path="/notification-permission" element={<NotificationPermission />} />
          </Routes>
        ) : (
          <div className="app-container">
            <Routes>
              <Route path="/" element={<Home />} />
              <Route path="/statistics" element={<Statistics />} />
              <Route path="/settings" element={<Settings />} />
              <Route path="/notification-permission" element={<NotificationPermission />} />
              <Route path="/detailed" element={<DetailedAnalytics />} />
              <Route path="/permission-debug" element={<PermissionDebug />} />
            </Routes>
            <Navigation />
          </div>
        )}
      </Router>
    </MantineProvider>
  );
}

export default App;
