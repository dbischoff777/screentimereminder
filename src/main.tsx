import React from 'react'
import ReactDOM from 'react-dom/client'
import { MantineProvider } from '@mantine/core'
import { Notifications } from '@mantine/notifications'
import './styles/index.css'
import './styles/DropdownUtils.css'
import './styles/TabStyles.css'
import './utils/dropdownPositioningFix'
import './styles/FocusTimerPage.css'
import App from './components/App.tsx'
import { ScreenTimeProvider } from './context/ScreenTimeContext.tsx'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <MantineProvider>
      <Notifications position="top-right" zIndex={1000} />
      <ScreenTimeProvider>
        <App />
      </ScreenTimeProvider>
    </MantineProvider>
  </React.StrictMode>,
)
