import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { MantineProvider, createTheme } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import '../styles/App.css';
import { useEffect } from 'react';
import BackgroundService from '../services/BackgroundService';

// Pages
import Home from '../pages/Home.tsx';
import Statistics from '../pages/Statistics.tsx';
import Settings from '../pages/Settings.tsx';
import NotificationPermission from '../pages/NotificationPermission.tsx';
import DetailedAnalytics from '../pages/DetailedAnalytics.tsx';

// Components
import Navigation from './Navigation.tsx';

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
  black: '#000020', // Updated to match our dark background
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

function App() {
  // Initialize background service when app starts
  useEffect(() => {
    const initializeBackgroundService = async () => {
      try {
        // Check if running on a mobile device
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

  return (
    <MantineProvider theme={cyberpunkTheme} defaultColorScheme="dark">
      <Notifications position="top-right" />
      <Router>
        <div className="app-container">
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/statistics" element={<Statistics />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="/notification-permission" element={<NotificationPermission />} />
            <Route path="/detailed" element={<DetailedAnalytics />} />
          </Routes>
          <Navigation />
        </div>
      </Router>
    </MantineProvider>
  );
}

export default App;
