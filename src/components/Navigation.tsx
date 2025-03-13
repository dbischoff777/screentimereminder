import { useLocation, useNavigate } from 'react-router-dom';
import { FiHome, FiBarChart2, FiSettings } from 'react-icons/fi';

const Navigation = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const currentPath = location.pathname;

  // Skip navigation on notification permission page
  if (currentPath === '/notification-permission') {
    return null;
  }

  const navItems = [
    { path: '/', label: 'Home', icon: <FiHome size={24} /> },
    { path: '/statistics', label: 'Stats', icon: <FiBarChart2 size={24} /> },
    { path: '/settings', label: 'Settings', icon: <FiSettings size={24} /> },
  ];

  return (
    <div
      style={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        display: 'flex',
        justifyContent: 'space-around',
        alignItems: 'center',
        background: 'rgba(10, 10, 20, 0.9)',
        borderTop: '2px solid #FF00FF',
        boxShadow: '0 -5px 15px rgba(255, 0, 255, 0.3)',
        padding: '0.75rem 0',
        zIndex: 1000,
      }}
    >
      {navItems.map((item) => {
        const isActive = currentPath === item.path;
        return (
          <div
            key={item.path}
            onClick={() => navigate(item.path)}
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              color: isActive ? '#FF00FF' : '#00FFFF',
              textShadow: isActive ? '0 0 10px #FF00FF' : 'none',
              cursor: 'pointer',
              transition: 'all 0.3s ease',
              padding: '0.5rem 1rem',
              borderRadius: '8px',
              background: isActive ? 'rgba(255, 0, 255, 0.1)' : 'transparent',
            }}
          >
            {item.icon}
            <span style={{ marginTop: '0.25rem', fontSize: '0.8rem' }}>{item.label}</span>
          </div>
        );
      })}
    </div>
  );
};

export default Navigation; 