import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { MantineProvider } from '@mantine/core';
import Home from './pages/Home';
import Settings from './pages/Settings';
import NotificationPermission from './pages/NotificationPermission';
import { ScreenTimeProvider } from './context/ScreenTimeContext';
import Statistics from './pages/Statistics';

function App() {
  return (
    <MantineProvider
      theme={{
        primaryColor: 'cyan',
      }}
    >
      <ScreenTimeProvider>
        <Router>
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="/notification-permission" element={<NotificationPermission />} />
            <Route path="/statistics" element={<Statistics />} />
          </Routes>
        </Router>
      </ScreenTimeProvider>
    </MantineProvider>
  );
}

export default App; 